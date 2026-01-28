package xin.vanilla.aotake.network;

import lombok.experimental.Accessors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.network.packet.*;


@Accessors(fluent = true)
@SuppressWarnings("resource")
public final class ModNetworkHandler {

    public static void registerCommonPackets() {
        PayloadTypeRegistry.playC2S().register(OpenDustbinToServer.ID, OpenDustbinToServer.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearDustbinToServer.ID, ClearDustbinToServer.CODEC);
        PayloadTypeRegistry.playC2S().register(ModLoadedToBoth.ID, ModLoadedToBoth.CODEC);

        PayloadTypeRegistry.playS2C().register(CustomConfigSyncToClient.ID, CustomConfigSyncToClient.CODEC);
        PayloadTypeRegistry.playS2C().register(SweepTimeSyncToClient.ID, SweepTimeSyncToClient.CODEC);
        PayloadTypeRegistry.playS2C().register(GhostCameraToClient.ID, GhostCameraToClient.CODEC);
        PayloadTypeRegistry.playS2C().register(DustbinPageSyncToClient.ID, DustbinPageSyncToClient.CODEC);
        PayloadTypeRegistry.playS2C().register(ModLoadedToBoth.ID, ModLoadedToBoth.CODEC);
    }

    public static void registerServerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(OpenDustbinToServer.ID, (payload, context) ->
                context.server().execute(() -> OpenDustbinToServer.handle(payload, context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(ClearDustbinToServer.ID, (payload, context) ->
                context.server().execute(() -> ClearDustbinToServer.handle(payload, context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(ModLoadedToBoth.ID, (payload, context) ->
                context.server().execute(() -> ModLoadedToBoth.handle(context.player()))
        );
    }

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(CustomConfigSyncToClient.ID, (payload, context) ->
                context.client().execute(() -> CustomConfigSyncToClient.handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(SweepTimeSyncToClient.ID, (payload, context) ->
                context.client().execute(() -> SweepTimeSyncToClient.handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(GhostCameraToClient.ID, (payload, context) ->
                context.client().execute(() -> GhostCameraToClient.handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(DustbinPageSyncToClient.ID, (payload, context) ->
                context.client().execute(() -> DustbinPageSyncToClient.handle(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(ModLoadedToBoth.ID, (payload, context) ->
                context.client().execute(() -> ModLoadedToBoth.handle(null))
        );
    }

    @Environment(EnvType.CLIENT)
    public static boolean hasAotakeServer() {
        return hasCannel(ModLoadedToBoth.ID.id());
    }

    @Environment(EnvType.CLIENT)
    public static boolean hasCannel(ResourceLocation channel) {
        return ClientPlayNetworking.getSendable().contains(channel);
    }

    public static boolean hasCannel(ServerPlayer player, ResourceLocation channel) {
        return ServerPlayNetworking.getSendable(player).contains(channel);
    }

}
