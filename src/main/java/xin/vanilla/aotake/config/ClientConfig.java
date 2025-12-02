package xin.vanilla.aotake.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
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
@Config(name = AotakeSweep.MODID + "-server")
public class ClientConfig implements ConfigData {
    public static ClientConfig CLIENT_CONFIG;

    // region 进度条设置

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
    private double progressBarLeafAngle = 0;

    /**
     * 进度条竹叶高度
     */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
    private int progressBarLeafHeight = 10;

    /**
     * 进度条竹叶宽度
     */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
    private int progressBarLeafWidth = 9;


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
    private double progressBarPoleAngle = 0;

    /**
     * 进度条竹竿高度
     */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
    private int progressBarPoleHeight = 5;

    /**
     * 进度条竹竿宽度
     */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
    private int progressBarPoleWidth = 180;


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
    @ConfigEntry.BoundedDiscrete(min = 0, max = 360)
    private double progressBarTextAngle = 0;

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
    private String progressBarTextColor = "#FF5DA530";

    // endregion 进度条设置


    public static void register() {
        AutoConfig.register(ClientConfig.class, JanksonConfigSerializer::new);
        init();
    }

    public static void init() {
        CLIENT_CONFIG = AutoConfig.getConfigHolder(ClientConfig.class).getConfig();
    }

}
