package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

/**
 * 倒计时文字相对「参考点」的水平对齐：参考点由竹竿位置 + 偏移 + 象限决定。
 */
public enum EnumProgressBarTextAlignH implements IEnumDescribable {
    LEFT,
    CENTER,
    RIGHT,
    ;

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
