package xin.vanilla.aotake.event;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.network.packet.GhostCameraToClient;
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@SuppressWarnings("resource")
public class EventHandlerProxy {
    private static final Logger LOGGER = LogManager.getLogger();

    @Getter
    @Setter
    private static long nextSweepTime = System.currentTimeMillis() - 1;
    private static long lastSelfCleanTime = System.currentTimeMillis();
    private static long lastSaveConfTime = System.currentTimeMillis();
    private static long lastReadConfTime = System.currentTimeMillis();
    private static long lastChunkCheckTime = System.currentTimeMillis();
    private static long lastVoiceTime = System.currentTimeMillis();
    private static final AtomicBoolean chunkSweepLock = new AtomicBoolean(false);
    private static final Map<String, Long> lastCatchTick = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastUseEntityTick = new ConcurrentHashMap<>();
    private static final Map<String, Long> suppressUseItemTick = new ConcurrentHashMap<>();
    private static final Map<String, GhostState> ghostStates = new ConcurrentHashMap<>();
    private static final int ghostScanInterval = 20;
    private static final int ghostClampInterval = 4;

    private static class GhostState {
        private String previousGameMode;
        private int lastTargetId = -1;
        private long lastScanTick = -1;
        private long lastClampTick = -1;
    }

    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (AotakeSweep.isDisable()) return;
        MinecraftServer server = event.getServer();
        if (server == null || !server.isRunning()) return;

        long now = System.currentTimeMillis();
        long countdown = nextSweepTime - now;
        long sweepInterval = ServerConfig.SWEEP_INTERVAL.get();

