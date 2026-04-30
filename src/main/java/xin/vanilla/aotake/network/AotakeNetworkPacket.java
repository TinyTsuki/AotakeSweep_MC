package xin.vanilla.aotake.network;

import net.minecraft.resources.ResourceLocation;
import xin.vanilla.banira.common.api.INetworkPacket;

/**
 * Aotake 模组在 {@link NetworkInit#HANDLER} 上注册的网络包标记接口（与 Banira 内 {@link xin.vanilla.banira.common.network.NetworkPacket} 同构，channel 为本模组）。
 */
public interface AotakeNetworkPacket extends INetworkPacket {

    default ResourceLocation channel() {
        return NetworkInit.HANDLER.channel();
    }
}
