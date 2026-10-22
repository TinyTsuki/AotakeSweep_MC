package xin.vanilla.aotake.client.ui;

import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.banira.api.client.ui.*;
import java.util.List;

public final class SweepProgressUiBinding implements UiHudBinding {
    public static final String ID = "aotake_sweep:progress";
    private UiHudFrame frame;
    private UiHudFrame applied;
    private UiView attached;
    private final java.util.function.BooleanSupplier contextVisible;

    public SweepProgressUiBinding() { this(() -> true); }
    public SweepProgressUiBinding(java.util.function.BooleanSupplier contextVisible) {
        this.contextVisible = java.util.Objects.requireNonNull(contextVisible);
    }

    public UiHudSpec spec() { return new UiHudSpec(ID, "aotake_sweep/progress.html", this, true, true); }
    public void prepare(UiHudFrame next) { frame = next; }
    public void clear() { frame = null; }
    @Override public boolean visible() { return contextVisible.getAsBoolean() && frame != null && !frame.parts().isEmpty(); }
    @Override public void bind(UiView view) { attached = view; applied = null; update(view); }
    @Override public void update(UiView view) {
        if (view == attached && visible() && !frame.equals(applied)) {
            view.hudFrame(frame); applied = frame;
        }
    }
    @Override public void detach(UiDetachReason reason) { attached = null; applied = null; }

    public record Selection(List<EnumProgressBarType> types, boolean hideExperience) {
        public Selection { types = List.copyOf(types); }
    }
    public static Selection selection(List<EnumProgressBarType> normal, List<EnumProgressBarType> held,
                                      boolean key, boolean gui, boolean leaf, boolean pole, boolean text) {
        List<EnumProgressBarType> types = key && !gui ? held : normal;
        return new Selection(types, types.contains(EnumProgressBarType.LEAF) && leaf
                || types.contains(EnumProgressBarType.POLE) && pole || types.contains(EnumProgressBarType.TEXT) && text);
    }
    public static boolean drawInPhase(boolean pre, boolean hideExperience) { return pre == hideExperience; }

    public static double textAngleRadians(double configured) {
        float angle = (float) configured;
        return Math.abs(angle % 360f) > 1e-3f ? angle : 0;
    }

    public static final class KeyState {
        private boolean previousDown;
        private boolean toggled;
        private boolean toggleMode;
        public boolean tick(boolean down, boolean gui, boolean toggle) {
            if (toggleMode != toggle) { toggled = false; toggleMode = toggle; }
            if (!gui && toggle && down && !previousDown) toggled = !toggled;
            previousDown = down;
            return !gui && (toggle ? toggled : down);
        }
        public void reset() { previousDown = false; toggled = false; toggleMode = false; }
    }
}
