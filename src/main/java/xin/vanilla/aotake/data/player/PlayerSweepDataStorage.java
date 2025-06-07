package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;

/**
 * 玩家数据存储类
 */
public class PlayerSweepDataStorage implements IStorage<IPlayerSweepData> {

    /**
     * 将玩家数据写入NBT标签
     *
     * @param capability 能力对象
     * @param instance   玩家数据实例
     * @param side       侧边标识，用于指定数据交换的方向
     */
    @Override
    public CompoundNBT writeNBT(Capability<IPlayerSweepData> capability, IPlayerSweepData instance, Direction side) {
        if (instance == null) {
            return new CompoundNBT();
        }
        CompoundNBT tag = new CompoundNBT();
        tag.putBoolean("notified", instance.isNotified());
        tag.putBoolean("showSweepResult", instance.isShowSweepResult());
        return tag;
    }

    /**
     * 从NBT标签读取玩家数据
     *
     * @param capability 能力对象
     * @param instance   玩家数据实例
     * @param side       侧边标识，用于指定数据交换的方向
     * @param nbt        包含玩家数据的NBT标签
     */
    @Override
    public void readNBT(Capability<IPlayerSweepData> capability, IPlayerSweepData instance, Direction side, INBT nbt) {
        if (nbt instanceof CompoundNBT) {
            CompoundNBT nbtTag = (CompoundNBT) nbt;
            instance.setNotified(nbtTag.getBoolean("notified"));
            instance.setShowSweepResult(nbtTag.getBoolean("showSweepResult"));
        }
    }
}
