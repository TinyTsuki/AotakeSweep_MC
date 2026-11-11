package xin.vanilla.aotake.client.ui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.mixin.ContainerScreenAccessor;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.util.ClientThemeManager;

import java.util.Map;

public final class AotakeUiPages {
    private static ChestScreen screen;
    private static DustbinUiSession session;
    private static UiContainerLayer layer;
    private static boolean owned;
    private static boolean registered;

    private AotakeUiPages() {
    }

    public static void register() {
        registered = true;
    }

    public static void attach(Screen candidate) {
        if (!registered || !(candidate instanceof ChestScreen)) return;
        ChestScreen chest = (ChestScreen) candidate;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != chest.getMenu()
                || chest.getMenu().slots.size() != 90) return;
        String title = chest.getTitle().getString();
        boolean chunk = DustbinRender.isChunkVaultTitle(title);
        if (!chunk && !DustbinRender.isDustbinTitle(title)) return;
        if (screen == chest) return;
        close(screen);
        screen = chest;
        session = new DustbinUiSession(chest.getMenu(), chunk,
                () -> mc.screen == chest && mc.player != null && mc.player.containerMenu == chest.getMenu(),
                action -> DustbinRender.performUiAction(chunk, action), chest::onClose, () -> mc.player == null
                ? net.minecraft.item.ItemStack.EMPTY : mc.player.inventory.getCarried());
    }

    public static boolean renderBackground(ChestScreen chest, MatrixStack graphics) {
        attach(chest);
        owned = false;
        if (screen != chest || session == null || !session.matches(chest.getMenu())) {
            if (screen == chest && layer != null) {
                layer.close();
                layer = null;
            }
            if (screen == chest && session != null) session.detach(UiDetachReason.SWITCH);
            DustbinRender.nativeToolbarVisible(true);
            return false;
        }
        boolean chunk = DustbinRender.isChunkVaultTitle(chest.getTitle().getString());
        Minecraft mc = Minecraft.getInstance();
        session.state(DustbinRender.currentPage(chunk), DustbinRender.totalPages(chunk),
                AotakeUtils.hasCommandPermission(mc.player, EnumCommandType.CACHE_CLEAR),
                AotakeUtils.hasCommandPermission(mc.player, EnumCommandType.DUSTBIN_CLEAR));
        try {
            if (layer == null) {
                layer = BaniraUi.container(new UiPageSpec("aotake_sweep:containers", "aotake_sweep/dustbin.html", session,
                                previous -> chest, ClientThemeManager::getEffectiveTheme),
                        () -> mc.screen == chest && mc.player != null && mc.player.containerMenu == chest.getMenu());
                if (layer != null) {
                    session.bind(layer.view());
                    layer.view().text("title", AotakeComponent.get().transClientAuto(chunk ? "chunk_vault" : "dustbin"));
                    layer.view().text("inventory_label", AotakeComponent.get().literal(new net.minecraft.util.text.TranslationTextComponent("container.inventory").getString()));
                    String[] keys = {"clear_cache", "clear_all_dustbin", "clear_cur_dustbin", "refresh_page", "previous_page", "next_page"};
                    int i = 0;
                    for (DustbinUiSession.Action action : DustbinUiSession.Action.values()) {
                        session.label(action, AotakeComponent.get().empty(), AotakeComponent.get().transClientAuto(keys[i]));
                        i++;
                    }
                }
            }
            if (layer != null) {
                ContainerScreenAccessor geometry = (ContainerScreenAccessor) chest;
                Map<String, ScreenCoordinate> regions = session.regions(geometry.aotake$getLeftPos(), geometry.aotake$getTopPos(),
                        geometry.aotake$getImageWidth(), geometry.aotake$getImageHeight());
                if (regions.isEmpty()) layer.fallback();
                else owned = layer.render(graphics, regions);
                if (!owned) {
                    session.detach(UiDetachReason.SWITCH);
                    layer.close();
                    layer = null;
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            owned = false;
            if (layer != null) layer.fallback();
            layer = null;
            session.detach(UiDetachReason.SWITCH);
            org.apache.logging.log4j.LogManager.getLogger().warn("Aotake AUI appearance failed; preserving native container", failure);
        }
        DustbinRender.nativeToolbarVisible(!owned);
        return owned;
    }

    public static boolean owns(Screen candidate) {
        return candidate == screen && owned;
    }

    public static boolean click(Screen candidate, double x, double y, int button) {
        if (candidate != screen || session == null) return false;
        return owns(candidate) ? session.click(x, y, button)
                : session.captureNativePress(x, y, button, DustbinRender.nativeToolbarRegions(candidate));
    }

    public static boolean release(Screen candidate, int button) {
        return candidate == screen && session != null && session.release(button);
    }

    public static void action(Screen candidate, DustbinUiSession.Action action) {
        attach(candidate);
        if (screen != candidate || session == null || !session.matches(screen.getMenu())) return;
        boolean chunk = DustbinRender.isChunkVaultTitle(screen.getTitle().getString());
        lombok.var player = Minecraft.getInstance().player;
        session.state(DustbinRender.currentPage(chunk), DustbinRender.totalPages(chunk),
                AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR),
                AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR));
        session.perform(action);
    }

    public static void tooltip(Screen candidate, MatrixStack graphics, int mouseX, int mouseY) {
        if (!owns(candidate) || session == null) return;
        DustbinUiSession.Action action = session.hit(mouseX, mouseY);
        if (action != null) {
            candidate.renderTooltip(graphics,
                    AotakeComponent.get().transClientAuto(tooltipKey(action)).toVanilla(), mouseX, mouseY);
        } else if (session.hitClose(mouseX, mouseY)) {
            candidate.renderTooltip(graphics,
                    xin.vanilla.banira.BaniraComponent.get().transClientAuto("config_editor_close").toVanilla(), mouseX, mouseY);
        } else if (layer != null) {
            ScreenCoordinate title = layer.view().bounds("title");
            if (title != null && mouseX >= title.x() && mouseX < title.x() + title.width()
                    && mouseY >= title.y() && mouseY < title.y() + title.height())
                candidate.renderTooltip(graphics, candidate.getTitle(), mouseX, mouseY);
        }
    }

    private static String tooltipKey(DustbinUiSession.Action action) {
        switch (action) {
            case CLEAR_CACHE:
                return "clear_cache";
            case CLEAR_ALL:
                return "clear_all_dustbin";
            case CLEAR_PAGE:
                return "clear_cur_dustbin";
            case REFRESH:
                return "refresh_page";
            case PREVIOUS:
                return "previous_page";
            case NEXT:
                return "next_page";
            default:
                throw new IllegalArgumentException("Unknown dustbin action");
        }
    }

    public static void close(Screen candidate) {
        if (candidate != screen) return;
        owned = false;
        if (layer != null) layer.close();
        layer = null;
        if (session != null) session.close(UiCloseReason.USER_CLOSE);
        session = null;
        screen = null;
        DustbinRender.nativeToolbarVisible(true);
    }
}
