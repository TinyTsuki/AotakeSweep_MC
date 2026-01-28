package xin.vanilla.aotake.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import xin.vanilla.aotake.data.FixedList;
import xin.vanilla.aotake.data.KeyValue;

import java.nio.DoubleBuffer;

public class MouseHelper {
    private final FixedList<Boolean> mouseLeftPressedRecord = new FixedList<>(5);
    private final FixedList<Boolean> mouseRightPressedRecord = new FixedList<>(5);
    private int mouseLeftPressedX = -1;
    private int mouseLeftPressedY = -1;
    private int mouseX;
    private int mouseY;

    private static long getWindowHandle() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    private boolean isMouseLeftPressing() {
        return isMouseLeftPressing(getWindowHandle());
    }

    private boolean isMouseLeftPressing(long windowHandle) {
        return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private boolean isMouseRightPressing() {
        return isMouseRightPressing(getWindowHandle());
    }

    private boolean isMouseRightPressing(long windowHandle) {
        return GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    public void tick(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        boolean mouseLeftPressing = isMouseLeftPressing();
        if (mouseLeftPressing) {
            if (!Boolean.TRUE.equals(mouseLeftPressedRecord.getLast())) {
                mouseLeftPressedX = mouseX;
                mouseLeftPressedY = mouseY;
            }
        }
        mouseLeftPressedRecord.add(mouseLeftPressing);

        boolean mouseRightPressing = isMouseRightPressing();
        mouseRightPressedRecord.add(mouseRightPressing);
    }

    public boolean isLeftPressing() {
        return Boolean.TRUE.equals(mouseLeftPressedRecord.getLast());
    }

    public boolean isLeftPressed() {
        return mouseLeftPressedRecord.size() > 1 && mouseLeftPressedRecord.get(mouseLeftPressedRecord.size() - 2) && !mouseLeftPressedRecord.getLast();
    }

    public boolean isHoverInRect(int x, int y, int width, int height) {
        return x <= this.mouseX && this.mouseX <= x + width && y <= this.mouseY && this.mouseY <= y + height;
    }

    public boolean isLeftHoverInRect(int x, int y, int width, int height) {
        return x <= mouseLeftPressedX && mouseLeftPressedX <= x + width && y <= mouseLeftPressedY && mouseLeftPressedY <= y + height;
    }

    public boolean isLeftPressedInRect(int x, int y, int width, int height) {
        return isLeftPressed() && isHoverInRect(x, y, width, height) && isLeftHoverInRect(x, y, width, height);
    }

    public static KeyValue<Double, Double> getRawCursorPos() {
        long window = getWindowHandle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xb = stack.mallocDouble(1);
            DoubleBuffer yb = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window, xb, yb);
            return new KeyValue<>(xb.get(0), yb.get(0));
        }
    }

    public static void setMouseRawPos(KeyValue<Double, Double> pos) {
        setMouseRawPos(pos.getKey(), pos.getValue());
    }

    public static void setMouseRawPos(double rawX, double rawY) {
        long window = getWindowHandle();
        GLFW.glfwSetCursorPos(window, rawX, rawY);
    }

}
