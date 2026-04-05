package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

/**
 * 尺寸类型
 */
public enum EnumSizeType implements IEnumDescribable {
    /**
     * 绝对大小
     */
    ABSOLUTE,
    /**
     * 相对大小(百分比)
     */
    RELATIVE_PERCENT,
    /**
     * 相对大小(像素)
     */
    RELATIVE_PIXEL,
    /**
     * 适应缩放
     */
    FIT,
    ;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
