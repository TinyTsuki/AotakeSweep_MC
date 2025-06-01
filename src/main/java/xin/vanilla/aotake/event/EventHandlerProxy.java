package xin.vanilla.aotake.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.play.server.SSetPassengersPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.Event;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.Coordinate;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.player.IPlayerSweepData;
import xin.vanilla.aotake.data.player.PlayerSweepDataCapability;
import xin.vanilla.aotake.data.player.PlayerSweepDataProvider;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.CollectionUtils;
import xin.vanilla.aotake.util.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.IntStream;

public class EventHandlerProxy {

    private static long lastSweepTime = System.currentTimeMillis();
    private static long lastSelfCleanTime = System.currentTimeMillis();
    private static long lastSaveConfTime = System.currentTimeMillis();
    private static long lastReadConfTime = System.currentTimeMillis();
    private static Queue<Integer> warnQueue;

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && AotakeSweep.getServerInstance() != null
                && AotakeSweep.getServerInstance().isRunning()
        ) {
            long swept = System.currentTimeMillis() - lastSweepTime;
            long countdown = ServerConfig.SWEEP_INTERVAL.get() - swept;

            // 扫地前提示
            if (warnQueue == null) {
                List<Integer> list = new ArrayList<>(CommonConfig.SWEEP_WARNING_SECOND.get());
                // 从大到小排序
                list.sort((a, b) -> Integer.compare(b, a));
                warnQueue = new ArrayDeque<>(list);
            }
            if (warnQueue.peek() != null && warnQueue.peek() < 0) warnQueue.add(warnQueue.poll());
            if (warnQueue.peek() != null && countdown / 1000 == warnQueue.peek()) {
                // 将头部元素放至尾部
                int sec = warnQueue.poll();
                warnQueue.add(sec);

                if (sec > 0) {
                    AotakeSweep.getServerInstance()
                            .getPlayerList()
                            .getPlayers()
                            .forEach(player -> AotakeUtils.sendActionBarMessage(player
                                    , AotakeUtils.getWarningMessage(sec, AotakeUtils.getPlayerLanguage(player), null)
                            ));
                }
            }

            // 扫地
            if (ServerConfig.SWEEP_INTERVAL.get() <= swept) {
                lastSweepTime = System.currentTimeMillis();
                AotakeUtils.sweep();
            }

            // 自清洁
            if (ServerConfig.SELF_CLEAN_INTERVAL.get() <= System.currentTimeMillis() - lastSelfCleanTime) {
                lastSelfCleanTime = System.currentTimeMillis();
                WorldTrashData worldTrashData = WorldTrashData.get();
                // 清空
                if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SCHEDULED_CLEAR)) {
                    worldTrashData.getDropList().clear();
                    worldTrashData.getInventoryList().forEach(Inventory::clearContent);
                }
                // 随机删除
                else if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SCHEDULED_DELETE)) {
                    if (AotakeSweep.RANDOM.nextBoolean()) {
                        List<KeyValue<Coordinate, ItemStack>> dropList = worldTrashData.getDropList();
                        dropList.remove(AotakeSweep.RANDOM.nextInt(dropList.size()));
                    } else {
                        List<Inventory> inventories = worldTrashData.getInventoryList();
                        Inventory inventory = inventories.get(AotakeSweep.RANDOM.nextInt(inventories.size()));
                        IntStream.range(0, inventory.getContainerSize())
                                .filter(i -> !inventory.getItem(i).isEmpty())
                                .findAny()
                                .ifPresent(i -> inventory.setItem(i, ItemStack.EMPTY));
                    }
                }
            }

            // 保存通用配置
            if (System.currentTimeMillis() - lastSaveConfTime >= 10 * 1000) {
                lastSaveConfTime = System.currentTimeMillis();
                CustomConfig.saveCustomConfig();
            }
            // 读取通用配置
            else if (System.currentTimeMillis() - lastReadConfTime >= 2 * 60 * 1000) {
                lastReadConfTime = System.currentTimeMillis();
                CustomConfig.loadCustomConfig(true);
            }
        }
    }

    public static void onAttachCapabilityEvent(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
            event.addCapability(AotakeUtils.createResource("player_sweep_data"), new PlayerSweepDataProvider());
        }
    }

    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity original = (ServerPlayerEntity) event.getOriginal();
            ServerPlayerEntity newPlayer = (ServerPlayerEntity) event.getPlayer();
            original.revive();
            AotakeUtils.clonePlayerLanguage(original, newPlayer);
            LazyOptional<IPlayerSweepData> oldDataCap = original.getCapability(PlayerSweepDataCapability.PLAYER_DATA);
            LazyOptional<IPlayerSweepData> newDataCap = newPlayer.getCapability(PlayerSweepDataCapability.PLAYER_DATA);
            oldDataCap.ifPresent(oldData -> newDataCap.ifPresent(newData -> newData.copyFrom(oldData)));
        }
    }

    public static void onPlayerUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            CompoundNBT tag = event.getItemStack().getTag();
            if (tag != null && tag.contains(AotakeSweep.MODID)) {
                CompoundNBT aotake = tag.getCompound(AotakeSweep.MODID);
                if (aotake.isEmpty()) tag.remove(AotakeSweep.MODID);
                event.setResult(Event.Result.DENY);
                event.setCancellationResult(ActionResultType.FAIL);
                event.setCanceled(true);
            }
        }
    }

    public static void onRightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            ItemStack original = event.getItemStack();
            releaseEntity(event, player, original, new Coordinate(event.getHitVec().getLocation().x(), event.getHitVec().getLocation().y(), event.getHitVec().getLocation().z()));
        }
    }

    /**
     * 释放实体
     */
    private static Entity releaseEntity(PlayerInteractEvent event, ServerPlayerEntity player, ItemStack original, Coordinate coordinate) {
        ItemStack copy = original.copy();
        copy.setCount(1);

        CompoundNBT tag = copy.getTag();
        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundNBT aotake = tag.getCompound(AotakeSweep.MODID);
            if (aotake.isEmpty()) {
                tag.remove(AotakeSweep.MODID);
            }
            //
            else {
                original.shrink(1);
                CompoundNBT entityData = aotake.getCompound("entity");
                Entity entity = EntityType.loadEntityRecursive(entityData, player.getLevel(), e -> e);
                if (entity != null) {
                    // 释放实体
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    player.getLevel().addFreshEntity(entity);
                    // 恢复物品原来的名称
                    ITextComponent name = AotakeUtils.textComponentFromJson(aotake.getString("name"));
                    if (name != null) copy.setHoverName(name);
                    else copy.resetHoverName();
                    // 清空节点下nbt
                    tag.put(AotakeSweep.MODID, new CompoundNBT());
                    if (!aotake.getBoolean("byPlayer")) {
                        copy.shrink(1);
                    }
                    player.addItem(copy);

                    player.displayClientMessage(new StringTextComponent("实体已释放！"), true);

                    event.setCanceled(true);
                    event.setResult(Event.Result.DENY);
                    event.setCancellationResult(ActionResultType.FAIL);

                    return entity;
                }
            }
        }
        return null;
    }

    public static void onRightEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            ItemStack original = event.getItemStack();
            ItemStack copy = original.copy();
            copy.setCount(1);

            CompoundNBT tag = copy.getTag();
            // 检查是否已包含实体
            Entity entity = event.getTarget();
            if (entity instanceof PartEntity) {
                entity = ((PartEntity<?>) entity).getParent();
            }
            if (tag != null && tag.contains(AotakeSweep.MODID)) {
                CompoundNBT aotake = tag.getCompound(AotakeSweep.MODID);
                if (!aotake.isEmpty()) {
                    Coordinate coordinate = new Coordinate(entity.getX(), entity.getY(), entity.getZ());
                    Entity back = releaseEntity(event, player, original, coordinate);
                    if (back != null) {
                        // 让实体骑乘
                        back.startRiding(entity, true);
                        // 同步客户端状态
                        AotakeUtils.broadcastPacket(new SSetPassengersPacket(entity));
                        return;
                    }
                }
            }

            if (ServerConfig.ALLOW_CATCH_ITEM.get()
                    && ServerConfig.CATCH_ITEM.get().stream().anyMatch(s -> s.equals(AotakeUtils.getItemRegistryName(original)))
                    && player.isCrouching()
                    && (tag == null || !tag.contains(AotakeSweep.MODID) || !tag.getCompound(AotakeSweep.MODID).contains("entity"))
            ) {
                original.shrink(1);

                tag = copy.getOrCreateTag();
                CompoundNBT aotake = new CompoundNBT();

                aotake.putBoolean("byPlayer", true);
                aotake.put("entity", entity.serializeNBT());
                aotake.putString("name", AotakeUtils.getItemCustomNameJson(copy));
                tag.put(AotakeSweep.MODID, aotake);
                copy.setHoverName(Component.literal(String.format("%s%s", entity.getDisplayName().getString(), copy.getHoverName().getString())).toChatComponent());
                player.addItem(copy);

                if (entity.isMultipartEntity()) {
                    PartEntity<?>[] parts = entity.getParts();
                    if (CollectionUtils.isNotNullOrEmpty(parts)) {
                        for (PartEntity<?> part : parts) {
                            part.remove(true);
                        }
                    }
                }
                player.getLevel().removeEntity(entity, true);
                // entity.remove(true);
                player.displayClientMessage(new StringTextComponent("实体已捕获！"), true);

                event.setCanceled(true);
                event.setResult(Event.Result.DENY);
                event.setCancellationResult(ActionResultType.FAIL);
            }
        }
    }

    public static void onPlayerUseItem(PlayerEvent event) {
        if (event.getPlayer() == null) return;
        if (event.getPlayer().isCrouching() && ServerConfig.ALLOW_CATCH_ITEM.get()) {
            ItemStack item;
            // 桶装牛奶事件
            if (event instanceof FillBucketEvent) {
                item = ((FillBucketEvent) event).getEmptyBucket();
            }
            // 使用弓箭事件
            else if (event instanceof ArrowNockEvent) {
                item = ((ArrowNockEvent) event).getBow();
            }
            // 使用骨粉事件
            else if (event instanceof BonemealEvent) {
                item = ((BonemealEvent) event).getStack();
            }
            // 使用铲子
            else if (event instanceof UseHoeEvent) {
                item = event.getPlayer().getUseItem();
            }
            // 其他
            else {
                item = null;
            }

            if (item != null && ServerConfig.CATCH_ITEM.get().stream()
                    .anyMatch(s -> s.equals(AotakeUtils.getItemRegistryName(item)))
            ) {
                event.setCanceled(true);
                event.setResult(Event.Result.DENY);
            }
        }

    }

    public static void onContainerOpen(PlayerContainerEvent.Open event) {
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
    }

}
