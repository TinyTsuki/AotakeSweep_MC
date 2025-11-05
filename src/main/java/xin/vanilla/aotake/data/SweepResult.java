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
