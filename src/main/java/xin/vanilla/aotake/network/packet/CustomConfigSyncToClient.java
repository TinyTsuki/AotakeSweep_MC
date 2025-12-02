package xin.vanilla.aotake.network.packet;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.network.AotakePacket;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.VirtualPermissionManager;

@Getter
public class CustomConfigSyncToClient implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createResource("custom_config_sync");

    /**
     * 自定义配置
     */
    private final JsonObject customConfig;

    public CustomConfigSyncToClient() {
        this.customConfig = CustomConfig.getCustomConfig();
    }

    public CustomConfigSyncToClient(FriendlyByteBuf buf) {
        this.customConfig = JsonUtils.GSON.fromJson(buf.readUtf(), JsonObject.class);
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
        CustomConfig.setClientConfig(packet.getCustomConfig());
        VirtualPermissionManager.reloadClient();
    }
}
