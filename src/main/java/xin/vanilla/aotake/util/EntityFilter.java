package xin.vanilla.aotake.util;

import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.UsernameCache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityFilter {

    // 缓存已解析的 filter spec（key 为 convertExpression 后的最终字符串）
    private final Map<String, FilterSpec> filterCache = new ConcurrentHashMap<>();
    // 缓存已解析的 EntityDataAccessor
    private static final Map<String, EntityDataAccessor<?>> accessorCache = new ConcurrentHashMap<>();

    public void clear() {
        filterCache.clear();
    }

    public boolean validEntity(List<? extends String> config, Entity entity) {
        if (CollectionUtils.isNullOrEmpty(config)) return false;

        Map<String, Object> vars = new HashMap<>(16);

        for (String raw : config) {
            String fullKey = convertExpression(raw);
            FilterSpec spec = filterCache.computeIfAbsent(fullKey, this::compileSpec);

            vars.clear();
            fillVarsForEntity(spec.varDescriptors, entity, vars);

            if (spec.evaluator.evaluateBoolean(vars)) {
                return true;
            }
        }
        return false;
    }

    private FilterSpec compileSpec(String fullKey) {
        // split at first unescaped '->'
        String[] parts = splitUnescaped(fullKey, "->", 1);
        String left = parts.length > 0 ? parts[0].trim() : "";
        String expr = parts.length > 1 ? parts[1].trim() : "";

        // parse left into VarDescriptor
        List<VarDescriptor> descriptors = parseLeftVariables(left);

        // construct evaluator
        SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator(expr);

        return new FilterSpec(fullKey, evaluator, descriptors);
    }

    /**
     * FilterSpec: 保存编译好的 evaluator 与变量描述（节省每次 parse）
     */
    private record FilterSpec(String key, SafeExpressionEvaluator evaluator, List<VarDescriptor> varDescriptors) {
        private FilterSpec(String key, SafeExpressionEvaluator evaluator, List<VarDescriptor> varDescriptors) {
            this.key = key;
            this.evaluator = evaluator;
            this.varDescriptors = Collections.unmodifiableList(varDescriptors);
        }
    }

    /**
     * VarDescriptor 描述左侧一个变量的来源（预定义 / 字面 / NBT path）
     *
     * @param name    变量名（左侧的 key）
     * @param type    来源类型
     * @param payload LITERAL: literal value (string), NBT: nbt path, PREDEF: token like "namespace"
     */
    private record VarDescriptor(String name, SourceType type, String payload) {

        public String toString() {
            return "Var{" + name + "," + type + "," + payload + "}";
        }
    }

    private enum SourceType {PREDEFINED, LITERAL, ACCESSOR_KEY, NBT_PATH}

    private List<VarDescriptor> parseLeftVariables(String left) {
        if (left == null || left.trim().isEmpty()) return Collections.emptyList();

        List<VarDescriptor> out = new ArrayList<>();
        // split by unescaped comma
        for (String rawVar : splitUnescaped(left, ",", 0)) {
            String var = rawVar.trim();
            if (var.isEmpty()) continue;

            // 支持形如 "k = v" 的语法（v 可能是字符串字面量或 nbt 路径）
            String[] kv = splitUnescaped(var, "=", 1);
            String name = kv[0].trim();
            if (kv.length == 2) {
                String rhs = kv[1].trim();
                // 字面量（单/双引号）
                if ((rhs.startsWith("'") && rhs.endsWith("'")) || (rhs.startsWith("\"") && rhs.endsWith("\""))) {
                    String literal = rhs.substring(1, rhs.length() - 1);
                    out.add(new VarDescriptor(name, SourceType.LITERAL, literal));
                }
                // EntityDataAccessor 路径
                else if (rhs.startsWith("<") && rhs.endsWith(">")) {
                    String accessorPath = rhs.substring(1, rhs.length() - 1);
                    out.add(new VarDescriptor(name, SourceType.ACCESSOR_KEY, accessorPath));
                }
                // NBT路径
                else {
                    if (rhs.startsWith("[") && rhs.endsWith("]")) {
                        rhs = rhs.substring(1, rhs.length() - 1);
                    }
                    out.add(new VarDescriptor(name, SourceType.NBT_PATH, rhs));
                }
            }
            // 预定义变量名称（如 namespace, path, clazz 等）
            else {
                out.add(new VarDescriptor(name, SourceType.PREDEFINED, name));
            }
        }
        return out;
    }

    private void fillVarsForEntity(List<VarDescriptor> descriptors, Entity entity, Map<String, Object> varsOut) {
        if (descriptors == null || descriptors.isEmpty()) return;

        // cache common computed values
        String entityType = null;
        String namespace = null;
        String path = null;
        String resourceLocation = null;
        Class<?> clazz = null;
        String clazzString = null;
        final Class<?> itemClazz = ItemEntity.class;
        final String itemClazzString = itemClazz.getName();
        String name = null;
        String displayName = null;
        String customName = null;
        Integer tick = null;
        Integer num = null;
        String dim = null;
        Double x = null, y = null, z = null;
        Integer chunkX = null, chunkZ = null;
        Boolean hasOwner = null;
        UUID ownerUUID = null;
        String ownerName = null;

        for (VarDescriptor d : descriptors) {
            String key = d.name;
            switch (d.type) {
                case LITERAL:
                    varsOut.put(key, d.payload);
                    break;
                case ACCESSOR_KEY:
                    EntityDataAccessor<?> accessor = accessorCache.computeIfAbsent(d.payload, k -> {
                        String[] split = k.split(":", 2);
                        EntityDataAccessor<?> result;
                        if (split.length == 1) {
                            result = (EntityDataAccessor<?>) FieldUtils.getPrivateFieldValue(FieldUtils.getClass(entity), entity, split[0], true);
                        } else {
                            result = (EntityDataAccessor<?>) FieldUtils.getPrivateFieldValue(FieldUtils.getClass(split[0]), entity, split[1]);
                        }
                        return result;
                    });
                    if (accessor != null && entity.getEntityData().hasItem(accessor)) {
                        try {
                            varsOut.put(key, entity.getEntityData().get(accessor));
                        } catch (Throwable ignored) {
                            varsOut.put(key, null);
                        }
                    } else {
                        varsOut.put(key, null);
                    }
                    break;
                case NBT_PATH:
                    if (NBTPathUtils.has(entity.getPersistentData(), d.payload)) {
                        Tag tag = NBTPathUtils.getTagByPath(entity.getPersistentData(), d.payload);
                        if (tag instanceof NumericTag) {
                            varsOut.put(key, ((NumericTag) tag).getAsNumber());
                        } else if (tag instanceof CollectionTag) {
                            varsOut.put(key, ((CollectionTag<?>) tag).toArray());
                        } else if (tag != null) {
                            varsOut.put(key, tag.getAsString());
                        } else {
                            varsOut.put(key, null);
                        }
                    } else {
                        varsOut.put(key, null);
                    }
                    break;
                case PREDEFINED:
                    switch (d.payload) {
                        case "namespace":
                            if (namespace == null) {
                                entityType = (entityType == null) ? AotakeUtils.getEntityTypeRegistryName(entity) : entityType;
                                String[] parts = entityType.split(":", 2);
                                namespace = parts.length > 0 ? parts[0] : "";
                            }
                            varsOut.put(key, namespace);
                            break;
                        case "path":
                            if (path == null) {
                                entityType = (entityType == null) ? AotakeUtils.getEntityTypeRegistryName(entity) : entityType;
                                String[] parts = entityType.split(":", 2);
                                path = parts.length > 1 ? parts[1] : "";
                            }
                            varsOut.put(key, path);
                            break;
                        case "resource":
                        case "location":
                        case "resourceLocation":
                            if (resourceLocation == null)
                                resourceLocation = (entityType == null) ? AotakeUtils.getEntityTypeRegistryName(entity) : entityType;
                            varsOut.put(key, resourceLocation);
                            break;
                        case "clazz":
                            if (clazz == null) clazz = entity.getClass();
                            varsOut.put(key, clazz);
                            break;
                        case "clazzString":
                            if (clazzString == null) clazzString = entity.getClass().getName();
                            varsOut.put(key, clazzString);
                            break;
                        case "itemClazz":
                            varsOut.put(key, itemClazz);
                            break;
                        case "itemClazzString":
                            varsOut.put(key, itemClazzString);
                            break;
                        case "name":
                            if (name == null) name = entity.getName().getString();
                            varsOut.put(key, name);
                            break;
                        case "displayName":
                            if (displayName == null) displayName = entity.getDisplayName().getString();
                            varsOut.put(key, displayName);
                            break;
                        case "customName":
                            if (customName == null)
                                customName = entity.getCustomName() == null ? null : entity.getCustomName().getString();
                            varsOut.put(key, customName);
                            break;
                        case "tick":
                            if (tick == null) tick = entity.tickCount;
                            varsOut.put(key, tick);
                            break;
                        case "num":
                            if (num == null) {
                                if (entity instanceof ItemEntity) num = ((ItemEntity) entity).getItem().getCount();
                                else num = 1;
                            }
                            varsOut.put(key, num);
                            break;
                        case "dim":
                        case "dimension":
                            if (dim == null) dim = AotakeUtils.getDimensionRegistryName(entity.level());
                            varsOut.put(key, dim);
                            break;
                        case "x":
                            if (x == null) x = entity.getX();
                            varsOut.put(key, x);
                            break;
                        case "y":
                            if (y == null) y = entity.getY();
                            varsOut.put(key, y);
                            break;
                        case "z":
                            if (z == null) z = entity.getZ();
                            varsOut.put(key, z);
                            break;
                        case "chunkX":
                            if (chunkX == null) chunkX = ((int) entity.getX()) >> 4;
                            varsOut.put(key, chunkX);
                            break;
                        case "chunkZ":
                            if (chunkZ == null) chunkZ = ((int) entity.getZ()) >> 4;
                            varsOut.put(key, chunkZ);
                            break;
                        case "hasOwner":
                            if (hasOwner == null) {
                                hasOwner = entity instanceof TamableAnimal && ((TamableAnimal) entity).getOwnerUUID() != null;
                            }
                            varsOut.put(key, hasOwner);
                            break;
                        case "ownerName":
                            if (ownerName == null) {
                                if (entity instanceof TamableAnimal) {
                                    ownerUUID = ((TamableAnimal) entity).getOwnerUUID();
                                }
                                if (ownerUUID != null) ownerName = UsernameCache.getLastKnownUsername(ownerUUID);
                            }
                            varsOut.put(key, ownerName);
                            break;
                        default:
                            varsOut.put(key, null);
                    }
                    break;
            }
        }
    }

    /**
     * 将字符串按未转义的分隔符拆分。limit==1 表示最多拆成两段等同于 split(...,2)
     *
     * @param s     输入字符串
     * @param sep   分隔符（短串），例如 "->" 或 "," 或 "="
     * @param limit 最多拆分多少次（0 表示不限）
     */
    private static String[] splitUnescaped(String s, String sep, int limit) {
        if (s == null) return new String[]{};
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (limit > 0 && parts.size() >= limit) {
                cur.append(s.substring(i));
                break;
            }
            if (s.startsWith(sep, i)) {
                int bs = 0;
                int j = i - 1;
                while (j >= 0 && s.charAt(j) == '\\') {
                    bs++;
                    j--;
                }
                if (bs % 2 == 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                    i += sep.length();
                    continue;
                }
            }
            cur.append(s.charAt(i++));
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    /**
     * 将类似 "minecraft:cow" 自动转换为 "namespace, path -> namespace == 'minecraft' && path == 'cow'"
     */
    private String convertExpression(String s) {
        if (s == null) return "";
        if (splitUnescaped(s, "->", 1).length > 1) return s;

        if (s.contains(":")) {
            String[] kv = s.split(":", 2);
            if (kv.length == 2) {
                String ns = kv[0];
                String path = kv[1];
                String left = "namespace, path";
                StringBuilder expr = new StringBuilder();
                if ("*".equals(ns)) {
                    expr.append("namespace == namespace");
                } else {
                    expr.append(String.format("namespace == '%s'", escapeSingleQuotes(ns)));
                }
                expr.append(" && ");
                if ("*".equals(path)) {
                    expr.append("path == path");
                } else {
                    expr.append(String.format("path == '%s'", escapeSingleQuotes(path)));
                }
                return left + " -> " + expr;
            }
        }
        return s;
    }

    private static String escapeSingleQuotes(String s) {
        return s.replace("'", "\\'");
    }

}
