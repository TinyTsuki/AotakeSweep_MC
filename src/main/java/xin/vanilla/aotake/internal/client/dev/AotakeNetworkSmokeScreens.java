package xin.vanilla.aotake.internal.client.dev;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.tooltip.TooltipRequestCollector;
import xin.vanilla.banira.client.gui.widget.CollapsiblePanelWidget;
import xin.vanilla.banira.client.gui.widget.IWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Dev-only wrappers retain the real chest mixins, sidebar events and Banira editor renderers. */
public final class AotakeNetworkSmokeScreens {
    private final BooleanSupplier sampling;
    private final Metrics vanilla;
    private final Metrics color;
    private final Metrics clientConfig;
    private final Metrics playerConfig;
    private final EnumDustbinClientUiStyle previousStyle;
    private final ChestMenu menu;
    private final Component title;
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private Screen active;
    private Metrics activeMetrics;
    private PendingFrame pending;
    private boolean registered;
    private boolean closed;
    private static TooltipRequestCollector<?> tooltipRequests;
    private static Method renderInputUpdate;
    private int preservedTooltipFrames;
    private Object pendingTooltip;

    AotakeNetworkSmokeScreens(ContainerScreen opened, BooleanSupplier sampling) {
        if (!AotakeNetworkSmokeStatus.enabled()) throw new IllegalStateException("Client smoke is disabled");
        Minecraft client = Minecraft.getInstance();
        if (!DustbinRender.isDustbinTitle(opened.getTitle().getString())
                || client.player == null || client.player.containerMenu != opened.getMenu()) {
            throw new IllegalStateException("UI workload requires the actual remote dustbin menu");
        }
        this.sampling = sampling;
        this.menu = opened.getMenu();
        this.title = opened.getTitle();
        previousStyle = ClientConfig.get().dustbin().dustbinUiStyle();
        vanilla = new Metrics("dustbin-vanilla", sampling);
        color = new Metrics("dustbin-custom-color", sampling);
        clientConfig = new Metrics("client-config", sampling);
        playerConfig = new Metrics("player-config", sampling);
    }

    void open(Minecraft client) {
        MinecraftForge.EVENT_BUS.register(this);
        registered = true;
        runCycle(client, 1);
    }

    boolean readyForCycle(Minecraft client) {
        requireActive(client);
        return activeMetrics.readyForCycle();
    }

    void runCycle(Minecraft client, int cycle) {
        if (client.player == null || client.player.containerMenu != menu) {
            throw new IllegalStateException("Remote dustbin menu was replaced during UI workload");
        }
        pending = null;
        int view = (cycle - 1) % 4;
        if (view < 2) {
            ClientConfig.get().dustbin().dustbinUiStyle(view == 0
                    ? EnumDustbinClientUiStyle.VANILLA : EnumDustbinClientUiStyle.BANIRA_THEME);
            activeMetrics = view == 0 ? vanilla : color;
            active = new DustbinView(client, activeMetrics);
        } else if (view == 2) {
            activeMetrics = clientConfig;
            active = new ClientConfigView();
        } else {
            activeMetrics = playerConfig;
            active = new PlayerConfigView();
        }
        activeMetrics.beginCycle(cycle);
        client.setScreen(active);
        if (active instanceof BaniraScreen) expandPanels(((BaniraScreen) active).widgets());
    }

    private void requireActive(Minecraft client) {
        if (client.screen != active || client.getOverlay() != null) {
            throw new IllegalStateException("Smoke UI was replaced or covered before completion");
        }
        if (client.player == null || client.player.containerMenu != menu) {
            throw new IllegalStateException("Smoke dustbin is no longer the synchronized client menu");
        }
    }

    private static void expandPanels(List<IWidget> widgets) {
        for (IWidget widget : widgets) {
            if (widget instanceof CollapsiblePanelWidget) ((CollapsiblePanelWidget) widget).expanded(true);
            expandPanels(widget.children());
        }
    }

    // NORMAL priority runs Aotake's real DustbinRender hook first; Banira's LOWEST tooltip flush stays untouched.
    @SubscribeEvent(priority = EventPriority.LOW)
    public void afterDraw(ScreenEvent.Render.Post event) {
        PendingFrame frame = pending;
        pending = null;
        if (closed || frame == null || event.getScreen() != active || frame.screen != active) return;
        requireActive(Minecraft.getInstance());
        if (pendingTooltip != null) {
            if (tooltipRequests().winner() != pendingTooltip) {
                throw new IllegalStateException("Smoke tooltip did not survive to the real screen post event");
            }
            preservedTooltipFrames++;
        }
        pendingTooltip = null;
        frame.metrics.render(frame.wallNanos, frame.cpuNanos, frame.content, frame.sampled);
    }

