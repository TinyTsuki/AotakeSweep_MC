package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.DimensionUtils;
import xin.vanilla.banira.common.util.MessageUtils;

import java.util.List;

@SuppressWarnings("resource")
public class CacheCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> clear() {
        Command<CommandSourceStack> clearCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }
            WorldTrashData.get().getDropList().clear();
            WorldTrashData.get().setDirty();
            Component message = AotakeComponent.get().transAuto("cache_cleared"
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            BaniraServer.require(MinecraftServer.class)
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendNotification(p, message, AotakeNotificationTypes.ADMIN_BROADCAST));
            return 1;
        };

        return Commands.literal(CommonConfig.get().command().commandCacheClear())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_CLEAR))
                .executes(clearCacheCommand);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> drop() {
        Command<CommandSourceStack> dropCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }
            boolean originalPos = CommandUtils.getBooleanDefault(context, "originalPos", false);
            ServerPlayer player = context.getSource().getPlayerOrException();
            List<KeyValue<WorldCoordinate, ItemStack>> items = WorldTrashData.get().getDropList().snapshot();
            WorldTrashData.get().getDropList().clear();
            items.forEach(kv -> {
                if (!kv.value().isEmpty()) {
                    WorldCoordinate coordinate;
                    if (originalPos) {
                        coordinate = kv.key();
                    } else {
                        coordinate = new WorldCoordinate(player);
                    }
                    ServerLevel level = DimensionUtils.getLevel(coordinate.dimension());
                    Entity entity = AotakeUtils.getEntityFromItem(level, kv.value());
                    entity.moveTo(coordinate.x(), coordinate.y(), coordinate.z(), (float) coordinate.yaw(), (float) coordinate.pitch());
                    level.addFreshEntity(entity);
                }
            });
            WorldTrashData.get().setDirty();
            Component message = AotakeComponent.get().transAuto("cache_dropped"
                    , context.getSource().getEntity() instanceof ServerPlayer
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            BaniraServer.require(MinecraftServer.class)
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendNotification(p, message, AotakeNotificationTypes.ADMIN_BROADCAST));
            return 1;
        };

        return Commands.literal(CommonConfig.get().command().commandCacheDrop())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_DROP))
                .executes(dropCacheCommand)
                .then(Commands.argument("originalPos", BoolArgumentType.bool())
                        .executes(dropCacheCommand)
                );
    }
}
