package xin.vanilla.aotake.rule.migration;

import xin.vanilla.aotake.rule.*;
import java.util.*;

public final class RuleTypeResolver {
    private RuleTypeResolver() { }
    public static RuleBindingPlan resolve(RuleAst ast, RuleBindingPlan known) {
        List<RuleBindingPlan.Binding> slots = new ArrayList<>();
        for (RuleAst.Declaration d : ast.declarations) {
            if (d.kind.equals("literal")) continue;
            RuleBindingPlan.Binding type = d.kind.equals("builtin") ? builtin(d) : known.known(d.kind, d.payload);
            if (type == null) throw new RuleDiagnostic(d.offset, "Unknown binding type; declare " + d.kind + " path " + d.payload);
            if (!stableType(type.type())) throw new RuleDiagnostic(d.offset, "Binding must expose a JRE scalar, Class, collection or array");
            slots.add(new RuleBindingPlan.Binding(d.name, d.kind, type.payload(), type.type(), type.nullable(), d.offset));
        }
        RuleBindingPlan plan = known.resolved(slots);
        JavaRuleEmitter.validate(ast, plan);
        return plan;
    }
    private static boolean stableType(Class<?> type) {
        if (type.isArray()) return type.getComponentType().isPrimitive() || stableType(type.getComponentType());
        return type == Object.class || type == String.class || type == Boolean.class || type == Character.class
                || type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == Class.class
                || type == Collection.class || type == List.class || type == Set.class || type == Map.class;
    }
    private static RuleBindingPlan.Binding builtin(RuleAst.Declaration d) {
        Class<?> type; String key = d.payload; boolean nullable = false;
        switch (key) {
            case "location": case "resourceLocation": case "entityResource": key = "resource";
            case "resource": case "namespace": case "path": case "clazzString": case "itemClazzString":
            case "name": case "displayName": case "dimension": case "dim": type = String.class; break;
            case "itemResource": case "customName": case "ownerName": type = String.class; nullable = true; break;
            case "tick": case "num": case "chunkX": case "chunkZ": type = Integer.class; break;
            case "x": case "y": case "z": type = Double.class; break;
            case "emptyItem": case "hasOwner": case "isItem": type = Boolean.class; break;
            case "clazz": case "itemClazz": type = Class.class; break;
            default: throw new RuleDiagnostic(d.offset, "Unknown built-in variable: " + key);
        }
        return new RuleBindingPlan.Binding(d.name, d.kind, key, type, nullable, d.offset);
    }
}
