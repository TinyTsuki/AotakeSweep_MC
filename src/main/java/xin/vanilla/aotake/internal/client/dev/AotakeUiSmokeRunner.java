package xin.vanilla.aotake.internal.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.internal.common.BrigadierCommandTree;
import xin.vanilla.aotake.mixin.ContainerScreenAccessor;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.*;
import xin.vanilla.banira.client.gui.component.Notification;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.util.NotificationManager;
import xin.vanilla.banira.common.data.Color;
import xin.vanilla.banira.common.enums.EnumMoveType;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.banira.common.util.EnvironmentUtils;
import xin.vanilla.banira.common.util.PacketUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Aotake 开发环境界面冒烟测试：检查 Banira 接入并自动保存关键界面截图。
 */
public final class AotakeUiSmokeRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ENABLE_PROPERTY = "aotake.uiSmoke";
    private static final String EXIT_PROPERTY = "aotake.uiSmoke.exitOnFinish";
    private static final String WORLD_PROPERTY = "aotake.uiSmoke.world";
    private static final int START_DELAY_TICKS = 80;
    private static final int STEP_TICKS = 36;
    private static final int CAPTURE_TICK = 28;
    private static final int WORLD_LOAD_TIMEOUT_TICKS = 1200;
    private static final int NETWORK_SYNC_TIMEOUT_TICKS = 240;
    private static final int DUSTBIN_TIMEOUT_TICKS = 240;
    private static final int NOTIFICATION_SMOKE_BG = 0xFFEA00FF;
    private static final EnumDustbinClientUiStyle[] DUSTBIN_STYLES = {
            EnumDustbinClientUiStyle.VANILLA,
            EnumDustbinClientUiStyle.TEXTURED,
            EnumDustbinClientUiStyle.BANIRA_THEME
    };

    private static AotakeUiSmokeRunner instance;

    private final Path outputDir;
    private final boolean exitOnFinish;
    private final String worldName;
    private final List<Step> steps;
    private Phase phase = Phase.WAITING;
    private int tick;
    private int stepIndex = -1;
    private int stepTick;
    private int phaseTick;
    private int readyTick;
    private int dustbinStyleIndex;
    private EnumDustbinClientUiStyle originalDustbinStyle;
    private CompletableFuture<EntityScanResult> entityScan;
    private Notification hudSmokeNotification;

    private AotakeUiSmokeRunner(@Nonnull Path outputDir, boolean exitOnFinish, @Nonnull String worldName) {
        this.outputDir = outputDir;
        this.exitOnFinish = exitOnFinish;
        this.worldName = worldName;
        this.steps = Arrays.asList(
                new Step("aotake-player-config", () -> new PlayerConfigScreen(null,
                        AotakeSweep.isClientCachedShowSweepResult(),
                        AotakeSweep.isClientCachedEnableWarningVoice())),
                new Step("banira-player-config", () -> new CustomPlayerConfigEditScreen(
                        new CustomPlayerConfigEditScreen.Args())),
                new Step("client-config", () -> new ConfigEditorScreen(
                        ClientConfig.get().holder(), new ConfigEditorScreen.Args())),
                new Step("notification-type-config", () -> new NotificationTypeConfigScreen(
                        new NotificationTypeConfigScreen.Args())),
                new Step("notification-color-log", AotakeUiSmokeRunner::createNotificationColorLogScreen),
                new Step("common-config", () -> new ConfigEditorScreen(
                        CommonConfig.get().holder(), new ConfigEditorScreen.Args())),
                new Step("banira-long-press", LongPressSmokeScreen::new)
        );
    }

    public static void register() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        if (!EnvironmentUtils.isDevelopment()) {
            LOGGER.warn("Aotake UI smoke requested outside development environment; ignored");
            return;
        }
        if (instance != null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Path outputDir = client.gameDirectory.toPath()
                .resolve("screenshots")
                .resolve("aotake-ui-smoke")
                .resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
        instance = new AotakeUiSmokeRunner(outputDir, Boolean.getBoolean(EXIT_PROPERTY),
                System.getProperty(WORLD_PROPERTY, "").trim());
        LOGGER.info("Aotake UI smoke registered; output directory: {}", outputDir);
    }

    public static void tick(@Nonnull Minecraft client) {
        if (instance != null) {
            instance.runTick(client);
        }
    }

    private void runTick(@Nonnull Minecraft client) {
        if (phase == Phase.FINISHED) {
            return;
        }
        tick++;
        switch (phase) {
            case WAITING:
                if (tick >= START_DELAY_TICKS) {
                    start(client);
                }
                break;
            case UI:
                runUiTick(client);
                break;
            case WORLD_LOADING:
                runWorldLoadingTick(client);
                break;
            case ENTITY_SCAN:
                runEntityScanTick(client);
                break;
            case HUD_NOTIFICATION:
                runNotificationHudTick(client);
                break;
            case HUD_NORMAL:
                runNormalHudTick(client);
                break;
            case HUD_HELD:
                runHeldHudTick(client);
                break;
            case DUSTBIN:
                runDustbinTick(client);
                break;
            default:
                break;
        }
    }

    private void runUiTick(@Nonnull Minecraft client) {
        stepTick++;
        if (client.screen instanceof LongPressSmokeScreen) {
            runLongPressUiTick(client, (LongPressSmokeScreen) client.screen);
            return;
        }
        if (stepTick == CAPTURE_TICK) {
            capture(client, String.format(Locale.ROOT, "%02d-%s", stepIndex + 1, steps.get(stepIndex).name));
        }
        if (stepTick >= STEP_TICKS) {
            enterNextStep(client);
        }
    }

    private void runLongPressUiTick(@Nonnull Minecraft client, LongPressSmokeScreen screen) {
        if (stepTick == 4) {
            screen.press();
            appendStatus("PRESS banira-long-press");
        } else if (stepTick == 12) {
            capture(client, String.format(Locale.ROOT, "%02d-long-press-progress", stepIndex + 1));
        } else if (stepTick == 24) {
            screen.release();
            if (!screen.fired()) {
                fail(client, "banira-long-press",
                        new IllegalStateException("Long-press callback did not fire"));
                return;
            }
            capture(client, String.format(Locale.ROOT, "%02d-long-press-complete", stepIndex + 1));
            appendStatus("PASS banira-long-press");
        } else if (stepTick >= 28) {
            enterNextStep(client);
        }
    }

    private void start(@Nonnull Minecraft client) {
        try {
            Files.createDirectories(outputDir);
            appendStatus("STARTED " + LocalDateTime.now());
            validateIntegration();
            appendStatus("PASS integration");
        } catch (Throwable t) {
            fail(client, "integration", t);
            return;
        }
        stepIndex = 0;
        phase = Phase.UI;
        enterStep(client);
    }

    /**
     * 先访问关键公共 API，尽早暴露配置和键位注册时序问题。
     */
    private static void validateIntegration() {
        if (ClientConfig.get().holder() == null || CommonConfig.get().holder() == null) {
            throw new IllegalStateException("Aotake config holder is not registered");
        }
        ClientModEventHandler.DUSTBIN_KEY.currentKey();
        ClientModEventHandler.DUSTBIN_PRE_KEY.currentKey();
        ClientModEventHandler.DUSTBIN_NEXT_KEY.currentKey();
        ClientModEventHandler.PROGRESS_KEY.currentKey();
        validateCommandRootRemoval();
    }

    private static void validateCommandRootRemoval() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandNode<CommandSourceStack> node = dispatcher.register(Commands.literal("aotake_smoke_root"));
        BrigadierCommandTree.removeRoot(dispatcher, "aotake_smoke_root", node);
        if (dispatcher.getRoot().getChild("aotake_smoke_root") != null) {
            throw new IllegalStateException("Brigadier command root was not removed");
        }
    }

    private void enterNextStep(@Nonnull Minecraft client) {
        stepIndex++;
        if (stepIndex >= steps.size()) {
            if (worldName.isEmpty()) {
                finish(client);
            } else {
                beginWorldSmoke(client);
            }
            return;
        }
        enterStep(client);
    }

    private void enterStep(@Nonnull Minecraft client) {
        stepTick = 0;
        Step step = steps.get(stepIndex);
        try {
            Screen screen = step.screenFactory.get();
            client.setScreen(screen);
            appendStatus("OPEN " + step.name);
            LOGGER.info("Aotake UI smoke step: {}", step.name);
        } catch (Throwable t) {
            fail(client, step.name, t);
        }
    }

    private void beginWorldSmoke(@Nonnull Minecraft client) {
        phase = Phase.WORLD_LOADING;
        phaseTick = 0;
        readyTick = 0;
        client.setScreen(null);
        appendStatus("LOAD world " + worldName);
        LOGGER.info("Aotake UI smoke loading world: {}", worldName);
        try {
            client.createWorldOpenFlows().loadLevel(client.screen, worldName);
        } catch (Throwable t) {
            fail(client, "world-load", t);
        }
    }

    private void runWorldLoadingTick(@Nonnull Minecraft client) {
        phaseTick++;
        boolean ready = client.player != null && client.level != null
                && client.getSingleplayerServer() != null && client.screen == null;
        if (!ready) {
            if (phaseTick >= WORLD_LOAD_TIMEOUT_TICKS) {
                fail(client, "world-load", new IllegalStateException("Timed out loading world " + worldName));
            }
            return;
        }

        readyTick++;
        if (AotakeSweep.getClientServerTime().val() <= 0L) {
            if (readyTick >= NETWORK_SYNC_TIMEOUT_TICKS) {
                fail(client, "login-sync", new IllegalStateException("Sweep data was not synchronized"));
            }
            return;
        }

        appendStatus("PASS world-load");
        appendStatus("PASS login-sync");
        entityScan = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            List<net.minecraft.world.entity.Entity> all = AotakeUtils.getAllEntities();
            int filtered = AotakeUtils.getAllEntitiesByFilter(all, false).size();
            long elapsedNanos = System.nanoTime() - start;
            return new EntityScanResult(all.size(), filtered, elapsedNanos);
        }, client.getSingleplayerServer());
        phase = Phase.ENTITY_SCAN;
        phaseTick = 0;
    }

    private void runEntityScanTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (!entityScan.isDone()) {
            if (phaseTick >= NETWORK_SYNC_TIMEOUT_TICKS) {
                fail(client, "entity-scan", new IllegalStateException("Entity scan timed out"));
            }
            return;
        }
        try {
            EntityScanResult result = entityScan.join();
            appendStatus(String.format(Locale.ROOT,
                    "PASS entity-scan total=%d filtered=%d elapsedMs=%.3f",
                    result.total, result.filtered, result.elapsedNanos / 1_000_000.0));
        } catch (RuntimeException e) {
            fail(client, "entity-scan", e);
            return;
        }
        beginNotificationHudSmoke();
    }

    private static Screen createNotificationColorLogScreen() {
        Notification notification = Notification.ofComponent(
                BaniraComponent.get().literal(
                        "\u00A7c红 \u00A76橙 \u00A7e黄 \u00A7a绿 "
                                + "\u00A7b青 \u00A79蓝 \u00A7d紫 \u00A77灰"));
        notification.notificationType("aotake_sweep:color_contrast_smoke");
        notification.position(EnumPosition.TOP_RIGHT);
        notification.durationTime(10_000);
        NotificationManager.get().addNotification(notification);
        return new NotificationLogScreen(new NotificationLogScreen.Args());
    }

    /**
     * 使用唯一背景色确认通知确实经过无 Screen HUD 回调进入帧缓冲。
     */
    private void beginNotificationHudSmoke() {
        Notification notification = Notification.ofComponentWithBlack(
                BaniraComponent.get().literal("Aotake HUD notification smoke"));
        notification.bgColor(Color.argb(NOTIFICATION_SMOKE_BG));
        notification.borderColor(Color.argb(NOTIFICATION_SMOKE_BG));
        notification.position(EnumPosition.TOP_RIGHT);
        notification.animation(EnumMoveType.FADE_IN);
        notification.animationTime(1);
        notification.durationTime(10_000);
        notification.notificationType("aotake_sweep:ui_smoke");
        hudSmokeNotification = notification;
        NotificationManager.get().addNotification(notification);
        appendStatus("SEND notification-hud-without-screen");
        phase = Phase.HUD_NOTIFICATION;
        phaseTick = 0;
    }

    private void runNotificationHudTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (client.screen != null) {
            fail(client, "notification-hud-without-screen",
                    new IllegalStateException("A screen opened during HUD notification smoke"));
            return;
        }
        if (phaseTick < 20) {
            return;
        }
        Path file = outputDir.resolve("05-notification-hud.png");
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int smokePixels = countNotificationSmokePixels(image);
            if (smokePixels < 32) {
                throw new IllegalStateException("Notification color was absent from framebuffer: " + smokePixels);
            }
            image.writeToFile(file);
            appendStatus("PASS notification-hud-without-screen pixels=" + smokePixels);
            appendStatus("PASS 05-notification-hud");
            LOGGER.info("Aotake UI smoke screenshot: {}", file);
        } catch (Throwable t) {
            fail(client, "notification-hud-without-screen", t);
            return;
        }
        hudSmokeNotification.dismiss();
        hudSmokeNotification = null;
        phase = Phase.HUD_NORMAL;
        phaseTick = 0;
    }

    private static int countNotificationSmokePixels(@Nonnull NativeImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int red = abgr & 0xFF;
                int green = (abgr >> 8) & 0xFF;
                int blue = (abgr >> 16) & 0xFF;
                if (red >= 225 && green <= 24 && blue >= 245) {
                    count++;
                }
            }
        }
        return count;
    }

    private void runNormalHudTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (phaseTick == 30) {
            capture(client, "04-gameplay-hud-normal");
            setProgressKey(true);
            phase = Phase.HUD_HELD;
            phaseTick = 0;
        }
    }

    private void runHeldHudTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (phaseTick == 30) {
            capture(client, "05-gameplay-hud-progress");
            setProgressKey(false);
            originalDustbinStyle = ClientConfig.get().dustbin().dustbinUiStyle();
            dustbinStyleIndex = 0;
            phase = Phase.DUSTBIN;
            openDustbinStyle(client);
        }
    }

    private void runDustbinTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (client.screen instanceof AbstractContainerScreen) {
            readyTick++;
            if (readyTick >= 20) {
                EnumDustbinClientUiStyle style = DUSTBIN_STYLES[dustbinStyleIndex];
                try {
                    assertDustbinStyle((AbstractContainerScreen<?>) client.screen, style);
                } catch (RuntimeException e) {
                    fail(client, "dustbin-" + style.name().toLowerCase(Locale.ROOT), e);
                    return;
                }
                String name = String.format(Locale.ROOT, "%02d-dustbin-%s",
                        6 + dustbinStyleIndex, style.name().toLowerCase(Locale.ROOT));
                capture(client, name);
                appendStatus("PASS dustbin-" + style.name().toLowerCase(Locale.ROOT));
                dustbinStyleIndex++;
                if (dustbinStyleIndex >= DUSTBIN_STYLES.length) {
                    finish(client);
                } else {
                    openDustbinStyle(client);
                }
            }
            return;
        }
        readyTick = 0;
        if (phaseTick >= DUSTBIN_TIMEOUT_TICKS) {
            fail(client, "open-dustbin", new IllegalStateException("Dustbin container did not open"));
        }
    }

    /**
     * 每种样式都重新初始化容器，同时覆盖布局 Mixin 与按钮注入。
     */
    private void openDustbinStyle(@Nonnull Minecraft client) {
        EnumDustbinClientUiStyle style = DUSTBIN_STYLES[dustbinStyleIndex];
        ClientConfig.get().dustbin().dustbinUiStyle(style);
        DustbinGuiLayoutCache.invalidate();
        client.setScreen(null);
        PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
        appendStatus("SEND open-dustbin style=" + style.name());
        phaseTick = 0;
        readyTick = 0;
    }

    private static void assertDustbinStyle(AbstractContainerScreen<?> screen,
                                           EnumDustbinClientUiStyle style) {
        if (!(screen instanceof ContainerScreen) || !DustbinRender.isDustbinTitle(screen.getTitle().getString())) {
            throw new IllegalStateException("Opened container was not recognized as dustbin: "
                    + screen.getTitle().getString());
        }
        if (ClientConfig.get().dustbin().dustbinUiStyle() != style) {
            throw new IllegalStateException("Dustbin style did not switch to " + style);
        }
        if (style == EnumDustbinClientUiStyle.VANILLA) {
            int left = ((ContainerScreenAccessor) screen).aotake$getLeftPos();
            long sidebarButtons = screen.children().stream()
                    .filter(child -> child instanceof Button && ((Button) child).getX() < left)
                    .count();
            if (sidebarButtons < 3) {
                throw new IllegalStateException("Vanilla dustbin sidebar is incomplete: " + sidebarButtons);
            }
        } else if (style == EnumDustbinClientUiStyle.TEXTURED
                && (!DustbinGuiLayoutCache.valid || DustbinGuiLayoutCache.drawWidth <= 0
                || DustbinGuiLayoutCache.drawHeight <= 0)) {
            throw new IllegalStateException("Textured dustbin layout was not initialized");
        }
    }

    private static void setProgressKey(boolean down) {
        int keyCode = ClientModEventHandler.PROGRESS_KEY.currentKey();
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(keyCode), down);
        LOGGER.info("Aotake UI smoke progress key={} down={} vanillaPlayerListDown={}", keyCode, down,
                Minecraft.getInstance().options.keyPlayerList.isDown());
    }

    private void capture(@Nonnull Minecraft client, @Nonnull String name) {
        Path file = outputDir.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(file);
            appendStatus("PASS " + name);
            LOGGER.info("Aotake UI smoke screenshot: {}", file);
        } catch (Throwable t) {
            fail(client, name, t);
        }
    }

    private void finish(@Nonnull Minecraft client) {
        phase = Phase.FINISHED;
        dismissHudSmokeNotification();
        setProgressKey(false);
        restoreDustbinStyle();
        appendStatus("FINISHED " + LocalDateTime.now());
        LOGGER.info("Aotake UI smoke finished; screenshots are in {}", outputDir);
        client.setScreen(null);
        if (exitOnFinish) {
            client.stop();
        }
    }

    private void fail(@Nonnull Minecraft client, @Nonnull String step, @Nonnull Throwable error) {
        phase = Phase.FINISHED;
        dismissHudSmokeNotification();
        setProgressKey(false);
        restoreDustbinStyle();
        appendStatus("FAILED " + step + ": " + error);
        LOGGER.error("Aotake UI smoke failed at {}", step, error);
        if (exitOnFinish) {
            client.stop();
        }
    }

    private void dismissHudSmokeNotification() {
        if (hudSmokeNotification != null) {
            hudSmokeNotification.dismiss();
            hudSmokeNotification = null;
        }
    }

    private void restoreDustbinStyle() {
        if (originalDustbinStyle != null) {
            ClientConfig.get().dustbin().dustbinUiStyle(originalDustbinStyle);
            originalDustbinStyle = null;
        }
    }

    private void appendStatus(@Nonnull String line) {
        try {
            Files.writeString(outputDir.resolve("status.txt"), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Failed to write Aotake UI smoke status", e);
        }
    }

    private static final class Step {
        private final String name;
        private final Supplier<Screen> screenFactory;

        private Step(@Nonnull String name, @Nonnull Supplier<Screen> screenFactory) {
            this.name = name;
            this.screenFactory = screenFactory;
        }
    }

    /**
     * 由 smoke runner 实际按下和释放，验证 ButtonWidget 的运行时状态机。
     */
    private static final class LongPressSmokeScreen extends BaniraScreen {
        private ButtonWidget button;
        private boolean fired;

        private LongPressSmokeScreen() {
            super(BaniraComponent.get().literal("Banira long-press smoke").toVanilla());
        }

        @Override
        protected void initWidgets() {
            button = new ButtonWidget(this);
            button.id("smoke_long_press");
            button.bounds(new ScreenCoordinate(width / 2.0 - 70, height / 2.0 - 12, 140, 24));
            button.text("长按运行时测试");
            button.onLongPress(600L, ignored -> fired = true);
            addWidget(button);
        }

        @Override
        protected void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderWidgets(graphics, partialTick);
        }

        private void press() {
            mouseClicked(button.absoluteX() + button.width() / 2,
                    button.absoluteY() + button.height() / 2, 0);
        }

        private void release() {
            mouseReleased(button.absoluteX() + button.width() / 2,
                    button.absoluteY() + button.height() / 2, 0);
        }

        private boolean fired() {
            return fired;
        }
    }

    private static final class EntityScanResult {
        private final int total;
        private final int filtered;
        private final long elapsedNanos;

        private EntityScanResult(int total, int filtered, long elapsedNanos) {
            this.total = total;
            this.filtered = filtered;
            this.elapsedNanos = elapsedNanos;
        }
    }

    private enum Phase {
        WAITING,
        UI,
        WORLD_LOADING,
        ENTITY_SCAN,
        HUD_NOTIFICATION,
        HUD_NORMAL,
        HUD_HELD,
        DUSTBIN,
        FINISHED
    }
}
