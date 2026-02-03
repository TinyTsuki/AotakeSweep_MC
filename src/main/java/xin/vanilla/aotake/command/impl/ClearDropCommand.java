package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("resource")
public class ClearDropCommand {
    public static LiteralArgumentBuilder<CommandSource> clear() {
        Command<CommandSource> clearDropCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            int range = CommandUtils.getIntDefault(context, "range", 0);
            if (range == 0)
                range = StringUtils.toInt(CommandUtils.replaceResourcePath(CommandUtils.getStringEx(context, "dimension", "")));
            ServerWorld dimension = CommandUtils.getDimensionDefault(context, "dimension", null);
            boolean withEntity = CommandUtils.getBooleanDefault(context, "withEntity", false);
            boolean ignoreFilter = CommandUtils.getBooleanDefault(context, "ignoreFilter", false);

            List<Entity> entities;
            if (range > 0) {
                ServerPlayerEntity player = context.getSource().getPlayerOrException();
                entities = new ArrayList<>(player.level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range)));
            } else if (dimension != null) {
                entities = AotakeUtils.getAllEntities().stream()
                        .filter(entity -> entity.level == dimension)
                        .collect(Collectors.toList());
            } else {
                entities = AotakeUtils.getAllEntities();
            }
            entities = entities.stream()
                    .filter(Objects::nonNull)
                    .filter(entity -> !(entity instanceof PlayerEntity))
                    .filter(entity -> withEntity || entity instanceof ItemEntity)
                    .collect(Collectors.toList());
            if (!ignoreFilter)
                entities = AotakeUtils.getAllEntitiesByFilter(entities, false);

            SweepResult result = new SweepResult();
            entities.forEach(entity -> {
                if (entity instanceof ItemEntity) {
                    result.plusItemCount(((ItemEntity) entity).getItem().getCount());
                } else {
                    result.plusEntityCount();
                }
                AotakeUtils.removeEntity(entity, false);
            });

            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(player -> AotakeUtils.sendMessage(player
                            , AotakeUtils.getWarningMessage(result.isEmpty() ? "fail" : "success"
                                    , AotakeUtils.getPlayerLanguage(player)
                                    , result
                            )
                    ));
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_CLEAR_DROP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.CLEAR_DROP))
                .executes(clearDropCommand)
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(clearDropCommand)
                        .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                .executes(clearDropCommand)
                                .then(Commands.argument("ignoreFilter", BoolArgumentType.bool())
                                        .executes(clearDropCommand)
                                )
                        )
                )
                .then(Commands.argument("range", IntegerArgumentType.integer(0))
                        .executes(clearDropCommand)
                        .then(Commands.argument("withEntity", BoolArgumentType.bool())
                                .executes(clearDropCommand)
                                .then(Commands.argument("ignoreFilter", BoolArgumentType.bool())
                                        .executes(clearDropCommand)
                                )
                        )
                );
    }
}
