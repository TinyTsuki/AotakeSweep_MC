package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家数据存储
 */
@Mod.EventBusSubscriber(modid = AotakeSweep.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDataStorage {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String SUFFIX = "_" + AotakeSweep.MODID + ".nbt";

    private final Map<UUID, CompoundTag> cache = new ConcurrentHashMap<>();

    private static final PlayerDataStorage INSTANCE = new PlayerDataStorage();

    private PlayerDataStorage() {
    }

    public static PlayerDataStorage instance() {
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
     * 从磁盘加载，并返回读取到的 CompoundTag
     */
    public synchronized CompoundTag loadFromDisk(UUID playerUuid) {
        File file = getPlayerDataFile(playerUuid);
        if (!file.exists()) {
            CompoundTag fresh = new CompoundTag();
            cache.put(playerUuid, fresh);
            return fresh;
        }

        try {
            CompoundTag tag = NbtIo.readCompressed(file);
            cache.put(playerUuid, tag);
            return tag;
        } catch (IOException e) {
            LOGGER.error("Failed to read player data file: {}", file.getAbsolutePath(), e);
            CompoundTag fresh = new CompoundTag();
            cache.put(playerUuid, fresh);
            return fresh;
        }
    }

    public void saveToDisk(Player player) {
        saveToDisk(player.getUUID());
    }

    /**
     * 保存到磁盘
     */
    public synchronized void saveToDisk(UUID playerUuid) {
        CompoundTag tag = cache.get(playerUuid);
        File file = getPlayerDataFile(playerUuid);
        if (tag == null) {
            if (file.exists() && !file.delete()) {
                LOGGER.warn("Failed to delete empty player data file: {}", file.getAbsolutePath());
            }
            return;
        }

        File dir = file.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("Could not create player data directory: {}", dir.getAbsolutePath());
        }

        try {
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            LOGGER.error("Failed to write player data file: {}", file.getAbsolutePath(), e);
        }
    }

    public void saveAllForWorld() {
        for (UUID uuid : cache.keySet()) {
            saveToDisk(uuid);
        }
    }

    /**
     * 获取玩家数据文件
     */
    private File getPlayerDataFile(UUID uuid) {
        Path playerDataDir = AotakeSweep.getServerInstance().key().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        String name = uuid.toString() + SUFFIX;
        return new File(playerDataDir.toFile(), name);
    }


    // region 事件

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        UUID uuid = event.getPlayer().getUUID();
        LOGGER.debug("Loading mod player data for {}", uuid);
        instance().loadFromDisk(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        UUID uuid = event.getPlayer().getUUID();
        LOGGER.debug("Saving mod player data for {} on logout", uuid);
        instance().saveToDisk(uuid);
        instance().remove(uuid);
    }

    private long lastWorldSaveTime = 0;

    @SubscribeEvent
    public static void onWorldSave(WorldEvent.Save event) {
        if (!(event.getWorld() instanceof ServerLevel)) return;
        long current = System.currentTimeMillis();
        if (current - instance().lastWorldSaveTime > 2000) {
            instance().lastWorldSaveTime = current;
            try {
                LOGGER.debug("Persisting player data for mod on world save");
                instance().saveAllForWorld();
            } catch (Exception e) {
                LOGGER.error("Failed to save player data for mod on world save", e);
                instance().lastWorldSaveTime = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof ServerLevel)) return;
        LOGGER.debug("Persisting player data for mod on world unload");
        instance().saveAllForWorld();
    }

    // endregion 事件

}
