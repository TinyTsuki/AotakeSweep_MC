package xin.vanilla.aotake.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.enums.EnumRotationCenter;

import java.util.Arrays;
import java.util.List;

/**
 * 客户端配置
 */
@Getter
@Setter
@Accessors(fluent = true)
@Config(name = AotakeSweep.MODID + "-client")
public class ClientConfig implements ConfigData {

    private static ClientConfig CLIENT_CONFIG;

    public static ClientConfig get() {
        return CLIENT_CONFIG;
    }

    // region 进度条设置
    @ConfigEntry.Gui.CollapsibleObject
    private ProgressBarConfig progressBarConfig = new ProgressBarConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class ProgressBarConfig {
        /**
         * 正常状态下进度条显示方式
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> progressBarDisplayNormal = List.of(EnumProgressBarType.LEAF.name());

        /**
         * 按住按键时进度条显示方式
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> progressBarDisplayHold = Arrays.asList(EnumProgressBarType.names());

        /**
         * 进度条显示按键应用模式
         */
        @ConfigEntry.Gui.Tooltip
        private boolean progressBarKeyApplyMode = false;

        @ConfigEntry.Gui.CollapsibleObject
        private LeafConfig leafConfig = new LeafConfig();

        @Getter
        @Setter
        @Accessors(fluent = true)
        public static class LeafConfig {
            /**
             * 绘制竹叶时是否隐藏经验条
             */
            @ConfigEntry.Gui.Tooltip
            private boolean hideExperienceBarLeaf = false;
            /**
             * 进度条竹叶屏幕坐标系象限
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.BoundedDiscrete(min = 1, max = 4)
            private int progressBarLeafScreenQuadrant = 1;

            /**
             * 进度条竹叶位置
             */
            @ConfigEntry.Gui.Tooltip
            private String progressBarLeafPosition = "0,4";

            /**
             * 进度条竹叶位置基准点
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
            private EnumRotationCenter progressBarLeafBase = EnumRotationCenter.TOP_LEFT;

            /**
             * 进度条竹叶角度
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.BoundedDiscrete(min = 0, max = 360)
            private double progressBarLeafAngle = 0.0;

            /**
             * 进度条竹叶高度
             */
            @ConfigEntry.Gui.Tooltip
            // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
            private int progressBarLeafHeight = 10;

            /**
             * 进度条竹叶宽度
             */
            @ConfigEntry.Gui.Tooltip
            // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
            private int progressBarLeafWidth = 9;
        }

        @ConfigEntry.Gui.CollapsibleObject
        private PoleConfig poleConfig = new PoleConfig();

        @Getter
        @Setter
        @Accessors(fluent = true)
        public static class PoleConfig {
            /**
             * 绘制竹竿时是否隐藏经验条
             */
            @ConfigEntry.Gui.Tooltip
            private boolean hideExperienceBarPole = true;

            /**
             * 进度条竹竿屏幕坐标系象限
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.BoundedDiscrete(min = 1, max = 4)
            private int progressBarPoleScreenQuadrant = 1;

            /**
             * 进度条竹竿位置
             */
            @ConfigEntry.Gui.Tooltip
            private String progressBarPolePosition = "50%,29";

            /**
             * 进度条竹竿位置基准点
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
            private EnumRotationCenter progressBarPoleBase = EnumRotationCenter.TOP_CENTER;

            /**
             * 进度条竹竿角度
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.BoundedDiscrete(min = 0, max = 360)
            private double progressBarPoleAngle = 0.0;

            /**
             * 进度条竹竿高度
             */
            @ConfigEntry.Gui.Tooltip
            // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
            private int progressBarPoleHeight = 5;

            /**
             * 进度条竹竿宽度
             */
            @ConfigEntry.Gui.Tooltip
            // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
            private int progressBarPoleWidth = 180;
        }

        @ConfigEntry.Gui.CollapsibleObject
        private TextConfig textConfig = new TextConfig();

        @Getter
        @Setter
        @Accessors(fluent = true)
        public static class TextConfig {
            /**
             * 绘制文字时是否隐藏经验条
             */
            @ConfigEntry.Gui.Tooltip
            private boolean hideExperienceBarText = true;

            /**
             * 进度条文字屏幕坐标系象限
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.BoundedDiscrete(min = 1, max = 4)
            private int progressBarTextScreenQuadrant = 1;

            /**
             * 进度条文字位置
             */
            @ConfigEntry.Gui.Tooltip
            private String progressBarTextPosition = "50%,8";

            /**
             * 进度条文字位置基准点
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
            private EnumRotationCenter progressBarTextBase = EnumRotationCenter.TOP_CENTER;

            /**
             * 进度条文字角度
             */
            @ConfigEntry.Gui.Tooltip
            // @ConfigEntry.BoundedDiscrete(min = 0, max = 360)
            private double progressBarTextAngle = 0.0;

            /**
             * 进度条文字大小
             */
            @ConfigEntry.Gui.Tooltip
            @ConfigEntry.BoundedDiscrete(min = 1, max = 16 * 16)
            private int progressBarTextSize = 8;

            /**
             * 进度条文字颜色
             */
            @ConfigEntry.Gui.Tooltip
            private String progressBarTextColor = "0x5DA530";
        }
    }
    // endregion 进度条设置


