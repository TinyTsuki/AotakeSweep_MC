package xin.vanilla.aotake.network;

import net.minecraftforge.fml.network.simple.SimpleChannel;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.network.packet.*;
import xin.vanilla.banira.common.network.NetworkHandler;

public class NetworkInit {

    private static final NetworkHandler HANDLER = NetworkHandler.create("main_network", Identifier.id());

    public static final SimpleChannel INSTANCE = HANDLER.getChannel();

    public static void registerPackets() {
        HANDLER.register(OpenDustbinToServer.class, OpenDustbinToServer::toBytes, OpenDustbinToServer::new, OpenDustbinToServer::handle);
        HANDLER.register(ClearDustbinToServer.class, ClearDustbinToServer::toBytes, ClearDustbinToServer::new, ClearDustbinToServer::handle);

        HANDLER.register(SweepTimeSyncToClient.class, SweepTimeSyncToClient::toBytes, SweepTimeSyncToClient::new, SweepTimeSyncToClient::handle);
        HANDLER.register(GhostCameraToClient.class, GhostCameraToClient::toBytes, GhostCameraToClient::new, GhostCameraToClient::handle);
        HANDLER.register(DustbinPageSyncToClient.class, DustbinPageSyncToClient::toBytes, DustbinPageSyncToClient::new, DustbinPageSyncToClient::handle);
    }
}
