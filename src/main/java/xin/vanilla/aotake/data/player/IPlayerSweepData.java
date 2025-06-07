package xin.vanilla.aotake.data.player;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 玩家数据
 */
public interface IPlayerSweepData extends INBTSerializable<CompoundNBT> {

    boolean isNotified();

    void setNotified(boolean notified);

    boolean isShowSweepResult();

    void setShowSweepResult(boolean showSweepResult);

    void writeToBuffer(PacketBuffer buffer);

    void readFromBuffer(PacketBuffer buffer);

    void copyFrom(IPlayerSweepData capability);

    void save(ServerPlayerEntity player);
}
