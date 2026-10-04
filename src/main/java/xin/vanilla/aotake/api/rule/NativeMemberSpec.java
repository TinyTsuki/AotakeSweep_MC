package xin.vanilla.aotake.api.rule;

import java.util.Objects;

/**
 * A member's declaring class, SRG name, JVM descriptor and exact functional interface.
 */
public final class NativeMemberSpec {
    public enum Kind {METHOD, FIELD_GET}

    private final String id, owner, name, descriptor;
    private final Kind kind;
    private final Class<?> functionType;

    public NativeMemberSpec(String id, Kind kind, String owner, String name, String descriptor, Class<?> functionType) {
        this.id = required(id);
        this.kind = Objects.requireNonNull(kind);
        this.owner = required(owner);
        this.name = required(name);
        this.descriptor = required(descriptor);
        this.functionType = Objects.requireNonNull(functionType);
    }

    private static String required(String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("Member specification cannot be empty");
        return value;
    }

    public String id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public String owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public String descriptor() {
        return descriptor;
    }

    public Class<?> functionType() {
        return functionType;
    }
}
