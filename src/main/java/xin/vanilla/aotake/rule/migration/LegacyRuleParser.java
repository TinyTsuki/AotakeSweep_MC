package xin.vanilla.aotake.rule.migration;

import xin.vanilla.aotake.rule.RuleDiagnostic;

import java.util.*;

import static xin.vanilla.aotake.rule.migration.RuleAst.Declaration;
import static xin.vanilla.aotake.rule.migration.RuleAst.Node;

/**
 * A bounded syntax-only parser. No evaluation, reflection or Java text interpolation.
 */
public final class LegacyRuleParser {
    private final String source;
    private int cursor, depth, nodes;
    private Token token;

    private LegacyRuleParser(String source) {
        this.source = source;
        next();
    }

    public static RuleAst parse(String source) {
        if (source == null || source.length() > 65536) throw new RuleDiagnostic(0, "Missing or oversized legacy rule");
        return new LegacyRuleParser(source).parseRule();
    }

    private RuleAst parseRule() {
        List<Declaration> declarations = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (!is("->")) do {
            Token name = token;
            if (!name.kind.equals("id")) fail("Expected a variable declaration");
            if (!names.add(name.text)) fail("Duplicate variable: " + name.text);
            next();
            String kind = "builtin", payload = name.text;
            if (take("=")) {
                if (token.kind.equals("string")) {
                    kind = "literal";
                    payload = token.text;
                    next();
                } else if (is("[") || is("{") || is("<")) {
                    String open = token.text, close = open.equals("[") ? "]" : open.equals("{") ? "}" : ">";
                    kind = open.equals("[") ? "nbt" : open.equals("{") ? "field" : "synced";
                    int start = cursor, level = 1;
                    while (cursor < source.length() && level > 0) {
                        char c = source.charAt(cursor++);
                        if (c == '\\' && cursor < source.length()) {
                            cursor++;
                            continue;
                        }
                        if (c == open.charAt(0)) level++;
                        if (c == close.charAt(0)) level--;
                    }
                    if (level != 0) fail("Unclosed binding path");
                    payload = source.substring(start, cursor - 1).trim();
                    next();
                } else {
                    kind = "nbt";
                    int start = token.offset;
                    while (!is(",") && !is("->") && !is("end")) next();
                    payload = source.substring(start, token.offset).trim();
                }
                if (payload.isEmpty()) fail("Empty binding path");
            }
            declarations.add(new Declaration(name.text, kind, payload, name.offset));
            if (declarations.size() > 128) fail("Too many declarations");
        } while (take(","));
        expect("->");
        Node root = expression(1);
        expect("end");
        return new RuleAst(source, declarations, root);
    }

    private Node expression(int min) {
        if (++depth > 128) fail("Expression nesting exceeds 128");
        Node left = prefix();
        while (precedence(token.text) >= min) {
            Token operator = token;
            int priority = precedence(operator.text);
            next();
            left = node(operator.text, "", operator.offset, left, expression(priority + 1));
        }
        depth--;
        return left;
    }

    private Node prefix() {
        Token current = token;
        next();
        Node result;
        if (current.text.equals("!") || current.text.equals("-") || current.text.equals("+") || current.text.equals("~")) {
            result = node("unary", current.text, current.offset, expression(11));
        } else if (current.text.equals("(")) {
            result = expression(1);
            expect(")");
        } else if (current.kind.equals("string") || current.kind.equals("number"))
            result = node(current.kind, current.text, current.offset);
        else if (current.kind.equals("id")) {
            if (current.text.equals("null") || current.text.equals("true") || current.text.equals("false"))
                result = node(current.text, current.text, current.offset);
            else if (is("(")) result = call("math", current.text, current.offset, null);
            else result = node("variable", current.text, current.offset);
        } else throw new RuleDiagnostic(current.offset, "Expected an expression");
        while (take(".")) {
            Token method = token;
            if (!method.kind.equals("id")) fail("Expected method name");
            next();
            result = result.op.equals("variable") && result.text.equals("Math")
                    ? call("math", method.text, method.offset, null) : call("method", method.text, method.offset, result);
        }
        return result;
    }

    private Node call(String kind, String name, int offset, Node target) {
        expect("(");
        List<Node> args = new ArrayList<>();
        if (target != null) args.add(target);
        if (!is(")")) do {
            args.add(expression(1));
        } while (take(","));
        expect(")");
        return node(kind, name, offset, args.toArray(new Node[0]));
    }

