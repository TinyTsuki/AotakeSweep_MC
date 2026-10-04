package xin.vanilla.aotake.data.world;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.util.EntitySweeper;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ChunkVaultPendingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); xin.vanilla.aotake.rule.ForgeNativeTestBootstrap.initialize(); }

    @Test public void stoppingCanceledSweepPersistsEachOriginWithoutDuplicatingItems() throws Exception {
        Path first = temporary.newFolder().toPath(), second = temporary.newFolder().toPath();
        ChunkVaultStorage.queueRecycledItem(first, "run", new ItemStack(Items.STONE, 3));
        ChunkVaultStorage.queueRecycledItem(second, "run", new ItemStack(Items.APPLE, 7));
        new EntitySweeper().clear();
        assertTrue("Committed output was stranded after cancellation", Files.isRegularFile(first.resolve("run.nbt")));
        assertEquals(3, count(first));
        assertEquals(7, count(second));
        ChunkVaultStorage.flushPending();
        assertEquals(3, count(first));
        assertEquals(7, count(second));
    }

    @Test public void failedWriteRetainsOriginalDestinationForRetry() throws Exception {
        Path blocked = temporary.newFile().toPath();
        ChunkVaultStorage.queueRecycledItem(blocked, "run", new ItemStack(Items.STONE, 3));
        ChunkVaultStorage.flushPending();
        Files.delete(blocked);
        Files.createDirectory(blocked);
        ChunkVaultStorage.flushPending();
        assertEquals(3, count(blocked));
        ChunkVaultStorage.flushPending();
        assertEquals(3, count(blocked));
    }

    private static int count(Path root) throws Exception {
        return ChunkVaultStorage.readItemsFromRoot(NbtIo.readCompressed(root.resolve("run.nbt").toFile()))
                .stream().mapToInt(ItemStack::getCount).sum();
    }
}
