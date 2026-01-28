package xin.vanilla.aotake.network.packet;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.VirtualPermissionManager;

public record CustomConfigSyncToClient(JsonObject customConfig) implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<CustomConfigSyncToClient> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createIdentifier("custom_config_sync"));
    public final static StreamCodec<ByteBuf, CustomConfigSyncToClient> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull CustomConfigSyncToClient decode(@NotNull ByteBuf byteBuf) {
            return new CustomConfigSyncToClient((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull CustomConfigSyncToClient packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    public CustomConfigSyncToClient() {
        this(CustomConfig.getCustomConfig());
    }

    public CustomConfigSyncToClient(FriendlyByteBuf buf) {
        this(JsonUtils.GSON.fromJson(buf.readUtf(), JsonObject.class));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.customConfig.toString());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CustomConfigSyncToClient packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            CustomConfig.setClientConfig(packet.customConfig());
            VirtualPermissionManager.reloadClient();
        });
    }
}
