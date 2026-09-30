package xin.vanilla.aotake.rule.migration;

import java.util.*;

public final class RuleAst {
    public final String source;
    public final List<Declaration> declarations;
    public final Node expression;
    public RuleAst(String source, List<Declaration> declarations, Node expression) {
        this.source = source;
        this.declarations = Collections.unmodifiableList(new ArrayList<>(declarations));
        this.expression = expression;
    }
    public static final class Declaration {
        public final String name, kind, payload;
        public final int offset;
        public Declaration(String name, String kind, String payload, int offset) {
            this.name = name; this.kind = kind; this.payload = payload; this.offset = offset;
        }
    }
    public static final class Node {
        public final String op, text;
        public final int offset, height;
        public final List<Node> args;
        public Node(String op, String text, int offset, Node... args) {
            this.op = op; this.text = text; this.offset = offset;
            this.args = Collections.unmodifiableList(Arrays.asList(args.clone()));
            int childHeight = 0;
            for (Node child : args) childHeight = Math.max(childHeight, child.height);
            this.height = childHeight + 1;
        }
    }
}
