package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

/**
 * 倒计时文字相对「参考点」的垂直对齐
 */
public enum EnumProgressBarTextAlignV implements IEnumDescribable {
    TOP,
    CENTER,
    BOTTOM,
    ;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
