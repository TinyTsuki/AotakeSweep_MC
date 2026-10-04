package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.MessageUtils;

public final class RuleReloadCommand {
    private RuleReloadCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("rules")
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CONFIG))
                .then(Commands.literal("reload").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    AotakeRuleRuntime.reload().whenComplete((id, failure) -> {
                        if (!source.getServer().isRunning()) return;
                        xin.vanilla.banira.common.data.Component message = AotakeComponent.get().transLang(
                                CommandUtils.getLanguage(source), EnumI18nType.WORD,
                                failure == null ? "rules_reload_success" : "rules_reload_failure");
                        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                            MessageUtils.sendNotification((net.minecraft.server.level.ServerPlayer) source.getEntity(),
                                    message, AotakeNotificationTypes.ADMIN_BROADCAST);
                        else MessageUtils.sendMessage(source, failure == null, message);
                        if (failure != null) org.apache.logging.log4j.LogManager.getLogger()
                                .error("Aotake local rule reload failed", failure);
                    });
                    return 1;
                }));
    }
}
