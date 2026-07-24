package xin.vanilla.aotake.internal.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import xin.vanilla.banira.api.client.BaniraInput;
import xin.vanilla.banira.api.client.input.BaniraKeyCodes;
import xin.vanilla.banira.common.data.KeyValue;

import java.nio.DoubleBuffer;

/**
 * 垃圾箱手绘侧栏的鼠标状态机，仅在同一按钮内完成按下和释放时触发一次。
 */
public final class DustbinMouseInput {
    private double mouseX;
    private double mouseY;
    private double leftDownX = -1;
    private double leftDownY = -1;
    private boolean leftDown;
    private boolean leftReleased;

    public void poll(double mouseX, double mouseY) {
        update(mouseX, mouseY, BaniraInput.isMouseDown(BaniraKeyCodes.MOUSE_LEFT));
    }

    void update(double mouseX, double mouseY, boolean pressed) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        leftReleased = leftDown && !pressed;
        if (pressed && !leftDown) {
            leftDownX = mouseX;
            leftDownY = mouseY;
        } else if (!pressed && !leftReleased) {
            leftDownX = -1;
            leftDownY = -1;
        }
        leftDown = pressed;
    }

    public boolean isPressingLeftEx() {
        return leftDown;
    }

    public boolean isHoverInRect(double x, double y, double width, double height) {
        return contains(mouseX, mouseY, x, y, width, height);
    }

    public boolean isLeftPressedInRect(double x, double y, double width, double height) {
        return leftReleased
                && contains(leftDownX, leftDownY, x, y, width, height)
                && isHoverInRect(x, y, width, height);
    }

    public static KeyValue<Double, Double> rawCursorPosition() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(windowHandle(), x, y);
            return new KeyValue<>(x.get(0), y.get(0));
        }
    }

    public static void setRawCursorPosition(double x, double y) {
        GLFW.glfwSetCursorPos(windowHandle(), x, y);
    }

    private static long windowHandle() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    private static boolean contains(double pointX, double pointY,
                                    double x, double y, double width, double height) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }
}
