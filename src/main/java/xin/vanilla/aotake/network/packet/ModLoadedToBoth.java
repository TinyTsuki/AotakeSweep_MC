package xin.vanilla.aotake.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

public class ModLoadedToBoth implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<ModLoadedToBoth> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createResource("client_loaded"));
    public final static StreamCodec<ByteBuf, ModLoadedToBoth> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ModLoadedToBoth decode(@NotNull ByteBuf byteBuf) {
            return new ModLoadedToBoth((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ModLoadedToBoth packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    public ModLoadedToBoth() {
    }

    public ModLoadedToBoth(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModLoadedToBoth packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                AotakeSweep.getCustomConfigStatus().add(AotakeUtils.getPlayerUUIDString(player));
                // 同步自定义配置到客户端
                AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
                // 同步清理时间到客户端
                AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
            }
        });
    }
}
