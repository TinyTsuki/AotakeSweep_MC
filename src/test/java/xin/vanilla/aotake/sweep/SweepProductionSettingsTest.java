package xin.vanilla.aotake.sweep;

import net.minecraft.util.registry.Bootstrap;
import org.junit.Test;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumDustbinMode;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import java.util.*;
import static org.junit.Assert.*;

public class SweepProductionSettingsTest {
    @Test public void snapshotPreservesNestedValuesEnumsAndCommaContainingLists() {
        Bootstrap.bootStrap();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("base.batch.sweepEntityLimit", 17);
        values.put("base.chunk.chunkCheckRetain", 0.25D);
        values.put("base.dustbin.dustbinBlockMode", EnumDustbinMode.VIRTUAL);
        List<String> rules = new ArrayList<>(Arrays.asList("a,b -> a > b", "minecraft:arrow"));
        values.put("base.sweep.entityList", rules);
        CommonConfig.BaseCategory captured = SweepConfiguration.base(new ConfigEditSnapshot(this, "test", new byte[0], values));
        rules.clear(); values.put("base.batch.sweepEntityLimit", 999);
        assertEquals(17, captured.batch().sweepEntityLimit());
        assertEquals(0.25D, captured.chunk().chunkCheckRetain(), 0D);
        assertEquals(EnumDustbinMode.VIRTUAL, captured.dustbin().dustbinBlockMode());
        assertEquals(Arrays.asList("a,b -> a > b", "minecraft:arrow"), captured.sweep().entityList());
    }
}
