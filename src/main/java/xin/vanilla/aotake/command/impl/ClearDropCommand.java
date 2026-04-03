package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.MessageUtils;
import xin.vanilla.banira.common.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("resource")
public class ClearDropCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> clear() {
        Command<CommandSourceStack> clearDropCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }
            int range = CommandUtils.getIntDefault(context, "range", 0);
            if (range == 0)
                range = NumberUtils.toInt(CommandUtils.replaceResourcePath(CommandUtils.getStringEx(context, "dimension", "")));
            ServerLevel dimension = CommandUtils.getDimensionDefault(context, "dimension", null);
            boolean withEntity = CommandUtils.getBooleanDefault(context, "withEntity", false);
            boolean ignoreFilter = CommandUtils.getBooleanDefault(context, "ignoreFilter", false);

            List<Entity> entities;
            if (range > 0) {
                ServerPlayer player = context.getSource().getPlayerOrException();
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
                    .filter(entity -> !(entity instanceof Player))
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

            BaniraCodex.serverInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(player -> MessageUtils.sendNotification(player
                            , AotakeUtils.getWarningMessage(result.isEmpty() ? "fail" : "success"
                                    , AotakeLang.getPlayerLanguage(player)
                                    , result
                            )
                            , AotakeNotificationTypes.ADMIN_BROADCAST));
            return 1;
        };

        return Commands.literal(CommonConfig.get().command().commandClearDrop())
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
