package xin.vanilla.aotake.client.ui;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import xin.vanilla.aotake.util.DustbinPageNavigation;
import xin.vanilla.banira.api.client.ui.*;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.common.data.Component;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DustbinUiSession implements UiPageSession {
    public enum Action {CLEAR_CACHE, CLEAR_ALL, CLEAR_PAGE, REFRESH, PREVIOUS, NEXT}

    private final AbstractContainerMenu menu;
    private final List<Slot> slots;
    private final int[] coordinates;
    private final boolean chunkVault;
    private final BooleanSupplier current;
    private final Consumer<Action> actions;
    private final Runnable closeScreen;
    private final List<AutoCloseable> bindings = new ArrayList<>();
    private UiView view;
    private boolean closed, clearCache, clearDustbin;
    private boolean closeRequested;
    private int iconStates = -1;
    private int page = -1, pages = -1;
    private int capturedButtons;
    private Layout layout;
    private Map<String, ScreenCoordinate> cachedRegions;

    private record Layout(int left, int top, int width, int height, boolean cache, boolean clear) {
    }

    public DustbinUiSession(AbstractContainerMenu menu, boolean chunkVault,
                            BooleanSupplier current, Consumer<Action> actions) {
        this(menu, chunkVault, current, actions, () -> {
        });
    }

    public DustbinUiSession(AbstractContainerMenu menu, boolean chunkVault,
                            BooleanSupplier current, Consumer<Action> actions, Runnable closeScreen) {
        this.menu = Objects.requireNonNull(menu);
        this.chunkVault = chunkVault;
        this.current = Objects.requireNonNull(current);
        this.actions = Objects.requireNonNull(actions);
        this.closeScreen = Objects.requireNonNull(closeScreen);
        slots = new ArrayList<>(menu.slots);
        coordinates = new int[slots.size() * 2];
        for (int i = 0; i < slots.size(); i++) {
            coordinates[i * 2] = slots.get(i).x;
            coordinates[i * 2 + 1] = slots.get(i).y;
        }
    }

    public boolean matches(AbstractContainerMenu candidate) {
        if (closed || candidate != menu || slots.size() != menu.slots.size() || !current.getAsBoolean()) return false;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot != slots.get(i) || slot.x != coordinates[i * 2] || slot.y != coordinates[i * 2 + 1]) return false;
        }
        return true;
    }

    public void state(int page, int pages, boolean clearCache, boolean clearDustbin) {
        this.page = page;
        this.pages = pages;
        this.clearCache = !chunkVault && clearCache;
        this.clearDustbin = !chunkVault && clearDustbin;
        if (view != null) {
            view.text("page_label", xin.vanilla.aotake.AotakeComponent.get().literal(
                    (page > 0 ? String.valueOf(page) : "-") + "/" + (pages > 0 ? String.valueOf(pages) : "-")));
            int nextIcons = 0;
            for (Action action : Action.values()) {
                boolean enabled = allowed(action);
                view.enabled(id(action), enabled);
                if (enabled) nextIcons |= 1 << action.ordinal();
            }
            if (nextIcons != iconStates) {
                iconStates = nextIcons;
                for (Action action : Action.values())
                    view.gameElement(id(action) + "_icon",
                            icon(id(action) + ((nextIcons & 1 << action.ordinal()) == 0 ? "_disabled" : "")));
            }
        }
    }

    public boolean allowed(Action action) {
        if (closeRequested || !matches(menu) || !menu.getCarried().isEmpty()) return false;
        switch (action) {
            case CLEAR_CACHE:
                return clearCache;
            case CLEAR_ALL:
            case CLEAR_PAGE:
                return clearDustbin;
            case PREVIOUS:
                return DustbinPageNavigation.canNavigate(page, pages, -1);
            case NEXT:
                return DustbinPageNavigation.canNavigate(page, pages, 1);
            default:
                return true;
        }
    }

    public boolean perform(Action action) {
        if (!allowed(action)) return false;
        actions.accept(action);
        return true;
    }

    public static String id(Action action) {
        return action.name().toLowerCase(Locale.ROOT);
    }

    public Map<String, ScreenCoordinate> regions(int left, int top, int width, int height) {
        if (!matches(menu) || slots.size() != 90 || width < 96 || height < 1) return Collections.emptyMap();
        Layout next = new Layout(left, top, width, height, clearCache, clearDustbin);
        if (next.equals(layout)) return cachedRegions;
        Map<String, ScreenCoordinate> result = new LinkedHashMap<>();
        result.put("panel", new ScreenCoordinate(left, top, width, height));
        result.put("brand", new ScreenCoordinate(left + 7, top + 3, 11, 11));
        result.put("title", new ScreenCoordinate(left + 22, top + 4, width - 76, 11));
        result.put("page_label", new ScreenCoordinate(left + width - 51, top + 4, 30, 11));
        result.put("close", new ScreenCoordinate(left + width - 18, top + 1, 15, 15));
        result.put("close_icon", new ScreenCoordinate(left + width - 14, top + 5, 7, 7));
        result.put("inventory_label", new ScreenCoordinate(left + 8, top + height - 95, width - 16, 11));
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.x < 1 || slot.y < 1 || slot.x + 16 > width || slot.y + 16 > height) return Collections.emptyMap();
            result.put("slot_" + i, new ScreenCoordinate(left + slot.x - 1, top + slot.y - 1, 18, 18));
        }
        int y = top;
        for (Action action : Action.values()) {
            if (action == Action.CLEAR_CACHE && !clearCache
                    || (action == Action.CLEAR_ALL || action == Action.CLEAR_PAGE) && !clearDustbin) continue;
            result.put(id(action), new ScreenCoordinate(left - 24, y, 21, 20));
            result.put(id(action) + "_icon", new ScreenCoordinate(left - 19.5, y + 4, 12, 12));
            y += 23;
        }
        layout = next;
        cachedRegions = Collections.unmodifiableMap(result);
        return cachedRegions;
    }

    public boolean click(double x, double y, int button) {
        if (button < 0 || button > 2) return false;
        if (hitClose(x, y)) {
            capturedButtons |= 1 << button;
            if (button == 0) requestClose();
            return true;
        }
        Action action = hit(x, y);
        if (action == null) return false;
        capturedButtons |= 1 << button;
        if (button == 0) perform(action);
        return true;
    }

    public boolean captureNativePress(double x, double y, int button, Collection<ScreenCoordinate> regions) {
        if (button < 0 || button > 2 || !matches(menu) || menu.getCarried().isEmpty()
                || !Double.isFinite(x) || !Double.isFinite(y) || regions == null || regions.size() > 6) return false;
        for (ScreenCoordinate bounds : regions) {
            if (bounds == null || !Double.isFinite(bounds.x()) || !Double.isFinite(bounds.y())
                    || !Double.isFinite(bounds.width()) || !Double.isFinite(bounds.height())
                    || Math.abs(bounds.x()) > 16384 || Math.abs(bounds.y()) > 16384
                    || bounds.width() <= 0 || bounds.height() <= 0 || bounds.width() > 16384 || bounds.height() > 16384)
                continue;
            if (x >= bounds.x() && x < bounds.x() + bounds.width()
                    && y >= bounds.y() && y < bounds.y() + bounds.height()) {
                capturedButtons |= 1 << button;
                return true;
            }
        }
        return false;
    }

    public boolean release(int button) {
        if (button < 0 || button > 2 || (capturedButtons & (1 << button)) == 0) return false;
        capturedButtons &= ~(1 << button);
        return true;
    }

    public Action hit(double x, double y) {
        if (view == null || !matches(menu) || !Double.isFinite(x) || !Double.isFinite(y)) return null;
        for (Action action : Action.values()) {
            if (action == Action.CLEAR_CACHE && !clearCache
                    || (action == Action.CLEAR_ALL || action == Action.CLEAR_PAGE) && !clearDustbin) continue;
            ScreenCoordinate b = view.bounds(id(action));
            if (b != null && Double.isFinite(b.x()) && Double.isFinite(b.y()) && Double.isFinite(b.width()) && Double.isFinite(b.height())
                    && b.width() > 0 && b.height() > 0
                    && x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height()) return action;
        }
        return null;
    }

    public boolean hitClose(double x, double y) {
        if (view == null || !matches(menu) || !Double.isFinite(x) || !Double.isFinite(y)) return false;
        ScreenCoordinate b = view.bounds("close");
        return b != null && Double.isFinite(b.x()) && Double.isFinite(b.y()) && Double.isFinite(b.width())
                && Double.isFinite(b.height()) && b.width() > 0 && b.height() > 0
                && x >= b.x() && x < b.x() + b.width() && y >= b.y() && y < b.y() + b.height();
    }

    private void requestClose() {
        if (closeRequested || !matches(menu)) return;
        closeRequested = true;
        closeScreen.run();
    }

    private static UiGameElement icon(String name) {
        return UiGameElement.sprite(xin.vanilla.banira.client.data.Texture.of(new net.minecraft.resources.ResourceLocation(
                "apricityui", "apricity/aotake_sweep/icons/" + name + ".png"), 32, 32));
    }

    @Override
    public void bind(UiView next) {
        detach(UiDetachReason.RELOAD);
        if (closed) return;
        view = next;
        iconStates = -1;
        next.gameElement("brand", UiGameElement.sprite(xin.vanilla.banira.client.data.Texture.of(
                new net.minecraft.resources.ResourceLocation("aotake_sweep", "gui/quick_icon.png"), 200, 200)));
        next.gameElement("close_icon", UiGameElement.sprite(xin.vanilla.banira.client.data.Texture.of(
                new net.minecraft.resources.ResourceLocation("apricityui", "apricity/banira_codex/icons/close.png"), 16, 16)));
        bindings.add(next.onAction("close", () -> {
            if (view == next) requestClose();
        }));
        for (Action action : Action.values()) {
            bindings.add(next.onAction(id(action), () -> {
                if (view == next) perform(action);
            }));
            next.enabled(id(action), allowed(action));
        }
        state(page, pages, clearCache, clearDustbin);
    }

    public void label(Action action, Component label, Component tooltip) {
        if (view != null) {
            view.text(id(action), label);
            view.tooltip(id(action), tooltip);
        }
    }

    @Override
    public void detach(UiDetachReason reason) {
        view = null;
        for (AutoCloseable binding : bindings) {
            try {
                binding.close();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }
        bindings.clear();
    }

    @Override
    public void close(UiCloseReason reason) {
        if (closed) return;
        closed = true;
        capturedButtons = 0;
        detach(UiDetachReason.SWITCH);
    }
}
