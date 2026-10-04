package xin.vanilla.aotake.rule;

import net.fabricmc.loader.api.MappingResolver;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.internal.fabric.rule.FabricNativeMembers;

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
