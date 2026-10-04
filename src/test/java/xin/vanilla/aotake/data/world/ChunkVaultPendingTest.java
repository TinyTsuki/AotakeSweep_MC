package xin.vanilla.aotake.data.world;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.banira.common.util.NBTUtils;
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
    @BeforeClass public static void bootstrap() { xin.vanilla.aotake.rule.ForgeNativeTestBootstrap.bootstrapGame(); }

    @Test public void stoppingCanceledSweepPersistsEachOriginWithoutDuplicatingItems() throws Exception {
        Path first = temporary.newFolder().toPath(), second = temporary.newFolder().toPath();
        ChunkVaultStorage.queueRecycledItem(first, "run", new ItemStack(Items.STONE, 3), provider());
        ChunkVaultStorage.queueRecycledItem(second, "run", new ItemStack(Items.APPLE, 7), provider());
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
        ChunkVaultStorage.queueRecycledItem(blocked, "run", new ItemStack(Items.STONE, 3), provider());
        ChunkVaultStorage.flushPending();
        Files.delete(blocked);
        Files.createDirectory(blocked);
        ChunkVaultStorage.flushPending();
        assertEquals(3, count(blocked));
        ChunkVaultStorage.flushPending();
        assertEquals(3, count(blocked));
    }

    private static net.minecraft.core.HolderLookup.Provider provider() {
        return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }

    private static int count(Path root) throws Exception {
        return ChunkVaultStorage.readItemsFromRoot(NBTUtils.readCompressed(root.resolve("run.nbt").toFile()), provider())
                .stream().mapToInt(ItemStack::getCount).sum();
    }
}
