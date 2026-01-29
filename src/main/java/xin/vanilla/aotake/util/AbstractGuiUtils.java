package xin.vanilla.aotake.util;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import xin.vanilla.aotake.enums.EnumEllipsisPosition;
import xin.vanilla.aotake.enums.EnumRotationCenter;
import xin.vanilla.aotake.screen.component.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * AbstractGui工具类
 */
@OnlyIn(Dist.CLIENT)
public class AbstractGuiUtils {

    // region 设置深度

    @Getter
    @Accessors(fluent = true)
    public enum EDepth {
        BACKGROUND(1),
        FOREGROUND(250),
        OVERLAY(500),
        TOOLTIP(750),
        POPUP_TIPS(900),
        MOUSE(1000);

        private final int depth;

        EDepth(int depth) {
            this.depth = depth;
        }
    }

    public static void renderByDepth(MatrixStack stack, EDepth depth, Consumer<MatrixStack> drawFunc) {
        if (depth != null) {
            renderByDepth(stack, depth.depth(), drawFunc);
        } else {
            drawFunc.accept(stack);
        }
    }

    public static void renderByDepth(MatrixStack stack, int depth, Consumer<MatrixStack> drawFunc) {
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        try {
            stack.pushPose();
            stack.translate(0, 0, depth);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            drawFunc.accept(stack);
        } finally {
            stack.popPose();

            if (!depthTest) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
            RenderSystem.depthFunc(depthFunc);
        }
    }

    // endregion 设置深度

    // region 绘制纹理

    public static void bindTexture(ResourceLocation resourceLocation) {
        Minecraft.getInstance().getTextureManager().bind(resourceLocation);
    }

    public static void blitBlend(MatrixStack matrixStack, int x0, int y0, double u0, double v0, int destWidth, int destHeight, int textureWidth, int textureHeight) {
        blitByBlend(() ->
                AbstractGui.blit(matrixStack, x0, y0, (float) u0, (float) v0, destWidth, destHeight, textureWidth, textureHeight)
        );
    }

    /**
     * 启用混合模式来绘制纹理
     */
    public static void blitByBlend(Runnable drawFunc) {
        // 启用混合模式来正确处理透明度
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawFunc.run();
        RenderSystem.disableBlend();
    }

    @Data
    @Accessors(chain = true)
    public static class TransformArgs {
        private MatrixStack stack;
        private double x;
        private double y;
        private double width;
        private double height;
        /**
         * 透明度
         */
        private double alpha = 0xFF;
        /**
         * 缩放比例
         */
        private double scale = 1.0;
        /**
         * 水平翻转
         */
        private boolean flipHorizontal;
        /**
         * 垂直翻转
         */
        private boolean flipVertical;
        /**
         * 旋转角度
         */
        private double angle = 0;
        /**
         * 旋转中心
         */
        private EnumRotationCenter center = EnumRotationCenter.CENTER;
        /**
         * 混合模式
         */
        private boolean blend = false;

        public TransformArgs(MatrixStack stack) {
            this.stack = stack;
        }

        public double getWidthScaled() {
            return this.width * this.scale;
        }

        public double getHeightScaled() {
            return this.height * this.scale;
        }

    }

    @Data
    @Accessors(chain = true)
    public static class TransformDrawArgs {
        private final MatrixStack stack;
        private double x;
        private double y;
        private double width;
        private double height;
    }

