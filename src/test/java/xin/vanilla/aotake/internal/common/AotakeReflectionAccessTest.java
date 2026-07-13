package xin.vanilla.aotake.internal.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AotakeReflectionAccessTest {
    @Test
    public void readsPrivateFieldFromParentWhenRequested() {
        Child child = new Child();

        assertEquals("hidden", AotakeReflectionAccess.fieldValue(Child.class, child, "value", true));
        assertNull(AotakeReflectionAccess.fieldValue(Child.class, child, "value", false));
    }

    @Test
    public void resolvesClassNamesWithoutThrowing() {
        assertEquals(String.class, AotakeReflectionAccess.classByName("java.lang.String"));
        assertNull(AotakeReflectionAccess.classByName("not.a.real.Type"));
    }

    private static class Parent {
        private final String value = "hidden";
    }

    private static final class Child extends Parent {
    }
}
