package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.internal.client.DustbinMouseInput;
import xin.vanilla.aotake.mixin.ContainerScreenAccessor;
import xin.vanilla.aotake.network.packet.ChunkVaultNavigateToServer;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.DustbinPageNavigation;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.ClientThemeManager;
import xin.vanilla.banira.client.util.TextureUtils;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.enums.EnumMCColor;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 垃圾箱 {@link ContainerScreen} 的侧栏控件绘制与快捷键处理
 */
public final class DustbinRender {

    private static final int BANIRA_TOOLBAR_GROUP_GAP = 5;
    private static final int BANIRA_TOOLBAR_CONTAINER_GAP = 2;
    private static final int BANIRA_TOOLBAR_RAIL_PADDING = 2;

    /**
     * 翻页/刷新前记录的光标
     */
    private static final KeyValue<Double, Double> pendingMouseRaw = new KeyValue<>(-1D, -1D);

    private static int dustbinPage = -1;
    private static int dustbinTotalPage = -1;
    private static int chunkVaultPage = -1;
    private static int chunkVaultTotalPage = -1;
    private static Button dustbinPrevButton;
    private static Button dustbinNextButton;
    private static final java.util.List<Button> nativeToolbar = new java.util.ArrayList<>();
    private static boolean nativeToolbarShown = true;
    private static DustbinMouseInput mouseHelper = new DustbinMouseInput();
    private static final DustbinToolbarTooltipRegistry<Component> vanillaButtonTooltips = new DustbinToolbarTooltipRegistry<>();
    private static long lastDustbinScreenKeyTime = 0L;

    private DustbinRender() {
    }

    public static int currentPage(boolean chunk) {
        return chunk ? chunkVaultPage : dustbinPage;
    }

    public static int totalPages(boolean chunk) {
        return chunk ? chunkVaultTotalPage : dustbinTotalPage;
    }

    public static void nativeToolbarVisible(boolean visible) {
        if (nativeToolbarShown != visible) {
            mouseHelper = new DustbinMouseInput();
            nativeToolbarShown = visible;
        }
        for (Button button : nativeToolbar) button.visible = visible;
    }

