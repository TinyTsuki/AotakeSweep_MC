package xin.vanilla.aotake.rule;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.config.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class RuleEditGuardTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void rejectsWholeRemoteBatchAndOnlyAllowsApprovedReferencesWithoutIo() throws Exception {
        try (RuleMigrationTransactionTest.Fixture f = new RuleMigrationTransactionTest.Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare(); f.transaction.commit(candidate);
            Runnable unregister = f.holder.onEdit(candidate.catalog().guard());
            byte[] before = Files.readAllBytes(f.config);
            Map<String,Object> values = new LinkedHashMap<>(); values.put("unrelated", 2);
            for (String invalid : new String[]{"tick -> tick > 1", "script:missing", "script:../escape", "minecraft:arrow;"}) {
                values.put(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList(invalid));
                assertThrows(IllegalArgumentException.class, () -> f.holder.setAll(values, ConfigEditOrigin.REMOTE));
                assertArrayEquals(before, Files.readAllBytes(f.config));
                assertEquals(Integer.valueOf(1), f.holder.get("unrelated"));
            }
            String id = candidate.catalog().sources().keySet().iterator().next();
            // Guard validates a compiled in-memory catalog; it never scans files or compiles.
            Files.delete(f.store.root().resolve("rules/sources/" + id + ".java"));
            values.put(RuleMigrationTransaction.LISTS.get(0), Arrays.asList("minecraft:*", "script:" + id));
            f.holder.setAll(values, ConfigEditOrigin.REMOTE);
            assertEquals(Integer.valueOf(2), f.holder.get("unrelated"));
            unregister.run();
        }
    }
}
