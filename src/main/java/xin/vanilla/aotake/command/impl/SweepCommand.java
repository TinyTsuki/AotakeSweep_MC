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
import xin.vanilla.banira.common.util.EntityUtils;
import xin.vanilla.banira.common.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SweepCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> sweep() {
        Command<CommandSourceStack> sweepCommand = context -> {
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
            List<Entity> entities;
            if (range > 0) {
                ServerPlayer player = context.getSource().getPlayerOrException();
                entities = new ArrayList<>(player.level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range)));
            } else if (dimension != null) {
                entities = EntityUtils.getAllEntities().stream()
                        .filter(entity -> entity.level == dimension)
                        .collect(Collectors.toList());
            } else {
                entities = EntityUtils.getAllEntities();
            }

            BaniraScheduler.schedule(context.getSource().getServer(), 1, () -> AotakeUtils.sweep(entities, false));
            return 1;
        };

        return Commands.literal(CommonConfig.get().command().commandSweep())
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
