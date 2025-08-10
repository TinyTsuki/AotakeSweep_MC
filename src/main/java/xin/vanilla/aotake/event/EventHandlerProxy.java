package xin.vanilla.aotake.event;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.Coordinate;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.player.IPlayerSweepData;
import xin.vanilla.aotake.data.player.PlayerSweepDataCapability;
import xin.vanilla.aotake.data.player.PlayerSweepDataProvider;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.util.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EventHandlerProxy {
    private static final Logger LOGGER = LogManager.getLogger();

    @Getter
    @Setter
    private static long nextSweepTime = System.currentTimeMillis() - 1;
    private static long lastSelfCleanTime = System.currentTimeMillis();
    private static long lastSaveConfTime = System.currentTimeMillis();
    private static long lastReadConfTime = System.currentTimeMillis();
    private static long lastChunkCheckTime = System.currentTimeMillis();
    private static final AtomicBoolean chunkSweepLock = new AtomicBoolean(false);


    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || AotakeSweep.isDisable()) return;
        MinecraftServer server = AotakeSweep.getServerInstance().key();
        if (server == null || !server.isRunning()) return;

        long now = System.currentTimeMillis();
        long countdown = nextSweepTime - now;

        // 扫地前提示
        String warnKey = String.valueOf(countdown / 1000);
        if (AotakeUtils.hasWarning(warnKey)) {
            AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
                    .forEach(player -> AotakeUtils.sendActionBarMessage(player
                            , AotakeUtils.getWarningMessage(warnKey, AotakeUtils.getPlayerLanguage(player), null)
                    ));
        }

        // 扫地
        if (countdown <= 0) {
            nextSweepTime = now + ServerConfig.SWEEP_INTERVAL.get();
            LOGGER.debug("Scheduled sweep will start");
            AotakeScheduler.schedule(server, 1, AotakeUtils::sweep);
        }

        // 自清洁
        if (ServerConfig.SELF_CLEAN_INTERVAL.get() <= now - lastSelfCleanTime) {
            lastSelfCleanTime = now;
            WorldTrashData worldTrashData = WorldTrashData.get();
            // 清空
            if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SCHEDULED_CLEAR.name())) {
                worldTrashData.getDropList().clear();
                worldTrashData.getInventoryList().forEach(SimpleContainer::clearContent);
            }
            // 随机删除
            else if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SCHEDULED_DELETE.name())) {
                if (AotakeSweep.RANDOM.nextBoolean()) {
                    ConcurrentShuffleList<KeyValue<Coordinate, ItemStack>> dropList = worldTrashData.getDropList();
                    dropList.removeRandom();
                } else {
                    List<SimpleContainer> inventories = worldTrashData.getInventoryList();
                    SimpleContainer inventory = inventories.get(AotakeSweep.RANDOM.nextInt(inventories.size()));
                    IntStream.range(0, inventory.getContainerSize())
                            .filter(i -> !inventory.getItem(i).isEmpty())
                            .findAny()
                            .ifPresent(i -> inventory.setItem(i, ItemStack.EMPTY));
                }
            }
        }

        // 检查区块实体数量
        if (ServerConfig.CHUNK_CHECK_INTERVAL.get() > 0
                && !chunkSweepLock.get()
                && ServerConfig.CHUNK_CHECK_INTERVAL.get() <= now - lastChunkCheckTime
        ) {
            chunkSweepLock.set(true);
            lastChunkCheckTime = now;
            try {
                List<Map.Entry<String, List<Entity>>> overcrowdedChunks = AotakeUtils.getAllEntitiesByFilter(null).stream()
                        .collect(Collectors.groupingBy(entity -> {
                            // 获取区块维度和坐标
                            String dimension = entity.level != null
                                    ? entity.level.dimension().location().toString()
                                    : "unknown";
                            int chunkX = entity.blockPosition().getX() >> 4;
                            int chunkZ = entity.blockPosition().getZ() >> 4;
                            return "<" + dimension + ">:" + chunkX + "," + chunkZ;
                        }))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().size() > ServerConfig.CHUNK_CHECK_LIMIT.get())
                        .toList();

                if (!overcrowdedChunks.isEmpty()) {
                    LOGGER.info("Chunk check info:\n{}", overcrowdedChunks.stream()
                            .map(entry -> entry.getKey() + " has " + entry.getValue().size())
                            .collect(Collectors.joining("\n")));

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        AotakeUtils.sendActionBarMessage(player,
                                Component.translatable(EnumI18nType.MESSAGE, "chunk_check_msg", overcrowdedChunks.get(0).getKey()));
                    }

                    AotakeScheduler.schedule(server, 25, () -> {
                        try {
                            LOGGER.debug("Starting chunk sweep");
                            List<Entity> entities = overcrowdedChunks.stream()
                                    .flatMap(entry -> entry.getValue().stream())
                                    .collect(Collectors.toList());
                            AotakeUtils.sweep(null, entities);
                        } catch (Exception e) {
                            LOGGER.error("Failed to sweep entities", e);
                        } finally {
                            LOGGER.debug("Chunk sweep finished");
                            chunkSweepLock.set(false);
                        }
                    });
                } else {
                    chunkSweepLock.set(false);
                }
            } catch (Exception e) {
                chunkSweepLock.set(false);
                LOGGER.error("Failed to check chunk entities", e);
            }
        }

        // 保存通用配置
        if (now - lastSaveConfTime >= 10 * 1000) {
            lastSaveConfTime = now;
            CustomConfig.saveCustomConfig();
        }
        // 读取通用配置
        else if (now - lastReadConfTime >= 2 * 60 * 1000) {
            lastReadConfTime = now;
            CustomConfig.loadCustomConfig(true);
        }

    }

    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isClientSide()) {
            EntitySweeper.flushPendingRemovals((ServerLevel) event.world);
        }
    }

    public static void registerCaps(RegisterCapabilitiesEvent event) {
        // 注册 PlayerDataCapability
        event.register(IPlayerSweepData.class);
    }

    public static void onAttachCapabilityEvent(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(AotakeSweep.createResource("player_sweep_data"), new PlayerSweepDataProvider());
        }
    }

    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.getPlayer() instanceof ServerPlayer) {
            ServerPlayer original = (ServerPlayer) event.getOriginal();
            ServerPlayer newPlayer = (ServerPlayer) event.getPlayer();
            original.revive();
            AotakeUtils.clonePlayerLanguage(original, newPlayer);
            LazyOptional<IPlayerSweepData> oldDataCap = original.getCapability(PlayerSweepDataCapability.PLAYER_DATA);
            LazyOptional<IPlayerSweepData> newDataCap = newPlayer.getCapability(PlayerSweepDataCapability.PLAYER_DATA);
            oldDataCap.ifPresent(oldData -> newDataCap.ifPresent(newData -> newData.copyFrom(oldData)));
        }
    }

    public static void onPlayerUseItem(PlayerInteractEvent.RightClickItem event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getPlayer() instanceof ServerPlayer) {
            CompoundTag tag = event.getItemStack().getTag();
            if (tag != null && tag.contains(AotakeSweep.MODID)) {
                CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
                if (aotake.isEmpty()) tag.remove(AotakeSweep.MODID);
                event.setResult(Event.Result.DENY);
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        }
    }

    public static void onRightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemStack original = event.getItemStack();
            releaseEntity(event, player, original, new Coordinate(event.getHitVec().getLocation().x(), event.getHitVec().getLocation().y(), event.getHitVec().getLocation().z()));
        }
    }

    /**
     * 释放实体
     */
    private static Entity releaseEntity(PlayerInteractEvent event, ServerPlayer player, ItemStack original, Coordinate coordinate) {
        ItemStack copy = original.copy();
        copy.setCount(1);

        CompoundTag tag = copy.getTag();
        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
            if (aotake.isEmpty()) {
                tag.remove(AotakeSweep.MODID);
            }
            //
            else {
                original.shrink(1);
                CompoundTag entityData = aotake.getCompound("entity");
                Entity entity = EntityType.loadEntityRecursive(entityData, player.getLevel(), e -> e);
                if (entity != null) {
                    // 释放实体
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    player.getLevel().addFreshEntity(entity);
                    // 恢复物品原来的名称
                    net.minecraft.network.chat.Component name = AotakeUtils.textComponentFromJson(aotake.getString("name"));
                    if (name != null) copy.setHoverName(name);
                    else copy.resetHoverName();
                    // 清空节点下nbt
                    tag.put(AotakeSweep.MODID, new CompoundTag());
                    if (!aotake.getBoolean("byPlayer")) {
                        copy.shrink(1);
                    }
                    player.addItem(copy);

                    AotakeUtils.sendActionBarMessage(player, Component.translatable(EnumI18nType.MESSAGE, "entity_released", entity.getDisplayName()));

                    event.setCanceled(true);
                    event.setResult(Event.Result.DENY);
                    event.setCancellationResult(InteractionResult.FAIL);

                    return entity;
                }
            }
        }
        return null;
    }

    public static void onRightEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemStack original = event.getItemStack();
            ItemStack copy = original.copy();
            copy.setCount(1);

            CompoundTag tag = copy.getTag();
            // 检查是否已包含实体
            Entity entity = event.getTarget();
            if (entity instanceof PartEntity) {
                entity = ((PartEntity<?>) entity).getParent();
            }
            if (tag != null && tag.contains(AotakeSweep.MODID)) {
                CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
                if (!aotake.isEmpty()) {
                    Coordinate coordinate = new Coordinate(entity.getX(), entity.getY(), entity.getZ());
                    Entity back = releaseEntity(event, player, original, coordinate);
                    if (back != null) {
                        // 让实体骑乘
                        back.startRiding(entity, true);
                        // 同步客户端状态
                        AotakeUtils.broadcastPacket(new ClientboundSetPassengersPacket(entity));
                        return;
                    }
                }
            }

            if (ServerConfig.ALLOW_CATCH_ENTITY.get()
                    && ServerConfig.CATCH_ITEM.get().stream().anyMatch(s -> s.equals(AotakeUtils.getItemRegistryName(original)))
                    && player.isCrouching()
                    && (tag == null || !tag.contains(AotakeSweep.MODID) || !tag.getCompound(AotakeSweep.MODID).contains("entity"))
            ) {
                original.shrink(1);

                tag = copy.getOrCreateTag();
                CompoundTag aotake = new CompoundTag();

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
                            part.remove(Entity.RemovalReason.KILLED);
                        }
                    }
                }
                player.getLevel().removeEntity(entity, true);

                AotakeUtils.sendActionBarMessage(player, Component.translatable(EnumI18nType.MESSAGE, "entity_caught"));

                event.setCanceled(true);
                event.setResult(Event.Result.DENY);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    public static void onPlayerUseItem(PlayerEvent event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getPlayer() == null) return;
        if (event.getPlayer().isCrouching() && ServerConfig.ALLOW_CATCH_ENTITY.get()) {
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
        if (AotakeSweep.isDisable()) return;
    }

    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (AotakeSweep.isDisable()) return;
    }

    /**
     * 玩家登出事件
     */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家退出服务器时移除mod安装状态
        if (event.getPlayer() instanceof ServerPlayer) {
            AotakeSweep.getCustomConfigStatus().remove(event.getPlayer().getStringUUID());
        }
    }
}
