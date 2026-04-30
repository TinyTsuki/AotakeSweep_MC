package xin.vanilla.aotake.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.banira.common.data.KeyValue;

/**
 * 客户端 GUI 中与 GLFW 鼠标状态配合的轻量辅助类。
 */
public final class MouseHelper {
    private int mouseX;
    private int mouseY;
    private boolean wasLeftDown;
    private boolean leftPressedThisFrame;

    public void tick(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        this.leftPressedThisFrame = down && !wasLeftDown;
        this.wasLeftDown = down;
    }

    public boolean isHoverInRect(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public boolean isLeftPressing() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    public boolean isLeftPressedInRect(int x, int y, int w, int h) {
        return leftPressedThisFrame && isHoverInRect(x, y, w, h);
    }

    public static KeyValue<Double, Double> getRawCursorPos() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        double[] xd = new double[1];
        double[] yd = new double[1];
        GLFW.glfwGetCursorPos(window, xd, yd);
        return new KeyValue<>(xd[0], yd[0]);
    }

    public static void setMouseRawPos(KeyValue<Double, Double> pos) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, pos.key(), pos.val());
    }
}
