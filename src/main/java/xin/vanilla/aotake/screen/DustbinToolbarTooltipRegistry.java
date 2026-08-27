package xin.vanilla.aotake.screen;

import java.util.ArrayList;
import java.util.List;

/** 保存原版按钮没有能力持有的 Tooltip 及其命中区域。 */
final class DustbinToolbarTooltipRegistry<T> {
    private final List<Entry<T>> entries = new ArrayList<>();

    void clear() {
        entries.clear();
    }

    void add(int x, int y, int width, int height, T tooltip) {
        entries.add(new Entry<>(x, y, width, height, tooltip));
    }

    T find(double mouseX, double mouseY) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry<T> entry = entries.get(i);
            if (entry.contains(mouseX, mouseY)) return entry.tooltip;
        }
        return null;
    }

    private static final class Entry<T> {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final T tooltip;

        private Entry(int x, int y, int width, int height, T tooltip) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tooltip = tooltip;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
