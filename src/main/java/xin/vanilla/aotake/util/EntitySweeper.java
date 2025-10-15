package xin.vanilla.aotake.util;

import lombok.NonNull;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.*;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EntitySweeper {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Map<RegistryKey<World>, Queue<KeyValue<Entity, Boolean>>> pendingRemovals = new ConcurrentHashMap<>();

    private List<Inventory> inventoryList;
    private ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> dropList;
    private Queue<DropStatistics> dropCount;

    private final Set<Entity> entitiesToRemove = ConcurrentHashMap.newKeySet();


    private void init() {
        WorldTrashData worldTrashData = WorldTrashData.get();
        if (this.inventoryList == null) {
            this.inventoryList = worldTrashData.getInventoryList();
        }
        if (this.dropList == null) {
            this.dropList = worldTrashData.getDropList();
        }
        if (this.dropCount == null) {
            this.dropCount = worldTrashData.getDropCount();
        }
    }

    public SweepResult addDrops(@NonNull List<Entity> entities, SweepResult result) {
        if (result.getTotalBatch() == 0) LOGGER.debug("AddDrops started at {}", System.currentTimeMillis());
        this.init();

        if (CollectionUtils.isNotNullOrEmpty(entities) && entities.size() > ServerConfig.SWEEP_ENTITY_LIMIT.get()) {
            List<List<Entity>> lists = CollectionUtils.splitToCollections(entities, ServerConfig.SWEEP_ENTITY_LIMIT.get());
            result.setTotalBatch(lists.size());
            if (lists.size() > 1) {
                for (int i = 1; i < lists.size(); i++) {
                    List<Entity> entityList = lists.get(i);
                    AotakeScheduler.schedule(AotakeSweep.getServerInstance().key()
                            , ServerConfig.SWEEP_ENTITY_INTERVAL.get() * i
                            , () -> AotakeSweep.getEntitySweeper().addDrops(entityList, result)
                    );
                }
                entities = lists.get(0);
            }
        } else if (result.getTotalBatch() == 0) {
            result.setTotalBatch(1);
        }

        Set<Integer> seenEntities = new HashSet<>();
        for (Entity entity : entities) {
            int id = System.identityHashCode(entity);
            if (seenEntities.add(id)) {
                result.add(this.processDrop(entity));
            }
        }

        for (Entity entity : entitiesToRemove) {
            if (entity.isAlive() && entity.level instanceof ServerWorld) {
                scheduleRemoveEntity(entity, false);
            }
        }
        entitiesToRemove.clear();

        WorldTrashData.get().setDirty();

        result.incrementBatch();

        if (result.getBatch().get() == result.getTotalBatch()) {
            LOGGER.debug("AddDrops finished at {}", System.currentTimeMillis());

            List<ServerPlayerEntity> players = AotakeSweep.getServerInstance().key().getPlayerList().getPlayers();
            for (ServerPlayerEntity p : players) {
                String language = AotakeUtils.getPlayerLanguage(p);
                Component msg = AotakeUtils.getWarningMessage(result.isEmpty() ? "fail" : "success"
                        , language
                        , result);
                PlayerSweepData playerData = PlayerSweepData.getData(p);
                if (playerData.isShowSweepResult()) {
                    String openCom = "/" + AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN);
                    msg.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, openCom))
                            .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                    , Component.literal(openCom).toTextComponent())
                            );
                    AotakeUtils.sendMessage(p, Component.empty()
                            .append(msg)
                            .append(Component.literal("[x]")
                                    .setColor(EnumMCColor.RED.getColor())
                                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                            , Component.translatable(EnumI18nType.MESSAGE, "not_show_button")
                                            .toTextComponent(language))
                                    )
                                    .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                            , "/" + AotakeUtils.getCommandPrefix() + " config showSweepResult change")
                                    )
                            )
                    );
                } else {
                    AotakeUtils.sendActionBarMessage(p, msg);
                }
                if (playerData.isEnableWarningVoice()) {
                    String voice = AotakeUtils.getWarningVoice(result.isEmpty() ? "fail" : "success");
                    float volume = CommonConfig.SWEEP_WARNING_VOICE_VOLUME.get() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(p, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        } else {
            LOGGER.debug("AddDrops {}/{} at {}", result.getBatch().get(), result.getTotalBatch(), System.currentTimeMillis());
        }

        return result;
    }

    private SweepResult processDrop(@NonNull Entity original) {
        SweepResult result = new SweepResult();
        Coordinate coordinate = new Coordinate(original);
        Entity entity = (original instanceof PartEntity) ? ((PartEntity<?>) original).getParent() : original;

        String typeKey = (entity instanceof ItemEntity)
                ? AotakeUtils.getItemRegistryName(((ItemEntity) entity).getItem())
                : AotakeUtils.getEntityTypeRegistryName(entity);

        ItemStack itemToRecycle = null;

        // 处理掉落物
        if (entity instanceof ItemEntity) {
            ItemStack item = ((ItemEntity) entity).getItem();
            if (!ServerConfig.ITEM_REDLIST.get().contains(typeKey)) {
                itemToRecycle = item.copy();
                result.setItemCount(item.getCount());
            }
            // 延迟移除
            entitiesToRemove.add(entity);
        }
        // 处理其他实体
        else {
            // 回收实体
            if (!ServerConfig.CATCH_ITEM.get().isEmpty()
                    && ServerConfig.CATCH_ENTITY.get().contains(typeKey)
            ) {
                String randomItem = CollectionUtils.getRandomElement(ServerConfig.CATCH_ITEM.get());
                itemToRecycle = new ItemStack(AotakeUtils.deserializeItem(randomItem));
                CompoundNBT tag = itemToRecycle.getOrCreateTag();
                CompoundNBT aotake = new CompoundNBT();
                aotake.putBoolean("byPlayer", false);
                aotake.put("entity", entity.serializeNBT());
                tag.put(AotakeSweep.MODID, aotake);

                result.setRecycledEntityCount(1);
            }
            result.setEntityCount(1);
            entitiesToRemove.add(entity);
        }

        // 记录清理历史
        this.dropCount.add(new DropStatistics(coordinate
                , typeKey
                , System.currentTimeMillis()
                , result.getItemCount()
                , result.getEntityCount()
        ));

        // 处理回收物品
        if (itemToRecycle != null) {
            handleItemRecycling(coordinate, itemToRecycle, result);
        }

        return result;
    }

    private void handleItemRecycling(Coordinate coordinate, ItemStack item, SweepResult result) {
        // 自清洁模式
        if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SWEEP_DELETE.name())) {
            switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
                case VIRTUAL: {
                    selfCleanVirtualDustbin();
                }
                break;
                case BLOCK: {
                    selfCleanDustbinBlock();
                }
                break;
                default: {
                    selfCleanVirtualDustbin();
                    selfCleanDustbinBlock();
                }
            }
        }

        ItemStack remaining = item;
        int recycledCount = item.getCount();

        switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
            case VIRTUAL: {
                remaining = addItemToVirtualDustbin(remaining);
            }
            break;
            case BLOCK: {
                remaining = addItemToDustbinBlock(remaining);
            }
            break;
            case VIRTUAL_BLOCK: {
                remaining = addItemToVirtualDustbin(remaining);
                remaining = addItemToDustbinBlock(remaining);
            }
            break;
            case BLOCK_VIRTUAL: {
                remaining = addItemToDustbinBlock(remaining);
                remaining = addItemToVirtualDustbin(remaining);
            }
            break;
        }

        // 剩余部分进行溢出处理
        if (!remaining.isEmpty()) {
            recycledCount = recycledCount - remaining.getCount();
            handleOverflow(coordinate, remaining, result);
        }

        result.setRecycledItemCount(recycledCount);
    }

    private void selfCleanVirtualDustbin() {
        Inventory inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
        IntStream.range(0, inv.getContainerSize())
                .filter(i -> !inv.getItem(i).isEmpty())
                .findAny()
                .ifPresent(i -> inv.setItem(i, ItemStack.EMPTY));
    }

    private void selfCleanDustbinBlock() {
        for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
            Coordinate dustbinPos = Coordinate.fromSimpleString(pos);
            IItemHandler handler = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (handler != null) {
                IntStream.range(0, handler.getSlots())
                        .filter(i -> !handler.getStackInSlot(i).isEmpty())
                        .findAny()
                        .ifPresent(i -> handler.extractItem(i, handler.getSlotLimit(i), false));
            }
        }
    }

    private ItemStack addItemToVirtualDustbin(ItemStack item) {
        ItemStack remaining = item;
        for (Inventory inv : this.inventoryList) {
            if (remaining.isEmpty()) break;

            if (inv.canAddItem(remaining)) {
                List<ItemStack> remainingList = new ArrayList<>();
                List<ItemStack> itemStackList = splitItemStack(remaining, inv.getMaxStackSize());
                int splits = itemStackList.size();
                for (ItemStack itemStack : itemStackList) {
                    ItemStack leftover = inv.addItem(itemStack);
                    remainingList.add(leftover);
                }
                if (splits > 0) remaining = mergeItemStack(remainingList);
            }
        }
        return remaining;
    }

    private ItemStack addItemToDustbinBlock(ItemStack item) {
        ItemStack remaining = item;
        for (String pos : ServerConfig.DUSTBIN_BLOCK_POSITIONS.get()) {
            Coordinate dustbinPos = Coordinate.fromSimpleString(pos);
            IItemHandler handler = AotakeUtils.getBlockItemHandler(dustbinPos);
            if (handler != null) {
                int invMax = IntStream.range(0, handler.getSlots())
                        .map(handler::getSlotLimit)
                        .filter(i -> i > 0)
                        .min().orElse(64);
                List<ItemStack> remainingList = new ArrayList<>();
                List<ItemStack> itemStackList = splitItemStack(remaining, invMax);
                int splits = itemStackList.size();
                for (ItemStack itemStack : itemStackList) {
                    ItemStack leftover = AotakeUtils.addItemToBlock(itemStack, dustbinPos);
                    remainingList.add(leftover);
                }
                if (splits > 0) remaining = mergeItemStack(remainingList);
            }
        }
        return remaining;
    }

    private void handleOverflow(Coordinate coordinate, ItemStack item, SweepResult result) {
        EnumOverflowMode mode = EnumOverflowMode.valueOf(ServerConfig.DUSTBIN_OVERFLOW_MODE.get());

        switch (mode) {
            case KEEP: {
                // 多余部分移除
                if (dropList.size() < CommonConfig.CACHE_LIMIT.get()) {
                    this.dropList.add(new KeyValue<>(coordinate, item.copy()));
                }
            }
            break;
            case REPLACE: {
                switch (EnumDustbinMode.valueOfOrDefault(ServerConfig.DUSTBIN_MODE.get())) {
                    case VIRTUAL:
                    case VIRTUAL_BLOCK: {
                        Inventory inv = this.inventoryList.get(AotakeSweep.RANDOM.nextInt(this.inventoryList.size()));
                        int slot = AotakeSweep.RANDOM.nextInt(inv.getContainerSize());
                        inv.setItem(slot, item.copy());
                    }
                    break;
                    case BLOCK:
                    case BLOCK_VIRTUAL: {
                        String pos = CollectionUtils.getRandomElement(ServerConfig.DUSTBIN_BLOCK_POSITIONS.get());
                        Coordinate dustbinPos = Coordinate.fromSimpleString(pos);
                        IItemHandler handler = AotakeUtils.getBlockItemHandler(dustbinPos);
                        if (handler != null) {
                            int slot = AotakeSweep.RANDOM.nextInt(handler.getSlots());
                            handler.extractItem(slot, handler.getSlotLimit(slot), false);
                            handler.insertItem(slot, item.copy(), false);
                        }
                    }
                    break;
                }
            }
            break;
            case REMOVE:
            default:
                // 丢弃
                break;
        }

        long baseCount = result.getItemCount() > 0 ? result.getItemCount() : result.getEntityCount();
        result.setRecycledItemCount(Math.max(result.getRecycledItemCount(), baseCount));
    }

    public static void scheduleRemoveEntity(Entity entity, boolean keepData) {
        if (!(entity.level instanceof ServerWorld)) return;
        RegistryKey<World> dimensionKey = entity.level.dimension();

        if (entity instanceof PartEntity) {
            entity = ((PartEntity<?>) entity).getParent();
        }
        if (entity.isMultipartEntity()) {
            PartEntity<?>[] parts = entity.getParts();
            if (CollectionUtils.isNotNullOrEmpty(parts)) {
                for (PartEntity<?> part : parts) {
                    pendingRemovals
                            .computeIfAbsent(dimensionKey, k -> new ConcurrentLinkedQueue<>())
                            .add(new KeyValue<>(part, keepData));
                }
            }
        } else {
            pendingRemovals
                    .computeIfAbsent(dimensionKey, k -> new ConcurrentLinkedQueue<>())
                    .add(new KeyValue<>(entity, keepData));
        }
    }

    public static void flushPendingRemovals(ServerWorld world) {
        Queue<KeyValue<Entity, Boolean>> queue = pendingRemovals.get(world.dimension());
        if (queue == null) return;

        KeyValue<Entity, Boolean> keyValue;
        while ((keyValue = queue.poll()) != null) {
            if (keyValue.getKey().isAlive()) {
                world.removeEntity(keyValue.getKey(), keyValue.getValue());
            }
        }
    }

    /**
     * 按 invMax 拆分 ItemStack
     *
     * @param stack  原始物品栈
     * @param invMax 每个子栈最大数量
     */
    private static List<ItemStack> splitItemStack(ItemStack stack, int invMax) {
        if (stack == null || stack.isEmpty() || invMax <= 0) {
            return new ArrayList<>();
        }
        int count = Math.min(invMax, stack.getMaxStackSize());

        int total = stack.getCount();
        int parts = (int) Math.ceil((double) total / count);

        return IntStream.range(0, parts)
                .mapToObj(i -> {
                    int splitSize = Math.min(count, total - i * count);
                    ItemStack s = stack.copy();
                    s.setCount(splitSize);
                    return s;
                })
                .collect(Collectors.toList());
    }

    /**
     * 合并多个 ItemStack
     *
     * @param stacks 要合并的物品栈(必须是相同的物品)
     */
    private static ItemStack mergeItemStack(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack base = stacks.get(0).copy();
        if (base.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int totalCount = stacks.stream()
                // .filter(s -> s != null && !s.isEmpty() && ItemStack.isSame(base, s))
                .mapToInt(ItemStack::getCount)
                .sum();

        base.setCount(totalCount);
        return base;
    }
}
