package xin.vanilla.aotake.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xin.vanilla.aotake.network.packet.*;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";


    public static void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playBidirectional(ModLoadedToBoth.TYPE, ModLoadedToBoth.STREAM_CODEC, ModLoadedToBoth::handle);
        registrar.playToServer(OpenDustbinToServer.TYPE, OpenDustbinToServer.STREAM_CODEC, OpenDustbinToServer::handle);
        registrar.playToServer(ClearDustbinToServer.TYPE, ClearDustbinToServer.STREAM_CODEC, ClearDustbinToServer::handle);
        registrar.playToClient(CustomConfigSyncToClient.TYPE, CustomConfigSyncToClient.STREAM_CODEC, CustomConfigSyncToClient::handle);
        registrar.playToClient(SweepTimeSyncToClient.TYPE, SweepTimeSyncToClient.STREAM_CODEC, SweepTimeSyncToClient::handle);
    }

    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("UnstableApiUsage")
    public static boolean hasAotakeServer() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && NetworkRegistry.hasChannel(connection, ModLoadedToBoth.TYPE.id());
    }
}
