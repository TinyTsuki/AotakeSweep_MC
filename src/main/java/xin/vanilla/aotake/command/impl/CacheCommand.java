package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.server.ServerWorld;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.DimensionUtils;
import xin.vanilla.banira.common.util.MessageUtils;

import java.util.List;

@SuppressWarnings("resource")
public class CacheCommand {
    public static LiteralArgumentBuilder<CommandSource> clear() {
        Command<CommandSource> clearCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }

            WorldTrashData.get().getDropList().clear();
            WorldTrashData.get().setDirty();
            Component message = AotakeComponent.get().transAuto("cache_cleared"
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            BaniraCodex.serverInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendMessage(p, message));
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_CACHE_CLEAR.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_CLEAR))
                .executes(clearCacheCommand);
    }

    public static LiteralArgumentBuilder<CommandSource> drop() {
        Command<CommandSource> dropCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }

            boolean originalPos = CommandUtils.getBooleanDefault(context, "originalPos", false);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
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
                    ServerWorld level = DimensionUtils.getLevel(coordinate.dimension());
                    Entity entity = AotakeUtils.getEntityFromItem(level, kv.value());
                    entity.moveTo(coordinate.x(), coordinate.y(), coordinate.z(), (float) coordinate.yaw(), (float) coordinate.pitch());
                    level.addFreshEntity(entity);
                }
            });
            WorldTrashData.get().setDirty();
            Component message = AotakeComponent.get().transAuto("cache_dropped"
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            BaniraCodex.serverInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> MessageUtils.sendMessage(p, message));
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_CACHE_DROP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_DROP))
                .executes(dropCacheCommand)
                .then(Commands.argument("originalPos", BoolArgumentType.bool())
                        .executes(dropCacheCommand)
                );
    }
}
