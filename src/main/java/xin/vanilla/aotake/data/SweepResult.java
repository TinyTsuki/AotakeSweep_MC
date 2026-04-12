package xin.vanilla.aotake.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 清理结果
 */
@Data
@Accessors(chain = true)
public class SweepResult {
    /**
     * 清理的物品数量
     */
    private long itemCount;

    /**
     * 清理的实体数量
     */
    private long entityCount;

    /**
     * 回收物品数量
     */
    private long recycledItemCount;

    /**
     * 回收实体数量
     */
    private long recycledEntityCount;

    /**
     * 当前批次
     */
    private AtomicInteger batch = new AtomicInteger(0);

    /**
     * 总批次
     */
    private long totalBatch;

    /**
     * 是否由「区块实体过多」触发的扫地（回收物进区块暂存箱）
     */
    private boolean chunkOverloadVault;

    /**
     * 本轮区块暂存写入的时间桶前缀（{@link xin.vanilla.aotake.data.world.ChunkVaultStorage#vaultTimePrefix()}），在整次扫地分批间复用，避免每个物品重复计算。
     */
    private String chunkVaultTimePrefix;

    /**
     * 本轮区块过载扫地的运行标识，与区块键组合成独立 vault 文件名，便于按次找回物品。
     */
    private String chunkVaultRunId;

    public SweepResult plusItemCount() {
        this.itemCount++;
        return this;
    }

    public SweepResult plusEntityCount() {
        this.entityCount++;
        return this;
    }

    public SweepResult plusRecycledItemCount() {
        this.recycledItemCount++;
        return this;
    }

    public SweepResult plusRecycledEntityCount() {
        this.recycledEntityCount++;
        return this;
    }

    public SweepResult plusItemCount(long count) {
        this.itemCount += count;
        return this;
    }

    public SweepResult plusEntityCount(long count) {
        this.entityCount += count;
        return this;
    }

    public SweepResult plusRecycledItemCount(long count) {
        this.recycledItemCount += count;
        return this;
    }

    public SweepResult plusRecycledEntityCount(long count) {
        this.recycledEntityCount += count;
        return this;
    }

    public synchronized SweepResult incrementBatch() {
        this.batch.incrementAndGet();
        return this;
    }

    public SweepResult add(SweepResult other) {
        this.itemCount += other.itemCount;
        this.entityCount += other.entityCount;
        this.recycledItemCount += other.recycledItemCount;
        this.recycledEntityCount += other.recycledEntityCount;
        return this;
    }

    public boolean isEmpty() {
        return this.itemCount == 0 && this.entityCount == 0;
    }
}
