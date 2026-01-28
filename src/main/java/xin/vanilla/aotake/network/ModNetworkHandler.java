package xin.vanilla.aotake.network;

import lombok.experimental.Accessors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.network.packet.*;

@Accessors(fluent = true)
public final class ModNetworkHandler {

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
        ClientPlayNetworking.registerGlobalReceiver(DustbinPageSyncToClient.ID, (client, handler, buf, responseSender) -> {
            DustbinPageSyncToClient packet = new DustbinPageSyncToClient(buf);
            client.execute(() -> DustbinPageSyncToClient.handle(packet));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModLoadedToBoth.ID, (client, handler, buf, responseSender) ->
                client.execute(() -> ModLoadedToBoth.handle(null))
        );
    }

    @Environment(EnvType.CLIENT)
    public static boolean hasAotakeServer() {
        return hasCannel(ModLoadedToBoth.ID);
    }

    @Environment(EnvType.CLIENT)
    public static boolean hasCannel(ResourceLocation channel) {
        return ClientPlayNetworking.getSendable().contains(channel);
    }

    public static boolean hasCannel(ServerPlayer player, ResourceLocation channel) {
        return ServerPlayNetworking.getSendable(player).contains(channel);
    }

}
