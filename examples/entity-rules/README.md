# Entity cleanup scripts

Add one trusted Java file to `config/vanilla.xin/aotake_sweep/scripts/` on the
server. Keep the filename equal to the public `EntityRule` class name and use
`xin.vanilla.banira.generated` as its package. Restart the server or run
`/aotake config rules reload`, then select `script:<lowercase filename without .java>`
in the appropriate rule list. Dropping in a file does not enable cleanup.

| Example | Rule ID | Where to use it |
| --- | --- | --- |
| [CobblemonWild.java](CobblemonWild.java) | `script:cobblemonwild` | Entity cleanup list in `BLACK` mode. Matches unowned Cobblemon Pokémon without the battle lock. Requires the matching Cobblemon API, verified against Forge 1.20.1 / Cobblemon 1.5.2. |
| [CreateProcessingProtection.java](CreateProcessingProtection.java) | `script:createprocessingprotection` | Item protection (red list). Matches item entities with positive `CreateData.Processing.Time`. The default protection rule already includes this check. |

Keep existing `rules/manifest.json`, `rules/sources/` and `rules/bindings/`:
generated legacy rules still read their compiled binding layout there. Existing
standalone files in `rules/local/` remain usable. You can move those files to
`scripts/` without changing their IDs; a same-name file in `scripts/` takes priority.
Forced cleanup options can bypass configured filters.
