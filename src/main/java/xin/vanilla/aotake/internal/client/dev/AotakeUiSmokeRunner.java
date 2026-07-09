package xin.vanilla.aotake.internal.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
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

    private AotakeUiSmokeRunner(@Nonnull Path outputDir, boolean exitOnFinish, @Nonnull String worldName) {
        this.outputDir = outputDir;
        this.exitOnFinish = exitOnFinish;
        this.worldName = worldName;
        this.steps = Arrays.asList(
                new Step("player-config", () -> new PlayerConfigScreen(null,
                        AotakeSweep.isClientCachedShowSweepResult(),
                        AotakeSweep.isClientCachedEnableWarningVoice())),
                new Step("client-config", () -> new ConfigEditorScreen(
                        ClientConfig.get().holder(), new ConfigEditorScreen.Args())),
                new Step("common-config", () -> new ConfigEditorScreen(
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
        if (stepTick == CAPTURE_TICK) {
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
        phase = Phase.HUD_NORMAL;
        phaseTick = 0;
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
            PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            appendStatus("SEND open-dustbin");
            phase = Phase.DUSTBIN;
            phaseTick = 0;
            readyTick = 0;
        }
    }

    private void runDustbinTick(@Nonnull Minecraft client) {
        phaseTick++;
        if (client.screen instanceof ContainerScreen) {
            readyTick++;
            if (readyTick >= 20) {
                capture(client, "06-dustbin");
                appendStatus("PASS open-dustbin");
                finish(client);
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
        setProgressKey(false);
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
        appendStatus("FAILED " + step + ": " + error);
        LOGGER.error("Aotake UI smoke failed at {}", step, error);
        if (exitOnFinish) {
            client.stop();
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

    private enum Phase {
        WAITING,
        UI,
        WORLD_LOADING,
        HUD_NORMAL,
        HUD_HELD,
        DUSTBIN,
        FINISHED
    }
}
