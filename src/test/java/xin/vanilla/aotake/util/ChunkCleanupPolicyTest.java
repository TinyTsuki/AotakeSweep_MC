package xin.vanilla.aotake.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChunkCleanupPolicyTest {
    @Test
    public void retainsRatioOfConfiguredLimit() {
        assertEquals(200, ChunkCleanupPolicy.retainedCount(1245, 250, 0.8));
        assertEquals(1045, ChunkCleanupPolicy.removalCount(1245, 250, 0.8));
    }

    @Test
    public void clampsRatioAndAvailableEntities() {
        assertEquals(0, ChunkCleanupPolicy.retainedCount(100, 250, -1.0));
        assertEquals(100, ChunkCleanupPolicy.retainedCount(100, 250, 2.0));
    }
}
