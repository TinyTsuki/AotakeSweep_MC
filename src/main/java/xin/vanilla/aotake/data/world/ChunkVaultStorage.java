package xin.vanilla.aotake.data.world;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.banira.BaniraCodex;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;


public final class ChunkVaultStorage {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SUBDIR = "chunk_vault";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH");

    private static final Map<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();
    private static final Map<String, List<ItemStack>> PENDING = new ConcurrentHashMap<>();

    private ChunkVaultStorage() {
    }

    @Nullable
    public static Path getVaultDirOrNull() {
        if (!BaniraCodex.serverInstance().val()) return null;
        MinecraftServer bound = BaniraCodex.serverInstance().key();
        if (bound == null) return null;
        return BaniraCodex.BANIRA_WORLD_DATA_PATH.get().resolve(AotakeSweep.MODID).resolve(SUBDIR);
    }

    public static Path getVaultDir() {
        Path p = getVaultDirOrNull();
        if (p == null) {
            throw new IllegalStateException("Banira world data path is not available yet");
        }
        return p;
    }

    public static Object lockFor(String vaultId) {
        return FILE_LOCKS.computeIfAbsent(vaultId, k -> new Object());
    }

    public static String vaultIdFor(String timePrefix, ChunkKey key) {
        String dim = sanitizePathSegment(key.dimension());
        return timePrefix + "_" + dim + "_" + key.chunkX() + "_" + key.chunkZ();
    }

    /**
     * 生成一次区块过载扫地对应的 vault 运行号（写入文件名后缀）。
     */
    public static String newVaultRunId() {
        return System.currentTimeMillis() + "_" + Integer.toHexString(ThreadLocalRandom.current().nextInt() & 0xffff);
    }

    private static String resolveVaultId(ChunkKey key, @Nullable SweepResult batchContext) {
        String timePrefix;
        if (batchContext != null && batchContext.getChunkVaultTimePrefix() != null
                && !batchContext.getChunkVaultTimePrefix().isEmpty()) {
            timePrefix = batchContext.getChunkVaultTimePrefix();
        } else {
            timePrefix = vaultTimePrefix();
        }
        String base = vaultIdFor(timePrefix, key);
        if (batchContext != null && batchContext.getChunkVaultRunId() != null
                && !batchContext.getChunkVaultRunId().isEmpty()) {
            return base + "_" + sanitizePathSegment(batchContext.getChunkVaultRunId());
        }
        return base;
    }

    /**
     * 文件名用时间前缀（受 {@link CommonConfig.ChunkSection#chunkVaultBucketHours()} 控制）。
     */
    public static String vaultTimePrefix() {
        int bucketHours = 1;
        try {
            bucketHours = CommonConfig.get().base().chunk().chunkVaultBucketHours();
        } catch (Throwable ignored) {
        }
        if (bucketHours < 1) {
            bucketHours = 1;
        }
        ZoneId zone = ZoneId.systemDefault();
        if (bucketHours >= 24) {
            return LocalDate.now(zone).format(DAY);
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        int hour = now.getHour();
        int startHour = (hour / bucketHours) * bucketHours;
        ZonedDateTime bucketStart = now.withHour(startHour).withMinute(0).withSecond(0).withNano(0);
        return bucketStart.format(HOUR_BUCKET);
    }

    public static ChunkKey chunkKeyFromEntity(net.minecraft.entity.Entity entity) {
        return ChunkKey.of(entity);
    }

    /**
     * 将实体对应分组的回收物加入待写入队列（在批次全部结束后 {@link #flushPending(MinecraftServer)}）。
     *
     * @param batchContext 非空且含 {@link SweepResult#getChunkVaultRunId()} 时，按「本轮清理」分文件；否则沿用仅时间桶+区块的旧 vault 名（兼容）。
     */
    public static void queueRecycledItem(net.minecraft.entity.Entity sourceEntity, ItemStack stack, @Nullable SweepResult batchContext) {
        if (stack == null || stack.isEmpty()) return;
        if (!CommonConfig.get().base().chunk().chunkVaultEnabled()) return;
        if (BaniraCodex.serverInstance().key() == null) return;
        ChunkKey key = chunkKeyFromEntity(sourceEntity);
        String vaultId = resolveVaultId(key, batchContext);
        PENDING.computeIfAbsent(vaultId, k -> Collections.synchronizedList(new ArrayList<>())).add(stack.copy());
    }

    public static void flushPending(@Nullable MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;
        for (String vaultId : new ArrayList<>(PENDING.keySet())) {
            List<ItemStack> batch = PENDING.remove(vaultId);
            if (batch == null || batch.isEmpty()) continue;
            appendItemsToFile(server, vaultId, batch);
        }
    }

    public static void appendItemsToFile(MinecraftServer server, String vaultId, List<ItemStack> stacks) {
        Path dir = getVaultDirOrNull();
        if (dir == null) {
            LOGGER.warn("Skip chunk vault write (path not ready): {}", vaultId);
            return;
        }
        Path file = dir.resolve(vaultId + ".nbt");
        synchronized (lockFor(vaultId)) {
            try {
                Files.createDirectories(dir);
                CompoundNBT root = Files.exists(file)
                        ? CompressedStreamTools.read(file.toFile())
                        : new CompoundNBT();
                root.putString("VaultId", vaultId);
                root.putLong("UpdatedAt", System.currentTimeMillis());
                if (!root.contains("CreatedAt")) {
                    root.putLong("CreatedAt", System.currentTimeMillis());
                }
                if (!root.contains("Kind")) {
                    root.putString("Kind", "chunk_overload");
                }
                ListNBT list = root.getList("Items", 10);
                for (ItemStack s : stacks) {
                    if (s.isEmpty()) continue;
                    list.add(s.save(new CompoundNBT()));
                }
                root.put("Items", list);
                CompressedStreamTools.write(root, file.toFile());
            } catch (Exception e) {
                LOGGER.warn("Failed to write chunk vault {}: {}", vaultId, e.getMessage());
            }
        }
    }

    public static List<ItemStack> readAllItems(MinecraftServer server, String vaultId) {
        Path dir = getVaultDirOrNull();
        if (dir == null) {
            return Collections.emptyList();
        }
        Path file = dir.resolve(vaultId + ".nbt");
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        synchronized (lockFor(vaultId)) {
            try {
                CompoundNBT root = CompressedStreamTools.read(file.toFile());
                return readItemsFromRoot(root);
            } catch (Exception e) {
                LOGGER.warn("Failed to read chunk vault {}: {}", vaultId, e.getMessage());
                return Collections.emptyList();
            }
        }
    }

    public static List<ItemStack> readItemsFromRoot(CompoundNBT root) {
        ListNBT list = root.getList("Items", 10);
        List<ItemStack> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack st = ItemStack.of(list.getCompound(i));
            if (!st.isEmpty()) {
                out.add(st);
            }
        }
        return out;
    }

