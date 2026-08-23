# Blockpal JVM tests

Minecraft cannot run in CI, and Gradle's distribution download is blocked in some
sandboxes — but the mod can still be **compiled and meaningfully tested** with plain
`javac`, because 26.x ships unobfuscated with official names and needs no mappings step.

```bash
./tests/setup-toolchain.sh    # once — MC 26.2 + libraries + Fabric + JDK 25 (~700 MB)
./tests/run.sh
```

## What these cover, and why these things

Each suite exists because a mistake there is **invisible at compile time** and
expensive to find in-world.

| Suite | Guards against |
|-------|----------------|
| `NetTest` | Broken backprop in the hand-rolled PVT network. It proves the thing learns XOR (99.5%, against the 50% a linear model is capped at), that a saved policy predicts identically to the one that saved it, and that a file from another layout is refused rather than misread. |
| `PipelineTest` | The recording format. Disk round-trip, quantisation error, episode boundaries (a pair must never straddle two play sessions), a crash-truncated file still yielding its complete frames, pruning to the cap, lopsided-data detection, and the inverse dynamics model recovering actions from two views. |
| `ConfigCodecTest` | `ConfigData`'s hand-written `StreamCodec`. Write order and read order drifting apart compiles perfectly and shows one setting's value in another setting's box, so every one of its 63 components is round-tripped with a distinct value. |
| `ApiConsistency` | The script API's three parallel tables — a verb that is declared but not dispatched throws at runtime, and one missing from `reference()` is invisible to the AI that is supposed to use it. |
| `ConfigTest` | The config schema. Migration from v13, defaults landing as intended rather than as Java's `false`/`0`, PvP staying **off** across an upgrade, a deliberately-raised delay surviving, and garbage values being clamped instead of obeyed. |

## The FabricLoader stub

`ModConfig` reaches for the game's config directory at class-init, which no test has.
`tests/stub/` shadows `FabricLoader` with one pointed at a temp directory.

It must be an **`interface`**, not a class: the mod is compiled against the real
interface, so its call site is an `InterfaceMethodref` and a class stub fails at link
time with `IncompatibleClassChangeError`.

## What these do *not* cover

Anything that needs the game running: how a trained PVT policy actually looks after an
hour of somebody's play, whether the combat ranges feel right in a real fight, in-world
pathfinding, and rendering. Those need a real machine.
