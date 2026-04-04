package xin.vanilla.aotake.event;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.mixin.ContainerScreenAccessor;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.TransformArgs;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.LabelWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.InputStateManager;
import xin.vanilla.banira.client.util.TextureUtils;
import xin.vanilla.banira.common.data.Color;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.banira.common.util.*;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * 客户端 Game 逻辑；通过 {@link BaniraClientEventHub} 订阅，在 {@link xin.vanilla.aotake.AotakeSweep.ClientProxy} 中注册。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register() {
        BaniraClientEventHub.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        BaniraClientEventHub.Client.onClientTick(ClientGameEventHandler::onClientTick);
        BaniraClientEventHub.Client.onGuiScreen(ClientGameEventHandler::onRenderScreen);
        BaniraClientEventHub.Client.onRenderOverlayPre(ClientGameEventHandler::onRenderOverlayPre);
        BaniraClientEventHub.Client.onRenderOverlayPost(ClientGameEventHandler::onRenderOverlayPost);
    }

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

    public static void updateDustbinPage(int page, int totalPage) {
        dustbinPage = page;
        dustbinTotalPage = totalPage;
    }

    /**
     * 客户端 Tick（{@link BaniraClientEventHub} 仅在 Phase.END 分发）
     */
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (Minecraft.getInstance().screen == null) {
            if (ClientModEventHandler.DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                lastTime = System.currentTimeMillis();
                PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(0));
            }
            if (ClientConfig.get().progressBar().progressBarKeyApplyMode()) {
                if (ClientModEventHandler.PROGRESS_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                    lastTime = System.currentTimeMillis();
                    showProgress = !showProgress;
                }
            } else {
                showProgress = ClientModEventHandler.PROGRESS_KEY.isDown();
            }
        }
    }

    private static final Component TITLE = AotakeComponent.get().trans(EnumI18nType.WORD, "title");

    private static final InputStateManager mouseHelper = InputStateManager.instance();
    private static Button dustbinPrevButton;
    private static Button dustbinNextButton;

    private static void onRenderScreen(GuiScreenEvent event) {
        Screen screen = event.getGui();
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof ChestScreen
                && mc.player != null
                && screen.getTitle().getContents()
                .startsWith(TITLE.toVanilla(Translator.getClientLanguage()).getContents())
        ) {
            if (event instanceof GuiScreenEvent.InitGuiEvent.Post) {
                for (int i = 0; i < 20; i++) {
                    BaniraScheduler.schedule(i, () -> {
                        if (mousePos.key() > -1 && mousePos.val() > -1 && mouseRestoreTicks > 0) {
                            InputStateManager.setMouseRawPos(mousePos);
                            mouseRestoreTicks--;
                            if (mouseRestoreTicks <= 0) {
                                mousePos.key(-1D).val(-1D);
                            }
                        }
                    });
                }
                if (ClientConfig.get().dustbin().vanillaDustbin()) {
                    GuiScreenEvent.InitGuiEvent.Post eve = (GuiScreenEvent.InitGuiEvent.Post) event;
                    ClientPlayerEntity player = mc.player;
                    ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
                    int baseX = accessor.aotake$getLeftPos();
                    int baseY = accessor.aotake$getTopPos();
                    int yOffset = 0;
                    boolean canPrev = true;
                    boolean canNext = true;
                    if (dustbinPage > 0 && dustbinTotalPage > 0) {
                        canPrev = dustbinPage > 1;
                        canNext = dustbinPage < dustbinTotalPage;
                    }
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                        eve.addWidget(
                                newButton(baseX - 21
                                        , baseY + 21 * (yOffset++)
                                        , 20, 20
                                        , AotakeComponent.get().literal("✕").color(EnumMCColor.RED.getColor())
                                        , button -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new ClearDustbinToServer(true, true))
                                        , AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cache")
                                )
                        );
                    }
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                        eve.addWidget(
                                newButton(baseX - 21
                                        , baseY + 21 * (yOffset++)
                                        , 20, 20
                                        , AotakeComponent.get().literal("✕").color(EnumMCColor.RED.getColor())
                                        , button -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new ClearDustbinToServer(true, false))
                                        , AotakeComponent.get().trans(EnumI18nType.WORD, "clear_all_dustbin")
                                )
                        );
                        eve.addWidget(
                                newButton(baseX - 21
                                        , baseY + 21 * (yOffset++)
                                        , 20, 20
                                        , AotakeComponent.get().literal("✕").color(EnumMCColor.YELLOW.getColor())
                                        , button -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new ClearDustbinToServer(false, false))
                                        , AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cur_dustbin")
                                )
                        );
                    }
                    eve.addWidget(
                            newButton(baseX - 21
                                    , baseY + 21 * (yOffset++)
                                    , 20, 20
                                    , AotakeComponent.get().literal("↻")
                                    , button -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(0))
                                    , AotakeComponent.get().trans(EnumI18nType.WORD, "refresh_page")
                            )
                    );
                    Button prevButton = newButton(baseX - 21
                            , baseY + 21 * (yOffset++)
                            , 20, 20
                            , AotakeComponent.get().literal("▲")
                            , button -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(-1))
                            , AotakeComponent.get().trans(EnumI18nType.WORD, "previous_page")
                    );
                    prevButton.active = canPrev;
                    dustbinPrevButton = prevButton;
                    eve.addWidget(prevButton);
                    Button nextButton = newButton(baseX - 21
                            , baseY + 21 * (yOffset++)
                            , 20, 20
                            , AotakeComponent.get().literal("▼")
                            , button -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(1))
                            , AotakeComponent.get().trans(EnumI18nType.WORD, "next_page")
                    );
                    nextButton.active = canNext;
                    dustbinNextButton = nextButton;
                    eve.addWidget(nextButton);
                }
            } else if (event instanceof GuiScreenEvent.DrawScreenEvent.Post) {
                if (ClientConfig.get().dustbin().vanillaDustbin()) {
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
                if (!ClientConfig.get().dustbin().vanillaDustbin()) {
                    GuiScreenEvent.DrawScreenEvent.Post eve = (GuiScreenEvent.DrawScreenEvent.Post) event;
                    ClientPlayerEntity player = mc.player;
                    int mouseX = eve.getMouseX();
                    int mouseY = eve.getMouseY();

                    MatrixStack ms = eve.getMatrixStack();
                    int baseW = 16;
                    int baseH = 16;
                    ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
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

                        if (mouseHelper.isPressingLeftEx() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/clear_cache.png"), x, y, 0, 0, 0, w, h, w, h);

                        if (hover) {
                            TooltipWidget.drawPopupMessage(ms,
                                    FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cache")).stack(ms))
                                            .x(mouseX).y(mouseY).popupUseTexture(false),
                                    null, null);
                        }

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new ClearDustbinToServer(true, true));
                        }
                    }
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                        {
                            int w = baseW;
                            int h = baseH;
                            int x = baseX - w - 1;
                            int y = baseY + (h + 1) * (yOffset++);
                            boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                            if (mouseHelper.isPressingLeftEx() && hover) {
                                x--;
                                y--;
                                w += 2;
                                h += 2;
                            }

                            AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/clear_all.png"), x, y, 0, 0, 0, w, h, w, h);

                            if (hover) {
                                TooltipWidget.drawPopupMessage(ms,
                                        FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "clear_all_dustbin")).stack(ms))
                                                .x(mouseX).y(mouseY).popupUseTexture(false),
                                        null, null);
                            }

                            if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                                PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new ClearDustbinToServer(true, false));
                            }
                        }

                        {
                            int w = baseW;
                            int h = baseH;
                            int x = baseX - w - 1;
                            int y = baseY + (h + 1) * (yOffset++);
                            boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                            if (mouseHelper.isPressingLeftEx() && hover) {
                                x--;
                                y--;
                                w += 2;
                                h += 2;
                            }

                            AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/clear_page.png"), x, y, 0, 0, 0, w, h, w, h);

                            if (hover) {
                                TooltipWidget.drawPopupMessage(ms,
                                        FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cur_dustbin")).stack(ms))
                                                .x(mouseX).y(mouseY).popupUseTexture(false),
                                        null, null);
                            }

                            if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                                PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new ClearDustbinToServer(false, false));
                            }
                        }
                    }
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isPressingLeftEx() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/refresh.png"), x, y, 0, 0, 0, w, h, w, h);

                        if (hover) {
                            TooltipWidget.drawPopupMessage(ms,
                                    FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "refresh_page")).stack(ms))
                                            .x(mouseX).y(mouseY).popupUseTexture(false),
                                    null, null);
                        }

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = InputStateManager.getRawCursorPos();
                            mousePos.key(cursorPos.key()).value(cursorPos.val());
                            mouseRestoreTicks = 20;
                            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(0));
                        }
                    }
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = canPrev && mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isPressingLeftEx() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        RenderSystem.color4f(1.0F, 1.0F, 1.0F, canPrev ? 1.0F : 0.5F);
                        AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/up.png"), x, y, 0, 0, 0, w, h, w, h);
                        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

                        if (hover) {
                            TooltipWidget.drawPopupMessage(ms,
                                    FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "previous_page")).stack(ms))
                                            .x(mouseX).y(mouseY).popupUseTexture(false),
                                    null, null);
                        }

                        if (canPrev && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = InputStateManager.getRawCursorPos();
                            mousePos.key(cursorPos.key()).value(cursorPos.val());
                            mouseRestoreTicks = 20;
                            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(-1));
                        }
                    }
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = canNext && mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isPressingLeftEx() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        RenderSystem.color4f(1.0F, 1.0F, 1.0F, canNext ? 1.0F : 0.5F);
                        AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/down.png"), x, y, 0, 0, 0, w, h, w, h);
                        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

                        if (hover) {
                            TooltipWidget.drawPopupMessage(ms,
                                    FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "next_page")).stack(ms))
                                            .x(mouseX).y(mouseY).popupUseTexture(false),
                                    null, null);
                        }

                        if (canNext && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = InputStateManager.getRawCursorPos();
                            mousePos.key(cursorPos.key()).value(cursorPos.val());
                            mouseRestoreTicks = 20;
                            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(1));
                        }
                    }
                }
            } else if (event instanceof GuiScreenEvent.KeyboardKeyPressedEvent.Pre) {
                GuiScreenEvent.KeyboardKeyPressedEvent.Pre keyEvent = (GuiScreenEvent.KeyboardKeyPressedEvent.Pre) event;
                if (keyEvent.getModifiers() != 0) return;
                if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_KEY.getKey().getValue()) {
                    if (System.currentTimeMillis() - lastTime > 200) {
                        lastTime = System.currentTimeMillis();
                        mc.setScreen(null);
                    }
                } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_PRE_KEY.getKey().getValue()) {
                    if (System.currentTimeMillis() - lastTime > 200) {
                        lastTime = System.currentTimeMillis();
                        KeyValue<Double, Double> cursorPos = InputStateManager.getRawCursorPos();
                        mousePos.key(cursorPos.key()).value(cursorPos.val());
                        mouseRestoreTicks = 20;
                        PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(-1));
                    }
                } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.getKey().getValue()) {
                    if (System.currentTimeMillis() - lastTime > 200) {
                        lastTime = System.currentTimeMillis();
                        KeyValue<Double, Double> cursorPos = InputStateManager.getRawCursorPos();
                        mousePos.key(cursorPos.key()).value(cursorPos.val());
                        mouseRestoreTicks = 20;
                        PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(1));
                    }
                }
            }
        }
    }

    private static void onRenderOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.EXPERIENCE) {
            renderProgress(event);
        }
    }

    private static void onRenderOverlayPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.EXPERIENCE) {
            renderProgress(event);
        }
    }

    private static void renderProgress(RenderGameOverlayEvent event) {
        ClientConfig.ProgressBarView cp = ClientConfig.get().progressBar();
        ClientConfig.ProgressBarLeafView cpl = cp.leaf();
        ClientConfig.ProgressBarPoleView cpp = cp.pole();
        ClientConfig.ProgressBarTextView cpt = cp.text();
        // 避免重复渲染
        if (event instanceof RenderGameOverlayEvent.Post
                && ((cpp.hideExperienceBarPole() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.POLE.name()))
                || (cpt.hideExperienceBarText() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.TEXT.name()))
                || (cpl.hideExperienceBarLeaf() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.LEAF.name())))
        ) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;
        MatrixStack ms = event.getMatrixStack();
        boolean hold = showProgress && mc.screen == null;
        List<? extends String> displayList = hold ? cp.progressBarDisplayHold() : cp.progressBarDisplayNormal();

        double scale = cpt.progressBarTextSize() / 16.0;

        if (displayList.contains(EnumProgressBarType.POLE.name())) {
            if (cpp.hideExperienceBarPole()) {
                event.setCanceled(true);
            }
            int width = cpp.progressBarPoleWidth();
            int height = cpp.progressBarPoleHeight();
            int drawX = getPoleX();
            int drawY = getPoleY();

            TransformArgs transformArgs = new TransformArgs(ms);
            transformArgs.angle(cpp.progressBarPoleAngle())
                    .center(EnumPosition.CENTER)
                    .x(drawX)
                    .y(drawY)
                    .width(width)
                    .height(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) ->
                    AbstractGuiUtils.blitBlend(ms, TextureUtils.loadCustomTexture(Identifier.id(), "gui/pole.png"),
                            (int) arg.x(), (int) arg.y(), 0, 0, 0, (int) arg.width(), (int) arg.height(), (int) arg.width(), (int) arg.height()));
        }

        if (displayList.contains(EnumProgressBarType.TEXT.name())) {
            if (cpt.hideExperienceBarText()) {
                event.setCanceled(true);
            }
            TransformArgs textTransformArgs = new TransformArgs(ms);
            textTransformArgs.scale(scale)
                    .angle(cpt.progressBarTextAngle())
                    .center(EnumPosition.CENTER)
                    .x(getTextX())
                    .y(getTextY())
                    .width(getTextWidth())
                    .height(getTextHeight());
            AbstractGuiUtils.renderByTransform(textTransformArgs, (arg) -> {
                Text time = Text.literal(getText())
                        .stack(arg.stack())
                        .color(getTextColor())
                        .shadow(true)
                        .font(Minecraft.getInstance().font);
                LabelWidget.drawLimitedText(FontDrawArgs.of(time).x(arg.x()).y(arg.y()).inScreen(false));
            });
        }

        if (displayList.contains(EnumProgressBarType.LEAF.name())) {
            if (cpl.hideExperienceBarLeaf()) {
                event.setCanceled(true);
            }
            int poleW = cpp.progressBarPoleWidth();

            int width = cpl.progressBarLeafWidth();
            int height = cpl.progressBarLeafHeight();
            int rangeWidth = poleW - width;
            int startX = getLeafX();

            int drawX = (int) (startX + rangeWidth * getProgress());
            int drawY = getLeafY();

            TransformArgs transformArgs = new TransformArgs(ms);
            transformArgs.angle(cpl.progressBarLeafAngle())
                    .center(EnumPosition.CENTER)
                    .x(drawX)
                    .y(drawY)
                    .width(width)
                    .height(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                ResourceLocation texture = TextureUtils.loadCustomTexture(Identifier.id(), "gui/leaf.png");
                AbstractGuiUtils.blitBlend(ms, texture, (int) arg.x(), (int) arg.y(), 0, 0, 0, (int) arg.width(), (int) arg.height(), (int) arg.width(), (int) arg.height());
            });
        }
    }

    private static int getLeafX() {
        ClientConfig.ProgressBarLeafView cpl = ClientConfig.get().progressBar().leaf();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int baseX = getPoleX();
        int width = cpp.progressBarPoleWidth();
        double x;
        String xString = cpl.progressBarLeafPosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = NumberUtils.toInt(xString);
        }
        int quadrant = cpl.progressBarLeafScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumPosition.valueOf(cpl.progressBarLeafBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= cpl.progressBarLeafWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= cpl.progressBarLeafWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getLeafY() {
        ClientConfig.ProgressBarLeafView cpl = ClientConfig.get().progressBar().leaf();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int baseY = getPoleY();
        int height = cpp.progressBarPoleHeight();
        double y;
        String yString = cpl.progressBarLeafPosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = NumberUtils.toInt(yString);
        }
        int quadrant = cpl.progressBarLeafScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumPosition.valueOf(cpl.progressBarLeafBase())) {
            case CENTER: {
                y -= cpl.progressBarLeafHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= cpl.progressBarLeafHeight();
            }
            break;
        }
        return (int) y;
    }

    private static int getPoleX() {
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        double x;
        String xString = cpp.progressBarPolePosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = NumberUtils.toInt(xString);
        }
        int quadrant = cpp.progressBarPoleScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = width - x;
        }
        switch (EnumPosition.valueOf(cpp.progressBarPoleBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= cpp.progressBarPoleWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= cpp.progressBarPoleWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getPoleY() {
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double y;
        String yString = cpp.progressBarPolePosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = NumberUtils.toInt(yString);
        }
        int quadrant = cpp.progressBarPoleScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = height - y;
        }
        switch (EnumPosition.valueOf(cpp.progressBarPoleBase())) {
            case CENTER: {
                y -= cpp.progressBarPoleHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= cpp.progressBarPoleHeight();
            }
            break;
        }
        return (int) y;
    }

    private static int getTextX() {
        ClientConfig.ProgressBarTextView cpt = ClientConfig.get().progressBar().text();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int baseX = getPoleX();
        int width = cpp.progressBarPoleWidth();
        double x;
        String xString = cpt.progressBarTextPosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = NumberUtils.toInt(xString);
        }
        int quadrant = cpt.progressBarTextScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumPosition.valueOf(cpt.progressBarTextBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= cpt.progressBarTextSize() / 16.0 * getTextWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= cpt.progressBarTextSize() / 16.0 * getTextWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getTextY() {
        ClientConfig.ProgressBarTextView cpt = ClientConfig.get().progressBar().text();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int baseY = getPoleY();
        int height = cpp.progressBarPoleHeight();
        double y;
        String yString = cpt.progressBarTextPosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = NumberUtils.toInt(yString);
        }
        int quadrant = cpt.progressBarTextScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumPosition.valueOf(cpt.progressBarTextBase())) {
            case CENTER: {
                y -= cpt.progressBarTextSize() / 16.0 * getTextHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= cpt.progressBarTextSize() / 16.0 * getTextHeight();
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
            Date lastSweepTime = DateUtils.addMilliSecond(nextSweepTime, -AotakeSweep.getSweepTime().key().intValue());
            return (double) (now.getTime() - lastSweepTime.getTime()) / (double) (nextSweepTime.getTime() - lastSweepTime.getTime());
        } else {
            return 0;
        }
    }

    private static Date getNextSweepTime(Date now) {
        int difference = (int) ((AotakeSweep.getClientServerTime().key() - AotakeSweep.getClientServerTime().val()) / 1000L);
        Date nextSweepTime = DateUtils.addSecond(new Date(AotakeSweep.getSweepTime().val()), difference);
        if (nextSweepTime.before(now)) {
            for (int i = 0; i < 5; i++) {
                if (nextSweepTime.before(now)) {
                    nextSweepTime = DateUtils.addMilliSecond(nextSweepTime, AotakeSweep.getSweepTime().key().intValue());
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
        return Color.parse(ClientConfig.get().progressBar().text().progressBarTextColor(), Color.white());
    }

    private static Button newButton(int x, int y, int width, int height,
                                    Component label,
                                    Consumer<Button> onPress,
                                    Component tooltip) {
        return new Button(x, y, width, height, label.toVanilla(), onPress::accept);
    }

}
