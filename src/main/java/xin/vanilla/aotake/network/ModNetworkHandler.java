package xin.vanilla.aotake.network;

import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.ClearDustbinNotice;
import xin.vanilla.aotake.network.packet.OpenDustbinNotice;

public class ModNetworkHandler {
    private static final int PROTOCOL_VERSION = 1;
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = ChannelBuilder.named(AotakeSweep.createResource("main_network"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .clientAcceptedVersions((status, version) -> true)    // 客户端版本始终有效
            .serverAcceptedVersions((status, version) -> true)    // 服务端版本始终有效
            .simpleChannel();

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.messageBuilder(OpenDustbinNotice.class, nextID()).encoder(OpenDustbinNotice::toBytes).decoder(OpenDustbinNotice::new).consumerMainThread(OpenDustbinNotice::handle).add();
        INSTANCE.messageBuilder(ClearDustbinNotice.class, nextID()).encoder(ClearDustbinNotice::toBytes).decoder(ClearDustbinNotice::new).consumerMainThread(ClearDustbinNotice::handle).add();
    }
}
