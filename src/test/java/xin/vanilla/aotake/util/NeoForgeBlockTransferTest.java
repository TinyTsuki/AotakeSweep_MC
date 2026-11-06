package xin.vanilla.aotake.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.test.NativeBootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NeoForgeBlockTransferTest {
    @BeforeClass
    public static void initialize() {
        NativeBootstrap.initialize();
    }

    @Test
    public void transferCommitsOnlyExtractedItemsAndPreservesTheirComponents() {
        var container = new SimpleContainer(1);
        var handler = VanillaContainerWrapper.of(container);
        var tag = new CompoundTag();
        tag.putInt("vault", 7);
        var source = new ItemStack(Items.STONE, 64);
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(ItemUtil.insertItemReturnRemaining(handler, source, false, null).isEmpty());
        var remainder = ItemUtil.insertItemReturnRemaining(handler, source.copyWithCount(2), false, null);
        assertEquals(2, remainder.getCount());
        assertEquals(64, source.getCount());

        var removed = AotakeUtils.extractBlockItem(handler, 0);
        assertEquals(64, removed.getCount());
        assertEquals(tag, removed.get(DataComponents.CUSTOM_DATA).copyTag());
        assertTrue(container.getItem(0).isEmpty());
        assertTrue(AotakeUtils.extractBlockItem(handler, 0).isEmpty());
    }
}
