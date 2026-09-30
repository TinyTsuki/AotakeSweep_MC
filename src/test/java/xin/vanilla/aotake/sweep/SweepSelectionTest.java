package xin.vanilla.aotake.sweep;

import net.minecraft.util.math.AxisAlignedBB;
import org.junit.Test;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.internal.forge.sweep.ForgeSweepTarget;
import java.util.*;
import static org.junit.Assert.*;

public class SweepSelectionTest {
    @Test public void rangeAuthorizationPreservesHeightDimensionAndEntityKind() {
        ForgeSweepTarget target = new ForgeSweepTarget("minecraft:overworld", new AxisAlignedBB(1,2,3,9,10,11), false);
        assertTrue(target.contains("minecraft:overworld",new AxisAlignedBB(2,3,4,3,4,5),false,true));
        assertFalse(target.contains("minecraft:the_end",new AxisAlignedBB(2,3,4,3,4,5),false,true));
        assertFalse(target.contains("minecraft:overworld",new AxisAlignedBB(2,30,4,3,31,5),false,true));
        assertFalse(target.contains("minecraft:overworld",new AxisAlignedBB(2,3,4,3,4,5),false,false));
        assertFalse(target.contains("minecraft:overworld",new AxisAlignedBB(2,3,4,3,4,5),true,true));
    }
    @Test public void chunkSelectionRetainsConfiguredThresholdFractionAndFullNoticeCounts() {
        Map<String,ChunkKey> keys=new LinkedHashMap<>();
        ChunkKey a=new ChunkKey("overworld",1,2,"item"), b=new ChunkKey("end",1,2,"item");
        for(int i=0;i<6;i++) keys.put("a"+i,a);
        for(int i=0;i<4;i++) keys.put("b"+i,b);
        ChunkSweepSelection<String> selection=ChunkSweepSelection.select(new ArrayList<>(keys.keySet()),keys::get,4,0.5);
        assertEquals(Arrays.asList("a2","a3","a4","a5"),selection.selected());
        assertEquals(1,selection.groups().size()); assertEquals(6,selection.groups().get(a).size());
        assertThrows(UnsupportedOperationException.class,()->selection.groups().get(a).clear());
        assertEquals(10,keys.size());
    }
}
