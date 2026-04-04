package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
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
                && ((cpp.hideExperienceBarPole() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.POLE.name()))
                || (cpt.hideExperienceBarText() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.TEXT.name()))
                || (cpl.hideExperienceBarLeaf() && cp.progressBarDisplayNormal().contains(EnumProgressBarType.LEAF.name())))
        ) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;
        MatrixStack ms = event.getMatrixStack();
        boolean hold = showProgressHeld && mc.screen == null;
        List<? extends String> displayList = hold ? cp.progressBarDisplayHold() : cp.progressBarDisplayNormal();

        double scale = cpt.progressBarTextSize() / 16.0;

        if (displayList.contains(EnumProgressBarType.POLE.name())) {
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

        if (displayList.contains(EnumProgressBarType.TEXT.name())) {
            if (cpt.hideExperienceBarText()) {
                event.setCanceled(true);
            }
            TransformArgs textTransformArgs = new TransformArgs(ms);
            textTransformArgs.scale(scale)
                    .angle(cpt.progressBarTextAngle())
                    .center(EnumPosition.CENTER)
                    .x(getTextX())
                    .y(getTextY())
                    .width(getTextWidth())
                    .height(getTextHeight());
            AbstractGuiUtils.renderByTransform(textTransformArgs, (arg) -> {
                Text time = Text.literal(getText())
                        .stack(arg.stack())
                        .color(getTextColor())
                        .shadow(true)
                        .font(Minecraft.getInstance().font);
                LabelWidget.drawLimitedText(FontDrawArgs.of(time).x(arg.x()).y(arg.y()).inScreen(false));
            });
        }

        if (displayList.contains(EnumProgressBarType.LEAF.name())) {
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

    /**
     * 进度条贴图需传入<strong>纹理文件真实宽高</strong>参与 UV；误把屏幕绘制宽高当作纹理尺寸会导致采样错误（竹竿呈细线等）。
     */
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
        switch (EnumPosition.valueOf(cpl.progressBarLeafBase())) {
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
        switch (EnumPosition.valueOf(cpl.progressBarLeafBase())) {
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
        switch (EnumPosition.valueOf(cpp.progressBarPoleBase())) {
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
        switch (EnumPosition.valueOf(cpp.progressBarPoleBase())) {
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

    private static int getTextX() {
        ClientConfig.ProgressBarTextView cpt = ClientConfig.get().progressBar().text();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int baseX = getPoleX();
        int width = cpp.progressBarPoleWidth();
        double x;
        String xString = cpt.progressBarTextPosition().split(",")[0];
        if (xString.endsWith("%")) {
            x = NumberUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = NumberUtils.toInt(xString);
        }
        int quadrant = cpt.progressBarTextScreenQuadrant();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumPosition.valueOf(cpt.progressBarTextBase())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= cpt.progressBarTextSize() / 16.0 * getTextWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= cpt.progressBarTextSize() / 16.0 * getTextWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getTextY() {
        ClientConfig.ProgressBarTextView cpt = ClientConfig.get().progressBar().text();
        ClientConfig.ProgressBarPoleView cpp = ClientConfig.get().progressBar().pole();
        int baseY = getPoleY();
        int height = cpp.progressBarPoleHeight();
        double y;
        String yString = cpt.progressBarTextPosition().split(",")[1];
        if (yString.endsWith("%")) {
            y = NumberUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = NumberUtils.toInt(yString);
        }
        int quadrant = cpt.progressBarTextScreenQuadrant();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumPosition.valueOf(cpt.progressBarTextBase())) {
            case CENTER: {
                y -= cpt.progressBarTextSize() / 16.0 * getTextHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= cpt.progressBarTextSize() / 16.0 * getTextHeight();
            }
            break;
        }
        return (int) y;
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

    private static int getTextWidth() {
        return AbstractGuiUtils.getStringWidth(Minecraft.getInstance().font, getText());
    }

    private static int getTextHeight() {
        return AbstractGuiUtils.getStringHeight(Minecraft.getInstance().font, getText());
    }

    private static Color getTextColor() {
        return Color.parse(ClientConfig.get().progressBar().text().progressBarTextColor(), Color.white());
    }
}
