package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.joml.Quaternionf;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown;
import xin.vanilla.aotake.client.ui.SweepProgressUiBinding;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.ClientConfigView;
import xin.vanilla.aotake.enums.EnumProgressBarTextAlignH;
import xin.vanilla.aotake.enums.EnumProgressBarTextAlignV;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.banira.client.data.FontDrawArgs;
import xin.vanilla.banira.client.data.TransformArgs;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.widget.LabelWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.TextureUtils;
import xin.vanilla.banira.common.data.Color;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.banira.api.client.ui.BaniraUi;
import xin.vanilla.banira.api.client.ui.UiHudFrame;
import xin.vanilla.banira.common.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 经验条区域上的扫地倒计时进度条绘制
 */
public final class ProgressRender {
    private static final SweepProgressUiBinding BINDING = new SweepProgressUiBinding(() -> {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.options.hideGui && !AotakeMinimapCountdown.snapshot().formattedTime().isEmpty();
    });
    private static AutoCloseable registration;

    private ProgressRender() {
    }

    public static void register() {
        if (registration == null) registration = BaniraUi.registerHud(BINDING.spec());
    }

    public static void reset() { BINDING.clear(); }

    public static void render(RenderGuiOverlayEvent event, boolean showProgressHeld) {
        ClientConfigView.ProgressBarView cp = ClientConfig.get().progressBar();
        ClientConfigView.ProgressBarView.LeafView cpl = cp.leaf();
        ClientConfigView.ProgressBarView.PoleView cpp = cp.pole();
        ClientConfigView.ProgressBarView.TextView cpt = cp.text();
        Minecraft mc = Minecraft.getInstance();
        var time = AotakeMinimapCountdown.snapshot();
        if (mc.options.hideGui || mc.player == null || time.formattedTime().isEmpty()) { BINDING.clear(); return; }
        var selection = SweepProgressUiBinding.selection(cp.progressBarDisplayNormal(), cp.progressBarDisplayHold(),
                showProgressHeld, mc.screen != null, cpl.hideExperienceBarLeaf(), cpp.hideExperienceBarPole(), cpt.hideExperienceBarText());
        boolean pre = event instanceof RenderGuiOverlayEvent.Pre;
        if (!SweepProgressUiBinding.drawInPhase(pre, selection.hideExperience())) return;
        if (pre && selection.hideExperience()) event.setCanceled(true);
        List<EnumProgressBarType> displayList = selection.types();
        List<UiHudFrame.Part> parts = new ArrayList<>(3);
        double scale = cpt.progressBarTextSize() / 16.0;
        if (displayList.contains(EnumProgressBarType.POLE)) {
            int width = cpp.progressBarPoleWidth();
            int height = cpp.progressBarPoleHeight();
            int drawX = getPoleX();
            int drawY = getPoleY();
            ResourceLocation poleTex = TextureUtils.loadCustomTexture(Identifier.id(), "gui/pole.png");
            parts.add(new UiHudFrame.Sprite("pole", poleTex, drawX, drawY, width, height, cpp.progressBarPoleAngle()));
        }
        if (displayList.contains(EnumProgressBarType.TEXT)) {
            String line = time.formattedTime();
            float lh = mc.font.lineHeight;
            Text probe = Text.literal(line).font(mc.font).color(getTextColor()).shadow(true);
            KeyValue<Integer, Integer> layout = LabelWidget.calculateLimitedTextSize(
                    FontDrawArgs.of(probe).x(0).y(0).inScreen(false).wrap(false).fontSize(lh));
            double[] anchor = progressTextAnchorScreen(cpt);
            parts.add(new UiHudFrame.Text("countdown", line, anchor[0], anchor[1], layout.key(), layout.val(), lh,
                    scale, SweepProgressUiBinding.textAngleRadians(cpt.progressBarTextAngle()), alignPivotX(cpt.progressBarTextAlignH(), layout.key()),
                    alignPivotY(cpt.progressBarTextAlignV(), layout.val()), getTextColor().argb()));
        }
        if (displayList.contains(EnumProgressBarType.LEAF)) {
            int poleW = cpp.progressBarPoleWidth();
            int width = cpl.progressBarLeafWidth();
            int height = cpl.progressBarLeafHeight();
            int rangeWidth = poleW - width;
            int startX = getLeafX();
            int drawX = (int) (startX + rangeWidth * time.progress());
            int drawY = getLeafY();
            ResourceLocation leafTex = TextureUtils.loadCustomTexture(Identifier.id(), "gui/leaf.png");
            parts.add(new UiHudFrame.Sprite("leaf", leafTex, drawX, drawY, width, height, cpl.progressBarLeafAngle()));
        }
        UiHudFrame frame = new UiHudFrame(parts);
        BINDING.prepare(frame);
        if (parts.isEmpty() || BaniraUi.renderHud(SweepProgressUiBinding.ID, event.getGuiGraphics())) return;
        PoseStack ms = event.getGuiGraphics().pose();
        for (UiHudFrame.Part part : parts) {
            if (part instanceof UiHudFrame.Sprite sprite) {
                TransformArgs transformArgs = new TransformArgs(ms).angle(sprite.angleDegrees()).center(EnumPosition.CENTER)
                        .x(sprite.x()).y(sprite.y()).width(sprite.width()).height(sprite.height());
                AbstractGuiUtils.renderByTransform(transformArgs, arg ->
                        blitProgressAtlas(ms, sprite.texture(), (int) arg.x(), (int) arg.y(), (int) arg.width(), (int) arg.height()));
            } else if (part instanceof UiHudFrame.Text text) {
                drawProgressCountdownText(mc, ms, text);
            }
        }
    }

