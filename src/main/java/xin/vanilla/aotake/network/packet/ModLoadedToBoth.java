package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

public record ModLoadedToBoth() {

    public ModLoadedToBoth(FriendlyByteBuf ignore) {
        this();
    }

    public void toBytes(FriendlyByteBuf ignore) {
    }

    public static void handle(ModLoadedToBoth ignore, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                AotakeSweep.getCustomConfigStatus().add(AotakeUtils.getPlayerUUIDString(player));
                // 同步自定义配置到客户端
                AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
                // 同步清理时间到客户端
                AotakeUtils.sendPacketToPlayer(new SweepTimeSyncToClient(), player);
                // 刷新权限信息
                AotakeUtils.refreshPermission(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
