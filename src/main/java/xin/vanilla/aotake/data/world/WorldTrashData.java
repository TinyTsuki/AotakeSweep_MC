package xin.vanilla.aotake.data.world;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.DropStatistics;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.data.WorldCoordinate;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.DateUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 世界垃圾数据
 */
@Getter
@SuppressWarnings("resource")
public class WorldTrashData extends SavedData {
    private static final String DATA_NAME = "world_trash_data";

    private List<SimpleContainer> inventoryList = new ArrayList<>();

    /**
     * 掉落物列表
     */
    private ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> dropList = new ConcurrentShuffleList<>();
    /**
     * 掉落物统计
     */
    private Queue<DropStatistics> dropCount = new ConcurrentLinkedQueue<>();
    /**
     * {@link #dropCount} 当前对应的日历日期（与 {@link DateUtils#toString(Date)} 一致），用于跨日时先落盘上一日再切换到当日
     */
    private String dropStatsDate;

    public WorldTrashData() {
    }

    public static WorldTrashData load(CompoundTag nbt) {
        WorldTrashData data = new WorldTrashData();
        // 未开启持久化直接返回
        try {
            if (!CommonConfig.get().base().dustbin().dustbinPersistent()) return data;
        } catch (Throwable ignored) {
        }

        data.dropList = new ConcurrentShuffleList<>();
        ListTag dropListTag = nbt.getList("dropList", 10);
        ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops = new ConcurrentShuffleList<>();
        for (int i = 0; i < dropListTag.size(); i++) {
            CompoundTag drop = dropListTag.getCompound(i);
            ItemStack item = ItemStack.of(drop.getCompound("item"));
            drops.add(new KeyValue<>(
                    WorldCoordinate.fromTag(drop.getCompound("coordinate"))
                    , item
            ));
        }
        data.setDrops(drops);

        String todayStr = DateUtils.toString(new Date());
        MinecraftServer server = BaniraCodex.serverInstance().val() ? BaniraCodex.serverInstance().key() : null;
        Queue<DropStatistics> dropCounts = DropStatisticsStorage.loadByDate(server, todayStr);
        // 若 NBT 中有 dropCount 且当日 JSON 为空，则迁移至 JSON
        if (dropCounts.isEmpty() && nbt.contains("dropCount")) {
            ListTag dropCountNBT = nbt.getList("dropCount", 10);
            for (int i = 0; i < dropCountNBT.size(); i++) {
                dropCounts.add(DropStatistics.deserializeNBT(dropCountNBT.getCompound(i)));
            }
            if (server != null && !dropCounts.isEmpty()) {
                DropStatisticsStorage.saveByDate(server, todayStr, dropCounts);
            }
            nbt.remove("dropCount");
        }
        data.setDropCount(dropCounts);
        data.dropStatsDate = todayStr;

        data.inventoryList = new ArrayList<>();
        ListTag inventoryListTag = nbt.getList("inventoryList", 9);
        for (Tag inbt : inventoryListTag) {
            SimpleContainer inventory = new SimpleContainer(6 * 9);
            inventory.fromTag((ListTag) inbt);
            data.inventoryList.add(inventory);
        }
        return data;
    }

    @NonNull
    @Override
    @ParametersAreNonnullByDefault
    public CompoundTag save(CompoundTag nbt) {
        // 未开启持久化直接返回
        try {
            if (!CommonConfig.get().base().dustbin().dustbinPersistent()) return nbt;
        } catch (Throwable ignored) {
        }

        ListTag dropsNBT = new ListTag();
        for (KeyValue<WorldCoordinate, ItemStack> drop : this.getDropList()) {
            if (drop == null || drop.value() == null) continue;
            CompoundTag dropTag = new CompoundTag();
            dropTag.put("item", drop.value().serializeNBT());
            dropTag.put("coordinate", drop.key().toTag());
            dropsNBT.add(dropTag);
        }
        nbt.put("dropList", dropsNBT);

        String todayStr = DateUtils.toString(new Date());
        if (BaniraCodex.serverInstance().val()) {
            MinecraftServer server = BaniraCodex.serverInstance().key();
            rolloverDropStatisticsIfNeeded(server, todayStr);
            DropStatisticsStorage.saveByDate(server, todayStr, this.dropCount);
        }

        ListTag inventoryNBT = new ListTag();
        for (SimpleContainer inventory : this.getInventoryList()) {
            inventoryNBT.add(inventory.createTag());
        }
        nbt.put("inventoryList", inventoryNBT);

        return nbt;
    }

    private void setDrops(ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops) {
        this.dropList = drops;
        super.setDirty();
    }

    private void setDropCount(Queue<DropStatistics> drops) {
        this.dropCount = drops;
        super.setDirty();
    }

