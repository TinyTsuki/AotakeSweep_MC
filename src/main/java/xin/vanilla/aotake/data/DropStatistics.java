package xin.vanilla.aotake.data;

import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundNBT;

@Data
@Accessors(chain = true)
public class DropStatistics {
    private final Coordinate coordinate;
    private final String name;
    private final long time;
    private final long itemCount;
    private final long entityCount;

    public CompoundNBT serializeNBT() {
        CompoundNBT tag = new CompoundNBT();
        tag.put("coordinate", coordinate.writeToNBT());
        tag.putString("name", name);
        tag.putLong("time", time);
        tag.putLong("itemCount", itemCount);
        tag.putLong("entityCount", entityCount);
        return tag;
    }

    public static DropStatistics deserializeNBT(CompoundNBT tag) {
        return new DropStatistics(
                Coordinate.readFromNBT(tag.getCompound("coordinate"))
                , tag.getString("name")
                , tag.getLong("time")
                , tag.getLong("itemCount")
                , tag.getLong("entityCount")
        );
    }
}
