package xin.vanilla.aotake.rule.migration;

import xin.vanilla.aotake.rule.*;
import xin.vanilla.banira.api.script.ScriptSource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import static xin.vanilla.aotake.rule.migration.RuleAst.*;

/** Emits only parsed, type-checked syntax. Source strings never become executable fragments. */
public final class JavaRuleEmitter {
    private final RuleAst ast;
    private final RuleBindingPlan plan;
    private final Map<String, String> literals = new HashMap<>();
    private final List<String> adjustments = new ArrayList<>();
    private final boolean defaultCreate, defaultProtection;
    private static final String CREATE_SHAPE = "((tick>=5)&&(clazz:>itemClazz))&&(($create<=0)||($create==null))";
    private static final String PROTECT_SHAPE = "((customName!=null)||hasOwner)||($create>0)";

    private JavaRuleEmitter(RuleAst ast, RuleBindingPlan plan) {
        this.ast = ast; this.plan = plan;
        for (Declaration d : ast.declarations) if (d.kind.equals("literal")) literals.put(d.name, d.payload);
        String shape = shape(ast.expression);
        defaultCreate = shape.equals("(" + CREATE_SHAPE + ")");
        defaultProtection = shape.equals("(" + PROTECT_SHAPE + ")");
    }
    public static void validate(RuleAst ast, RuleBindingPlan plan) { new JavaRuleEmitter(ast, plan).body(); }
    public static RuleSource emit(String id, RuleAst ast, RuleBindingPlan plan) {
        JavaRuleEmitter emitter = new JavaRuleEmitter(ast, plan);
        String expression = emitter.body();
        String hash = sha(ast.source);
        String simpleName = "AotakeRule_" + hash;
        String className = "xin.vanilla.banira.generated." + simpleName;
        String source = "package xin.vanilla.banira.generated;\n"
                + "public final class " + simpleName + " implements xin.vanilla.aotake.api.rule.EntityRule {\n"
                + "  public boolean test(xin.vanilla.aotake.api.rule.EntityRuleContext context) {\n"
                + "    return " + expression + ";\n  }\n}\n";
        return new RuleSource(new ScriptSource(id, className, simpleName + ".java", source), plan, hash, emitter.adjustments);
    }
    private String body() {
        Value value = visit(ast.expression, Collections.emptySet());
        require(value.type == Boolean.class && !value.nullable, ast.expression, "Rule must return boolean");
        return value.java;
    }
    private Value visit(Node n, Set<String> nonnull) {
        switch (n.op) {
            case "string": return new Value(quote(n.text), String.class, false);
            case "number": return number(n);
            case "null": return new Value("null", Void.class, true);
            case "true": case "false": return new Value(n.op, Boolean.class, false);
            case "variable": return variable(n, nonnull);
            case "unary": {
                Value v = visit(n.args.get(0), nonnull);
                require(!v.nullable, n, "Add an explicit null guard");
                require(n.text.equals("!") ? v.type == Boolean.class : numeric(v.type), n, "Invalid unary operand");
                if (n.text.equals("~")) require(integral(v.type), n, "Bitwise operand must be integral");
                return new Value("(" + n.text + unbox(v) + ")", n.text.equals("!") ? Boolean.class : promote(v.type, Integer.class), false);
            }
            case "math": return math(n, nonnull);
            case "method": return method(n, nonnull);
            default: return binary(n, nonnull);
        }
    }
    private Value variable(Node n, Set<String> nonnull) {
        if (literals.containsKey(n.text)) return new Value(quote(literals.get(n.text)), String.class, false);
        for (int i = 0; i < plan.slots().size(); i++) {
            RuleBindingPlan.Binding binding = plan.slots().get(i);
            if (binding.name().equals(n.text)) return new Value("((" + binding.type().getCanonicalName()
                    + ")context.value(" + i + ", " + binding.type().getCanonicalName() + ".class))",
                    binding.type(), binding.nullable() && !nonnull.contains(n.text), true);
        }
        throw new RuleDiagnostic(n.offset, "Undeclared variable: " + n.text);
    }
    private Value binary(Node n, Set<String> nonnull) {
        Node left = n.args.get(0), right = n.args.get(1);
        // Only the two shipped rules get a documented null-order correction.
        if (defaultCreate && n.op.equals("||") && shape(n).equals("(($create<=0)||($create==null))")) {
            adjustments.add("Default Create rule: test missing processing time before comparison");
            return binary(new Node("||", "", n.offset, right, left), nonnull);
        }
        if (defaultProtection && n.op.equals(">") && shape(n).equals("($create>0)") && !nonnull.contains(left.text)) {
            adjustments.add("Default protection rule: missing processing time does not protect an entity");
            Set<String> present = new HashSet<>(nonnull); present.add(left.text);
            Value check = variable(left, nonnull), comparison = binary(n, present);
            return new Value("(" + check.java + " != null && " + comparison.java + ")", Boolean.class, false);
        }
        Value a = visit(left, nonnull);
        Set<String> rightKnown = nonnull;
        if (n.op.equals("&&") || n.op.equals("||")) {
            rightKnown = new HashSet<>(nonnull);
            rightKnown.addAll(nonnullWhen(left, n.op.equals("&&")));
        }
        Value b = visit(right, rightKnown);
        String op = n.op.equals("<>") ? "!=" : n.op;
        if (op.equals("==") || op.equals("!=")) {
            if (a.type == Void.class || b.type == Void.class) {
                Value other = a.type == Void.class ? b : a;
                require(other.boxed || !numeric(other.type) && other.type != Boolean.class, n, "A primitive expression cannot be compared with null");
                return new Value("(" + a.java + op + b.java + ")", Boolean.class, false);
            }
            if (a.type == String.class && b.type == String.class) return new Value((op.equals("!=") ? "!" : "")
                    + "java.util.Objects.equals(" + a.java + ", " + b.java + ")", Boolean.class, false);
            require(numeric(a.type) && numeric(b.type) || a.type == b.type, n, "Incompatible equality types");
            if (numeric(a.type) || a.type == Boolean.class) {
                require(!a.nullable && !b.nullable, n, "Add an explicit null guard before value comparison");
                return new Value("(" + unbox(a) + op + unbox(b) + ")", Boolean.class, false);
            }
            return new Value("(" + a.java + op + b.java + ")", Boolean.class, false);
        }
        require(!a.nullable && !b.nullable, n, "Add an explicit null guard before " + op);
        if (op.equals(":>") || op.equals("<:")) {
            require(a.type == Class.class && b.type == Class.class, n, "Type relation requires Class bindings");
            return new Value((op.equals(":>") ? b.java : a.java) + ".isAssignableFrom(" + (op.equals(":>") ? a.java : b.java) + ")", Boolean.class, false);
        }
        if (op.equals("&&") || op.equals("||")) {
            require(a.type == Boolean.class && b.type == Boolean.class, n, "Logical operands must be boolean");
            return new Value("(" + unbox(a) + op + unbox(b) + ")", Boolean.class, false);
        }
        if (op.equals("+") && (a.type == String.class || b.type == String.class)) {
            return new Value("(" + a.java + "+" + b.java + ")", String.class, false);
        }
        boolean bit = op.equals("^") || op.equals("&") || op.equals("|");
        if (bit && a.type == Boolean.class && b.type == Boolean.class)
            return new Value("(" + unbox(a) + op + unbox(b) + ")", Boolean.class, false);
        require(numeric(a.type) && numeric(b.type), n, "Numeric operands required");
        boolean shift = op.equals("<<") || op.equals(">>") || op.equals(">>>");
        if (bit || shift) require(integral(a.type) && integral(b.type), n, "Bitwise operands must be integral");
        boolean compare = Arrays.asList("<", ">", "<=", ">=").contains(op);
        return new Value("(" + unbox(a) + op + unbox(b) + ")", compare ? Boolean.class : promote(a.type, shift ? Integer.class : b.type), false);
    }
    private Set<String> nonnullWhen(Node n, boolean truth) {
        Set<String> result = new HashSet<>();
        if ((n.op.equals("!=") || n.op.equals("<>")) && truth || n.op.equals("==") && !truth) {
            Node a = n.args.get(0), b = n.args.get(1);
            if (a.op.equals("variable") && b.op.equals("null")) result.add(a.text);
            if (b.op.equals("variable") && a.op.equals("null")) result.add(b.text);
        }
        if (n.op.equals("&&") && truth || n.op.equals("||") && !truth) {
            result.addAll(nonnullWhen(n.args.get(0), truth)); result.addAll(nonnullWhen(n.args.get(1), truth));
        }
        if (n.op.equals("unary") && n.text.equals("!")) result.addAll(nonnullWhen(n.args.get(0), !truth));
        return result;
    }
    private Value method(Node n, Set<String> nonnull) {
        require(n.text.equals("contains") && n.args.size() == 2, n, "Only contains(value) is supported");
        Value target = visit(n.args.get(0), nonnull), needle = visit(n.args.get(1), nonnull);
        require(!target.nullable, n, "Add a null guard before contains");
        if (target.type == String.class) {
            require(needle.type == String.class && !needle.nullable, n, "String contains requires a non-null String");
            return new Value(target.java + ".contains(" + needle.java + ")", Boolean.class, false);
        }
        if (target.type.isArray()) return new Value("xin.vanilla.aotake.api.rule.RuleValues.contains(" + target.java + ", " + needle.java + ")", Boolean.class, false);
        require(Collection.class.isAssignableFrom(target.type), n, "contains requires a String, array or Collection");
        return new Value(target.java + ".contains(" + needle.java + ")", Boolean.class, false);
    }
    private Value math(Node n, Set<String> nonnull) {
        int count;
        switch (n.text) {
            case "random": count = 0; break;
            case "pow": case "min": case "max": case "atan2": count = 2; break;
            case "sqrt": case "abs": case "log": case "log10": case "exp": case "sin": case "cos": case "tan":
            case "asin": case "acos": case "atan": case "sinh": case "cosh": case "tanh": case "ceil": case "floor":
            case "round": case "signum": case "toRadians": case "toDegrees": count = 1; break;
            default: throw new RuleDiagnostic(n.offset, "Unsupported Math function: " + n.text);
        }
        require(n.args.size() == count, n, "Incorrect function argument count");
        StringJoiner args = new StringJoiner(", "); Class<?> promoted = Integer.class;
        for (Node arg : n.args) {
            Value value = visit(arg, nonnull);
            require(numeric(value.type) && !value.nullable, arg, "Math argument must be a non-null number");
            args.add(unbox(value)); promoted = promote(promoted, value.type);
        }
        Class<?> type = Double.class;
        if (Arrays.asList("min", "max", "abs").contains(n.text)) type = promoted;
        if (n.text.equals("round")) type = promoted == Double.class ? Long.class : Integer.class;
        if (n.text.equals("signum")) type = promoted == Double.class ? Double.class : Float.class;
        return new Value("java.lang.Math." + n.text + "(" + args + ")", type, false);
    }
    private Value number(Node n) {
        String text = n.text;
        require(text.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[lLfFdD]?"), n, "Invalid number");
        char last = text.charAt(text.length() - 1); Class<?> type;
        try {
            if (last == 'l' || last == 'L') { Long.decode(text.substring(0, text.length() - 1)); type = Long.class; }
            else if (last == 'f' || last == 'F') { require(Float.isFinite(Float.parseFloat(text)), n, "Non-finite literal"); type = Float.class; }
            else if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0 || last == 'd' || last == 'D') {
                require(Double.isFinite(Double.parseDouble(text)), n, "Non-finite literal"); type = Double.class;
            } else { Integer.decode(text); type = Integer.class; }
        } catch (NumberFormatException error) { throw new RuleDiagnostic(n.offset, "Number out of range or invalid; use an explicit Java numeric suffix"); }
        return new Value(text, type, false);
    }
    private String shape(Node node) {
        if (node.op.equals("variable")) {
            RuleBindingPlan.Binding b = plan.named(node.text);
            return b != null && b.kind().equals("nbt") && b.payload().equals(RuleBindingPlan.CREATE_TIME) ? "$create" : node.text;
        }
        if (node.args.isEmpty()) return node.text;
        if (node.args.size() == 2) return "(" + shape(node.args.get(0)) + node.op + shape(node.args.get(1)) + ")";
        return "?";
    }
    private static boolean numeric(Class<?> t) { return t == Integer.class || t == Long.class || t == Float.class || t == Double.class || t == Short.class || t == Byte.class; }
    private static boolean integral(Class<?> t) { return numeric(t) && t != Float.class && t != Double.class; }
    private static Class<?> promote(Class<?> a, Class<?> b) {
        if (a == Double.class || b == Double.class) return Double.class;
        if (a == Float.class || b == Float.class) return Float.class;
        if (a == Long.class || b == Long.class) return Long.class;
        return Integer.class;
    }
    private static String unbox(Value value) {
        // Literals and computed expressions are already primitive; binding reads are explicitly boxed.
        if (!value.boxed) return value.java;
        String method = value.type == Boolean.class ? "booleanValue" : value.type == Long.class ? "longValue"
                : value.type == Double.class ? "doubleValue" : value.type == Float.class ? "floatValue" : "intValue";
        return value.java + "." + method + "()";
    }
    private static void require(boolean ok, Node node, String reason) { if (!ok) throw new RuleDiagnostic(node.offset, reason); }
    private static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': result.append("\\\\"); break; case '"': result.append("\\\""); break;
                case '\n': result.append("\\n"); break; case '\r': result.append("\\r"); break; case '\t': result.append("\\t"); break;
                case '\b': result.append("\\b"); break; case '\f': result.append("\\f"); break;
                default: if (c < 32) result.append(String.format(Locale.ROOT, "\\%03o", (int) c)); else result.append(c);
            }
        }
        return result.append('"').toString();
    }
    private static String sha(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static final class Value {
        final String java; final Class<?> type; final boolean nullable, boxed;
        Value(String java, Class<?> type, boolean nullable) { this(java, type, nullable, false); }
        Value(String java, Class<?> type, boolean nullable, boolean boxed) {
            this.java = java; this.type = type; this.nullable = nullable; this.boxed = boxed;
        }
    }
}
