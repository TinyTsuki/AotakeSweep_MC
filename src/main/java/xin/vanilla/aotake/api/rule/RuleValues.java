package xin.vanilla.aotake.api.rule;

import java.lang.reflect.Array;
import java.util.Objects;

/**
 * Java equality for array membership; no string/number coercion.
 */
public final class RuleValues {
    private RuleValues() {
    }

    public static boolean contains(Object array, Object needle) {
        for (int i = 0; i < Array.getLength(array); i++) {
            if (Objects.equals(Array.get(array, i), needle)) return true;
        }
        return false;
    }
}
