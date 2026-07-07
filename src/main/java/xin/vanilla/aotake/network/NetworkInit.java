package xin.vanilla.aotake.network;

import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.network.packet.*;
import xin.vanilla.banira.api.BaniraIdentifier;
import xin.vanilla.banira.common.network.NetworkHandler;

public class NetworkInit {
    public static final String CHANNEL_ID = AotakeSweep.MODID + ":main_network";

    private static final NetworkHandler HANDLER = NetworkHandler.create("main_network", BaniraIdentifier.of(Identifier.id().modId(), "main_network"));

    public static void registerPackets() {
        HANDLER.register(OpenDustbinToServer.class, OpenDustbinToServer::toBytes, OpenDustbinToServer::new, OpenDustbinToServer::handle);
        HANDLER.register(ChunkVaultNavigateToServer.class, ChunkVaultNavigateToServer::toBytes, ChunkVaultNavigateToServer::new, ChunkVaultNavigateToServer::handle);
        HANDLER.register(ClearDustbinToServer.class, ClearDustbinToServer::toBytes, ClearDustbinToServer::new, ClearDustbinToServer::handle);
        HANDLER.register(PlayerConfigSyncToServer.class, PlayerConfigSyncToServer::toBytes, PlayerConfigSyncToServer::new, PlayerConfigSyncToServer::handle);

        HANDLER.register(SweepDataSyncToClient.class, SweepDataSyncToClient::toBytes, SweepDataSyncToClient::new, SweepDataSyncToClient::handle);
        HANDLER.register(GhostCameraToClient.class, GhostCameraToClient::toBytes, GhostCameraToClient::new, GhostCameraToClient::handle);
        HANDLER.register(DustbinPageSyncToClient.class, DustbinPageSyncToClient::toBytes, DustbinPageSyncToClient::new, DustbinPageSyncToClient::handle);
        HANDLER.register(ChunkVaultPageSyncToClient.class, ChunkVaultPageSyncToClient::toBytes, ChunkVaultPageSyncToClient::new, ChunkVaultPageSyncToClient::handle);
    }
}
