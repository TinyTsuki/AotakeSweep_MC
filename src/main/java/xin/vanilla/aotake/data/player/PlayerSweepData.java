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
    /**
     * 是否已发送使用说明
     */
    private boolean notified;
    private String language = "client";

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

    public void writeToBuffer(PacketBuffer buffer) {
        buffer.writeBoolean(this.notified);
        buffer.writeUtf(this.getLanguage());
    }

    public void readFromBuffer(PacketBuffer buffer) {
        this.notified = buffer.readBoolean();
        this.language = buffer.readUtf();
    }

    public void copyFrom(IPlayerSweepData capability) {
        this.notified = capability.isNotified();
        this.language = capability.getLanguage();
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.putBoolean("notified", this.notified);
        tag.putString("language", this.getLanguage());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        this.notified = nbt.getBoolean("notified");
        this.setLanguage(nbt.getString("language"));
    }

    @Override
    public void save(ServerPlayerEntity player) {
        player.getCapability(PlayerSweepDataCapability.PLAYER_DATA).ifPresent(this::copyFrom);
    }
}
