package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.event.HoverEvent;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.*;

import java.util.Date;

@SuppressWarnings("resource")
public class DelayCommand {
    public static LiteralArgumentBuilder<CommandSource> delay() {
        Command<CommandSource> delaySweepCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }

            Date current = new Date();
            long delay = CommandUtils.getLongDefault(context, "seconds", CommonConfig.get().base().sweep().sweepInterval() / 1000);
            if (delay > 0) {
                EventHandlerProxy.setNextSweepTime(current.getTime() + delay * 1000);
            } else {
                long nextSweepTime = EventHandlerProxy.getNextSweepTime() + delay * 1000;
                if (nextSweepTime < current.getTime())
                    nextSweepTime = current.getTime() + CommonConfig.get().base().sweep().sweepInterval();
                EventHandlerProxy.setNextSweepTime(nextSweepTime);
            }
            // 给已声明客户端 mod 且尚未完成数据同步的玩家同步扫地倒计时与玩家偏好
            for (ServerPlayerEntity player : BaniraCodex.serverInstance().key().getPlayerList().getPlayers()) {
                if (PlayerUtils.isPlayerDataSynced(player, AotakeSweep.MODID)) continue;
                PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new SweepDataSyncToClient(player), player);
            }
            long seconds = (EventHandlerProxy.getNextSweepTime() - current.getTime()) / 1000;
            Component message = AotakeComponent.get().transAuto("next_sweep_time_set"
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
                    , AotakeComponent.get().literal(String.valueOf(seconds)).hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                            , AotakeComponent.get().literal(DateUtils.toDateTimeString(new Date(EventHandlerProxy.getNextSweepTime())) + " (Server Time)").toVanilla())
                    )
            );
            BaniraCodex.serverInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendNotification(p, message, AotakeNotificationTypes.ADMIN_BROADCAST));

            return 1;
        };

        return Commands.literal(CommonConfig.get().command().commandDelaySweep())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DELAY_SWEEP))
                .executes(delaySweepCommand)
                .then(Commands.argument("seconds", LongArgumentType.longArg())
                        .suggests((context, builder) -> {
                            builder.suggest((int) (CommonConfig.get().base().sweep().sweepInterval() / 1000));
                            return builder.buildFuture();
                        })
                        .executes(delaySweepCommand)
                );
    }
}
