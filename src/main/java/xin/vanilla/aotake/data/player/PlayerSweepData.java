package xin.vanilla.aotake.data.player;

import lombok.NonNull;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import xin.vanilla.aotake.util.AotakeUtils;

import javax.annotation.Nullable;

/**
 * 玩家传送数据
 */
public class PlayerSweepData implements IPlayerSweepData {
    private String language = "client";
    /**
     * 是否已发送使用说明
     */
    private boolean notified;
    /**
     * 是否显示清理结果
     */
    private boolean showSweepResult = true;

    @Override
    public String getLanguage() {
        return this.language;
    }

    @Override
    public void setLanguage(String language) {
        this.language = language;
    }

    @NonNull
    @Override
    public String getValidLanguage(@Nullable PlayerEntity player) {
        return AotakeUtils.getValidLanguage(player, this.getLanguage());
    }

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

    public void writeToBuffer(PacketBuffer buffer) {
        buffer.writeUtf(this.getLanguage());
        buffer.writeBoolean(this.notified);
        buffer.writeBoolean(this.showSweepResult);
    }

    public void readFromBuffer(PacketBuffer buffer) {
        this.language = buffer.readUtf();
        this.notified = buffer.readBoolean();
        this.showSweepResult = buffer.readBoolean();
    }

    public void copyFrom(IPlayerSweepData capability) {
        this.language = capability.getLanguage();
        this.notified = capability.isNotified();
        this.showSweepResult = capability.isShowSweepResult();
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putString("language", this.getLanguage());
        tag.putBoolean("notified", this.notified);
        tag.putBoolean("showSweepResult", this.showSweepResult);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        this.setLanguage(nbt.getString("language"));
        this.notified = nbt.getBoolean("notified");
        this.showSweepResult = nbt.getBoolean("showSweepResult");
    }

    @Override
    public void save(ServerPlayerEntity player) {
        player.getCapability(PlayerSweepDataCapability.PLAYER_DATA).ifPresent(this::copyFrom);
    }
}
