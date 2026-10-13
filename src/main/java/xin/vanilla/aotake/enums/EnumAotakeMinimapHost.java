package xin.vanilla.aotake.enums;

import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumNotificationHudHost;
import xin.vanilla.banira.common.enums.IEnumDescribable;

public enum EnumAotakeMinimapHost implements IEnumDescribable {
    AUTO, XAERO, JOURNEYMAP, FTB_CHUNKS, VOXELMAP, MAP_ATLASES;

    public EnumNotificationHudHost notificationHost() {
        return EnumNotificationHudHost.valueOf(name());
    }

    @Override
    public Component enumDescription() {
        return notificationHost().enumDescription();
    }
}
