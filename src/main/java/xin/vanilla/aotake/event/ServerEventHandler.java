package xin.vanilla.aotake.event;

import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.ConcurrentShuffleList;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.WorldCoordinate;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumChunkCheckMode;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ServerEventHandler {
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


    public static void register() {
        // 注册服务端Tick事件
        ServerTickEvents.END_SERVER_TICK.register(ServerEventHandler::onServerTick);
        // 注册世界Tick事件
        ServerTickEvents.END_WORLD_TICK.register(ServerEventHandler::onWorldTick);

        // 注册玩家登录事件
        ServerPlayConnectionEvents.JOIN.register(ServerEventHandler::onPlayerLoggedIn);
        // 注册玩家登出事件
        ServerPlayConnectionEvents.DISCONNECT.register(ServerEventHandler::onPlayerLoggedOut);

        UseItemCallback.EVENT.register(ServerEventHandler::onPlayerUseItem);

        UseBlockCallback.EVENT.register(ServerEventHandler::onRightBlock);

        UseEntityCallback.EVENT.register(ServerEventHandler::onRightEntity);
    }

    private static void onServerTick(MinecraftServer server) {
        if (AotakeSweep.disable()) return;
        if (server == null || !server.isRunning()) return;

        long now = System.currentTimeMillis();
        long countdown = nextSweepTime - now;
        long sweepInterval = ServerConfig.SERVER_CONFIG.sweepInterval();

        // 扫地前提示
        String warnKey = String.valueOf(countdown / 1000);
        if (AotakeUtils.hasWarning(warnKey)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // 给已安装mod玩家同步扫地倒计时
                AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
                Component warningMessage = AotakeUtils.getWarningMessage(warnKey, AotakeUtils.getPlayerLanguage(player), null);
                if (warningMessage != null) {
                    AotakeUtils.sendActionBarMessage(player, warningMessage);
                }
            }
        }
        // 扫地前提示音效
        if (AotakeUtils.hasWarningVoice(warnKey) && lastVoiceTime + 1010 < now) {
            lastVoiceTime = now;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (PlayerSweepData.getData(player).isEnableWarningVoice()) {
                    String voice = AotakeUtils.getWarningVoice(warnKey);
                    float volume = ServerConfig.SERVER_CONFIG.sweepWarningVoiceVolume() / 100f;
                    if (StringUtils.isNotNullOrEmpty(voice)) {
                        AotakeUtils.executeCommandNoOutput(player, String.format("playsound %s voice @s ~ ~ ~ %s", voice, volume));
                    }
                }
            }
        }

        // 扫地
        if (countdown <= 0 && sweepInterval > 0) {
            nextSweepTime = now + sweepInterval;
            LOGGER.debug("Scheduled sweep will start");
            AotakeScheduler.schedule(server, 1, AotakeUtils::sweep);
            // 给已安装mod玩家同步扫地倒计时
            for (String uuid : AotakeSweep.customConfigStatus()) {
                ServerPlayer player = AotakeUtils.getPlayerByUUID(uuid);
                if (player != null) {
                    AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
                }
            }
        }

        // 自清洁
        if (ServerConfig.SERVER_CONFIG.selfCleanInterval() <= now - lastSelfCleanTime) {
            lastSelfCleanTime = now;
            WorldTrashData worldTrashData = WorldTrashData.get();
            List<SimpleContainer> inventories = worldTrashData.getInventoryList();
            // 清空
            if (ServerConfig.SERVER_CONFIG.selfCleanMode().contains(EnumSelfCleanMode.SCHEDULED_CLEAR.name())) {
                worldTrashData.getDropList().clear();
                if (CollectionUtils.isNotNullOrEmpty(inventories)) inventories.forEach(SimpleContainer::clearContent);
                WorldTrashData.get().setDirty();
            }
            // 随机删除
            else if (ServerConfig.SERVER_CONFIG.selfCleanMode().contains(EnumSelfCleanMode.SCHEDULED_DELETE.name())) {
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
        if (ServerConfig.SERVER_CONFIG.chunkCheckInterval() > 0
                && !chunkSweepLock.get()
                && ServerConfig.SERVER_CONFIG.chunkCheckInterval() <= now - lastChunkCheckTime
        ) {
            chunkSweepLock.set(true);
            lastChunkCheckTime = now;
            try {
                long start = System.currentTimeMillis();
                List<Map.Entry<String, List<Entity>>> overcrowdedChunks = AotakeUtils.getAllEntitiesByFilter(null, true).stream()
                        .collect(Collectors.groupingBy(entity -> {
                            // 获取区块维度和坐标
                            String dimension = entity.level() != null
                                    ? entity.level().dimension().location().toString()
                                    : "unknown";
                            int chunkX = entity.blockPosition().getX() >> 4;
                            int chunkZ = entity.blockPosition().getZ() >> 4;
                            if (EnumChunkCheckMode.DEFAULT == ServerConfig.SERVER_CONFIG.chunkCheckMode()) {
                                return String.format("Dimension: %s, Chunk: %s %s", dimension, chunkX, chunkZ);
                            } else {
                                return String.format("Dimension: %s, Chunk: %s %s, EntityType: %s", dimension, chunkX, chunkZ, AotakeUtils.getEntityTypeRegistryName(entity));
                            }
                        }))
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().size() > ServerConfig.SERVER_CONFIG.chunkCheckLimit())
                        .toList();
                long end = System.currentTimeMillis();

                if (!overcrowdedChunks.isEmpty()) {
                    LOGGER.debug("Chunk check started at {}", start);
                    LOGGER.debug("Chunk check finished at {}", end);
                    LOGGER.debug("Chunk check info:\n{}", overcrowdedChunks.stream()
                            .map(entry -> String.format("%s, Entities: %s", entry.getKey(), entry.getValue().size()))
                            .collect(Collectors.joining("\n")));

                    if (ServerConfig.SERVER_CONFIG.chunkCheckNotice()) {
                        Map.Entry<String, List<Entity>> entityEntryList = overcrowdedChunks.get(0);
                        Entity entity = entityEntryList.getValue().get(0);
                        WorldCoordinate entityCoordinate = new WorldCoordinate(entity);
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            String language = AotakeUtils.getPlayerLanguage(player);

                            Component message = Component.translatable(EnumI18nType.MESSAGE,
                                    ServerConfig.SERVER_CONFIG.chunkCheckOnlyNotice()
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
                                                        .map(entry -> {
                                                            WorldCoordinate coordinate = new WorldCoordinate(entry.getValue().get(0));
                                                            return String.format("Dimension: %s, Chunk: %s %s, Entities: %s",
                                                                    coordinate.getDimensionResourceId(),
                                                                    coordinate.getXInt() >> 4,
                                                                    coordinate.getZInt() >> 4,
                                                                    entry.getValue().size()
                                                            );
                                                        })
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
                        entities.subList(0, (int) (ServerConfig.SERVER_CONFIG.chunkCheckRetain() * entities.size())).clear();
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

    public static void onWorldTick(ServerLevel level) {
        EntitySweeper.flushPendingRemovals(level);
    }

    /**
     * 释放实体
     */
    private static Entity releaseEntity(ServerPlayer player, ItemStack original, WorldCoordinate coordinate) {
        ItemStack copy = original.copy();
        copy.setCount(1);

        CompoundTag tag = copy.getTag();
        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
            if (aotake.isEmpty()) {
                tag.remove(AotakeSweep.MODID);
            } else {
                original.shrink(1);

                CompoundTag entityData = aotake.getCompound("entity");
                ServerLevel level = player.serverLevel();
                Entity entity = EntityType.loadEntityRecursive(entityData, level, (e) -> e);
                if (entity != null) {
                    // 将实体放置到指定位置并加入世界
                    entity.moveTo(coordinate.getX(), coordinate.getY(), coordinate.getZ(), (float) coordinate.getYaw(), (float) coordinate.getPitch());
                    level.addFreshEntity(entity);
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

                    return entity;
                }
            }
        }

        return null;
    }


    public static InteractionResultHolder<ItemStack> onPlayerUseItem(Player player, Level world, InteractionHand hand) {
        if (AotakeSweep.disable()) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (!(player instanceof ServerPlayer)) return InteractionResultHolder.pass(player.getItemInHand(hand));

        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
            if (aotake.isEmpty()) tag.remove(AotakeSweep.MODID);
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    public static InteractionResult onRightBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (AotakeSweep.disable()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        ItemStack original = player.getItemInHand(hand);
        Vec3 loc = hitResult.getLocation();
        WorldCoordinate coordinate = new WorldCoordinate(loc.x, loc.y, loc.z);

        Entity released = releaseEntity(serverPlayer, original, coordinate);
        if (released != null) {
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult onRightEntity(Player player, Level world, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
        if (AotakeSweep.disable()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        ItemStack original = player.getItemInHand(hand);
        ItemStack copy = original.copy();
        copy.setCount(1);

        CompoundTag tag = copy.getTag();

        if (tag != null && tag.contains(AotakeSweep.MODID)) {
            CompoundTag aotake = tag.getCompound(AotakeSweep.MODID);
            if (!aotake.isEmpty()) {
                WorldCoordinate coordinate = new WorldCoordinate(entity.getX(), entity.getY(), entity.getZ());
                Entity back = releaseEntity(serverPlayer, original, coordinate);
                if (back != null) {
                    back.startRiding(entity, true);
                    // 同步客户端乘客信息
                    AotakeUtils.broadcastPacket(new ClientboundSetPassengersPacket(entity));
                    return InteractionResult.SUCCESS;
                }
            }
        }

        boolean allowCatch = ServerConfig.SERVER_CONFIG.allowCatchEntity();
        List<String> catchItems = ServerConfig.SERVER_CONFIG.catchItem();
        boolean isCatchItem = catchItems.stream().anyMatch(s -> s.equals(AotakeUtils.getItemRegistryName(original)));

        boolean hasEntityInTag = tag != null && tag.contains(AotakeSweep.MODID)
                && tag.getCompound(AotakeSweep.MODID).contains("entity");

        if (allowCatch && isCatchItem && serverPlayer.isCrouching() && !hasEntityInTag) {
            original.shrink(1);

            tag = copy.getOrCreateTag();
            CompoundTag aotake = new CompoundTag();
            aotake.putBoolean("byPlayer", true);
            CompoundTag entityTag = new CompoundTag();
            entity.save(entityTag);
            aotake.put("entity", entityTag);
            aotake.putString("name", AotakeUtils.getItemCustomNameJson(copy));
            tag.put(AotakeSweep.MODID, aotake);

            Component combined = Component.literal(entity.getDisplayName().getString() + copy.getHoverName().getString());
            copy.setHoverName(combined.toTextComponent());
            serverPlayer.addItem(copy);
            // 移除被捕实体
            AotakeUtils.removeEntity(entity, true);

            AotakeUtils.sendActionBarMessage(serverPlayer, Component.translatable(EnumI18nType.MESSAGE, "entity_caught"));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }


    /**
     * 玩家登录事件
     */
    public static void onPlayerLoggedIn(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        if (AotakeSweep.customConfigStatus().contains(AotakeUtils.getPlayerUUIDString(handler.getPlayer()))) {
            AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), handler.getPlayer());
        }
    }

    /**
     * 玩家登出事件
     */
    public static void onPlayerLoggedOut(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        // 玩家退出服务器时移除mod安装状态
        AotakeSweep.customConfigStatus().remove(handler.getPlayer().getStringUUID());
    }
}
