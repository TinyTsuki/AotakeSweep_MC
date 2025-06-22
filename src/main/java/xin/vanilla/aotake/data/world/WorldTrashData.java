package xin.vanilla.aotake.data.world;

import lombok.Getter;
import lombok.NonNull;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
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
import net.minecraftforge.entity.PartEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.Coordinate;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CollectionUtils;
import xin.vanilla.aotake.util.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 世界垃圾数据
 */
@Getter
public class WorldTrashData extends WorldCapabilityData {
    private static final String DATA_NAME = "world_trash_data";

    private List<Inventory> inventoryList = new ArrayList<>();

    /**
     * 掉落物列表
     */
    private List<KeyValue<Coordinate, ItemStack>> dropList = new ArrayList<>();
    /**
     * 掉落物统计
     */
    private List<KeyValue<Coordinate, KeyValue<Long, String>>> dropCount = new ArrayList<>();

    public WorldTrashData() {
        super(DATA_NAME);
    }

    public void load(CompoundNBT nbt) {
        this.dropList = new ArrayList<>();
        ListNBT dropListNBT = nbt.getList("dropList", 10);
        List<KeyValue<Coordinate, ItemStack>> drops = new ArrayList<>();
        for (int i = 0; i < dropListNBT.size(); i++) {
            CompoundNBT drop = dropListNBT.getCompound(i);
            ItemStack item = ItemStack.of(drop.getCompound("item"));
            drops.add(new KeyValue<>(
                    Coordinate.readFromNBT(drop.getCompound("coordinate"))
                    , item
            ));
        }
        this.setDrops(drops);

        this.dropCount = new ArrayList<>();
        ListNBT dropCountNBT = nbt.getList("dropCount", 10);
        List<KeyValue<Coordinate, KeyValue<Long, String>>> dropCounts = new ArrayList<>();
        for (int i = 0; i < dropCountNBT.size(); i++) {
            CompoundNBT drop = dropCountNBT.getCompound(i);
            dropCounts.add(new KeyValue<>(
                    Coordinate.readFromNBT(drop.getCompound("coordinate"))
                    , new KeyValue<>(drop.getLong("count"), drop.getString("name"))
            ));
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
        ListNBT dropsNBT = new ListNBT();
        for (KeyValue<Coordinate, ItemStack> drop : this.getDropList()) {
            CompoundNBT dropTag = new CompoundNBT();
            dropTag.put("item", drop.getValue().serializeNBT());
            dropTag.put("coordinate", drop.getKey().writeToNBT());
            dropsNBT.add(dropTag);
        }
        nbt.put("dropList", dropsNBT);

        ListNBT dropCountNBT = new ListNBT();
        for (KeyValue<Coordinate, KeyValue<Long, String>> drop : this.getDropCount()) {
            CompoundNBT dropTag = new CompoundNBT();
            dropTag.putLong("count", drop.getValue().getKey());
            dropTag.putString("name", drop.getValue().getValue());
            dropTag.put("coordinate", drop.getKey().writeToNBT());
            dropCountNBT.add(dropTag);
        }
        nbt.put("dropCount", dropCountNBT);

        ListNBT inventoryNBT = new ListNBT();
        for (Inventory inventory : this.getInventoryList()) {
            inventoryNBT.add(inventory.createTag());
        }
        nbt.put("inventoryList", inventoryNBT);

        return nbt;
    }

    private void setDrops(List<KeyValue<Coordinate, ItemStack>> drops) {
        this.dropList = drops;
        super.setDirty();
    }

    private void setDropCount(List<KeyValue<Coordinate, KeyValue<Long, String>>> drops) {
        this.dropCount = drops;
        super.setDirty();
    }

    public SweepResult addDrops(@NonNull List<Entity> entities) {
        SweepResult result = new SweepResult();
        for (Entity entity : entities) {
            SweepResult added = this.addDrop(entity);
            result.add(added);
        }
        return result;
    }

    public SweepResult addDrop(@NonNull Entity entity) {
        SweepResult result = new SweepResult();

        ItemStack item;
        String typeKey;
        Coordinate coordinate = new Coordinate(entity);

        // 若为物品且不在红名单
        if (entity instanceof ItemEntity
                && !ServerConfig.ITEM_REDLIST.get().contains(AotakeUtils.getItemRegistryName(((ItemEntity) entity).getItem()))
        ) {
            item = ((ItemEntity) entity).getItem();
            typeKey = AotakeUtils.getItemRegistryName(item);
            result.setItemCount(item.getCount());
            AotakeUtils.removeEntity((ServerWorld) entity.level, entity, false);
        }
        // 若不为物品
        else if (!(entity instanceof ItemEntity)
                && !ServerConfig.CATCH_ITEM.get().isEmpty()
        ) {
            if (entity instanceof PartEntity) {
                entity = ((PartEntity<?>) entity).getParent();
            }

            typeKey = AotakeUtils.getEntityTypeRegistryName(entity);

            // 回收实体
            if (ServerConfig.CATCH_ENTITY.get().contains(typeKey)) {
                String randomItem = CollectionUtils.getRandomElement(ServerConfig.CATCH_ITEM.get());
                item = new ItemStack(AotakeUtils.deserializeItem(randomItem));
                CompoundNBT tag = item.getOrCreateTag();
                CompoundNBT aotake = new CompoundNBT();
                aotake.putBoolean("byPlayer", false);
                aotake.put("entity", entity.serializeNBT());
                tag.put(AotakeSweep.MODID, aotake);

                result.setRecycledEntityCount(1);
            }
            // 清理实体
            else {
                item = null;
            }
            result.setEntityCount(1);
            AotakeUtils.removeEntity((ServerWorld) entity.level, entity, item != null);
        } else {
            typeKey = AotakeUtils.getEntityTypeRegistryName(entity);
            item = null;
            AotakeUtils.removeEntity((ServerWorld) entity.level, entity, false);
            result.setEntityCount(1);
        }
        // 统计项
        this.dropCount.add(new KeyValue<>(coordinate, new KeyValue<>(System.currentTimeMillis(), typeKey)));

        // 回收物品
        if (item != null) {
            // 自清洁
            if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_DELETE.name())) {
                Inventory inventory = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                IntStream.range(0, inventory.getContainerSize())
                        .filter(i -> !inventory.getItem(i).isEmpty())
                        .findAny()
                        .ifPresent(i -> inventory.setItem(i, ItemStack.EMPTY));
            }

            Inventory box = this.inventoryList.stream()
                    .filter(inventory -> inventory.canAddItem(item))
                    .findFirst().orElse(null);
            // 回收物品
            if (box != null) {
                ItemStack itemStack = box.addItem(item);
                if (result.getItemCount() > 0) {
                    result.setRecycledItemCount(result.getItemCount() - item.getCount());
                } else if (result.getEntityCount() > 0) {
                    result.setRecycledItemCount(result.getEntityCount() - itemStack.getCount());
                }
            } else {
                switch (ServerConfig.DUSTBIN_OVERFLOW_MODE.get()) {
                    case KEEP: {
                        this.dropList.add(new KeyValue<>(coordinate, item));
                        if (result.getItemCount() > 0) {
                            result.setRecycledItemCount(result.getItemCount());
                        } else if (result.getEntityCount() > 0) {
                            result.setRecycledItemCount(result.getEntityCount());
                        }
                    }
                    break;
                    case REPLACE: {
                        Inventory inventory = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                        inventory.setItem(AotakeSweep.RANDOM.nextInt(inventory.getContainerSize()), item);
                        if (result.getItemCount() > 0) {
                            result.setRecycledItemCount(result.getItemCount());
                        } else if (result.getEntityCount() > 0) {
                            result.setRecycledItemCount(result.getEntityCount());
                        }
                    }
                    break;
                    case REMOVE:
                    default:
                        break;
                }
            }
            super.setDirty();
        }
        return result;
    }

    public static WorldTrashData get() {
        return get(AotakeSweep.getServerInstance().getAllLevels().iterator().next());
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
        List<KeyValue<Coordinate, ItemStack>> drops = get().getDropList();
        List<KeyValue<Coordinate, ItemStack>> toAdd = drops.stream()
                .filter(kv -> inventory.canAddItem(kv.getValue()))
                .collect(Collectors.toList());

        toAdd.forEach(kv -> {
            drops.remove(kv);
            inventory.addItem(kv.getValue());
        });

        return new INamedContainerProvider() {
            @NonNull
            @Override
            public ITextComponent getDisplayName() {
                return Component.translatable(EnumI18nType.KEY, "categories")
                        .setColor(EnumMCColor.DARK_GREEN.getColor())
                        .append(String.format("(%s/%s)", page, limit))
                        .toTextComponent(AotakeUtils.getPlayerLanguage(player));
            }

            @Override
            public Container createMenu(int id, @NonNull PlayerInventory playerInventory, @NonNull PlayerEntity p) {
                return ChestContainer.sixRows(id, playerInventory, inventory);
            }
        };
    }

}
