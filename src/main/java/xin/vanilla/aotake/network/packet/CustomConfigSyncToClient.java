package xin.vanilla.aotake.network.packet;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.network.AotakePacket;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.VirtualPermissionManager;


public record CustomConfigSyncToClient(JsonObject customConfig) implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createIdentifier("custom_config_sync");

    public CustomConfigSyncToClient() {
        this(CustomConfig.getCustomConfig());
    }

    public CustomConfigSyncToClient(FriendlyByteBuf buf) {
        this(JsonUtils.GSON.fromJson(buf.readUtf(), JsonObject.class));
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        buf.writeUtf(this.customConfig.toString());
        return buf;
    }

    public static void handle(CustomConfigSyncToClient packet) {
        CustomConfig.setClientConfig(packet.customConfig());
        VirtualPermissionManager.reloadClient();
    }
}
