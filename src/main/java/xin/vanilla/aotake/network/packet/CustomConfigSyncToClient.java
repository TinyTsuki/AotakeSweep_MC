package xin.vanilla.aotake.network.packet;

import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.VirtualPermissionManager;

import java.util.function.Supplier;

@Getter
public class CustomConfigSyncToClient {
    /**
     * 自定义配置
     */
    private final JsonObject customConfig;

    public CustomConfigSyncToClient() {
        this.customConfig = CustomConfig.getCustomConfig();
    }

    public CustomConfigSyncToClient(PacketBuffer buf) {
        this.customConfig = JsonUtils.GSON.fromJson(buf.readUtf(), JsonObject.class);
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeUtf(this.customConfig.toString());
    }

    public static void handle(CustomConfigSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CustomConfig.setClientConfig(packet.getCustomConfig());
            VirtualPermissionManager.reloadClient();
        });
        ctx.get().setPacketHandled(true);
    }
}
