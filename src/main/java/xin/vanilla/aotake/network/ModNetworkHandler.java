package xin.vanilla.aotake.network;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import xin.vanilla.aotake.network.packet.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Accessors(fluent = true)
public final class ModNetworkHandler {
    /**
     * 分片网络包缓存
     */
    @Getter
    private static final Map<String, List<? extends SplitPacket>> packetCache = new ConcurrentHashMap<>();


    public static void registerServerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(OpenDustbinToServer.ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> OpenDustbinToServer.handle(new OpenDustbinToServer(buf), player))
        );
        ServerPlayNetworking.registerGlobalReceiver(ClearDustbinToServer.ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> ClearDustbinToServer.handle(new ClearDustbinToServer(buf), player))
        );
        ServerPlayNetworking.registerGlobalReceiver(ClientLoadedToServer.ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> ClientLoadedToServer.handle(player))
        );
    }

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(CustomConfigSyncToClient.ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> CustomConfigSyncToClient.handle(new CustomConfigSyncToClient(buf)));
        });
        ClientPlayNetworking.registerGlobalReceiver(SweepTimeSyncToClient.ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> SweepTimeSyncToClient.handle(new SweepTimeSyncToClient(buf)));
        });
    }

}
