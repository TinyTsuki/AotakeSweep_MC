# Entity cleanup scripts

Add one trusted Java file to `config/vanilla.xin/aotake_sweep/scripts/` on the
server. Keep the filename equal to the public `EntityRule` class name and use
`xin.vanilla.banira.generated` as its package. Restart the server or run
`/aotake config rules reload`, then select `script:<lowercase filename without .java>`
in the appropriate rule list. Dropping in a file does not enable cleanup.

| Example | Rule ID | Where to use it |
| --- | --- | --- |
| [CobblemonWild.java](CobblemonWild.java) | `script:cobblemonwild` | Entity cleanup list in `BLACK` mode. Matches unowned Cobblemon Pokémon without the battle lock. Requires the matching Cobblemon API, verified against Forge 1.20.1 / Cobblemon 1.5.2. |
| [CreateProcessingProtection.java](CreateProcessingProtection.java) | `script:createprocessingprotection` | Optional example for a custom protection list. It matches item entities with positive `CreateData.Processing.Time`. To exclude matching entities from cleanup, put the rule on the relevant entity or chunk-check list in `WHITE` mode. The built-in rules already check the same processing value. |

Do not use the red list to protect these items from cleanup. If a matching item
has already been selected for cleanup, the red list prevents its recycling;
the item is still removed.

## Reading NBT in single-file rules

- `persistentNumber(path...)` retains the original boxed byte, short, int, long, float, or double.
- `persistentString(path...)`, `persistentBoolean(path...)` and `persistentValue(MyType.class, path...)` return `null` if the path is missing or has a different type. Boolean reads accept NBT byte values 0 and 1.
- `persistentList(path...)` and `persistentCompound(path...)` return detached, read-only snapshots, including nested containers. Read a narrower path for large containers.
- Each path argument is a literal compound key. A decimal path argument indexes a list or array when the current value is a container of that kind. Use `hasPersistentValue(path...)` to test for presence without copying the value.

Forge and NeoForge use entity persistent NBT. Fabric uses the entity's serialized
NBT for that evaluation; a mod-specific value is available only if the mod saves
it there. The existing `persistentInt` keeps its original integer conversion and
compound-only paths for scripts that already use it.

Keep existing `rules/manifest.json`, `rules/sources/` and `rules/bindings/`:
generated legacy rules still read their compiled binding layout there. Existing
standalone files in `rules/local/` remain usable. You can move those files to
`scripts/` without changing their IDs; a same-name file in `scripts/` takes priority.
Forced cleanup options can bypass configured filters.
