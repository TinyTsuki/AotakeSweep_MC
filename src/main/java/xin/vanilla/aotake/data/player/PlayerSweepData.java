package xin.vanilla.aotake.data.player;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家数据
 */
public class PlayerSweepData implements IPlayerSweepData {
    /**
     * 是否已发送使用说明
     */
    private boolean notified;
    /**
     * 是否显示清理结果
     */
    private boolean showSweepResult = true;

    @Override
    public boolean isNotified() {
        return this.notified;
    }

    @Override
    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    @Override
    public boolean isShowSweepResult() {
        return this.showSweepResult;
    }

    @Override
    public void setShowSweepResult(boolean showSweepResult) {
        this.showSweepResult = showSweepResult;
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.notified);
        buffer.writeBoolean(this.showSweepResult);
    }

    public void readFromBuffer(FriendlyByteBuf buffer) {
        this.notified = buffer.readBoolean();
        this.showSweepResult = buffer.readBoolean();
    }

    public void copyFrom(IPlayerSweepData capability) {
        this.notified = capability.isNotified();
        this.showSweepResult = capability.isShowSweepResult();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registryAccess) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("notified", this.notified);
        tag.putBoolean("showSweepResult", this.showSweepResult);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registryAccess, CompoundTag nbt) {
        this.notified = nbt.getBoolean("notified");
        this.showSweepResult = nbt.getBoolean("showSweepResult");
    }

    @Override
    public void save(ServerPlayer player) {
        player.getCapability(PlayerSweepDataCapability.PLAYER_DATA).ifPresent(this::copyFrom);
    }
}
