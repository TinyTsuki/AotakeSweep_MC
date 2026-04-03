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
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.*;

import java.util.Date;
import java.util.Map;

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
            long delay = CommandUtils.getLongDefault(context, "seconds", CommonConfig.SWEEP_INTERVAL.get() / 1000);
            if (delay > 0) {
                EventHandlerProxy.setNextSweepTime(current.getTime() + delay * 1000);
            } else {
                long nextSweepTime = EventHandlerProxy.getNextSweepTime() + delay * 1000;
                if (nextSweepTime < current.getTime())
                    nextSweepTime = current.getTime() + CommonConfig.SWEEP_INTERVAL.get();
                EventHandlerProxy.setNextSweepTime(nextSweepTime);
            }
            // 给已安装mod玩家同步扫地倒计时
            for (Map.Entry<String, Boolean> entry : PlayerUtils.remoteClientModInstalled().entrySet()) {
                if (entry.getValue()) continue;
                ServerPlayerEntity player = AotakeUtils.getPlayerByUUID(entry.getKey());
                if (player != null) {
                    PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new SweepTimeSyncToClient(), player);
                }
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
                    .forEach(p -> MessageUtils.sendMessage(p, message));

            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_DELAY_SWEEP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.DELAY_SWEEP))
                .executes(delaySweepCommand)
                .then(Commands.argument("seconds", LongArgumentType.longArg())
                        .suggests((context, builder) -> {
                            builder.suggest((int) (CommonConfig.SWEEP_INTERVAL.get() / 1000));
                            return builder.buildFuture();
                        })
                        .executes(delaySweepCommand)
                );
    }
}
