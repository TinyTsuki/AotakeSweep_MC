package xin.vanilla.aotake.data;

import com.google.gson.JsonObject;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;
import xin.vanilla.aotake.util.JsonUtils;

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
