package xin.vanilla.aotake.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class SafeExpressionEvaluator {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList(
            "sqrt", "pow", "log", "sin", "cos", "abs", "random"
    ));
    private static final Set<String> OPS = new HashSet<>(Arrays.asList(
            "<=", ">=", "!=", "<>", "==", "=", "<", ">"
    ));

    private final String expression;

    private final Map<String, Object> vars = new HashMap<>();

    public SafeExpressionEvaluator(String expression) {
        this.expression = expression;
    }

    public SafeExpressionEvaluator setVar(String name, Object value) {
        this.vars.put(name, value);
        return this;
    }

    public SafeExpressionEvaluator putVar(String name, Object value) {
        return setVar(name, value);
    }

    public double evaluate() {
        return evaluateExpressionWithLogic(expression.replaceAll("\\s+", ""));
    }

    public double evaluate(Map<String, Object> vars) {
        this.vars.clear();
        this.vars.putAll(vars);
        return evaluate();
    }

    private double evaluateExpressionWithLogic(String expr) {
        for (String op : OPS) {
            int idx = indexOfOutsideQuotes(expr, op);
            if (idx != -1) {
                String left = expr.substring(0, idx);
                String right = expr.substring(idx + op.length());

                String leftStr = tryParseString(left.trim());
                String rightStr = tryParseString(right.trim());
                if (leftStr != null && rightStr != null) {
                    switch (op) {
                        case "==":
                        case "=":
                            return leftStr.equals(rightStr) ? 1.0 : 0.0;
                        case "!=":
                        case "<>":
                            return !leftStr.equals(rightStr) ? 1.0 : 0.0;
                        default:
                            LOGGER.error("String comparisons only support '=' and '!=' operators");
                            return 0;
                    }
                }

                // 否则按数学表达式处理
                double lv = parseExpression(left);
                double rv = parseExpression(right);
                switch (op) {
                    case "<":
                        return lv < rv ? 1.0 : 0.0;
                    case ">":
                        return lv > rv ? 1.0 : 0.0;
                    case "<=":
                        return lv <= rv ? 1.0 : 0.0;
                    case ">=":
                        return lv >= rv ? 1.0 : 0.0;
                    case "=":
                    case "==":
                        return lv == rv ? 1.0 : 0.0;
                    case "!=":
                    case "<>":
                        return lv != rv ? 1.0 : 0.0;
                }
            }
        }

        // 无逻辑符号，直接求值
        return parseExpression(expr);
    }

    private double parseExpression(String expr) {
        Deque<Double> numbers = new ArrayDeque<>();
        Deque<Character> operators = new ArrayDeque<>();

        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sb.append(expr.charAt(i++));
                }
                numbers.push(Double.parseDouble(sb.toString()));
                continue;
            }

            if (Character.isLetter(c)) {
                StringBuilder sb = new StringBuilder();
                while (i < expr.length() && Character.isLetter(expr.charAt(i))) {
                    sb.append(expr.charAt(i++));
                }
                String name = sb.toString();

                if (vars.containsKey(name)) {
                    Object val = vars.get(name);
                    if (val instanceof Double) {
                        numbers.push((Double) val);
                    } else {
                        throw new RuntimeException("Variable \"" + name + "\" is not of a numeric type");
                    }
                } else if (FUNCTIONS.contains(name)) {
                    if (i >= expr.length() || expr.charAt(i) != '(') {
                        throw new RuntimeException("Function " + name + " must be followed by '('");
                    }
                    i++; // skip '('
                    int start = i, depth = 1;
                    while (i < expr.length() && depth > 0) {
                        if (expr.charAt(i) == '(') depth++;
                        else if (expr.charAt(i) == ')') depth--;
                        i++;
                    }
                    if (depth != 0) throw new RuntimeException("Mismatched parentheses in function " + name);

                    String argExpr = expr.substring(start, i - 1);
                    List<Double> args = splitArguments(argExpr);
                    numbers.push(applyFunction(name, args));
                } else {
                    throw new RuntimeException("Unknown identifier: " + name);
                }
                continue;
            }

            if (c == '(') {
                operators.push(c);
                i++;
            } else if (c == ')') {
                while (!operators.isEmpty() && operators.peek() != '(') {
                    applyOperator(numbers, operators.pop());
                }
                if (operators.isEmpty() || operators.pop() != '(') {
                    throw new RuntimeException("Mismatched parentheses");
                }
                i++;
            } else if ("+-*/^".indexOf(c) >= 0) {
                while (!operators.isEmpty() && precedence(operators.peek()) >= precedence(c)) {
                    applyOperator(numbers, operators.pop());
                }
                operators.push(c);
                i++;
            } else {
                throw new RuntimeException("Unexpected character: " + c);
            }
        }

        while (!operators.isEmpty()) {
            applyOperator(numbers, operators.pop());
        }

        if (numbers.size() != 1) {
            throw new RuntimeException("Invalid expression");
        }

        return numbers.pop();
    }

    private int precedence(char op) {
        if (op == '+' || op == '-') return 1;
        if (op == '*' || op == '/') return 2;
        if (op == '^') return 3;
        return 0;
    }

    private void applyOperator(Deque<Double> numbers, char op) {
        double b = numbers.pop();
        double a = numbers.pop();
        switch (op) {
            case '+':
                numbers.push(a + b);
                break;
            case '-':
                numbers.push(a - b);
                break;
            case '*':
                numbers.push(a * b);
                break;
            case '/':
                numbers.push(a / b);
                break;
            case '^':
                numbers.push(Math.pow(a, b));
                break;
            default:
                throw new RuntimeException("Unknown operator: " + op);
        }
    }

    private List<Double> splitArguments(String expr) {
        List<Double> args = new ArrayList<>();
        int depth = 0;
        int lastSplit = 0;

        for (int i = 0; i <= expr.length(); i++) {
            char c = i < expr.length() ? expr.charAt(i) : ',';
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                String arg = expr.substring(lastSplit, i);
                args.add(parseExpression(arg));
                lastSplit = i + 1;
            }
        }
        return args;
    }

    private double applyFunction(String name, List<Double> args) {
        if ("sqrt".equals(name)) return Math.sqrt(args.get(0));
        if ("pow".equals(name)) return Math.pow(args.get(0), args.get(1));
        if ("log".equals(name)) return Math.log(args.get(0));
        if ("sin".equals(name)) return Math.sin(args.get(0));
        if ("cos".equals(name)) return Math.cos(args.get(0));
        if ("abs".equals(name)) return Math.abs(args.get(0));
        if ("random".equals(name)) {
            if (args.size() != 2) throw new RuntimeException("random(start, end) need 2 arguments");
            double min = args.get(0);
            double max = args.get(1);
            if (min > max) {
                double tmp = min;
                min = max;
                max = tmp;
            }
            return min + (Math.random() * (max - min));
        }
        throw new RuntimeException("Unsupported function: " + name);
    }

    private String tryParseString(String expr) {
        if ((expr.startsWith("\"") && expr.endsWith("\"") || (expr.startsWith("'") && expr.endsWith("'")))) {
            return expr.substring(1, expr.length() - 1);
        }
        if (vars.containsKey(expr)) {
            Object val = vars.get(expr);
            if (val instanceof String) {
                return (String) val;
            }
        }
        return null;
    }

    private int indexOfOutsideQuotes(String str, String target) {
        boolean inQuotes = false;
        for (int i = 0; i <= str.length() - target.length(); i++) {
            if (str.charAt(i) == '"') {
                inQuotes = !inQuotes;
            }
            if (!inQuotes && str.startsWith(target, i)) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("team", "Red");
        vars.put("playerTeam", "Red");
        vars.put("name", "Steve");
        vars.put("playerX", 100.0);

        System.out.println(new SafeExpressionEvaluator("team = playerTeam").evaluate(vars));
        System.out.println(new SafeExpressionEvaluator("name = 'Alex'").evaluate(vars));
        System.out.println(new SafeExpressionEvaluator("name <> \"Alex\"").evaluate(vars));
        System.out.println(new SafeExpressionEvaluator("name == 'Steve'").evaluate(vars));
        System.out.println(new SafeExpressionEvaluator("playerX + 20 > 110").evaluate(vars));
        System.out.println(new SafeExpressionEvaluator("playerX + playerX < playerX * 2.5").evaluate(vars));
        System.out.println(new SafeExpressionEvaluator("playerX + playerX == playerX * 2").evaluate(vars));
    }

}
