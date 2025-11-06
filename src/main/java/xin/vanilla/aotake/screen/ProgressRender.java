package xin.vanilla.aotake.screen;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.data.Color;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.enums.EnumRotationCenter;
import xin.vanilla.aotake.screen.component.Text;
import xin.vanilla.aotake.util.AbstractGuiUtils;
import xin.vanilla.aotake.util.DateUtils;
import xin.vanilla.aotake.util.StringUtils;
import xin.vanilla.aotake.util.TextureUtils;

import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;

public class ProgressRender {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 是否显示进度条
     */
    @Getter
    @Setter
    public static boolean showProgress = false;

    public static BooleanSupplier experienceSupplier = () -> {
        boolean survival = Minecraft.getInstance().gameMode != null && Minecraft.getInstance().gameMode.getPlayerMode().isSurvival();
        boolean result = true;
        try {
            boolean hold = showProgress && Minecraft.getInstance().screen == null;
            List<? extends String> displayList = hold ? ClientConfig.PROGRESS_BAR_DISPLAY_HOLD.get() : ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get();
            if (displayList.contains(EnumProgressBarType.POLE.name()) && ClientConfig.HIDE_EXPERIENCE_BAR_POLE.get()) {
                result = false;
            } else if (displayList.contains(EnumProgressBarType.TEXT.name()) && ClientConfig.HIDE_EXPERIENCE_BAR_TEXT.get()) {
                result = false;
            } else if (displayList.contains(EnumProgressBarType.LEAF.name()) && ClientConfig.HIDE_EXPERIENCE_BAR_LEAF.get()) {
                result = false;
            }
        } catch (Throwable ignored) {
        }
        return survival && result;
    };

    public static void renderProgress(GuiGraphics graphics, boolean post) {
        // 避免重复渲染
        if (post
                && ((ClientConfig.HIDE_EXPERIENCE_BAR_POLE.get() && ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get().contains(EnumProgressBarType.POLE.name()))
                || (ClientConfig.HIDE_EXPERIENCE_BAR_TEXT.get() && ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get().contains(EnumProgressBarType.TEXT.name()))
                || (ClientConfig.HIDE_EXPERIENCE_BAR_LEAF.get() && ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get().contains(EnumProgressBarType.LEAF.name())))
        ) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;
        boolean hold = showProgress && mc.screen == null;
        List<? extends String> displayList = hold ? ClientConfig.PROGRESS_BAR_DISPLAY_HOLD.get() : ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get();

        double scale = ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0;

        if (displayList.contains(EnumProgressBarType.POLE.name())) {
            int width = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
            int height = ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
            int drawX = getPoleX();
            int drawY = getPoleY();

            AbstractGuiUtils.TransformArgs transformArgs = new AbstractGuiUtils.TransformArgs(graphics);
            transformArgs.setAngle(ClientConfig.PROGRESS_BAR_POLE_ANGLE.get())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(drawX)
                    .setY(drawY)
                    .setWidth(width)
                    .setHeight(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "pole.png");
                AbstractGuiUtils.blitBlend(graphics, texture, (int) arg.getX(), (int) arg.getY(), 0, 0, (int) arg.getWidth(), (int) arg.getHeight(), (int) arg.getWidth(), (int) arg.getHeight());
            });
        }

        if (displayList.contains(EnumProgressBarType.TEXT.name())) {
            Text time = Text.literal(getText())
                    .setGraphics(graphics)
                    .setColor(getTextColor())
                    .setShadow(true)
                    .setFont(Minecraft.getInstance().font);
            AbstractGuiUtils.TransformArgs textTransformArgs = new AbstractGuiUtils.TransformArgs(graphics);
            textTransformArgs.setScale(scale)
                    .setAngle(ClientConfig.PROGRESS_BAR_TEXT_ANGLE.get())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(getTextX())
                    .setY(getTextY())
                    .setWidth(getTextWidth())
                    .setHeight(getTextHeight());
            AbstractGuiUtils.renderByTransform(textTransformArgs, (arg) -> AbstractGuiUtils.drawString(time
                    , arg.getX()
                    , arg.getY()
            ));
        }

        if (displayList.contains(EnumProgressBarType.LEAF.name())) {
            int poleW = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();

            int width = ClientConfig.PROGRESS_BAR_LEAF_WIDTH.get();
            int height = ClientConfig.PROGRESS_BAR_LEAF_HEIGHT.get();
            int rangeWidth = poleW - width;
            int startX = getLeafX();

            int drawX = (int) (startX + rangeWidth * getProgress());
            int drawY = getLeafY();

            AbstractGuiUtils.TransformArgs transformArgs = new AbstractGuiUtils.TransformArgs(graphics);
            transformArgs.setAngle(ClientConfig.PROGRESS_BAR_LEAF_ANGLE.get())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(drawX)
                    .setY(drawY)
                    .setWidth(width)
                    .setHeight(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "leaf.png");
                AbstractGuiUtils.blitBlend(graphics, texture, (int) arg.getX(), (int) arg.getY(), 0, 0, (int) arg.getWidth(), (int) arg.getHeight(), (int) arg.getWidth(), (int) arg.getHeight());
            });
        }
    }


    private static int getLeafX() {
        int baseX = getPoleX();
        int width = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
        double x;
        String xString = ClientConfig.PROGRESS_BAR_LEAF_POSITION.get().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_LEAF_SCREEN_QUADRANT.get();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_LEAF_BASE.get())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.PROGRESS_BAR_LEAF_WIDTH.get() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.PROGRESS_BAR_LEAF_WIDTH.get();
            }
            break;
        }
        return (int) x;
    }

    private static int getLeafY() {
        int baseY = getPoleY();
        int height = ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
        double y;
        String yString = ClientConfig.PROGRESS_BAR_LEAF_POSITION.get().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_LEAF_SCREEN_QUADRANT.get();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_LEAF_BASE.get())) {
            case CENTER: {
                y -= ClientConfig.PROGRESS_BAR_LEAF_HEIGHT.get() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.PROGRESS_BAR_LEAF_HEIGHT.get();
            }
            break;
        }
        return (int) y;
    }

    private static int getPoleX() {
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        double x;
        String xString = ClientConfig.PROGRESS_BAR_POLE_POSITION.get().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_POLE_SCREEN_QUADRANT.get();
        if (quadrant == 2 || quadrant == 3) {
            x = width - x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_POLE_BASE.get())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.PROGRESS_BAR_POLE_WIDTH.get() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
            }
            break;
        }
        return (int) x;
    }

    private static int getPoleY() {
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double y;
        String yString = ClientConfig.PROGRESS_BAR_POLE_POSITION.get().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_POLE_SCREEN_QUADRANT.get();
        if (quadrant == 1 || quadrant == 2) {
            y = height - y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_POLE_BASE.get())) {
            case CENTER: {
                y -= ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
            }
            break;
        }
        return (int) y;
    }

    private static int getTextX() {
        int baseX = getPoleX();
        int width = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
        double x;
        String xString = ClientConfig.PROGRESS_BAR_TEXT_POSITION.get().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_TEXT_SCREEN_QUADRANT.get();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_TEXT_BASE.get())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getTextY() {
        int baseY = getPoleY();
        int height = ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
        double y;
        String yString = ClientConfig.PROGRESS_BAR_TEXT_POSITION.get().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_TEXT_SCREEN_QUADRANT.get();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_TEXT_BASE.get())) {
            case CENTER: {
                y -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextHeight();
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
        return Color.parse(ClientConfig.PROGRESS_BAR_TEXT_COLOR.get(), Color.white());
    }
}