    /**
     * 长时间运行时若已跨自然日，将仍属于 {@link #dropStatsDate} 的条目写入对应日期文件，
     * 再与当日 JSON 合并，避免把前一日累计继续写入新日期文件。
     */
    private void rolloverDropStatisticsIfNeeded(MinecraftServer server, String todayStr) {
        if (this.dropStatsDate == null) {
            this.dropStatsDate = todayStr;
            return;
        }
        if (this.dropStatsDate.equals(todayStr)) {
            return;
        }
        List<DropStatistics> snapshot = new ArrayList<>(this.dropCount);
        Queue<DropStatistics> oldDayEntries = new ConcurrentLinkedQueue<>();
        Queue<DropStatistics> todayEntries = new ConcurrentLinkedQueue<>();
        for (DropStatistics stat : snapshot) {
            String entryDate = DateUtils.toString(new Date(stat.getTime()));
            if (this.dropStatsDate.equals(entryDate)) {
                oldDayEntries.add(stat);
            } else {
                todayEntries.add(stat);
            }
        }
        DropStatisticsStorage.saveByDate(server, this.dropStatsDate, oldDayEntries);
        Queue<DropStatistics> mergedToday = DropStatisticsStorage.loadByDate(server, todayStr);
        mergedToday.addAll(todayEntries);
        this.dropCount = mergedToday;
        this.dropStatsDate = todayStr;
        super.setDirty();
    }

    public static WorldTrashData get() {
        return get(BaniraCodex.serverInstance().key().getAllLevels().iterator().next());
    }

    public static WorldTrashData get(ServerPlayer player) {
        return get(player.serverLevel());
    }

    public static WorldTrashData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(WorldTrashData::load, WorldTrashData::new, DATA_NAME);
    }

    public static MenuProvider getTrashContainer(ServerPlayer player, int page) {
        int limit = CommonConfig.get().base().dustbin().dustbinPageLimit();
        List<SimpleContainer> inventories = get().getInventoryList();
        int size = inventories.size();
        if (inventories.isEmpty() || size < limit) {
            for (int i = 0; i < limit - size; i++) {
                inventories.add(new SimpleContainer(6 * 9));
            }
        } else if (size > limit) {
            for (int i = size - limit; i > 0; i--) {
                inventories.remove(inventories.size() - 1);
            }
        }

        if (page < 1 || page > limit) {
            return null;
        }

        // 将当前页垃圾箱填充满
        SimpleContainer inventory = inventories.get(page - 1);
        ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops = get().getDropList();

        fillInventory(inventory, drops);

        return new MenuProvider() {
            @NonNull
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                Component title = AotakeComponent.get().transAuto("title")
                        .color(0x5DA530);
                Component vComponent = AotakeComponent.get().literal(String.format("(%s/%s)", page, limit))
                        .color(0x5DA530);
                Component bComponent = AotakeComponent.get().literal(String.format("(%s)", CommonConfig.get().base().dustbin().dustbinBlockPositions().size()))
                        .color(EnumMCColor.RED.getColor());
                Component plusComponent = AotakeComponent.get().literal("+")
                        .color(EnumMCColor.BLACK.getColor());
                switch (CommonConfig.get().base().dustbin().dustbinBlockMode()) {
                    case VIRTUAL: {
                        title.append(String.format("(%s/%s)", page, limit));
                    }
                    break;
                    case VIRTUAL_BLOCK: {
                        title.append(vComponent.append(plusComponent).append(bComponent));
                    }
                    break;
                    case BLOCK_VIRTUAL: {
                        title.append(bComponent.append(plusComponent).append(vComponent));
                    }
                    break;
                }
                return title.toVanilla(AotakeLang.getPlayerLanguage(player));
            }

            @Override
            public AbstractContainerMenu createMenu(int id, @NonNull Inventory playerInventory, @NonNull Player p) {
                return ChestMenu.sixRows(id, playerInventory, inventory);
            }
        };
    }

    private static void fillInventory(SimpleContainer inventory, ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops) {
        List<KeyValue<WorldCoordinate, ItemStack>> leftovers = new ArrayList<>();

        for (KeyValue<WorldCoordinate, ItemStack> drop : drops.snapshot()) {
            ItemStack stack = drop.value();
            if (stack == null || stack.isEmpty()) continue;

            ItemStack remaining = tryFillInventory(inventory, stack);

            drops.remove(drop);

            if (!remaining.isEmpty()) {
                leftovers.add(new KeyValue<>(drop.key(), remaining));
            }
        }

        // 剩余部分重新塞回去
        drops.addAll(leftovers);
    }

    private static ItemStack tryFillInventory(SimpleContainer inventory, ItemStack stack) {
        // 合并到已有的相同物品槽
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (ItemStack.isSameItemSameTags(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int transferable = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(transferable);
                stack.shrink(transferable);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        // 填入空槽
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

}