    /**
     * 变换后绘制
     *
     * @param args 变换参数
     */
    public static void renderByTransform(TransformArgs args, Consumer<TransformDrawArgs> drawFunc) {

        // 保存当前矩阵状态
        args.getStack().pushPose();

        // 计算目标点
        double tranX = 0, tranY = 0;
        double tranW = 0, tranH = 0;
        // 旋转角度为0不需要进行变换
        if (args.getAngle() % 360 == 0) args.setCenter(EnumRotationCenter.TOP_LEFT);
        switch (args.getCenter()) {
            case CENTER:
                tranW = args.getWidthScaled() / 2.0;
                tranH = args.getHeightScaled() / 2.0;
                tranX = args.getX() + tranW;
                tranY = args.getY() + tranH;
                break;
            case TOP_LEFT:
                tranX = args.getX();
                tranY = args.getY();
                break;
            case TOP_RIGHT:
                tranW = args.getWidthScaled();
                tranX = args.getX() + tranW;
                tranY = args.getY();
                break;
            case TOP_CENTER:
                tranW = args.getWidthScaled() / 2.0;
                tranX = args.getX() + tranW;
                tranY = args.getY();
                break;
            case BOTTOM_LEFT:
                tranH = args.getHeightScaled();
                tranX = args.getX();
                tranY = args.getY() + tranH;
                break;
            case BOTTOM_RIGHT:
                tranW = args.getWidthScaled();
                tranH = args.getHeightScaled();
                tranX = args.getX() + tranW;
                tranY = args.getY() + tranH;
                break;
            case BOTTOM_CENTER:
                tranW = args.getWidthScaled() / 2.0;
                tranH = args.getHeightScaled();
                tranX = args.getX() + tranW;
                tranY = args.getY() + tranH;
                break;
        }
        // 移至目标点
        args.getStack().translate(tranX, tranY, 0);

        // 缩放
        args.getStack().scale((float) args.getScale(), (float) args.getScale(), 1);

        // 旋转
        if (args.getAngle() % 360 != 0) {
            args.getStack().mulPose(Vector3f.ZP.rotationDegrees((float) args.getAngle()));
        }

        // 翻转
        if (args.isFlipHorizontal()) {
            args.getStack().mulPose(Vector3f.YP.rotationDegrees(180));
        }
        if (args.isFlipVertical()) {
            args.getStack().mulPose(Vector3f.XP.rotationDegrees(180));
        }

        // 返回原点
        args.getStack().translate(-tranW, -tranH, 0);

        // 关闭背面剔除
        RenderSystem.disableCull();
        // 绘制方法
        TransformDrawArgs drawArgs = new TransformDrawArgs(args.getStack());
        drawArgs.setX(0).setY(0).setWidth(args.getWidth()).setHeight(args.getHeight());

        // 启用混合模式
        if (args.isBlend() || args.getAlpha() < 0xFF) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // 设置透明度
            if (args.getAlpha() < 0xFF)
                RenderSystem.color4f(1, 1, 1, (float) args.getAlpha() / 0xFF);
        }

        drawFunc.accept(drawArgs);

        // 关闭混合模式
        if (args.isBlend() || args.getAlpha() < 0xFF) {
            // 还原透明度
            if (args.getAlpha() < 0xFF)
                RenderSystem.color4f(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }

        // 恢复背面剔除
        RenderSystem.enableCull();

        // 恢复矩阵状态
        args.getStack().popPose();
    }

    // endregion 绘制纹理

    // region 绘制文字

    public static void drawString(Text text, double x, double y) {
        AbstractGuiUtils.drawLimitedText(text, x, y, 0, 0, null);
    }

    /**
     * 获取多行文本的高度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextHeight(Text text) {
        return AbstractGuiUtils.multilineTextHeight(text.getFont(), text.getContent());
    }

    /**
     * 获取多行文本的高度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextHeight(FontRenderer font, String text) {
        return StringUtils.replaceLine(text).split("\n").length * font.lineHeight;
    }

    public static int getStringWidth(FontRenderer font, String... texts) {
        return AbstractGuiUtils.getStringWidth(font, Arrays.asList(texts));
    }

    public static int getStringWidth(FontRenderer font, Collection<String> texts) {
        int width = 0;
        for (String s : texts) {
            width = Math.max(width, font.width(s));
        }
        return width;
    }

    public static int getStringHeight(FontRenderer font, String... texts) {
        return AbstractGuiUtils.getStringHeight(font, Arrays.asList(texts));
    }

    public static int getStringHeight(FontRenderer font, Collection<String> texts) {
        return AbstractGuiUtils.multilineTextHeight(font, String.join("\n", texts));
    }

    /**
     * 获取多行文本的宽度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextWidth(Text text) {
        return AbstractGuiUtils.multilineTextWidth(text.getFont(), text.getContent());
    }

    /**
     * 获取多行文本的宽度，以\n为换行符
     *
     * @param text 要绘制的文本
     */
    public static int multilineTextWidth(FontRenderer font, String text) {
        int width = 0;
        if (StringUtils.isNotNullOrEmpty(text)) {
            for (String s : StringUtils.replaceLine(text).split("\n")) {
                width = Math.max(width, font.width(s));
            }
        }
        return width;
    }

