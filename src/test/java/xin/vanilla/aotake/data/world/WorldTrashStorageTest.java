package xin.vanilla.aotake.data.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.test.NativeBootstrap;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.banira.platform.BaniraServerService;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class WorldTrashStorageTest {
    private static HolderLookup.Provider registries;
    private BaniraPlatform previousPlatform;
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void bootstrap() {
        NativeBootstrap.initialize();
        registries = VanillaRegistries.createLookup();
    }

    @Before
    public void installOfflineServerService() {
        previousPlatform = BaniraPlatforms.get();
        BaniraServerService offline = new BaniraServerService() {
            @Override
            public Object current() {
                return null;
            }

            @Override
            public boolean isRunning() {
                return false;
            }
        };
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(
                BaniraPlatform.class.getClassLoader(), new Class<?>[]{BaniraPlatform.class},
                (proxy, method, args) -> method.getName().equals("serverService")
                        ? offline : method.invoke(previousPlatform, args)));
    }

    @After
    public void restorePlatform() {
        BaniraPlatforms.install(previousPlatform);
    }

    @Test
    public void legacyOverworldImportsOnceAndKeepsOriginalBytes() throws Exception {
        Path world = temporary.newFolder().toPath();
        Path legacy = world.resolve("data/world_trash_data.dat");
        Path dataDir = world.resolve("dimensions/minecraft/overworld/data");
        writeLegacy(legacy, 7);
        byte[] original = Files.readAllBytes(legacy);
        try (var storage = storage(dataDir)) {
            WorldTrashData imported = WorldTrashData.get(storage, registries, world, Level.OVERWORLD);
            assertEquals(7, count(imported));
            assertSame(imported, WorldTrashData.get(storage, registries, world, Level.OVERWORLD));
            storage.saveAndJoin();
        }
        assertTrue(Files.isRegularFile(dataDir.resolve("minecraft/world_trash_data.dat")));
        assertArrayEquals(original, Files.readAllBytes(legacy));

        writeLegacy(legacy, 13);
        try (var storage = storage(dataDir)) {
            assertEquals(7, count(WorldTrashData.get(storage, registries, world, Level.OVERWORLD)));
        }
    }

    @Test
    public void netherUsesItsOwnLegacyDirectory() throws Exception {
        Path world = temporary.newFolder().toPath();
        writeLegacy(world.resolve("data/world_trash_data.dat"), 7);
        writeLegacy(world.resolve("DIM-1/data/world_trash_data.dat"), 9);
        Path dataDir = world.resolve("dimensions/minecraft/the_nether/data");
        try (var storage = storage(dataDir)) {
            assertEquals(9, count(WorldTrashData.get(storage, registries, world, Level.NETHER)));
            storage.saveAndJoin();
        }
        assertTrue(Files.isRegularFile(dataDir.resolve("minecraft/world_trash_data.dat")));
    }

    @Test
    public void unreadableLegacyDoesNotCreateAnEmptyReplacement() throws Exception {
        Path world = temporary.newFolder().toPath();
        Path legacy = world.resolve("data/world_trash_data.dat");
        Files.createDirectories(legacy.getParent());
        byte[] broken = {1, 2, 3};
        Files.write(legacy, broken);
        Path dataDir = world.resolve("dimensions/minecraft/overworld/data");
        try (var storage = storage(dataDir)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> WorldTrashData.get(storage, registries, world, Level.OVERWORLD));
            assertTrue(error.getMessage().contains(legacy.toString()));
        }
        assertArrayEquals(broken, Files.readAllBytes(legacy));
        assertFalse(Files.exists(dataDir.resolve("minecraft/world_trash_data.dat")));
    }

    private static SavedDataStorage storage(Path dataDir) {
        return new SavedDataStorage(dataDir, DataFixers.getDataFixer(), registries);
    }

    private static int count(WorldTrashData trash) {
        return trash.getInventoryList().getFirst().countItem(Items.DIAMOND);
    }

    private static void writeLegacy(Path file, int count) throws IOException {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:diamond");
        stack.putInt("count", count);
        ListTag inventory = new ListTag();
        inventory.add(stack);
        ListTag pages = new ListTag();
        pages.add(inventory);
        CompoundTag payload = new CompoundTag();
        payload.put("inventoryList", pages);
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", 3955);
        root.put("data", payload);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
    }
}
