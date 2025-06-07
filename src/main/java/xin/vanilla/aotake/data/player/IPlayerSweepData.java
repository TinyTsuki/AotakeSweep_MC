package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 玩家数据
 */
public interface IPlayerSweepData extends INBTSerializable<CompoundTag> {

    boolean isNotified();

    void setNotified(boolean notified);

    boolean isShowSweepResult();

    void setShowSweepResult(boolean showSweepResult);

    void writeToBuffer(FriendlyByteBuf buffer);

    void readFromBuffer(FriendlyByteBuf buffer);

    void copyFrom(IPlayerSweepData capability);

    void save(ServerPlayer player);
}
