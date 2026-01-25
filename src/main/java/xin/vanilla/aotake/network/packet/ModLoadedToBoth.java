package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

public record ModLoadedToBoth() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ModLoadedToBoth> ID = new CustomPacketPayload.Type<>(AotakeSweep.createResource("client_loaded"));
    public static final StreamCodec<FriendlyByteBuf, ModLoadedToBoth> CODEC = StreamCodec.unit(new ModLoadedToBoth());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(ServerPlayer player) {
        if (player != null) {
            AotakeSweep.customConfigStatus().add(AotakeUtils.getPlayerUUIDString(player));
            // 同步自定义配置到客户端
            AotakeUtils.sendPacketToPlayer(player, new CustomConfigSyncToClient());
            // 同步清理时间到客户端
            AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
        }
    }
}