    private static void performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action action) {
        xin.vanilla.aotake.client.ui.AotakeUiPages.action(Minecraft.getInstance().screen, action);
    }

    private static void navigateCurrent(int direction) {
        performCurrentUiAction(direction < 0 ? xin.vanilla.aotake.client.ui.DustbinUiSession.Action.PREVIOUS
                : direction > 0 ? xin.vanilla.aotake.client.ui.DustbinUiSession.Action.NEXT
                : xin.vanilla.aotake.client.ui.DustbinUiSession.Action.REFRESH);
    }

    public static void performUiAction(boolean chunk, xin.vanilla.aotake.client.ui.DustbinUiSession.Action action) {
        switch (action) {
            case CLEAR_CACHE:
                PacketUtils.sendPacketToServer(new ClearDustbinToServer(true, true));
                break;
            case CLEAR_ALL:
                PacketUtils.sendPacketToServer(new ClearDustbinToServer(true, false));
                break;
            case CLEAR_PAGE:
                PacketUtils.sendPacketToServer(new ClearDustbinToServer(false, false));
                break;
            default:
                int direction = action == xin.vanilla.aotake.client.ui.DustbinUiSession.Action.PREVIOUS ? -1
                        : action == xin.vanilla.aotake.client.ui.DustbinUiSession.Action.NEXT ? 1 : 0;
                queueCursorRestoreBeforeContainerRefresh();
                if (chunk) PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(direction));
                else PacketUtils.sendPacketToServer(new OpenDustbinToServer(direction));
        }
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

    public static void updateChunkVaultPage(int page, int totalPage) {
        chunkVaultPage = page;
        chunkVaultTotalPage = totalPage;
    }

    public static boolean isChunkVaultTitle(String title) {
        return StringUtils.isNotNullOrEmpty(title)
                && AotakeLang.get().getI18nFiles().stream().anyMatch(lang ->
                title.startsWith(
                        AotakeComponent.get().transAuto("chunk_vault_title").getString(lang)
                )
        );
    }

    /**
     * 在发送会触发垃圾箱界面重建的 {@link OpenDustbinToServer} 之前调用，记录当前光标
     */
    private static void queueCursorRestoreBeforeContainerRefresh() {
        KeyValue<Double, Double> cur = DustbinMouseInput.rawCursorPosition();
        pendingMouseRaw.key(cur.key()).value(cur.val());
    }

    private static boolean isOurDustbinChestScreen(Screen screen, Minecraft mc) {
        return screen instanceof ContainerScreen
                && mc.player != null
                && isDustbinTitle(screen.getTitle().getString());
    }

    private static boolean isOurSpecialChestScreen(Screen screen, Minecraft mc) {
        return screen instanceof ContainerScreen
                && mc.player != null
                && (isDustbinTitle(screen.getTitle().getString())
                || isChunkVaultTitle(screen.getTitle().getString()));
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
        DustbinMouseInput.setRawCursorPosition(rx, ry);
    }

    public static void handleGuiScreen(ScreenEvent event) {
        Screen screen = event.getGui();
        Minecraft mc = Minecraft.getInstance();
        if (!isOurSpecialChestScreen(screen, mc)) {
            abandonPendingCursorRestore();
            return;
        }

        if (event instanceof InitPost eve) {
            nativeToolbar.clear();
            vanillaButtonTooltips.clear();
            xin.vanilla.aotake.client.ui.AotakeUiPages.attach(screen);
            if (ClientConfig.get().dustbin().dustbinUiStyle() == EnumDustbinClientUiStyle.VANILLA) {
                LocalPlayer player = mc.player;
                ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
                int baseX = accessor.aotake$getLeftPos();
                int baseY = accessor.aotake$getTopPos();
                int yOffset = 0;
                boolean chunkVault = isChunkVaultTitle(screen.getTitle().getString());
                int curPage = chunkVault ? chunkVaultPage : dustbinPage;
                int totPage = chunkVault ? chunkVaultTotalPage : dustbinTotalPage;
                boolean canPrev = DustbinPageNavigation.canNavigate(curPage, totPage, -1);
                boolean canNext = DustbinPageNavigation.canNavigate(curPage, totPage, 1);
                if (!chunkVault && AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                    eve.addWidget(
                            newButton(baseX - 21
                                    , baseY + 21 * (yOffset++)
                                    , 20, 20
                                    , AotakeComponent.get().literal("✕").color(EnumMCColor.RED.getColor())
                                    , button -> performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action.CLEAR_CACHE)
                                    , AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cache")
                            )
                    );
                }
                if (!chunkVault && AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                    eve.addWidget(
                            newButton(baseX - 21
                                    , baseY + 21 * (yOffset++)
                                    , 20, 20
                                    , AotakeComponent.get().literal("✕").color(EnumMCColor.RED.getColor())
                                    , button -> performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action.CLEAR_ALL)
                                    , AotakeComponent.get().trans(EnumI18nType.WORD, "clear_all_dustbin")
                            )
                    );
                    eve.addWidget(
                            newButton(baseX - 21
                                    , baseY + 21 * (yOffset++)
                                    , 20, 20
                                    , AotakeComponent.get().literal("✕").color(EnumMCColor.YELLOW.getColor())
                                    , button -> performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action.CLEAR_PAGE)
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
                                    navigateCurrent(0);
                                }
                                , AotakeComponent.get().trans(EnumI18nType.WORD, "refresh_page")
                        )
                );
                Button prevButton = newButton(baseX - 21
                        , baseY + 21 * (yOffset++)
                        , 20, 20
                        , AotakeComponent.get().literal("▲")
                        , button -> {
                            navigateCurrent(-1);
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
                            navigateCurrent(1);
                        }
                        , AotakeComponent.get().trans(EnumI18nType.WORD, "next_page")
                );
                nextButton.active = canNext;
                dustbinNextButton = nextButton;
                eve.addWidget(nextButton);
            }
        } else if (event instanceof DrawPost rendered) {
            if (xin.vanilla.aotake.client.ui.AotakeUiPages.owns(screen)) {
                xin.vanilla.aotake.client.ui.AotakeUiPages.tooltip(screen, rendered.getPoseStack(), rendered.getMouseX(), rendered.getMouseY());
                return;
            }
            if (ClientConfig.get().dustbin().dustbinUiStyle() == EnumDustbinClientUiStyle.VANILLA) {
                boolean chunkVault = isChunkVaultTitle(screen.getTitle().getString());
                int curPage = chunkVault ? chunkVaultPage : dustbinPage;
                int totPage = chunkVault ? chunkVaultTotalPage : dustbinTotalPage;
                boolean canPrev = DustbinPageNavigation.canNavigate(curPage, totPage, -1);
                boolean canNext = DustbinPageNavigation.canNavigate(curPage, totPage, 1);
                if (dustbinPrevButton != null) {
                    dustbinPrevButton.active = canPrev;
                }
                if (dustbinNextButton != null) {
                    dustbinNextButton.active = canNext;
                }
                Component tooltip = vanillaButtonTooltips.find(rendered.getMouseX(), rendered.getMouseY());
                if (tooltip != null) {
                    TooltipWidget.drawPopupMessage(rendered.getPoseStack(),
                            FontDrawArgs.ofPopo(new Text(tooltip).stack(rendered.getPoseStack()))
                                    .x(rendered.getMouseX()).y(rendered.getMouseY()),
                            ClientThemeManager.getEffectiveTheme(), null);
                }
            }
            EnumDustbinClientUiStyle dustbinUi = ClientConfig.get().dustbin().dustbinUiStyle();
            if (dustbinUi == EnumDustbinClientUiStyle.TEXTURED || dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME) {
                DrawPost eve = (DrawPost) event;
                LocalPlayer player = mc.player;
                int mouseX = eve.getMouseX();
                int mouseY = eve.getMouseY();
                updateMouseClickState(mouseX, mouseY);

                PoseStack stack = eve.getPoseStack();
                BaniraColorConfig baniraTheme = dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                        ? ClientThemeManager.getEffectiveTheme() : null;
                int baseW = 16;
                int baseH = 16;
                ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
                int baseX = DustbinGuiLayoutCache.valid
                        ? DustbinGuiLayoutCache.leftPos + DustbinGuiConfig.getButtonXOffset()
                        : accessor.aotake$getLeftPos();
                int baseY = DustbinGuiLayoutCache.valid
                        ? DustbinGuiLayoutCache.topPos + DustbinGuiConfig.getButtonYOffset()
                        : accessor.aotake$getTopPos();
                if (dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME) {
                    baseX -= BANIRA_TOOLBAR_CONTAINER_GAP;
                    baseY += BANIRA_TOOLBAR_RAIL_PADDING;
                }

                boolean chunkVaultDraw = isChunkVaultTitle(screen.getTitle().getString());
                boolean canClearCache = !chunkVaultDraw
                        && AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR);
                boolean canClearDustbin = !chunkVaultDraw
                        && AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR);
                int curDrawPage = chunkVaultDraw ? chunkVaultPage : dustbinPage;
                int totDrawPage = chunkVaultDraw ? chunkVaultTotalPage : dustbinTotalPage;
                boolean canPrev = DustbinPageNavigation.canNavigate(curDrawPage, totDrawPage, -1);
                boolean canNext = DustbinPageNavigation.canNavigate(curDrawPage, totDrawPage, 1);

                if (dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME) {
                    int themedToolbarButtonCount = 3
                            + (canClearCache ? 1 : 0)
                            + (canClearDustbin ? 2 : 0);
                    int themedToolbarGapCount = 1 + ((canClearCache || canClearDustbin) ? 1 : 0);
                    int toolbarStackHeight = themedToolbarButtonCount * (baseH + 1) - 1
                            + themedToolbarGapCount * BANIRA_TOOLBAR_GROUP_GAP;
                    int railX = baseX - baseW - 1 - BANIRA_TOOLBAR_RAIL_PADDING;
                    int railY = baseY - BANIRA_TOOLBAR_RAIL_PADDING;
                    int railWidth = baseW + BANIRA_TOOLBAR_RAIL_PADDING * 2;
                    int railHeight = toolbarStackHeight + BANIRA_TOOLBAR_RAIL_PADDING * 2;
                    DustbinBaniraToolbarButtonRenderer.drawRail(stack, baniraTheme,
                            railX, railY, railWidth, railHeight);
                }

                int yOffset = 0;
                int toolbarGroupOffset = 0;
                if (canClearCache) {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++) + toolbarGroupOffset;
                    boolean hover = isRectHit(mouseX, mouseY, x, y, w, h);

                    boolean pressVisual = isLeftMousePressing() && hover;
                    if (pressVisual) {
                        x--;
                        y--;
                        w += 2;
                        h += 2;
                    }

                    dustbinDrawToolbarAppearance(stack, dustbinUi, baniraTheme, x, y, w, h, hover, true, pressVisual,
                            dustbinUi == EnumDustbinClientUiStyle.TEXTURED
                                    ? TextureUtils.loadCustomTexture(Identifier.id(), "gui/clear_cache.png") : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME ? ButtonWidget.PresetStyle.CLOSE : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                                    ? DustbinBaniraToolbarButtonRenderer.IconTint.CLEAR_CACHE_CLOSE_ORANGE : null);

                    if (hover) {
                        TooltipWidget.drawPopupMessage(stack,
                                FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cache")).stack(stack))
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (isLeftClickedInRect(mouseX, mouseY, x, y, w, h)) {
                        performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action.CLEAR_CACHE);
                    }
                }
                if (canClearDustbin) {
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++) + toolbarGroupOffset;
                        boolean hover = isRectHit(mouseX, mouseY, x, y, w, h);

                        boolean pressVisual = isLeftMousePressing() && hover;
                        if (pressVisual) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        dustbinDrawToolbarAppearance(stack, dustbinUi, baniraTheme, x, y, w, h, hover, true, pressVisual,
                                dustbinUi == EnumDustbinClientUiStyle.TEXTURED
                                        ? TextureUtils.loadCustomTexture(Identifier.id(), "gui/clear_all.png") : null,
                                dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME ? ButtonWidget.PresetStyle.CLOSE : null,
                                dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                                        ? DustbinBaniraToolbarButtonRenderer.IconTint.CLEAR_ALL_CLOSE_RED : null);

                        if (hover) {
                            TooltipWidget.drawPopupMessage(stack,
                                    FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "clear_all_dustbin")).stack(stack))
                                            .x(mouseX).y(mouseY),
                                    ClientThemeManager.getEffectiveTheme(), null);
                        }

                        if (isLeftClickedInRect(mouseX, mouseY, x, y, w, h)) {
                            performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action.CLEAR_ALL);
                        }
                    }

                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++) + toolbarGroupOffset;
                        boolean hover = isRectHit(mouseX, mouseY, x, y, w, h);

                        boolean pressVisual = isLeftMousePressing() && hover;
                        if (pressVisual) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        dustbinDrawToolbarAppearance(stack, dustbinUi, baniraTheme, x, y, w, h, hover, true, pressVisual,
                                dustbinUi == EnumDustbinClientUiStyle.TEXTURED
                                        ? TextureUtils.loadCustomTexture(Identifier.id(), "gui/clear_page.png") : null,
                                dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME ? ButtonWidget.PresetStyle.MINUS : null,
                                dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                                        ? DustbinBaniraToolbarButtonRenderer.IconTint.CLEAR_PAGE_MINUS_ACCENT : null);

                        if (hover) {
                            TooltipWidget.drawPopupMessage(stack,
                                    FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "clear_cur_dustbin")).stack(stack))
                                            .x(mouseX).y(mouseY),
                                    ClientThemeManager.getEffectiveTheme(), null);
                        }

                        if (isLeftClickedInRect(mouseX, mouseY, x, y, w, h)) {
                            performCurrentUiAction(xin.vanilla.aotake.client.ui.DustbinUiSession.Action.CLEAR_PAGE);
                        }
                    }
                }
                if (dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME && yOffset > 0) {
                    toolbarGroupOffset += BANIRA_TOOLBAR_GROUP_GAP;
                }
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++) + toolbarGroupOffset;
                    boolean hover = isRectHit(mouseX, mouseY, x, y, w, h);

                    boolean pressVisual = isLeftMousePressing() && hover;
                    if (pressVisual) {
                        x--;
                        y--;
                        w += 2;
                        h += 2;
                    }

                    dustbinDrawToolbarAppearance(stack, dustbinUi, baniraTheme, x, y, w, h, hover, true, pressVisual,
                            dustbinUi == EnumDustbinClientUiStyle.TEXTURED
                                    ? TextureUtils.loadCustomTexture(Identifier.id(), "gui/refresh.png") : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME ? ButtonWidget.PresetStyle.RESET : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                                    ? DustbinBaniraToolbarButtonRenderer.IconTint.PRESET_DEFAULT : null);

                    if (hover) {
                        TooltipWidget.drawPopupMessage(stack,
                                FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "refresh_page")).stack(stack))
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (isLeftClickedInRect(mouseX, mouseY, x, y, w, h)) {
                        navigateCurrent(0);
                    }
                }
                if (dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME) {
                    toolbarGroupOffset += BANIRA_TOOLBAR_GROUP_GAP;
                }
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++) + toolbarGroupOffset;
                    boolean hover = canPrev && isRectHit(mouseX, mouseY, x, y, w, h);

                    boolean pressVisual = isLeftMousePressing() && hover;
                    if (pressVisual) {
                        x--;
                        y--;
                        w += 2;
                        h += 2;
                    }

                    dustbinDrawToolbarAppearance(stack, dustbinUi, baniraTheme, x, y, w, h, hover, canPrev, pressVisual,
                            dustbinUi == EnumDustbinClientUiStyle.TEXTURED
                                    ? TextureUtils.loadCustomTexture(Identifier.id(), "gui/up.png") : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME ? ButtonWidget.PresetStyle.ARROW_UP : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                                    ? DustbinBaniraToolbarButtonRenderer.IconTint.PRESET_DEFAULT : null);

                    if (hover) {
                        TooltipWidget.drawPopupMessage(stack,
                                FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "previous_page")).stack(stack))
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (canPrev && isLeftClickedInRect(mouseX, mouseY, x, y, w, h)) {
                        navigateCurrent(-1);
                    }
                }
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++) + toolbarGroupOffset;
                    boolean hover = canNext && isRectHit(mouseX, mouseY, x, y, w, h);

                    boolean pressVisual = isLeftMousePressing() && hover;
                    if (pressVisual) {
                        x--;
                        y--;
                        w += 2;
                        h += 2;
                    }

                    dustbinDrawToolbarAppearance(stack, dustbinUi, baniraTheme, x, y, w, h, hover, canNext, pressVisual,
                            dustbinUi == EnumDustbinClientUiStyle.TEXTURED
                                    ? TextureUtils.loadCustomTexture(Identifier.id(), "gui/down.png") : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME ? ButtonWidget.PresetStyle.ARROW_DOWN : null,
                            dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME
                                    ? DustbinBaniraToolbarButtonRenderer.IconTint.PRESET_DEFAULT : null);

                    if (hover) {
                        TooltipWidget.drawPopupMessage(stack,
                                FontDrawArgs.ofPopo(new Text(AotakeComponent.get().trans(EnumI18nType.WORD, "next_page")).stack(stack))
                                        .x(mouseX).y(mouseY),
                                ClientThemeManager.getEffectiveTheme(), null);
                    }

                    if (canNext && isLeftClickedInRect(mouseX, mouseY, x, y, w, h)) {
                        navigateCurrent(1);
                    }
                }
            }
        } else if (event instanceof KeyPressedPre) {
            KeyPressedPre keyEvent = (KeyPressedPre) event;
            if (keyEvent.getModifiers() != 0) return;
            boolean chunkKeys = screen instanceof ContainerScreen
                    && isChunkVaultTitle(screen.getTitle().getString());
            if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_KEY.currentKey()) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    mc.setScreen(null);
                }
            } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_PRE_KEY.currentKey()) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    int current = chunkKeys ? chunkVaultPage : dustbinPage;
                    int total = chunkKeys ? chunkVaultTotalPage : dustbinTotalPage;
                    if (!DustbinPageNavigation.canNavigate(current, total, -1)) return;
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    navigateCurrent(-1);
                }
            } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.currentKey()) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    int current = chunkKeys ? chunkVaultPage : dustbinPage;
                    int total = chunkKeys ? chunkVaultTotalPage : dustbinTotalPage;
                    if (!DustbinPageNavigation.canNavigate(current, total, 1)) return;
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    navigateCurrent(1);
                }
            }
        }
    }

    public static List<ScreenCoordinate> nativeToolbarRegions(Screen screen) {
        if (!isOurSpecialChestScreen(screen, Minecraft.getInstance())) return List.of();
        List<ScreenCoordinate> regions = new ArrayList<>();
        EnumDustbinClientUiStyle style = ClientConfig.get().dustbin().dustbinUiStyle();
        if (style == EnumDustbinClientUiStyle.VANILLA) {
            for (Button button : nativeToolbar)
                if (button.visible)
                    regions.add(new ScreenCoordinate(button.x, button.y, button.getWidth(), button.getHeight()));
            return regions;
        }
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int x = DustbinGuiLayoutCache.valid
                ? DustbinGuiLayoutCache.leftPos + DustbinGuiConfig.getButtonXOffset() : accessor.aotake$getLeftPos();
        int y = DustbinGuiLayoutCache.valid
                ? DustbinGuiLayoutCache.topPos + DustbinGuiConfig.getButtonYOffset() : accessor.aotake$getTopPos();
        boolean themed = style == EnumDustbinClientUiStyle.BANIRA_THEME;
        if (themed) {
            x -= BANIRA_TOOLBAR_CONTAINER_GAP;
            y += BANIRA_TOOLBAR_RAIL_PADDING;
        }
        boolean chunk = isChunkVaultTitle(screen.getTitle().getString());
        LocalPlayer player = Minecraft.getInstance().player;
        int clearButtons = (!chunk && AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR) ? 1 : 0)
                + (!chunk && AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR) ? 2 : 0);
        for (int i = 0; i < clearButtons; i++) {
            regions.add(new ScreenCoordinate(x - 17, y, 16, 16));
            y += 17;
        }
        if (themed && clearButtons > 0) y += BANIRA_TOOLBAR_GROUP_GAP;
        regions.add(new ScreenCoordinate(x - 17, y, 16, 16));
        y += 17;
        if (themed) y += BANIRA_TOOLBAR_GROUP_GAP;
        regions.add(new ScreenCoordinate(x - 17, y, 16, 16));
        y += 17;
        regions.add(new ScreenCoordinate(x - 17, y, 16, 16));
        return regions;
    }

    private static boolean isRectHit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return x <= mouseX && mouseX <= x + width && y <= mouseY && mouseY <= y + height;
    }

    private static boolean isLeftClickedInRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseHelper.isLeftPressedInRect(x, y, width, height);
    }

    private static boolean isLeftMousePressing() {
        return mouseHelper.isPressingLeftEx();
    }

    private static void updateMouseClickState(double mouseX, double mouseY) {
        mouseHelper.poll(mouseX, mouseY);
    }

    private static void dustbinDrawToolbarAppearance(PoseStack stack,
                                                     EnumDustbinClientUiStyle dustbinUi,
                                                     BaniraColorConfig baniraTheme,
                                                     int x, int y, int w, int h,
                                                     boolean hover,
                                                     boolean enabled,
                                                     boolean pressVisual,
                                                     @Nullable ResourceLocation texture,
                                                     @Nullable ButtonWidget.PresetStyle baniraPreset,
                                                     @Nullable DustbinBaniraToolbarButtonRenderer.IconTint baniraTint) {
        if (dustbinUi == EnumDustbinClientUiStyle.TEXTURED) {
            if (texture == null) {
                return;
            }
            if (!enabled) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
            }
            AbstractGuiUtils.blitBlend(stack, texture, x, y, 0, 0, 0, w, h, w, h);
            if (!enabled) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                AbstractGuiUtils.fill(stack, x, y, w, h, 0x66000000);
            }
            return;
        }
        if (baniraTheme == null || baniraPreset == null || baniraTint == null) {
            return;
        }
        DustbinBaniraToolbarButtonRenderer.draw(stack, baniraTheme, x, y, w, h, hover, pressVisual, enabled, baniraPreset, baniraTint);
    }

    private static Button newButton(int x, int y, int width, int height,
                                    Component label,
                                    Consumer<Button> onPress,
                                    Component tooltip) {
        Button button = new Button(x, y, width, height, label.toVanilla(), onPress::accept);
        nativeToolbar.add(button);
        vanillaButtonTooltips.add(x, y, width, height, tooltip);
        return button;
    }

    /**
     * 加载器 adapter 只需构造这三类事件，UI 主体不再依赖 Forge/Fabric 类型。
     */
    public abstract static class ScreenEvent {
        private final Screen gui;

        protected ScreenEvent(Screen gui) {
            this.gui = gui;
        }

        public Screen getGui() {
            return gui;
        }
    }

    public static final class InitPost extends ScreenEvent {
        private final Consumer<Button> widgetAdder;

        public InitPost(Screen gui, Consumer<Button> widgetAdder) {
            super(gui);
            this.widgetAdder = widgetAdder;
        }

        public void addWidget(Button button) {
            widgetAdder.accept(button);
        }
    }

    public static final class DrawPost extends ScreenEvent {
        private final PoseStack poseStack;
        private final int mouseX;
        private final int mouseY;

        public DrawPost(Screen gui, PoseStack poseStack, int mouseX, int mouseY) {
            super(gui);
            this.poseStack = poseStack;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public PoseStack getPoseStack() {
            return poseStack;
        }

        public int getMouseX() {
            return mouseX;
        }

        public int getMouseY() {
            return mouseY;
        }
    }

    public static final class KeyPressedPre extends ScreenEvent {
        private final int keyCode;
        private final int modifiers;
        private boolean canceled;

        public KeyPressedPre(Screen gui, int keyCode, int modifiers) {
            super(gui);
            this.keyCode = keyCode;
            this.modifiers = modifiers;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public int getModifiers() {
            return modifiers;
        }

        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        public boolean isCanceled() {
            return canceled;
        }
    }
}