    /**
     * 绘制限制长度的文本，超出部分以省略号表示，可选择省略号的位置
     *
     * @param text     要绘制的文本
     * @param x        绘制的X坐标
     * @param y        绘制的Y坐标
     * @param maxWidth 文本显示的最大宽度
     * @param maxLine  文本显示的最大行数
     * @param position 省略号位置（开头、中间、结尾）
     */
    public static void drawLimitedText(Text text, double x, double y, int maxWidth, int maxLine, EnumEllipsisPosition position) {
        if (StringUtils.isNotNullOrEmpty(text.getContent())) {
            String ellipsis = "...";
            FontRenderer font = text.getFont();
            int ellipsisWidth = font.width(ellipsis);

            // 拆分文本行
            String[] lines = StringUtils.replaceLine(text.getContent()).split("\n");

            // 如果 maxLine <= 1 或 maxLine 大于等于行数，则正常显示所有行
            if (maxLine <= 0 || maxLine >= lines.length) {
                maxLine = lines.length;
                position = null; // 不需要省略号
            }

            List<String> outputLines = new ArrayList<>();
            if (position != null && maxLine > 1) {
                switch (position) {
                    case START:
                        // 显示最后 maxLine 行，开头加省略号
                        outputLines.add(ellipsis);
                        outputLines.addAll(Arrays.asList(lines).subList(lines.length - maxLine + 1, lines.length));
                        break;
                    case MIDDLE:
                        // 显示前后各一部分，中间加省略号
                        int midStart = maxLine / 2;
                        int midEnd = lines.length - (maxLine - midStart) + 1;
                        outputLines.addAll(Arrays.asList(lines).subList(0, midStart));
                        outputLines.add(ellipsis);
                        outputLines.addAll(Arrays.asList(lines).subList(midEnd, lines.length));
                        break;
                    case END:
                    default:
                        // 显示前 maxLine 行，结尾加省略号
                        outputLines.addAll(Arrays.asList(lines).subList(0, maxLine - 1));
                        outputLines.add(ellipsis);
                        break;
                }
            } else {
                if (maxLine == 1) {
                    outputLines.add(lines[0]);
                } else {
                    // 正常显示所有行
                    outputLines.addAll(Arrays.asList(lines));
                }
            }

            // 绘制文本
            int index = 0;
            int maxLineWidth = AbstractGuiUtils.multilineTextWidth(text);
            maxLineWidth = maxLine > 0 ? Math.min(maxLineWidth, maxWidth) : maxLineWidth;
            for (String line : outputLines) {
                // 如果宽度超出 maxWidth，进行截断并加省略号
                if (maxWidth > 0 && font.width(line) > maxWidth) {
                    if (position == EnumEllipsisPosition.START) {
                        // 截断前部
                        while (font.width(ellipsis + line) > maxWidth && line.length() > 1) {
                            line = line.substring(1);
                        }
                        line = ellipsis + line;
                    } else if (position == EnumEllipsisPosition.END) {
                        // 截断后部
                        while (font.width(line + ellipsis) > maxWidth && line.length() > 1) {
                            line = line.substring(0, line.length() - 1);
                        }
                        line = line + ellipsis;
                    } else {
                        // 截断两侧（默认处理）
                        int halfWidth = (maxWidth - ellipsisWidth) / 2;
                        String start = line, end = line;
                        while (font.width(start) > halfWidth && start.length() > 1) {
                            start = start.substring(0, start.length() - 1);
                        }
                        while (font.width(end) > halfWidth && end.length() > 1) {
                            end = end.substring(1);
                        }
                        line = start + ellipsis + end;
                    }
                }

                // 计算水平偏移
                float xOffset;
                switch (text.getAlign()) {
                    case CENTER:
                        xOffset = (maxLineWidth - font.width(line)) / 2.0f;
                        break;
                    case RIGHT:
                        xOffset = maxLineWidth - font.width(line);
                        break;
                    default:
                        xOffset = 0;
                        break;
                }

                // 绘制每行文本
                MatrixStack matrixStack = text.getMatrixStack();
                if (!text.isBgColorEmpty()) {
                    AbstractGuiUtils.fill(matrixStack, (int) (x + xOffset), (int) (y + index * font.lineHeight), font.width(line), font.lineHeight, text.getBgColorArgb());
                }
                if (text.isShadow()) {
                    font.drawShadow(matrixStack, text.copyWithoutChildren().setText(line).toComponent().toTextComponent(AotakeUtils.getClientLanguage()), (float) x + xOffset, (float) y + index * font.lineHeight, text.getColorArgb());
                } else {
                    font.draw(matrixStack, text.copyWithoutChildren().setText(line).toComponent().toTextComponent(AotakeUtils.getClientLanguage()), (float) x + xOffset, (float) y + index * font.lineHeight, text.getColorArgb());
                }

                index++;
            }
        }
    }

