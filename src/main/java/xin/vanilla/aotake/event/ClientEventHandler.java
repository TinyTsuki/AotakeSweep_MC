package xin.vanilla.aotake.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.data.Color;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.mixin.AbstractContainerScreenAccessor;
import xin.vanilla.aotake.mixin.ScreenAccessor;
import xin.vanilla.aotake.network.ModNetworkHandler;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.ModLoadedToBoth;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.component.Text;
import xin.vanilla.aotake.util.*;

import java.util.*;


/**
 * 客户端事件处理器
 */
@Accessors(fluent = true)
@Environment(EnvType.CLIENT)
public class ClientEventHandler implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();


    private static final String CATEGORIES = "key.aotake_sweep.categories";

    /**
     * 垃圾箱快捷键
     */
    public static KeyMapping DUSTBIN_KEY;
    /**
     * 垃圾箱上页快捷键
     */
    public static KeyMapping DUSTBIN_PRE_KEY;
    /**
     * 垃圾箱下页快捷键
     */
    public static KeyMapping DUSTBIN_NEXT_KEY;

    /**
     * 切换进度条显示按键
     */
    public static KeyMapping PROGRESS_KEY;


    private static long lastTime = 0;

    /**
     * 重新打开垃圾箱页面前鼠标位置
     */
    private static final KeyValue<Double, Double> mousePos = new KeyValue<>(-1D, -1D);
    private static int mouseRestoreTicks = 0;
    private static int dustbinPage = -1;
    private static int dustbinTotalPage = -1;
    /**
     * 是否显示进度条
     */
    private static boolean showProgress = false;
    /**
     * 隐藏经验条
     */
    @Getter
    private static boolean hideExpBar = false;

    public static void updateDustbinPage(int page, int totalPage) {
        dustbinPage = page;
        dustbinTotalPage = totalPage;
    }

    private static final Component TITLE = Component.translatable(EnumI18nType.WORD, "title");

    private static final MouseHelper mouseHelper = new MouseHelper();
    private static final Set<Screen> REGISTERED_SCREEN = Collections.newSetFromMap(new WeakHashMap<>());
    private static Button dustbinPrevButton;
    private static Button dustbinNextButton;


    @Override
    public void onInitializeClient() {
        // 注册调度器
        ClientTickEvents.END_CLIENT_TICK.register(client -> AotakeScheduler.onClientTick());
        // 注册配置
        ClientConfig.register();
        CustomConfig.loadCustomConfig(false);
        // 注册网络包
        ModNetworkHandler.registerClientPackets();
        // 注册玩家登录事件
        onPlayerLoggedIn();
        // 注册玩家登出事件
        onPlayerLoggedOut();
        // 注册键绑定
        onKeyBindings();
        // 注册客户端Tick事件
        onClientTickEvent();
        // 注册屏幕事件
        onScreenEvent();
        // 注册HUD渲染事件
        onHudRender();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new TextureUtils());
    }

    private static void onPlayerLoggedIn() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.debug("Client: Player logged in.");
            if (ModNetworkHandler.hasAotakeServer()) {
                // 通知服务器客户端已加载mod
                AotakeUtils.sendPacketToServer(new ModLoadedToBoth());
            }
        });
    }

    private static void onPlayerLoggedOut() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AotakeSweep.clientServerTime().setKey(0L).setValue(0L);
            AotakeSweep.sweepTime().setKey(0L).setValue(0L);
        });
    }

    /**
     * 注册键绑定
     */
    private static void onKeyBindings() {
        // 注册键绑定
        LOGGER.debug("Registering key bindings");
        DUSTBIN_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.aotake_sweep.open_dustbin", GLFW.GLFW_KEY_UNKNOWN, CATEGORIES)
        );
        DUSTBIN_PRE_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.aotake_sweep.open_dustbin_pre",
                        GLFW.GLFW_KEY_LEFT, CATEGORIES)
        );
        DUSTBIN_NEXT_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.aotake_sweep.open_dustbin_next", GLFW.GLFW_KEY_RIGHT, CATEGORIES)
        );
        PROGRESS_KEY = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.aotake_sweep.progress", GLFW.GLFW_KEY_TAB, CATEGORIES)
        );
    }

    private static void onClientTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Minecraft.getInstance().screen == null) {
                if (DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                    lastTime = System.currentTimeMillis();
                    AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                }
                if (ClientConfig.get().progressBarConfig().progressBarKeyApplyMode()) {
                    if (PROGRESS_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                        lastTime = System.currentTimeMillis();
                        showProgress = !showProgress;
                    }
                } else {
                    showProgress = PROGRESS_KEY.isDown();
                }
            }
            updateHideExpBar();
        });
    }

    private static void onScreenEvent() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (isAotakeContainerScreen(client, screen)) {
                REGISTERED_SCREEN.add(screen);
                screenAfterInit(client, screen);

                ScreenKeyboardEvents.allowKeyPress(screen).register((screen_, key, scancode, modifiers) ->
                        !handleDustbinKeyPress(client, key, scancode)
                );

                ScreenEvents.afterRender(screen).register((screenRendered, stack, mouseX, mouseY, tickDelta) ->
                        screenAfterRender(client, screenRendered, stack, mouseX, mouseY)
                );

                ScreenEvents.afterTick(screen).register(screenTicked ->
                        screenAfterTick(client)
                );

                ScreenEvents.remove(screen).register(REGISTERED_SCREEN::remove);
            }
        });
    }

    private static boolean isAotakeContainerScreen(Minecraft client, Screen screen) {
        return screen instanceof ContainerScreen
                && client.player != null
                && screen.getTitle().getString()
                .startsWith(TITLE.toTextComponent(AotakeUtils.getPlayerLanguage(client.player)).getString()
                );
    }

    private static void screenAfterInit(Minecraft client, Screen screen) {
        for (int i = 0; i < 20; i++) {
            AotakeScheduler.schedule(i, () -> {
                if (mousePos.key() > -1 && mousePos.val() > -1 && mouseRestoreTicks > 0) {
                    MouseHelper.setMouseRawPos(mousePos);
                    mouseRestoreTicks--;
                    if (mouseRestoreTicks <= 0) {
                        mousePos.setKey(-1D).setValue(-1D);
                    }
                }
            });
        }
        if (ClientConfig.get().vanillaDustbin()) {
            LocalPlayer player = client.player;
            if (player == null) return;
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int baseX = accessor.aotake$getLeftPos();
            int baseY = accessor.aotake$getTopPos();
            int yOffset = 0;
            boolean canPrev = true;
            boolean canNext = true;
            if (dustbinPage > 0 && dustbinTotalPage > 0) {
                canPrev = dustbinPage > 1;
                canNext = dustbinPage < dustbinTotalPage;
            }
            ScreenAccessor screen_ = (ScreenAccessor) screen;
            if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                screen_.aotake$addRenderableWidget(
                        AbstractGuiUtils.newButton(baseX - 21
                                , baseY + 21 * (yOffset++)
                                , 20, 20
                                , Component.literal("✕").setColor(EnumMCColor.RED.getColor())
                                , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, true))
                                , Component.translatable(EnumI18nType.MESSAGE, "clear_cache")
                        )
                );
            }
            if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                screen_.aotake$addRenderableWidget(
                        AbstractGuiUtils.newButton(baseX - 21
                                , baseY + 21 * (yOffset++)
                                , 20, 20
                                , Component.literal("✕").setColor(EnumMCColor.RED.getColor())
                                , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, false))
                                , Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin")
                        )
                );
                screen_.aotake$addRenderableWidget(
                        AbstractGuiUtils.newButton(baseX - 21
                                , baseY + 21 * (yOffset++)
                                , 20, 20
                                , Component.literal("✕").setColor(EnumMCColor.YELLOW.getColor())
                                , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(false, false))
                                , Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin")
                        )
                );
            }
            screen_.aotake$addRenderableWidget(
                    AbstractGuiUtils.newButton(baseX - 21
                            , baseY + 21 * (yOffset++)
                            , 20, 20
                            , Component.literal("↻")
                            , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0))
                            , Component.translatable(EnumI18nType.MESSAGE, "refresh_page")
                    )
            );
            Button prevButton = AbstractGuiUtils.newButton(baseX - 21
                    , baseY + 21 * (yOffset++)
                    , 20, 20
                    , Component.literal("▲")
                    , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1))
                    , Component.translatable(EnumI18nType.MESSAGE, "previous_page")
            );
            prevButton.active = canPrev;
            dustbinPrevButton = prevButton;
            screen_.aotake$addRenderableWidget(prevButton);
            Button nextButton = AbstractGuiUtils.newButton(baseX - 21
                    , baseY + 21 * (yOffset++)
                    , 20, 20
                    , Component.literal("▼")
                    , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1))
                    , Component.translatable(EnumI18nType.MESSAGE, "next_page")
            );
            nextButton.active = canNext;
            dustbinNextButton = nextButton;
            screen_.aotake$addRenderableWidget(nextButton);
        }
    }

    private static void screenAfterRender(Minecraft client, Screen screen, PoseStack stack, int mouseX, int mouseY) {
        if (ClientConfig.get().vanillaDustbin()) {
            boolean canPrev = true;
            boolean canNext = true;
            if (dustbinPage > 0 && dustbinTotalPage > 0) {
                canPrev = dustbinPage > 1;
                canNext = dustbinPage < dustbinTotalPage;
            }
            if (dustbinPrevButton != null) {
                dustbinPrevButton.active = canPrev;
            }
            if (dustbinNextButton != null) {
                dustbinNextButton.active = canNext;
            }
        }
        if (!ClientConfig.get().vanillaDustbin()) {
            LocalPlayer player = client.player;
            if (player == null) return;
            mouseHelper.tick(mouseX, mouseY);

            int baseW = 16;
            int baseH = 16;
            AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
            int baseX = DustbinGuiLayoutCache.valid
                    ? DustbinGuiLayoutCache.leftPos + DustbinGuiConfig.getButtonXOffset()
                    : accessor.aotake$getLeftPos();
            int baseY = DustbinGuiLayoutCache.valid
                    ? DustbinGuiLayoutCache.topPos + DustbinGuiConfig.getButtonYOffset()
                    : accessor.aotake$getTopPos();
            boolean canPrev = true;
            boolean canNext = true;
            if (dustbinPage > 0 && dustbinTotalPage > 0) {
                canPrev = dustbinPage > 1;
                canNext = dustbinPage < dustbinTotalPage;
            }

            int yOffset = 0;
            if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                int w = baseW;
                int h = baseH;
                int x = baseX - w - 1;
                int y = baseY + (h + 1) * (yOffset++);
                boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                if (mouseHelper.isLeftPressing() && hover) {
                    x--;
                    y--;
                    w += 2;
                    h += 2;
                }

                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_cache.png");
                AbstractGuiUtils.bindTexture(texture);
                AbstractGuiUtils.blitBlend(stack, x, y, 0, 0, w, h, w, h);

                if (hover) {
                    AbstractGuiUtils.drawPopupMessage(Text.empty()
                                    .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_cache"))
                                    .setStack(stack)
                            , mouseX
                            , mouseY
                            , screen.width
                            , screen.height
                    );
                }

                if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                    AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, true));
                }
            }
            if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++);
                    boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                    if (mouseHelper.isLeftPressing() && hover) {
                        x--;
                        y--;
                        w += 2;
                        h += 2;
                    }

                    ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_all.png");
                    AbstractGuiUtils.bindTexture(texture);
                    AbstractGuiUtils.blitBlend(stack, x, y, 0, 0, w, h, w, h);

                    if (hover) {
                        AbstractGuiUtils.drawPopupMessage(Text.empty()
                                        .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin"))
                                        .setStack(stack)
                                , mouseX
                                , mouseY
                                , screen.width
                                , screen.height
                        );
                    }

                    if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, false));
                    }
                }

                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++);
                    boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                    if (mouseHelper.isLeftPressing() && hover) {
                        x--;
                        y--;
                        w += 2;
                        h += 2;
                    }

                    ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_page.png");
                    AbstractGuiUtils.bindTexture(texture);
                    AbstractGuiUtils.blitBlend(stack, x, y, 0, 0, w, h, w, h);

                    if (hover) {
                        AbstractGuiUtils.drawPopupMessage(Text.empty()
                                        .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin"))
                                        .setStack(stack)
                                , mouseX
                                , mouseY
                                , screen.width
                                , screen.height
                        );
                    }

                    if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        AotakeUtils.sendPacketToServer(new ClearDustbinToServer(false, false));
                    }
                }
            }
            {
                int w = baseW;
                int h = baseH;
                int x = baseX - w - 1;
                int y = baseY + (h + 1) * (yOffset++);
                boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                if (mouseHelper.isLeftPressing() && hover) {
                    x--;
                    y--;
                    w += 2;
                    h += 2;
                }

                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "refresh.png");
                AbstractGuiUtils.bindTexture(texture);
                AbstractGuiUtils.blitBlend(stack, x, y, 0, 0, w, h, w, h);

                if (hover) {
                    AbstractGuiUtils.drawPopupMessage(Text.empty()
                                    .setText(Component.translatable(EnumI18nType.MESSAGE, "refresh_page"))
                                    .setStack(stack)
                            , mouseX
                            , mouseY
                            , screen.width
                            , screen.height
                    );
                }

                if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                    KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                    mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                    mouseRestoreTicks = 20;
                    AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                }
            }
            {
                int w = baseW;
                int h = baseH;
                int x = baseX - w - 1;
                int y = baseY + (h + 1) * (yOffset++);
                boolean hover = canPrev && mouseHelper.isHoverInRect(x, y, w, h);

                if (mouseHelper.isLeftPressing() && hover) {
                    x--;
                    y--;
                    w += 2;
                    h += 2;
                }

                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "up.png");
                AbstractGuiUtils.bindTexture(texture);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, canPrev ? 1.0F : 0.5F);
                AbstractGuiUtils.blitBlend(stack, x, y, 0, 0, w, h, w, h);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                if (hover) {
                    AbstractGuiUtils.drawPopupMessage(Text.empty()
                                    .setText(Component.translatable(EnumI18nType.MESSAGE, "previous_page"))
                                    .setStack(stack)
                            , mouseX
                            , mouseY
                            , screen.width
                            , screen.height
                    );
                }

                if (canPrev && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                    KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                    mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                    mouseRestoreTicks = 20;
                    AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                }
            }
            {
                int w = baseW;
                int h = baseH;
                int x = baseX - w - 1;
                int y = baseY + (h + 1) * (yOffset++);
                boolean hover = canNext && mouseHelper.isHoverInRect(x, y, w, h);

                if (mouseHelper.isLeftPressing() && hover) {
                    x--;
                    y--;
                    w += 2;
                    h += 2;
                }

                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "down.png");
                AbstractGuiUtils.bindTexture(texture);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, canNext ? 1.0F : 0.5F);
                AbstractGuiUtils.blitBlend(stack, x, y, 0, 0, w, h, w, h);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                if (hover) {
                    AbstractGuiUtils.drawPopupMessage(Text.empty()
                                    .setText(Component.translatable(EnumI18nType.MESSAGE, "next_page"))
                                    .setStack(stack)
                            , mouseX
                            , mouseY
                            , screen.width
                            , screen.height
                    );
                }

                if (canNext && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                    KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                    mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                    mouseRestoreTicks = 20;
                    AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
                }
            }
        }
    }

    private static boolean handleDustbinKeyPress(Minecraft client, int key, int scancode) {
        boolean isOurKey = DUSTBIN_KEY.matches(key, scancode)
                || DUSTBIN_PRE_KEY.matches(key, scancode)
                || DUSTBIN_NEXT_KEY.matches(key, scancode);
        if (!isOurKey) {
            return false;
        }
        if (System.currentTimeMillis() - lastTime <= 200) {
            return true;
        }
        if (DUSTBIN_KEY.matches(key, scancode)) {
            lastTime = System.currentTimeMillis();
            client.setScreen(null);
            return true;
        }
        if (DUSTBIN_PRE_KEY.matches(key, scancode)) {
            lastTime = System.currentTimeMillis();
            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
            mouseRestoreTicks = 20;
            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
            return true;
        }
        if (DUSTBIN_NEXT_KEY.matches(key, scancode)) {
            lastTime = System.currentTimeMillis();
            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
            mouseRestoreTicks = 20;
            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
            return true;
        }
        return false;
    }

    private static void screenAfterTick(Minecraft client) {
        if (DUSTBIN_KEY.isDown()) {
            if (System.currentTimeMillis() - lastTime > 200) {
                lastTime = System.currentTimeMillis();
                client.setScreen(null);
            }
        } else if (DUSTBIN_PRE_KEY.isDown()) {
            if (System.currentTimeMillis() - lastTime > 200) {
                lastTime = System.currentTimeMillis();
                KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                mouseRestoreTicks = 20;
                AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
            }
        } else if (DUSTBIN_NEXT_KEY.isDown()) {
            if (System.currentTimeMillis() - lastTime > 200) {
                lastTime = System.currentTimeMillis();
                KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                mouseRestoreTicks = 20;
                AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
            }
        }
    }

    private static void updateHideExpBar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            hideExpBar = false;
            return;
        }
        List<String> displayList = getProgressDisplayList(mc);
        boolean hide = displayList.contains(EnumProgressBarType.POLE.name()) && ClientConfig.get().progressBarConfig().poleConfig().hideExperienceBarPole();
        if (displayList.contains(EnumProgressBarType.TEXT.name()) && ClientConfig.get().progressBarConfig().textConfig().hideExperienceBarText()) {
            hide = true;
        }
        if (displayList.contains(EnumProgressBarType.LEAF.name()) && ClientConfig.get().progressBarConfig().leafConfig().hideExperienceBarLeaf()) {
            hide = true;
        }
        hideExpBar = hide;
    }

    private static List<String> getProgressDisplayList(Minecraft mc) {
        boolean hold = showProgress && mc.screen == null;
        return hold ? ClientConfig.get().progressBarConfig().progressBarDisplayHold() : ClientConfig.get().progressBarConfig().progressBarDisplayNormal();
    }

    private static void onHudRender() {
        HudRenderCallback.EVENT.register(ClientEventHandler::renderProgress);
    }

    private static void renderProgress(PoseStack stack, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;

        List<String> displayList = getProgressDisplayList(mc);

        double scale = ClientConfig.get().progressBarConfig().textConfig().progressBarTextSize() / 16.0;

        if (displayList.contains(EnumProgressBarType.POLE.name())) {
            int width = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleWidth();
            int height = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleHeight();
            int drawX = getPoleX();
            int drawY = getPoleY();

            AbstractGuiUtils.TransformArgs transformArgs = new AbstractGuiUtils.TransformArgs(stack);
            transformArgs.setAngle(ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleAngle())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(drawX)
                    .setY(drawY)
                    .setWidth(width)
                    .setHeight(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "pole.png");
                AbstractGuiUtils.bindTexture(texture);
                AbstractGuiUtils.blitBlend(stack, (int) arg.getX(), (int) arg.getY(), 0, 0, (int) arg.getWidth(), (int) arg.getHeight(), (int) arg.getWidth(), (int) arg.getHeight());
            });
        }

        if (displayList.contains(EnumProgressBarType.TEXT.name())) {
            Text time = Text.literal(getText())
                    .setStack(stack)
                    .setColor(getTextColor())
                    .setShadow(true)
                    .setFont(Minecraft.getInstance().font);
            AbstractGuiUtils.TransformArgs textTransformArgs = new AbstractGuiUtils.TransformArgs(stack);
            textTransformArgs.setScale(scale)
                    .setAngle(ClientConfig.get().progressBarConfig().textConfig().progressBarTextAngle())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(getTextX())
                    .setY(getTextY())
                    .setWidth(getTextWidth())
                    .setHeight(getTextHeight());
            AbstractGuiUtils.renderByTransform(textTransformArgs, (arg) -> AbstractGuiUtils.drawString(time
                    , arg.getX()
                    , arg.getY()
            ));
        }

        if (displayList.contains(EnumProgressBarType.LEAF.name())) {
            int poleW = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleWidth();

            int width = ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafWidth();
            int height = ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafHeight();
            int rangeWidth = poleW - width;
            int startX = getLeafX();

            int drawX = (int) (startX + rangeWidth * getProgress());
            int drawY = getLeafY();

            AbstractGuiUtils.TransformArgs transformArgs = new AbstractGuiUtils.TransformArgs(stack);
            transformArgs.setAngle(ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafAngle())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(drawX)
                    .setY(drawY)
                    .setWidth(width)
                    .setHeight(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "leaf.png");
                AbstractGuiUtils.bindTexture(texture);
                AbstractGuiUtils.blitBlend(stack, (int) arg.getX(), (int) arg.getY(), 0, 0, (int) arg.getWidth(), (int) arg.getHeight(), (int) arg.getWidth(), (int) arg.getHeight());
            });
        }
    }

    private static int getLeafX() {
        int baseX = getPoleX();
        int width = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleWidth();
        double x;
        String xString = ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafPosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getLeafY() {
        int baseY = getPoleY();
        int height = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleHeight();
        double y;
        String yString = ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafPosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafBase())) {
            case CENTER: {
                y -= ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.get().progressBarConfig().leafConfig().progressBarLeafHeight();
            }
            break;
        }
        return (int) y;
    }

    private static int getPoleX() {
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        double x;
        String xString = ClientConfig.get().progressBarConfig().poleConfig().progressBarPolePosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = width - x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getPoleY() {
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double y;
        String yString = ClientConfig.get().progressBarConfig().poleConfig().progressBarPolePosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = height - y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleBase())) {
            case CENTER: {
                y -= ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleHeight();
            }
            break;
        }
        return (int) y;
    }

    private static int getTextX() {
        int baseX = getPoleX();
        int width = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleWidth();
        double x;
        String xString = ClientConfig.get().progressBarConfig().textConfig().progressBarTextPosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.get().progressBarConfig().textConfig().progressBarTextScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.get().progressBarConfig().textConfig().progressBarTextBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.get().progressBarConfig().textConfig().progressBarTextSize() / 16.0 * getTextWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.get().progressBarConfig().textConfig().progressBarTextSize() / 16.0 * getTextWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getTextY() {
        int baseY = getPoleY();
        int height = ClientConfig.get().progressBarConfig().poleConfig().progressBarPoleHeight();
        double y;
        String yString = ClientConfig.get().progressBarConfig().textConfig().progressBarTextPosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.get().progressBarConfig().textConfig().progressBarTextScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.get().progressBarConfig().textConfig().progressBarTextBase())) {
            case CENTER: {
                y -= ClientConfig.get().progressBarConfig().textConfig().progressBarTextSize() / 16.0 * getTextHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.get().progressBarConfig().textConfig().progressBarTextSize() / 16.0 * getTextHeight();
            }
            break;
        }
        return (int) y;
    }

    private static String getText() {
        Date now = new Date();
        Date nextSweepTime = getNextSweepTime(now);
        if (nextSweepTime.after(now)) {
            long seconds = DateUtils.secondsOfTwo(now, nextSweepTime);
            if (seconds / 60 >= 100) {
                long hours = seconds / 3600;
                long minutes = (seconds % 3600) / 60;
                long secs = seconds % 60;
                return String.format("%02d:%02d:%02d", hours, minutes, secs);
            } else {
                long minutes = seconds / 60;
                long secs = seconds % 60;
                return String.format("%02d:%02d", minutes, secs);
            }
        } else {
            return "∞";
        }
    }

    private static double getProgress() {
        Date now = new Date();
        Date nextSweepTime = getNextSweepTime(now);
        if (nextSweepTime.after(now)) {
            Date lastSweepTime = DateUtils.addMilliSecond(nextSweepTime, -AotakeSweep.sweepTime().key().intValue());
            return (double) (now.getTime() - lastSweepTime.getTime()) / (double) (nextSweepTime.getTime() - lastSweepTime.getTime());
        } else {
            return 0;
        }
    }

    private static Date getNextSweepTime(Date now) {
        int difference = (int) ((AotakeSweep.clientServerTime().key() - AotakeSweep.clientServerTime().val()) / 1000L);
        Date nextSweepTime = DateUtils.addSecond(new Date(AotakeSweep.sweepTime().val()), difference);
        if (nextSweepTime.before(now)) {
            for (int i = 0; i < 5; i++) {
                if (nextSweepTime.before(now)) {
                    nextSweepTime = DateUtils.addMilliSecond(nextSweepTime, AotakeSweep.sweepTime().key().intValue());
                } else {
                    break;
                }
            }
        }
        return nextSweepTime;
    }

    private static int getTextWidth() {
        return AbstractGuiUtils.getStringWidth(Minecraft.getInstance().font, getText());
    }

    private static int getTextHeight() {
        return AbstractGuiUtils.getStringHeight(Minecraft.getInstance().font, getText());
    }

    private static Color getTextColor() {
        return Color.parse(ClientConfig.get().progressBarConfig().textConfig().progressBarTextColor(), Color.white());
    }

}
