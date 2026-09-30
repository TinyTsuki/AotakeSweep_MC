package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.aotake.util.RemovalLedger;
import java.util.*;
import static org.junit.Assert.*;

public class SweepBatchLifecycleTest {
    @Test public void preparesWholeBatchBeforeSelfCleanAndNeverRecyclesCommittedEntityTwice() {
        Store store = new Store(); SweepBatchCommitter<Object> batch = new SweepBatchCommitter<>(store);
        Object first = new Object(), second = new Object();
        store.failAfterOwnership = second;
        SweepBackend.Progress progress = new SweepBackend.Progress(2);
        assertThrows(IllegalArgumentException.class, () -> batch.execute(Arrays.asList(first, second), progress));
        BatchResult result = progress.snapshot(false, "failed");
        assertEquals(2, result.processed()); assertEquals(2, result.pendingRemoval());
        assertEquals(1, store.items.size()); assertSame(first, store.items.get(0));
        assertTrue(store.removals.contains(second)); assertEquals(1, store.flushed);
        store.failAfterOwnership = null;
        SweepBackend.Progress retry = new SweepBackend.Progress(2);
        batch.execute(Arrays.asList(first, second), retry);
        assertEquals(2, retry.snapshot(false, null).skipped()); assertEquals(1, store.items.size());
        assertEquals(1, store.cleared); assertEquals(2, store.flushed);
        assertEquals(Arrays.asList("prepare", "clear", "commit", "commit", "flush", "prepare", "flush"), store.events);
    }

    @Test public void failedPreparationDoesNotClearOrCommitAndNextAttemptCanStart() {
        Store store = new Store(); store.failPreparation = true;
        SweepBatchCommitter<Object> batch = new SweepBatchCommitter<>(store);
        assertThrows(IllegalArgumentException.class, () -> batch.execute(Collections.singletonList(new Object()), new SweepBackend.Progress(1)));
        assertEquals(0, store.cleared); assertTrue(store.items.isEmpty()); assertEquals(1, store.flushed);
        store.failPreparation = false;
        batch.execute(Collections.emptyList(), new SweepBackend.Progress(0));
        assertEquals(1, store.cleared); assertTrue(store.items.isEmpty());
    }

    @Test public void cancellationDuringCommitKeepsLedgerAndStopsRemainingEntities() {
        Store store = new Store(); store.cancelAfterCommit = true;
        Object a = new Object(), b = new Object();
        SweepBackend.Progress progress = new SweepBackend.Progress(2);
        assertThrows(IllegalStateException.class, () -> new SweepBatchCommitter<>(store).execute(Arrays.asList(a, b), progress));
        assertEquals(1, progress.snapshot(true, null).processed()); assertEquals(1, progress.snapshot(true, null).remaining());
        assertTrue(store.removals.contains(a)); assertFalse(store.removals.contains(b)); assertEquals(1, store.flushed);
        store.removals.flush("world", (entity, keep) -> true);
        assertFalse(store.removals.contains(a));
    }

    @Test public void failureBeforeOwnershipIsCountedAndFlushFailureDoesNotReplaceOriginal() {
        Store store = new Store(); store.failBeforeOwnership = true; store.failFlush = true;
        SweepBackend.Progress progress = new SweepBackend.Progress(2);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new SweepBatchCommitter<>(store).execute(Arrays.asList(new Object(), new Object()), progress));
        assertEquals("before ownership", failure.getMessage()); assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, progress.snapshot(false, null).failed()); assertEquals(2, progress.snapshot(false, null).remaining());
        assertEquals(0, progress.processed());
    }

    @Test public void copyProtectsBatchAgainstReentrantMutation() {
        Store store = new Store(); List<Object> entities = new ArrayList<>(Arrays.asList(new Object(), new Object()));
        store.onCommit = entities::clear;
        SweepBackend.Progress progress = new SweepBackend.Progress(2);
        new SweepBatchCommitter<>(store).execute(entities, progress);
        assertEquals(2, store.items.size()); assertEquals(2, progress.processed());
    }

    @Test public void ownershipTakenDuringSerializationSkipsTransferWithoutFailingBatch() {
        Store store = new Store(); Object entity = new Object();
        store.beforeClaim = () -> assertTrue(store.removals.schedule("world", entity, false));
        SweepBackend.Progress progress = new SweepBackend.Progress(1);
        new SweepBatchCommitter<>(store).execute(Collections.singletonList(entity), progress);
        assertTrue(store.items.isEmpty()); assertTrue(store.removals.contains(entity));
        BatchResult result = progress.snapshot(false, null);
        assertEquals(0, result.processed()); assertEquals(1, result.skipped()); assertEquals(0, result.failed());
        assertEquals(0, result.pendingRemoval()); assertEquals(1, store.flushed);
    }

    static final class Store implements SweepBatchCommitter.Operations<Object> {
        final RemovalLedger<String,Object> removals = new RemovalLedger<>();
        final List<Object> items = new ArrayList<>(); final List<String> events = new ArrayList<>();
        int cleared, flushed; boolean usable = true, failPreparation, failBeforeOwnership, failFlush, cancelAfterCommit;
        Object failAfterOwnership; Runnable onCommit = () -> {}, beforeClaim = () -> {};
        public void requireUsable() { if (!usable) throw new IllegalStateException("retired"); }
        public void prepare(List<Object> entities) { events.add("prepare"); if (failPreparation) throw new IllegalArgumentException("prepare"); }
        public void begin() { cleared++; events.add("clear"); }
        public boolean eligible(Object entity) { return !removals.contains(entity); }
        public void commit(Object entity, SweepBackend.Progress progress) {
            events.add("commit"); if (failBeforeOwnership) throw new IllegalArgumentException("before ownership");
            beforeClaim.run();
            if (!removals.schedule("world", entity, false)) { progress.skipped(1); return; }
            progress.processed(1, 1, 1, true);
            if (entity == failAfterOwnership) throw new IllegalArgumentException("after ownership");
            items.add(entity); onCommit.run(); if (cancelAfterCommit) usable = false;
        }
        public void flush() { flushed++; events.add("flush"); if (failFlush) throw new IllegalStateException("flush"); }
    }
}
