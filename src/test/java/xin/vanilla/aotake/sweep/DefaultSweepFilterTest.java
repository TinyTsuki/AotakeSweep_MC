package xin.vanilla.aotake.sweep;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DefaultSweepFilterTest {
    @Test public void unreachableLimitsDoNotBuildChunkCounts() {
        int[] hashes = {0};
        Object chunk = new Object() {
            @Override public int hashCode() { hashes[0]++; return 1; }
        };
        List<DefaultSweepFilter.Entry<String,Object>> entries = Arrays.asList(
                new DefaultSweepFilter.Entry<>("safe", true, true, null, chunk),
                new DefaultSweepFilter.Entry<>("other", false, false, "other", null),
                new DefaultSweepFilter.Entry<>("junk", false, true, null, null));
        assertEquals(Collections.singletonList("junk"), DefaultSweepFilter.selectEntries(entries, 3, 3));
        assertEquals(0, hashes[0]);
        assertEquals(Arrays.asList("safe", "junk"), DefaultSweepFilter.selectEntries(entries, 3, 0));
        assertTrue(hashes[0] > 0);
        assertEquals(Arrays.asList("other", "junk"), DefaultSweepFilter.selectEntries(entries, 0, 3));
    }
    private DefaultSweepFilter.Entry<String, String> entry(String id, boolean safe, boolean junk, String type, String chunk) {
        return new DefaultSweepFilter.Entry<>(id, safe, junk, type, chunk);
    }

    @Test public void safetyProtectsOrdinaryJunkButNotExceededTypeOrSafeChunkLimits() {
        Map<String, DefaultSweepFilter.Entry<String, String>> entries = new LinkedHashMap<>();
        entries.put("normal", entry("normal", false, true, null, null));
        entries.put("protected", entry("protected", true, true, null, "quiet"));
        entries.put("type1", entry("type1", false, false, "arrow", null));
        entries.put("type2", entry("type2", true, false, "arrow", "quiet"));
        entries.put("safe1", entry("safe1", true, true, null, "busy"));
        entries.put("safe2", entry("safe2", true, true, null, "busy"));
        entries.put("safe3", entry("safe3", true, true, null, "busy"));
        entries.put("otherWorld", entry("otherWorld", true, true, null, "other:busy"));
        assertEquals(Arrays.asList("normal", "type1", "type2", "safe1", "safe2", "safe3"),
                new DefaultSweepFilter<String, String>(1, 2, entries::get).select(new ArrayList<>(entries.keySet())));
    }

    @Test public void thresholdsAreStrictAndNonJunkCountsExcludeJunk() {
        Map<String, DefaultSweepFilter.Entry<String, String>> entries = new HashMap<>();
        entries.put("a", entry("a", false, false, "arrow", null));
        entries.put("b", entry("b", false, false, "arrow", null));
        entries.put("junk", entry("junk", true, true, null, "safe"));
        assertTrue(new DefaultSweepFilter<String, String>(2, 1, entries::get)
                .select(Arrays.asList("a", "b", "junk")).isEmpty());
        assertEquals(Arrays.asList("a", "b", "junk"), new DefaultSweepFilter<String, String>(1, 0, entries::get)
                .select(Arrays.asList("a", "b", "junk")));
    }

    @Test public void eachCandidateIsInspectedOnceAndRejectedEntriesDoNotAffectCounts() {
        List<String> input = new ArrayList<>(Arrays.asList("a", "invalid", "b"));
        AtomicInteger inspections = new AtomicInteger();
        DefaultSweepFilter<String, String> filter = new DefaultSweepFilter<>(2, 2, id -> {
            inspections.incrementAndGet();
            return id.equals("invalid") ? null : entry(id, false, false, "arrow", null);
        });
        assertTrue(filter.select(input).isEmpty());
        assertEquals(3, inspections.get());
        assertEquals(Arrays.asList("a", "invalid", "b"), input);
        assertTrue(filter.select(Collections.emptyList()).isEmpty());
        assertEquals(3, inspections.get());
    }
}
