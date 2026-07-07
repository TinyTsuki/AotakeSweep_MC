package xin.vanilla.aotake.data.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.banira.common.api.ICommandNotify;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.player.IPlayerData;
import xin.vanilla.banira.common.util.BaniraServerUtils;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 玩家数据
 */
public final class PlayerSweepData implements IPlayerData<PlayerSweepData>, ICommandNotify {

    // region override

    private static final Map<UUID, PlayerSweepData> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private final Player player;
    private boolean dirty = false;

    private PlayerSweepData(Player player) {
        this.player = player;
        if (this.player instanceof ServerPlayer) {
            this.deserializeNBT(BaniraServerUtils.playerDataManager().getOrCreate(player.getUUID(), AotakeSweep.MODID).copy(), false);
        }
    }

    /**
     * 获取或创建 PlayerSweepData
     */
    public static PlayerSweepData getData(Player player) {
        return CACHE.computeIfAbsent(player.getUUID(), k -> new PlayerSweepData(player));
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void setDirty() {
        this.dirty = true;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * 将数据写到网络包
     */
    @Override
    public void writeToBuffer(BaniraPacketBuffer buffer) {
        buffer.writeBoolean(isNotified());
        buffer.writeBoolean(isShowSweepResult());
        buffer.writeBoolean(isEnableWarningVoice());
    }

    /**
     * 从网络包读数据
     */
    @Override
    public void readFromBuffer(BaniraPacketBuffer buffer) {
        this.notified = buffer.readBoolean();
        this.showSweepResult = buffer.readBoolean();
        this.enableWarningVoice = buffer.readBoolean();

        this.save();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("notified", this.isNotified());
        tag.putBoolean("showSweepResult", this.isShowSweepResult());
        tag.putBoolean("enableWarningVoice", this.isEnableWarningVoice());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt, boolean dirty) {
        this.notified = nbt.getBoolean("notified");
        // 默认显示
        this.showSweepResult = !nbt.contains("showSweepResult") || nbt.getBoolean("showSweepResult");
        // 默认启用
        this.enableWarningVoice = !nbt.contains("enableWarningVoice") || nbt.getBoolean("enableWarningVoice");
        if (dirty) {
            this.save();
        }
    }

    /**
     * 复制来自同类型实例的数据
     */
    @Override
    public void copyFrom(PlayerSweepData playerData) {
        if (playerData == null) return;

        this.notified = playerData.isNotified();
        this.showSweepResult = playerData.isShowSweepResult();
        this.enableWarningVoice = playerData.isEnableWarningVoice();

        this.save();
    }

    @Override
    public void save() {
        if (this.player instanceof ServerPlayer) {
            BaniraServerUtils.playerDataManager().put(player.getUUID(), AotakeSweep.MODID, serializeNBT());
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    // endregion override

    private boolean notified;
    private boolean showSweepResult;
    private boolean enableWarningVoice;

    /**
     * 是否已发送使用说明
     */
    public boolean isNotified() {
        if (this.isDirty()) this.saveEx();
        return this.notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
        this.save();
    }

    /**
     * 是否显示清理结果
     */
    public boolean isShowSweepResult() {
        if (this.isDirty()) this.saveEx();
        return this.showSweepResult;
    }

    public void setShowSweepResult(boolean showSweepResult) {
        this.showSweepResult = showSweepResult;
        this.save();
    }

    public boolean isEnableWarningVoice() {
        if (this.isDirty()) this.saveEx();
        return this.enableWarningVoice;
    }

    public void setEnableWarningVoice(boolean enableWarningVoice) {
        this.enableWarningVoice = enableWarningVoice;
        this.save();
    }

}
