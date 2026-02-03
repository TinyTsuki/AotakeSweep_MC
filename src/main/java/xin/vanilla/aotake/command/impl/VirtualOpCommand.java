package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumOperationType;
import xin.vanilla.aotake.network.packet.CustomConfigSyncToClient;
import xin.vanilla.aotake.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class VirtualOpCommand {
    public static LiteralArgumentBuilder<CommandSource> vop() {
        Command<CommandSource> virtualOpCommand = context -> {
            if (CommandUtils.checkModStatus(context)) return 0;
            CommandUtils.notifyHelp(context);
            CommandSource source = context.getSource();
            // 如果命令来自玩家
            if (source.getEntity() == null || source.getEntity() instanceof ServerPlayerEntity) {
                EnumOperationType type = EnumOperationType.fromString(StringArgumentType.getString(context, "operation"));
                EnumCommandType[] rules;
                try {
                    rules = Arrays.stream(StringArgumentType.getString(context, "rules").split(","))
                            .filter(StringUtils::isNotNullOrEmpty)
                            .map(String::trim)
                            .map(String::toUpperCase)
                            .map(EnumCommandType::valueOf).toArray(EnumCommandType[]::new);
                } catch (IllegalArgumentException ignored) {
                    rules = new EnumCommandType[]{};
                }
                List<ServerPlayerEntity> targetList = new ArrayList<>();
                try {
                    targetList.addAll(EntityArgument.getPlayers(context, "player"));
                } catch (IllegalArgumentException ignored) {
                }
                String language = CommandUtils.getLanguage(source);
                for (ServerPlayerEntity target : targetList) {
                    switch (type) {
                        case ADD:
                            VirtualPermissionManager.addVirtualPermission(target, rules);
                            break;
                        case SET:
                            VirtualPermissionManager.setVirtualPermission(target, rules);
                            break;
                        case DEL:
                        case REMOVE:
                            VirtualPermissionManager.delVirtualPermission(target, rules);
                            break;
                        case CLEAR:
                            VirtualPermissionManager.clearVirtualPermission(target);
                            break;
                    }
                    String permissions = VirtualPermissionManager.buildPermissionsString(VirtualPermissionManager.getVirtualPermission(target));
                    AotakeUtils.sendTranslatableMessage(target, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                    if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
                        ServerPlayerEntity player = source.getPlayerOrException();
                        if (!target.getStringUUID().equalsIgnoreCase(player.getStringUUID())) {
                            AotakeUtils.sendTranslatableMessage(player, I18nUtils.getKey(EnumI18nType.MESSAGE, "player_virtual_op"), target.getDisplayName().getString(), permissions);
                        }
                    } else {
                        source.sendSuccess(Component.translatable(language, EnumI18nType.MESSAGE, "player_virtual_op", target.getDisplayName().getString(), permissions).toChatComponent(), true);
                    }
                    // 更新权限信息
                    AotakeUtils.refreshPermission(target);
                    for (ServerPlayerEntity player : source.getServer().getPlayerList().getPlayers()) {
                        if (AotakeSweep.getCustomConfigStatus().contains(AotakeUtils.getPlayerUUIDString(player))) {
                            AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
                        }
                    }
                }
            }
            return 1;
        };

        return Commands.literal(CommonConfig.COMMAND_VIRTUAL_OP.get())
                .requires(source -> AotakeUtils.hasCommandPermission(source, EnumCommandType.VIRTUAL_OP))
                .then(Commands.argument("operation", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest(EnumOperationType.ADD.name().toLowerCase());
                            builder.suggest(EnumOperationType.SET.name().toLowerCase());
                            builder.suggest(EnumOperationType.DEL.name().toLowerCase());
                            builder.suggest(EnumOperationType.CLEAR.name().toLowerCase());
                            builder.suggest(EnumOperationType.GET.name().toLowerCase());
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(virtualOpCommand)
                                .then(Commands.argument("rules", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            String operation = StringArgumentType.getString(context, "operation");
                                            if (operation.equalsIgnoreCase(EnumOperationType.GET.name().toLowerCase())
                                                    || operation.equalsIgnoreCase(EnumOperationType.CLEAR.name().toLowerCase())
                                                    || operation.equalsIgnoreCase(EnumOperationType.LIST.name().toLowerCase())) {
                                                return builder.buildFuture();
                                            }
                                            String input = CommandUtils.getStringEmpty(context, "rules").replace(" ", ",");
                                            String[] split = input.split(",");
                                            String current = input.endsWith(",") ? "" : split[split.length - 1];
                                            for (EnumCommandType value : Arrays.stream(EnumCommandType.values())
                                                    .filter(EnumCommandType::isOp)
                                                    .filter(type -> Arrays.stream(split).noneMatch(in -> in.equalsIgnoreCase(type.name())))
                                                    .filter(type -> StringUtils.isNullOrEmptyEx(current) || type.name().toLowerCase().contains(current.toLowerCase()))
                                                    .sorted(Comparator.comparing(EnumCommandType::getSort))
                                                    .collect(Collectors.toList())) {
                                                String suggest = value.name();
                                                if (input.endsWith(",")) {
                                                    suggest = input + suggest;
                                                }
                                                builder.suggest(suggest);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(virtualOpCommand)
                                )
                        )
                );
    }
}
