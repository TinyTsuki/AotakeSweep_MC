package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.banira.BaniraComponent;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.ScreenCoordinate;
import xin.vanilla.banira.client.enums.EnumAlignment;
import xin.vanilla.banira.client.enums.EnumOrientation;
import xin.vanilla.banira.client.gui.BaniraScreen;
import xin.vanilla.banira.client.gui.component.Notification;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.event.MouseEvent;
import xin.vanilla.banira.client.gui.event.MouseScrollEvent;
import xin.vanilla.banira.client.gui.widget.*;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.NotificationManager;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.banira.common.enums.EnumSeason;
import xin.vanilla.banira.common.util.ColorUtils;
import xin.vanilla.banira.common.util.PacketUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑 {@link xin.vanilla.aotake.data.player.PlayerSweepData} 中的客户端相关偏好
 */
public class PlayerConfigScreen extends BaniraScreen {

    private static final int CARD_MARGIN = 10;
    private static final int CARD_INNER = 10;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final double LABEL_COLUMN_WIDTH_RATIO = 0.32;
    private static final double LABEL_COLUMN_MIN_WIDTH = 64;
    private static final int GAP_LABEL_TO_VALUE = 4;
    private static final double VALUE_AREA_MIN_WIDTH = 56;
    private static final int SCROLL_WIDTH = 6;
    private static final int SCROLL_GAP = 2;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_PADDING = 12;
    private static final int BUTTON_GAP = 8;
    private static final int CARD_GAP = 1;
    private static final int CARD_RADIUS = 8;
    private static final int CARD_ALPHA = 0xFF;

    private final Args args;
    private boolean showSweepResult;
    private boolean enableWarningVoice;

    private CollapsiblePanelWidget contentRootPanel;
    private ScrollbarWidget scrollbar;
    private double scrollOffset;
    private int contentHeight;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int listTop;
    private int listAreaHeight;
    private int maxListHeight;
    private int contentLeft;
    private int contentW;
    private int btnY;
    private int contentTotalW;
    private final List<ButtonWidget> bottomButtons = new ArrayList<>();

    public PlayerConfigScreen(@Nullable Screen parent, boolean showSweepResult, boolean enableWarningVoice) {
        this(new Args()
                .parentScreen(parent)
                .showSweepResult(showSweepResult)
                .enableWarningVoice(enableWarningVoice));
    }

