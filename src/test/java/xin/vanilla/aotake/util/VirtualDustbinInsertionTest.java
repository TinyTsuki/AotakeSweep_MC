package xin.vanilla.aotake.util;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.registry.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.*;

public class VirtualDustbinInsertionTest {
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void insertionUsesVanillaMergingWithoutASecondSlotScan() throws Exception {
        CountingInventory inventory = new CountingInventory(2);
        inventory.setItem(0, new ItemStack(Items.APPLE, 63));
        ItemStack input = new ItemStack(Items.APPLE, 5);
        assertTrue(insert(input, inventory).isEmpty());
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(4, inventory.getItem(1).getCount());
        assertEquals(5, input.getCount());
        assertEquals(0, inventory.preflights);
    }

    @Test public void fullInventoriesPreserveTheRemainderAndContinueToLaterPages() throws Exception {
        CountingInventory full = new CountingInventory(1), next = new CountingInventory(1);
        full.setItem(0, new ItemStack(Items.APPLE, 64));
        next.setItem(0, new ItemStack(Items.APPLE, 60));
        ItemStack input = new ItemStack(Items.APPLE, 8);
        ItemStack remainder = insert(input, full, next);
        assertEquals(64, full.getItem(0).getCount());
        assertEquals(64, next.getItem(0).getCount());
        assertEquals(4, remainder.getCount());
        assertEquals(8, input.getCount());
        assertEquals(0, full.preflights + next.preflights);
    }

    @Test public void differentTagsAreNotMerged() throws Exception {
        CountingInventory inventory = new CountingInventory(1);
        ItemStack stored = new ItemStack(Items.APPLE, 1);
        CompoundNBT tag = new CompoundNBT(); tag.putString("kind", "stored"); stored.setTag(tag);
        inventory.setItem(0, stored);
        ItemStack input = new ItemStack(Items.APPLE, 2);
        assertEquals(2, insert(input, inventory).getCount());
        assertEquals(1, inventory.getItem(0).getCount());
        assertEquals("stored", inventory.getItem(0).getTag().getString("kind"));
    }

    private static ItemStack insert(ItemStack stack, Inventory... inventories) throws Exception {
        EntitySweeper sweeper = new EntitySweeper();
        Field field = EntitySweeper.class.getDeclaredField("inventoryList"); field.setAccessible(true);
        field.set(sweeper, Arrays.asList(inventories));
        Method method = EntitySweeper.class.getDeclaredMethod("addItemToVirtualDustbin", ItemStack.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(sweeper, stack);
    }

    private static final class CountingInventory extends Inventory {
        int preflights;
        CountingInventory(int size) { super(size); }
        @Override public boolean canAddItem(ItemStack stack) { preflights++; return super.canAddItem(stack); }
    }
}
