package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
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
    public static LiteralArgumentBuilder<CommandSourceStack> clear() {
        Command<CommandSourceStack> clearCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            WorldTrashData.get().getDropList().clear();
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_cleared"
                    , context.getSource().getEntity() instanceof ServerPlayer
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

    public static LiteralArgumentBuilder<CommandSourceStack> drop() {
        Command<CommandSourceStack> dropCacheCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            boolean originalPos = CommandUtils.getBooleanDefault(context, "originalPos", false);
            ServerPlayer player = context.getSource().getPlayerOrException();
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
                    ServerLevel level = AotakeUtils.getWorld(coordinate.getDimension());
                    Entity entity = AotakeUtils.getEntityFromItem(level, kv.getValue());
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    level.addFreshEntity(entity);
                }
            });
            WorldTrashData.get().setDirty();
            Component message = Component.translatable(EnumI18nType.MESSAGE
                    , "cache_dropped"
                    , context.getSource().getEntity() instanceof ServerPlayer
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
