package xin.vanilla.aotake.data;

import com.google.gson.JsonObject;
import com.mojang.math.Vector3d;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WorldCoordinate implements Serializable, Cloneable {
    private double x = 0;
    private double y = 0;
    private double z = 0;
    private double yaw = 0;
    private double pitch = 0;
    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private Direction direction = null;

    public WorldCoordinate(@NonNull Entity entity) {
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
        this.yaw = entity.getYRot();
        this.pitch = entity.getXRot();
        this.dimension = entity.level.dimension();
    }

    public WorldCoordinate(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public WorldCoordinate(double x, double y, double z, ResourceKey<Level> dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
    }

    public WorldCoordinate(double x, double y, double z, double yaw, double pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public WorldCoordinate(double x, double y, double z, double yaw, double pitch, ResourceKey<Level> dimension) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dimension = dimension;
    }

    public int getXInt() {
        return (int) x;
    }

    public int getYInt() {
        return (int) y;
    }

    public int getZInt() {
        return (int) z;
    }

    /**
     * 根据距离和权重生成随机数
     *
     * @param a 最小值
     * @param b 最大值
     * @param c 中心值
     * @param k 权重系数
     * @return 随机数
     */
    public static int getRandomWithWeight(int a, int b, int c, double k) {
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0;
        // 计算每个值的权重
        for (int i = a; i <= b; i++) {
            double weight = 1.0 / (1 + k * Math.abs(i - c));
            weights.add(weight);
            totalWeight += weight;
        }
        // 生成随机数并选中对应的值
        double rand = new Random().nextDouble() * totalWeight;
        double cumulativeWeight = 0;
        for (int i = 0; i < weights.size(); i++) {
            cumulativeWeight += weights.get(i);
            if (rand <= cumulativeWeight) {
                return a + i;
            }
        }
        // 默认返回最小值（理论上不会执行到这里）
        return a;
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public Vector3d toVector3d() {
        return new Vector3d(x, y, z);
    }

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }

    public WorldCoordinate fromBlockPos(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        return this;
    }

    public WorldCoordinate fromVector3d(Vector3d pos) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        return this;
    }

    public WorldCoordinate addX(double x) {
        this.x += x;
        return this;
    }

    public WorldCoordinate addY(double y) {
        this.y += y;
        return this;
    }

    public WorldCoordinate addZ(double z) {
        this.z += z;
        return this;
    }

    /**
     * 序列化到 NBT
     */
    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putDouble("yaw", yaw);
        tag.putDouble("pitch", pitch);
        tag.putString("dimension", dimension.location().toString());
        return tag;
    }

    /**
     * 反序列化
     */
    public static WorldCoordinate readFromNBT(CompoundTag tag) {
        WorldCoordinate coordinate = new WorldCoordinate();
        coordinate.x = tag.getDouble("x");
        coordinate.y = tag.getDouble("y");
        coordinate.z = tag.getDouble("z");
        coordinate.yaw = tag.getDouble("yaw");
        coordinate.pitch = tag.getDouble("pitch");
        coordinate.dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, AotakeSweep.parseIdentifier(tag.getString("dimension")));
        return coordinate;
    }

    /**
     * 序列化到JsonString
     */
    public String toJsonString() {
        JsonObject json = new JsonObject();
        JsonUtils.set(json, "x", x);
        JsonUtils.set(json, "y", y);
        JsonUtils.set(json, "z", z);
        JsonUtils.set(json, "yaw", yaw);
        JsonUtils.set(json, "pitch", pitch);
        JsonUtils.set(json, "dimension", dimension.location().toString());
        return json.toString();
    }

    /**
     * 从JsonString反序列化
     */
    public static WorldCoordinate fromJsonString(String jsonString) {
        JsonObject json = JsonUtils.GSON.fromJson(jsonString, JsonObject.class);
        WorldCoordinate coordinate = new WorldCoordinate();
        coordinate.x = JsonUtils.getDouble(json, "x", 0);
        coordinate.y = JsonUtils.getDouble(json, "y", 0);
        coordinate.z = JsonUtils.getDouble(json, "z", 0);
        coordinate.yaw = JsonUtils.getDouble(json, "yaw", 0);
        coordinate.pitch = JsonUtils.getDouble(json, "pitch", 0);
        String dimensionStr = JsonUtils.getString(json, "dimension", Level.OVERWORLD.location().toString());
        coordinate.dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, AotakeSweep.parseIdentifier(dimensionStr));
        return coordinate;
    }

    @Override
    public WorldCoordinate clone() {
        try {
            WorldCoordinate cloned = (WorldCoordinate) super.clone();
            cloned.dimension = this.dimension;
            cloned.x = this.x;
            cloned.y = this.y;
            cloned.z = this.z;
            cloned.yaw = this.yaw;
            cloned.pitch = this.pitch;
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public WorldCoordinate above() {
        return this.clone().addY(1);
    }

    public WorldCoordinate below() {
        return this.clone().addY(-1);
    }

    public double distanceFrom(WorldCoordinate coordinate) {
        return Math.sqrt(Math.pow(coordinate.x - x, 2) + Math.pow(coordinate.y - y, 2) + Math.pow(coordinate.z - z, 2));
    }

    public double distanceFrom(double x, double y, double z) {
        return Math.sqrt(Math.pow(x - this.x, 2) + Math.pow(y - this.y, 2) + Math.pow(z - this.z, 2));
    }

    public double distanceFrom2D(WorldCoordinate coordinate) {
        return Math.sqrt(Math.pow(coordinate.x - x, 2) + Math.pow(coordinate.z - z, 2));
    }

    public String toXString() {
        return StringUtils.toFixedEx(x, 1);
    }

    public String toYString() {
        return StringUtils.toFixedEx(y, 1);
    }

    public String toZString() {
        return StringUtils.toFixedEx(z, 1);
    }

    public String toXyzString() {
        return StringUtils.toFixedEx(x, 1) + ", " + StringUtils.toFixedEx(y, 1) + ", " + StringUtils.toFixedEx(z, 1);
    }

    public String toChunkXZString() {
        return String.format("%d,%d", this.getXInt() >> 4, this.getZInt() >> 4);
    }

    public String getDimensionResourceId() {
        return dimension.location().toString();
    }

    public boolean equalsOfRange(WorldCoordinate coordinate, int range) {
        return Math.abs((int) coordinate.x - (int) x) <= range
                && Math.abs((int) coordinate.y - (int) y) <= range
                && Math.abs((int) coordinate.z - (int) z) <= range
                && coordinate.dimension.equals(dimension);
    }

    public static WorldCoordinate fromSimpleString(String str) {
        WorldCoordinate result = null;
        try {
            String[] split = str.split(",");
            if (split.length == 5) {
                ResourceKey<Level> dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, AotakeSweep.parseIdentifier(split[0].trim()));
                Direction direction = valuOfDirection(split[4].trim());
                result = new WorldCoordinate(StringUtils.toDouble(split[1]), StringUtils.toDouble(split[2]), StringUtils.toDouble(split[3]), dimension).setDirection(direction);
            } else if (split.length == 4) {
                if (split[0].contains(":")) {
                    ResourceKey<Level> dimension = ResourceKey.create(Registry.DIMENSION_REGISTRY, AotakeSweep.parseIdentifier(split[0].trim()));
                    result = new WorldCoordinate(StringUtils.toDouble(split[1]), StringUtils.toDouble(split[2]), StringUtils.toDouble(split[3]), dimension);
                } else if (Arrays.stream(Direction.values()).anyMatch(dir -> dir.getName().equals(split[3].trim()))) {
                    Direction direction = valuOfDirection(split[3].trim());
                    result = new WorldCoordinate(StringUtils.toDouble(split[0]), StringUtils.toDouble(split[1]), StringUtils.toDouble(split[2])).setDirection(direction);
                }
            } else if (split.length == 3) {
                result = new WorldCoordinate(StringUtils.toDouble(split[0]), StringUtils.toDouble(split[1]), StringUtils.toDouble(split[2]));
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static Direction valuOfDirection(String str) {
        try {
            return Arrays.stream(Direction.values())
                    .filter(dir -> dir.getName().equalsIgnoreCase(str) || dir.name().equalsIgnoreCase(str))
                    .findFirst().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
