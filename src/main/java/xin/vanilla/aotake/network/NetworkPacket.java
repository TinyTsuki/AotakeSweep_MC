package xin.vanilla.aotake.network;

import net.minecraftforge.network.SimpleChannel;
import xin.vanilla.banira.common.api.INetworkPacket;

import java.util.function.Supplier;

public interface NetworkPacket extends INetworkPacket {
    default Supplier<SimpleChannel> channel() {
        return () -> NetworkInit.INSTANCE;
    }
}
