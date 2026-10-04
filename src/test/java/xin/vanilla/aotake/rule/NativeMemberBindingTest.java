package xin.vanilla.aotake.rule;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.internal.fabric.rule.FabricNativeMembers;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class NativeMemberBindingTest {
    public static class Subject {
        public int count = 7;
        public int value(int n) { return count + n; }
        public String value(String s) { return s; }
        public static long twice(long n) { return n * 2; }
    }
    public static class Child extends Subject { }
    public interface Read { int get(Subject subject); }
    public interface Call { int call(Subject subject, int n); }
    public interface StaticCall { long call(long n); }
    public interface WrongReceiver { int call(String subject, int n); }
    public interface WrongReturn { long call(Subject subject, int n); }

    private NativeMemberSpec spec(String id, NativeMemberSpec.Kind kind, String name, String descriptor, Class<?> function) {
        return new NativeMemberSpec(id, kind, Subject.class.getName(), name, descriptor, function);
    }

    @Test public void resolvesOnceAndCallsTypedOverloadsFieldsAndStaticMembers() {
        AtomicInteger resolutions = new AtomicInteger();
        NativeMembers members = FabricNativeMembers.prepare(Arrays.asList(
                spec("value", NativeMemberSpec.Kind.METHOD, "method_1", "(I)I", Call.class),
                spec("count", NativeMemberSpec.Kind.FIELD_GET, "field_1", "I", Read.class),
                spec("twice", NativeMemberSpec.Kind.METHOD, "twice", "(J)J", StaticCall.class)),
                getClass().getClassLoader(), (kind, name) -> {
                    resolutions.incrementAndGet();
                    return name.equals("method_1") ? "value" : name.equals("field_1") ? "count" : name;
                });
        Child subject = new Child();
        Call call = members.require("value", Call.class);
        for (int i = 0; i < 1000; i++) assertEquals(i + 7, call.call(subject, i));
        assertEquals(7, members.require("count", Read.class).get(subject));
        assertEquals(18L, members.require("twice", StaticCall.class).call(9L));
        assertEquals(3, resolutions.get());
        assertSame(call, members.require("value", Call.class));
        assertThrows(IllegalArgumentException.class, () -> members.require("value", Read.class));
        assertThrows(IllegalArgumentException.class, () -> members.require("missing", Call.class));
    }

    @Test public void rejectsBadDescriptorsTypesMissingOwnersAndDuplicateIdsBeforeUse() {
        for (NativeMemberSpec spec : Arrays.asList(
                spec("bad", NativeMemberSpec.Kind.METHOD, "value", "(J)I", Call.class),
                spec("bad", NativeMemberSpec.Kind.METHOD, "value", "(I)J", Call.class),
                spec("bad", NativeMemberSpec.Kind.METHOD, "value", "(I)I", WrongReceiver.class),
                spec("bad", NativeMemberSpec.Kind.METHOD, "value", "(I)I", WrongReturn.class),
                spec("bad", NativeMemberSpec.Kind.FIELD_GET, "count", "J", Read.class),
                new NativeMemberSpec("bad", NativeMemberSpec.Kind.METHOD, "missing.OptionalMod", "value", "(I)I", Call.class))) {
            assertThrows(IllegalArgumentException.class, () -> FabricNativeMembers.prepare(Collections.singletonList(spec),
                    getClass().getClassLoader(), (kind, name) -> name));
        }
        NativeMemberSpec duplicate = spec("dup", NativeMemberSpec.Kind.METHOD, "value", "(I)I", Call.class);
        assertThrows(IllegalArgumentException.class, () -> FabricNativeMembers.prepare(Arrays.asList(duplicate, duplicate),
                getClass().getClassLoader(), (kind, name) -> name));
    }
}
