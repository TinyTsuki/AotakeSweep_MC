package xin.vanilla.aotake.network;

import xin.vanilla.banira.common.api.INetworkPacket;

public interface NetworkPacket extends INetworkPacket {
    @Override
    default String channelId() {
        return NetworkInit.CHANNEL_ID;
    }
}
