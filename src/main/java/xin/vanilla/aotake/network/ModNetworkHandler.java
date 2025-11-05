package xin.vanilla.aotake.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xin.vanilla.aotake.network.packet.*;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";


    public static void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(OpenDustbinToServer.TYPE, OpenDustbinToServer.STREAM_CODEC, OpenDustbinToServer::handle);
        registrar.playToServer(ClearDustbinToServer.TYPE, ClearDustbinToServer.STREAM_CODEC, ClearDustbinToServer::handle);
        registrar.playToServer(ClientLoadedToServer.TYPE, ClientLoadedToServer.STREAM_CODEC, ClientLoadedToServer::handle);
        registrar.playToClient(CustomConfigSyncToClient.TYPE, CustomConfigSyncToClient.STREAM_CODEC, CustomConfigSyncToClient::handle);
        registrar.playToClient(SweepTimeSyncToClient.TYPE, SweepTimeSyncToClient.STREAM_CODEC, SweepTimeSyncToClient::handle);
    }
}
