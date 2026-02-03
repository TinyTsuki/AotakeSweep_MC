package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.I18nUtils;

public class LanguageCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> lang() {
        Command<CommandSourceStack> languageCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            ServerPlayer player = context.getSource().getPlayerOrException();
            String language = StringArgumentType.getString(context, "language");
            if (I18nUtils.getI18nFiles().contains(language)) {
                CustomConfig.setPlayerLanguage(AotakeUtils.getPlayerUUIDString(player), language);
                AotakeUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_default_language", language));
            } else if ("server".equalsIgnoreCase(language) || "client".equalsIgnoreCase(language)) {
                CustomConfig.setPlayerLanguage(AotakeUtils.getPlayerUUIDString(player), language);
                AotakeUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "player_default_language", language));
            } else {
                AotakeUtils.sendMessage(player, Component.translatable(player, EnumI18nType.MESSAGE, "language_not_exist").setColor(0xFFFF0000));
            }
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_LANGUAGE.get())
                .then(Commands.argument("language", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("client");
                            builder.suggest("server");
                            I18nUtils.getI18nFiles().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(languageCommand)
                );
    }
}