        // 扫地前提示
        String warnKey = String.valueOf(countdown / 1000);
        if (AotakeUtils.hasWarning(warnKey)) {
            for (ServerPlayer player : server
                    .getPlayerList()
                    .getPlayers()
            ) {
                // 给已安装mod玩家同步扫地倒计时
                if (AotakeSweep.getCustomConfigStatus().contains(AotakeUtils.getPlayerUUIDString(player))) {
                    AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
                }
                Component warningMessage = AotakeUtils.getWarningMessage(warnKey, AotakeUtils.getPlayerLanguage(player), null);
                if (warningMessage != null) {
                    AotakeUtils.sendActionBarMessage(player, warningMessage);
                }
            }
        }
        // 扫地前提示音效
        if (AotakeUtils.hasWarningVoice(warnKey) && lastVoiceTime + 1010 < now) {
            lastVoiceTime = now;
            for (ServerPlayer player : AotakeSweep.getServerInstance().key()
                    .getPlayerList()
                    .getPlayers()
            ) {
                if (PlayerSweepData.getData(player).isEnableWarningVoice()) {
                    String voice = AotakeUtils.getWarningVoice(warnKey);
                    float volume = CommonConfig.SWEEP_WARNING_VOICE_VOLUME.get() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(player, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        }

        // 扫地
        if (countdown <= 0 && sweepInterval > 0) {
            nextSweepTime = now + sweepInterval;
            AotakeScheduler.schedule(server, 1, AotakeUtils::sweep);
            // 给已安装mod玩家同步扫地倒计时
            for (String uuid : AotakeSweep.getCustomConfigStatus()) {
                ServerPlayer player = AotakeUtils.getPlayerByUUID(uuid);
                if (player != null) {
                    AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
                }
            }
        }

        // 自清洁
        if (ServerConfig.SELF_CLEAN_INTERVAL.get() <= now - lastSelfCleanTime) {
            lastSelfCleanTime = now;
            WorldTrashData worldTrashData = WorldTrashData.get();
            List<SimpleContainer> inventories = worldTrashData.getInventoryList();
            // 清空
            if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SCHEDULED_CLEAR.name())) {
                worldTrashData.getDropList().clear();
                if (CollectionUtils.isNotNullOrEmpty(inventories)) inventories.forEach(SimpleContainer::clearContent);
                WorldTrashData.get().setDirty();
            }
            // 随机删除
            else if (ServerConfig.SELF_CLEAN_MODE.get().contains(EnumSelfCleanMode.SCHEDULED_DELETE.name())) {
                if (AotakeSweep.RANDOM.nextBoolean()) {
                    ConcurrentShuffleList<KeyValue<WorldCoordinate, ItemStack>> dropList = worldTrashData.getDropList();
                    dropList.removeRandom();
                } else {
                    if (CollectionUtils.isNotNullOrEmpty(inventories)) {
                        SimpleContainer inventory = inventories.get(AotakeSweep.RANDOM.nextInt(inventories.size()));
                        IntStream.range(0, inventory.getContainerSize())
                                .filter(i -> !inventory.getItem(i).isEmpty())
                                .findAny()
                                .ifPresent(i -> inventory.setItem(i, ItemStack.EMPTY));
                    }
                }
                WorldTrashData.get().setDirty();
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
                long start = System.currentTimeMillis();
                boolean advanced = !EnumChunkCheckMode.DEFAULT.name().equals(ServerConfig.CHUNK_CHECK_MODE.get());
                Map<ChunkKey, List<Entity>> chunkEntities = new HashMap<>();
                for (Entity entity : AotakeUtils.getAllEntitiesByFilter(null, true)) {
                    String dimension = entity.level() != null
                            ? entity.level().dimension().location().toString()
                            : "unknown";
                    int chunkX = entity.blockPosition().getX() >> 4;
                    int chunkZ = entity.blockPosition().getZ() >> 4;
                    String entityType = advanced ? AotakeUtils.getEntityTypeRegistryName(entity) : null;
                    ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ, entityType);
                    chunkEntities.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
                }
                int limit = ServerConfig.CHUNK_CHECK_LIMIT.get();
                List<Map.Entry<ChunkKey, List<Entity>>> overcrowdedChunks = new ArrayList<>();
                for (Map.Entry<ChunkKey, List<Entity>> entry : chunkEntities.entrySet()) {
                    if (entry.getValue().size() > limit) {
                        overcrowdedChunks.add(entry);
                    }
                }
                long end = System.currentTimeMillis();

                if (!overcrowdedChunks.isEmpty()) {
                    LOGGER.debug("Chunk check started at {}", start);
                    LOGGER.debug("Chunk check finished at {}", end);
                    LOGGER.debug("Chunk check info:\n{}", overcrowdedChunks.stream()
                            .map(entry -> String.format("%s, Entities: %s"
                                    , formatChunkKey(entry.getKey(), advanced)
                                    , entry.getValue().size()))
                            .collect(Collectors.joining("\n")));

                    if (ServerConfig.CHUNK_CHECK_NOTICE.get()) {
                        Map.Entry<ChunkKey, List<Entity>> entityEntryList = overcrowdedChunks.getFirst();
                        Entity entity = entityEntryList.getValue().getFirst();
                        WorldCoordinate entityCoordinate = new WorldCoordinate(entity);
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            String language = AotakeUtils.getPlayerLanguage(player);

                            Component message = Component.translatable(EnumI18nType.MESSAGE,
                                    ServerConfig.CHUNK_CHECK_ONLY_NOTICE.get()
                                            ? "chunk_check_msg_no"
                                            : "chunk_check_msg_yes"
                                    , Component.literal(entityCoordinate.toChunkXZString())
                                            .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                                    , Component.literal(entityCoordinate.getDimensionResourceId()).toTextComponent())
                                            )
                            );
                            if (player.hasPermissions(1)
                                    && PlayerSweepData.getData(player).isShowSweepResult()
                            ) {
                                AotakeUtils.sendMessage(player, message
                                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                                , Component.translatable(EnumI18nType.MESSAGE, "chunk_check_msg_hover")
                                                .toTextComponent(language))
                                        )
                                        .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND
                                                , AotakeUtils.genTeleportCommand(entityCoordinate))
                                        )
                                        .append(Component.literal("[+]")
                                                .setColor(EnumMCColor.GREEN.getColor())
                                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                                        , Component.translatable(EnumI18nType.MESSAGE, "click_to_copy_detail")
                                                        .toTextComponent(language))
                                                )
                                                .setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD
                                                        , overcrowdedChunks.stream()
                                                        .map(entry -> String.format("%s, Entities: %s"
                                                                , formatChunkKey(entry.getKey(), advanced)
                                                                , entry.getValue().size()))
                                                        .collect(Collectors.joining("\n")))
                                                )
                                        )
                                        .append(Component.literal("[x]")
                                                .setColor(EnumMCColor.RED.getColor())
                                                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT
                                                        , Component.translatable(EnumI18nType.MESSAGE, "not_show_button")
                                                        .toTextComponent(language))
                                                )
                                                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND
                                                        , "/" + AotakeUtils.getCommandPrefix() + " config player showSweepResult change")
                                                )
                                        )
                                );
                            } else {
                                AotakeUtils.sendActionBarMessage(player, message);
                            }
                        }
                    }

                    // 将指定数量的实体移出列表实现不清理
                    overcrowdedChunks.forEach(entry -> {
                        List<Entity> entities = entry.getValue();
                        if (entities.isEmpty()) return;
                        entities.subList(0, (int) (ServerConfig.CHUNK_CHECK_RETAIN.get() * entities.size())).clear();
                    });

                    AotakeScheduler.schedule(server, 25, () -> {
                        try {
                            LOGGER.debug("Chunk sweep started at {}", System.currentTimeMillis());
                            List<Entity> entities = overcrowdedChunks.stream()
                                    .flatMap(entry -> entry.getValue().stream())
                                    .collect(Collectors.toList());
                            AotakeUtils.sweep(entities, true);
                        } catch (Exception e) {
                            LOGGER.error("Failed to sweep entities", e);
                        } finally {
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
        updateGhostTargets(server);
        clampGhostMovement(server);

    }

    public static void onWorldTick(TickEvent.LevelTickEvent.Post event) {
        if (!event.level.isClientSide()) {
            EntitySweeper.flushPendingRemovals((ServerLevel) event.level);
        }
    }

    private static String formatChunkKey(ChunkKey key, boolean advanced) {
        if (advanced) {
            return String.format("Dimension: %s, Chunk: %s %s, EntityType: %s", key.dimension(), key.chunkX(), key.chunkZ(), key.entityType());
        }
        return String.format("Dimension: %s, Chunk: %s %s", key.dimension(), key.chunkX(), key.chunkZ());
    }

    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            ServerPlayer original = (ServerPlayer) event.getOriginal();
            original.revive();
            AotakeUtils.clonePlayerLanguage(original, newPlayer);
        }
    }

    public static void onPlayerUseItem(PlayerInteractEvent.RightClickItem event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getItemStack();
            long tick = player.serverLevel().getGameTime();
            String uuid = player.getStringUUID();
            Long suppressTick = suppressUseItemTick.get(uuid);
            if (suppressTick != null && suppressTick == tick) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
                return;
            }
            if (AotakeUtils.hasAotakeTag(stack)) {
                CompoundTag aotake = AotakeUtils.getAotakeTag(stack);
                if (aotake.isEmpty()) {
                    AotakeUtils.clearItemTagEx(stack);
                    return;
                }
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        }
    }

    public static void onRightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack original = event.getItemStack();
            Entity released = releaseEntity(event, player, original, new WorldCoordinate(event.getHitVec().getLocation().x(), event.getHitVec().getLocation().y(), event.getHitVec().getLocation().z()));
            if (released != null) {
                suppressUseItemTick.put(player.getStringUUID(), player.serverLevel().getGameTime());
            }
        }
    }

    /**
     * 释放实体
     */
    private static Entity releaseEntity(PlayerInteractEvent event, ServerPlayer player, ItemStack original, WorldCoordinate coordinate) {
        ItemStack copy = original.copy();
        copy.setCount(1);

        if (AotakeUtils.hasAotakeTag(copy)) {
            CompoundTag aotake = AotakeUtils.getAotakeTag(copy);
            if (aotake.isEmpty()) {
                AotakeUtils.clearAotakeTag(copy);
                AotakeUtils.clearItemTag(copy);
                AotakeUtils.clearItemTagEx(original);
            } else {
                if (aotake.contains("player")) {
                    if (!AotakeUtils.hasCommandPermission(player, EnumCommandType.CATCH_PLAYER)) {
                        return null;
                    }
                    String playerId = aotake.getString("player");
                    ServerPlayer target = AotakeUtils.getPlayerByUUID(playerId);
                    if (target != null) {
                        ServerLevel level = AotakeUtils.getWorld(coordinate.getDimension());
                        if (level == null) {
                            level = player.serverLevel();
                        }
                        target.teleportTo(level, coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                        original.shrink(1);
                        net.minecraft.network.chat.Component name = AotakeUtils.textComponentFromJson(aotake.getString("name"));
                        if (name != null) {
                            copy.set(DataComponents.CUSTOM_NAME, name);
                        } else {
                            clearCustomName(copy);
                        }
                        AotakeUtils.clearItemTagEx(copy);
                        if (!aotake.getBoolean("byPlayer")) {
                            copy.shrink(1);
                        }
                        if (!copy.isEmpty()) {
                            player.addItem(copy);
                        }
                        stopGhost(target);
                        AotakeUtils.sendActionBarMessage(player, Component.translatable(EnumI18nType.MESSAGE, "entity_released", target.getDisplayName()));
                        if (event instanceof PlayerInteractEvent.EntityInteractSpecific eve) {
                            eve.setCanceled(true);
                            eve.setCancellationResult(InteractionResult.SUCCESS);
                        } else if (event instanceof PlayerInteractEvent.RightClickBlock eve) {
                            eve.setCanceled(true);
                            eve.setCancellationResult(InteractionResult.SUCCESS);
                        }
                        return target;
                    }
                } else {
                    CompoundTag entityData = aotake.getCompound("entity");
                    if (!entityData.contains("id") && aotake.contains("entityId")) {
                        entityData.putString("id", aotake.getString("entityId"));
                    }
                    AotakeUtils.sanitizeCapturedEntityTag(entityData);
                    ServerLevel level = player.serverLevel();
                    Entity entity = EntityType.loadEntityRecursive(entityData, level, e -> e);
                    if (entity != null) {
                        entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                        boolean spawned = level.addFreshEntity(entity);
                        if (!spawned) {
                            return null;
                        }
                        original.shrink(1);
                        String originalNameJson = aotake.getString("name");
                        net.minecraft.network.chat.Component name = AotakeUtils.textComponentFromJson(originalNameJson);
                        if (name != null) {
                            copy.set(DataComponents.CUSTOM_NAME, name);
                        } else {
                            clearCustomName(copy);
                        }
                        AotakeUtils.clearItemTagEx(copy);
                        if (!aotake.getBoolean("byPlayer")) {
                            copy.shrink(1);
                        }
                        if (!copy.isEmpty()) {
                            player.addItem(copy);
                        }
                        AotakeUtils.sendActionBarMessage(player, Component.translatable(EnumI18nType.MESSAGE, "entity_released", entity.getDisplayName()));
                        if (event instanceof PlayerInteractEvent.EntityInteractSpecific eve) {
                            eve.setCanceled(true);
                            eve.setCancellationResult(InteractionResult.SUCCESS);
                        } else if (event instanceof PlayerInteractEvent.RightClickBlock eve) {
                            eve.setCanceled(true);
                            eve.setCancellationResult(InteractionResult.SUCCESS);
                        }
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    public static void onRightEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            long tick = player.serverLevel().getGameTime();
            String uuid = player.getStringUUID();
            Long lastUseTick = lastUseEntityTick.get(uuid);
            if (lastUseTick != null && lastUseTick == tick) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            lastUseEntityTick.put(uuid, tick);
            ItemStack original = event.getItemStack();
            if (original.isEmpty()) return;
            ItemStack copy = original.copy();
            copy.setCount(1);

            // 检查是否已包含实体
            Entity entity = event.getTarget();
            if (entity instanceof PartEntity<?>) {
                entity = ((PartEntity<?>) entity).getParent();
            }
            if (AotakeUtils.hasAotakeTag(copy)) {
                CompoundTag aotake = AotakeUtils.getAotakeTag(copy);
                if (!aotake.isEmpty()) {
                    WorldCoordinate coordinate = new WorldCoordinate(entity.getX(), entity.getY(), entity.getZ());
                    Entity back = releaseEntity(event, player, original, coordinate);
                    if (back != null) {
                        if (back == entity) {
                            suppressUseItemTick.put(uuid, tick);
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }
                        if (entity.isPassenger() && entity.getVehicle() == back) {
                            suppressUseItemTick.put(uuid, tick);
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }
                        if (back.isPassenger() && back.getVehicle() == entity) {
                            suppressUseItemTick.put(uuid, tick);
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }
                        if (back.isPassenger()) {
                            back.stopRiding();
                        }
                        back.startRiding(entity, true);
                        AotakeUtils.broadcastPacket(new ClientboundSetPassengersPacket(entity));
                        suppressUseItemTick.put(uuid, tick);
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        return;
                    }
                }
            }

            boolean allowCatch = ServerConfig.ALLOW_CATCH_ENTITY.get();
            boolean isCatchItem = ServerConfig.CATCH_ITEM.get().stream().anyMatch(s -> s.equals(AotakeUtils.getItemRegistryName(original)));
            CompoundTag aotakeTag = AotakeUtils.getAotakeTag(copy);
            boolean hasEntityInTag = (!aotakeTag.isEmpty()
                    && (aotakeTag.contains("entity")
                    || aotakeTag.contains("player")));

            if (allowCatch && isCatchItem && player.isCrouching() && !hasEntityInTag) {
                Long lastTick = lastCatchTick.get(uuid);
                if (lastTick != null && lastTick == tick) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }
                if (entity instanceof Player && !AotakeUtils.hasCommandPermission(player, EnumCommandType.CATCH_PLAYER)) {
                    return;
                }
                original.shrink(1);

                CompoundTag aotake = new CompoundTag();
                aotake.putBoolean("byPlayer", true);
                if (entity instanceof Player targetPlayer) {
                    aotake.putString("player", targetPlayer.getStringUUID());
                } else {
                    if (entity.isPassenger()) {
                        entity.stopRiding();
                    }
                    CompoundTag entityTag = new CompoundTag();
                    entity.save(entityTag);
                    AotakeUtils.sanitizeCapturedEntityTag(entityTag);
                    aotake.put("entity", entityTag);
                    aotake.putString("entityId", AotakeUtils.getEntityTypeRegistryName(entity));
                }
                String originalNameText = copy.getHoverName().getString();
                String originalNameJson = AotakeUtils.getItemCustomNameJson(copy);
                aotake.putString("name", originalNameJson);
                AotakeUtils.setAotakeTag(copy, aotake);
                copy.set(DataComponents.CUSTOM_NAME, Component.literal(String.format("%s %s", entity.getDisplayName().getString(), originalNameText)).toChatComponent());
                player.addItem(copy);
                if (!(entity instanceof ServerPlayer targetPlayer)) {
                    AotakeUtils.removeEntity(entity, true);
                } else {
                    startGhost(targetPlayer, player);
                }
                lastCatchTick.put(uuid, tick);
                suppressUseItemTick.put(uuid, tick);
                AotakeUtils.sendActionBarMessage(player, Component.translatable(EnumI18nType.MESSAGE, "entity_caught"));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    public static void onPlayerUseItem(PlayerEvent event) {
        if (AotakeSweep.isDisable()) return;
        if (event.getEntity() == null) return;
        if (event.getEntity().isCrouching() && ServerConfig.ALLOW_CATCH_ENTITY.get()) {
            ItemStack item = switch (event) {
                case FillBucketEvent fillBucketEvent -> fillBucketEvent.getEmptyBucket();
                // 使用弓箭事件
                case ArrowNockEvent arrowNockEvent -> arrowNockEvent.getBow();
                // 使用骨粉事件
                case BonemealEvent bonemealEvent -> bonemealEvent.getStack();
                // 其他
                default -> null;
            };

            if (item != null && ServerConfig.CATCH_ITEM.get().stream()
                    .anyMatch(s -> s.equals(AotakeUtils.getItemRegistryName(item)))
            ) {
                event.setCanceled(true);
                event.setResult(Event.Result.DENY);
            }
        }

    }

    /**
     * 玩家登录事件
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (AotakeSweep.getCustomConfigStatus().contains(AotakeUtils.getPlayerUUIDString(player))) {
                AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
            }
        }
    }

    /**
     * 玩家登出事件
     */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 玩家退出服务器时移除mod安装状态
        if (event.getEntity() instanceof ServerPlayer) {
            AotakeSweep.getCustomConfigStatus().remove(event.getEntity().getStringUUID());
            ghostStates.remove(event.getEntity().getStringUUID());
        } else {
            AotakeSweep.getClientServerTime().setKey(0L).setValue(0L);
            AotakeSweep.getSweepTime().setKey(0L).setValue(0L);
        }
    }

    private static void startGhost(ServerPlayer target, ServerPlayer holder) {
        String uuid = target.getStringUUID();
        GhostState state = ghostStates.computeIfAbsent(uuid, k -> new GhostState());
        if (StringUtils.isNullOrEmptyEx(state.previousGameMode)) {
            GameType type = target.gameMode.getGameModeForPlayer();
            state.previousGameMode = type.getName();
        }
        if (StringUtils.isNullOrEmptyEx(state.previousGameMode)) {
            state.previousGameMode = GameType.SURVIVAL.getName();
        }
        AotakeUtils.executeCommandNoOutput(target, "gamemode spectator", 4);
        if (holder != null) {
            sendGhostCamera(target, holder.getId(), false);
            state.lastTargetId = holder.getId();
        } else {
            sendGhostCamera(target, -1, true);
        }
    }

    private static void stopGhost(ServerPlayer target) {
        String uuid = target.getStringUUID();
        GhostState state = ghostStates.remove(uuid);
        if (state != null && StringUtils.isNotNullOrEmpty(state.previousGameMode)) {
            AotakeUtils.executeCommandNoOutput(target, "gamemode " + state.previousGameMode, 4);
        }
        sendGhostCamera(target, -1, true);
    }

    private static void updateGhostTargets(MinecraftServer server) {
        if (ghostStates.isEmpty()) return;
        long tick = server.getTickCount();
        List<String> scanList = new java.util.ArrayList<>();
        for (Map.Entry<String, GhostState> entry : ghostStates.entrySet()) {
            String uuid = entry.getKey();
            GhostState state = entry.getValue();
            if (state.lastScanTick >= 0 && tick - state.lastScanTick < ghostScanInterval) {
                continue;
            }
            state.lastScanTick = tick;
            scanList.add(uuid);
        }
        if (scanList.isEmpty()) return;
        Map<String, Entity> targets = findGhostTargets(server, scanList);
        for (String uuid : scanList) {
            GhostState state = ghostStates.get(uuid);
            if (state == null) continue;
            ServerPlayer targetPlayer = AotakeUtils.getPlayerByUUID(uuid);
            if (targetPlayer == null) continue;
            Entity target = targets.get(uuid);
            if (target == null) {
                stopGhost(targetPlayer);
                continue;
            }
            int targetId = target.getId();
            if (targetId != state.lastTargetId) {
                state.lastTargetId = targetId;
                sendGhostCamera(targetPlayer, targetId, false);
            }
        }
    }

    private static Map<String, Entity> findGhostTargets(MinecraftServer server, List<String> playerUuids) {
        Map<String, Entity> targets = new java.util.HashMap<>();
        java.util.Set<String> uuidSet = new java.util.HashSet<>(playerUuids);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            collectCapturedTargetsFromPlayer(player, uuidSet, targets);
        }
        if (targets.size() == uuidSet.size()) return targets;
        for (ServerLevel level : server.getAllLevels()) {
            level.getEntities().getAll().forEach(entity -> {
                if (entity instanceof ItemEntity) {
                    String uuid = getCapturedPlayerUuid(((ItemEntity) entity).getItem());
                    if (uuid != null && uuidSet.contains(uuid)) {
                        targets.putIfAbsent(uuid, entity);
                    }
                    return;
                }
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    collectCapturedTargetsFromLiving((LivingEntity) entity, uuidSet, targets);
                }
            });
        }
        return targets;
    }

    private static void clearCustomName(ItemStack stack) {
        net.minecraft.network.chat.Component tag = stack.get(DataComponents.CUSTOM_NAME);
        if (tag == null) return;
        stack.remove(DataComponents.CUSTOM_NAME);
    }

    private static void sendGhostCamera(ServerPlayer player, int entityId, boolean reset) {
        AotakeUtils.sendPacketToPlayer(new GhostCameraToClient(entityId, reset), player);
    }

    private static void clampGhostMovement(MinecraftServer server) {
        if (ghostStates.isEmpty()) return;
        long tick = server.getTickCount();
        for (Map.Entry<String, GhostState> entry : ghostStates.entrySet()) {
            String uuid = entry.getKey();
            GhostState state = entry.getValue();
            if (state == null) continue;
            if (state.lastTargetId < 0) continue;
            if (state.lastClampTick >= 0 && tick - state.lastClampTick < ghostClampInterval) continue;
            state.lastClampTick = tick;
            ServerPlayer targetPlayer = AotakeUtils.getPlayerByUUID(uuid);
            if (targetPlayer == null) continue;
            Entity target = findEntityById(server, state.lastTargetId);
            if (target == null) continue;
            double desiredX = target.getX();
            double desiredY = target.getBoundingBox().maxY + 0.2;
            double desiredZ = target.getZ();
            Vec3 desired = new Vec3(desiredX, desiredY, desiredZ);
            float yaw = target.getYRot();
            float pitch = target.getXRot();
            Vec3 current = targetPlayer.position();
            boolean needTeleport =
                    current.distanceToSqr(desired) > 0.04
                            || Math.abs(targetPlayer.getYRot() - yaw) > 1.0f
                            || Math.abs(targetPlayer.getXRot() - pitch) > 1.0f;
            if (needTeleport) {
                ServerLevel level = (ServerLevel) target.level();
                targetPlayer.teleportTo(level, desired.x, desired.y, desired.z, yaw, pitch);
                targetPlayer.setDeltaMovement(Vec3.ZERO);
                targetPlayer.fallDistance = 0;
            }
        }
    }

    private static Entity findEntityById(MinecraftServer server, int entityId) {
        if (entityId < 0) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e != null) return e;
        }
        return null;
    }

    private static String getCapturedPlayerUuid(ItemStack stack) {
        if (!AotakeUtils.hasAotakeTag(stack)) return null;
        CompoundTag aotake = AotakeUtils.getAotakeTag(stack);
        if (!aotake.contains("player")) return null;
        String uuid = aotake.getString("player");
        return StringUtils.isNullOrEmptyEx(uuid) ? null : uuid;
    }

    private static void collectCapturedTargetsFromPlayer(ServerPlayer player, java.util.Set<String> uuidSet, Map<String, Entity> targets) {
        for (ItemStack stack : player.getInventory().items) {
            collectCapturedTarget(stack, uuidSet, targets, player);
        }
        for (ItemStack stack : player.getInventory().armor) {
            collectCapturedTarget(stack, uuidSet, targets, player);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            collectCapturedTarget(stack, uuidSet, targets, player);
        }
    }

    private static void collectCapturedTargetsFromLiving(LivingEntity living, java.util.Set<String> uuidSet, Map<String, Entity> targets) {
        collectCapturedTarget(living.getMainHandItem(), uuidSet, targets, living);
        collectCapturedTarget(living.getOffhandItem(), uuidSet, targets, living);
        for (ItemStack stack : living.getArmorSlots()) {
            collectCapturedTarget(stack, uuidSet, targets, living);
        }
    }

    private static void collectCapturedTarget(ItemStack stack, java.util.Set<String> uuidSet, Map<String, Entity> targets, Entity holder) {
        String uuid = getCapturedPlayerUuid(stack);
        if (uuid == null || !uuidSet.contains(uuid)) return;
        targets.putIfAbsent(uuid, holder);
    }
}
