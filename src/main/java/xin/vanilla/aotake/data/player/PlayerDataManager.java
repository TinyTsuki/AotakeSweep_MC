package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 玩家数据管理
 */
@Mod.EventBusSubscriber(modid = AotakeSweep.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDataManager {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String MOD_ID = AotakeSweep.MODID;
    private static final String SUFFIX = Arrays.stream(AotakeSweep.ARTIFACT_ID.split("\\.")).sorted().collect(Collectors.joining("."));

    private static final PlayerDataManager INSTANCE = new PlayerDataManager();
    private final Map<UUID, CompoundTag> cache = new ConcurrentHashMap<>();

    private PlayerDataManager() {
    }

    public static PlayerDataManager instance() {
        return INSTANCE;
    }

    public CompoundTag getOrCreate(Player player) {
        return getOrCreate(player.getUUID());
    }

    public CompoundTag getOrCreate(UUID playerUuid) {
        return cache.computeIfAbsent(playerUuid, k -> new CompoundTag());
    }

    public void put(Player player, CompoundTag tag) {
        put(player.getUUID(), tag);
    }

    public void put(UUID playerUuid, CompoundTag tag) {
        if (tag == null) {
            cache.remove(playerUuid);
        } else {
            cache.put(playerUuid, tag);
        }
    }

    public void remove(Player player) {
        remove(player.getUUID());
    }

    public void remove(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public CompoundTag loadFromDisk(Player player) {
        return loadFromDisk(player.getUUID());
    }

    /**
     * 从磁盘读取整个玩家文件，并获取MODID节点下数据
     */
    public synchronized CompoundTag loadFromDisk(UUID playerUuid) {
        File file = getPlayerDataFile(playerUuid);
        CompoundTag modNode;
        if (!file.exists()) {
            modNode = new CompoundTag();
            cache.put(playerUuid, modNode);
            return modNode;
        }

        CompoundTag root = AotakeUtils.readCompressed(file);
        if (root.contains(MOD_ID, 10)) {
            modNode = root.getCompound(MOD_ID);
        } else {
            modNode = new CompoundTag();
        }
        cache.put(playerUuid, modNode);
        return modNode;
    }

    public void saveToDisk(Player player) {
        saveToDisk(player.getUUID());
    }

    /**
     * 将缓存中的数据写入文件
     */
    public synchronized void saveToDisk(UUID playerUuid) {
        CompoundTag modNode = cache.get(playerUuid);
        File file = getPlayerDataFile(playerUuid);

        // 如果没有要保存的节点（null），则我们需要从磁盘中移除本 MOD 的节点（如果存在）
        if (modNode == null) {
            if (!file.exists()) return;
            try {
                CompoundTag root = AotakeUtils.readCompressed(file);
                if (root.contains(MOD_ID, 10)) {
                    root.remove(MOD_ID);
                    atomicWrite(root, file);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to remove mod node from player data file: {}", file.getAbsolutePath(), e);
            }
            return;
        }

        // 确保目录存在
        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("Could not create player data directory: {}", dir.getAbsolutePath());
        }

        try {
            CompoundTag root;
            if (file.exists()) {
                root = AotakeUtils.readCompressed(file);
            } else {
                root = new CompoundTag();
            }

            // 覆盖或添加本 MOD 节点
            root.put(MOD_ID, modNode);
            atomicWrite(root, file);
        } catch (Exception e) {
            LOGGER.error("Failed to write player data file: {}", file.getAbsolutePath(), e);
        }
    }

    private long lastSaveAllTime = 0;

    public synchronized void saveAllForWorld() {
        long current = System.currentTimeMillis();
        if (current - lastSaveAllTime > 2000) {
            lastSaveAllTime = current;
            try {
                LOGGER.debug("Saving all mod player data");
                for (UUID uuid : cache.keySet().toArray(new UUID[0])) {
                    saveToDisk(uuid);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save all mod player data", e);
                lastSaveAllTime = 0;
            }
        }
    }

    /**
     * 获取玩家数据文件
     */
    private File getPlayerDataFile(UUID uuid) {
        Path playerDataDir = AotakeSweep.getServerInstance().key().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        return new File(playerDataDir.resolve(SUFFIX).toFile(), uuid + ".nbt");
    }


    // region events

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        UUID uuid = event.getEntity().getUUID();
        LOGGER.debug("Loading mod player data for {}", uuid);
        instance().loadFromDisk(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        UUID uuid = event.getEntity().getUUID();
        LOGGER.debug("Saving mod player data for {} on logout", uuid);
        instance().saveToDisk(uuid);
        instance().remove(uuid);
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        instance().saveAllForWorld();
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        instance().saveAllForWorld();
    }

    // endregion events

    // region utils

    /**
     * 原子写文件：先写入临时文件，再替换目标文件（尝试 ATOMIC_MOVE，失败则 REPLACE_EXISTING）
     */
    private void atomicWrite(CompoundTag root, File target) throws IOException {
        File dir = target.getParentFile();
        File tmpFile = new File(dir, target.getName() + ".tmp");
        File bakFile = new File(dir, target.getName() + ".bak");

        boolean written = AotakeUtils.writeCompressed(root, tmpFile);
        if (!written) throw new IOException("Failed to write temp file: " + tmpFile.getAbsolutePath());

        // 备份现有目标为 .bak
        try {
            if (target.exists()) {
                try {
                    // 优先尝试原子移动备份
                    Files.move(target.toPath(), bakFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ex) {
                    // 如果 ATOMIC_MOVE 不可用则退回普通替换
                    Files.move(target.toPath(), bakFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to move original to bak: {} -> {}", target.getAbsolutePath(), bakFile.getAbsolutePath(), e);
        }

        try {
            try {
                Files.move(tmpFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                // 原子移动失败则退回到普通移动
                Files.move(tmpFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to move temp file to target: {} -> {}", tmpFile.getAbsolutePath(), target.getAbsolutePath(), e);
            // 回滚尝试：如果有 bak，则把 bak 恢复为 target
            if (bakFile.exists()) {
                try {
                    Files.move(bakFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.warn("Rollback: restored bak to target for {}", target.getAbsolutePath());
                } catch (Exception rex) {
                    LOGGER.error("Rollback failed for target: {} (bak: {})", target.getAbsolutePath(), bakFile.getAbsolutePath(), rex);
                }
            }
            // 清理 tmp
            if (tmpFile.exists()) tmpFile.delete();
            throw e;
        }
    }

    // endregion utils
}
