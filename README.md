# Peripherals++

[![](https://img.shields.io/badge/Minecraft-1.7.10-62a34a)](https://minecraft.wiki/w/Java_Edition_1.7.10)
[![](https://img.shields.io/badge/Forge-10.13.4.1614-1e2b4f)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.7.10.html)
[![](https://img.shields.io/badge/ComputerCraft-3.3.1-2ea44f)](https://github.com/MrFuzzihead/Computercraft-Legacy)

A pseudo-port of MiscPeripherals for Minecraft 1.7.10: a set of computer peripherals, turtle upgrades and pocket
computer upgrades for [ComputerCraft](https://github.com/MrFuzzihead/Computercraft-Legacy).

Originally written by austinv11; this fork keeps it building and running against ComputerCraft 3.3.x (the
`Computercraft-Legacy` continuation) and CollectiveFramework 1.0.x.

## What it adds

| Kind | Peripherals |
|---|---|
| Blocks | chat box, AI chat box, player sensor, ore dictionary, iron note block, speaker, antenna, time sensor, environment scanner, interactive sorter, player interface, resupply station, turtle teleporter (mk I/II), peripheral container, ME bridge (AE2), bee/tree/butterfly analyzers (Forestry), RF turtle charger (TE/BC) |
| Turtle upgrades | chat box, player sensor, compass, XP, barrel, ore dictionary, shears, environment scanner, feeder, speaker, tank, note block, ridable, dispenser, resupply, chunk loader, sign reader, gardening, plus the ProjectRed and BluePower tool families |
| Pocket upgrades | motion detector, peripheral container |

Peripherals ship their example Lua programs under `/ppp` on the attached computer, mounted per peripheral type.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.7.10 |
| Minecraft Forge | 10.13.4.1614 |
| [Computercraft-Legacy](https://github.com/MrFuzzihead/Computercraft-Legacy) | 3.3.1 (`[3.3.0,)`) |
| [CollectiveFramework](https://github.com/MrFuzzihead/CollectiveFramework) | 1.0.1 (`[1.0.0,)`) |
| Applied Energistics 2, Forestry | optional |

ComputerCraft 3.3.x requires lwjgl3ify, which in turn requires a Java 17+ JVM at runtime.

## Building

Everything is handled by the `com.gtnewhorizons.gtnhconvention` plugin:

```sh
./gradlew build              # compile, unit tests, format check, coverage gate
./gradlew test               # unit tests only
./gradlew jacocoTestReport   # HTML coverage -> build/reports/jacoco/test/html
./gradlew runClient          # dev client (forwards to runClient25)
./gradlew runServer          # dev server (forwards to runServer25)
```

`runClient`/`runServer` forward to the modern (`*25`) variants, because ComputerCraft 3.3.x cannot load on the Java 8
toolchain the plain tasks use.

### Tests

| Command | What it runs |
|---|---|
| `./gradlew test` | 357 headless JUnit 4 tests (JUnit 4.13.2 + Mockito 4.11.0, Java 8 launcher) |
| `./gradlew check` | the above plus Spotless, Checkstyle, the coverage floor and the gameTest compile |
| `./gradlew gameTestServer` | boots a real dedicated server and runs 158 in-game assertions, then exits with the result |
| `./gradlew runServer25 -PsmokeTest` | boots a real server, prints `PERIPHERALSPLUSPLUS_SMOKE_TEST_OK` and exits |

The bundled Lua programs are compiled by `LuaScriptsTest` with ComputerCraft's own Lua 5.1 compiler, and every
peripheral method they call is cross-checked against the Java `getMethodNames()`.

The in-game mod lives in `src/gameTest/java` and is never shipped. It covers what cannot be tested headless: the real
block/item registries, the real peripheral provider, inventory moves against a real chest, the crafting recipe, the ore
dictionary, and — against a real running ComputerCraft computer — the mount contract the `/ppp` docs depend on.


`CODE_REVIEW.md` records the dependency audit, the bug/optimization/refactor backlog (every P0–P3 item is closed,
with the deliberately-deferred ones and their reasons listed), the phased refactor plan and the test-coverage
ratchet.

### Configuration

`config/PeripheralsPlusPlus.cfg`, generated on first launch. Every peripheral has an enable flag; the notable options
are `sayRate`/`sayRange`/`readRange` for the chat box, `sensorRange` for the player sensor, `teleporterPenalty` for the
turtle teleporter, and `maxNumberOfPeripherals` for the peripheral container.

## Contributing

Bug reports and pull requests are welcome. Please read `CODE_REVIEW.md` before structural changes: it records the
known limitations and the ordered refactor plan.

If a bug looks like it is inside ComputerCraft itself, please report it in the
[Computercraft-Legacy](https://github.com/MrFuzzihead/Computercraft-Legacy) repository.

## License

See [LICENSE](LICENSE). ComputerCraft itself is distributed under the ComputerCraft Public License; see
[Computercraft-Legacy](https://github.com/MrFuzzihead/Computercraft-Legacy) for its terms.

## Credit

Peripherals++ was created by austinv11; some ideas were taken from RichardG. ComputerCraft was created by
Daniel "dan200" Ratcliffe. This fork is maintained by MrFuzzihead for the GTNH pack.
