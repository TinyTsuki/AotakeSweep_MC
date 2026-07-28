package xin.vanilla.aotake.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * 锁定区块扫描与延迟清理使用同一套实体有效性和计数规则。
 */
public class EntityCleanupRobustnessContractTest {

    @Test
    public void emptyItemsAndStaleEntitiesCannotEnterNormalRecycling() throws Exception {
        String utils = source("AotakeUtils.java");
        String sweeper = source("EntitySweeper.java");
        String event = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/aotake/event/EventHandlerProxy.java")), StandardCharsets.UTF_8);

        assertTrue(utils.contains("prepareSweepCandidate(entity)"));
        assertTrue(utils.contains("itemEntity.getItem().isEmpty()"));
        assertTrue(sweeper.contains("AotakeUtils.prepareSweepCandidate(canonical)"));
        assertTrue(sweeper.contains("result.setItemCount(item.getCount())"));
        assertTrue(sweeper.contains("pendingRemovalOptions.putIfAbsent"));
        assertTrue(sweeper.contains("catch (RuntimeException e)"));
        assertTrue(event.contains("LevelTickEvent.Pre"));
    }

    @Test
    public void filterSeparatesEntityAndItemResourceVariables() throws Exception {
        String filter = source("EntityFilter.java");

        assertTrue(filter.contains("case \"entityResource\""));
        assertTrue(filter.contains("case \"itemResource\""));
        assertTrue(filter.contains("case \"emptyItem\""));
    }

    private static String source(String fileName) throws Exception {
        return new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/aotake/util/" + fileName)), StandardCharsets.UTF_8);
    }
}