    private void render(Screen screen, Metrics metrics, PoseStack stack, int mouseX, int mouseY,
                        float partialTicks, IWidget hovered, boolean content, RenderCall actualRender) {
        int x = hovered == null ? mouseX : (int) (hovered.absoluteX() + hovered.bounds().width() / 2.0D);
        int y = hovered == null ? mouseY : (int) (hovered.absoluteY() + hovered.bounds().height() / 2.0D);
        BaniraScreen inputScreen = hovered != null && screen instanceof BaniraScreen ? (BaniraScreen) screen : null;
        double previousX = inputScreen == null ? mouseX : inputScreen.inputState().mouseX();
        double previousY = inputScreen == null ? mouseY : inputScreen.inputState().mouseY();
        Object submittedTooltip = null;
        if (inputScreen != null) updateRenderInput(inputScreen, x, y);
        try {
            boolean sampledAtStart = sampling.getAsBoolean();
            long cpuStartedAt = cpuTime();
            long startedAt = System.nanoTime();
            actualRender.render(stack, x, y, partialTicks);
            long elapsed = System.nanoTime() - startedAt;
            long cpuEndedAt = cpuTime();
            long cpuElapsed = cpuStartedAt < 0 || cpuEndedAt < cpuStartedAt ? -1 : cpuEndedAt - cpuStartedAt;
            pending = new PendingFrame(screen, metrics, elapsed, cpuElapsed, content,
                    sampledAtStart && sampling.getAsBoolean());
            submittedTooltip = tooltipRequests().winner();
            pendingTooltip = submittedTooltip;
        } finally {
            if (inputScreen != null) updateRenderInput(inputScreen, previousX, previousY);
            if (submittedTooltip != null) {
                if (tooltipRequests().winner() != submittedTooltip) {
                    throw new IllegalStateException("Smoke input restoration discarded the submitted tooltip");
                }
            }
        }
    }

    // Dev-only runtime updater; replaying render-pre would clear deferred tooltips.
    private static void updateRenderInput(BaniraScreen screen, double mouseX, double mouseY) {
        try {
            Object state = screen.inputState();
            if (renderInputUpdate == null) {
                renderInputUpdate = state.getClass().getMethod("handleDrawScreenPre", double.class, double.class);
            }
            renderInputUpdate.invoke(state, mouseX, mouseY);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to update smoke render input", error);
        }
    }

    // Read-only dev assertion: input simulation must not reset the deferred tooltip frame.
    private static TooltipRequestCollector<?> tooltipRequests() {
        if (tooltipRequests == null) {
            try {
                Field field = TooltipWidget.class.getDeclaredField("POPUP_REQUESTS");
                field.setAccessible(true);
                tooltipRequests = (TooltipRequestCollector<?>) field.get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot inspect smoke tooltip requests", error);
            }
        }
        return tooltipRequests;
    }

    private long cpuTime() {
        return threadBean.isCurrentThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled()
                ? threadBean.getCurrentThreadCpuTime() : -1L;
    }

    boolean verified() {
        return preservedTooltipFrames > 0 && completeCoverage(vanilla.renderedCycles(), color.renderedCycles(),
                clientConfig.renderedCycles(), playerConfig.renderedCycles());
    }

    static boolean completeCoverage(int vanilla, int color, int clientConfig, int playerConfig) {
        return vanilla > 0 && color > 0 && clientConfig > 0 && playerConfig > 0
                && vanilla + color + clientConfig + playerConfig >= 20;
    }

    int completedCycles() {
        return vanilla.renderedCycles() + color.renderedCycles() + clientConfig.renderedCycles() + playerConfig.renderedCycles();
    }

    String summary() {
        return vanilla.summary() + " " + color.summary() + " " + clientConfig.summary() + " " + playerConfig.summary()
                + " styles=VANILLA,BANIRA_THEME render-timing=screen.render-wall-and-thread-cpu"
                + " excludes=screen-post-timing,gpu frame-gate=render-start,render-end,post-dustbin-hook"
                + " tooltip-preserved-through-post-frames=" + preservedTooltipFrames
                + " config-save=false pixel-verification=false";
    }

    void close(Minecraft client) {
        if (closed) return;
        closed = true;
        pending = null;
        pendingTooltip = null;
        try {
            if (registered) MinecraftForge.EVENT_BUS.unregister(this);
            if (client.screen == active) client.setScreen(null);
        } finally {
            try {
                if (client.player != null && client.player.containerMenu == menu) client.player.closeContainer();
            } finally {
                ClientConfig.get().dustbin().dustbinUiStyle(previousStyle);
            }
        }
    }

    private final class DustbinView extends ContainerScreen {
        private final Metrics metrics;

        private DustbinView(Minecraft client, Metrics metrics) {
            super(AotakeNetworkSmokeScreens.this.menu, client.player.getInventory(), AotakeNetworkSmokeScreens.this.title);
            this.metrics = metrics;
        }

        @Override
        public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            Slot content = null;
            for (int index = 0; index < menu.slots.size() - 36; index++) {
                Slot slot = menu.getSlot(index);
                if (!slot.getItem().isEmpty() && leftPos + slot.x >= 0 && topPos + slot.y >= 0
                        && leftPos + slot.x + 16 <= width && topPos + slot.y + 16 <= height) {
                    content = slot;
                    break;
                }
            }
            AotakeNetworkSmokeScreens.this.render(this, metrics, stack, mouseX, mouseY, partialTicks,
                    null, content != null, super::render);
        }

