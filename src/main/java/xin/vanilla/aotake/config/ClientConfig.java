package xin.vanilla.aotake.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.enums.EnumRotationCenter;

import java.util.Arrays;
import java.util.List;

/**
 * 客户端配置
 */
public class ClientConfig {
    public static final ModConfigSpec CLIENT_CONFIG;


    // region 进度条设置

    /**
     * 正常状态下进度条显示方式
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROGRESS_BAR_DISPLAY_NORMAL;

    /**
     * 按住按键时进度条显示方式
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROGRESS_BAR_DISPLAY_HOLD;

    /**
     * 进度条显示按键应用模式
     */
    public static final ModConfigSpec.BooleanValue PROGRESS_BAR_KEY_APPLY_MODE;


    /**
     * 绘制竹叶时是否隐藏经验条
     */
    public static final ModConfigSpec.BooleanValue HIDE_EXPERIENCE_BAR_LEAF;

    /**
     * 进度条竹叶屏幕坐标系象限
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_LEAF_SCREEN_QUADRANT;

    /**
     * 进度条竹叶位置
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_LEAF_POSITION;

    /**
     * 进度条竹叶位置基准点
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_LEAF_BASE;

    /**
     * 进度条竹叶角度
     */
    public static final ModConfigSpec.DoubleValue PROGRESS_BAR_LEAF_ANGLE;

    /**
     * 进度条竹叶高度
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_LEAF_HEIGHT;

    /**
     * 进度条竹叶宽度
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_LEAF_WIDTH;


    /**
     * 绘制竹竿时是否隐藏经验条
     */
    public static final ModConfigSpec.BooleanValue HIDE_EXPERIENCE_BAR_POLE;

    /**
     * 进度条竹竿屏幕坐标系象限
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_POLE_SCREEN_QUADRANT;

    /**
     * 进度条竹竿位置
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_POLE_POSITION;

    /**
     * 进度条竹竿位置基准点
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_POLE_BASE;

    /**
     * 进度条竹竿角度
     */
    public static final ModConfigSpec.DoubleValue PROGRESS_BAR_POLE_ANGLE;

    /**
     * 进度条竹竿高度
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_POLE_HEIGHT;

    /**
     * 进度条竹竿宽度
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_POLE_WIDTH;


    /**
     * 绘制文字时是否隐藏经验条
     */
    public static final ModConfigSpec.BooleanValue HIDE_EXPERIENCE_BAR_TEXT;

    /**
     * 进度条文字屏幕坐标系象限
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_TEXT_SCREEN_QUADRANT;

    /**
     * 进度条文字位置
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_TEXT_POSITION;

    /**
     * 进度条文字位置基准点
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_TEXT_BASE;

    /**
     * 进度条文字角度
     */
    public static final ModConfigSpec.DoubleValue PROGRESS_BAR_TEXT_ANGLE;

    /**
     * 进度条文字大小
     */
    public static final ModConfigSpec.IntValue PROGRESS_BAR_TEXT_SIZE;

    /**
     * 进度条文字颜色
     */
    public static final ModConfigSpec.ConfigValue<String> PROGRESS_BAR_TEXT_COLOR;

    // endregion 进度条设置

