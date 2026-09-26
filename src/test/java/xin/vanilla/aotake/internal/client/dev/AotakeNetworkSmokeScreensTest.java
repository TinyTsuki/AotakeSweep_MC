package xin.vanilla.aotake.internal.client.dev;

import org.junit.Test;
import net.minecraft.client.gui.GuiGraphics;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.widget.BaseWidget;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeScreensTest {
    @Test
    public void simulatedRenderHoverUpdatesChildrenAndRestoresRealPointer() {
        HoverProbe parent = new HoverProbe(20, 30, 100, 100);
        HoverProbe child = new HoverProbe(10, 10, 30, 20);
        child.parent(parent);
        assertFalse(child.inside());
        AotakeNetworkSmokeScreens.updateRenderHover(Collections.singletonList(parent), 35, 45);
        assertTrue(parent.inside());
        assertTrue(child.inside());
        AotakeNetworkSmokeScreens.updateRenderHover(Collections.singletonList(parent), 0, 0);
        assertFalse(parent.inside());
        assertFalse(child.inside());
    }

    private static final class HoverProbe extends BaseWidget {
        private HoverProbe(int x, int y, int width, int height) {
            super(null, new ScreenCoordinate(x, y, width, height));
        }

        private boolean inside() { return mouseInside; }

        @Override public void render(GuiGraphics graphics, float partialTicks) { }
    }

    @Test
    public void rejectsFramesThatStartBeforeOrFinishAfterSampling() {
        AtomicBoolean sampling = new AtomicBoolean(true);
        AotakeNetworkSmokeScreens.Metrics metrics = new AotakeNetworkSmokeScreens.Metrics("test", sampling::get);
        metrics.beginCycle(1);
        String before = metrics.summary();
        metrics.render(100, 80, true, false);
        assertEquals(before, metrics.summary());
        sampling.set(false);
        metrics.render(100, 80, true, true);
        assertEquals(before, metrics.summary());
        assertFalse(metrics.readyForCycle());
    }

    @Test
    public void countsOnlyRenderedContentAndOnlyOncePerCycle() {
        AotakeNetworkSmokeScreens.Metrics metrics = new AotakeNetworkSmokeScreens.Metrics("test", () -> true);
        metrics.beginCycle(1);
        metrics.render(100, 80, false, true);
        assertFalse(metrics.readyForCycle());
        assertEquals(0, metrics.renderedCycles());
        metrics.render(200, 120, true, true);
        metrics.render(300, 160, true, true);
        assertTrue(metrics.readyForCycle());
        assertEquals(1, metrics.renderedCycles());
        assertTrue(metrics.summary().contains("test-render-wall-average-ns=200"));
        assertTrue(metrics.summary().contains("test-render-cpu-average-ns=120"));
        metrics.beginCycle(2);
        assertFalse(metrics.readyForCycle());
        metrics.render(400, -1, true, true);
        assertEquals(2, metrics.renderedCycles());
        assertTrue(metrics.summary().contains("test-render-cpu-samples=3"));
    }

    @Test
    public void expirationCannotCompleteTheTwentiethContentCycle() {
        AtomicBoolean sampling = new AtomicBoolean(true);
        AotakeNetworkSmokeScreens.Metrics metrics = new AotakeNetworkSmokeScreens.Metrics("test", sampling::get);
        for (int cycle = 1; cycle < 20; cycle++) {
            metrics.beginCycle(cycle);
            metrics.render(100, -1, true, true);
        }
        metrics.beginCycle(20);
        sampling.set(false);
        metrics.render(100, -1, true, true);
        assertEquals(19, metrics.renderedCycles());
        assertFalse(metrics.readyForCycle());
    }

    @Test
    public void coverageRequiresEveryViewAndTwentyCompletedCycles() {
        assertFalse(AotakeNetworkSmokeScreens.completeCoverage(19, 0, 0, 1));
        assertFalse(AotakeNetworkSmokeScreens.completeCoverage(5, 5, 5, 4));
        assertTrue(AotakeNetworkSmokeScreens.completeCoverage(5, 5, 5, 5));
    }
}
