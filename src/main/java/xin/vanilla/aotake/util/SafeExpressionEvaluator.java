package xin.vanilla.aotake.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 安全表达式求值器
 */
public class SafeExpressionEvaluator {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");

    // 允许的数学函数
    private static final Set<String> MATH_FUNCTIONS = new HashSet<>(Arrays.asList(
            // 基本运算
            "sqrt", "pow", "abs", "max", "min",
            // 对数函数
            "log", "log10", "exp",
            // 三角函数
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            // 双曲函数
            "sinh", "cosh", "tanh",
            // 取整函数
            "ceil", "floor", "round",
            // 其他
            "signum", "toRadians", "toDegrees", "random"
    ));

    // 允许的安全方法
    private static final Set<String> SAFE_METHODS = new HashSet<>(Collections.singletonList(
            "contains"
    ));

    // 编译后的 AST 根节点
    private final Node root;

    public SafeExpressionEvaluator(String expression) {
        Tokenizer tok = new Tokenizer(expression);
        Parser parser = new Parser(tok);
        this.root = parser.parseExpression();
    }

    // region public

    public boolean evaluateBoolean(Map<String, Object> vars) {
        return evaluateBoolean(vars, true);
    }

    public boolean evaluateBoolean(Map<String, Object> vars, boolean throwException) {
        try {
            Map<String, Object> scope = vars == null ? Collections.emptyMap() : vars;
            Object val = root.evaluate(scope);
            return toBoolean(val);
        } catch (Throwable e) {
            if (throwException) {
                throw e;
            } else {
                LOGGER.error("Failed to evaluate expression", e);
            }
            return false;
        }
    }

    // endregion public


    private static boolean isNumericString(String s) {
        if (s == null) return false;
        return NUMERIC_PATTERN.matcher(s.trim()).matches();
    }

