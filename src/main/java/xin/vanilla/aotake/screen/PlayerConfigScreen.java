package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.enums.EnumAlignment;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Notification;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.widget.BaseWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.gui.widget.CollapsiblePanelWidget;
import xin.vanilla.banira.client.gui.widget.IWidget;
import xin.vanilla.banira.client.gui.widget.LabelWidget;
import xin.vanilla.banira.client.util.NotificationManager;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.banira.common.enums.EnumSeason;
import xin.vanilla.banira.common.util.PacketUtils;

import javax.annotation.Nullable;

/**
 * 编辑当前玩家的清理显示与提示音偏好。
 */
public class PlayerConfigScreen extends xin.vanilla.banira.client.gui.PlayerConfigScreen {
    private static final double LABEL_COLUMN_WIDTH_RATIO = 0.32;
    private static final double LABEL_COLUMN_MIN_WIDTH = 64;
    private static final int GAP_LABEL_TO_VALUE = 4;
    private static final double VALUE_AREA_MIN_WIDTH = 56;

    private boolean showSweepResult;
    private boolean enableWarningVoice;

    public PlayerConfigScreen(@Nullable Screen parent, boolean showSweepResult, boolean enableWarningVoice) {
        this(new Args().parentScreen(parent).showSweepResult(showSweepResult)
                .enableWarningVoice(enableWarningVoice));
    }

    public PlayerConfigScreen(@Nullable Args args) {
        this(args != null ? args : new Args(), true);
    }

    private PlayerConfigScreen(Args args, boolean ignored) {
        super(AotakeSweep.MODID, AotakeComponent.get().transClientAuto("player_prefs_screen_title"),
                args.parentScreen(), args.theme(), args.season());
        showSweepResult = args.showSweepResult();
        enableWarningVoice = args.enableWarningVoice();
    }

    @Data
    @Accessors(chain = true, fluent = true)
    public static class Args {
        @Nullable
        private Screen parentScreen;
        @Nullable
        private BaniraColorConfig theme;
        @Nullable
        private EnumSeason season;
        private boolean showSweepResult = AotakeSweep.isClientCachedShowSweepResult();
        private boolean enableWarningVoice = AotakeSweep.isClientCachedEnableWarningVoice();
    }

    @Override
    protected void buildPlayerConfig(CollapsiblePanelWidget root) {
        addToggleRow(root, "player_prefs_show", true);
        addToggleRow(root, "player_prefs_voice", false);
    }

    private void addToggleRow(CollapsiblePanelWidget root, String id, boolean sweepResultRow) {
        double rowWidth = root.getContentWidth();
        EntryRowWidget row = new EntryRowWidget(this);
        row.id(id);
        row.bounds(new ScreenCoordinate(0, 0, rowWidth, ROW_HEIGHT));

        Component title = AotakeComponent.get().transClientAuto(sweepResultRow
                ? "player_prefs_show_row" : "player_prefs_voice_row", "");
        LabelWidget label = new LabelWidget(this);
        label.id(id + "_label");
        label.bounds(new ScreenCoordinate(0, 0, labelTextWidth(rowWidth), ROW_HEIGHT));
        label.text(Text.from(title));
        label.textWrap(false);
        label.textVerticalAlign(EnumAlignment.CENTER);

        ButtonWidget button = new ButtonWidget(this);
        button.id(id + "_toggle");
        button.bounds(new ScreenCoordinate(valueStartX(rowWidth), 0,
                valueWidgetWidth(rowWidth), ROW_HEIGHT));
        button.text(toggleText(sweepResultRow ? showSweepResult : enableWarningVoice));
        button.onClick(clicked -> {
            if (sweepResultRow) {
                showSweepResult = !showSweepResult;
                clicked.text(toggleText(showSweepResult));
            } else {
                enableWarningVoice = !enableWarningVoice;
                clicked.text(toggleText(enableWarningVoice));
            }
        });

        row.addChild(label);
        row.addChild(button);
        addPlayerRow(root, row, ROW_HEIGHT, label, null, title, null,
                id, sweepResultRow ? "showSweepResult" : "enableWarningVoice");
    }

    private double labelColumnEndX(double rowWidth) {
        double maxEnd = rowWidth - VALUE_AREA_MIN_WIDTH;
        if (maxEnd < 1) return Math.max(1, rowWidth * 0.2);
        return Math.min(Math.max(LABEL_COLUMN_MIN_WIDTH,
                Math.min(rowWidth * LABEL_COLUMN_WIDTH_RATIO, maxEnd)), maxEnd);
    }

    private double labelTextWidth(double rowWidth) {
        return Math.max(1, labelColumnEndX(rowWidth) - GAP_LABEL_TO_VALUE);
    }

    private double valueStartX(double rowWidth) {
        return labelColumnEndX(rowWidth);
    }

    private double valueWidgetWidth(double rowWidth) {
        return Math.max(1, rowWidth - labelColumnEndX(rowWidth));
    }

    private String toggleText(boolean enabled) {
        return AotakeComponent.get().transClientAuto(enabled ? "enabled" : "disabled").toString();
    }

    @Override
    protected void savePlayerConfig() {
        if (Minecraft.getInstance().getConnection() == null) {
            Notification notification = Notification.ofComponent(
                    BaniraComponent.get().transClientAuto("config_editor_sync_not_connected"));
            notification.position(EnumPosition.TOP_RIGHT).durationTime(3500);
            NotificationManager.get().addNotification(notification);
            return;
        }
        PacketUtils.sendPacketToServer(new PlayerConfigSyncToServer(showSweepResult, enableWarningVoice));
        AotakeSweep.setClientCachedPlayerSweepPrefs(showSweepResult, enableWarningVoice);
        onClose();
    }

    private static final class EntryRowWidget extends BaseWidget {
        private EntryRowWidget(BaniraScreen screen) {
            super(screen);
        }

        @Override
        public double effectiveHeight() {
            double maxBottom = 0;
            for (IWidget child : children()) {
                if (child == null || !child.visible() || child.bounds() == null) continue;
                maxBottom = Math.max(maxBottom, child.bounds().y() + child.effectiveHeight());
            }
            return maxBottom > 0 ? maxBottom : (bounds() != null ? bounds().height() : 0);
        }

        @Override
        protected boolean onMouseClick(MouseEvent event) {
            return true;
        }

        @Override
        public void render(MatrixStack stack, float partialTicks) {
            if (visible()) renderChildren(stack, partialTicks);
        }
    }
}