    private Node node(String op, String text, int offset, Node... children) {
        if (++nodes > 4096) fail("Too many expression nodes");
        Node result = new Node(op, text, offset, children);
        if (result.height > 128) throw new RuleDiagnostic(offset, "Expression tree exceeds depth 128");
        return result;
    }

    private static int precedence(String op) {
        switch (op) {
            case "||":
                return 1;
            case "&&":
                return 2;
            case "|":
                return 3;
            case "^":
                return 4;
            case "&":
                return 5;
            case "==":
            case "!=":
            case "<>":
                return 6;
            case "<":
            case ">":
            case "<=":
            case ">=":
            case ":>":
            case "<:":
                return 7;
            case "<<":
            case ">>":
            case ">>>":
                return 8;
            case "+":
            case "-":
                return 9;
            case "*":
            case "/":
            case "%":
                return 10;
            default:
                return 0;
        }
    }

    private boolean is(String text) {
        return token.text.equals(text);
    }

    private boolean take(String text) {
        if (!is(text)) return false;
        next();
        return true;
    }

    private void expect(String text) {
        if (!take(text)) fail("Expected " + text);
    }

    private void fail(String message) {
        throw new RuleDiagnostic(token.offset, message);
    }

    private void next() {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        int start = cursor;
        if (cursor == source.length()) {
            token = new Token("end", "end", cursor);
            return;
        }
        char c = source.charAt(cursor++);
        if (c == '\'' || c == '"') {
            StringBuilder text = new StringBuilder();
            boolean closed = false;
            while (cursor < source.length()) {
                char ch = source.charAt(cursor++);
                if (ch == c) {
                    closed = true;
                    break;
                }
                if (ch == '\\') {
                    if (cursor == source.length()) break;
                    char escape = source.charAt(cursor++);
                    switch (escape) {
                        case 'n':
                            ch = '\n';
                            break;
                        case 'r':
                            ch = '\r';
                            break;
                        case 't':
                            ch = '\t';
                            break;
                        case 'b':
                            ch = '\b';
                            break;
                        case 'f':
                            ch = '\f';
                            break;
                        case '\\':
                        case '\'':
                        case '"':
                        case ',':
                            ch = escape;
                            break;
                        default:
                            throw new RuleDiagnostic(cursor - 2, "Unsupported string escape");
                    }
                }
                text.append(ch);
            }
            if (!closed) throw new RuleDiagnostic(start, "Unclosed string");
            token = new Token("string", text.toString(), start);
            return;
        }
        if (Character.isJavaIdentifierStart(c)) {
            while (cursor < source.length() && Character.isJavaIdentifierPart(source.charAt(cursor))) cursor++;
            token = new Token("id", source.substring(start, cursor), start);
            return;
        }
        if (Character.isDigit(c) || c == '.' && cursor < source.length() && Character.isDigit(source.charAt(cursor))) {
            while (cursor < source.length()) {
                char ch = source.charAt(cursor);
                if (Character.isDigit(ch) || ch == '.' || ch == 'e' || ch == 'E' || ch == 'l' || ch == 'L' || ch == 'f' || ch == 'F' || ch == 'd' || ch == 'D'
                        || (ch == '+' || ch == '-') && (source.charAt(cursor - 1) == 'e' || source.charAt(cursor - 1) == 'E'))
                    cursor++;
                else break;
            }
            token = new Token("number", source.substring(start, cursor), start);
            return;
        }
        for (String op : Arrays.asList(">>>", "->", "&&", "||", "==", "!=", "<=", ">=", ":>", "<:", "<>", "<<", ">>")) {
            if (source.startsWith(op, start)) {
                cursor = start + op.length();
                token = new Token("op", op, start);
                return;
            }
        }
        if ("=,()[]{}<>.+-*/%^!~&|:".indexOf(c) < 0) throw new RuleDiagnostic(start, "Unexpected character: " + c);
        token = new Token("op", String.valueOf(c), start);
    }

    private static final class Token {
        final String kind, text;
        final int offset;

        Token(String kind, String text, int offset) {
            this.kind = kind;
            this.text = text;
            this.offset = offset;
        }
    }
}
