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
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.Component;

import java.util.List;

@SuppressWarnings("resource")
public class CacheCommand {
    public static LiteralArgumentBuilder<CommandSource> clear() {
        Command<CommandSource> clearCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            WorldTrashData.get().getDropList().clear();
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_cleared"
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_CACHE_CLEAR.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CACHE_CLEAR))
                .executes(clearCacheCommand);
    }

    public static LiteralArgumentBuilder<CommandSource> drop() {
        Command<CommandSource> dropCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            boolean originalPos = CommandUtils.getBooleanDefault(context, "originalPos", false);
            ServerPlayerEntity player = context.getSource().getPlayerOrException();
            List<KeyValue<WorldCoordinate, ItemStack>> items = WorldTrashData.get().getDropList().snapshot();
            WorldTrashData.get().getDropList().clear();
            items.forEach(kv -> {
                if (!kv.getValue().isEmpty()) {
                    WorldCoordinate coordinate;
                    if (originalPos) {
                        coordinate = kv.getKey();
                    } else {
                        coordinate = new WorldCoordinate(player);
                    }
                    ServerWorld level = AotakeUtils.getWorld(coordinate.getDimension());
                    Entity entity = AotakeUtils.getEntityFromItem(level, kv.getValue());
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    level.addFreshEntity(entity);
                }
            });
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_dropped"
                    , context.getSource().getEntity() instanceof ServerPlayerEntity
                            ? context.getSource().getPlayerOrException().getDisplayName().getString()
                            : "server"
            );
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(p -> AotakeUtils.sendMessage(p, message));
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
