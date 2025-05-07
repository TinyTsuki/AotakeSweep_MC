package xin.vanilla.aotake.network;

import lombok.Data;
import net.minecraft.network.PacketBuffer;
import xin.vanilla.aotake.AotakeSweep;

import java.util.*;
import java.util.stream.Collectors;

@Data
public abstract class SplitPacket {
    /**
     * 分包ID
     */
    private String id;
    /**
     * 总包数
     */
    private int total;
    /**
     * 当前包序号
     */
    private int sort;

    protected SplitPacket() {
        this.id = String.format("%d.%d", System.currentTimeMillis(), new Random().nextInt(999999999));
    }

    protected SplitPacket(PacketBuffer buf) {
        this.id = buf.readUtf();
        this.total = buf.readInt();
        this.sort = buf.readInt();
    }

    public static <T extends SplitPacket> List<T> handle(T packet) {
        List<T> result = new ArrayList<>();
        Map<String, List<? extends SplitPacket>> packetCache = AotakeSweep.getPacketCache();
        // 确保键存在，并初始化为空列表
        @SuppressWarnings("unchecked")
        List<T> splitPackets = (List<T>) packetCache.computeIfAbsent(packet.getId(), k -> new ArrayList<>());
        splitPackets.add(packet);
        if (splitPackets.size() == packet.getTotal()) {
            // 对列表进行排序
            result = splitPackets.stream()
                    .sorted(Comparator.comparingInt(SplitPacket::getSort))
                    .collect(Collectors.toList());
            // 清理缓存
            packetCache.remove(packet.getId());
            //  清理过时缓存(超过5分钟)
            AotakeSweep.getPacketCache().keySet().stream()
                    .filter(key -> Math.abs(System.currentTimeMillis() - Long.parseLong(key.split("\\.")[0])) > 1000 * 60 * 5)
                    .forEach(packetCache::remove);
        }
        return result;
    }

    protected void toBytes(PacketBuffer buf) {
        buf.writeUtf(id);
        buf.writeInt(total);
        buf.writeInt(sort);
    }

    public abstract int getChunkSize();
}
