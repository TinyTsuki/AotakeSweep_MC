package xin.vanilla.aotake.api.rule;

/** Trusted local rule. Failure is distinct from a non-matching entity. */
@FunctionalInterface
public interface EntityRule {
    boolean test(EntityRuleContext context);
}