    public PlayerConfigScreen(@Nullable Args args) {
        super(AotakeComponent.get().transClientAuto("player_prefs_screen_title").toVanilla());
        this.args = args != null ? args : new Args();
        this.showSweepResult = this.args.showSweepResult();
        this.enableWarningVoice = this.args.enableWarningVoice();
        previousScreen(this.args.parentScreen());
        BaniraScreen.inheritThemeAndSeason(this, this.args.parentScreen(), this.args.theme(), this.args.season());
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
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    protected void initWidgets() {
        cardX = CARD_MARGIN;
        cardY = CARD_MARGIN;
        cardW = width - CARD_MARGIN * 2;
        cardH = height - CARD_MARGIN * 2;
        contentLeft = cardX + CARD_INNER;
        contentW = cardW - CARD_INNER * 2 - SCROLL_WIDTH - SCROLL_GAP;
        contentTotalW = contentW + SCROLL_GAP + SCROLL_WIDTH;
        listTop = cardY + CARD_INNER;
        bottomButtons.clear();

        contentRootPanel = buildContentPanel();
        contentHeight = (int) contentRootPanel.height();
        addWidget(contentRootPanel);

        scrollbar = new ScrollbarWidget(this);
        scrollbar.id("player_prefs_scroll");
        scrollbar.orientation(EnumOrientation.VERTICAL);
        scrollbar.minValue(0);
        scrollbar.onValueChanged(v -> {
            scrollOffset = v;
            updateWidgetPositions();
        });
        addWidget(scrollbar);

        ButtonWidget saveBtn = new ButtonWidget(this);
        saveBtn.id("player_prefs_save");
        saveBtn.text(AotakeComponent.get().transClientAuto("save").toString());
        saveBtn.onClick(b -> saveToServer());
        bottomButtons.add(saveBtn);

        ButtonWidget closeBtn = new ButtonWidget(this);
        closeBtn.id("player_prefs_cancel");
        closeBtn.text(BaniraComponent.get().transClientAuto("config_editor_close").toString());
        closeBtn.onClick(b -> onClose());
        bottomButtons.add(closeBtn);

        for (ButtonWidget btn : bottomButtons) {
            addWidget(btn);
        }

        updateLayout();
        updateWidgetPositions();
    }

    private CollapsiblePanelWidget buildContentPanel() {
        CollapsiblePanelWidget root = CollapsiblePanelWidget.createAutoHeight(this, 0, 0, contentW);
        root.text(Text.from(AotakeComponent.get().transClientAuto("player_prefs_screen_title")));
        root.expanded(true);
        root.contentGap(ROW_GAP);
        root.headerHeight(ROW_HEIGHT);
        root.onExpandChanged(p -> syncContentHeight());

        double cw = root.getContentWidth();
        root.addChildAuto(createToggleRow("player_prefs_show", true, cw), ROW_HEIGHT);
        root.addChildAuto(createToggleRow("player_prefs_voice", false, cw), ROW_HEIGHT);
        root.refreshLayout();
        return root;
    }

    private EntryRowWidget createToggleRow(String id, boolean sweepResultRow, double rowWidth) {
        EntryRowWidget row = new EntryRowWidget(this);
        row.bounds(new ScreenCoordinate(0, 0, rowWidth, ROW_HEIGHT));

        LabelWidget label = new LabelWidget(this);
        label.id(id + "_label");
        label.bounds(new ScreenCoordinate(0, 0, labelTextWidth(rowWidth), ROW_HEIGHT));
        label.text(rowLabel(sweepResultRow));
        label.textWrap(false);
        label.textVerticalAlign(EnumAlignment.CENTER);

        ButtonWidget btn = new ButtonWidget(this);
        btn.id(id + "_toggle");
        btn.bounds(new ScreenCoordinate(valueStartX(rowWidth), 0, valueWidgetWidth(rowWidth), ROW_HEIGHT));
        btn.text(toggleText(sweepResultRow ? showSweepResult : enableWarningVoice));
        btn.onClick(b -> {
            if (sweepResultRow) {
                showSweepResult = !showSweepResult;
                b.text(toggleText(showSweepResult));
            } else {
                enableWarningVoice = !enableWarningVoice;
                b.text(toggleText(enableWarningVoice));
            }
        });

        row.addChild(label);
        row.addChild(btn);
        return row;
    }

    private double labelColumnEndX(double rowWidth) {
        if (rowWidth <= 1) {
            return 1;
        }
        double maxEnd = rowWidth - VALUE_AREA_MIN_WIDTH;
        if (maxEnd < 1) {
            return Math.max(1, rowWidth * 0.2);
        }
        double fromRatio = rowWidth * LABEL_COLUMN_WIDTH_RATIO;
        double end = Math.max(LABEL_COLUMN_MIN_WIDTH, Math.min(fromRatio, maxEnd));
        return Math.min(end, maxEnd);
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

    private Text rowLabel(boolean sweepResultRow) {
        return Text.from(AotakeComponent.get().transClientAuto(sweepResultRow
                ? "player_prefs_show_row"
                : "player_prefs_voice_row", ""));
    }

    private String toggleText(boolean enabled) {
        return AotakeComponent.get().transClientAuto(enabled ? "enabled" : "disabled").toString();
    }

    private void syncContentHeight() {
        if (contentRootPanel != null) {
            contentRootPanel.refreshLayout();
            contentHeight = (int) contentRootPanel.height();
            updateLayout();
            updateWidgetPositions();
        }
    }

    private void saveToServer() {
        if (Minecraft.getInstance().getConnection() == null) {
            Notification notification = Notification.ofComponent(BaniraComponent.get().transClientAuto("config_editor_sync_not_connected"));
            notification.position(EnumPosition.TOP_RIGHT).durationTime(3500);
            NotificationManager.get().addNotification(notification);
            return;
        }
        PacketUtils.sendPacketToServer(new PlayerConfigSyncToServer(showSweepResult, enableWarningVoice));
        AotakeSweep.setClientCachedPlayerSweepPrefs(showSweepResult, enableWarningVoice);
        onClose();
    }

    private void updateLayout() {
        maxListHeight = Math.max(0, cardH - CARD_INNER * 2 - BUTTON_HEIGHT - CARD_GAP);

        int btnAreaH = BUTTON_HEIGHT + CARD_INNER;
        int btnAreaTop = cardY + cardH - btnAreaH;
        int centeredBtnY = btnAreaTop + (btnAreaH - BUTTON_HEIGHT) / 2;

        if (contentHeight <= maxListHeight) {
            listAreaHeight = Math.max(1, contentHeight);
            btnY = centeredBtnY;
            scrollOffset = 0;
            scrollbar.maxValue(0);
            scrollbar.value(0);
            scrollbar.visible(false);
            scrollbar.scrollingCoordinates(new ArrayList<>());
        } else {
            listAreaHeight = maxListHeight;
            btnY = centeredBtnY;
            scrollbar.visible(true);
            scrollbar.bounds(new ScreenCoordinate(contentLeft + contentW + SCROLL_GAP, listTop, SCROLL_WIDTH, listAreaHeight));
            scrollbar.maxValue(Math.max(0, contentHeight - listAreaHeight));
            scrollbar.value(Math.min(scrollOffset, scrollbar.maxValue()));
            scrollOffset = scrollbar.value();
            scrollbar.visibleSize(listAreaHeight);
            scrollbar.scrollingCoordinates(new ArrayList<>());
            scrollbar.addScrollHoverArea(new ScreenCoordinate(contentLeft, listTop, contentTotalW, listAreaHeight));
        }

        int n = bottomButtons.size();
        int[] btnWidths = new int[n];
        for (int i = 0; i < n; i++) {
            btnWidths[i] = font.width(bottomButtons.get(i).text().toString()) + BUTTON_PADDING * 2;
        }

        int contentTotal = cardW - CARD_INNER * 2 - CARD_GAP;
        int zoneW = contentTotal / 2;
        int leftRectW = CARD_INNER + zoneW;
        int rightRectW = cardW - leftRectW - CARD_GAP;
        int rightRectX = cardX + leftRectW + CARD_GAP;
        int lastIdx = n - 1;
        int leftTotalW = 0;
        for (int i = 0; i < lastIdx; i++) {
            leftTotalW += btnWidths[i] + (i > 0 ? BUTTON_GAP : 0);
        }
        int rightTotalW = btnWidths[lastIdx];
        double leftScale = leftTotalW > zoneW ? (double) zoneW / leftTotalW : 1.0;
        double rightScale = rightTotalW > zoneW ? (double) zoneW / rightTotalW : 1.0;
        int leftTotalScaled = (int) (leftTotalW * leftScale);
        int curX = cardX + (leftRectW - leftTotalScaled) / 2;
        for (int i = 0; i < n; i++) {
            ButtonWidget btn = bottomButtons.get(i);
            double scale = i < lastIdx ? leftScale : rightScale;
            int bw = Math.max(20, (int) (btnWidths[i] * scale));
            if (i == lastIdx) {
                curX = rightRectX + (rightRectW - bw) / 2;
            }
            btn.bounds(new ScreenCoordinate(curX, btnY, bw, BUTTON_HEIGHT));
            curX += bw + BUTTON_GAP;
        }

        for (ButtonWidget btn : bottomButtons) {
            TooltipWidget tip = btn.findChildByType(TooltipWidget.class);
            if (tip != null && btn.bounds() != null) {
                ScreenCoordinate bc = btn.bounds();
                tip.bounds(new ScreenCoordinate(0, 0, bc.width(), bc.height()));
            }
        }
    }

    private void updateWidgetPositions() {
        if (contentRootPanel != null) {
            contentRootPanel.bounds(new ScreenCoordinate(contentLeft, listTop - (int) scrollOffset, contentW, contentHeight));
        }
    }

    @Override
    protected void renderWidgets(GuiGraphics graphics, float partialTicks) {
        PoseStack stack = graphics.pose();
        BaniraColorConfig theme = getEffectiveTheme();
        int cardBg = ColorUtils.applyAlphaToArgb(theme.bgSurface(), CARD_ALPHA);
        int btnAreaH = BUTTON_HEIGHT + CARD_INNER;
        int btnAreaTop = cardY + cardH - btnAreaH;
        int contentHDraw = btnAreaTop - cardY - CARD_GAP;

        AbstractGuiUtils.drawRoundedRect(stack, cardX, cardY, cardW, contentHDraw,
                CARD_RADIUS, CARD_RADIUS, 0, 0, cardBg);

        int contentTotal = cardW - CARD_INNER * 2 - CARD_GAP;
        int zoneW = contentTotal / 2;
        int leftRectW = CARD_INNER + zoneW;
        int rightRectW = cardW - leftRectW - CARD_GAP;
        AbstractGuiUtils.drawRoundedRect(stack, cardX, btnAreaTop, leftRectW, btnAreaH,
                0, 0, CARD_RADIUS, 0, cardBg);
        AbstractGuiUtils.drawRoundedRect(stack, cardX + leftRectW + CARD_GAP, btnAreaTop, rightRectW, btnAreaH,
                0, 0, 0, CARD_RADIUS, cardBg);

        AbstractGuiUtils.enableScissor(contentLeft, listTop, contentTotalW, Math.max(1, listAreaHeight));

        if (contentRootPanel != null && contentRootPanel.visible()) {
            if (contentRootPanel.enabled() && contentRootPanel.needsUpdate()) {
                contentRootPanel.update();
            }
            contentRootPanel.render(graphics, partialTicks);
        }
        if (scrollbar != null && scrollbar.visible()) {
            if (scrollbar.enabled() && scrollbar.needsUpdate()) {
                scrollbar.update();
            }
            scrollbar.render(graphics, partialTicks);
        }

        AbstractGuiUtils.disableScissor();

        for (ButtonWidget btn : bottomButtons) {
            if (btn.visible()) {
                if (btn.enabled() && btn.needsUpdate()) {
                    btn.update();
                }
                btn.render(graphics, partialTicks);
            }
        }

        for (IWidget widget : widgets()) {
            if (widget == contentRootPanel || widget == scrollbar || bottomButtons.contains(widget)) {
                continue;
            }
            if (widget.parent() != null || !widget.visible()) {
                continue;
            }
            if (widget.enabled() && widget.needsUpdate()) {
                widget.update();
            }
            widget.render(graphics, partialTicks);
        }
    }

    @Override
    protected void onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderWidgets(graphics, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && contentRootPanel != null && contentRootPanel.visible() && contentRootPanel.enabled()
                && contentRootPanel.isMouseInside(mouseX, mouseY)
                && contentRootPanel.handleMouseScroll(MouseScrollEvent.of(mouseX, mouseY, scrollY))) {
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (scrollbar != null && scrollY != 0) {
            double newVal = scrollbar.value() - scrollY * 20;
            newVal = Math.max(scrollbar.minValue(), Math.min(scrollbar.maxValue(), newVal));
            scrollbar.value(newVal);
            scrollOffset = newVal;
            updateWidgetPositions();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (args.parentScreen() != null) {
            Minecraft.getInstance().setScreen(args.parentScreen());
        } else {
            super.onClose();
        }
    }

    private static class EntryRowWidget extends BaseWidget {
        EntryRowWidget(BaniraScreen screen) {
            super(screen);
        }

        @Override
        public double effectiveHeight() {
            double maxBottom = 0;
            for (IWidget child : children()) {
                if (child == null || !child.visible()) {
                    continue;
                }
                ScreenCoordinate b = child.bounds();
                if (b != null) {
                    double bottom = b.y() + child.effectiveHeight();
                    if (bottom > maxBottom) {
                        maxBottom = bottom;
                    }
                }
            }
            return maxBottom > 0 ? maxBottom : (bounds() != null ? bounds().height() : 0);
        }

        @Override
        protected boolean onMouseClick(MouseEvent event) {
            return true;
        }

        @Override
        public void render(GuiGraphics graphics, float partialTicks) {
            if (!visible()) {
                return;
            }
            renderChildren(graphics, partialTicks);
        }
    }
}
