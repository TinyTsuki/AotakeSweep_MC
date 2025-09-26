package xin.vanilla.aotake.data.player;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家数据
 */
public interface IPlayerData<T extends IPlayerData<T>> {

    void writeToBuffer(FriendlyByteBuf buffer);

    void readFromBuffer(FriendlyByteBuf buffer);

    void copyFrom(T playerData);

    void save(Player player);

}
