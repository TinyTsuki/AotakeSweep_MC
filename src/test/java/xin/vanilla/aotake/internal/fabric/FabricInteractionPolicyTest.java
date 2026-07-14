package xin.vanilla.aotake.internal.fabric;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
