package xin.vanilla.aotake.util;

import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.UsernameCache;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.*;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EntityFilter {

    // 缓存已解析的 filter spec（key 为 convertExpression 后的最终字符串）
    private final Map<String, FilterSpec> filterCache = new ConcurrentHashMap<>();
    // 缓存已解析的 EntityDataAccessor（无显式类名时 key 含实体类名，避免跨类型错误复用）
    private static final Map<String, EntityDataAccessor<?>> accessorCache = new ConcurrentHashMap<>();
    // 缓存已解析的 ACCESSOR_KEY 路径（key 为完整 accessorPath 字符串）
    private static final Map<String, AccessorPath> accessorPathCache = new ConcurrentHashMap<>();
    private final ThreadLocal<Map<String, Object>> variableBuffer =
            ThreadLocal.withInitial(() -> new HashMap<>(24));
    private final Matcher emptyMatcher = new Matcher(Collections.emptyList());

    public void clear() {
        filterCache.clear();
        variableBuffer.remove();
    }

    public boolean validEntity(List<? extends String> config, Entity entity) {
        return compile(config).matches(entity);
    }

    /**
     * 将一组规则预编译为可重复使用的匹配器，供单次全量扫描复用。
     */
    public Matcher compile(List<? extends String> config) {
        if (CollectionUtils.isNullOrEmpty(config)) {
            return emptyMatcher;
        }
        List<FilterSpec> specs = new ArrayList<>(config.size());
        for (String raw : config) {
            String fullKey = convertExpression(raw);
            specs.add(filterCache.computeIfAbsent(fullKey, this::compileSpec));
        }
        return new Matcher(Collections.unmodifiableList(specs));
    }

    public final class Matcher {
        private final List<FilterSpec> specs;

        private Matcher(List<FilterSpec> specs) {
            this.specs = specs;
        }

        public boolean matches(Entity entity) {
            if (specs.isEmpty() || entity == null) {
                return false;
            }
            Map<String, Object> vars = variableBuffer.get();
            try {
                for (FilterSpec spec : specs) {
                    vars.clear();
                    fillVarsForEntity(spec.varDescriptors, entity, vars);
                    if (spec.evaluator.evaluateBoolean(vars)) {
                        return true;
                    }
                }
                return false;
            } finally {
                vars.clear();
            }
        }
    }

    private FilterSpec compileSpec(String fullKey) {
        // split at first unescaped '->'
        String[] parts = splitUnescaped(fullKey, "->", 1);
        String left = parts.length > 0 ? parts[0].trim() : "";
        String expr = parts.length > 1 ? parts[1].trim() : "";

        // parse left into VarDescriptor
        List<VarDescriptor> descriptors = finalizeDescriptors(parseLeftVariables(left));

        // construct evaluator
        SafeExpressionEvaluator evaluator = new SafeExpressionEvaluator(expr);

        return new FilterSpec(fullKey, evaluator, descriptors);
    }

    /**
     * 编译期解析路径，避免每次实体判定时重复 split/parse。
     */
    private static List<VarDescriptor> finalizeDescriptors(List<VarDescriptor> raw) {
        if (raw.isEmpty()) return raw;
        List<VarDescriptor> out = new ArrayList<>(raw.size());
        for (VarDescriptor d : raw) {
            if (d.type == SourceType.ACCESSOR_KEY || d.type == SourceType.FIELD_CHAIN) {
                boolean fieldChain = d.type == SourceType.FIELD_CHAIN;
                String cacheKey = (fieldChain ? "F|" : "A|") + d.payload;
                AccessorPath ap = accessorPathCache.computeIfAbsent(cacheKey, k -> parseEntityPath(d.payload, fieldChain));
                out.add(new VarDescriptor(d.name, d.type, d.payload, ap));
            } else {
                out.add(new VarDescriptor(d.name, d.type, d.payload, null));
            }
        }
        return out;
    }

    /**
     * FilterSpec: 保存编译好的 evaluator 与变量描述（节省每次 parse）
     */
    private static class FilterSpec {
        final String key;
        final SafeExpressionEvaluator evaluator;
        final List<VarDescriptor> varDescriptors;

        FilterSpec(String key, SafeExpressionEvaluator evaluator, List<VarDescriptor> varDescriptors) {
            this.key = key;
            this.evaluator = evaluator;
            this.varDescriptors = Collections.unmodifiableList(varDescriptors);
        }
    }

    /**
     * VarDescriptor 描述左侧一个变量的来源（预定义 / 字面 / NBT path）
     */
    private static class VarDescriptor {
        final String name;               // 变量名（左侧的 key）
        final SourceType type;           // 来源类型
        final String payload;            // LITERAL: literal value (string), NBT: nbt path, PREDEF: token like "namespace"
        @Nullable
        final AccessorPath accessorPath; // ACCESSOR_KEY / FIELD_CHAIN 编译期解析结果

        VarDescriptor(String name, SourceType type, String payload, @Nullable AccessorPath accessorPath) {
            this.name = name;
            this.type = type;
            this.payload = payload;
            this.accessorPath = accessorPath;
        }

        public String toString() {
            return "Var{" + name + "," + type + "," + payload + "}";
        }
    }

    /**
     * 解析后的 {@code <>} / {@code {}} 路径（声明类 + 字段链）
     */
    private static class AccessorPath {
        @Nullable
        final String className;
        final List<String> chain;

        AccessorPath(String className, List<String> chain) {
            this.className = className;
            this.chain = chain;
        }
    }

    private enum SourceType implements IEnumDescribable {
        PREDEFINED,
        LITERAL,
        ACCESSOR_KEY,
        /**
         * 从实体（或显式声明类上的首段字段）开始的纯反射链；分隔符可为 . 与 :
         */
        FIELD_CHAIN,
        NBT_PATH,
        ;

        @Override
        public Component enumDescription() {
            return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
        }
    }

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
                    out.add(new VarDescriptor(name, SourceType.LITERAL, literal, null));
                }
                // EntityDataAccessor 路径
                else if (rhs.startsWith("<") && rhs.endsWith(">")) {
                    String accessorPath = rhs.substring(1, rhs.length() - 1);
                    out.add(new VarDescriptor(name, SourceType.ACCESSOR_KEY, accessorPath, null));
                } else if (rhs.startsWith("{") && rhs.endsWith("}")) {
                    String path = rhs.substring(1, rhs.length() - 1).trim();
                    out.add(new VarDescriptor(name, SourceType.FIELD_CHAIN, path, null));
                }
                // NBT路径
                else {
                    if (rhs.startsWith("[") && rhs.endsWith("]")) {
                        rhs = rhs.substring(1, rhs.length() - 1);
                    }
                    out.add(new VarDescriptor(name, SourceType.NBT_PATH, rhs, null));
                }
            }
            // 预定义变量名称（如 namespace, path, clazz 等）
            else {
                out.add(new VarDescriptor(name, SourceType.PREDEFINED, name, null));
            }
        }
        return out;
    }

    /**
     * 解析 {@code <>} 与 {@code {}} 路径。
     * <ul>
     *   <li><b>声明类</b>：在每个未转义的 {@code .} / {@code :} 处截断，取能 {@link Class#forName(String)} 的最长前缀为类名，其后为字段链。</li>
     *   <li><b>有声明类时</b>：字段链对 tail 使用未转义的 {@code .} 与 {@code :} 混切；{@code \\.}、{@code \\:}、{@code \\\\} 转义。</li>
     *   <li><b>无声明类</b>：{@code fieldChainMode==false}（{@code <>}）仅按未转义的 {@code :} 分段，单段时保留其中的 {@code .}（兼容
     *       {@code <foo.bar>} 单字段名）；{@code fieldChainMode==true}（{@code {}}）对整段使用 {@code .} 与 {@code :} 混切。</li>
     * </ul>
     */
    @Nullable
    private static AccessorPath parseEntityPath(String path, boolean fieldChainMode) {
        if (path == null) return null;
        path = path.trim();
        if (path.isEmpty()) return null;

        int bestExclusiveEnd = -1;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if ((c != '.' && c != ':') || !isUnescapedDelimiter(path, i)) continue;
            String prefix = path.substring(0, i);
            if (isLoadableClassFqn(prefix)) bestExclusiveEnd = i;
        }

        String className = null;
        String tail;
        if (bestExclusiveEnd >= 0) {
            className = path.substring(0, bestExclusiveEnd);
            tail = path.substring(bestExclusiveEnd + 1);
        } else {
            tail = path;
        }

        List<String> chain;
        if (className != null) {
            chain = splitMixedPathSegments(tail);
        } else if (fieldChainMode) {
            chain = splitMixedPathSegments(tail);
        } else {
            chain = splitLegacyColonOnlyChain(tail);
        }
        if (chain.isEmpty()) return null;
        return new AccessorPath(className, Collections.unmodifiableList(chain));
    }

    /**
     * 无显式类名时 {@code <>} 的兼容分段：仅 {@code :} 分段；仅一段时整段保留（含 {@code .}）。
     */
    private static List<String> splitLegacyColonOnlyChain(String tail) {
        if (tail == null || tail.isEmpty()) return Collections.emptyList();
        List<String> raw = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c == '\\' && i + 1 < tail.length()) {
                char n = tail.charAt(i + 1);
                if (n == ':' || n == '\\') {
                    cur.append(n);
                    i++;
                    continue;
                }
            }
            if (c == ':' && isUnescapedDelimiter(tail, i)) {
                String t = cur.toString().trim();
                if (!t.isEmpty()) raw.add(t);
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String t = cur.toString().trim();
        if (!t.isEmpty()) raw.add(t);
        if (raw.isEmpty()) return Collections.emptyList();
        if (raw.size() == 1) return Collections.singletonList(raw.get(0));
        return raw;
    }

    private static boolean isUnescapedDelimiter(String s, int index) {
        int bs = 0;
        for (int j = index - 1; j >= 0 && s.charAt(j) == '\\'; j--) {
            bs++;
        }
        return bs % 2 == 0;
    }

    private static boolean isLoadableClassFqn(String name) {
        if (name == null || name.isEmpty()) return false;
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * 在未转义的 {@code .} 与 {@code :} 处切分；{@code \\} 转义其后一字节。
     */
    private static List<String> splitMixedPathSegments(String s) {
        if (s == null || s.isEmpty()) return Collections.emptyList();
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '.' || n == ':' || n == '\\') {
                    cur.append(n);
                    i++;
                    continue;
                }
            }
            if ((c == '.' || c == ':') && isUnescapedDelimiter(s, i)) {
                String t = cur.toString().trim();
                if (!t.isEmpty()) parts.add(t);
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String t = cur.toString().trim();
        if (!t.isEmpty()) parts.add(t);
        return parts;
    }

    /**
     * 从实体起按 {@link AccessorPath} 做反射链（首段可在显式声明类上解析）；用于 {@code {}} 与 {@code <>} 同步器缺失时的回退。
     */
    private static Object walkReflectChainFromEntity(Entity entity, AccessorPath ap) {
        if (ap == null || ap.chain.isEmpty() || entity == null) return null;
        try {
            Object cur;
            int startIdx;
            if (ap.className != null) {
                Class<?> decl = ReflectionUtils.getClass(ap.className);
                if (decl == null || !decl.isInstance(entity)) {
                    return null;
                }
                cur = ReflectionUtils.getPrivateFieldValue(decl, entity, ap.chain.get(0), true);
                startIdx = 1;
            } else {
                cur = entity;
                startIdx = 0;
            }
            for (int i = startIdx; i < ap.chain.size(); i++) {
                if (cur == null) break;
                cur = resolveSegment(cur, ap.chain.get(i));
            }
            return cur;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object unwrapOptionalChain(@Nullable Object obj) {
        Object cur = obj;
        for (int depth = 0; depth < 64 && cur != null; depth++) {
            if (cur instanceof Optional) {
                cur = ((Optional<?>) cur).orElse(null);
            } else {
                break;
            }
        }
        return cur;
    }

    /**
     * 将反射/同步器包装类型转为表达式引擎易用的标量。
     */
    private static Object normalizeFieldValue(@Nullable Object v) {
        if (v == null) return null;
        v = unwrapOptionalChain(v);
        if (v == null) return null;
        if (v instanceof OptionalInt) {
            OptionalInt oi = (OptionalInt) v;
            return oi.isPresent() ? oi.getAsInt() : null;
        }
        if (v instanceof OptionalLong) {
            OptionalLong ol = (OptionalLong) v;
            return ol.isPresent() ? ol.getAsLong() : null;
        }
        if (v instanceof OptionalDouble) {
            OptionalDouble od = (OptionalDouble) v;
            return od.isPresent() ? od.getAsDouble() : null;
        }
        if (v instanceof AtomicInteger) return ((AtomicInteger) v).get();
        if (v instanceof AtomicLong) return ((AtomicLong) v).get();
        if (v instanceof AtomicBoolean) return ((AtomicBoolean) v).get();
        return v;
    }

    /**
     * 根据当前对象类型解析下一段
     */
    private static Object resolveSegment(Object obj, String segment) {
        obj = unwrapOptionalChain(obj);
        if (obj == null) return null;
        try {
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                Object key = tryParseKey(segment, map);
                return map.get(key);
            }
            if (obj instanceof List) {
                int idx = tryParseIndex(segment);
                if (idx >= 0 && idx < ((List<?>) obj).size()) {
                    return ((List<?>) obj).get(idx);
                }
                return null;
            }
            if (obj.getClass().isArray()) {
                int idx = tryParseIndex(segment);
                if (idx >= 0 && idx < Array.getLength(obj)) {
                    return Array.get(obj, idx);
                }
                return null;
            }
            return ReflectionUtils.getPrivateFieldValue(obj.getClass(), obj, segment, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryParseKey(String segment, Map<?, ?> map) {
        if (map.isEmpty()) return segment;
        Object sampleKey = map.keySet().iterator().next();
        if (sampleKey instanceof Integer) {
            Integer i = tryParseIndexObj(segment);
            return i != null ? i : segment;
        }
        if (sampleKey instanceof Long) {
            try {
                return Long.parseLong(segment);
            } catch (NumberFormatException e) {
                return segment;
            }
        }
        return segment;
    }

    private static int tryParseIndex(String segment) {
        Integer i = tryParseIndexObj(segment);
        return i != null ? i : -1;
    }

    private static Integer tryParseIndexObj(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return null;
        }
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
                    AccessorPath ap = d.accessorPath;
                    if (ap == null || ap.chain.isEmpty()) {
                        varsOut.put(key, null);
                        break;
                    }
                    String firstPartKey = ap.className != null ? ap.className + ":" + ap.chain.get(0) : ap.chain.get(0);
                    String accessorCacheKey = entity.getClass().getName() + "::" + firstPartKey;
                    EntityDataAccessor<?> accessor = accessorCache.computeIfAbsent(accessorCacheKey, k -> {
                        try {
                            String[] split = firstPartKey.split(":", 2);
                            if (split.length == 1) {
                                return (EntityDataAccessor<?>) ReflectionUtils.getPrivateFieldValue(ReflectionUtils.getClass(entity), entity, split[0], true);
                            }
                            Class<?> decl = ReflectionUtils.getClass(split[0]);
                            if (decl == null || !decl.isInstance(entity)) {
                                return null;
                            }
                            return (EntityDataAccessor<?>) ReflectionUtils.getPrivateFieldValue(decl, entity, split[1]);
                        } catch (Throwable ignored) {
                            return null;
                        }
                    });
                    Object value = null;
                    if (accessor != null) {
                        try {
                            value = entity.getEntityData().get(accessor);
                        } catch (Throwable ignored) {
                        }
                        for (int i = 1; i < ap.chain.size() && value != null; i++) {
                            value = resolveSegment(value, ap.chain.get(i));
                        }
                    } else {
                        value = walkReflectChainFromEntity(entity, ap);
                    }
                    varsOut.put(key, normalizeFieldValue(value));
                    break;
                case FIELD_CHAIN:
                    AccessorPath fp = d.accessorPath;
                    if (fp == null || fp.chain.isEmpty()) {
                        varsOut.put(key, null);
                        break;
                    }
                    varsOut.put(key, normalizeFieldValue(walkReflectChainFromEntity(entity, fp)));
                    break;
                case NBT_PATH:
                    if (NBTUtils.has(entity.getPersistentData(), d.payload)) {
                        Tag tag = NBTUtils.getTagByPath(entity.getPersistentData(), d.payload);
                        if (tag instanceof NumericTag n) {
                            varsOut.put(key, n.getAsNumber());
                        } else if (tag instanceof CollectionTag<?> c) {
                            varsOut.put(key, c.toArray());
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
                                entityType = (entityType == null) ? EntityUtils.getEntityRegistryString(entity) : entityType;
                                String[] parts = entityType.split(":", 2);
                                namespace = parts.length > 0 ? parts[0] : "";
                            }
                            varsOut.put(key, namespace);
                            break;
                        case "path":
                            if (path == null) {
                                entityType = (entityType == null) ? EntityUtils.getEntityRegistryString(entity) : entityType;
                                String[] parts = entityType.split(":", 2);
                                path = parts.length > 1 ? parts[1] : "";
                            }
                            varsOut.put(key, path);
                            break;
                        case "resource":
                        case "location":
                        case "resourceLocation":
                            if (resourceLocation == null)
                                resourceLocation = (entityType == null) ? EntityUtils.getEntityRegistryString(entity) : entityType;
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
                                if (entity instanceof ItemEntity item) num = item.getItem().getCount();
                                else num = 1;
                            }
                            varsOut.put(key, num);
                            break;
                        case "dim":
                        case "dimension":
                            if (dim == null) dim = DimensionUtils.getDimensionId(entity.level);
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
                                hasOwner = entity instanceof TamableAnimal t && t.getOwnerUUID() != null;
                            }
                            varsOut.put(key, hasOwner);
                            break;
                        case "ownerName":
                            if (ownerName == null) {
                                if (entity instanceof TamableAnimal t) {
                                    ownerUUID = t.getOwnerUUID();
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
