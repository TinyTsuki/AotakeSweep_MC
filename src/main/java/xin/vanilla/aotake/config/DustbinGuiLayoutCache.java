package xin.vanilla.aotake.config;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DustbinGuiLayoutCache {

    public static int leftPos;
    public static int topPos;
    public static int drawWidth;
    public static int drawHeight;
    public static int srcWidth;
    public static int srcHeight;
    public static boolean valid;

    private DustbinGuiLayoutCache() {
    }

    public static void set(int left, int top, int drawW, int drawH, int srcW, int srcH) {
        leftPos = left;
        topPos = top;
        drawWidth = drawW;
        drawHeight = drawH;
        srcWidth = srcW;
        srcHeight = srcH;
        valid = true;
    }

    public static void invalidate() {
        valid = false;
    }
}
