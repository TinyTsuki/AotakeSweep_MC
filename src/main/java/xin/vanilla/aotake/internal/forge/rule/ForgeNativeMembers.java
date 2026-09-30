package xin.vanilla.aotake.internal.forge.rule;

import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import xin.vanilla.aotake.api.rule.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiFunction;

/** Resolves SRG names and exact descriptors once, before a generation is activated. */
public final class ForgeNativeMembers implements NativeMembers {
    private final Map<String, Object> functions;
    private final Map<String, Class<?>> types;

    private ForgeNativeMembers(Map<String, Object> functions, Map<String, Class<?>> types) {
        this.functions = Collections.unmodifiableMap(functions);
        this.types = Collections.unmodifiableMap(types);
    }

    public static NativeMembers prepare(List<NativeMemberSpec> specs, ClassLoader loader) {
        return prepare(specs, loader, (kind, name) -> ObfuscationReflectionHelper.remapName(
                kind == NativeMemberSpec.Kind.METHOD ? INameMappingService.Domain.METHOD : INameMappingService.Domain.FIELD, name));
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
                    if (field.getType() != fieldType) throw new IllegalArgumentException("Field descriptor disagrees with declared type");
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
                    if (found == null) throw new NoSuchMethodException(owner.getName() + "." + name + spec.descriptor());
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
        return new ForgeNativeMembers(functions, types);
    }

    private static Method functionalMethod(Class<?> type) {
        if (!type.isInterface() || !Modifier.isPublic(type.getModifiers())) {
            throw new IllegalArgumentException("Function type must be a public interface");
        }
        Method found = null;
        for (Method method : type.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || method.isDefault() || Modifier.isStatic(method.getModifiers())) continue;
            try { Object.class.getMethod(method.getName(), method.getParameterTypes()); continue; }
            catch (NoSuchMethodException ignored) { }
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

    @Override public <F> F require(String id, Class<F> functionType) {
        if (types.get(id) != functionType || !functions.containsKey(id)) {
            throw new IllegalArgumentException("Missing member or incorrect function type: " + id);
        }
        return functionType.cast(functions.get(id));
    }
}
