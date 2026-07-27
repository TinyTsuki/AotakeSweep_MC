<div align="center">

| [中文](../README.md) | [English](README_en.md) | [日本語](README_ja.md) |
|:------------------:|:-----------------------:|:-------------------:|

<img src="../assets/logo.png" alt="Aotake Sweep" width="160" />

# Aotake Sweep

**A timed cleanup mod for Minecraft Forge, Fabric, and NeoForge.**

</div>

---

## Table of Contents

- [Aotake Sweep](#aotake-sweep)
    - [Table of Contents](#table-of-contents)
    - [Meaning](#meaning)
    - [Introduction](#introduction)
    - [Features](#features)
    - [Configuration](#configuration)
    - [Commands](#commands)
    - [Entity Filter](#entity-filter)
    - [Building](#building)
    - [License](#license)

## Meaning

- **Aotake (竹叶)**: Bamboo leaves have effects like clearing heat and relieving annoyance; green bamboo symbolizes
  freshness, purity, and elegance.
- **Sweep (清)**: To clean, sweep, or clear away.
- **Aotake Sweep (竹叶清)**: With the fresh power of bamboo leaves, sweep away dust and clutter, making the world as
  clean as new, peaceful, and elegant.

## Introduction

This project is for Minecraft (Neo)Forge servers, implementing timed cleanup of dropped items and entities.
This mod is required on the server side and optional on the client side.

## Features

- **Recycling Strategy**: Optional strategies for handling overflow during garbage collection.
- **Timed Cleanup**: Automatically cleans up dropped items, arrows, etc., at regular intervals.
- **Manual Cleanup**: Allows triggering cleanup via commands, with customizable dimensions and range.
- **Auto Cleanup**: Automatically triggers sweeping when there are too many entities (only sweepable ones) in a chunk.
- **Safe Cleanup**: Configurable whitelist and blacklist, configurable to ignore items on specific blocks.
- **Multi-page Dustbin**: Customizable number of dustbin pages, no more worrying about insufficient capacity.
- **Chunk Vaults**: Keep items recovered from overloaded chunks separate from the global dustbin, with listing, opening,
  and per-player access grants.
- **Custom Filters**: Customize entity filters using [Expressions](#entity-filter) based on your needs.
- **Poor Translation**: Text descriptions might be ambiguous or unclear (not just in English).
- **Poor Code**: Bad code + negligent testing = a bunch of smelly bugs.

## TODO

- [ ] **Heatmap**: Generate a heatmap of item drop frequency based on chunk, coordinates, and item type.

---

## Configuration

You can find the mod-related configurations in the following paths. Details are not repeated here; please refer to the
comments in the Forge default configuration files.

### Shared Files

- Countdown Message Config: `config/aotake_sweep-warning.json`
- Server Dustbin Data: `world/data/world_trash_data.dat`
- Drop Statistics: `world/stats/aotake_sweep/*.json` (stored by date, e.g. `2025-02-24.json`)
- Vanilla Xin Series Common Config: `config/vanilla.xin/common_config.json`
- Vanilla Xin Series Player Data: `world/playerdata/vanilla.xin/*.nbt`

### Server Configuration Highlights (Dustbin)

- **dustbinPersistent**: Whether to persist dustbin data
- **dropStatsFileLimit**: Maximum number of drop statistics files (by date)
    - `-1`: Disabled
    - `0`: Unlimited
    - `1`–`3650`: Keep the most recent N days of statistics; older files are deleted when exceeded

### Forge

- Common Config (Both Sides): `config/aotake_sweep-common.toml`
- Client Config: `config/aotake_sweep-client.toml`
- Server Config: `world/serverconfig/aotake_sweep-server.toml`

### NeoForge

- Common Config (Both Sides): `config/aotake_sweep-common.toml`
- Client Config: `config/aotake_sweep-client.toml`
- Server Config: `config/aotake_sweep-server.toml`

### Fabric

- Client Config: `config/aotake_sweep-client.toml`
- Server Config: `config/aotake_sweep-server.toml`

---

## Commands

By default, use with the prefix `/aotake`.

- **dustbin**: Open the dustbin.
    - **Arguments**:
        1. `[<Page>]`
- **chunkvault**: Inspect items recovered from overloaded chunks. Chunk vaults are stored separately from the global
  dustbin.
    - **Arguments**:
        1. `list [<Page>]`: List vault IDs.
        2. `open <ID> [<Page>]`: Open a vault.
        3. `grant <ID> <Players>`: Grant players access to a vault.
- **sweep**: Manually trigger sweeping.
    - **Arguments**:
        1. `[<Range>]`
        2. `[<Dimension>]`
- **delay**: Delay the next cleanup trigger time.
    - **Arguments**:
        1. `[<Seconds>]`
- **killitem**: Clean up dropped items.
    - **Arguments**:
        1. `[<Range>] [<Include Entities>] [<Ignore Entity List Filter>]`
        2. `[<Dimension>] [<Include Entities>] [<Ignore Entity List Filter>]`
- **clearcache**: Clear the garbage in the buffer.
- **dropcache**: Clear the garbage in the buffer by dropping them.
- **cleardustbin**: Clear the dustbin.
    - **Arguments**:
        1. `[<Page>]`
- **dropdustbin**: Clear the dustbin by dropping items.
    - **Arguments**:
        1. `[<Page>]`
- **opv**: Grant a player permission to use a specific command.
    - **Arguments**:
        1. `<Operation> <Player> [<Command Type List>]`
- **language**: Set the player's default language.
    - **Arguments**:
        1. `<Language>`
- **config**: Modify configuration. Do not use this command to modify complex `server` and `common` configurations.
    - **Arguments**:
        1. `mode <Mode>`: Reset configuration to a preset mode.
        2. `disable <Disable mod>`: Temporarily disable mod functions.
        3. `player <Config Key> <Config Value>`: Modify player configuration.
        4. `server <Config Key> <Config Value>`: Modify server configuration.
        5. `common <Config Key> <Config Value>`: Modify common configuration.

---

## Entity Filter

For convenience, entity filter expressions are called **`AotakeEL`** below.
Configuration keys that support AotakeEL: `entityList`, `entityRedlist`, `catchEntity`, `chunkCheckEntityList`.

### Examples

#### Entity ID

1. A specific entity, e.g. arrow `minecraft:arrow`
2. All entities from one mod, e.g. [Diligent Stalker](https://github.com/Mafuyu404/DiligentStalker) `diligentstalker:*`
3. A given entity id in any mod, e.g. arrow `*:arrow`

#### AotakeEL

1. Items processed by a fan in [Create](https://github.com/Creators-of-Create/Create)
   `clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> clazz :> itemClazz && createProcessing > 0`
2. Dead ice/fire dragons in [Ice and Fire](https://github.com/AlexModGuy/Ice_and_Fire)
   `resource, dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD> -> (resource == 'iceandfire:fire_dragon' || resource == 'iceandfire:ice_dragon') && dead == true`
3. Reflection from the entity root (unlike `<>`: never reads `DataParameter`)
   `t = {persistentData.someKey}, tick -> t != null && tick > 60`
4. Reflection with an explicit declaring class (same class-detection rules as sync paths); segments may use `.` or `:`
   `v = {com.example.Entity:someField:child}` (same as `{com.example.Entity.someField.child}`)

### Notes

1. A rule can be **only** an entity id; see [Entity ID](#entity-id).
2. Entity ids are expanded to AotakeEL, e.g.:
    1. `minecraft:arrow` → `resource -> resource == 'minecraft:arrow'` or
       `namespace, path -> namespace == 'minecraft' && path == 'arrow'`
    2. `diligentstalker:*` → `namespace -> namespace == 'diligentstalker'`
    3. `*:arrow` → `path -> path == 'arrow'`
3. **AotakeEL shape**: `varDecl1, ..., varDeclN -> booleanExpr`
   Commas, `->`, and `=` can be escaped with a leading `\` when they must appear literally.

#### Variable sources (left-hand side)

Each item is either **`name = rhs`** or **`name`** alone (predefined variable). The **rhs** shape selects the source:

| Source               | Example                                                                  | What it does                                                                                                                                                                                                                                                                                                                                                                                                                                  |
|----------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Predefined**       | `resource`, `tick`, `clazz`                                              | Common entity facts (registry id, position, class, taming, …); see **Built-in variables** below. **Cheapest.**                                                                                                                                                                                                                                                                                                                                |
| **Literal**          | `tag = 'foo'`, `tag = "bar"`                                             | Constant strings for comparisons. Negligible cost.                                                                                                                                                                                                                                                                                                                                                                                            |
| **NBT path**         | `t = [SomeMod.Data]` or `t = SomeMod.Data`                               | Reads `entity.getPersistentData()`; numbers → `Number`, collections → arrays, else string; missing key → `null`. **Cost: NBT walk.**                                                                                                                                                                                                                                                                                                          |
| **`<…>` sync path**  | `dead = <pkg.Entity:FLAG>`, `v = <:FLAG>`, `v = <:a:b:0>`, `v = <a:b.c>` | Tries `DataParameter` first; **if the first segment is not a sync key**, falls back to reflection per the path rules below. Each segment can be a **field name, Map key, or list/array index**. **Cost: low when the parameter cache hits; grows with chain depth / reflection.**                                                                                                                                                             |
| **`{…}` reflection** | `x = {field.sub.0}`, `x = {pkg.Entity:field:child}`                      | **Never** reads `DataParameter`. Optional **declaring class FQN** (parsed like `<>`); the first field is read on `entity` in that class context, then nested walk. Without a class, the chain starts at `Entity`; **without a class**, `.` and `:` are equivalent delimiters; use `\.` / `\:` for literal dots/colons. `Optional` / `OptionalInt` / `Atomic*` are normalized for the evaluator. **Cost: similar to a deep reflection chain.** |

**Path parsing (`<>` and `{}` shared)**

- **Declaring class**: Scan from the start; at each **unescaped** `.` or `:`, take the prefix and try `Class.forName`;
  the **longest** successful prefix is the declaring class; the remainder is the field chain (FQN uses `.` only inside
  the class part).
- **When a declaring class is present**: Split the **tail** on unescaped **`.`** and **`:`** (mix allowed).
- **`<>` with no declaring class** (back-compat): Split **only** on unescaped **`:`**; if there is **one** segment, keep
  it **as a whole** (`.` stays part of the name, e.g. `<MODEL_DEAD>` or `<foo.bar>`).
- **`{}` with no declaring class**: Split the full inner path on **`.`** and **`:`** (same as “tail with declaring
  class”), so `a.b.c` or `a:b:c` are easy to write.

#### Logical expression (right-hand side)

Parentheses; `!` `&&` `||`; comparisons and arithmetic; `^` as `Math.pow`; `:>` / `<:` (class hierarchy / `instanceof`
-style checks); `contains`; and a fixed set of `Math.*` helpers (`sqrt`, `abs`, `sin`, …). Names refer to variables
declared on the left.

#### Built-in variables

`namespace`, `path`, `resource` / `location` / `resourceLocation`, `clazz`, `clazzString`, `itemClazz`,
`itemClazzString`, `name`, `displayName`, `customName`, `tick`, `num`, `dim` / `dimension`, `x` / `y` / `z`, `chunkX`,
`chunkZ`, `hasOwner`, `ownerName`. Any other bare identifier evaluates to `null`.

#### Performance and caching

- **Per-rule string cache**: The first time each config string appears it is compiled (variable descriptors + expression
  AST) and cached; identical strings are not parsed again.
- **Reuse one `HashMap` per evaluation pass** over the rule list (`clear` between rules) to cut allocations.
- **Path pre-parsing**: `<>` / `{}` paths are split at compile/cache time, not on every entity.
- **`DataParameter` cache key**: **`runtime entity class :: first-part key`**, so different entity types do not share
  the wrong static field key, and a failed resolve on one type does not cache `null` for another type that would
  succeed.
- **Rough cost order**: predefined / literals **<** single `DataParameter` read **<** NBT **≈** deep reflection (`<>`
  chain or `{}`). Put **lighter rules first** to save a little CPU (short-circuit: first match wins).

---

## Building

The docs branch provides one build entry for all maintained branches:

```bat
scripts\build-all.bat
```

By default, it dynamically builds all local `forge/*`, `fabric/*`, and `neoforge/*` branches. Other namespaces such
as `dev/*` and `maintenance/*` are excluded. Each branch is built in a detached temporary worktree without switching
the current checkout.

List selected branches and validate JDK discovery without running Gradle:

```bat
scripts\build-all.bat -ListOnly
```

Select branches with glob expressions:

```bat
scripts\build-all.bat -BranchExpression "forge/*"
scripts\build-all.bat -BranchExpression "*/21.1"
scripts\build-all.bat -BranchExpression "forge/*,!forge/16.5"
scripts\build-all.bat -BranchExpression "fabric/18.2"
```

Expressions beginning with `!` exclude matching branches. The previous parameter name `-Branches` remains available
as an alias.

---

## License

MIT License

---

If you have any questions or suggestions, please submit Issues or Pull requests.