    public static void register() {
        AutoConfig.register(ClientConfig.class, Toml4jConfigSerializer::new);
        CLIENT_CONFIG = AutoConfig.getConfigHolder(ClientConfig.class).getConfig();
    }

    @Override
    public void validatePostLoad() {
        // Leaf
        if (progressBarConfig.leafConfig().progressBarLeafScreenQuadrant() < 1)
            progressBarConfig.leafConfig().progressBarLeafScreenQuadrant(1);
        if (progressBarConfig.leafConfig().progressBarLeafScreenQuadrant() > 4)
            progressBarConfig.leafConfig().progressBarLeafScreenQuadrant(4);

        if (progressBarConfig.leafConfig().progressBarLeafHeight() < 1)
            progressBarConfig.leafConfig().progressBarLeafHeight(1);
        if (progressBarConfig.leafConfig().progressBarLeafWidth() < 1)
            progressBarConfig.leafConfig().progressBarLeafWidth(1);

        // Pole
        if (progressBarConfig.poleConfig().progressBarPoleScreenQuadrant() < 1)
            progressBarConfig.poleConfig().progressBarPoleScreenQuadrant(1);
        if (progressBarConfig.poleConfig().progressBarPoleScreenQuadrant() > 4)
            progressBarConfig.poleConfig().progressBarPoleScreenQuadrant(4);

        if (progressBarConfig.poleConfig().progressBarPoleHeight() < 1)
            progressBarConfig.poleConfig().progressBarPoleHeight(1);
        if (progressBarConfig.poleConfig().progressBarPoleWidth() < 1)
            progressBarConfig.poleConfig().progressBarPoleWidth(1);

        // Text
        if (progressBarConfig.textConfig().progressBarTextScreenQuadrant() < 1)
            progressBarConfig.textConfig().progressBarTextScreenQuadrant(1);
        if (progressBarConfig.textConfig().progressBarTextScreenQuadrant() > 4)
            progressBarConfig.textConfig().progressBarTextScreenQuadrant(4);

        if (progressBarConfig.textConfig().progressBarTextAngle() < 0)
            progressBarConfig.textConfig().progressBarTextAngle(0);
        if (progressBarConfig.textConfig().progressBarTextAngle() > 360)
            progressBarConfig.textConfig().progressBarTextAngle(360);

        if (progressBarConfig.textConfig().progressBarTextSize() < 1)
            progressBarConfig.textConfig().progressBarTextSize(1);
        if (progressBarConfig.textConfig().progressBarTextSize() > 256)
            progressBarConfig.textConfig().progressBarTextSize(256);
    }
}