        @Override
        public void removed() {
            // Rotation is not a container close. Keep the server-opened client menu until close().
        }
    }

    private final class ClientConfigView extends ConfigEditorScreen {
        private ClientConfigView() {
            super(ClientConfig.get().holder(), new ConfigEditorScreen.Args());
        }

        @Override
        public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            IWidget content = contentWidget(this, widgets(), false);
            AotakeNetworkSmokeScreens.this.render(this, clientConfig, stack, mouseX, mouseY, partialTicks,
                    content, content != null, super::render);
        }
    }

    private final class PlayerConfigView extends PlayerConfigScreen {
        private PlayerConfigView() {
            super(null, AotakeSweep.isClientCachedShowSweepResult(), AotakeSweep.isClientCachedEnableWarningVoice());
        }

        @Override
        public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
            IWidget content = contentWidget(this, widgets(), true);
            AotakeNetworkSmokeScreens.this.render(this, playerConfig, stack, mouseX, mouseY, partialTicks,
                    content, content != null, super::render);
        }
    }

    private static IWidget contentWidget(Screen screen, List<IWidget> widgets, boolean player) {
        for (IWidget widget : widgets) {
            if (!widget.visible() || widget.bounds() == null) continue;
            String id = widget.id();
            boolean content = id != null && (player ? id.startsWith("player_prefs_") && id.endsWith("_label")
                    : id.startsWith("lbl_"));
            double x = widget.absoluteX() + widget.bounds().width() / 2.0D;
            double y = widget.absoluteY() + widget.bounds().height() / 2.0D;
            boolean visible = widget.bounds().width() > 0 && widget.bounds().height() > 0
                    && x > 0 && x < screen.width && y > 40 && y < screen.height - 40;
            for (IWidget parent = widget.parent(); visible && parent != null; parent = parent.parent()) {
                if (parent instanceof CollapsiblePanelWidget && !((CollapsiblePanelWidget) parent).expanded()) visible = false;
                if (!parent.visible() || parent.bounds() == null || x < parent.absoluteX() || y < parent.absoluteY()
                        || x >= parent.absoluteX() + parent.bounds().width()
                        || y >= parent.absoluteY() + parent.bounds().height()) visible = false;
            }
            if (content && visible) return widget;
            IWidget child = contentWidget(screen, widget.children(), player);
            if (child != null) return child;
        }
        return null;
    }

    private interface RenderCall {
        void render(PoseStack stack, int mouseX, int mouseY, float partialTicks);
    }

    private static final class PendingFrame {
        private final Screen screen;
        private final Metrics metrics;
        private final long wallNanos;
        private final long cpuNanos;
        private final boolean content;
        private final boolean sampled;

        private PendingFrame(Screen screen, Metrics metrics, long wallNanos, long cpuNanos, boolean content, boolean sampled) {
            this.screen = screen;
            this.metrics = metrics;
            this.wallNanos = wallNanos;
            this.cpuNanos = cpuNanos;
            this.content = content;
            this.sampled = sampled;
        }
    }

    static final class Metrics {
        private final String view;
        private final BooleanSupplier sampling;
        private long frames;
        private long contentFrames;
        private long wallTotal;
        private long wallMax;
        private long cpuTotal;
        private long cpuMax;
        private long cpuSamples;
        private int contentCycle;
        private int lastRenderedCycle;
        private int renderedCycles;

        Metrics(String view, BooleanSupplier sampling) {
            this.view = view;
            this.sampling = sampling;
        }

        void beginCycle(int cycle) {
            contentCycle = cycle;
        }

        boolean readyForCycle() {
            return contentCycle > 0 && lastRenderedCycle == contentCycle;
        }

        int renderedCycles() {
            return renderedCycles;
        }

        void render(long wall, long cpu, boolean content, boolean sampledAtStart) {
            if (!sampledAtStart || !sampling.getAsBoolean()) return;
            frames++;
            wallTotal += wall;
            wallMax = Math.max(wallMax, wall);
            if (cpu >= 0) {
                cpuSamples++;
                cpuTotal += cpu;
                cpuMax = Math.max(cpuMax, cpu);
            }
            if (content) {
                contentFrames++;
                if (contentCycle > 0 && contentCycle != lastRenderedCycle) {
                    lastRenderedCycle = contentCycle;
                    renderedCycles++;
                }
            }
        }

        String summary() {
            return view + "-cycles=" + renderedCycles + " " + view + "-render-frames=" + frames
                    + " " + view + "-content-render-frames=" + contentFrames
                    + " " + view + "-render-wall-average-ns=" + (frames == 0 ? 0 : wallTotal / frames)
                    + " " + view + "-render-wall-max-ns=" + wallMax
                    + " " + view + "-render-cpu-samples=" + cpuSamples
                    + " " + view + "-render-cpu-average-ns=" + (cpuSamples == 0 ? -1 : cpuTotal / cpuSamples)
                    + " " + view + "-render-cpu-max-ns=" + (cpuSamples == 0 ? -1 : cpuMax);
        }
    }
}
