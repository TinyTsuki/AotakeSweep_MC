package xin.vanilla.aotake.data.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 玩家数据
 */
public final class PlayerSweepData implements IPlayerData<PlayerSweepData> {

    private static final Map<UUID, PlayerSweepData> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final PlayerEntity player;

    private PlayerSweepData(PlayerEntity player) {
        this.player = player;
    }

    /**
     * 获取或创建 PlayerSweepData
     */
    public static PlayerSweepData getData(PlayerEntity player) {
        return CACHE.computeIfAbsent(player.getUUID(), k -> new PlayerSweepData(player));
    }

    /**
     * 是否已发送使用说明
     */
    public boolean isNotified() {
        return PlayerDataStorage.instance().getOrCreate(player).getBoolean("notified");
    }

    public void setNotified(boolean notified) {
        PlayerDataStorage.instance().getOrCreate(player).putBoolean("notified", notified);
    }

    /**
     * 是否显示清理结果
     */
    public boolean isShowSweepResult() {
        return PlayerDataStorage.instance().getOrCreate(player).getBoolean("showSweepResult");
    }

    public void setShowSweepResult(boolean showSweepResult) {
        PlayerDataStorage.instance().getOrCreate(player).putBoolean("showSweepResult", showSweepResult);
    }

    /**
     * 将数据写到网络包
     */
    @Override
    public void writeToBuffer(PacketBuffer buffer) {
        buffer.writeBoolean(isNotified());
        buffer.writeBoolean(isShowSweepResult());
    }

    /**
     * 从网络包读数据
     */
    @Override
    public void readFromBuffer(PacketBuffer buffer) {
        this.setNotified(buffer.readBoolean());
        this.setShowSweepResult(buffer.readBoolean());
    }

    /**
     * 复制来自同类型实例的数据
     */
    @Override
    public void copyFrom(PlayerSweepData playerData) {
        this.setNotified(playerData.isNotified());
        this.setShowSweepResult(playerData.isShowSweepResult());
    }

    /**
     * 保存
     */
    @Override
    public void save(ServerPlayerEntity player) {
        PlayerDataStorage.instance().saveToDisk(player);
    }

}
