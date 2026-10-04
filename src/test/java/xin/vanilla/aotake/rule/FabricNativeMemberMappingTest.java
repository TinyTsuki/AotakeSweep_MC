package xin.vanilla.aotake.rule;

import net.fabricmc.loader.api.MappingResolver;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.internal.fabric.rule.FabricNativeMembers;
import xin.vanilla.aotake.internal.fabric.rule.FabricRuleBindings;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class FabricNativeMemberMappingTest {
    public static class Subject {
        public Subject other;
        public Subject pick(Subject[] candidates, int index) { return candidates[index]; }
    }
    public interface Read { Subject get(Subject subject); }
    public interface Pick { Subject get(Subject subject, Subject[] candidates, int index); }

    @Test public void fieldPathsMapDeclaringClassesAndNestedObjectDescriptorsInTheColdPath() {
        MappingResolver mappings = (MappingResolver) Proxy.newProxyInstance(MappingResolver.class.getClassLoader(),
                new Class<?>[]{MappingResolver.class}, (proxy, method, args) -> {
                    assertEquals("intermediary", args[0]);
                    if (method.getName().equals("mapClassName"))
                        return args[1].equals("net.minecraft.class_1") ? Subject.class.getName() : args[1];
                    if (method.getName().equals("unmapClassName"))
                        return args[1].equals(Subject.class.getName()) ? "net.minecraft.class_1" : args[1];
                    if (method.getName().equals("mapFieldName")) {
                        assertEquals("net.minecraft.class_1", args[1]);
                        assertEquals("field_1", args[2]);
                        assertEquals("Lnet/minecraft/class_1;", args[3]);
                        return "other";
                    }
                    throw new AssertionError(method.getName());
                });
        RuleBindingPlan.Binding binding = new RuleBindingPlan.Binding("other", "field",
                "net.minecraft.class_1:field_1", Subject.class, true, 0);
        FabricRuleBindings.Reader reader = FabricRuleBindings.field(binding, Object.class,
                getClass().getClassLoader(), mappings);
        Subject first = new Subject(), second = new Subject();
        first.other = second;
        assertSame(second, reader.read(first));
        assertNull(reader.read(new Object()));
        first.other = null;
        assertNull(reader.read(first));
    }

    @Test public void mapsDeclaringClassMemberAndArrayDescriptorsBeforeBinding() {
        List<String> requests = new ArrayList<>();
        MappingResolver mappings = (MappingResolver) Proxy.newProxyInstance(MappingResolver.class.getClassLoader(),
                new Class<?>[]{MappingResolver.class}, (proxy, method, args) -> {
                    assertEquals("intermediary", args[0]);
                    if (method.getName().equals("mapClassName")) {
                        assertEquals("stable.Owner", args[1]);
                        return Subject.class.getName();
                    }
                    assertEquals("stable.Owner", args[1]);
                    requests.add(args[2] + ":" + args[3]);
                    if (method.getName().equals("mapFieldName")) return "other";
                    if (method.getName().equals("mapMethodName")) return "pick";
                    throw new AssertionError(method.getName());
                });
        NativeMembers members = FabricNativeMembers.prepare(Arrays.asList(
                new NativeMemberSpec("other", NativeMemberSpec.Kind.FIELD_GET, "stable.Owner", "field_1", "Lstable/Owner;", Read.class),
                new NativeMemberSpec("pick", NativeMemberSpec.Kind.METHOD, "stable.Owner", "method_1", "([Lstable/Owner;I)Lstable/Owner;", Pick.class)),
                getClass().getClassLoader(), mappings);
        Subject current = new Subject(), other = new Subject();
        current.other = other;
        assertSame(other, members.require("other", Read.class).get(current));
        assertSame(other, members.require("pick", Pick.class).get(current, new Subject[]{current, other}, 1));
        assertEquals(Arrays.asList("field_1:Lstable/Owner;", "method_1:([Lstable/Owner;I)Lstable/Owner;"), requests);
    }
}
