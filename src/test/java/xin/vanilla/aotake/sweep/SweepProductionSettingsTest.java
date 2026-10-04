package xin.vanilla.aotake.sweep;

import net.minecraft.server.Bootstrap;
import org.junit.Test;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import java.util.*;
import static org.junit.Assert.*;

public class SweepProductionSettingsTest {
    @Test public void snapshotPreservesNestedValuesEnumsAndCommaContainingLists() {
        net.minecraft.SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); xin.vanilla.aotake.rule.ForgeNativeTestBootstrap.initialize();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("base.batch.sweepEntityLimit", 17);
        values.put("base.chunk.chunkCheckRetain", 0.25D);
        values.put("base.dustbin.dustbinBlockMode", EnumDustbinMode.VIRTUAL);
        List<String> rules = new ArrayList<>(Arrays.asList("a,b -> a > b", "minecraft:arrow"));
        values.put("base.sweep.entityList", rules);
        values.put("base.safe.safeBlocks", Arrays.asList("minecraft:stone[foo=1,bar=2]", "mod:block.with.dot"));
        values.put("command.commandSweep", "unrelated");
        CommonConfig.BaseCategory captured = SweepConfiguration.base(new ConfigEditSnapshot(this, "test", new byte[0], values));
        rules.clear(); values.put("base.batch.sweepEntityLimit", 999);
        assertEquals(17, captured.batch().sweepEntityLimit());
        assertEquals(0.25D, captured.chunk().chunkCheckRetain(), 0D);
        assertEquals(EnumDustbinMode.VIRTUAL, captured.dustbin().dustbinBlockMode());
        assertEquals(Arrays.asList("a,b -> a > b", "minecraft:arrow"), captured.sweep().entityList());
        assertEquals(Arrays.asList("minecraft:stone[foo=1,bar=2]", "mod:block.with.dot"), captured.safe().safeBlocks());
    }
}
