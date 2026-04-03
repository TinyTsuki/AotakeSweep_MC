package xin.vanilla.aotake.network;

import net.minecraftforge.fmllegacy.network.simple.SimpleChannel;
import xin.vanilla.banira.common.api.INetworkPacket;

import java.util.function.Supplier;

public interface NetworkPacket extends INetworkPacket {
    default Supplier<SimpleChannel> channel() {
        return () -> NetworkInit.INSTANCE;
    }
}
