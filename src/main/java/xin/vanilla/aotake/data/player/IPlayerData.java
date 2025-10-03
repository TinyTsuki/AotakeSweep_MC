package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 玩家数据
 */
public interface IPlayerData<T extends IPlayerData<T>> {

    boolean isDirty();

    void setDirty();

    void setDirty(boolean dirty);

    void writeToBuffer(FriendlyByteBuf buffer);

    void readFromBuffer(FriendlyByteBuf buffer);

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag nbt, boolean dirty);

    void copyFrom(T playerData);

    void save();

    default void saveEx() {
        if (this.isDirty()) {
            this.setDirty(false);
            this.save();
        }
    }

}
