package xin.vanilla.aotake.rule;

import com.google.gson.JsonElement;
import xin.vanilla.aotake.api.rule.NativeMemberSpec;
import xin.vanilla.aotake.api.rule.NativeMembers;

import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Cold-path metadata shared by entity rules and sweep policies.
 */
final class NativeMemberDeclarations {
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList("id", "kind", "owner", "name", "descriptor", "functionType"));
    private final List<Map<String, String>> entries;

    private NativeMemberDeclarations(List<Map<String, String>> entries) {
        this.entries = entries;
    }

    static NativeMemberDeclarations read(JsonElement value) throws IOException {
        if (!value.isJsonArray() || value.getAsJsonArray().size() > 128)
            throw new IOException("Invalid native member declarations");
        List<Map<String, String>> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new IOException("Expected native member object");
            Map<String, String> member = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                JsonElement text = field.getValue();
                if (!text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString() || text.getAsString().isEmpty())
                    throw new IOException("Expected nonempty native member field: " + field.getKey());
                member.put(field.getKey(), text.getAsString());
            }
            if (!member.keySet().equals(FIELDS) || !ids.add(member.get("id")))
                throw new IOException("Invalid or duplicate native member declaration");
            try {
                NativeMemberSpec.Kind.valueOf(member.get("kind"));
            } catch (IllegalArgumentException error) {
                throw new IOException("Invalid native member kind", error);
            }
            entries.add(Collections.unmodifiableMap(member));
        }
        return new NativeMemberDeclarations(Collections.unmodifiableList(entries));
    }

    NativeMembers prepare(ClassLoader loader, BiFunction<List<NativeMemberSpec>, ClassLoader, NativeMembers> binder) {
        if (entries.isEmpty()) return NativeMembers.EMPTY;
        List<NativeMemberSpec> specs = new ArrayList<>();
        for (Map<String, String> member : entries) {
            try {
                specs.add(new NativeMemberSpec(member.get("id"), NativeMemberSpec.Kind.valueOf(member.get("kind")),
                        member.get("owner"), member.get("name"), member.get("descriptor"), Class.forName(member.get("functionType"), false, loader)));
            } catch (ClassNotFoundException | LinkageError error) {
                throw new IllegalArgumentException("Missing native function type for " + member.get("id") + ": " + member.get("functionType"), error);
            }
        }
        return Objects.requireNonNull(binder.apply(Collections.unmodifiableList(specs), loader), "native bindings");
    }
}