    // endregion 绘制文字

    //  region 绘制形状

    /**
     * 绘制一个“像素”矩形
     *
     * @param x    像素的 X 坐标
     * @param y    像素的 Y 坐标
     * @param argb 像素的颜色
     */
    public static void drawPixel(MatrixStack matrixStack, int x, int y, int argb) {
        AbstractGui.fill(matrixStack, x, y, x + 1, y + 1, argb);
    }

    /**
     * 绘制一个矩形
     */
    public static void fill(MatrixStack matrixStack, int x, int y, int width, int height, int argb) {
        AbstractGuiUtils.fill(matrixStack, x, y, width, height, argb, 0);
    }

    /**
     * 绘制一个圆角矩形
     *
     * @param x      矩形的左上角X坐标
     * @param y      矩形的左上角Y坐标
     * @param width  矩形的宽度
     * @param height 矩形的高度
     * @param argb   矩形的颜色
     * @param radius 圆角半径(0-10)
     */
    public static void fill(MatrixStack matrixStack, int x, int y, int width, int height, int argb, int radius) {
        // 如果半径为0，则直接绘制普通矩形
        if (radius <= 0) {
            AbstractGui.fill(matrixStack, x, y, x + width, y + height, argb);
            return;
        }

        // 限制半径最大值为10
        radius = Math.min(radius, 10);

        // 1. 绘制中间的矩形部分（去掉圆角占用的区域）
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + radius + 1, width - 2 * (radius + 1), height - 2 * (radius + 1), argb);

