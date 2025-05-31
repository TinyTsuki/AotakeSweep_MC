package xin.vanilla.aotake.enums;

import lombok.Getter;

@Getter
public enum EnumCommandType {
    HELP(false, false),
    LANGUAGE(false, false),
    LANGUAGE_CONCISE(),
    VIRTUAL_OP(),
    VIRTUAL_OP_CONCISE(),
    DUSTBIN_OPEN(),
    DUSTBIN_OPEN_CONCISE(),
    DUSTBIN_CLEAR(),
    DUSTBIN_CLEAR_CONCISE(),
    DUSTBIN_DROP(),
    DUSTBIN_DROP_CONCISE(),
    CACHE_CLEAR(),
    CACHE_CLEAR_CONCISE(),
    CACHE_DROP(),
    CACHE_DROP_CONCISE(),
    SWEEP(),
    SWEEP_CONCISE(),
    CLEAR_DROP(),
    CLEAR_DROP_CONCISE(),
    ;

    /**
     * 是否在帮助信息中忽略
     */
    private final boolean ignore;
    /**
     * 是否简短指令
     */
    private final boolean concise = this.name().endsWith("_CONCISE");
    /**
     * 是否被虚拟权限管理
     */
    private final boolean op;

    EnumCommandType() {
        this.ignore = false;
        this.op = !this.concise;
    }

    EnumCommandType(boolean ig) {
        this.ignore = ig;
        this.op = !this.concise;
    }

    EnumCommandType(boolean ig, boolean op) {
        this.ignore = ig;
        this.op = !this.concise && op;
    }

    public int getSort() {
        return this.ordinal();
    }

    public EnumCommandType replaceConcise() {
        if (this.name().endsWith("_CONCISE")) {
            return EnumCommandType.valueOf(this.name().replace("_CONCISE", ""));
        }
        return this;
    }
}
