package xin.vanilla.aotake.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.AotakeSweep;

import static org.junit.Assert.*;

public class AotakeItemDataTest {
    @BeforeClass
    public static void bootstrap() {
        xin.vanilla.aotake.rule.ForgeNativeTestBootstrap.bootstrapGame();
    }

    @Test
    public void firstReadPreservesUnrelatedCustomData() {
        ItemStack item = taggedItem();
        CustomData before = item.get(DataComponents.CUSTOM_DATA);
        assertTrue(AotakeUtils.getAotakeTag(item).isEmpty());
        assertUnrelatedData(item);
        assertTrue(AotakeUtils.hasAotakeTag(item));
        assertEquals(unrelatedData(), before.copyTag());
    }

    @Test
    public void firstWritePreservesUnrelatedCustomData() {
        ItemStack item = taggedItem();
        CustomData before = item.get(DataComponents.CUSTOM_DATA);
        CompoundTag capture = captureData();
        AotakeUtils.setAotakeTag(item, capture);
        assertUnrelatedData(item);
        assertEquals(capture, AotakeUtils.getAotakeTag(item));
        assertEquals(unrelatedData(), before.copyTag());
    }

    @Test
    public void replacingCapturePreservesOtherNamespaces() {
        ItemStack item = taggedItem();
        AotakeUtils.setAotakeTag(item, captureData());
        CompoundTag replacement = new CompoundTag();
        replacement.putString("player", "captured-player");
        AotakeUtils.setAotakeTag(item, replacement);
        assertUnrelatedData(item);
        assertEquals(replacement, AotakeUtils.getAotakeTag(item));
    }

    @Test
    public void captureCopyAndReleasePreserveOriginalItemData() {
        ItemStack original = taggedItem();
        ItemStack captured = original.copy();
        captured.setCount(1);
        AotakeUtils.getAotakeTag(captured);
        AotakeUtils.setAotakeTag(captured, captureData());
        assertUnrelatedData(captured);
        assertFalse(AotakeUtils.hasAotakeTag(original));
        assertEquals(unrelatedData(), original.get(DataComponents.CUSTOM_DATA).copyTag());
        ItemStack released = captured.copy();
        AotakeUtils.clearItemTagEx(released);
        assertFalse(AotakeUtils.hasAotakeTag(released));
        assertEquals(unrelatedData(), released.get(DataComponents.CUSTOM_DATA).copyTag());
        assertEquals(captureData(), AotakeUtils.getAotakeTag(captured));
        assertEquals(3, original.getCount());
        assertEquals(1, released.getCount());
        assertEquals(Component.literal("Custom capture item"), released.get(DataComponents.CUSTOM_NAME));
    }

    @Test
    public void clearingCaptureRetainsUnrelatedData() {
        ItemStack item = taggedItem();
        CompoundTag complete = unrelatedData();
        complete.put(AotakeSweep.MODID, captureData());
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(complete));
        assertEquals(unrelatedData(), AotakeUtils.clearAotakeTag(item));
        assertEquals(unrelatedData(), item.get(DataComponents.CUSTOM_DATA).copyTag());
        assertFalse(AotakeUtils.hasAotakeTag(item));
    }

    @Test
    public void clearingLastNamespaceRemovesEmptyComponent() {
        ItemStack item = new ItemStack(Items.APPLE);
        AotakeUtils.setAotakeTag(item, captureData());
        AotakeUtils.clearItemTagEx(item);
        assertFalse(item.has(DataComponents.CUSTOM_DATA));
    }

    @Test
    public void plainItemsCanReadAndWriteCaptureData() {
        ItemStack item = new ItemStack(Items.APPLE);
        assertTrue(AotakeUtils.getAotakeTag(item).isEmpty());
        AotakeUtils.setAotakeTag(item, captureData());
        assertEquals(captureData(), AotakeUtils.getAotakeTag(item));
    }

    @Test
    public void clearingUnmarkedItemsDoesNotRemoveOtherData() {
        ItemStack item = taggedItem();
        AotakeUtils.clearItemTagEx(item);
        assertEquals(unrelatedData(), item.get(DataComponents.CUSTOM_DATA).copyTag());
        AotakeUtils.clearItemTagEx(null);
        assertNull(AotakeUtils.clearAotakeTag(null));
    }

    private static ItemStack taggedItem() {
        ItemStack item = new ItemStack(Items.APPLE, 3);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelatedData()));
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Custom capture item"));
        return item;
    }

    private static CompoundTag unrelatedData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", "other-mod");
        CompoundTag nested = new CompoundTag();
        nested.putInt("charge", 42);
        nested.putIntArray("history", new int[]{1, 3, 5});
        tag.put("other_namespace", nested);
        return tag;
    }

    private static CompoundTag captureData() {
        CompoundTag capture = new CompoundTag();
        capture.putBoolean("byPlayer", true);
        capture.putString("entityId", "minecraft:pig");
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:pig");
        capture.put("entity", entity);
        return capture;
    }

    private static void assertUnrelatedData(ItemStack item) {
        CompoundTag data = item.get(DataComponents.CUSTOM_DATA).copyTag();
        data.remove(AotakeSweep.MODID);
        assertEquals(unrelatedData(), data);
        assertEquals(Component.literal("Custom capture item"), item.get(DataComponents.CUSTOM_NAME));
    }
}
