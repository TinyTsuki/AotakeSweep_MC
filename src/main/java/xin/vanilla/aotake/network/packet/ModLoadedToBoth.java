package xin.vanilla.aotake.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.AotakePacket;
import xin.vanilla.aotake.util.AotakeUtils;

public record ModLoadedToBoth() implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createIdentifier("client_loaded");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        return buf;
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
