package xin.vanilla.aotake.data.player;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家数据
 */
@Setter
@Getter
public class PlayerSweepData implements INBTSerializable<CompoundTag> {
    /**
     * 是否已发送使用说明
     */
    private boolean notified;
    /**
     * 是否显示清理结果
     */
    private boolean showSweepResult = true;

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.notified);
        buffer.writeBoolean(this.showSweepResult);
    }

    public void readFromBuffer(FriendlyByteBuf buffer) {
        this.notified = buffer.readBoolean();
        this.showSweepResult = buffer.readBoolean();
    }

    public void copyFrom(PlayerSweepData capability) {
        this.notified = capability.isNotified();
        this.showSweepResult = capability.isShowSweepResult();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("notified", this.notified);
        tag.putBoolean("showSweepResult", this.showSweepResult);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, CompoundTag nbt) {
        this.notified = nbt.getBoolean("notified");
        this.showSweepResult = nbt.getBoolean("showSweepResult");
    }

}