        // 2. 绘制四条边（去掉圆角占用的部分）
        // 上边
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y, width - 2 * radius - 2, radius, argb);
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + radius, width - 2 * (radius + 1), 1, argb);
        // 下边
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + height - radius, width - 2 * radius - 2, radius, argb);
        AbstractGuiUtils.fill(matrixStack, x + radius + 1, y + height - radius - 1, width - 2 * (radius + 1), 1, argb);
        // 左边
        AbstractGuiUtils.fill(matrixStack, x, y + radius + 1, radius, height - 2 * radius - 2, argb);
        AbstractGuiUtils.fill(matrixStack, x + radius, y + radius + 1, 1, height - 2 * (radius + 1), argb);
        // 右边
        AbstractGuiUtils.fill(matrixStack, x + width - radius, y + radius + 1, radius, height - 2 * radius - 2, argb);
        AbstractGuiUtils.fill(matrixStack, x + width - radius - 1, y + radius + 1, 1, height - 2 * (radius + 1), argb);

        // 3. 绘制四个圆角
        // 左上角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + radius, y + radius, radius, argb, 1);
        // 右上角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + width - radius - 1, y + radius, radius, argb, 2);
        // 左下角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + radius, y + height - radius - 1, radius, argb, 3);
        // 右下角
        AbstractGuiUtils.drawCircleQuadrant(matrixStack, x + width - radius - 1, y + height - radius - 1, radius, argb, 4);
    }

    /**
     * 绘制一个圆的四分之一部分（圆角辅助函数）
     *
     * @param centerX  圆角中心点X坐标
     * @param centerY  圆角中心点Y坐标
     * @param radius   圆角半径
     * @param argb     圆角颜色
     * @param quadrant 指定绘制的象限（1=左上，2=右上，3=左下，4=右下）
     */
    private static void drawCircleQuadrant(MatrixStack matrixStack, int centerX, int centerY, int radius, int argb, int quadrant) {
        for (int dx = 0; dx <= radius; dx++) {
            for (int dy = 0; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    switch (quadrant) {
                        case 1: // 左上角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX - dx, centerY - dy, argb);
                            break;
                        case 2: // 右上角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX + dx, centerY - dy, argb);
                            break;
                        case 3: // 左下角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX - dx, centerY + dy, argb);
                            break;
                        case 4: // 右下角
                            AbstractGuiUtils.drawPixel(matrixStack, centerX + dx, centerY + dy, argb);
                            break;
                    }
                }
            }
        }
    }

    //  endregion 绘制形状

    //  region 绘制弹出层提示

    /**
     * 绘制弹出层消息
     *
     * @param text         消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, 0xAA000000);
    }

    /**
     * 绘制弹出层消息
     *
     * @param text         消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param bgArgb       背景颜色
     */
    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, 2, bgArgb);
    }

    /**
     * 绘制弹出层消息
     *
     * @param text         消息内容
     * @param x            鼠标坐标X
     * @param y            鼠标坐标y
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param margin       弹出层的外边距(外层背景与屏幕边缘)
     * @param bgArgb       背景颜色
     */
    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int margin, int bgArgb) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, margin, margin, bgArgb);
    }

    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int margin, int padding, int bgAgb) {
        AbstractGuiUtils.drawPopupMessage(text, x, y, screenWidth, screenHeight, margin, padding, bgAgb, true);
    }

    public static void drawPopupMessage(Text text, double x, double y, int screenWidth, int screenHeight, int margin, int padding, int bgArgb, boolean inScreen) {
        // 计算消息宽度和高度, 并添加一些边距
        int msgWidth = AbstractGuiUtils.multilineTextWidth(text) + padding;
        int msgHeight = AbstractGuiUtils.multilineTextHeight(text) + padding;

        // 计算调整后的坐标
        double adjustedX = x;
        double adjustedY = y;
        if (inScreen) {
            if (msgWidth >= screenWidth) msgWidth = screenWidth - padding * 2;
            if (msgHeight >= screenHeight) msgHeight = screenHeight - padding * 2;

            // 初始化调整后的坐标
            adjustedX = x - msgWidth / 2.0; // 横向居中
            adjustedY = y - msgHeight - 5; // 放置在鼠标上方（默认偏移 5 像素）

            // 检查顶部空间是否充足
            boolean hasTopSpace = adjustedY >= margin;
            // 检查左右空间是否充足
            boolean hasLeftSpace = adjustedX >= margin;
            boolean hasRightSpace = adjustedX + msgWidth <= screenWidth - margin;

            // 如果顶部空间不足，调整到鼠标下方
            if (!hasTopSpace) {
                adjustedY = y + 1 + 5;
            }
            // 如果顶部空间充足
            else {
                // 如果左侧空间不足，靠右
                if (!hasLeftSpace) {
                    adjustedX = margin;
                }
                // 如果右侧空间不足，靠左
                else if (!hasRightSpace) {
                    adjustedX = screenWidth - msgWidth - margin;
                }
            }

            // 如果调整后仍然超出屏幕范围，强制限制在屏幕内
            adjustedX = Math.max(margin, Math.min(adjustedX, screenWidth - msgWidth - margin));
            adjustedY = Math.max(margin, Math.min(adjustedY, screenHeight - msgHeight - margin));
        }

        double finalAdjustedX = adjustedX;
        double finalAdjustedY = adjustedY;
        int finalMsgWidth = msgWidth;
        int finalMsgHeight = msgHeight;
        AbstractGuiUtils.renderByDepth(text.getMatrixStack(), EDepth.POPUP_TIPS, (stack) -> {
            // 在计算完的坐标位置绘制消息框背景
            AbstractGui.fill(text.getMatrixStack(), (int) finalAdjustedX, (int) finalAdjustedY, (int) (finalAdjustedX + finalMsgWidth), (int) (finalAdjustedY + finalMsgHeight), bgArgb);
            // 绘制消息文字
            AbstractGuiUtils.drawLimitedText(text, finalAdjustedX + (float) padding / 2, finalAdjustedY + (float) padding / 2, finalMsgWidth, finalMsgHeight / text.getFont().lineHeight, EnumEllipsisPosition.MIDDLE);
        });
    }

    //  endregion 绘制弹出层提示


    // region 重写方法签名

    public static Button newButton(int x, int y, int width, int height, Component content, Button.IPressable onPress, Component tooltip) {
        Screen screen = Minecraft.getInstance().screen;
        return new Button(x, y, width, height, content.toTextComponent(AotakeUtils.getClientLanguage()), onPress, (button, stack, x1, y1) -> {
            if (screen != null) {
                screen.renderTooltip(stack, tooltip.toTextComponent(AotakeUtils.getClientLanguage()), x1, y1);
            }
        });
    }

    // endregion 重写方法签名
}
