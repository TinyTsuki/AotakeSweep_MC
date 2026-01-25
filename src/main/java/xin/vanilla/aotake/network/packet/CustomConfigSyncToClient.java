package xin.vanilla.aotake.network.packet;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.VirtualPermissionManager;

public record CustomConfigSyncToClient(JsonObject customConfig) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CustomConfigSyncToClient> ID = new CustomPacketPayload.Type<>(AotakeSweep.createResource("custom_config_sync"));
    public static final StreamCodec<FriendlyByteBuf, CustomConfigSyncToClient> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.customConfig.toString()),
            buf -> new CustomConfigSyncToClient(JsonUtils.GSON.fromJson(buf.readUtf(), JsonObject.class))
    );

    public CustomConfigSyncToClient() {
        this(CustomConfig.getCustomConfig());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(CustomConfigSyncToClient packet) {
        CustomConfig.setClientConfig(packet.customConfig());
        VirtualPermissionManager.reloadClient();
    }
}
