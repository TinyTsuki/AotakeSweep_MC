package xin.vanilla.aotake.command.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.ChunkVaultGrants;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.data.world.ChunkVaultStorage;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.*;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class ChunkVaultCommand {

    private static final SuggestionProvider<CommandSourceStack> VAULT_ID_SUGGEST = (context, builder) -> {
        if (!BaniraServer.isRunning()) {
            return builder.buildFuture();
        }
        for (String id : ChunkVaultStorage.listVaultIds(BaniraServer.require(MinecraftServer.class))) {
            if (StringUtils.isNullOrEmptyEx(id)) continue;
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(CommonConfig.get().command().commandChunkVault());

        root.then(Commands.literal("list")
                .requires(src -> AotakeUtils.hasCommandPermission(src, EnumCommandType.CHUNK_VAULT))
                .executes(ctx -> list(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page")))));

        root.then(Commands.literal("open")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(VAULT_ID_SUGGEST)
                        .executes(ctx -> openVault(ctx, StringArgumentType.getString(ctx, "id"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> openVault(ctx, StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "page"))))));

        root.then(Commands.literal("grant")
                .requires(src -> AotakeUtils.hasCommandPermission(src, EnumCommandType.CHUNK_VAULT))
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests(VAULT_ID_SUGGEST)
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> grant(ctx, StringArgumentType.getString(ctx, "id"),
                                        EntityArgument.getPlayers(ctx, "players"))))));

        return root;
    }

    private static boolean canOpen(ServerPlayer player, String vaultId) {
        return AotakeUtils.hasCommandPermission(player, EnumCommandType.CHUNK_VAULT)
                || ChunkVaultGrants.isGranted(player, vaultId);
    }

    private static int list(CommandContext<CommandSourceStack> context, int page) throws CommandSyntaxException {
        if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<String> ids = ChunkVaultStorage.listVaultIds(player.getServer());
        if (CollectionUtils.isNullOrEmpty(ids)) {
            MessageUtils.sendNotification(player, AotakeComponent.get().transAuto("chunk_vault_list_empty"), AotakeNotificationTypes.CHUNK_VAULT_LIST);
            return 1;
        }
        int perPage = CommonConfig.get().base().common().helpInfoNumPerPage();
        int pages = (int) Math.ceil(ids.size() / (double) perPage);
        page = Math.min(Math.max(page, 1), Math.max(pages, 1));
        int from = (page - 1) * perPage;
        int to = Math.min(from + perPage, ids.size());
        String lang = AotakeLang.getPlayerLanguage(player);
        Component header = AotakeComponent.get().transAuto("chunk_vault_list_header",
                String.valueOf(page), String.valueOf(pages), String.valueOf(ids.size()));
        Component body = AotakeComponent.get().empty();
        for (int i = from; i < to; i++) {
            String id = ids.get(i);
            if (i > from) body.append("\n");
            String openCmd = "/" + AotakeUtils.getCommandPrefix() + " " + CommonConfig.get().command().commandChunkVault()
                    + " open " + id;
            body.append(AotakeComponent.get().literal(id)
                    .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, openCmd))
                    .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            AotakeComponent.get().transAuto("click_to_suggest").toVanilla(lang))));
        }
        MessageUtils.sendNotification(player, header.append("\n").append(body), AotakeNotificationTypes.CHUNK_VAULT_LIST);
        return 1;
    }

    private static int openVault(CommandContext<CommandSourceStack> context, String vaultId, int page) throws CommandSyntaxException {
        if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!canOpen(player, vaultId)) {
            MessageUtils.sendNotification(player, AotakeComponent.get().transAuto("chunk_vault_no_access", vaultId), AotakeNotificationTypes.DUSTBIN);
            return 0;
        }
        if (!ChunkVaultStorage.vaultExists(vaultId)) {
            MessageUtils.sendNotification(player, AotakeComponent.get().transAuto("chunk_vault_not_found", vaultId), AotakeNotificationTypes.DUSTBIN);
            return 0;
        }
        ChunkVaultSession.open(player, vaultId, page);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> context, String vaultId, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        if (CommandUtils.checkModStatus(context, AotakeSweep::isDisable)) return 0;
        ServerPlayer admin = context.getSource().getPlayerOrException();
        if (!ChunkVaultStorage.vaultExists(vaultId)) {
            MessageUtils.sendNotification(admin, AotakeComponent.get().transAuto("chunk_vault_not_found", vaultId), AotakeNotificationTypes.DUSTBIN);
            return 0;
        }
        for (ServerPlayer target : targets) {
            ChunkVaultGrants.grant(admin.getServer(), vaultId, target);
            MessageUtils.sendNotification(target, AotakeComponent.get().transAuto("chunk_vault_granted", vaultId), AotakeNotificationTypes.DUSTBIN);
        }
        String playerNames = targets.stream()
                .map(p -> p.getDisplayName().getString())
                .collect(Collectors.joining("§7, §f"));
        MessageUtils.sendNotification(admin, AotakeComponent.get().transAuto("chunk_vault_grant_ok",
                vaultId, playerNames, String.valueOf(targets.size())), AotakeNotificationTypes.DUSTBIN);
        return 1;
    }
}
