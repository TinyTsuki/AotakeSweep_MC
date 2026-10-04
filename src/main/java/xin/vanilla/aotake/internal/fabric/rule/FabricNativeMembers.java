package xin.vanilla.aotake.internal.fabric.rule;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import xin.vanilla.aotake.api.rule.NativeMemberSpec;
import xin.vanilla.aotake.api.rule.NativeMembers;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Resolves intermediary names and exact descriptors before a generation is activated.
 */
public final class FabricNativeMembers implements NativeMembers {
    private final Map<String, Object> functions;
    private final Map<String, Class<?>> types;

    private FabricNativeMembers(Map<String, Object> functions, Map<String, Class<?>> types) {
        this.functions = Collections.unmodifiableMap(functions);
        this.types = Collections.unmodifiableMap(types);
    }

    public static NativeMembers prepare(List<NativeMemberSpec> specs, ClassLoader loader) {
        return prepare(specs, loader, FabricLoader.getInstance().getMappingResolver());
    }

    public static NativeMembers prepare(List<NativeMemberSpec> specs, ClassLoader loader, MappingResolver mappings) {
        List<NativeMemberSpec> resolved = new ArrayList<>();
        for (NativeMemberSpec spec : specs) {
            String name = spec.kind() == NativeMemberSpec.Kind.METHOD
                    ? mappings.mapMethodName("intermediary", spec.owner(), spec.name(), spec.descriptor())
                    : mappings.mapFieldName("intermediary", spec.owner(), spec.name(), spec.descriptor());
            resolved.add(new NativeMemberSpec(spec.id(), spec.kind(),
                    mappings.mapClassName("intermediary", spec.owner()), name,
                    mapDescriptor(spec.descriptor(), mappings), spec.functionType()));
        }
        return prepare(resolved, loader, (kind, name) -> name);
    }

    private static String mapDescriptor(String descriptor, MappingResolver mappings) {
        StringBuilder mapped = new StringBuilder();
        for (int i = 0; i < descriptor.length(); i++) {
            char token = descriptor.charAt(i);
            mapped.append(token);
            if (token != 'L') continue;
            int end = descriptor.indexOf(';', i);
            if (end < 0) throw new IllegalArgumentException("Invalid native member descriptor");
            String type = descriptor.substring(i + 1, end).replace('/', '.');
            mapped.append(mappings.mapClassName("intermediary", type).replace('.', '/')).append(';');
            i = end;
        }
        return mapped.toString();
    }

    public static NativeMembers prepare(List<NativeMemberSpec> specs, ClassLoader loader,
                                        BiFunction<NativeMemberSpec.Kind, String, String> remap) {
        Map<String, Object> functions = new LinkedHashMap<>();
        Map<String, Class<?>> types = new HashMap<>();
        for (NativeMemberSpec spec : specs) {
            try {
                if (functions.containsKey(spec.id())) throw new IllegalArgumentException("Duplicate member ID");
                Class<?> owner = Class.forName(spec.owner(), false, loader);
                String name = Objects.requireNonNull(remap.apply(spec.kind(), spec.name()), "mapped name");
                MethodHandle handle;
                if (spec.kind() == NativeMemberSpec.Kind.FIELD_GET) {
                    Class<?> fieldType = MethodType.fromMethodDescriptorString("()" + spec.descriptor(), loader).returnType();
                    Field field = owner.getDeclaredField(name);
                    if (field.getType() != fieldType)
                        throw new IllegalArgumentException("Field descriptor disagrees with declared type");
                    field.setAccessible(true);
                    handle = MethodHandles.lookup().unreflectGetter(field);
                } else {
                    MethodType signature = MethodType.fromMethodDescriptorString(spec.descriptor(), loader);
                    Method found = null;
                    for (Method method : owner.getDeclaredMethods()) {
                        if (method.getName().equals(name) && MethodType.methodType(method.getReturnType(), method.getParameterTypes()).equals(signature)) {
                            if (found != null) throw new IllegalArgumentException("Ambiguous member descriptor");
                            found = method;
                        }
                    }
                    if (found == null)
                        throw new NoSuchMethodException(owner.getName() + "." + name + spec.descriptor());
                    found.setAccessible(true);
                    handle = MethodHandles.lookup().unreflect(found);
                }
                Method sam = functionalMethod(spec.functionType());
                MethodType functionSignature = MethodType.methodType(sam.getReturnType(), sam.getParameterTypes());
                if (!functionSignature.equals(handle.type())) throw new IllegalArgumentException(
                        "Function signature " + functionSignature + " disagrees with member " + handle.type());
                functions.put(spec.id(), MethodHandleProxies.asInterfaceInstance(spec.functionType(), handle));
                types.put(spec.id(), spec.functionType());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                throw new IllegalArgumentException("Cannot bind native member " + spec.id() + " (" + spec.owner()
                        + "." + spec.name() + spec.descriptor() + ")", error);
            }
        }
        return new FabricNativeMembers(functions, types);
    }

    private static Method functionalMethod(Class<?> type) {
        if (!type.isInterface() || !Modifier.isPublic(type.getModifiers())) {
            throw new IllegalArgumentException("Function type must be a public interface");
        }
        Method found = null;
        for (Method method : type.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || method.isDefault() || Modifier.isStatic(method.getModifiers()))
                continue;
            try {
                Object.class.getMethod(method.getName(), method.getParameterTypes());
                continue;
            } catch (NoSuchMethodException ignored) {
            }
            if (found != null && (!found.getName().equals(method.getName())
                    || !MethodType.methodType(found.getReturnType(), found.getParameterTypes()).equals(
                    MethodType.methodType(method.getReturnType(), method.getParameterTypes())))) {
                throw new IllegalArgumentException("Function type must have one abstract method");
            }
            found = method;
        }
        if (found == null) throw new IllegalArgumentException("Function type has no abstract method");
        return found;
    }

    @Override
    public <F> F require(String id, Class<F> functionType) {
        if (types.get(id) != functionType || !functions.containsKey(id)) {
            throw new IllegalArgumentException("Missing member or incorrect function type: " + id);
        }
        return functionType.cast(functions.get(id));
    }
}
