package xin.vanilla.aotake.util;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RemovalLedgerTest {
    @Test public void committedEntityCannotBeRecycledTwiceAndFailedRemovalRetriesOncePerFlush() {
        RemovalLedger<String,Object> ledger = new RemovalLedger<>();
        Object entity = new Object();
        assertTrue(ledger.schedule("world", entity, false));
        assertFalse(ledger.schedule("world", entity, false));
        AtomicInteger attempts = new AtomicInteger();
        ledger.flush("world", (value, keep) -> { attempts.incrementAndGet(); return false; });
        assertEquals(1, attempts.get());
        assertTrue(ledger.contains(entity));
        ledger.flush("world", (value, keep) -> { attempts.incrementAndGet(); return true; });
        assertEquals(2, attempts.get());
        assertFalse(ledger.contains(entity));
        ledger.flush("world", (value, keep) -> { fail("Removal repeated"); return true; });
    }

    @Test public void exceptionRetainsOwnershipAndWorldStopReleasesIt() {
        RemovalLedger<String,Object> ledger = new RemovalLedger<>();
        Object entity = new Object();
        ledger.schedule("world", entity, false);
        ledger.schedule("world", entity, true);
        assertThrows(IllegalStateException.class, () -> ledger.flush("world", (value, keep) -> {
            assertTrue(keep); throw new IllegalStateException("blocked");
        }));
        assertTrue(ledger.contains(entity));
        ledger.clear(); assertFalse(ledger.contains(entity));
        ledger.flush("world", (value, keep) -> { fail("Retained after close"); return true; });
    }
}