    static {
        ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();

        // 定义客户端配置项
        CLIENT_BUILDER.comment("Client Settings", "客户端设置").push("client");

        // 进度条设置
        {
            CLIENT_BUILDER.comment("Progress Bar Settings", "进度条设置").push("progress_bar");

            // 正常状态下进度条显示方式
            PROGRESS_BAR_DISPLAY_NORMAL = CLIENT_BUILDER
                    .comment("Progress bar display mode under normal conditions."
                            , "LEAF: Show leaf;"
                            , "POLE: Show pole;"
                            , "TEXT: Show countdown text."
                            , "正常状态下进度条显示方式。"
                            , "LEAF：显示竹叶；"
                            , "POLE：显示竹竿；"
                            , "TEXT：显示倒计时文字。")
                    .defineList("progressBarDisplayNormal"
                            , Arrays.asList(EnumProgressBarType.LEAF.name())
                            , EnumProgressBarType::isValid
                    );

            // 按住按键时进度条显示方式
            PROGRESS_BAR_DISPLAY_HOLD = CLIENT_BUILDER
                    .comment("Progress bar display mode when holding the key."
                            , "LEAF: Show leaf;"
                            , "POLE: Show pole;"
                            , "TEXT: Show countdown text."
                            , "按住按键时进度条显示方式。"
                            , "LEAF：显示竹叶；"
                            , "POLE：显示竹竿；"
                            , "TEXT：显示倒计时文字。")
                    .defineList("progressBarDisplayHold"
                            , Arrays.asList(EnumProgressBarType.LEAF.name(), EnumProgressBarType.POLE.name(), EnumProgressBarType.TEXT.name())
                            , EnumProgressBarType::isValid);

            // 进度条显示按键应用模式
            PROGRESS_BAR_KEY_APPLY_MODE = CLIENT_BUILDER
                    .comment("Progress bar key apply mode."
                            , "false: Hold the key;"
                            , "true: Toggle."
                            , "进度条显示按键应用模式。"
                            , "false：按住；"
                            , "true：切换。")
                    .define("progressBarKeyApplyMode", false);

            // 竹叶
            {
                CLIENT_BUILDER.comment("Leaf", "竹叶").push("leaf");

                // 绘制竹叶时是否隐藏经验条
                HIDE_EXPERIENCE_BAR_LEAF = CLIENT_BUILDER
                        .comment("Whether to hide the experience bar when drawing the bamboo leaf."
                                , "绘制竹叶时是否隐藏经验条。")
                        .define("hideExperienceBarLeaf", false);

                // 屏幕坐标系象限
                PROGRESS_BAR_LEAF_SCREEN_QUADRANT = CLIENT_BUILDER
                        .comment("Screen coordinate system quadrant."
                                , "1: The origin is in the lower left corner of the screen, ↑→ is the positive direction;"
                                , "2: The origin is in the lower right corner of the screen, ↑← is the positive direction;"
                                , "3: The origin is in the upper right corner of the screen, ↓← is the positive direction;"
                                , "4: The origin is in the upper left corner of the screen, ↓→ is the positive direction."
                                , "屏幕坐标系象限。"
                                , "1：原点在屏幕左下角，↑→为正方向；"
                                , "2：原点在屏幕右下角，↑←为正方向；"
                                , "3：原点在屏幕右上角，↓←为正方向；"
                                , "4：原点在屏幕左上角，↓→为正方向。")
                        .defineInRange("progressBarLeafScreenQuadrant", 1, 1, 4);

                // 进度条竹叶位置
                PROGRESS_BAR_LEAF_POSITION = CLIENT_BUILDER
                        .comment("Position of the progress bar leaf relative to the bamboo pole."
                                , "x,y: Absolute coordinates. When the quadrant is set to 4, (0,0) represents the top-left corner of the bamboo pole;"
                                , "x%,y%: Relative coordinates. When the quadrant is set to 4, (0%,0%) represents the top-left corner of the bamboo pole."
                                , "进度条竹叶相对于竹竿的位置。"
                                , "x,y：绝对坐标，象限为4时，0,0为竹竿左上角；"
                                , "x%,y%：相对坐标，象限为4时，0%,0%为竹竿左上角。")
                        .define("progressBarLeafPosition"
                                , "0,4"
                                , s -> s instanceof String && ((String) s).contains(","));

                // 进度条竹叶位置基准点
                PROGRESS_BAR_LEAF_BASE = CLIENT_BUILDER
                        .comment("Progress bar leaf base."
                                , "TOP_LEFT: Uses the top-left corner of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s top-left corner aligns with the screen’s top-left corner."
                                , "TOP_RIGHT: Uses the top-right corner of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s top-right corner aligns with the screen’s top-left corner."
                                , "TOP_CENTER: Uses the top-center of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s top-center aligns with the screen’s top-left corner."
                                , "BOTTOM_LEFT: Uses the bottom-left corner of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s bottom-left corner aligns with the screen’s top-left corner."
                                , "BOTTOM_RIGHT: Uses the bottom-right corner of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s bottom-right corner aligns with the screen’s top-left corner."
                                , "BOTTOM_CENTER: Uses the bottom-center of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s bottom-center aligns with the screen’s top-left corner."
                                , "CENTER: Uses the center of the texture as the anchor point. When `progressBarLeafPosition` is `0,0`, the texture’s center aligns with the screen’s top-left corner."
                                , "进度条竹叶位置基准点。"
                                , "TOP_LEFT: 以纹理左上角为基准点，progressBarLeafPosition为0,0时纹理左上角与屏幕左上角重合；"
                                , "TOP_RIGHT: 以纹理右上角为基准点，progressBarLeafPosition为0,0时纹理右上角与屏幕左上角重合；"
                                , "TOP_CENTER: 以纹理中上部为基准点，progressBarLeafPosition为0,0时纹理中上部与屏幕左上角重合；"
                                , "BOTTOM_LEFT: 以纹理左下角为基准点，progressBarLeafPosition为0,0时纹理左下角与屏幕左上角重合；"
                                , "BOTTOM_RIGHT: 以纹理右下角为基准点，progressBarLeafPosition为0,0时纹理右下角与屏幕左上角重合；"
                                , "BOTTOM_CENTER: 以纹理中下部为基准点，progressBarLeafPosition为0,0时纹理中下部与屏幕左上角重合；"
                                , "CENTER: 以纹理中心为基准点，progressBarLeafPosition为0,0时纹理中心与屏幕左上角重合。")
                        .define("progressBarLeafBase"
                                , EnumRotationCenter.TOP_LEFT.name()
                                , EnumRotationCenter::isValid);

                // 进度条竹叶角度
                PROGRESS_BAR_LEAF_ANGLE = CLIENT_BUILDER
                        .comment("Progress bar leaf angle."
                                , "进度条竹叶角度。")
                        .defineInRange("progressBarLeafAngle", 0.0, 0.0, 360.0);

                // 进度条竹叶高度
                PROGRESS_BAR_LEAF_HEIGHT = CLIENT_BUILDER
                        .comment("Progress bar leaf height."
                                , "进度条竹叶高度。")
                        .defineInRange("progressBarLeafHeight", 10, 1, Integer.MAX_VALUE);

                // 进度条竹叶宽度
                PROGRESS_BAR_LEAF_WIDTH = CLIENT_BUILDER
                        .comment("Progress bar leaf width."
                                , "进度条竹叶宽度。")
                        .defineInRange("progressBarLeafWidth", 9, 1, Integer.MAX_VALUE);

                CLIENT_BUILDER.pop();
            }

            // 竹竿
            {
                CLIENT_BUILDER.comment("Pole", "竹竿").push("pole");

                // 绘制竹竿时是否隐藏经验条
                HIDE_EXPERIENCE_BAR_POLE = CLIENT_BUILDER
                        .comment("Whether to hide the experience bar when drawing the bamboo pole."
                                , "绘制竹竿时是否隐藏经验条。")
                        .define("hideExperienceBarPole", true);

                // 屏幕坐标系象限
                PROGRESS_BAR_POLE_SCREEN_QUADRANT = CLIENT_BUILDER
                        .comment("Screen coordinate system quadrant."
                                , "1: The origin is in the lower left corner of the screen, ↑→ is the positive direction;"
                                , "2: The origin is in the lower right corner of the screen, ↑← is the positive direction;"
                                , "3: The origin is in the upper right corner of the screen, ↓← is the positive direction;"
                                , "4: The origin is in the upper left corner of the screen, ↓→ is the positive direction."
                                , "屏幕坐标系象限。"
                                , "1：原点在屏幕左下角，↑→为正方向；"
                                , "2：原点在屏幕右下角，↑←为正方向；"
                                , "3：原点在屏幕右上角，↓←为正方向；"
                                , "4：原点在屏幕左上角，↓→为正方向。")
                        .defineInRange("progressBarPoleScreenQuadrant", 1, 1, 4);

                // 进度条竹竿位置
                PROGRESS_BAR_POLE_POSITION = CLIENT_BUILDER
                        .comment("Progress bar pole position."
                                , "x,y: Absolute coordinates. When the quadrant is set to 4, 0,0 is the top left corner of the screen;"
                                , "x%,y%: Relative coordinates. When the quadrant is set to 4, 0%,0% is the top left corner of the screen."
                                , "进度条竹竿位置。"
                                , "x,y：绝对坐标，象限为4时，0,0为屏幕左上角；"
                                , "x%,y%：相对坐标，象限为4时，0%,0%为屏幕左上角。")
                        .define("progressBarPolePosition"
                                , "50%,29"
                                , s -> s instanceof String && ((String) s).contains(","));

                // 进度条竹竿位置基准点
                PROGRESS_BAR_POLE_BASE = CLIENT_BUILDER
                        .comment("Progress bar pole base."
                                , "TOP_LEFT: Uses the top-left corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s top-left corner aligns with the screen’s top-left corner."
                                , "TOP_RIGHT: Uses the top-right corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s top-right corner aligns with the screen’s top-left corner."
                                , "TOP_CENTER: Uses the top-center of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s top-center aligns with the screen’s top-left corner."
                                , "BOTTOM_LEFT: Uses the bottom-left corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s bottom-left corner aligns with the screen’s top-left corner."
                                , "BOTTOM_RIGHT: Uses the bottom-right corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s bottom-right corner aligns with the screen’s top-left corner."
                                , "BOTTOM_CENTER: Uses the bottom-center of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s bottom-center aligns with the screen’s top-left corner."
                                , "CENTER: Uses the center of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s center aligns with the screen’s top-left corner."
                                , "进度条竹竿位置基准点。"
                                , "TOP_LEFT: 以纹理左上角为基准点，progressBarPolePosition为0,0时纹理左上角与屏幕左上角重合；"
                                , "TOP_RIGHT: 以纹理右上角为基准点，progressBarPolePosition为0,0时纹理右上角与屏幕左上角重合；"
                                , "TOP_CENTER: 以纹理中上部为基准点，progressBarPolePosition为0,0时纹理中上部与屏幕左上角重合；"
                                , "BOTTOM_LEFT: 以纹理左下角为基准点，progressBarPolePosition为0,0时纹理左下角与屏幕左上角重合；"
                                , "BOTTOM_RIGHT: 以纹理右下角为基准点，progressBarPolePosition为0,0时纹理右下角与屏幕左上角重合；"
                                , "BOTTOM_CENTER: 以纹理中下部为基准点，progressBarPolePosition为0,0时纹理中下部与屏幕左上角重合；"
                                , "CENTER: 以纹理中心为基准点，progressBarPolePosition为0,0时纹理中心与屏幕左上角重合。")
                        .define("progressBarPoleBase"
                                , EnumRotationCenter.TOP_CENTER.name()
                                , EnumRotationCenter::isValid);

                // 进度条竹竿角度
                PROGRESS_BAR_POLE_ANGLE = CLIENT_BUILDER
                        .comment("Progress bar pole angle."
                                , "进度条竹竿角度。")
                        .defineInRange("progressBarPoleAngle", 0.0, 0.0, 360.0);

                // 进度条竹竿高度
                PROGRESS_BAR_POLE_HEIGHT = CLIENT_BUILDER
                        .comment("Progress bar pole height."
                                , "进度条竹竿高度。")
                        .defineInRange("progressBarPoleHeight", 5, 1, Integer.MAX_VALUE);

                // 进度条竹竿宽度
                PROGRESS_BAR_POLE_WIDTH = CLIENT_BUILDER
                        .comment("Progress bar pole width."
                                , "进度条竹竿宽度。")
                        .defineInRange("progressBarPoleWidth", 180, 1, Integer.MAX_VALUE);

                CLIENT_BUILDER.pop();
            }

            // 文字
            {
                CLIENT_BUILDER.comment("Text", "文字").push("text");

                // 绘制文字时是否隐藏经验条
                HIDE_EXPERIENCE_BAR_TEXT = CLIENT_BUILDER
                        .comment("Whether to hide the experience bar when drawing the text."
                                , "绘制文字时是否隐藏经验条。")
                        .define("hideExperienceBarText", true);

                // 屏幕坐标系象限
                PROGRESS_BAR_TEXT_SCREEN_QUADRANT = CLIENT_BUILDER
                        .comment("Screen coordinate system quadrant."
                                , "1: The origin is in the lower left corner of the screen, ↑→ is the positive direction;"
                                , "2: The origin is in the lower right corner of the screen, ↑← is the positive direction;"
                                , "3: The origin is in the upper right corner of the screen, ↓← is the positive direction;"
                                , "4: The origin is in the upper left corner of the screen, ↓→ is the positive direction."
                                , "屏幕坐标系象限。"
                                , "1：原点在屏幕左下角，↑→为正方向；"
                                , "2：原点在屏幕右下角，↑←为正方向；"
                                , "3：原点在屏幕右上角，↓←为正方向；"
                                , "4：原点在屏幕左上角，↓→为正方向。")
                        .defineInRange("progressBarTextScreenQuadrant", 1, 1, 4);

                // 进度条文字位置
                PROGRESS_BAR_TEXT_POSITION = CLIENT_BUILDER
                        .comment("Position of the progress bar text relative to the text."
                                , "x,y: Absolute coordinates. When the quadrant is set to 4, (0,0) represents the top-left corner of the text;"
                                , "x%,y%: Relative coordinates. When the quadrant is set to 4, (0%,0%) represents the top-left corner of the text."
                                , "进度条文字相对于竹竿的位置。"
                                , "x,y：绝对坐标，象限为4时，0,0为竹竿左上角；"
                                , "x%,y%：相对坐标，象限为4时，0%,0%为竹竿左上角。")
                        .define("progressBarTextPosition"
                                , "50%,8"
                                , s -> s instanceof String && ((String) s).contains(","));

                // 进度条文字位置基准点
                PROGRESS_BAR_TEXT_BASE = CLIENT_BUILDER
                        .comment("Progress bar text base."
                                , "TOP_LEFT: Uses the top-left corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s top-left corner aligns with the screen’s top-left corner."
                                , "TOP_RIGHT: Uses the top-right corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s top-right corner aligns with the screen’s top-left corner."
                                , "TOP_CENTER: Uses the top-center of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s top-center aligns with the screen’s top-left corner."
                                , "BOTTOM_LEFT: Uses the bottom-left corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s bottom-left corner aligns with the screen’s top-left corner."
                                , "BOTTOM_RIGHT: Uses the bottom-right corner of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s bottom-right corner aligns with the screen’s top-left corner."
                                , "BOTTOM_CENTER: Uses the bottom-center of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s bottom-center aligns with the screen’s top-left corner."
                                , "CENTER: Uses the center of the texture as the anchor point. When `progressBarPolePosition` is `0,0`, the texture’s center aligns with the screen’s top-left corner."
                                , "进度条文字位置基准点。"
                                , "TOP_LEFT: 以纹理左上角为基准点，progressBarPolePosition为0,0时纹理左上角与屏幕左上角重合；"
                                , "TOP_RIGHT: 以纹理右上角为基准点，progressBarPolePosition为0,0时纹理右上角与屏幕左上角重合；"
                                , "TOP_CENTER: 以纹理中上部为基准点，progressBarPolePosition为0,0时纹理中上部与屏幕左上角重合；"
                                , "BOTTOM_LEFT: 以纹理左下角为基准点，progressBarPolePosition为0,0时纹理左下角与屏幕左上角重合；"
                                , "BOTTOM_RIGHT: 以纹理右下角为基准点，progressBarPolePosition为0,0时纹理右下角与屏幕左上角重合；"
                                , "BOTTOM_CENTER: 以纹理中下部为基准点，progressBarPolePosition为0,0时纹理中下部与屏幕左上角重合；"
                                , "CENTER: 以纹理中心为基准点，progressBarPolePosition为0,0时纹理中心与屏幕左上角重合。")
                        .define("progressBarTextBase"
                                , EnumRotationCenter.TOP_CENTER.name()
                                , EnumRotationCenter::isValid);

                // 进度条文字角度
                PROGRESS_BAR_TEXT_ANGLE = CLIENT_BUILDER
                        .comment("Progress bar text angle."
                                , "进度条文字角度。")
                        .defineInRange("progressBarTextAngle", 0.0, 0.0, 360.0);

                // 进度条文字大小
                PROGRESS_BAR_TEXT_SIZE = CLIENT_BUILDER
                        .comment("Progress bar text size."
                                , "进度条文字大小。")
                        .defineInRange("progressBarTextSize", 8, 1, 16 * 16);

                // 进度条文字颜色
                PROGRESS_BAR_TEXT_COLOR = CLIENT_BUILDER
                        .comment("Progress bar text color. The following formats are supported:"
                                , "Hex: `0xAARRGGBB`, `0xRRGGBB`, `#RRGGBB`, `#AARRGGBB`"
                                , "Decimal: `R,G,B` or `A,R,G,B`"
                                , "进度条文字颜色。支持以下格式："
                                , "十六进制：`0xAARRGGBB`、`0xRRGGBB`、`#RRGGBB`、`#AARRGGBB`"
                                , "十进制：`R,G,B`、`A,R,G,B`")
                        .define("progressBarTextColor", "0x5DA530", o -> o instanceof String);

                CLIENT_BUILDER.pop();
            }


            CLIENT_BUILDER.pop();

        }

        CLIENT_BUILDER.pop();

        CLIENT_CONFIG = CLIENT_BUILDER.build();
    }
}
