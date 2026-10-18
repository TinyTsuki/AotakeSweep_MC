package xin.vanilla.aotake.client.ui;

import net.minecraft.client.gui.screens.Screen;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.DropdownOption;
import xin.vanilla.banira.internal.client.ui.page.PlayerConfigUiSession;

import java.util.Arrays;
import java.util.function.BiConsumer;

public final class AotakePlayerUiSession {
    private final PlayerConfigUiSession shared;

    public AotakePlayerUiSession(Screen owner, boolean showResult, boolean warningVoice,
                                 BiConsumer<Boolean, Boolean> save) {
        shared = new PlayerConfigUiSession(new InputFormScreen.Args().setParentScreen(owner)
                .setTitle(Text.from(AotakeComponent.get().transClientAuto("player_prefs_screen_title")))
                .setCallback(result -> save.accept(Boolean.parseBoolean(result.value("showSweepResult")),
                        Boolean.parseBoolean(result.value("enableWarningVoice"))))
                .addWidget(toggle("showSweepResult", "player_prefs_show_row", showResult))
                .addWidget(toggle("enableWarningVoice", "player_prefs_voice_row", warningVoice)));
    }

    private static InputFormScreen.Widget toggle(String name, String key, boolean value) {
        return new InputFormScreen.Widget().name(name).type(InputFormScreen.WidgetType.DROPDOWN)
                .title(Text.from(AotakeComponent.get().transClientAuto(key, "")))
                .tooltip(Text.from(AotakeComponent.get().transClientAuto(key, "")))
                .dropdownOptionEntries(Arrays.asList(
                        new DropdownOption("true", AotakeComponent.get().transClientAuto("enabled").toString()),
                        new DropdownOption("false", AotakeComponent.get().transClientAuto("disabled").toString())))
                .defaultValue(Boolean.toString(value));
    }

    public PlayerConfigUiSession shared() { return shared; }
    public boolean showResult() { return Boolean.parseBoolean(shared.fields().draft("showSweepResult")); }
    public boolean warningVoice() { return Boolean.parseBoolean(shared.fields().draft("enableWarningVoice")); }
    public void showResult(boolean value) { shared.fields().draft("showSweepResult", Boolean.toString(value)); }
    public void warningVoice(boolean value) { shared.fields().draft("enableWarningVoice", Boolean.toString(value)); }
    public boolean submit() { return shared.fields().submit(); }
}
