package xin.vanilla.aotake.internal.fabric;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.Test;
import xin.vanilla.aotake.test.NativeBootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FabricInteractionPolicyTest {
    @Test
    public void normalAndCandidateItemsKeepVanillaUseAvailable() {
        assertEquals(FabricInteractionPolicy.Decision.PASS,
                FabricInteractionPolicy.itemUse(false, false, false));
        assertEquals(FabricInteractionPolicy.Decision.PASS,
                FabricInteractionPolicy.itemUse(true, false, false));
    }

    @Test
    public void capturedPayloadConsumesVanillaItemUse() {
        assertEquals(FabricInteractionPolicy.Decision.CONSUME,
                FabricInteractionPolicy.itemUse(true, true, false));
        assertEquals(FabricInteractionPolicy.Decision.CONSUME,
                FabricInteractionPolicy.itemUse(true, false, true));
    }

    @Test
    public void duplicateOnlyConsumesAfterAotakeHandledInteraction() {
        assertEquals(FabricInteractionPolicy.Decision.PASS,
                FabricInteractionPolicy.duplicateEntityUse(false));
        assertEquals(FabricInteractionPolicy.Decision.CONSUME,
                FabricInteractionPolicy.duplicateEntityUse(true));
    }

    @Test
    public void successfulCaptureBlocksOnlyTheMatchingShortLivedFallback() {
        NativeBootstrap.initialize();
        var stack = new ItemStack(Items.SNOWBALL, 4);
        var fallback = new FabricInteractionPolicy.CaptureFallback(InteractionHand.MAIN_HAND, stack, 100);
        stack.shrink(1);
        assertTrue(fallback.matches(InteractionHand.MAIN_HAND, stack, 100));
        assertTrue(fallback.matches(InteractionHand.MAIN_HAND, stack, 101));
        assertFalse(fallback.matches(InteractionHand.MAIN_HAND, stack, 102));
        assertFalse(fallback.matches(InteractionHand.MAIN_HAND, stack, 99));
        assertFalse(fallback.matches(InteractionHand.OFF_HAND, stack, 100));
        assertFalse(fallback.matches(InteractionHand.MAIN_HAND, stack.copy(), 100));
    }
}
