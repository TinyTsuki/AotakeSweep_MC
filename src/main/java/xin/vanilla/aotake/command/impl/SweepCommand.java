package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.BaniraScheduler;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SweepCommand {
    public static LiteralArgumentBuilder<CommandSource> sweep() {
        Command<CommandSource> sweepCommand = context -> {
            if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
            if (context.getSource().getEntity() instanceof ServerPlayerEntity) {
                ServerPlayerEntity player = context.getSource().getPlayerOrException();
                Component modName = AotakeComponent.get().trans("key.aotake_sweep.categories").languageCode(AotakeLang.getPlayerLanguage(player));
                CommandUtils.notifyHelp(context, PlayerSweepData.getData(player), modName, "/" + AotakeUtils.getCommandPrefix());
            }

            int range = CommandUtils.getIntDefault(context, "range", 0);
            if (range == 0)
                range = NumberUtils.toInt(CommandUtils.replaceResourcePath(CommandUtils.getStringEx(context, "dimension", "")));
            ServerWorld dimension = CommandUtils.getDimensionDefault(context, "dimension", null);
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

            BaniraScheduler.schedule(context.getSource().getServer(), 1, () -> AotakeUtils.sweep(entities, false));
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_SWEEP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.SWEEP))
                .executes(sweepCommand)
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(sweepCommand)
                )
                .then(Commands.argument("range", IntegerArgumentType.integer(0))
                        .executes(sweepCommand)
                );
    }
}
