package xin.vanilla.aotake.data.world;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.ChunkVaultPageSyncToClient;
import xin.vanilla.banira.common.util.BaniraScheduler;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public final class ChunkVaultSession {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final ConcurrentHashMap<String, Holder> OPEN = new ConcurrentHashMap<>();

    private ChunkVaultSession() {
    }

    public static boolean hasOpenSession(ServerPlayerEntity player) {
        return OPEN.containsKey(PlayerUtils.getPlayerUUIDString(player));
    }

    public static void open(ServerPlayerEntity player, String vaultId, int page1Based) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!ChunkVaultStorage.vaultExists(vaultId)) {
            return;
        }
        List<ItemStack> items = ChunkVaultStorage.readAllItems(server, vaultId);
        List<Inventory> pages = distributeIntoPages(items);
        int total = Math.max(1, pages.size());
        int page = Math.min(Math.max(page1Based, 1), total);
        String uuid = PlayerUtils.getPlayerUUIDString(player);
        Holder holder = new Holder(vaultId, pages, page);
        OPEN.put(uuid, holder);
        player.openMenu(holder.createMenuProvider(player, page, total));
        AotakeSweep.getPlayerChunkVaultId().put(uuid, vaultId);
        AotakeSweep.getPlayerChunkVaultPage().put(uuid, page);
        PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new ChunkVaultPageSyncToClient(page, total), player);
    }

    /**
     * 翻页或刷新（offset 0）：复用内存中的分页数据，避免每次关箱都写盘并全量重读 NBT。
     */
    public static void navigateOrReload(ServerPlayerEntity player, int offset) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String uuid = PlayerUtils.getPlayerUUIDString(player);
        Holder holder = OPEN.get(uuid);
        if (holder == null) {
            String vaultId = AotakeSweep.getPlayerChunkVaultId().get(uuid);
            Integer page = AotakeSweep.getPlayerChunkVaultPage().get(uuid);
            if (vaultId != null && page != null) {
                int next = page + offset;
                if (next < 1) return;
                open(player, vaultId, next);
            }
            return;
        }
        if (offset == 0) {
            List<ItemStack> items = ChunkVaultStorage.readAllItems(server, holder.vaultId);
            List<Inventory> newPages = distributeIntoPages(items);
            holder.replacePages(newPages);
            int total = Math.max(1, holder.pages.size());
            holder.currentPage = Math.min(Math.max(holder.currentPage, 1), total);
        } else {
            int total = Math.max(1, holder.pages.size());
            int next = Math.min(Math.max(holder.currentPage + offset, 1), total);
            if (next == holder.currentPage) return;
            holder.currentPage = next;
        }
        holder.skipPersistOnNextClose = true;
        player.closeContainer();
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        String uuid = PlayerUtils.getPlayerUUIDString(player);
        Holder holder = OPEN.get(uuid);
        if (holder == null) return;

        if (holder.skipPersistOnNextClose) {
            holder.skipPersistOnNextClose = false;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            BaniraScheduler.schedule(server, 0, () -> reopenAfterPageChange(player, uuid));
            return;
        }

        OPEN.remove(uuid);
        AotakeSweep.getPlayerChunkVaultId().remove(uuid);
        AotakeSweep.getPlayerChunkVaultPage().remove(uuid);
        MinecraftServer server = player.getServer();
        if (server == null) return;
        try {
            List<ItemStack> flat = flattenInventories(holder.pages);
            ChunkVaultStorage.writeAllItems(server, holder.vaultId, flat);
        } catch (Exception e) {
            LOGGER.warn("Failed to save chunk vault session {}: {}", holder.vaultId, e.getMessage());
        }
    }

    private static void reopenAfterPageChange(ServerPlayerEntity player, String uuid) {
        if (!player.isAlive()) return;
        Holder holder = OPEN.get(uuid);
        if (holder == null) return;
        int total = Math.max(1, holder.pages.size());
        int page = Math.min(Math.max(holder.currentPage, 1), total);
        holder.currentPage = page;
        player.openMenu(holder.createMenuProvider(player, page, total));
        AotakeSweep.getPlayerChunkVaultId().put(uuid, holder.vaultId);
        AotakeSweep.getPlayerChunkVaultPage().put(uuid, page);
        PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new ChunkVaultPageSyncToClient(page, total), player);
    }

    private static List<ItemStack> flattenInventories(List<Inventory> pages) {
        List<ItemStack> out = new ArrayList<>();
        for (Inventory inv : pages) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) {
                    out.add(s.copy());
                }
            }
        }
        return out;
    }

    private static List<Inventory> distributeIntoPages(List<ItemStack> all) {
        if (all == null || all.isEmpty()) {
            Inventory one = new Inventory(54);
            return new ArrayList<>(Collections.singletonList(one));
        }
        List<Inventory> pages = new ArrayList<>();
        Inventory cur = new Inventory(54);
        pages.add(cur);
        for (ItemStack raw : all) {
            ItemStack stack = raw.copy();
            while (!stack.isEmpty()) {
                ItemStack leftover = tryFillInventory(cur, stack);
                stack = leftover;
                if (!stack.isEmpty()) {
                    cur = new Inventory(54);
                    pages.add(cur);
                }
            }
        }
        return pages;
    }

    private static ItemStack tryFillInventory(Inventory inventory, ItemStack stack) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (ItemStack.isSame(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int transferable = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(transferable);
                stack.shrink(transferable);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                int transferable = Math.min(stack.getCount(), stack.getMaxStackSize());
                ItemStack toInsert = stack.copy();
                toInsert.setCount(transferable);
                inventory.setItem(i, toInsert);
                stack.shrink(transferable);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private static final class Holder {
        final String vaultId;
        List<Inventory> pages;
        int currentPage;
        boolean skipPersistOnNextClose;

        Holder(String vaultId, List<Inventory> pages, int currentPage1Based) {
            this.vaultId = vaultId;
            this.pages = pages;
            this.currentPage = currentPage1Based;
        }

        void replacePages(List<Inventory> newPages) {
            this.pages = newPages;
        }

        INamedContainerProvider createMenuProvider(ServerPlayerEntity player, int page, int totalPages) {
            Inventory inv = pages.get(page - 1);
            return new INamedContainerProvider() {
                @Nonnull
                @Override
                public ITextComponent getDisplayName() {
                    return AotakeComponent.get().transAuto("chunk_vault_title")
                            .append(AotakeComponent.get().literal(String.format(" (%s/%s)", page, totalPages)))
                            .toVanilla(AotakeLang.getPlayerLanguage(player));
                }

                @Nullable
                @Override
                public Container createMenu(int id, @Nonnull PlayerInventory playerInventory, @Nonnull PlayerEntity p) {
                    return ChestContainer.sixRows(id, playerInventory, inv);
                }
            };
        }
    }
}
