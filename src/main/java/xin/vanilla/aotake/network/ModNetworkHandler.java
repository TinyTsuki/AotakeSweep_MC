package xin.vanilla.aotake.network;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
        ServerPlayNetworking.registerGlobalReceiver(OpenDustbinToServer.ID, (server, player, handler, buf, responseSender) -> {
            OpenDustbinToServer packet = new OpenDustbinToServer(buf);
            server.execute(() -> OpenDustbinToServer.handle(packet, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ClearDustbinToServer.ID, (server, player, handler, buf, responseSender) -> {
            ClearDustbinToServer packet = new ClearDustbinToServer(buf);
            server.execute(() -> ClearDustbinToServer.handle(packet, player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModLoadedToBoth.ID, (server, player, handler, buf, responseSender) ->
                server.execute(() -> ModLoadedToBoth.handle(player))
        );
    }

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(CustomConfigSyncToClient.ID, (client, handler, buf, responseSender) -> {
            CustomConfigSyncToClient packet = new CustomConfigSyncToClient(buf);
            client.execute(() -> CustomConfigSyncToClient.handle(packet));
        });
        ClientPlayNetworking.registerGlobalReceiver(SweepTimeSyncToClient.ID, (client, handler, buf, responseSender) -> {
            SweepTimeSyncToClient packet = new SweepTimeSyncToClient(buf);
            client.execute(() -> SweepTimeSyncToClient.handle(packet));
        });
        ClientPlayNetworking.registerGlobalReceiver(GhostCameraToClient.ID, (client, handler, buf, responseSender) -> {
            GhostCameraToClient packet = new GhostCameraToClient(buf);
            client.execute(() -> GhostCameraToClient.handle(packet));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModLoadedToBoth.ID, (client, handler, buf, responseSender) ->
                client.execute(() -> ModLoadedToBoth.handle(null))
        );
    }

    @Environment(EnvType.CLIENT)
    public static boolean hasAotakeServer() {
        return ClientPlayNetworking.getSendable().contains(ModLoadedToBoth.ID);
    }

}
