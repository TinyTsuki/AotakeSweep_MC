package xin.vanilla.banira.generated;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.rule.EntityRuleContext;

public final class CobblemonWild implements EntityRule {
    @Override
    public boolean test(EntityRuleContext context) {
        if (!context.matchesResource("cobblemon", "pokemon") || context.hasOwner()) return false;
        PokemonEntity pokemon = (PokemonEntity) context.nativeEntity(PokemonEntity.class);
        return !pokemon.getBusyLocks().contains(PokemonEntity.BATTLE_LOCK);
    }
}
