package xin.vanilla.aotake.internal.server.dev;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.util.AotakeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded real-world cleanup operations; asynchronous latency is separate from synchronous submission cost. */
final class AotakeNetworkSmokeWorkload {
    static final boolean MUTABLE_THRESHOLDS = Boolean.getBoolean("aotake.networkSmoke.mutableThresholds");
    private static final int CHUNK_ITEMS = MUTABLE_THRESHOLDS ? 16 : 544;
    static final int PAPER_PER_CYCLE = 1728 + 128 + CHUNK_ITEMS + 128;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final BlockPos origin;
    private final Map<String, AotakeNetworkSmokeTimings> timings = new LinkedHashMap<>();
    private List<Entity> pending = Collections.emptyList();
    private String operation;
    private long startedAt;
    private int step;
    private int cycles;
    private int expectedPaper;
    private int burstEntities;
    private int globalEntities;
    private final List<net.minecraft.world.level.ChunkPos> forcedChunks = new ArrayList<>();

    AotakeNetworkSmokeWorkload(ServerPlayer player) {
        this.player = player;
        this.level = player.getLevel();
        this.origin = new BlockPos((player.blockPosition().getX() >> 4) * 16 + 4, 100,
                (player.blockPosition().getZ() >> 4) * 16 + 4);
        player.teleportTo(level, origin.getX() + 1.5D, origin.getY() + 1.0D, origin.getZ(), 0.0F, 0.0F);
        CommonConfig.get().base().dustbin().selfCleanMode(Collections.singletonList(EnumSelfCleanMode.NONE))
                .selfCleanInterval(0L).dustbinPersistent(true).cacheLimit(100000);
        CommonConfig.get().base().sweep().entityList(Arrays.asList("minecraft:item", "minecraft:experience_orb"));
        CommonConfig.get().base().chunk().chunkCheckLimit(512).chunkCheckInterval(1000L);
        WorldTrashData.get().getDropList().clear();
        WorldTrashData.get().getInventoryList().forEach(SimpleContainer::clearContent);
        player.inventory.clearContent();
        player.inventory.selected = 8;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            int chunkX = (origin.getX() >> 4) + x;
            int chunkZ = (origin.getZ() >> 4) + z;
            if (!level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ))) {
                level.setChunkForced(chunkX, chunkZ, true);
                forcedChunks.add(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ));
            }
            level.getChunk(chunkX, chunkZ);
        }
        for (String name : new String[]{"container-break", "container-recovery", "global-countdown-recovery",
                "chunk-recovery", "capture-submit", "capture-complete", "pipeline-submit", "pipeline-complete"}) {
            timings.put(name, new AotakeNetworkSmokeTimings(AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES));
        }
    }

    boolean tick() {
        if (complete()) return true;
        require(CommonConfig.get().base().chunk().chunkCheckMode() == xin.vanilla.aotake.enums.EnumChunkCheckMode.DEFAULT
                && CommonConfig.get().base().chunk().chunkCheckRetain() == 0.0D
                && !CommonConfig.get().base().chunk().chunkVaultEnabled(), "Cleanup fixture configuration changed unexpectedly");
        if (operation != null) {
            if (System.nanoTime() - startedAt > 10_000_000_000L) {
                throw new IllegalStateException("Cleanup timed out: " + operation + " cycle=" + cycles
                        + " alive=" + pending.stream().filter(Entity::isAlive).count()
                        + " recovered=" + recoveredPaper() + "/" + expectedPaper);
            }
            if (pending.stream().anyMatch(Entity::isAlive)) return false;
            require(recoveredPaper() == expectedPaper, "Removed entities without exact recovered item count");
            if ("capture-complete".equals(operation)) {
                require(capturedCount(player) == cycles + 1, "Captured payload count mismatch");
            }
            timings.get(operation).record(System.nanoTime() - startedAt);
            operation = null;
            pending = Collections.emptyList();
            if (++step == 5) {
                step = 0;
                cycles++;
                require(expectedPaper == cycles * PAPER_PER_CYCLE, "Incomplete cycle payload");
            }
            return complete();
        }
        if (MUTABLE_THRESHOLDS) CommonConfig.get().base().chunk().chunkCheckInterval(0L);
        switch (step) {
            case 0:
                prepareContainer();
                break;
            case 1:
                prepareGlobal();
                break;
            case 2:
                pending = spawnItems(CHUNK_ITEMS, false);
                expectedPaper += CHUNK_ITEMS;
                begin("chunk-recovery");
                if (MUTABLE_THRESHOLDS) CommonConfig.get().base().chunk().chunkCheckLimit(8).chunkCheckInterval(1L);
                break;
            case 3:
                prepareCapture();
                break;
            case 4:
                pending = spawnItems(128, true);
                expectedPaper += 128;
                begin("pipeline-complete");
                measure("pipeline-submit", () -> AotakeUtils.sweep(new ArrayList<>(pending), true));
                break;
            default:
                throw new IllegalStateException("Unknown cleanup step " + step);
        }
        return false;
    }

    private void prepareContainer() {
        require(level.setBlockAndUpdate(origin, Blocks.CHEST.defaultBlockState()), "Unable to place fixture container");
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(origin);
        require(chest != null, "Container tile missing");
        for (int slot = 0; slot < 27; slot++) chest.setItem(slot, paper(64, slot));
        measure("container-break", () -> require(level.destroyBlock(origin, true, player), "Container was not destroyed"));
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(3.0D));
        pending = new ArrayList<>();
        int dropped = 0;
        for (ItemEntity entity : drops) {
            if (entity.getItem().getItem() != Items.PAPER) continue;
            dropped += entity.getItem().getCount();
            stabilize(entity, pending.size());
            pending.add(entity);
        }
        require(dropped == 1728, "Container did not drop all 27 stacks: " + dropped);
        burstEntities += pending.size();
        expectedPaper += dropped;
        begin("container-recovery");
        EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
    }

    private void prepareGlobal() {
        pending = spawnItems(128, true);
        for (int chunk = 0; chunk < 4; chunk++) for (int index = 0; index < 8; index++) {
            ExperienceOrb orb = new ExperienceOrb(level, x(chunk) + index * .2D,
                    origin.getY(), z(chunk), 1);
            orb.setNoGravity(true);
            require(level.addFreshEntity(orb), "Failed to add experience entity");
            pending.add(orb);
        }
        require(pending.size() == 160, "Global fixture missing entities");
        globalEntities += pending.size();
        expectedPaper += 128;
        // Check the configured threshold before arming the actual scheduled global sweep.
        if (MUTABLE_THRESHOLDS) CommonConfig.get().base().chunk().chunkCheckLimit(512).chunkCheckInterval(1L);
        begin("global-countdown-recovery");
        EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
    }

    private void prepareCapture() {
        Cow cow = EntityType.COW.create(level);
        require(cow != null, "Could not create capture target");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.moveTo(origin.getX(), origin.getY(), origin.getZ(), 0.0F, 0.0F);
        require(level.addFreshEntity(cow), "Could not add capture target");
        pending = new ArrayList<>();
        pending.add(cow);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        final InteractionResult[] result = new InteractionResult[1];
        begin("capture-complete");
        try {
            measure("capture-submit", () -> result[0] = EventHandlerProxy.onRightEntity(player, level,
                    InteractionHand.MAIN_HAND, cow, new net.minecraft.world.phys.EntityHitResult(cow, Vec3.ZERO)));
        } finally {
            player.setShiftKeyDown(false);
            player.setPose(Pose.STANDING);
        }
        require(result[0] == InteractionResult.SUCCESS,
                "Capture handler did not consume the interaction");
        // Keep the captured stack out of the hand that the next cycle replaces.
        ItemStack captured = player.getMainHandItem();
        if (AotakeUtils.hasAotakeTag(captured)) {
            boolean moved = false;
            for (int slot = 0; slot < player.inventory.items.size(); slot++) {
                if (slot != 8 && player.inventory.getItem(slot).isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.inventory.setItem(slot, captured);
                    moved = true;
                    break;
                }
            }
            require(moved, "No room for captured payload");
        }
    }

    private List<Entity> spawnItems(int count, boolean spread) {
        List<Entity> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int chunk = spread ? index % 4 : 0;
            ItemEntity entity = new ItemEntity(level, x(chunk) + (index / 4 % 4) * .3D,
                    origin.getY(), z(chunk) + (index / 16 % 16) * .3D, paper(1, index));
            stabilize(entity, index);
            require(level.addFreshEntity(entity), "Could not add fixture item: cycle=" + cycles
                    + " step=" + step + " index=" + index + " position=" + entity.blockPosition()
                    + " loaded=" + level.hasChunkAt(entity.blockPosition()));
            result.add(entity);
        }
        return result;
    }

    private ItemStack paper(int count, int index) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        stack.getOrCreateTag().putString("aotakeSmokeItem", cycles + ":" + step + ":" + index);
        return stack;
    }

    private void stabilize(ItemEntity entity, int index) {
        entity.getItem().getOrCreateTag().putString("aotakeSmokeEntity", cycles + ":" + step + ":" + index);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPickUpDelay(32767);
    }

    private double x(int chunk) { return origin.getX() + (chunk % 2) * 16; }
    private double z(int chunk) { return origin.getZ() + (chunk / 2) * 16; }
    private void begin(String name) { operation = name; startedAt = System.nanoTime(); }

    private void measure(String name, Runnable action) {
        long start = System.nanoTime();
        action.run();
        timings.get(name).record(System.nanoTime() - start);
    }

    boolean complete() { return cycles == AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES; }
    int cycles() { return cycles; }

    void report() {
        for (Map.Entry<String, AotakeNetworkSmokeTimings> entry : timings.entrySet()) {
            require(entry.getValue().count() == AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES, "Incomplete " + entry.getKey());
            AotakeNetworkSmokeStatus.append("PASS operation " + entry.getKey() + " " + entry.getValue().summary());
        }
        AotakeNetworkSmokeStatus.append("PASS measured-cleanup cycles=" + cycles + " paper=" + recoveredPaper()
                + " burst-entities=" + burstEntities + " global-entities=" + globalEntities
                + " captured=" + capturedCount(player));
        for (net.minecraft.world.level.ChunkPos chunk : forcedChunks) level.setChunkForced(chunk.x, chunk.z, false);
        forcedChunks.clear();
    }

    static int recoveredPaper() {
        return recoveredItem(Items.PAPER);
    }

    static int recoveredItem(net.minecraft.world.item.Item item) {
        int total = 0;
        for (SimpleContainer inventory : WorldTrashData.get().getInventoryList()) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.getItem() == item) total += stack.getCount();
            }
        }
        for (xin.vanilla.banira.common.data.KeyValue<xin.vanilla.banira.common.data.WorldCoordinate, ItemStack> drop
                : WorldTrashData.get().getDropList().snapshot()) {
            if (drop.value().getItem() == item) total += drop.value().getCount();
        }
        return total;
    }

    static int capturedCount(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.inventory.items) if (AotakeUtils.hasAotakeTag(stack)) {
            net.minecraft.nbt.CompoundTag tag = AotakeUtils.getAotakeTag(stack);
            require("minecraft:cow".equals(tag.getString("entityId"))
                    && "minecraft:cow".equals(tag.getCompound("entity").getString("id")),
                    "Captured payload is not the expected complete cow entity");
            total += stack.getCount();
        }
        return total;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
