package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;

/**
 * 玩家数据
 */
public interface IPlayerData<T extends IPlayerData<T>> {

    boolean isDirty();

    void setDirty();

    void setDirty(boolean dirty);

    void writeToBuffer(PacketBuffer buffer);

    void readFromBuffer(PacketBuffer buffer);

    CompoundNBT serializeNBT();

    void deserializeNBT(CompoundNBT nbt, boolean dirty);

    void copyFrom(T playerData);

    void save();

    default void saveEx() {
        if (this.isDirty()) {
            this.setDirty(false);
            this.save();
        }
    }

}
