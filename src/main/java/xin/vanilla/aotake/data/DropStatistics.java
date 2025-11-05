package xin.vanilla.aotake.data;

import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;

@Accessors(chain = true)
public record DropStatistics(WorldCoordinate coordinate, String name, long time, long itemCount, long entityCount) {
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("coordinate", coordinate.writeToNBT());
        tag.putString("name", name);
        tag.putLong("time", time);
        tag.putLong("itemCount", itemCount);
        tag.putLong("entityCount", entityCount);
        return tag;
    }

    public static DropStatistics deserializeNBT(CompoundTag tag) {
        return new DropStatistics(
                WorldCoordinate.readFromNBT(tag.getCompound("coordinate"))
                , tag.getString("name")
                , tag.getLong("time")
                , tag.getLong("itemCount")
                , tag.getLong("entityCount")
        );
    }
}
