package xin.vanilla.aotake.data.world;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.entity.PartEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.Coordinate;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.enums.EnumOverflowMode;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CollectionUtils;
import xin.vanilla.aotake.util.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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
    private List<KeyValue<Coordinate, ItemStack>> dropList = new ArrayList<>();
    /**
     * 掉落物统计
     */
    private List<KeyValue<Coordinate, KeyValue<Long, String>>> dropCount = new ArrayList<>();

    public WorldTrashData() {
    }

    public static WorldTrashData load(CompoundTag nbt, HolderLookup.Provider provider) {
        WorldTrashData data = new WorldTrashData();
        data.dropList = new ArrayList<>();
        ListTag dropListTag = nbt.getList("dropList", 10);
        List<KeyValue<Coordinate, ItemStack>> drops = new ArrayList<>();
        for (int i = 0; i < dropListTag.size(); i++) {
            CompoundTag drop = dropListTag.getCompound(i);
            ItemStack item = ItemStack.CODEC.decode(NbtOps.INSTANCE, drop.getCompound("item")).result().orElse(new Pair<>(null, null)).getFirst();
            drops.add(new KeyValue<>(
                    Coordinate.readFromNBT(drop.getCompound("coordinate"))
                    , item
            ));
        }
        data.setDrops(drops);

        data.dropCount = new ArrayList<>();
        ListTag dropCountNBT = nbt.getList("dropCount", 10);
        List<KeyValue<Coordinate, KeyValue<Long, String>>> dropCounts = new ArrayList<>();
        for (int i = 0; i < dropCountNBT.size(); i++) {
            CompoundTag drop = dropCountNBT.getCompound(i);
            dropCounts.add(new KeyValue<>(
                    Coordinate.readFromNBT(drop.getCompound("coordinate"))
                    , new KeyValue<>(drop.getLong("count"), drop.getString("name"))
            ));
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
            CompoundTag dropTag = new CompoundTag();
            dropTag.put("item", ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, drop.getValue()).result().orElse(new CompoundTag()));
            dropTag.put("coordinate", drop.getKey().writeToNBT());
            dropsNBT.add(dropTag);
        }
        nbt.put("dropList", dropsNBT);

        ListTag dropCountNBT = new ListTag();
        for (KeyValue<Coordinate, KeyValue<Long, String>> drop : this.getDropCount()) {
            CompoundTag dropTag = new CompoundTag();
            dropTag.putLong("count", drop.getValue().getKey());
            dropTag.putString("name", drop.getValue().getValue());
            dropTag.put("coordinate", drop.getKey().writeToNBT());
            dropCountNBT.add(dropTag);
        }
        nbt.put("dropCount", dropCountNBT);

        ListTag inventoryNBT = new ListTag();
        for (SimpleContainer inventory : this.getInventoryList()) {
            inventoryNBT.add(inventory.createTag(provider));
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
            AotakeUtils.removeEntity((ServerLevel) entity.level(), entity, false);
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
                Item it = AotakeUtils.deserializeItem(randomItem);
                if (it != null) {
                    item = new ItemStack(it);
                    CompoundTag customData = new CompoundTag();
                    CompoundTag aotake = new CompoundTag();
                    aotake.putBoolean("byPlayer", false);
                    aotake.put("entity", entity.serializeNBT());
                    customData.put(AotakeSweep.MODID, aotake);
                    item.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

                    result.setRecycledEntityCount(1);
                } else {
                    item = null;
                }
            }
            // 清理实体
            else {
                item = null;
            }
            result.setEntityCount(1);
            AotakeUtils.removeEntity((ServerLevel) entity.level(), entity, item != null);
        } else {
            typeKey = AotakeUtils.getEntityTypeRegistryName(entity);
            item = null;
            AotakeUtils.removeEntity((ServerLevel) entity.level(), entity, false);
            result.setEntityCount(1);
        }
        // 统计项
        this.dropCount.add(new KeyValue<>(coordinate, new KeyValue<>(System.currentTimeMillis(), typeKey)));

        // 回收物品
        if (item != null) {
            // 自清洁
            if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_DELETE.name())) {
                SimpleContainer inventory = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                IntStream.range(0, inventory.getContainerSize())
                        .filter(i -> !inventory.getItem(i).isEmpty())
                        .findAny()
                        .ifPresent(i -> inventory.setItem(i, ItemStack.EMPTY));
            }

            SimpleContainer box = this.inventoryList.stream()
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
                switch (EnumOverflowMode.valueOf(ServerConfig.DUSTBIN_OVERFLOW_MODE.get())) {
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
                        SimpleContainer inventory = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
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
        List<KeyValue<Coordinate, ItemStack>> drops = get().getDropList();
        List<KeyValue<Coordinate, ItemStack>> toAdd = drops.stream()
                .filter(kv -> inventory.canAddItem(kv.getValue()))
                .toList();

        toAdd.forEach(kv -> {
            drops.remove(kv);
            inventory.addItem(kv.getValue());
        });

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

}
