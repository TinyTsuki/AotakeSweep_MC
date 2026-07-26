package xin.vanilla.aotake.internal.client.dev;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.internal.fabric.modmenu.AotakeModMenuIntegration;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.internal.common.BrigadierCommandTree;
import xin.vanilla.aotake.mixin.ContainerScreenAccessor;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.client.hud.BaniraHudEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudRenderEvent;
import xin.vanilla.banira.api.client.hud.HudOverlayElement;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.quickaction.EnumQuickActionDisplay;
import xin.vanilla.banira.client.gui.quickaction.QuickActionEntry;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.client.gui.quickaction.QuickIcon;
import xin.vanilla.banira.client.util.TextureUtils;
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
    private int experienceBarEvents;
    private int experienceTextEvents;
    private int canceledExperienceBarEvents;
    private int canceledExperienceTextEvents;
    private int dustbinStyleIndex;
    private EnumDustbinClientUiStyle originalDustbinStyle;
    private CompletableFuture<EntityScanResult> entityScan;

    private AotakeUiSmokeRunner(@Nonnull Path outputDir, boolean exitOnFinish, @Nonnull String worldName) {
        this.outputDir = outputDir;
        this.exitOnFinish = exitOnFinish;
        this.worldName = worldName;
        this.steps = Arrays.asList(
                new Step("modmenu-client-config", true, () ->
                        new AotakeModMenuIntegration().getModConfigScreenFactory().create(null)),
                new Step("player-config", true, () -> new PlayerConfigScreen(null,
                        AotakeSweep.isClientCachedShowSweepResult(),
                        AotakeSweep.isClientCachedEnableWarningVoice())),
                new Step("client-config", false, () -> new ConfigEditorScreen(
                        ClientConfig.get().holder(), new ConfigEditorScreen.Args())),
                new Step("common-config", true, () -> new ConfigEditorScreen(
                        CommonConfig.get().holder(), new ConfigEditorScreen.Args()))
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
        Minecraft client = Minecraft.getInstance();
        Path outputDir = client.gameDirectory.toPath()
                .resolve("screenshots")
                .resolve("aotake-ui-smoke")
                .resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
        instance = new AotakeUiSmokeRunner(outputDir, Boolean.getBoolean(EXIT_PROPERTY),
                System.getProperty(WORLD_PROPERTY, "").trim());
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_BAR, event -> observeHudEvent(event, true));
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_TEXT, event -> observeHudEvent(event, false));
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
            case HUD_NORMAL:
                runNormalHudTick(client);
                break;
            case HUD_HELD:
                runHeldHudTick(client);
                break;
            case QUICK_ACTION:
                runQuickActionTick(client);
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
        if (stepTick == CAPTURE_TICK && steps.get(stepIndex).capture) {
            capture(client, String.format(Locale.ROOT, "%02d-%s", stepIndex + 1, steps.get(stepIndex).name));
        }
        if (stepTick >= STEP_TICKS) {
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
        if (!AotakeLang.get().getI18nFiles().contains("zh_cn")) {
            throw new IllegalStateException("Bundled zh_cn language was not discovered");
        }
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
            Path levelData = client.gameDirectory.toPath().resolve("saves").resolve(worldName).resolve("level.dat");
            if (Files.isRegularFile(levelData)) {
                client.loadLevel(worldName);
            } else {
                createSmokeWorld(client);
            }
        } catch (Throwable t) {
            fail(client, "world-load", t);
        }
    }

    /**
     * 创建不含 Forge 数据包残留的专用 Fabric 烟测世界。
     */
    private void createSmokeWorld(@Nonnull Minecraft client) {
        RegistryAccess.RegistryHolder registries = RegistryAccess.builtin();
        LevelSettings levelSettings = new LevelSettings(
                worldName,
                GameType.SURVIVAL,
                false,
                Difficulty.NORMAL,
                true,
                new GameRules(),
                DataPackConfig.DEFAULT
        );
        WorldGenSettings worldGenSettings = WorldGenSettings.makeDefault(
                registries.dimensionTypes(),
                registries.registryOrThrow(Registry.BIOME_REGISTRY),
                registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY)
        );
        client.createLevel(worldName, levelSettings, registries, worldGenSettings);
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
            List<Entity> all = xin.vanilla.banira.common.util.EntityUtils.getAllEntities();
            int filtered = AotakeUtils.getAllEntitiesByFilter(all, false).size();
            boolean vanillaItemUsePasses = verifyVanillaItemUsePasses(client);
            return new EntityScanResult(all.size(), filtered, System.nanoTime() - start, vanillaItemUsePasses);
        }, client.getSingleplayerServer());
        phase = Phase.ENTITY_SCAN;
        phaseTick = 0;
    }

    /**
     * 实际执行一次全服实体过滤，既覆盖功能，也记录本轮耗时供性能回归对比。
     */
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
            if (!result.vanillaItemUsePasses) {
                throw new IllegalStateException("Fabric item-use callback consumed a normal item");
            }
            appendStatus("PASS vanilla-item-use");
        } catch (RuntimeException e) {
            fail(client, "entity-scan", e);
            return;
        }
        if (client.player != null) {
            // 非零等级才会走到 1.16.5 的经验文本绘制分支。
            client.player.experienceLevel = 7;
            client.player.experienceProgress = 0.5F;
        }
        resetHudObservations();
        phase = Phase.HUD_NORMAL;
        phaseTick = 0;
    }

    private boolean verifyVanillaItemUsePasses(@Nonnull Minecraft client) {
        net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return false;
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ItemStack original = player.getMainHandItem().copy();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            return UseItemCallback.EVENT.invoker()
                    .interact(player, player.getLevel(), InteractionHand.MAIN_HAND)
                    .getResult() == InteractionResult.PASS;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
    }

    private void runNormalHudTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (phaseTick == 30) {
            try {
                assertHudContract(false);
            } catch (RuntimeException e) {
                fail(client, "hud-normal-events", e);
                return;
            }
            appendStatus("PASS hud-normal-events");
            capture(client, "04-gameplay-hud-normal");
            try {
                int progressKey = ClientModEventHandler.PROGRESS_KEY.currentKey();
                int conflictingPlayerListKey = client.options.keyPlayerList.matches(progressKey, 0)
                        ? progressKey : Integer.MIN_VALUE;
                validateProgressKey(progressKey, conflictingPlayerListKey);
            } catch (IllegalStateException e) {
                fail(client, "hud-key-conflict", e);
                return;
            }
            setProgressKey(true);
            resetHudObservations();
            phase = Phase.HUD_HELD;
            phaseTick = 0;
        }
    }

    private void runHeldHudTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (phaseTick == 30) {
            try {
                assertHudContract(true);
            } catch (RuntimeException e) {
                fail(client, "hud-held-events", e);
                return;
            }
            appendStatus("PASS hud-held-events");
            capture(client, "05-gameplay-hud-progress");
            setProgressKey(false);
            client.setScreen(new InventoryScreen(client.player));
            phase = Phase.QUICK_ACTION;
            phaseTick = 0;
        }
    }

    private void runQuickActionTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (!(client.screen instanceof InventoryScreen)) {
            fail(client, "quick-action", new IllegalStateException("Inventory screen did not remain open"));
            return;
        }
        if (phaseTick < 20) return;
        try {
            QuickActionEntry entry = QuickActionRegistry.get().getEntry(AotakeSweep.MODID + ":quick");
            if (entry == null || entry.display() != EnumQuickActionDisplay.ICON
                    || entry.quickIcon().kind() != QuickIcon.Kind.RESOURCE
                    || entry.quickIcon().texture() == null
                    || entry.quickIcon().texture().uvWidth() <= 0
                    || entry.quickIcon().texture().uvHeight() <= 0
                    || !TextureUtils.isTextureAvailable(entry.quickIcon().texture().location())) {
                throw new IllegalStateException("Aotake resource quick action is unavailable");
            }
        } catch (RuntimeException e) {
            fail(client, "quick-action", e);
            return;
        }
        capture(client, "06-inventory-quick-actions");
        appendStatus("PASS quick-action-resource-icon");
        client.setScreen(null);
        originalDustbinStyle = ClientConfig.get().dustbin().dustbinUiStyle();
        dustbinStyleIndex = 0;
        phase = Phase.DUSTBIN;
        openDustbinStyle(client);
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
                        7 + dustbinStyleIndex, style.name().toLowerCase(Locale.ROOT));
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

    private static void setProgressKey(boolean down) {
        int keyCode = ClientModEventHandler.PROGRESS_KEY.currentKey();
        KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(keyCode), down);
        LOGGER.info("Aotake UI smoke progress key={} down={} vanillaPlayerListDown={}", keyCode, down,
                Minecraft.getInstance().options.keyPlayerList.isDown());
    }

    static void validateProgressKey(int progressKey, int playerListKey) {
        if (progressKey == playerListKey) {
            throw new IllegalStateException("Progress key conflicts with the vanilla player-list key: " + progressKey);
        }
    }

    private static void observeHudEvent(BaniraHudRenderEvent event, boolean bar) {
        AotakeUiSmokeRunner runner = instance;
        if (runner == null) {
            return;
        }
        if (bar) {
            runner.experienceBarEvents++;
            if (event.canceled()) runner.canceledExperienceBarEvents++;
        } else {
            runner.experienceTextEvents++;
            if (event.canceled()) runner.canceledExperienceTextEvents++;
        }
    }

    /**
     * 每种样式都重新初始化容器，覆盖布局 Mixin 与初始化后按钮注入。
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
            ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
            int left = accessor.aotake$getLeftPos();
            long sidebarButtons = Screens.getButtons(screen).stream()
                    .filter(button -> button.x < left)
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

    private void resetHudObservations() {
        experienceBarEvents = 0;
        experienceTextEvents = 0;
        canceledExperienceBarEvents = 0;
        canceledExperienceTextEvents = 0;
    }

    private void assertHudContract(boolean expectedCanceled) {
        if (experienceBarEvents <= 0 || experienceTextEvents <= 0) {
            throw new IllegalStateException("Experience HUD events were not both observed: bar="
                    + experienceBarEvents + ", text=" + experienceTextEvents);
        }
        boolean allCanceled = canceledExperienceBarEvents == experienceBarEvents
                && canceledExperienceTextEvents == experienceTextEvents;
        boolean noneCanceled = canceledExperienceBarEvents == 0 && canceledExperienceTextEvents == 0;
        if ((expectedCanceled && !allCanceled) || (!expectedCanceled && !noneCanceled)) {
            throw new IllegalStateException("Unexpected experience HUD cancellation state: bar="
                    + canceledExperienceBarEvents + "/" + experienceBarEvents + ", text="
                    + canceledExperienceTextEvents + "/" + experienceTextEvents);
        }
    }

    private void capture(@Nonnull Minecraft client, @Nonnull String name) {
        Path file = outputDir.resolve(name + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(
                client.getWindow().getWidth(), client.getWindow().getHeight(), client.getMainRenderTarget())) {
            image.writeToFile(file);
            appendStatus("PASS " + name);
            LOGGER.info("Aotake UI smoke screenshot: {}", file);
        } catch (Throwable t) {
            fail(client, name, t);
        }
    }

    private void finish(@Nonnull Minecraft client) {
        phase = Phase.FINISHED;
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
        setProgressKey(false);
        restoreDustbinStyle();
        appendStatus("FAILED " + step + ": " + error);
        LOGGER.error("Aotake UI smoke failed at {}", step, error);
        if (exitOnFinish) {
            client.stop();
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
            Files.write(outputDir.resolve("status.txt"),
                    (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.warn("Failed to write Aotake UI smoke status", e);
        }
    }

    private static final class Step {
        private final String name;
        private final boolean capture;
        private final Supplier<Screen> screenFactory;

        private Step(@Nonnull String name, boolean capture, @Nonnull Supplier<Screen> screenFactory) {
            this.name = name;
            this.capture = capture;
            this.screenFactory = screenFactory;
        }
    }

    private static final class EntityScanResult {
        private final int total;
        private final int filtered;
        private final long elapsedNanos;
        private final boolean vanillaItemUsePasses;

        private EntityScanResult(int total, int filtered, long elapsedNanos, boolean vanillaItemUsePasses) {
            this.total = total;
            this.filtered = filtered;
            this.elapsedNanos = elapsedNanos;
            this.vanillaItemUsePasses = vanillaItemUsePasses;
        }
    }

    private enum Phase {
        WAITING,
        UI,
        WORLD_LOADING,
        ENTITY_SCAN,
        HUD_NORMAL,
        HUD_HELD,
        QUICK_ACTION,
        DUSTBIN,
        FINISHED
    }
}
