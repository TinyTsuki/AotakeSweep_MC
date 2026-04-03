package xin.vanilla.aotake.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraftforge.fml.config.ModConfig;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.access.ClientConfigAccess;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.enums.EnumProgressBarTextAlignH;
import xin.vanilla.aotake.enums.EnumProgressBarTextAlignV;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.banira.common.config.ConfigData;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ForgeConfigAdapter;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.common.config.annotation.ConfigEntry;
import xin.vanilla.banira.common.enums.EnumPosition;

import java.util.Arrays;
import java.util.List;

/**
 * 客户端配置（Forge CLIENT），由 Banira {@link ForgeConfigAdapter} 构建并在配置编辑器中编辑。
 */
@Config(name = AotakeSweep.MODID + "-client", type = ModConfig.Type.CLIENT)
public class ClientConfig implements ConfigData {

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "进度条与竹叶/竹竿/文字显示", en_us = "Progress bar (bamboo leaf / pole / text)")
    private ProgressBarCategory progressBar = new ProgressBarCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "垃圾箱界面", en_us = "Dustbin screen")
    private DustbinCategory dustbin = new DustbinCategory();

    public ClientConfig() {
    }

    public static RootView get() {
        return ClientConfigAccess.root(ForgeConfigAdapter.getHolder(ClientConfig.class));
    }

    public interface RootView {
        ProgressBarView progressBar();

        DustbinView dustbin();

        ConfigHolder holder();
    }

    public interface ProgressBarView {
        List<EnumProgressBarType> progressBarDisplayNormal();

        ProgressBarView progressBarDisplayNormal(List<EnumProgressBarType> value);

        List<EnumProgressBarType> progressBarDisplayHold();

        ProgressBarView progressBarDisplayHold(List<EnumProgressBarType> value);

        boolean progressBarKeyApplyMode();

        ProgressBarView progressBarKeyApplyMode(boolean value);

        ProgressBarLeafView leaf();

        ProgressBarPoleView pole();

        ProgressBarTextView text();
    }

    public interface ProgressBarLeafView {
        boolean hideExperienceBarLeaf();

        ProgressBarLeafView hideExperienceBarLeaf(boolean value);

        int progressBarLeafScreenQuadrant();

        ProgressBarLeafView progressBarLeafScreenQuadrant(int value);

        String progressBarLeafPosition();

        ProgressBarLeafView progressBarLeafPosition(String value);

        EnumPosition progressBarLeafBase();

        ProgressBarLeafView progressBarLeafBase(EnumPosition value);

        double progressBarLeafAngle();

        ProgressBarLeafView progressBarLeafAngle(double value);

        int progressBarLeafHeight();

        ProgressBarLeafView progressBarLeafHeight(int value);

        int progressBarLeafWidth();

        ProgressBarLeafView progressBarLeafWidth(int value);
    }

    public interface ProgressBarPoleView {
        boolean hideExperienceBarPole();

        ProgressBarPoleView hideExperienceBarPole(boolean value);

        int progressBarPoleScreenQuadrant();

        ProgressBarPoleView progressBarPoleScreenQuadrant(int value);

        String progressBarPolePosition();

        ProgressBarPoleView progressBarPolePosition(String value);

        EnumPosition progressBarPoleBase();

        ProgressBarPoleView progressBarPoleBase(EnumPosition value);

        double progressBarPoleAngle();

        ProgressBarPoleView progressBarPoleAngle(double value);

        int progressBarPoleHeight();

        ProgressBarPoleView progressBarPoleHeight(int value);

        int progressBarPoleWidth();

        ProgressBarPoleView progressBarPoleWidth(int value);
    }

    public interface ProgressBarTextView {
        boolean hideExperienceBarText();

        ProgressBarTextView hideExperienceBarText(boolean value);

        int progressBarTextScreenQuadrant();

        ProgressBarTextView progressBarTextScreenQuadrant(int value);

        String progressBarTextPosition();

        ProgressBarTextView progressBarTextPosition(String value);

        EnumProgressBarTextAlignH progressBarTextAlignH();

        ProgressBarTextView progressBarTextAlignH(EnumProgressBarTextAlignH value);

        EnumProgressBarTextAlignV progressBarTextAlignV();

        ProgressBarTextView progressBarTextAlignV(EnumProgressBarTextAlignV value);

        double progressBarTextAngle();

        ProgressBarTextView progressBarTextAngle(double value);

        int progressBarTextSize();

        ProgressBarTextView progressBarTextSize(int value);

        String progressBarTextColor();

        ProgressBarTextView progressBarTextColor(String value);
    }

    public interface DustbinView {
        EnumDustbinClientUiStyle dustbinUiStyle();

        DustbinView dustbinUiStyle(EnumDustbinClientUiStyle value);
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ProgressBarCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "正常状态下进度条显示方式（LEAF/POLE/TEXT）",
                en_us = "Progress bar modes when idle (LEAF / POLE / TEXT)")
        private List<EnumProgressBarType> progressBarDisplayNormal = Arrays.asList(EnumProgressBarType.LEAF);

        @ConfigEntry.Gui.Tooltip(zh_cn = "按住按键时进度条显示方式", en_us = "Progress bar modes while holding the key")
        private List<EnumProgressBarType> progressBarDisplayHold = Arrays.asList(EnumProgressBarType.LEAF, EnumProgressBarType.POLE, EnumProgressBarType.TEXT);

        @ConfigEntry.Gui.Tooltip(zh_cn = "进度条快捷键：false 按住，true 切换", en_us = "Key mode: false = hold, true = toggle")
        private boolean progressBarKeyApplyMode = false;

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "竹叶", en_us = "Bamboo leaf")
        private ProgressBarLeafCategory leaf = new ProgressBarLeafCategory();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "竹竿", en_us = "Bamboo pole")
        private ProgressBarPoleCategory pole = new ProgressBarPoleCategory();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "文字", en_us = "Text overlay")
        private ProgressBarTextCategory text = new ProgressBarTextCategory();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ProgressBarLeafCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "绘制竹叶时是否隐藏经验条。", en_us = "Hide the XP bar while drawing the bamboo leaf.")
        private boolean hideExperienceBarLeaf = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "屏幕坐标象限：1 左下(↑→)；2 右下(↑←)；3 右上(↓←)；4 左上(↓→)。",
                en_us = "Screen quadrant: 1 bottom-left; 2 bottom-right; 3 top-right; 4 top-left.")
        @ConfigEntry.BoundedDiscrete(min = 1, max = 4)
        private int progressBarLeafScreenQuadrant = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹叶相对竹竿的偏移。x,y 为绝对坐标；x%,y% 为相对坐标（象限为 4 时，0,0 表示竹竿左上角）。",
                en_us = "Leaf position vs pole: x,y absolute; x%,y% relative (quadrant 4: 0,0 = pole top-left).")
        private String progressBarLeafPosition = "0,4";

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹叶纹理锚点：TOP_LEFT/TOP_RIGHT/TOP_CENTER/BOTTOM_LEFT/BOTTOM_RIGHT/BOTTOM_CENTER/CENTER（与 EnumPosition 一致）。",
                en_us = "Texture anchor for the leaf (EnumPosition names, e.g. TOP_LEFT, CENTER).")
        private EnumPosition progressBarLeafBase = EnumPosition.TOP_LEFT;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹叶旋转角度（0–360）。", en_us = "Leaf rotation in degrees (0–360).")
        @ConfigEntry.BoundedDouble(max = 360.0)
        private double progressBarLeafAngle = 0.0;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹叶显示高度（像素）。", en_us = "Leaf draw height in pixels.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int progressBarLeafHeight = 10;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹叶显示宽度（像素）。", en_us = "Leaf draw width in pixels.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int progressBarLeafWidth = 9;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ProgressBarPoleCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "绘制竹竿时是否隐藏经验条。", en_us = "Hide the XP bar while drawing the bamboo pole.")
        private boolean hideExperienceBarPole = true;

        @ConfigEntry.Gui.Tooltip(zh_cn = "屏幕坐标象限：1 左下；2 右下；3 右上；4 左上（与竹叶说明相同）。",
                en_us = "Screen quadrant for pole placement (same meaning as leaf quadrant).")
        @ConfigEntry.BoundedDiscrete(min = 1, max = 4)
        private int progressBarPoleScreenQuadrant = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹竿在屏幕上的位置。x,y 绝对坐标；x%,y% 相对坐标（象限 4 时 0%,0% 为屏幕左上角）。",
                en_us = "Pole position: x,y absolute; x%,y% relative (quadrant 4: 0%,0% = screen top-left).")
        private String progressBarPolePosition = "50%,29";

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹竿纹理锚点（EnumPosition）。", en_us = "Texture anchor for the pole (EnumPosition).")
        private EnumPosition progressBarPoleBase = EnumPosition.TOP_CENTER;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹竿旋转角度（0–360）。", en_us = "Pole rotation in degrees (0–360).")
        @ConfigEntry.BoundedDouble(min = 0.0, max = 360.0)
        private double progressBarPoleAngle = 0.0;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹竿显示高度（像素）。", en_us = "Pole draw height in pixels.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int progressBarPoleHeight = 5;

        @ConfigEntry.Gui.Tooltip(zh_cn = "竹竿显示宽度（像素）。", en_us = "Pole draw width in pixels.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int progressBarPoleWidth = 180;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ProgressBarTextCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "绘制倒计时文字时是否隐藏经验条。", en_us = "Hide the XP bar while drawing countdown text.")
        private boolean hideExperienceBarText = true;

        @ConfigEntry.Gui.Tooltip(zh_cn = "文字相对竹竿定位所用的屏幕象限（1–4）。", en_us = "Screen quadrant for text placement relative to the pole.")
        @ConfigEntry.BoundedDiscrete(min = 1, max = 4)
        private int progressBarTextScreenQuadrant = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "参考点相对竹竿的偏移（x,y 或 x%,y%，象限含义与竹竿一致）。文字再按水平/垂直对齐相对该点对齐。",
                en_us = "Anchor offset from pole (x,y or x%,y%). Text aligns to this point per horizontal/vertical align.")
        private String progressBarTextPosition = "50%,8";

        @ConfigEntry.Gui.Tooltip(zh_cn = "相对参考点的水平对齐：LEFT/CENTER/RIGHT。", en_us = "Horizontal align to anchor: LEFT / CENTER / RIGHT.")
        private EnumProgressBarTextAlignH progressBarTextAlignH = EnumProgressBarTextAlignH.CENTER;

        @ConfigEntry.Gui.Tooltip(zh_cn = "相对参考点的垂直对齐：TOP/CENTER/BOTTOM。", en_us = "Vertical align to anchor: TOP / CENTER / BOTTOM.")
        private EnumProgressBarTextAlignV progressBarTextAlignV = EnumProgressBarTextAlignV.TOP;

        @ConfigEntry.Gui.Tooltip(zh_cn = "文字旋转角度（0–360）。", en_us = "Text rotation in degrees (0–360).")
        @ConfigEntry.BoundedDouble(min = 0.0, max = 360.0)
        private double progressBarTextAngle = 0.0;

        @ConfigEntry.Gui.Tooltip(zh_cn = "文字大小（相对 16px 基准缩放）。", en_us = "Font size scale relative to 16px base.")
        @ConfigEntry.BoundedDiscrete(min = 1, max = 256)
        private int progressBarTextSize = 8;

        @ConfigEntry.Gui.Tooltip(zh_cn = "文字颜色。支持十六进制（0xAARRGGBB、#RRGGBB 等）或十进制 R,G,B / A,R,G,B。",
                en_us = "Text color: hex (0xAARRGGBB, #RRGGBB, …) or decimal R,G,B / A,R,G,B.")
        private String progressBarTextColor = "0x5DA530";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class DustbinCategory {
        @ConfigEntry.Gui.Tooltip(
                zh_cn = "垃圾箱界面：VANILLA 原版；TEXTURED 自定义纹理；BANIRA_THEME 使用主题色纯色绘制。",
                en_us = "Dustbin UI: VANILLA; TEXTURED; BANIRA_THEME.")
        private EnumDustbinClientUiStyle dustbinUiStyle = EnumDustbinClientUiStyle.TEXTURED;
    }
}