    private static void blitProgressAtlas(PoseStack ms, ResourceLocation texture, int x, int y, int destW, int destH) {
        KeyValue<Integer, Integer> dim = TextureUtils.getTextureSize(texture);
        int texW = dim.key();
        int texH = dim.val();
        if (texW <= 0 || texH <= 0) {
            return;
        }
        AbstractGuiUtils.blitBlend(ms, texture, x, y, destW, destH, 0, 0, texW, texH, texW, texH);
    }

    private static int getLeafX() {
        ClientConfigView.ProgressBarView.LeafView cpl = ClientConfig.get().progressBar().leaf();
        ClientConfigView.ProgressBarView.PoleView cpp = ClientConfig.get().progressBar().pole();
        int baseX = getPoleX();
        int width = cpp.progressBarPoleWidth();
        double x;
        String xString = cpl.progressBarLeafPosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = NumberUtils.toInt(xString);
        }
        int quadrant = cpl.progressBarLeafScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (cpl.progressBarLeafBase()) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= cpl.progressBarLeafWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= cpl.progressBarLeafWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getLeafY() {
        ClientConfigView.ProgressBarView.LeafView cpl = ClientConfig.get().progressBar().leaf();
        ClientConfigView.ProgressBarView.PoleView cpp = ClientConfig.get().progressBar().pole();
        int baseY = getPoleY();
        int height = cpp.progressBarPoleHeight();
        double y;
        String yString = cpl.progressBarLeafPosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = NumberUtils.toInt(yString);
        }
        int quadrant = cpl.progressBarLeafScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (cpl.progressBarLeafBase()) {
            case CENTER: {
                y -= cpl.progressBarLeafHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= cpl.progressBarLeafHeight();
            }
            break;
        }
        return (int) y;
    }

    private static int getPoleX() {
        ClientConfigView.ProgressBarView.PoleView cpp = ClientConfig.get().progressBar().pole();
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        double x;
        String xString = cpp.progressBarPolePosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = NumberUtils.toInt(xString);
        }
        int quadrant = cpp.progressBarPoleScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = width - x;
        }
        switch (cpp.progressBarPoleBase()) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= cpp.progressBarPoleWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= cpp.progressBarPoleWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getPoleY() {
        ClientConfigView.ProgressBarView.PoleView cpp = ClientConfig.get().progressBar().pole();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double y;
        String yString = cpp.progressBarPolePosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = NumberUtils.toInt(yString);
        }
        int quadrant = cpp.progressBarPoleScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = height - y;
        }
        switch (cpp.progressBarPoleBase()) {
            case CENTER: {
                y -= cpp.progressBarPoleHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= cpp.progressBarPoleHeight();
            }
            break;
        }
        return (int) y;
    }

    /**
     * 倒计时文字
     */
    private static void drawProgressCountdownText(Minecraft mc, PoseStack stack, UiHudFrame.Text text) {
        stack.pushPose();
        try {
            stack.translate(text.x(), text.y(), 0);
            if (text.angleRadians() != 0) {
                stack.mulPose(new Quaternionf().rotationZ((float) text.angleRadians()));
            }
            stack.scale((float) text.scale(), (float) text.scale(), 1f);
            stack.translate(-text.pivotX(), -text.pivotY(), 0);
            Text drawText = Text.literal(text.value()).stack(stack).font(mc.font).color(Color.argb(text.color())).shadow(true);
            LabelWidget.drawLimitedText(FontDrawArgs.of(drawText).x(0).y(0).inScreen(false).wrap(false).fontSize(text.fontSize()));
        } finally { stack.popPose(); }
    }

    private static float alignPivotX(EnumProgressBarTextAlignH h, int layoutWidth) {
        switch (h) {
            case CENTER:
                return layoutWidth / 2f;
            case RIGHT:
                return layoutWidth;
            case LEFT:
            default:
                return 0f;
        }
    }

    private static float alignPivotY(EnumProgressBarTextAlignV v, int layoutHeight) {
        switch (v) {
            case CENTER:
                return layoutHeight / 2f;
            case BOTTOM:
                return layoutHeight;
            case TOP:
            default:
                return 0f;
        }
    }

    /**
     * 配置中「相对竹竿」的参考点（屏幕坐标），不含文字尺寸。
     */
    private static double[] progressTextAnchorScreen(ClientConfigView.ProgressBarView.TextView cpt) {
        ClientConfigView.ProgressBarView.PoleView cpp = ClientConfig.get().progressBar().pole();
        double baseX = getPoleX();
        double baseY = getPoleY();
        int poleW = cpp.progressBarPoleWidth();
        int poleH = cpp.progressBarPoleHeight();
        String[] parts = cpt.progressBarTextPosition().split(",");
        if (parts.length < 2) {
            return new double[]{baseX, baseY};
        }
        double relX;
        String xString = parts[0];
        if (xString.endsWith("%")) {
            relX = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * poleW;
        } else {
            relX = NumberUtils.toInt(xString);
        }
        double relY;
        String yString = parts[1];
        if (yString.endsWith("%")) {
            relY = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * poleH;
        } else {
            relY = NumberUtils.toInt(yString);
        }
        int quadrant = cpt.progressBarTextScreenQuadrant();
        double x = (quadrant == 2 || quadrant == 3) ? baseX - relX : baseX + relX;
        double y = (quadrant == 1 || quadrant == 2) ? baseY - relY : baseY + relY;
        return new double[]{x, y};
    }

    private static Color getTextColor() {
        return Color.parse(ClientConfig.get().progressBar().text().progressBarTextColor(), Color.white());
    }
}
