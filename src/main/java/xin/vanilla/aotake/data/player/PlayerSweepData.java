package xin.vanilla.aotake.data.player;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.util.AotakeUtils;

import javax.annotation.Nullable;

/**
 * 玩家传送数据
 */
@Setter
@Getter
public class PlayerSweepData implements INBTSerializable<CompoundTag> {
    private String language = "client";
    /**
     * 是否已发送使用说明
     */
    private boolean notified;
    /**
     * 是否显示清理结果
     */
    private boolean showSweepResult = true;

    @NonNull
    public String getValidLanguage(@Nullable Player player) {
        return AotakeUtils.getValidLanguage(player, this.getLanguage());
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.getLanguage());
        buffer.writeBoolean(this.notified);
        buffer.writeBoolean(this.showSweepResult);
    }

    public void readFromBuffer(FriendlyByteBuf buffer) {
        this.language = buffer.readUtf();
        this.notified = buffer.readBoolean();
        this.showSweepResult = buffer.readBoolean();
    }

    public void copyFrom(PlayerSweepData capability) {
        this.language = capability.getLanguage();
        this.notified = capability.isNotified();
        this.showSweepResult = capability.isShowSweepResult();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("language", this.getLanguage());
        tag.putBoolean("notified", this.notified);
        tag.putBoolean("showSweepResult", this.showSweepResult);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, CompoundTag nbt) {
        this.setLanguage(nbt.getString("language"));
        this.notified = nbt.getBoolean("notified");
        this.showSweepResult = nbt.getBoolean("showSweepResult");
    }

}
