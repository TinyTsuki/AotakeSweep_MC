package xin.vanilla.aotake.data.player;

import lombok.NonNull;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nullable;

/**
 * 玩家传送数据
 */
@AutoRegisterCapability
public interface IPlayerSweepData extends INBTSerializable<CompoundTag> {

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
    String getValidLanguage(@Nullable Player player);

    boolean isNotified();

    void setNotified(boolean notified);

    boolean isShowSweepResult();

    void setShowSweepResult(boolean showSweepResult);

    void writeToBuffer(FriendlyByteBuf buffer);

    void readFromBuffer(FriendlyByteBuf buffer);

    void copyFrom(IPlayerSweepData capability);

    void save(ServerPlayer player);
}
