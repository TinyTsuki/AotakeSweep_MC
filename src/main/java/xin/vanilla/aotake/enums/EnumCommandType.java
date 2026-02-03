package xin.vanilla.aotake.enums;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Getter;
import net.minecraft.commands.CommandSourceStack;
import xin.vanilla.aotake.command.impl.*;

import javax.annotation.Nullable;
import java.util.function.Supplier;

@Getter
public enum EnumCommandType {
    HELP(HelpCommand::help, false, false),
    LANGUAGE(LanguageCommand::lang, false, false),
    LANGUAGE_CONCISE(),
    VIRTUAL_OP(VirtualOpCommand::vop),
    VIRTUAL_OP_CONCISE(),
    DUSTBIN_OPEN(DustbinCommand::open),
    DUSTBIN_OPEN_CONCISE(),
    DUSTBIN_OPEN_OTHER(true),
    DUSTBIN_OPEN_OTHER_CONCISE(true),
    DUSTBIN_CLEAR(DustbinCommand::clear),
    DUSTBIN_CLEAR_CONCISE(),
    DUSTBIN_DROP(DustbinCommand::drop),
    DUSTBIN_DROP_CONCISE(),
    CACHE_CLEAR(CacheCommand::clear),
    CACHE_CLEAR_CONCISE(),
    CACHE_DROP(CacheCommand::drop),
    CACHE_DROP_CONCISE(),
    SWEEP(SweepCommand::sweep),
    SWEEP_CONCISE(),
    CLEAR_DROP(ClearDropCommand::clear),
    CLEAR_DROP_CONCISE(),
    DELAY_SWEEP(DelayCommand::delay),
    DELAY_SWEEP_CONCISE(),
    CATCH_PLAYER(true),
    CONFIG(ConfigCommand::config, true, true),
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

    private final Supplier<LiteralArgumentBuilder<CommandSourceStack>> instance;

    EnumCommandType() {
        this.instance = null;
        this.ignore = false;
        this.op = !this.concise;
    }

    EnumCommandType(boolean ig) {
        this.instance = null;
        this.ignore = ig;
        this.op = !this.concise;
    }

    EnumCommandType(@Nullable Supplier<LiteralArgumentBuilder<CommandSourceStack>> instance) {
        this.instance = instance;
        this.ignore = false;
        this.op = !this.concise;
    }

    EnumCommandType(@Nullable Supplier<LiteralArgumentBuilder<CommandSourceStack>> instance, boolean ig, boolean op) {
        this.instance = instance;
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
