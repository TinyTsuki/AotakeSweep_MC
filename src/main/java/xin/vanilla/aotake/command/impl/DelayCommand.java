package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.DateUtils;

import java.util.Date;

@SuppressWarnings("resource")
public class DelayCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> delay() {
        Command<CommandSourceStack> delaySweepCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            Date current = new Date();
            long delay = CommandUtils.getLongDefault(context, "seconds", ServerConfig.SWEEP_INTERVAL.get() / 1000);
            if (delay > 0) {
                EventHandlerProxy.setNextSweepTime(current.getTime() + delay * 1000);
            } else {
                long nextSweepTime = EventHandlerProxy.getNextSweepTime() + delay * 1000;
                if (nextSweepTime < current.getTime())
                    nextSweepTime = current.getTime() + ServerConfig.SWEEP_INTERVAL.get();
                EventHandlerProxy.setNextSweepTime(nextSweepTime);
            }
            // 给已安装mod玩家同步扫地倒计时
            for (String uuid : AotakeSweep.getCustomConfigStatus()) {
                ServerPlayer player = AotakeUtils.getPlayerByUUID(uuid);
                if (player != null) {
                    AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
                }
            }
            long seconds = (EventHandlerProxy.getNextSweepTime() - current.getTime()) / 1000;
            Component message = Component.translatable(EnumI18nType.MESSAGE, "next_sweep_time_set"
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
                    , Component.literal(String.valueOf(seconds)).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                            , Component.literal(DateUtils.toDateTimeString(new Date(EventHandlerProxy.getNextSweepTime())) + " (Server Time)").toTextComponent())
                    )
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));

            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_DELAY_SWEEP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DELAY_SWEEP))
                .executes(delaySweepCommand)
                .then(Commands.argument("seconds", LongArgumentType.longArg())
                        .suggests((context, builder) -> {
                            builder.suggest((int) (ServerConfig.SWEEP_INTERVAL.get() / 1000));
                            return builder.buildFuture();
                        })
                        .executes(delaySweepCommand)
                );
    }
}
