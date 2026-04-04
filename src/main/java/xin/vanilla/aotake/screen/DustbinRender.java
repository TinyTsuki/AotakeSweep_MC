package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraftforge.client.event.GuiScreenEvent;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.mixin.ContainerScreenAccessor;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.ClientThemeManager;
import xin.vanilla.banira.client.util.InputStateManager;
import xin.vanilla.banira.client.util.TextureUtils;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.StringUtils;

import java.util.function.Consumer;

/**
 * 垃圾箱 {@link ChestScreen} 的控件与自定义纹理按钮绘制、快捷键处理
 */
public final class DustbinRender {

    private static final InputStateManager mouseHelper = InputStateManager.instance();

    /**
     * 翻页/刷新前记录的光标
     */
    private static final KeyValue<Double, Double> pendingMouseRaw = new KeyValue<>(-1D, -1D);

    private static int dustbinPage = -1;
    private static int dustbinTotalPage = -1;
    private static Button dustbinPrevButton;
    private static Button dustbinNextButton;
    private static long lastDustbinScreenKeyTime = 0L;

    private DustbinRender() {
    }

    public static boolean isDustbinTitle(String title) {
        return StringUtils.isNotNullOrEmpty(title) &&
                AotakeLang.get().getI18nFiles().stream().anyMatch(lang ->
                        title.startsWith(
                                AotakeComponent.get().transAuto("title").getString(lang)
                        )
                );
    }

    public static void updateDustbinPage(int page, int totalPage) {
        dustbinPage = page;
        dustbinTotalPage = totalPage;
    }

    /**
     * 在发送会触发垃圾箱界面重建的 {@link OpenDustbinToServer} 之前调用，记录当前光标
     */
    private static void queueCursorRestoreBeforeContainerRefresh() {
        KeyValue<Double, Double> cur = InputStateManager.getRawCursorPos();
        pendingMouseRaw.key(cur.key()).value(cur.val());
    }

    private static boolean isOurDustbinChestScreen(Screen screen, Minecraft mc) {
        return screen instanceof ChestScreen
                && mc.player != null
                && isDustbinTitle(screen.getTitle().getContents());
    }

    /**
     * 打开非垃圾箱界面时丢弃待恢复坐标
     */
    public static void abandonPendingCursorRestore() {
        if (pendingMouseRaw.key() >= 0) {
            pendingMouseRaw.key(-1D).val(-1D);
        }
    }

    /**
     * 若存在待恢复坐标则消费并写入
     */
    public static void tryConsumePendingCursorRaw() {
        if (pendingMouseRaw.key() < 0) {
            return;
        }
        double rx = pendingMouseRaw.key();
        double ry = pendingMouseRaw.val();
        pendingMouseRaw.key(-1D).val(-1D);
        InputStateManager.setMouseRawPos(rx, ry);
    }

    public static void handleGuiScreen(GuiScreenEvent event) {
        Screen screen = event.getGui();
        Minecraft mc = Minecraft.getInstance();
        if (!isOurDustbinChestScreen(screen, mc)) {
            abandonPendingCursorRestore();
            return;
        }

        if (event instanceof GuiScreenEvent.InitGuiEvent.Post) {
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
                                , button -> {
                                    queueCursorRestoreBeforeContainerRefresh();
                                    PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(0));
                                }
                                , AotakeComponent.get().trans(EnumI18nType.WORD, "refresh_page")
                        )
                );
                Button prevButton = newButton(baseX - 21
                        , baseY + 21 * (yOffset++)
                        , 20, 20
                        , AotakeComponent.get().literal("▲")
                        , button -> {
                            queueCursorRestoreBeforeContainerRefresh();
                            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(-1));
                        }
                        , AotakeComponent.get().trans(EnumI18nType.WORD, "previous_page")
                );
                prevButton.active = canPrev;
                dustbinPrevButton = prevButton;
                eve.addWidget(prevButton);
                Button nextButton = newButton(baseX - 21
                        , baseY + 21 * (yOffset++)
                        , 20, 20
                        , AotakeComponent.get().literal("▼")
                        , button -> {
                            queueCursorRestoreBeforeContainerRefresh();
                            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(1));
                        }
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
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
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
                                            .x(mouseX).y(mouseY),
                                    ClientThemeManager.getEffectiveTheme(), null);
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
                                            .x(mouseX).y(mouseY),
                                    ClientThemeManager.getEffectiveTheme(), null);
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
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        queueCursorRestoreBeforeContainerRefresh();
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
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (canPrev && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        queueCursorRestoreBeforeContainerRefresh();
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
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (canNext && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        queueCursorRestoreBeforeContainerRefresh();
                        PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(1));
                    }
                }
            }
        } else if (event instanceof GuiScreenEvent.KeyboardKeyPressedEvent.Pre) {
            GuiScreenEvent.KeyboardKeyPressedEvent.Pre keyEvent = (GuiScreenEvent.KeyboardKeyPressedEvent.Pre) event;
            if (keyEvent.getModifiers() != 0) return;
            if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_KEY.getKey().getValue()) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    mc.setScreen(null);
                }
            } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_PRE_KEY.getKey().getValue()) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    queueCursorRestoreBeforeContainerRefresh();
                    PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(-1));
                }
            } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.getKey().getValue()) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    queueCursorRestoreBeforeContainerRefresh();
                    PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(1));
                }
            }
        }
    }

    private static Button newButton(int x, int y, int width, int height,
                                    Component label,
                                    Consumer<Button> onPress,
                                    Component tooltip) {
        return new Button(x, y, width, height, label.toVanilla(), onPress::accept);
    }
}
