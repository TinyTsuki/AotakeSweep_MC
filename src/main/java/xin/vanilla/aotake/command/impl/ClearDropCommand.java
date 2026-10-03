package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.api.sweep.SweepOperation;
import xin.vanilla.aotake.api.sweep.SweepTrigger;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.internal.forge.sweep.ForgeSweepTarget;
import xin.vanilla.aotake.internal.server.AotakeSweepRuntime;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.NumberUtils;


@SuppressWarnings("resource")
public class ClearDropCommand {
    public static LiteralArgumentBuilder<CommandSource> clear() {
        Command<CommandSource> clearDropCommand = context -> {
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
            boolean withEntity = CommandUtils.getBooleanDefault(context, "withEntity", false);
            boolean ignoreFilter = CommandUtils.getBooleanDefault(context, "ignoreFilter", false);

            ServerPlayerEntity player = range > 0 ? context.getSource().getPlayerOrException() : null;
            String dimensionId = player != null ? player.level.dimension().location().toString()
                    : dimension == null ? null : dimension.dimension().location().toString();
            ForgeSweepTarget target =
                    new ForgeSweepTarget(dimensionId,
                            player == null ? null : player.getBoundingBox().inflate(range), withEntity);
            try {
                AotakeSweepRuntime.submit(
                        SweepTrigger.COMMAND, SweepOperation.DELETE,
                        target, ignoreFilter, null, false, new SweepResult(), AotakeSweep.getEntitySweeper(), null);
            } catch (RuntimeException failure) {
                AotakeUtils.reportSweepFailure(failure);
                return 0;
            }
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