    public static void writeAllItems(MinecraftServer server, String vaultId, List<ItemStack> stacks) {
        Path dir = getVaultDirOrNull();
        if (dir == null) {
            LOGGER.warn("Skip chunk vault rewrite (path not ready): {}", vaultId);
            return;
        }
        Path file = dir.resolve(vaultId + ".nbt");
        synchronized (lockFor(vaultId)) {
            try {
                Files.createDirectories(dir);
                CompoundNBT root = Files.exists(file)
                        ? CompressedStreamTools.read(file.toFile())
                        : new CompoundNBT();
                root.putString("VaultId", vaultId);
                root.putLong("UpdatedAt", System.currentTimeMillis());
                if (!root.contains("CreatedAt")) {
                    root.putLong("CreatedAt", System.currentTimeMillis());
                }
                ListNBT list = new ListNBT();
                for (ItemStack s : stacks) {
                    if (s.isEmpty()) continue;
                    list.add(s.save(new CompoundNBT()));
                }
                root.put("Items", list);
                if (list.isEmpty()) {
                    Files.deleteIfExists(file);
                } else {
                    CompressedStreamTools.write(root, file.toFile());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to rewrite chunk vault {}: {}", vaultId, e.getMessage());
            }
        }
    }

    public static List<String> listVaultIds(MinecraftServer server) {
        Path dir = getVaultDirOrNull();
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        try {
            List<Path> paths = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt"))
                        .forEach(paths::add);
            }
            paths.sort((a, b) -> {
                try {
                    long ta = Files.getLastModifiedTime(a).toMillis();
                    long tb = Files.getLastModifiedTime(b).toMillis();
                    int cmp = Long.compare(tb, ta);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return b.getFileName().toString().compareTo(a.getFileName().toString());
                } catch (IOException e) {
                    return 0;
                }
            });
            List<String> ids = new ArrayList<>(paths.size());
            for (Path p : paths) {
                String name = p.getFileName().toString();
                ids.add(name.substring(0, name.length() - 4));
            }
            return ids;
        } catch (IOException e) {
            LOGGER.warn("Failed to list chunk vault dir: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public static boolean vaultExists(String vaultId) {
        Path dir = getVaultDirOrNull();
        return dir != null && Files.isRegularFile(dir.resolve(vaultId + ".nbt"));
    }

    public static void pruneExpired(MinecraftServer server) {
        int days;
        try {
            days = CommonConfig.get().base().chunk().chunkVaultRetentionDays();
        } catch (Throwable ignored) {
            days = 2;
        }
        if (days <= 0) return;
        Path dir = getVaultDirOrNull();
        if (dir == null || !Files.isDirectory(dir)) return;
        LocalDate cutoff = LocalDate.now().minusDays(days);
        try {
            for (Path p : Files.newDirectoryStream(dir, "*.nbt")) {
                String name = p.getFileName().toString();
                if (!name.endsWith(".nbt")) continue;
                String id = name.substring(0, name.length() - 4);
                LocalDate fileDay = parseLeadingDate(id);
                if (fileDay != null && fileDay.isBefore(cutoff)) {
                    Files.deleteIfExists(p);
                    LOGGER.debug("Pruned expired chunk vault file {}", name);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Chunk vault prune failed: {}", e.getMessage());
        }
    }

    @Nullable
    private static LocalDate parseLeadingDate(String vaultId) {
        int u = vaultId.indexOf('_');
        if (u < 0) return null;
        String prefix = vaultId.substring(0, u);
        try {
            return LocalDate.parse(prefix, DAY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String sanitizePathSegment(String raw) {
        if (raw == null) return "unknown";
        return raw.replace(':', '_').replace('/', '_').replace('\\', '_').replace(' ', '_');
    }
}
