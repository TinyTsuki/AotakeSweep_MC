package xin.vanilla.aotake.network;

import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.*;

public class ModNetworkHandler {
    private static final int PROTOCOL_VERSION = 1;
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = ChannelBuilder.named(AotakeSweep.createIdentifier("main_network"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .clientAcceptedVersions((status, version) -> true)    // 客户端版本始终有效
            .serverAcceptedVersions((status, version) -> true)    // 服务端版本始终有效
            .simpleChannel();

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.messageBuilder(OpenDustbinToServer.class, nextID()).encoder(OpenDustbinToServer::toBytes).decoder(OpenDustbinToServer::new).consumerMainThread(OpenDustbinToServer::handle).add();
        INSTANCE.messageBuilder(ClearDustbinToServer.class, nextID()).encoder(ClearDustbinToServer::toBytes).decoder(ClearDustbinToServer::new).consumerMainThread(ClearDustbinToServer::handle).add();
        INSTANCE.messageBuilder(ModLoadedToBoth.class, nextID()).encoder(ModLoadedToBoth::toBytes).decoder(ModLoadedToBoth::new).consumerMainThread(ModLoadedToBoth::handle).add();
        INSTANCE.messageBuilder(CustomConfigSyncToClient.class, nextID()).encoder(CustomConfigSyncToClient::toBytes).decoder(CustomConfigSyncToClient::new).consumerMainThread(CustomConfigSyncToClient::handle).add();
        INSTANCE.messageBuilder(SweepTimeSyncToClient.class, nextID()).encoder(SweepTimeSyncToClient::toBytes).decoder(SweepTimeSyncToClient::new).consumerMainThread(SweepTimeSyncToClient::handle).add();
        INSTANCE.messageBuilder(GhostCameraToClient.class, nextID()).encoder(GhostCameraToClient::toBytes).decoder(GhostCameraToClient::new).consumerMainThread(GhostCameraToClient::handle).add();
        INSTANCE.messageBuilder(DustbinPageSyncToClient.class, nextID()).encoder(DustbinPageSyncToClient::toBytes).decoder(DustbinPageSyncToClient::new).consumerMainThread(DustbinPageSyncToClient::handle).add();
    }
}
