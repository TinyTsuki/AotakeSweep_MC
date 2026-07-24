package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.components.Button;
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
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.api.client.BaniraKeyHandle;
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
import java.util.function.Consumer;

/**
 * 垃圾箱 {@link ContainerScreen} 的侧栏控件绘制与快捷键处理
 */
public final class DustbinRender {

    private static final DustbinMouseInput mouseHelper = new DustbinMouseInput();

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

        if (event instanceof InitPost) {
            if (ClientConfig.get().dustbin().dustbinUiStyle() == EnumDustbinClientUiStyle.VANILLA) {
                InitPost eve = (InitPost) event;
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
                                    , button -> PacketUtils.sendPacketToServer(new ClearDustbinToServer(true, true))
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
                                    , button -> PacketUtils.sendPacketToServer(new ClearDustbinToServer(true, false))
                                    , AotakeComponent.get().trans(EnumI18nType.WORD, "clear_all_dustbin")
                            )
                    );
                    eve.addWidget(
                            newButton(baseX - 21
                                    , baseY + 21 * (yOffset++)
                                    , 20, 20
                                    , AotakeComponent.get().literal("✕").color(EnumMCColor.YELLOW.getColor())
                                    , button -> PacketUtils.sendPacketToServer(new ClearDustbinToServer(false, false))
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
                                    if (chunkVault) {
                                        PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(0));
                                    } else {
                                        PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
                                    }
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
                            if (chunkVault) {
                                PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(-1));
                            } else {
                                PacketUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                            }
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
                            if (chunkVault) {
                                PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(1));
                            } else {
                                PacketUtils.sendPacketToServer(new OpenDustbinToServer(1));
                            }
                        }
                        , AotakeComponent.get().trans(EnumI18nType.WORD, "next_page")
                );
                nextButton.active = canNext;
                dustbinNextButton = nextButton;
                eve.addWidget(nextButton);
            }
        } else if (event instanceof DrawPost) {
            DrawPost drawEvent = (DrawPost) event;
            mouseHelper.poll(drawEvent.getMouseX(), drawEvent.getMouseY());
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
            }
            EnumDustbinClientUiStyle dustbinUi = ClientConfig.get().dustbin().dustbinUiStyle();
            if (dustbinUi == EnumDustbinClientUiStyle.TEXTURED || dustbinUi == EnumDustbinClientUiStyle.BANIRA_THEME) {
                DrawPost eve = drawEvent;
                LocalPlayer player = mc.player;
                int mouseX = eve.getMouseX();
                int mouseY = eve.getMouseY();

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

                boolean chunkVaultDraw = isChunkVaultTitle(screen.getTitle().getString());
                int curDrawPage = chunkVaultDraw ? chunkVaultPage : dustbinPage;
                int totDrawPage = chunkVaultDraw ? chunkVaultTotalPage : dustbinTotalPage;
                boolean canPrev = DustbinPageNavigation.canNavigate(curDrawPage, totDrawPage, -1);
                boolean canNext = DustbinPageNavigation.canNavigate(curDrawPage, totDrawPage, 1);

                int yOffset = 0;
                if (!chunkVaultDraw && AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++);
                    boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                    boolean pressVisual = mouseHelper.isPressingLeftEx() && hover;
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

                    if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        PacketUtils.sendPacketToServer(new ClearDustbinToServer(true, true));
                    }
                }
                if (!chunkVaultDraw && AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        boolean pressVisual = mouseHelper.isPressingLeftEx() && hover;
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

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            PacketUtils.sendPacketToServer(new ClearDustbinToServer(true, false));
                        }
                    }

                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        boolean pressVisual = mouseHelper.isPressingLeftEx() && hover;
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

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            PacketUtils.sendPacketToServer(new ClearDustbinToServer(false, false));
                        }
                    }
                }
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++);
                    boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                    boolean pressVisual = mouseHelper.isPressingLeftEx() && hover;
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

                    if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        queueCursorRestoreBeforeContainerRefresh();
                        if (chunkVaultDraw) {
                            PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(0));
                        } else {
                            PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
                        }
                    }
                }
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++);
                    boolean hover = canPrev && mouseHelper.isHoverInRect(x, y, w, h);

                    boolean pressVisual = mouseHelper.isPressingLeftEx() && hover;
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

                    if (canPrev && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        queueCursorRestoreBeforeContainerRefresh();
                        if (chunkVaultDraw) {
                            PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(-1));
                        } else {
                            PacketUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                        }
                    }
                }
                {
                    int w = baseW;
                    int h = baseH;
                    int x = baseX - w - 1;
                    int y = baseY + (h + 1) * (yOffset++);
                    boolean hover = canNext && mouseHelper.isHoverInRect(x, y, w, h);

                    boolean pressVisual = mouseHelper.isPressingLeftEx() && hover;
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

                    if (canNext && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                        queueCursorRestoreBeforeContainerRefresh();
                        if (chunkVaultDraw) {
                            PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(1));
                        } else {
                            PacketUtils.sendPacketToServer(new OpenDustbinToServer(1));
                        }
                    }
                }
            }
        } else if (event instanceof KeyPressedPre) {
            KeyPressedPre keyEvent = (KeyPressedPre) event;
            if (keyEvent.getModifiers() != 0) return;
            boolean chunkKeys = screen instanceof ContainerScreen
                    && isChunkVaultTitle(screen.getTitle().getString());
            if (keyEvent.getKeyCode() == keyCode(ClientModEventHandler.DUSTBIN_KEY)) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    mc.setScreen(null);
                }
            } else if (keyEvent.getKeyCode() == keyCode(ClientModEventHandler.DUSTBIN_PRE_KEY)) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    int current = chunkKeys ? chunkVaultPage : dustbinPage;
                    int total = chunkKeys ? chunkVaultTotalPage : dustbinTotalPage;
                    if (!DustbinPageNavigation.canNavigate(current, total, -1)) return;
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    queueCursorRestoreBeforeContainerRefresh();
                    if (chunkKeys) {
                        PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(-1));
                    } else {
                        PacketUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                    }
                }
            } else if (keyEvent.getKeyCode() == keyCode(ClientModEventHandler.DUSTBIN_NEXT_KEY)) {
                if (System.currentTimeMillis() - lastDustbinScreenKeyTime > 200) {
                    int current = chunkKeys ? chunkVaultPage : dustbinPage;
                    int total = chunkKeys ? chunkVaultTotalPage : dustbinTotalPage;
                    if (!DustbinPageNavigation.canNavigate(current, total, 1)) return;
                    lastDustbinScreenKeyTime = System.currentTimeMillis();
                    queueCursorRestoreBeforeContainerRefresh();
                    if (chunkKeys) {
                        PacketUtils.sendPacketToServer(new ChunkVaultNavigateToServer(1));
                    } else {
                        PacketUtils.sendPacketToServer(new OpenDustbinToServer(1));
                    }
                }
            }
        }
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

    private static int keyCode(BaniraKeyHandle handle) {
        return handle.currentKey();
    }

    private static Button newButton(int x, int y, int width, int height,
                                    Component label,
                                    Consumer<Button> onPress,
                                    Component tooltip) {
        return new Button(x, y, width, height, label.toVanilla(), onPress::accept);
    }

    /** 加载器 adapter 只需构造这三类事件，UI 主体不再依赖 Forge/Fabric 类型。 */
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
