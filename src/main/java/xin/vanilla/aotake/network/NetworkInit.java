package xin.vanilla.aotake.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.*;

@EventBusSubscriber(modid = AotakeSweep.MODID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkInit {

    private NetworkInit() {
    }

    /**
     * 保留空方法，便于主类构造函数中仍调用（载荷实际在 {@link #registerPayloadHandlers} 注册）。
     */
    public static void registerPackets() {
    }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1").optional();

        reg.playToServer(OpenDustbinToServer.TYPE, OpenDustbinToServer.STREAM_CODEC, OpenDustbinToServer::handle);
        reg.playToServer(ChunkVaultNavigateToServer.TYPE, ChunkVaultNavigateToServer.STREAM_CODEC, ChunkVaultNavigateToServer::handle);
        reg.playToServer(ClearDustbinToServer.TYPE, ClearDustbinToServer.STREAM_CODEC, ClearDustbinToServer::handle);
        reg.playToServer(PlayerConfigSyncToServer.TYPE, PlayerConfigSyncToServer.STREAM_CODEC, PlayerConfigSyncToServer::handle);

        reg.playToClient(SweepDataSyncToClient.TYPE, SweepDataSyncToClient.STREAM_CODEC, SweepDataSyncToClient::handle);
        reg.playToClient(GhostCameraToClient.TYPE, GhostCameraToClient.STREAM_CODEC, GhostCameraToClient::handle);
        reg.playToClient(DustbinPageSyncToClient.TYPE, DustbinPageSyncToClient.STREAM_CODEC, DustbinPageSyncToClient::handle);
        reg.playToClient(ChunkVaultPageSyncToClient.TYPE, ChunkVaultPageSyncToClient.STREAM_CODEC, ChunkVaultPageSyncToClient::handle);
    }
}
