package xin.vanilla.aotake.data.player;

import lombok.NonNull;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;

/**
 * 玩家传送数据
 */
public interface IPlayerSweepData extends INBTSerializable<CompoundNBT> {

    /**
     * 获取语言
     */
    String getLanguage();

    /**
     * 设置语言
     */
    void setLanguage(String language);

    /**
     * 获取有效的语言
     */
    @NonNull
    String getValidLanguage(@Nullable PlayerEntity player);

    boolean isNotified();

    void setNotified(boolean notified);

    boolean isShowSweepResult();

    void setShowSweepResult(boolean showSweepResult);

    void writeToBuffer(PacketBuffer buffer);

    void readFromBuffer(PacketBuffer buffer);

    void copyFrom(IPlayerSweepData capability);

    void save(ServerPlayerEntity player);
}
