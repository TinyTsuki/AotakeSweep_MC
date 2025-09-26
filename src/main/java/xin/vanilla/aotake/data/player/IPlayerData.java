package xin.vanilla.aotake.data.player;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;

/**
 * 玩家数据
 */
public interface IPlayerData<T extends IPlayerData<T>> {

    void writeToBuffer(PacketBuffer buffer);

    void readFromBuffer(PacketBuffer buffer);

    void copyFrom(T playerData);

    void save(ServerPlayerEntity player);

}
