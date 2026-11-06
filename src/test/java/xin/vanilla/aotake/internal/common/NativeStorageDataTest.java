package xin.vanilla.aotake.internal.common;

import net.minecraft.core.component.DataComponents;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.test.NativeBootstrap;

import static org.junit.Assert.*;

public class NativeStorageDataTest {
    @BeforeClass
    public static void bootstrap() {
        NativeBootstrap.initialize();
    }

    @Test
    public void itemRoundTripPreservesTypedDataAndNativeTranslation() {
        var registries = VanillaRegistries.createLookup();
        var item = item();
        var restored = NativeStorageData.item(NativeStorageData.item(item, registries), registries);
        assertTrue(ItemStack.matches(item, restored));
        assertEquals(item.get(DataComponents.CUSTOM_DATA).copyTag(), restored.get(DataComponents.CUSTOM_DATA).copyTag());
        assertEquals(Component.translatable("item.minecraft.diamond"), restored.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    public void containerRoundTripPreservesCountsAndDistinctComponents() {
        var registries = VanillaRegistries.createLookup();
        var inventory = new SimpleContainer(54);
        inventory.setItem(0, item());
        inventory.setItem(37, new ItemStack(Items.APPLE, 27));
        var saved = NativeStorageData.inventory(inventory, registries);
        assertEquals(2, saved.size());
        var restored = new SimpleContainer(54);
        restored.setItem(0, new ItemStack(Items.DIRT, 64));
        NativeStorageData.inventory(restored, saved, registries);
        assertEquals(3, restored.countItem(Items.DIAMOND));
        assertEquals(27, restored.countItem(Items.APPLE));
        assertEquals(0, restored.countItem(Items.DIRT));
        assertEquals(saved, NativeStorageData.inventory(restored, registries));
    }

    @Test
    public void emptyAndMalformedItemsKeepTheLegacyEmptyFallback() {
        var registries = VanillaRegistries.createLookup();
        assertTrue(NativeStorageData.item(ItemStack.EMPTY, registries) instanceof CompoundTag tag && tag.isEmpty());
        assertSame(ItemStack.EMPTY, NativeStorageData.item((net.minecraft.nbt.Tag) null, registries));
        assertSame(ItemStack.EMPTY, NativeStorageData.item(new CompoundTag(), registries));
        var broken = new CompoundTag();
        broken.putString("id", "missing:deleted_item");
        assertTrue(NativeStorageData.item(broken, registries).isEmpty());
    }

    private static ItemStack item() {
        var item = new ItemStack(Items.DIAMOND, 3);
        var data = new CompoundTag();
        data.putByte("byte", (byte) 2);
        data.putInt("int", 400);
        data.putDouble("double", 1.25);
        data.putIntArray("array", new int[]{1, 7, 17});
        var capture = new CompoundTag();
        capture.putString("entityId", "minecraft:pig");
        data.put("aotake_sweep", capture);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        item.set(DataComponents.CUSTOM_NAME, Component.translatable("item.minecraft.diamond"));
        return item;
    }
}
