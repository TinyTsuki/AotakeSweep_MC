package xin.vanilla.aotake.data;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundNBT;
import xin.vanilla.aotake.util.JsonUtils;

@Data
@Accessors(chain = true)
public class DropStatistics {
    private final WorldCoordinate coordinate;
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
                WorldCoordinate.readFromNBT(tag.getCompound("coordinate"))
                , tag.getString("name")
                , tag.getLong("time")
                , tag.getLong("itemCount")
                , tag.getLong("entityCount")
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("coordinate", JsonUtils.GSON.fromJson(coordinate.toJsonString(), JsonObject.class));
        json.addProperty("name", name);
        json.addProperty("time", time);
        json.addProperty("itemCount", itemCount);
        json.addProperty("entityCount", entityCount);
        return json;
    }

    public static DropStatistics fromJson(JsonObject json) {
        WorldCoordinate coordinate = json.has("coordinate")
                ? WorldCoordinate.fromJsonString(JsonUtils.GSON.toJson(json.getAsJsonObject("coordinate")))
                : new WorldCoordinate(0, 0, 0);
        return new DropStatistics(
                coordinate,
                JsonUtils.getString(json, "name", ""),
                json.has("time") ? json.get("time").getAsLong() : 0L,
                json.has("itemCount") ? json.get("itemCount").getAsLong() : 0L,
                json.has("entityCount") ? json.get("entityCount").getAsLong() : 0L
        );
    }
}
