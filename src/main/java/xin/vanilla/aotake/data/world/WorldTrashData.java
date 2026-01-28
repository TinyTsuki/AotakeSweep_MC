package xin.vanilla.aotake.data.world;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.WorldCapabilityData;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.DropStatistics;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 世界垃圾数据
 */
@Getter
@SuppressWarnings("resource")
public class WorldTrashData extends WorldCapabilityData {
    private static final String DATA_NAME = "world_trash_data";

    private List<Inventory> inventoryList = new ArrayList<>();

    /**
     * 掉落物列表
     */
    private ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> dropList = new ConcurrentShuffleList<>();
    /**
     * 掉落物统计
     */
    private Queue<DropStatistics> dropCount = new ConcurrentLinkedQueue<>();

    public WorldTrashData() {
        super(DATA_NAME);
    }

    public void load(CompoundNBT nbt) {
        // 未开启持久化直接返回
        try {
            if (Boolean.FALSE.equals(ServerConfig.DUSTBIN_PERSISTENT.get())) return;
        } catch (Throwable ignored) {
        }

        this.dropList = new ConcurrentShuffleList<>();
        ListNBT dropListNBT = nbt.getList("dropList", 10);
        ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops = new ConcurrentShuffleList<>();
        for (int i = 0; i < dropListNBT.size(); i++) {
            CompoundNBT drop = dropListNBT.getCompound(i);
            ItemStack item = ItemStack.of(drop.getCompound("item"));
            drops.add(new KeyValue<>(
                    WorldCoordinate.readFromNBT(drop.getCompound("coordinate"))
                    , item
            ));
        }
        this.setDrops(drops);

        this.dropCount = new ConcurrentLinkedQueue<>();
        ListNBT dropCountNBT = nbt.getList("dropCount", 10);
        Queue<DropStatistics> dropCounts = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < dropCountNBT.size(); i++) {
            CompoundNBT drop = dropCountNBT.getCompound(i);
            dropCounts.add(DropStatistics.deserializeNBT(drop));
        }
        this.setDropCount(dropCounts);

        this.inventoryList = new ArrayList<>();
        ListNBT inventoryListNBT = nbt.getList("inventoryList", 9);
        for (INBT inbt : inventoryListNBT) {
            Inventory inventory = new Inventory(6 * 9);
            inventory.fromTag((ListNBT) inbt);
            this.inventoryList.add(inventory);
        }

    }

    @Override
    @NonNull
    public CompoundNBT save(CompoundNBT nbt) {
        // 未开启持久化直接返回
        try {
            if (Boolean.FALSE.equals(ServerConfig.DUSTBIN_PERSISTENT.get())) return nbt;
        } catch (Throwable ignored) {
        }

        ListNBT dropsNBT = new ListNBT();
        for (KeyValue<WorldCoordinate, ItemStack> drop : this.getDropList()) {
            if (drop == null || drop.getValue() == null) continue;
            CompoundNBT dropTag = new CompoundNBT();
            dropTag.put("item", drop.getValue().serializeNBT());
            dropTag.put("coordinate", drop.getKey().writeToNBT());
            dropsNBT.add(dropTag);
        }
        nbt.put("dropList", dropsNBT);

        ListNBT dropCountNBT = new ListNBT();
        this.dropCount.forEach(statistics -> dropCountNBT.add(statistics.serializeNBT()));
        nbt.put("dropCount", dropCountNBT);

        ListNBT inventoryNBT = new ListNBT();
        for (Inventory inventory : this.getInventoryList()) {
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

    public static WorldTrashData get() {
        return get(AotakeSweep.getServerInstance().key().getAllLevels().iterator().next());
    }

    public static WorldTrashData get(ServerPlayerEntity player) {
        return get(player.getLevel());
    }

    public static WorldTrashData get(ServerWorld world) {
        return world.getDataStorage().computeIfAbsent(WorldTrashData::new, DATA_NAME);
    }

    public static INamedContainerProvider getTrashContainer(ServerPlayerEntity player, int page) {
        int limit = CommonConfig.DUSTBIN_PAGE_LIMIT.get();
        List<Inventory> inventories = get().getInventoryList();
        int size = inventories.size();
        if (inventories.isEmpty() || size < limit) {
            for (int i = 0; i < limit - size; i++) {
                inventories.add(new Inventory(6 * 9));
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
        Inventory inventory = inventories.get(page - 1);
        ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops = get().getDropList();

        fillInventory(inventory, drops);

        return new INamedContainerProvider() {
            @NonNull
            @Override
            public ITextComponent getDisplayName() {
                Component title = Component.translatable(EnumI18nType.KEY, "categories")
                        .setColor(0x5DA530);
                Component vComponent = Component.literal(String.format("(%s/%s)", page, limit))
                        .setColor(0x5DA530);
                Component bComponent = Component.literal(String.format("(%s)", ServerConfig.DUSTBIN_BLOCK_POSITIONS.get().size()))
                        .setColor(EnumMCColor.RED.getColor());
                Component plusComponent = Component.literal("+")
                        .setColor(EnumMCColor.BLACK.getColor());
                switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
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
                return title.toTextComponent(AotakeUtils.getPlayerLanguage(player));
            }

            @Override
            public Container createMenu(int id, @NonNull PlayerInventory playerInventory, @NonNull PlayerEntity p) {
                return ChestContainer.sixRows(id, playerInventory, inventory);
            }
        };
    }

    private static void fillInventory(Inventory inventory, ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> drops) {
        List<KeyValue<WorldCoordinate, ItemStack>> leftovers = new ArrayList<>();

        for (KeyValue<WorldCoordinate, ItemStack> drop : drops.snapshot()) {
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

    private static ItemStack tryFillInventory(Inventory inventory, ItemStack stack) {
        // 合并到已有的相同物品槽
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (ItemStack.isSame(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
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
