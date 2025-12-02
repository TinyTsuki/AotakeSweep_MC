package xin.vanilla.aotake.util;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class CommandUtils {

    private static final Logger LOGGER = LogManager.getLogger();


    public static boolean checkModStatus(CommandContext<CommandSourceStack> context) {
        if (AotakeSweep.disable()) {
            CommandSourceStack source = context.getSource();
            Entity entity = source.getEntity();
            if (entity instanceof ServerPlayer) {
                AotakeUtils.sendMessage((ServerPlayer) entity, Component.translatable(EnumI18nType.MESSAGE, "mod_disabled"));
            }
        }
        return AotakeSweep.disable();
    }

    public static String getLanguage(CommandSourceStack source) {
        String lang = ServerConfig.SERVER_CONFIG.defaultLanguage();
        if (source.getEntity() != null && source.getEntity() instanceof ServerPlayer) {
            try {
                lang = AotakeUtils.getPlayerLanguage(source.getPlayerOrException());
            } catch (Exception ignored) {
            }
        }
        return lang;
    }


    // region 指令参数相关

    public static void addSuggestion(SuggestionsBuilder suggestion, String input, String suggest) {
        if (suggest.contains(input) || StringUtils.isNullOrEmpty(input)) {
            suggestion.suggest(suggest);
        }
    }

    public static String getStringEmpty(CommandContext<?> context, String name) {
        return getStringDefault(context, name, "");
    }

    public static String getStringDefault(CommandContext<?> context, String name, String defaultValue) {
        String result;
        try {
            result = StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static String getStringEx(CommandContext<?> context, String name, String defaultValue) {
        String result;
        try {
            result = String.valueOf(context.getArgument(name, Object.class));
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static String replaceResourcePath(String s) {
        if (StringUtils.isNullOrEmpty(s)) return "";
        return s.substring(s.indexOf(":") + 1);
    }

    public static int getIntDefault(CommandContext<?> context, String name, int defaultValue) {
        int result;
        try {
            result = IntegerArgumentType.getInteger(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static long getLongDefault(CommandContext<?> context, String name, long defaultValue) {
        long result;
        try {
            result = LongArgumentType.getLong(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static boolean getBooleanDefault(CommandContext<?> context, String name, boolean defaultValue) {
        boolean result;
        try {
            result = BoolArgumentType.getBool(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static ServerLevel getDimensionDefault(CommandContext<CommandSourceStack> context, String name, ServerLevel defaultDimension) {
        ServerLevel result;
        try {
            result = DimensionArgument.getDimension(context, name);
        } catch (IllegalArgumentException | CommandSyntaxException e) {
            result = defaultDimension;
        }
        return result;
    }

    /**
     * 若为第一次使用指令则进行提示
     */
    public static void notifyHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            PlayerSweepData data = PlayerSweepData.getData(player);
            if (!data.isNotified()) {
                Component button = Component.literal("/" + AotakeUtils.getCommandPrefix())
                        .setColor(EnumMCColor.AQUA.getColor())
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + AotakeUtils.getCommandPrefix()))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + AotakeUtils.getCommandPrefix())
                                .toTextComponent())
                        );
                AotakeUtils.sendMessage(player, Component.translatable(EnumI18nType.MESSAGE, "notify_help", button));
                data.setNotified(true);
            }
        }
    }

    // endregion 指令参数相关

}
