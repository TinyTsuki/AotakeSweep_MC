package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
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
import xin.vanilla.banira.common.util.DateUtils;
import xin.vanilla.banira.common.util.NumberUtils;

import java.util.Date;
import java.util.List;

/**
 * 经验条区域上的扫地倒计时进度条绘制
 */
public final class ProgressRender {

    private ProgressRender() {
    }

    public static void render(RenderGameOverlayEvent event, boolean showProgressHeld) {
        ClientConfig.ProgressBarView cp = ClientConfig.get().progressBar();
        ClientConfig.ProgressBarLeafView cpl = cp.leaf();
        ClientConfig.ProgressBarPoleView cpp = cp.pole();
        ClientConfig.ProgressBarTextView cpt = cp.text();
        if (event instanceof RenderGameOverlayEvent.Post
                && ((cpp.hideExperienceBarPole() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.POLE))
                || (cpt.hideExperienceBarText() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.TEXT))
                || (cpl.hideExperienceBarLeaf() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.LEAF)))
        ) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;
        MatrixStack ms = event.getMatrixStack();
        boolean hold = showProgressHeld && mc.screen == null;
        List<EnumProgressBarType> displayList = hold ? cp.progressBarDisplayHold() : cp.progressBarDisplayNormal();

        double scale = cpt.progressBarTextSize() / 16.0;

        if (displayList.contains(EnumProgressBarType.POLE)) {
            if (cpp.hideExperienceBarPole()) {
                event.setCanceled(true);
            }
            int width = cpp.progressBarPoleWidth();
            int height = cpp.progressBarPoleHeight();
            int drawX = getPoleX();
            int drawY = getPoleY();

            TransformArgs transformArgs = new TransformArgs(ms);
            transformArgs.angle(cpp.progressBarPoleAngle())
                    .center(EnumPosition.CENTER)
                    .x(drawX)
                    .y(drawY)
                    .width(width)
                    .height(height);
            ResourceLocation poleTex = TextureUtils.loadCustomTexture(Identifier.id(), "gui/pole.png");
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) ->
                    blitProgressAtlas(ms, poleTex, (int) arg.x(), (int) arg.y(), (int) arg.width(), (int) arg.height()));
        }

        if (displayList.contains(EnumProgressBarType.TEXT)) {
            if (cpt.hideExperienceBarText()) {
                event.setCanceled(true);
            }
            drawProgressCountdownText(mc, ms, cpt, scale);
        }

        if (displayList.contains(EnumProgressBarType.LEAF)) {
            if (cpl.hideExperienceBarLeaf()) {
                event.setCanceled(true);
            }
            int poleW = cpp.progressBarPoleWidth();

            int width = cpl.progressBarLeafWidth();
            int height = cpl.progressBarLeafHeight();
            int rangeWidth = poleW - width;
            int startX = getLeafX();

            int drawX = (int) (startX + rangeWidth * getProgress());
            int drawY = getLeafY();

            TransformArgs transformArgs = new TransformArgs(ms);
            transformArgs.angle(cpl.progressBarLeafAngle())
                    .center(EnumPosition.CENTER)
                    .x(drawX)
                    .y(drawY)
                    .width(width)
                    .height(height);
            ResourceLocation leafTex = TextureUtils.loadCustomTexture(Identifier.id(), "gui/leaf.png");
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) ->
                    blitProgressAtlas(ms, leafTex, (int) arg.x(), (int) arg.y(), (int) arg.width(), (int) arg.height()));
        }
    }

    private static void blitProgressAtlas(MatrixStack ms, ResourceLocation texture, int x, int y, int destW, int destH) {
        KeyValue<Integer, Integer> dim = TextureUtils.getTextureSize(texture);
        int texW = dim.key();
        int texH = dim.val();
        if (texW <= 0 || texH <= 0) {
            return;
        }
        AbstractGuiUtils.blitBlend(ms, texture, x, y, destW, destH, 0, 0, texW, texH, texW, texH);
    }

    private static int getLeafX() {
        ClientConfig.ProgressBarLeafView cpl = ClientConfig.get().progressBar().leaf();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
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
        ClientConfig.ProgressBarLeafView cpl = ClientConfig.get().progressBar().leaf();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
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
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
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
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
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
    private static void drawProgressCountdownText(Minecraft mc, MatrixStack stack, ClientConfig.ProgressBarTextView cpt, double scale) {
        String line = getText();
        if (line.isEmpty()) {
            return;
        }
        float lh = mc.font.lineHeight;
        Color color = getTextColor();
        Text probe = Text.literal(line).font(mc.font).color(color).shadow(true);
        FontDrawArgs measureArgs = FontDrawArgs.of(probe).x(0).y(0).inScreen(false).wrap(false).fontSize(lh);
        KeyValue<Integer, Integer> layout = LabelWidget.calculateLimitedTextSize(measureArgs);
        int layoutW = layout.key();
        int layoutH = layout.val();

        double[] anchor = progressTextAnchorScreen(cpt);
        float ax = (float) anchor[0];
        float ay = (float) anchor[1];
        float pivotX = alignPivotX(cpt.progressBarTextAlignH(), layoutW);
        float pivotY = alignPivotY(cpt.progressBarTextAlignV(), layoutH);
        float angle = (float) cpt.progressBarTextAngle();

        stack.pushPose();
        stack.translate(ax, ay, 0);
        if (Math.abs(angle % 360f) > 1e-3f) {
            stack.mulPose(Vector3f.ZP.rotationDegrees(angle));
        }
        stack.scale((float) scale, (float) scale, 1f);
        stack.translate(-pivotX, -pivotY, 0);

        Text drawText = Text.literal(line).stack(stack).font(mc.font).color(color).shadow(true);
        LabelWidget.drawLimitedText(FontDrawArgs.of(drawText).x(0).y(0).inScreen(false).wrap(false).fontSize(lh));
        stack.popPose();
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
    private static double[] progressTextAnchorScreen(ClientConfig.ProgressBarTextView cpt) {
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
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

    private static String getText() {
        Date now = new Date();
        Date nextSweepTime = getNextSweepTime(now);
        if (nextSweepTime.after(now)) {
            long seconds = DateUtils.secondsOfTwo(now, nextSweepTime);
            if (seconds / 60 >= 100) {
                long hours = seconds / 3600;
                long minutes = (seconds % 3600) / 60;
                long secs = seconds % 60;
                return String.format("%02d:%02d:%02d", hours, minutes, secs);
            } else {
                long minutes = seconds / 60;
                long secs = seconds % 60;
                return String.format("%02d:%02d", minutes, secs);
            }
        } else {
            return "∞";
        }
    }

    private static double getProgress() {
        Date now = new Date();
        Date nextSweepTime = getNextSweepTime(now);
        if (nextSweepTime.after(now)) {
            Date lastSweepTime = DateUtils.addMilliSecond(nextSweepTime, -AotakeSweep.getSweepTime().key().intValue());
            return (double) (now.getTime() - lastSweepTime.getTime()) / (double) (nextSweepTime.getTime() - lastSweepTime.getTime());
        } else {
            return 0;
        }
    }

    private static Date getNextSweepTime(Date now) {
        int difference = (int) ((AotakeSweep.getClientServerTime().key() - AotakeSweep.getClientServerTime().val()) / 1000L);
        Date nextSweepTime = DateUtils.addSecond(new Date(AotakeSweep.getSweepTime().val()), difference);
        if (nextSweepTime.before(now)) {
            for (int i = 0; i < 5; i++) {
                if (nextSweepTime.before(now)) {
                    nextSweepTime = DateUtils.addMilliSecond(nextSweepTime, AotakeSweep.getSweepTime().key().intValue());
                } else {
                    break;
                }
            }
        }
        return nextSweepTime;
    }

    private static Color getTextColor() {
        return Color.parse(ClientConfig.get().progressBar().text().progressBarTextColor(), Color.white());
    }
}
