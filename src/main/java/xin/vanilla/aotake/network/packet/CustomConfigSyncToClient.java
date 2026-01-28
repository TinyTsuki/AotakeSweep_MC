package xin.vanilla.aotake.network.packet;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.VirtualPermissionManager;

public record CustomConfigSyncToClient(JsonObject customConfig) {

    public CustomConfigSyncToClient() {
        this(CustomConfig.getCustomConfig());
    }

    public CustomConfigSyncToClient(FriendlyByteBuf buf) {
        this(JsonUtils.GSON.fromJson(buf.readUtf(), JsonObject.class));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.customConfig.toString());
    }

    public static void handle(CustomConfigSyncToClient packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            CustomConfig.setClientConfig(packet.customConfig());
            VirtualPermissionManager.reloadClient();
        });
        ctx.setPacketHandled(true);
    }
}