    /**
     * 是否为整数类型
     */
    private static boolean isIntegerType(Object o) {
        if (o == null) return false;
        if (o instanceof Byte || o instanceof Short || o instanceof Integer || o instanceof Long) {
            return true;
        }
        if (o instanceof Float || o instanceof Double) {
            return false;
        }
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            return d == Math.floor(d) && !Double.isInfinite(d);
        }
        if (o instanceof String) {
            String s = ((String) o).trim();
            return INTEGER_PATTERN.matcher(s).matches();
        }
        return false;
    }

    /**
     * 获取整数值
     */
    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Byte) return ((Byte) o).longValue();
        if (o instanceof Short) return ((Short) o).longValue();
        if (o instanceof Integer) return ((Integer) o).longValue();
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            return (long) d;
        }
        if (o instanceof String) {
            String s = ((String) o).trim();
            if (INTEGER_PATTERN.matcher(s).matches()) {
                return Long.parseLong(s);
            }
        }
        return 0L;
    }

    /**
     * 解析数字字符串
     */
    private static Number parseNumber(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.contains(".")) {
            return Double.parseDouble(s);
        } else {
            try {
                long l = Long.parseLong(s);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                } else {
                    return l;
                }
            } catch (NumberFormatException e) {
                return Double.parseDouble(s);
            }
        }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Boolean) return (Boolean) o ? 1.0 : 0.0;
        if (o instanceof String) {
            String s = (String) o;
            if (isNumericString(s)) {
                return Double.parseDouble(s);
            } else {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static boolean toBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0.0;
        if (o instanceof String) {
            return StringUtils.stringToBoolean(o.toString());
        }
        return true;
    }

    private enum TokenType {
        IDENT, NUMBER, STRING,
        TRUE, FALSE, NULL,
        LPAREN, RPAREN, COMMA,
        PLUS, MINUS, MUL, DIV, MOD, POW,
        AND, OR, NOT,
        EQ, NEQ, LT, GT, LE, GE,
        ARROW, LARROW,
        DOT,
        END
    }

    private static class Token {
        final TokenType type;
        final String text;

        Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }

        public String toString() {
            return type + "(" + text + ")";
        }
    }

    private static class Tokenizer {
        private final String input;
        private final int len;
        private int pos = 0;
        private Token lookahead = null;

        Tokenizer(String in) {
            this.input = in == null ? "" : in;
            this.len = input.length();
            this.skipWhitespace();
        }

        private void skipWhitespace() {
            while (pos < len && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        Token next() {
            if (lookahead != null) {
                Token t = lookahead;
                lookahead = null;
                return t;
            }
            skipWhitespace();
            if (pos >= len) return new Token(TokenType.END, "");
            char c = input.charAt(pos);

            if (pos + 1 < len) {
                String two = input.substring(pos, pos + 2);
                switch (two) {
                    case "&&":
                        pos += 2;
                        return new Token(TokenType.AND, "&&");
                    case "||":
                        pos += 2;
                        return new Token(TokenType.OR, "||");
                    case "==":
                        pos += 2;
                        return new Token(TokenType.EQ, "==");
                    case "!=":
                        pos += 2;
                        return new Token(TokenType.NEQ, "!=");
                    case "<>":
                        pos += 2;
                        return new Token(TokenType.NEQ, "<>");
                    case "<=":
                        pos += 2;
                        return new Token(TokenType.LE, "<=");
                    case ">=":
                        pos += 2;
                        return new Token(TokenType.GE, ">=");
                    case ":>":
                        pos += 2;
                        return new Token(TokenType.ARROW, ":>");
                    case "<:":
                        pos += 2;
                        return new Token(TokenType.LARROW, "<:");
                }
            }

            switch (c) {
                case '(':
                    pos++;
                    return new Token(TokenType.LPAREN, "(");
                case ')':
                    pos++;
                    return new Token(TokenType.RPAREN, ")");
                case ',':
                    pos++;
                    return new Token(TokenType.COMMA, ",");
                case '+':
                    pos++;
                    return new Token(TokenType.PLUS, "+");
                case '-':
                    pos++;
                    return new Token(TokenType.MINUS, "-");
                case '*':
                    pos++;
                    return new Token(TokenType.MUL, "*");
                case '/':
                    pos++;
                    return new Token(TokenType.DIV, "/");
                case '%':
                    pos++;
                    return new Token(TokenType.MOD, "%");
                case '^':
                    pos++;
                    return new Token(TokenType.POW, "^");
                case '<':
                    pos++;
                    return new Token(TokenType.LT, "<");
                case '>':
                    pos++;
                    return new Token(TokenType.GT, ">");
                case '.':
                    pos++;
                    return new Token(TokenType.DOT, ".");
                case '!':
                    pos++;
                    return new Token(TokenType.NOT, "!");
                case '\'':
                case '"':
                    pos++;
                    StringBuilder sb = new StringBuilder();
                    boolean closed = false;
                    while (pos < len) {
                        char ch = input.charAt(pos++);
                        if (ch == '\\' && pos < len) {
                            char next = input.charAt(pos++);
                            sb.append(next);
                        } else if (ch == c) {
                            closed = true;
                            break;
                        } else {
                            sb.append(ch);
                        }
                    }
                    if (!closed) {
                        throw new RuntimeException("Unterminated string literal");
                    }
                    return new Token(TokenType.STRING, sb.toString());
                default:
                    if (Character.isDigit(c)) {
                        int s = pos;
                        boolean dotSeen = false;
                        while (pos < len) {
                            char ch = input.charAt(pos);
                            if (ch == '.') {
                                if (dotSeen) break;
                                dotSeen = true;
                            } else if (!Character.isDigit(ch)) break;
                            pos++;
                        }
                        String num = input.substring(s, pos);
                        return new Token(TokenType.NUMBER, num);
                    } else if (Character.isLetter(c) || c == '_') {
                        int s = pos;
                        while (pos < len) {
                            char ch = input.charAt(pos);
                            if (Character.isLetterOrDigit(ch) || ch == '_') pos++;
                            else break;
                        }
                        String id = input.substring(s, pos);
                        String lid = id.toLowerCase(Locale.ROOT);
                        switch (lid) {
                            case "true":
                                return new Token(TokenType.TRUE, id);
                            case "false":
                                return new Token(TokenType.FALSE, id);
                            case "null":
                                return new Token(TokenType.NULL, id);
                        }
                        return new Token(TokenType.IDENT, id);
                    } else {
                        throw new RuntimeException("Unexpected char: " + c + " at pos " + pos);
                    }
            }
        }

        Token peek() {
            if (lookahead == null) lookahead = next();
            return lookahead;
        }
    }

    private static class Parser {
        private final Tokenizer tok;

        Parser(Tokenizer t) {
            this.tok = t;
        }

        Node parseExpression() {
            Node n = parseOr();
            Token t = tok.peek();
            if (t.type != TokenType.END) {
                throw new RuntimeException("Unexpected token after expression: " + t);
            }
            return n;
        }

        private Node parseOr() {
            Node left = parseAnd();
            while (tok.peek().type == TokenType.OR) {
                tok.next();
                Node right = parseAnd();
                left = new BinaryNode("||", left, right);
            }
            return left;
        }

        private Node parseAnd() {
            Node left = parseComparison();
            while (tok.peek().type == TokenType.AND) {
                tok.next();
                Node right = parseComparison();
                left = new BinaryNode("&&", left, right);
            }
            return left;
        }

        private Node parseComparison() {
            Node left = parseAdd();
            Token t = tok.peek();
            while (t.type == TokenType.EQ || t.type == TokenType.NEQ ||
                    t.type == TokenType.LT || t.type == TokenType.GT ||
                    t.type == TokenType.LE || t.type == TokenType.GE ||
                    t.type == TokenType.ARROW || t.type == TokenType.LARROW) {
                tok.next();
                Node right = parseAdd();
                String op;
                switch (t.type) {
                    case EQ:
                        op = "==";
                        break;
                    case NEQ:
                        op = "!=";
                        break;
                    case LT:
                        op = "<";
                        break;
                    case GT:
                        op = ">";
                        break;
                    case LE:
                        op = "<=";
                        break;
                    case GE:
                        op = ">=";
                        break;
                    case ARROW:
                        op = ":>";
                        break;
                    case LARROW:
                        op = "<:";
                        break;
                    default:
                        op = t.text;
                }
                left = new BinaryNode(op, left, right);
                t = tok.peek();
            }
            return left;
        }

        private Node parseAdd() {
            Node left = parseMul();
            while (tok.peek().type == TokenType.PLUS || tok.peek().type == TokenType.MINUS) {
                Token op = tok.next();
                Node right = parseMul();
                left = new BinaryNode(op.text, left, right);
            }
            return left;
        }

        private Node parseMul() {
            Node left = parseUnary();
            while (tok.peek().type == TokenType.MUL || tok.peek().type == TokenType.DIV || tok.peek().type == TokenType.MOD) {
                Token op = tok.next();
                Node right = parseUnary();
                left = new BinaryNode(op.text, left, right);
            }
            return left;
        }

        private Node parseUnary() {
            Token t = tok.peek();
            if (t.type == TokenType.NOT) {
                tok.next();
                Node inner = parseUnary();
                return new UnaryNode("!", inner);
            } else if (t.type == TokenType.MINUS) {
                tok.next();
                Node inner = parseUnary();
                return new UnaryNode("u-", inner);
            }
            return parsePower();
        }

        private Node parsePower() {
            Node left = parsePrimary();
            if (tok.peek().type == TokenType.POW) {
                tok.next();
                Node right = parsePower();
                return new BinaryNode("^", left, right);
            }
            return left;
        }

        private Node parsePrimary() {
            Token t = tok.peek();
            switch (t.type) {
                case NUMBER:
                    tok.next();
                    return new ValueNode(parseNumber(t.text));
                case STRING:
                    tok.next();
                    return new ValueNode(t.text);
                case TRUE:
                    tok.next();
                    return new ValueNode(true);
                case FALSE:
                    tok.next();
                    return new ValueNode(false);
                case NULL:
                    tok.next();
                    return new ValueNode(null);
                case LPAREN:
                    tok.next();
                    Node inner = parseOr();
                    Token r = tok.next();
                    if (r.type != TokenType.RPAREN) throw new RuntimeException("Expecting )");
                    return inner;
                case IDENT:
                    return parseIdentifierOrCall();
                default:
                    throw new RuntimeException("Unexpected token: " + t);
            }
        }

        private Node parseIdentifierOrCall() {
            Token id = tok.next();
            String name = id.text;

            if (tok.peek().type == TokenType.LPAREN) {
                tok.next();
                List<Node> args = new ArrayList<>();
                if (tok.peek().type != TokenType.RPAREN) {
                    while (true) {
                        args.add(parseOr());
                        if (tok.peek().type == TokenType.COMMA) {
                            tok.next();
                        } else break;
                    }
                }
                Token rp = tok.next();
                if (rp.type != TokenType.RPAREN) throw new RuntimeException("Expecting ) after function args");
                if (MATH_FUNCTIONS.contains(name)) {
                    return new FuncNode(name, args);
                } else {
                    return new MethodCallNode(new VarNode(name), name, args);
                }
            }

            Node node = new VarNode(name);
            while (tok.peek().type == TokenType.DOT) {
                tok.next();
                Token next = tok.next();
                if (next.type != TokenType.IDENT) throw new RuntimeException("Expect identifier after '.'");
                String member = next.text;
                if ("class".equals(member)) {
                    if (node instanceof VarNode) {
                        node = new ClassAccessNode((VarNode) node);
                    }
                } else {
                    if (tok.peek().type == TokenType.LPAREN) {
                        tok.next();
                        List<Node> args = new ArrayList<>();
                        if (tok.peek().type != TokenType.RPAREN) {
                            while (true) {
                                args.add(parseOr());
                                if (tok.peek().type == TokenType.COMMA) {
                                    tok.next();
                                } else break;
                            }
                        }
                        Token rp = tok.next();
                        if (rp.type != TokenType.RPAREN) throw new RuntimeException("Expecting ) after method args");
                        node = new MethodCallNode(node, member, args);
                    } else {
                        node = new PropertyAccessNode(node, member);
                    }
                }
            }
            return node;
        }
    }


    private interface Node {
        Object evaluate(Map<String, Object> vars);
    }

    private static class ValueNode implements Node {
        private final Object val;

        ValueNode(Object v) {
            this.val = v;
        }

        public Object evaluate(Map<String, Object> vars) {
            return val;
        }

        public String toString() {
            return "V(" + val + ")";
        }
    }

    private static class VarNode implements Node {
        final String name;

        VarNode(String n) {
            this.name = n;
        }

        public Object evaluate(Map<String, Object> vars) {
            if (vars == null) return null;
            return vars.get(name);
        }

        public String toString() {
            return "Var(" + name + ")";
        }
    }

    private static class ClassAccessNode implements Node {
        final VarNode base;

        ClassAccessNode(VarNode base) {
            this.base = base;
        }

        public Object evaluate(Map<String, Object> vars) {
            Object v = base.evaluate(vars);
            if (v instanceof Class) return v;
            throw new RuntimeException("Expected Class for " + base.name + ".class but got " + (v == null ? "null" : v.getClass()));
        }
    }

    private static class PropertyAccessNode implements Node {
        final Node base;
        final String prop;

        PropertyAccessNode(Node base, String prop) {
            this.base = base;
            this.prop = prop;
        }

        public Object evaluate(Map<String, Object> vars) {
            throw new RuntimeException("Property access '" + prop + "' not supported for safety");
        }
    }

    private static class FuncNode implements Node {
        final String name;
        final List<Node> args;

        FuncNode(String n, List<Node> a) {
            this.name = n;
            this.args = a;
        }

        public Object evaluate(Map<String, Object> vars) {
            List<Double> evalArgs = new ArrayList<>(args.size());
            for (Node n : args) {
                Object v = n.evaluate(vars);
                if (v == null) evalArgs.add(0.0);
                else if (v instanceof Number) evalArgs.add(((Number) v).doubleValue());
                else if (v instanceof String && isNumericString((String) v))
                    evalArgs.add(Double.parseDouble((String) v));
                else throw new RuntimeException("Function " + name + " requires numeric args, got " + v);
            }
            switch (name) {
                // 基本运算
                case "sqrt":
                    if (evalArgs.size() != 1) throw new RuntimeException("sqrt(x) requires 1 argument");
                    return Math.sqrt(evalArgs.get(0));
                case "pow":
                    if (evalArgs.size() != 2) throw new RuntimeException("pow(x,y) requires 2 arguments");
                    return Math.pow(evalArgs.get(0), evalArgs.get(1));
                case "abs":
                    if (evalArgs.size() != 1) throw new RuntimeException("abs(x) requires 1 argument");
                    return Math.abs(evalArgs.get(0));
                case "max":
                    if (evalArgs.size() != 2) throw new RuntimeException("max(x,y) requires 2 arguments");
                    return Math.max(evalArgs.get(0), evalArgs.get(1));
                case "min":
                    if (evalArgs.size() != 2) throw new RuntimeException("min(x,y) requires 2 arguments");
                    return Math.min(evalArgs.get(0), evalArgs.get(1));
                // 对数函数
                case "log":
                    if (evalArgs.size() != 1) throw new RuntimeException("log(x) requires 1 argument");
                    return Math.log(evalArgs.get(0));
                case "log10":
                    if (evalArgs.size() != 1) throw new RuntimeException("log10(x) requires 1 argument");
                    return Math.log10(evalArgs.get(0));
                case "exp":
                    if (evalArgs.size() != 1) throw new RuntimeException("exp(x) requires 1 argument");
                    return Math.exp(evalArgs.get(0));
                // 三角函数
                case "sin":
                    if (evalArgs.size() != 1) throw new RuntimeException("sin(x) requires 1 argument");
                    return Math.sin(evalArgs.get(0));
                case "cos":
                    if (evalArgs.size() != 1) throw new RuntimeException("cos(x) requires 1 argument");
                    return Math.cos(evalArgs.get(0));
                case "tan":
                    if (evalArgs.size() != 1) throw new RuntimeException("tan(x) requires 1 argument");
                    return Math.tan(evalArgs.get(0));
                case "asin":
                    if (evalArgs.size() != 1) throw new RuntimeException("asin(x) requires 1 argument");
                    return Math.asin(evalArgs.get(0));
                case "acos":
                    if (evalArgs.size() != 1) throw new RuntimeException("acos(x) requires 1 argument");
                    return Math.acos(evalArgs.get(0));
                case "atan":
                    if (evalArgs.size() != 1) throw new RuntimeException("atan(x) requires 1 argument");
                    return Math.atan(evalArgs.get(0));
                case "atan2":
                    if (evalArgs.size() != 2) throw new RuntimeException("atan2(y,x) requires 2 arguments");
                    return Math.atan2(evalArgs.get(0), evalArgs.get(1));
                // 双曲函数
                case "sinh":
                    if (evalArgs.size() != 1) throw new RuntimeException("sinh(x) requires 1 argument");
                    return Math.sinh(evalArgs.get(0));
                case "cosh":
                    if (evalArgs.size() != 1) throw new RuntimeException("cosh(x) requires 1 argument");
                    return Math.cosh(evalArgs.get(0));
                case "tanh":
                    if (evalArgs.size() != 1) throw new RuntimeException("tanh(x) requires 1 argument");
                    return Math.tanh(evalArgs.get(0));
                // 取整函数
                case "ceil":
                    if (evalArgs.size() != 1) throw new RuntimeException("ceil(x) requires 1 argument");
                    return Math.ceil(evalArgs.get(0));
                case "floor":
                    if (evalArgs.size() != 1) throw new RuntimeException("floor(x) requires 1 argument");
                    return Math.floor(evalArgs.get(0));
                case "round":
                    if (evalArgs.size() != 1) throw new RuntimeException("round(x) requires 1 argument");
                    return (double) Math.round(evalArgs.get(0));
                // 其他
                case "signum":
                    if (evalArgs.size() != 1) throw new RuntimeException("signum(x) requires 1 argument");
                    return Math.signum(evalArgs.get(0));
                case "toRadians":
                    if (evalArgs.size() != 1) throw new RuntimeException("toRadians(x) requires 1 argument");
                    return Math.toRadians(evalArgs.get(0));
                case "toDegrees":
                    if (evalArgs.size() != 1) throw new RuntimeException("toDegrees(x) requires 1 argument");
                    return Math.toDegrees(evalArgs.get(0));
                case "random": {
                    if (evalArgs.isEmpty()) {
                        return Math.random();
                    } else if (evalArgs.size() == 2) {
                        double min = evalArgs.get(0), max = evalArgs.get(1);
                        if (min > max) {
                            double t = min;
                            min = max;
                            max = t;
                        }
                        return min + Math.random() * (max - min);
                    } else {
                        throw new RuntimeException("random() or random(start,end) requires 0 or 2 arguments");
                    }
                }
                default:
                    throw new RuntimeException("Unsupported function: " + name);
            }
        }
    }

    private static class MethodCallNode implements Node {
        final Node target;
        final String method;
        final List<Node> args;

        MethodCallNode(Node t, String m, List<Node> a) {
            this.target = t;
            this.method = m;
            this.args = a;
        }

        public Object evaluate(Map<String, Object> vars) {
            Object tar = target.evaluate(vars);
            List<Object> argVals = args.isEmpty() ? Collections.emptyList() : new ArrayList<>(args.size());
            for (Node n : args) argVals.add(n.evaluate(vars));

            if (!SAFE_METHODS.contains(method)) {
                throw new RuntimeException("Method '" + method + "' is not allowed for safety");
            }

            if ("contains".equals(method)) {
                Object needle = !argVals.isEmpty() ? argVals.get(0) : null;
                if (tar == null) return false;
                if (tar instanceof Collection) {
                    return ((Collection<?>) tar).contains(needle);
                } else if (tar.getClass().isArray()) {
                    int len = Array.getLength(tar);
                    for (int i = 0; i < len; i++) {
                        Object el = Array.get(tar, i);
                        if (Objects.equals(el, needle)) return true;
                    }
                    return false;
                } else if (tar instanceof String) {
                    String s = (String) tar;
                    return s.contains(String.valueOf(needle));
                } else {
                    throw new RuntimeException("contains is not supported on type: " + tar.getClass());
                }
            }

            throw new RuntimeException("Unsupported method: " + method);
        }
    }

    private static class UnaryNode implements Node {
        final String op;
        final Node inner;

        UnaryNode(String op, Node in) {
            this.op = op;
            this.inner = in;
        }

        public Object evaluate(Map<String, Object> vars) {
            Object v = inner.evaluate(vars);
            if ("!".equals(op)) {
                return !toBoolean(v);
            } else if ("u-".equals(op)) {
                return -toDouble(v);
            }
            throw new RuntimeException("Unknown unary op: " + op);
        }
    }

    private static class BinaryNode implements Node {
        final String op;
        final Node left, right;

        BinaryNode(String op, Node l, Node r) {
            this.op = op;
            this.left = l;
            this.right = r;
        }

        public Object evaluate(Map<String, Object> vars) {
            if ("&&".equals(op)) {
                boolean lv = toBoolean(left.evaluate(vars));
                if (!lv) return false;
                return toBoolean(right.evaluate(vars));
            } else if ("||".equals(op)) {
                boolean lv = toBoolean(left.evaluate(vars));
                if (lv) return true;
                return toBoolean(right.evaluate(vars));
            }

            Object lvObj = left.evaluate(vars);
            Object rvObj = right.evaluate(vars);

            if ("==".equals(op) || "=".equals(op)) {
                if (lvObj == null && rvObj == null) return true;
                if (lvObj == null || rvObj == null) {
                    return false;
                }
                if (lvObj instanceof Number || rvObj instanceof Number
                        || (lvObj instanceof String && isNumericString((String) lvObj))
                        || (rvObj instanceof String && isNumericString((String) rvObj))) {
                    double a = toDouble(lvObj), b = toDouble(rvObj);
                    return Double.compare(a, b) == 0;
                }
                return Objects.equals(lvObj, rvObj);
            } else if ("!=".equals(op)) {
                if (lvObj == null && rvObj == null) return false;
                if (lvObj == null || rvObj == null) return true;
                if (lvObj instanceof Number || rvObj instanceof Number
                        || (lvObj instanceof String && isNumericString((String) lvObj))
                        || (rvObj instanceof String && isNumericString((String) rvObj))) {
                    double a = toDouble(lvObj), b = toDouble(rvObj);
                    return Double.compare(a, b) != 0;
                }
                return !Objects.equals(lvObj, rvObj);
            } else if ("<".equals(op) || ">".equals(op) || "<=".equals(op) || ">=".equals(op)) {
                if (lvObj == null || rvObj == null) {
                    // throw new RuntimeException("Cannot compare null with numeric operator");
                    return false;
                }
                if (lvObj instanceof Number || rvObj instanceof Number
                        || (lvObj instanceof String && isNumericString((String) lvObj))
                        || (rvObj instanceof String && isNumericString((String) rvObj))) {
                    double a = toDouble(lvObj), b = toDouble(rvObj);
                    switch (op) {
                        case "<":
                            return a < b;
                        case ">":
                            return a > b;
                        case "<=":
                            return a <= b;
                        case ">=":
                            return a >= b;
                    }
                }
                String aStr = String.valueOf(lvObj);
                String bStr = String.valueOf(rvObj);
                int cmp = aStr.compareTo(bStr);
                switch (op) {
                    case "<":
                        return cmp < 0;
                    case ">":
                        return cmp > 0;
                    case "<=":
                        return cmp <= 0;
                    case ">=":
                        return cmp >= 0;
                }
            } else if (":>".equals(op) || "<:".equals(op)) {
                if (":>".equals(op)) {
                    return handleArrowOp(lvObj, rvObj);
                } else {
                    // a <: b  ==  b :> a
                    return handleArrowOp(rvObj, lvObj);
                }
            } else if ("+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op) || "%".equals(op) || "^".equals(op)) {
                // 检查操作数是否为整数类型
                boolean leftIsInt = isIntegerType(lvObj);
                boolean rightIsInt = isIntegerType(rvObj);
                boolean bothInt = leftIsInt && rightIsInt;

                if (bothInt) {
                    // 进行整数运算
                    long a = toLong(lvObj);
                    long b = toLong(rvObj);

                    switch (op) {
                        case "+": {
                            long result = a + b;
                            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                                return (int) result;
                            }
                            return result;
                        }
                        case "-": {
                            long result = a - b;
                            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                                return (int) result;
                            }
                            return result;
                        }
                        case "*": {
                            long result = a * b;
                            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                                return (int) result;
                            }
                            return result;
                        }
                        case "/": {
                            if (b == 0) {
                                throw new RuntimeException("Division by zero");
                            }
                            // 整数除法
                            long result = a / b;
                            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                                return (int) result;
                            }
                            return result;
                        }
                        case "%": {
                            if (b == 0) {
                                throw new RuntimeException("Modulo by zero");
                            }
                            long result = a % b;
                            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                                return (int) result;
                            }
                            return result;
                        }
                        case "^": {
                            return Math.pow(a, b);
                        }
                    }
                } else {
                    // 进行浮点数运算
                    double a = toDouble(lvObj);
                    double b = toDouble(rvObj);

                    switch (op) {
                        case "+":
                            return a + b;
                        case "-":
                            return a - b;
                        case "*":
                            return a * b;
                        case "/":
                            if (b == 0.0) {
                                throw new RuntimeException("Division by zero");
                            }
                            return a / b;
                        case "%":
                            if (b == 0.0) {
                                throw new RuntimeException("Modulo by zero");
                            }
                            return a % b;
                        case "^":
                            return Math.pow(a, b);
                    }
                }
            }

            throw new RuntimeException("Unsupported binary op: " + op);
        }

        private Object handleArrowOp(Object leftVal, Object rightVal) {
            Class<?> leftClass = resolveClass(leftVal);
            Class<?> rightClass = resolveClass(rightVal);

            if (rightClass != null) {
                if (leftClass != null) {
                    return rightClass.isAssignableFrom(leftClass);
                } else {
                    return rightClass.isInstance(leftVal);
                }
            } else if (leftClass != null) {
                if (rightVal == null) return false;
                return leftClass.isAssignableFrom(rightVal.getClass());
            } else {
                throw new RuntimeException("At least one side of ':>' must be a Class or a class-name string");
            }
        }
    }

    private static Class<?> resolveClass(Object o) {
        if (o == null) return null;
        if (o instanceof Class) return (Class<?>) o;
        if (o instanceof String) {
            String s = (String) o;
            try {
                return Class.forName(s);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unknown class name: " + s);
            }
        }
        return null;
    }

}
