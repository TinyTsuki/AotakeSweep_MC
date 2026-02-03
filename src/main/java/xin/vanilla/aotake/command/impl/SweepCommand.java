package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeScheduler;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SweepCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> sweep() {
        Command<CommandSourceStack> sweepCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            int range = CommandUtils.getIntDefault(context, "range", 0);
            if (range == 0)
                range = StringUtils.toInt(CommandUtils.replaceResourcePath(CommandUtils.getStringEx(context, "dimension", "")));
            ServerLevel dimension = CommandUtils.getDimensionDefault(context, "dimension", null);
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

            AotakeScheduler.schedule(context.getSource().getServer(), 1, () -> AotakeUtils.sweep(entities, false));
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
