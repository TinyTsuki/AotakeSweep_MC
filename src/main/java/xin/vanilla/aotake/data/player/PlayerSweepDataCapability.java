package xin.vanilla.aotake.data.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.util.LazyOptional;

/**
 * 玩家能力
 */
public class PlayerSweepDataCapability {

    @CapabilityInject(IPlayerSweepData.class)
    public static Capability<IPlayerSweepData> PLAYER_DATA;

    public static void register() {
        CapabilityManager.INSTANCE.register(IPlayerSweepData.class, new PlayerSweepDataStorage(), PlayerSweepData::new);
    }

    /**
     * 获取玩家数据
     *
     * @param player 玩家实体
     * @return 玩家的数据
     */
    public static IPlayerSweepData getData(PlayerEntity player) {
        return player.getCapability(PLAYER_DATA).orElseThrow(() -> new IllegalArgumentException("Player data capability is missing."));
    }

    public static LazyOptional<IPlayerSweepData> getDataOptional(ServerPlayerEntity player) {
        return player.getCapability(PLAYER_DATA);
    }

    /**
     * 设置玩家数据
     *
     * @param player 玩家实体
     * @param data   玩家数据
     */
    public static void setData(PlayerEntity player, IPlayerSweepData data) {
        player.getCapability(PLAYER_DATA).ifPresent(capability -> capability.copyFrom(data));
    }

    /**
     * 同步玩家数据到客户端
     */
    public static void syncPlayerData(ServerPlayerEntity player) {
        // 创建自定义包并发送到客户端
        // PlayerDataSyncPacket packet = new PlayerDataSyncPacket(player.getUUID(), PlayerSweepDataCapability.getData(player));
        // for (PlayerDataSyncPacket syncPacket : packet.split()) {
        //     ModNetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), syncPacket);
        // }
    }
}
