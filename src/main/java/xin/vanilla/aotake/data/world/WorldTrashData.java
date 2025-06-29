package xin.vanilla.aotake.data.world;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.Coordinate;
import xin.vanilla.aotake.data.DropStatistics;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 世界垃圾数据
 */
@Getter
public class WorldTrashData extends SavedData {
    private static final String DATA_NAME = "world_trash_data";

    private List<SimpleContainer> inventoryList = new ArrayList<>();

    /**
     * 掉落物列表
     */
    private ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> dropList = new ConcurrentShuffleList<>();
    /**
     * 掉落物统计
     */
    private Queue<DropStatistics> dropCount = new ConcurrentLinkedQueue<>();

    public WorldTrashData() {
    }

    public static WorldTrashData load(CompoundTag nbt, HolderLookup.Provider provider) {
        WorldTrashData data = new WorldTrashData();
        data.dropList = new ConcurrentShuffleList<>();
        ListTag dropListTag = nbt.getList("dropList", 10);
        ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> drops = new ConcurrentShuffleList<>();
        for (int i = 0; i < dropListTag.size(); i++) {
            CompoundTag drop = dropListTag.getCompound(i);
            ItemStack item = ItemStack.CODEC.decode(NbtOps.INSTANCE, drop.getCompound("item")).result().orElse(new Pair<>(null, null)).getFirst();
            drops.add(new KeyValue<>(
                    Coordinate.readFromNBT(drop.getCompound("coordinate"))
                    , item
            ));
        }
        data.setDrops(drops);

        data.dropCount = new ConcurrentLinkedQueue<>();
        ListTag dropCountNBT = nbt.getList("dropCount", 10);
        Queue<DropStatistics> dropCounts = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < dropCountNBT.size(); i++) {
            CompoundTag drop = dropCountNBT.getCompound(i);
            dropCounts.add(DropStatistics.deserializeNBT(drop));
        }
        data.setDropCount(dropCounts);

        data.inventoryList = new ArrayList<>();
        ListTag inventoryListTag = nbt.getList("inventoryList", 9);
        for (Tag inbt : inventoryListTag) {
            SimpleContainer inventory = new SimpleContainer(6 * 9);
            inventory.fromTag((ListTag) inbt, provider);
            data.inventoryList.add(inventory);
        }
        return data;
    }

    @NonNull
    @Override
    @ParametersAreNonnullByDefault
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag dropsNBT = new ListTag();
        for (KeyValue<Coordinate, ItemStack> drop : this.getDropList()) {
            if (drop == null || drop.getValue() == null) continue;
            CompoundTag dropTag = new CompoundTag();
            dropTag.put("item", ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, drop.getValue()).result().orElse(new CompoundTag()));
            dropTag.put("coordinate", drop.getKey().writeToNBT());
            dropsNBT.add(dropTag);
        }
        nbt.put("dropList", dropsNBT);

        ListTag dropCountNBT = new ListTag();
        this.dropCount.forEach(statistics -> dropCountNBT.add(statistics.serializeNBT()));
        nbt.put("dropCount", dropCountNBT);

        ListTag inventoryNBT = new ListTag();
        for (SimpleContainer inventory : this.getInventoryList()) {
            inventoryNBT.add(inventory.createTag(provider));
        }
        nbt.put("inventoryList", inventoryNBT);

        return nbt;
    }

    private void setDrops(ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> drops) {
        this.dropList = drops;
        super.setDirty();
    }

    private void setDropCount(Queue<DropStatistics> drops) {
        this.dropCount = drops;
        super.setDirty();
    }

    public static WorldTrashData get() {
        return get(AotakeSweep.getServerInstance().getAllLevels().iterator().next());
    }

    public static WorldTrashData get(ServerPlayer player) {
        return get(player.serverLevel());
    }

    public static WorldTrashData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(new Factory<>(WorldTrashData::new, WorldTrashData::load, DataFixTypes.SAVED_DATA_MAP_DATA), DATA_NAME);
    }

    public static MenuProvider getTrashContainer(ServerPlayer player, int page) {
        int limit = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
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
        ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> drops = get().getDropList();

        fillInventory(inventory, drops);

        return new MenuProvider() {
            @NonNull
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return Component.translatable(EnumI18nType.KEY, "categories")
                        .setColor(EnumMCColor.DARK_GREEN.getColor())
                        .append(String.format("(%s/%s)", page, limit))
                        .toTextComponent(AotakeUtils.getPlayerLanguage(player));
            }

            @Override
            public AbstractContainerMenu createMenu(int id, @NonNull Inventory playerInventory, @NonNull Player p) {
                return ChestMenu.sixRows(id, playerInventory, inventory);
            }
        };
    }

    private static void fillInventory(SimpleContainer inventory, ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> drops) {
        List<KeyValue<Coordinate, ItemStack>> leftovers = new ArrayList<>();

        for (KeyValue<Coordinate, ItemStack> drop : drops.snapshot()) {
            ItemStack stack = drop.getValue();
            if (stack == null || stack.isEmpty()) continue;

            ItemStack remaining = tryFillInventory(inventory, stack);

            drops.remove(drop);

            if (!remaining.isEmpty()) {
                leftovers.add(new KeyValue<>(drop.getKey(), remaining));
            }
        }

        // 剩余部分重新塞回去
        drops.addAll(leftovers);
    }

    private static ItemStack tryFillInventory(SimpleContainer inventory, ItemStack stack) {
        // 合并到已有的相同物品槽
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
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
