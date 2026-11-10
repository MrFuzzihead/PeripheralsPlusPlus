# Peripherals++ — Code Review, Dependency Audit, Refactor & Test Plan

Review date: 2026-02-09
Scope: `src/main/java` (201 files, 17,299 LOC), resources (`assets/peripheralsplusplus`, 34 Lua files),
Gradle build, CI workflows.
Baseline: ComputerCraft-Legacy **3.3.1**, CollectiveFramework **1.0.1** (updated in `dependencies.gradle`).

The code **compiles cleanly** against both new versions (`./gradlew compileJava` — verified), and `./gradlew check`
(compile + Spotless + Checkstyle + 51 unit tests + coverage gate + gameTest compilation) passes against them.

The problems below are behavioural, not compilation failures. Most of them were already present; two of them are
**regressions introduced by the CollectiveFramework 1.0.1 bump** and were release blockers (§3).

What landed in this pass (items marked ✅ below):

* the four P0 fixes — CF 1.0.1 thread regression, dead container recipe, colliding `/ppp` mount, duplicate AE grid
  node;
* three P1 fixes — `InteractiveSorter` push/pull arithmetic, `SynthPacket` codec (rate/volume), note-block pitch
  argument;
* the full test harness — JUnit 4.13.2 + Mockito 4.11.0 + JaCoCo 0.8.16, a non-shipped `gameTest` source set, a
  `-PsmokeTest` boot hook, and `.github/workflows/smoke-test.yml` (server boot + in-game assertions + Xvfb client);
* 51 unit tests in 6 classes, plus four new main-source types (`lua/LuaArgs`, `mount/PeripheralMounts`,
  `SmokeTestHook`, and a reworked `DynamicMount`).

Everything else is a documented backlog, ordered by phase in §9.

---

## 1. Executive summary

* **One critical regression from the CollectiveFramework 1.0.1 upgrade.** `SimpleRunnable.start()` changed
  from "run once, gated by `RESTRICT_THREAD_USAGE`" to "**repeat forever on a 1 ms timer**". `AIChatRequest`
  calls `start()` on a fire-and-forget Cleverbot request and never disables it, so every AI chat box now
  issues one Cleverbot HTTP request **per millisecond, forever**, and floods the computer with `ai_response`
  events. `PeripheralsPlusPlus.postInit`'s `SimpleRunnable.RESTRICT_THREAD_USAGE = false` — the line that
  used to prevent exactly this — is now a deprecated no-op. §3.1.
* **The Peripheral Container feature is dead code.** `ContainerRecipe.matches()` can never return `true`
  (inverted blacklist test plus a `block instanceof IPeripheralProvider` check that no `Block` can ever
  satisfy), so the "combine peripherals into a container" recipe never matches. §3.2.
* **The `/ppp` docs mount breaks with more than one PPP peripheral and can throw on detach.** All peripherals
  mount at the same path `/ppp`; ComputerCraft's `findFreeLocation` returns `null` for an occupied path, so
  only the *first* peripheral's scripts are visible — and `detach()` mounts again and then calls
  `unmount("/ppp")`, which throws `RuntimeException("You didn't mount this location")` when the mount never
  succeeded. §3.3.
* **Four user-visible miscalculations** in the sorter/tank/XP/note-block code paths that lose or duplicate
  items. §4.1–§4.4.
* **Every P1 is fixed** (batch 2, §14) and **39 of the 48 P2 items are fixed** (batch 3, §15): the thread-unsafe
  static collections, the unbounded `getClosestEntity`, the `Class.forName` on a network-supplied name, the shared
  smart-helmet message stack, the per-tick NBT rewrites and biome lookups, and the wind-down duplication in the
  garden/sign-reader/resupply/barrel peripherals.
* **5,000+ lines of duplicated logic** (two copies of `isInventoryOnSide`/`getInventoryForSide`, three copies
  of the slot-moving code, six copies of the "1-based Lua table" helper, five copies of `mount`/`detach`).
  §7 lists every duplicate with the target abstraction.
* **Testing went from nothing to a working harness.** No `src/test`, no JaCoCo and no CI server run
  (`client-only: true`) existed before; §10 now documents what was added (modelled on
  `MrFuzzihead/OpenPeripheral`) and what remains: 51 tests in 6 classes, line coverage **4.1 %**, enforced in
  `check` at the current baseline with an explicit ratchet to 65 % as the §9 phases land.
* **The mod is a "peripheral provider + Lua bridge" and is therefore unusually testable**: most classes only
  need `net.minecraft.World`/`TileEntity` mocks. §10.2 lists the 60-odd classes that can be tested with no
  game running at all.

---

## 2. Dependency audit

### 2.1 ComputerCraft-Legacy 3.3.0 → 3.3.1

Diffed the `3.3.0` and `3.3.1` source jars (`~/.gradle/caches/.../Computercraft-Legacy/`). The **entire Java
API is byte-identical**; the only changes are:

| File | Change |
|------|--------|
| `assets/computercraft/lua/bios.lua` | ROM fixes |
| `assets/.../rom/modules/main/cc/internal/menu.lua` | new file |
| `assets/.../rom/programs/{edit,id,lua,rename,shell}` | ROM fixes |
| `assets/.../rom/programs/fun/advanced/paint` | ROM fixes |
| `NOTICE` | licence text |

**Verdict: no code change required.** The `@Mod(dependencies = "...ComputerCraft@[3.3.0,)")` range stays
correct. Note the range is still `3.3.0` even though the build is now pinned to `3.3.1` — that is fine
(3.3.0 is genuinely still supported) but the mod metadata in `mcmod.info` declares no version range at all
(`requiredMods: ["ComputerCraft"]`), which is good enough for FML since `@Mod` wins.

### 2.2 CollectiveFramework 1.0.0 → 1.0.1

Diffed the source jars. This release is a large internal cleanup and it **does** change observable behaviour
that Peripherals++ depends on:

| CF class | 1.0.1 change | Impact on PPP |
|---|---|---|
| `multithreading.SimpleRunnable` | **Rewritten.** `start()` now schedules a repeating task (`scheduleWithFixedDelay(..., 0, 1 ms)`); `startOnce()` is the new single-shot API. `RESTRICT_THREAD_USAGE` is `@Deprecated` and ignored. | **BREAKS `AIChatRequest`** (infinite loop) and makes `PeripheralsPlusPlus.postInit`'s flag assignment dead. `AudioPacket` is safe only because it self-disables. §3.1 |
| `multithreading.SimpleThread` | Removed from the public model (still present, no longer used by `SimpleRunnable`). | none |
| `minecraft/threading/MainThread` (new) | New helper for main-thread hops. | **Opportunity**: use it instead of `synchronized` in packets. §8.6 |
| `minecraft/tiles/TileEntityInventory` | `getStackInSlotOnClosing` is now implemented; `items` is lazily sized via `ensureCapacity()`; bounds checks fixed. | PPP's `getStackInSlotOnClosing` overrides in `MountedTileEntityInventory` and `TileEntityResupplyStation` are now **redundant** (dead code). §7.3 |
| `minecraft/tiles/INBTSyncable` (new) | Sync packets now require the interface and strip `x/y/z/id` from the payload. | none (`TileEntityRFCharger`/`TileEntityPeripheralContainer` extend `NetworkedTileEntity`, which now implements it). **Note**: `TileEntityPeripheralContainer.writeToNBT` still writes positions into the drop NBT — see §4.9 |
| `minecraft/config/ConfigRegistry` | Skips synthetic/`static final` fields, walks superclasses, `null` check on `registerConfig`. | PPP's `Config` declares `exclude = { "enableAPIs", "ENABLE_CONFIG_MESSAGE" }`; both entries are now unnecessary but harmless. `enableAPIs` is a **dead config option** anyway. §6.14 |
| `minecraft/logging/Logger` | `all()` / `off()` removed. | not used. |
| `minecraft/utils/Location` | `hashCode()` fixed (was constant `0`); `toChunkCoordinates()` deprecated in favour of `chunkCoordsFromLocation()`. | `TileEntityNoteBlock` uses the deprecated method. §6.15 |
| `minecraft/utils/WorldUtils#getNearestEntityToLocation` | Seed distance changed to `+Infinity`. | **No functional change**, but it highlights that PPP calls the **1-arg overload** whose `maxRange` default is `Double.MAX_VALUE` — `setTarget(x,y,z)` can grab an entity anywhere in the world. §4.7 |
| `utils/ArrayUtils#indexOf(Object[],Object)` | Null-safe (`Objects.equals`). | none, but see §3.2. |
| `CollectiveFramework.SmokeTestHook` (new) | `-PsmokeTest` support. | **Opportunity**: PPP can reuse it (or mirror it) instead of writing its own. §10.5 |
| `assets/theframework` → `assets/collectiveframework` | asset rename. | none (PPP ships its own assets). |

Everything else in CF (`NBTHelper`, `ModIds`, `Colors`, `BlockBase`, `ItemBase`, `IconManager`,
`ExclusiveSlot`, `ConfigException`, `Config`, `Description`, `TimeProfiler`, `MathUtils`,
`minecraft/network/*`) is source-compatible.

### 2.3 Version table

| Dependency | In build | Newest 1.7.10-compatible | Note |
|---|---:|---:|---|
| Minecraft Forge | 10.13.4.1614 | 10.13.4.1614 | final 1.7.10 build |
| MCP mappings | stable / 12 | stable / 12 | final 1.7.10 channel |
| ComputerCraft-Legacy | **3.3.1** | 3.3.1 | no Java API delta from 3.3.0 |
| CollectiveFramework | **1.0.1** | 1.0.1 | behaviour changes, see §2.2 |
| Applied Energistics 2 (Unofficial) | rv3-beta-1099-GTNH | rv3-beta-1099-GTNH | `compileOnly` |
| ForestryMC | 4.12.0 | 4.12.0 | `compileOnly` |
| GTNH settings convention | 2.0.34 | 2.0.34 | — |
| JUnit | *(none)* | 4.13.2 | to add |
| Mockito | *(none)* | 4.11.0 | to add (5.x needs Java 11) |
| JaCoCo | *(none)* | 0.8.16 | to add |

---

## 3. P0 — release blockers

### 3.1 ✅ `AIChatRequest` spins forever under CollectiveFramework 1.0.1 *(regression)*

`cleverbot/AIChatRequest.java:43` — `new SimpleRunnable() { … }.start()`.

CF 1.0.0's `SimpleThread.run()` was `do { … } while (RESTRICT_THREAD_USAGE)`, and
`PeripheralsPlusPlus.postInit` set that flag to `false`, so the body ran exactly once. CF 1.0.1 replaced the
implementation with `EXECUTOR.scheduleWithFixedDelay(..., 0, 1, MILLISECONDS)` and made the flag a no-op.

Consequences of the current code on CF 1.0.1:

* one blocking `ChatterBotSession.think()` HTTP round-trip per millisecond, per session, forever;
* one `tileEntity.sendEvent(...)` per millisecond, forever → `queueEvent("ai_response", …)` floods the
  computer's event queue and every `BotSessionLuaObject:think()` `pullEvent` lambda is re-evaluated;
* the runnable is never `disable()`d, so the worker is pinned permanently.

**Fix (applied):** use `startOnce()`. Also `AudioPacket.AudioThread` should use `startOnce()` instead of
relying on its `finally { disable(true) }` self-cancel, and `PeripheralsPlusPlus.postInit` drops the
`RESTRICT_THREAD_USAGE` assignment.

### 3.2 ✅ `ContainerRecipe` can never match — the Peripheral Container feature is unusable

`recipe/ContainerRecipe.java:50` and `:52`:

```java
} else if (ArrayUtils.indexOf(blacklist, block) == -1) {   // ← never reached for a storable block
    return false;
} else if (block instanceof IPeripheralProvider) {         // ← no Block ever implements this
```

Three independent defects in one `if/else` chain:

1. The blacklist test is inverted. `ArrayUtils.indexOf` returns `-1` when the block is **not** in the
   blacklist; the code bails out in that case, i.e. it accepts only blacklisted blocks.
2. `dan200.computercraft.api.peripheral.IPeripheralProvider` is implemented by *providers*
   (`IPlusPlusPeripheral.Provider`), never by a `Block`. `hasPeripheral` can therefore never become `true`,
   so `matches()` always returns `false`.
3. `getCraftingResult` (`:70`) repeats the same `block instanceof IPeripheralProvider` test, so even if
   `matches` were fixed the result would be an empty container.

The correct test already exists in `getCraftingResult`: build the tile entity and check
`instanceof IPeripheral`. **Fix (applied):** whitelist semantics (`blacklist` → `EXCLUDED`), and probe
`block.createTileEntity(null, 0) instanceof IPeripheral`.

### 3.3 ✅ The `/ppp` mount collides between peripherals and `detach()` can throw

`tiles/MountedTileEntity.java:18/23`, `MountedNetworkedTileEntity.java:17/22`,
`MountedTileEntityInventory.java:31/36`, `turtles/peripherals/MountedPeripheral.java:16/21` — five copies of:

```java
public void detach(IComputerAccess computer) {
    computer.mount(DynamicMount.DIRECTORY, new DynamicMount(this));  // pointless, and fails
    computer.unmount(DynamicMount.DIRECTORY);                        // throws if the mount above failed
}
```

From the ComputerCraft 3.3.1 sources (`core/lua/lib/LuaEnvironment.java`, `core/apis/PeripheralAPI.java`):

* `mount(desiredLoc, mount)` → `findFreeLocation(desiredLoc)`, which returns **`null` when the path already
  exists** — it does not replace, and it does not throw. `DynamicMount.DIRECTORY` is the constant `/ppp`
  for every peripheral, so once one PPP peripheral is attached on a computer, **every later one silently
  gets no docs**.
* `unmount(location)` throws `RuntimeException("You didn't mount this location")` when the location is not
  in **that `IComputerAccess`'s own** mount list. Because the re-mount in `detach()` fails for any
  peripheral that did not win the race, `detach()` throws for it.

So a computer with two or more PPP peripherals gets (a) one peripheral's script set, and (b) a
`RuntimeException` on every detach of the losers.

**Fix (applied):** the mount is now per-peripheral (`/ppp/<peripheral type>`), `detach()` only unmounts, and
`DynamicMount` exposes the path it actually used. The docs section of the Lua ROM keeps working because
`/ppp` still shows one directory per attached peripheral.

### 3.4 ✅ `TileEntityMEBridge.getNode()` creates a duplicate AE grid node

`tiles/TileEntityMEBridge.java:367-371`:

```java
private IGridNode getNode() {
    if (worldObj == null || worldObj.isRemote) return null;
    return node = AEApi.instance().createGridNode(this);   // ← every call
}
```

`readFromNBT` already creates and loads a node (`createGridNode` + `loadFromNBT`). `updateEntity()` then
calls `getNode()` **twice** (`setPlayerID` and `updateState`), each of which allocates a *new* grid node,
discards the one loaded from NBT without destroying it (AE channel leak) and returns `null` on the client so
`updateEntity` NPEs there. Also `callMethod` dereferences `node` unconditionally, so a bridge that has not
ticked yet (or whose chunk was just loaded) NPEs on any method call.

**Fix (applied):** lazily create *once*, keep the NBT-loaded node, null-guard `callMethod`.

### 3.5 ✅ `BlockDispenser` behaviour registration leaks between worlds / double-registers

`PeripheralsPlusPlus.postInit` does `BlockDispenser.dispenseBehaviorRegistry.putObject(...)` — a *global*
vanilla registry mutated during postInit. It is idempotent in practice, but combined with
`ItemNanoSwarm.BehaviorNanoSwarm` not implementing `equals`, a second registration (e.g. a hot-reload in the
dev environment) replaces the entry. Low urgency, flagged for consistency: the mod already gates every other
registration behind `Config.enable*`; this one is not gated at all.

---

## 4. P1 — high severity (user-visible)

### 4.1 `TileEntityInteractiveSorter.push` moves the wrong quantity and writes to slot 0

`tiles/TileEntityInteractiveSorter.java:118-119`:

```java
int transferred = MathHelper.clamp_int(stack0.stackSize + amount, stack0.stackSize, max);
getStackInSlot(0).stackSize -= transferred;        // subtracts the TOTAL, not the delta
inventory.getStackInSlot(0).stackSize += transferred;  // slot 0 of the TARGET, not slots[currentSlot]
```

* With 10 items in the sorter, `push(side, 5)`, target max 64 → `transferred = 15` → source becomes **-5**
  and the target receives 15. Negative stack sizes corrupt the inventory and crash `ItemStack#splitStack`.
* The `else` branch writes to `inventory.getStackInSlot(0)` regardless of which slot was selected; if the
  target inventory is empty (`getStackInSlot(0) == null`) this is an immediate NPE.
* Additionally, the empty-slot branch (`inventory.setInventorySlotContents(slots[currentSlot],
  getStackInSlot(0)); setInventorySlotContents(0, null);`) moves the **whole stack** and ignores `amount`.

`pull` (`:176`) has the same `clamp(total, …)` mistake.

**Fix (applied):** `push`/`pull` now take an owned copy with `decrStackSize`, move
`Math.min(requested, room)` items, write to the slot they actually probed (`slots[currentSlot]`, never slot 0 of the
target), and refuse to merge stacks with different NBT (`ItemStack#areItemStackTagsEqual`).

### 4.2 ✅ `PeripheralTank.drain` can delete fluid without returning it

`turtles/peripherals/PeripheralTank.java:236-266` — the `IFluidContainerItem` path calls
`containerItem.drain(drainStack, 1000, true)` and unconditionally returns `1000`, but it never checks the
return value of `drain`. A container that refuses (wrong fluid, already empty, unstackable) silently loses
1000 mB from the tank. The `ItemBucket` path has the mirror problem: `FluidContainerRegistry.getFluidForFilledItem`
is called three times and can return `null` between checks.

Also `fill` (`:91`) only handles water/lava buckets. Any other `ItemBucket` subclass returns `1000` after
`fluidTank.drain(1000, true)` **without filling the bucket** (`if/else if` with no `else`) — fluid destroyed.

### 4.3 ✅ `PeripheralXP.enchant` NPEs on an empty slot and destroys stacks/NBT

`turtles/peripherals/PeripheralXP.java:180-190`:

* `slot = turtle.getInventory().getStackInSlot(turtle.getSelectedSlot());` then
  `slot.isItemEnchantable()` — **NPE when the slot is empty**.
* `enchanted = new ItemStack(Items.enchanted_book)` discards the original book's NBT and copies only
  `enchants.get(0)`, so a level-30 enchant that rolls 3 enchantments produces a book with 1.
* `setInventorySlotContents(selected, enchanted)` replaces the stack, so a stack of *N* books becomes **one**
  enchanted book (the other *N-1* are destroyed).
* `random.setSeed(tag.getLong("rndSeed"))` with a missing tag seeds with `0`, making enchanting **predictable**
  (first `buildEnchantmentList` after every chunk load is identical).

### 4.4 ✅ `TileEntityNoteBlock.playSound` uses the wrong argument

`tiles/TileEntityNoteBlock.java:90` — `double pitch = (arguments.length > 2) ? (Double) arguments[1] : 1.0f;`
reads `arguments[1]` (volume) instead of `arguments[2]`. `playSound("x", 0.5, 2.0)` plays at volume 0.5,
pitch 0.5.

**Fix (applied):** both methods were converted to the new `lua/LuaArgs` reader, which makes the index explicit and
fixed the bug in the process.

### 4.5 ✅ `SynthPacket` reads the wrong NBT keys

`network/SynthPacket.java:60-61` — `rate` is parsed from `tag.getString("pitch")`:

```java
rate = tag.getString("pitch").equals("null") ? null : Float.parseFloat(tag.getString("pitch"));
```

`toBytes` correctly writes `"rate"`, so the pitch is silently used as the rate; a `null` rate is impossible
(it reports whatever the pitch was). And `:116` — `voice.setPitch(message.volume)` should be
`voice.setVolume(...)`, so the volume parameter changes the pitch.

**Fix (applied):** `rate` is read from `"rate"`, `setVolume` is used, and `network/PacketCodecTest` round-trips every
voice parameter so this class of bug cannot come back.

### 4.6 ✅ `LuaObjectHUD.drawTexture` only reads `v` after 36 arguments

`lua/LuaObjectHUD.java:116` — `if (arguments.length > 36) v = (int) (double) (Double) arguments[6];`
(should be `> 6`). Callers that pass `texture, x, y, w, h, u, v` get `v = -1`, which is the "full texture"
sentinel, so every tiled texture renders the whole sheet.

Same file `:253` — `if (arguments.length > 6 && !(arguments[5] instanceof String))` in `addTextField`
(should be `> 5`), so a non-string 6th argument is accepted and cast at `:262` → `ClassCastException`.

### 4.7 ✅ Nano-bot `setTarget` / `setAttackTarget` accept any entity in the world

`lua/LuaObjectEntityControl.java:209` and `:229` call
`WorldUtils.getNearestEntityToLocation(new Location(x, y, z, world))` — the **1-argument** overload, whose
`maxRange` defaults to `Double.MAX_VALUE`. A Lua program can therefore make any mob target a player anywhere
in the loaded world, with no distance limit.

Additionally `:234` casts the result to `EntityLivingBase` without an `instanceof` check:
`entity.setAttackTarget((EntityLivingBase) attackTarget)` → `ClassCastException` becomes a raw Lua error for
boats/minecarts/items.

### 4.8 ✅ `LuaObjectEntityControl.heal` heals the *player* for non-player entities

`lua/LuaObjectEntityControl.java:86` — in the non-player branch:

```java
if (!ItemNanoSwarm.doInstruction(id, entity)) return new Object[] { false };
player.heal(1.0F);      // `player` is null here
```

NPE for every `heal()` call on a mob. The `hurt()` branch above it correctly uses `entity`.

### 4.9 ✅ `TileEntityPeripheralContainer` never clears `needsUpdate` and can store `null`

`tiles/TileEntityPeripheralContainer.java`:

* `:123` sets `needsUpdate = true` when `worldObj == null`; `:102` uses it but **never resets it**, so once
  a container is NBT-loaded outside a world, `markBlockForUpdate` is issued **every tick, forever**.
* `readFromNBT` (`:39`) calls `addPeripheral(Block.getBlockById(id))` during NBT load, where `worldObj` is
  `null`; `Block#createTileEntity(null, 0)` returns `null` for most blocks, and
  `peripheralsContained.add((IPeripheral) null)` follows, so the first `updateEntity` NPEs.
* `readFromNBT` also never clears `blocksContained`/`peripheralsContained` → duplicates on every load.
* `invalidate()` (`:150`) writes `writeToNBT(tag)` (which includes `x/y/z/id` **and** the contained NBT) into
  the dropped item while `BlockContainerPPP`/`BlockPeripheralContainer` may also drop the tile's inventory.

### 4.10 ✅ `PeripheralDispenser` slot arithmetic is off by one

`turtles/peripherals/PeripheralDispenser.java:43-50`:

```java
int slot = 1;
if (arguments.length > 0) slot += (int) (double) (Double) arguments[0];   // ← adds
else slot = turtle.getSelectedSlot();
```

`dispense(0)` dispenses from slot 1, `dispense(1)` from slot 2. The `else` branch is 0-based, the `if` branch
is `1 + n`. Also `:54` `newStack.stackSize == 0 ? null : newStack` NPEs when `behavior.dispense` returns
`null`, and `:57` is a bare no-op statement (`turtle.getDirection();`).

### 4.11 ✅ `TileEntityRFCharger` divides by the turtle count

`tiles/TileEntityRFCharger.java:64` — `int rate = ((int) Math.floor((float) 6 / (float) turtles.size()));`
With no adjacent turtle this is `(int) Infinity == Integer.MAX_VALUE` (no exception, because the division is
on `float`). It happens to be harmless today only because the following loop body is empty. It becomes a
silent no-charge bug the moment the loop condition changes, and reads as a bug.

The charger also ignores `EnergyStorage#extractEnergy`'s return value and calls `setFuelLevel(level + rate)`
(bypassing `addFuel`) which can exceed `getFuelLimit()` — see the `//Bad fix for a crash` comment.

### 4.12 ✅ `TileEntityTeleporter` corrupts link state

* `readFromNBT` (`:58`) adds to `links` without clearing it → every chunk reload duplicates the links, and
  `addLink`'s `while (links.size() > getMaxLinks()) links.pop();` then throws the new link away.
* `:150` `destWorld.notifyBlockChange(linkToX, linkToY, linkToZ, te.blockType)` passes the **source** turtle
  block (and `te` may be `null`) — the destination gets an unrelated pending block update.
* `:145-152` `markBlockForUpdate`/`notifyBlockChange` run even when `turtle.teleportTo` returned `false`.
* `getLinks` (`:161`) is **0-based** while `teleport(index)` (`:100`) is **1-based**; the Lua API is
  internally inconsistent.
* `setName` (`:176`) assigns `this.name` (the internal `getType` backing field) instead of `tag`, so
  `getLinks().name` shows the peripheral's internal name and `setName` has no visible effect on anything else.
* `onTeleport(byte)` and `LinkData`-related `FIXME`s are dead: `onTeleport` is never called.

### 4.13 ✅ `TileEntityPlayerSensor`: the range argument is ignored and the distance formula is wrong

`tiles/TileEntityPlayerSensor.java:91-99`:

```java
double range = Config.sensorRange;
if (arguments.length > 0) range = (Double) arguments[0];      // never used again
…
Math.abs(location.getX() - player.posX + (location.getZ() - player.posZ))
```

`range` is computed and then unused — `getNearbyPlayers(4)` returns players at any distance. The distance is
`|dx + dz|` (sum inside one `abs`) and ignores Y entirely, so two players 10 blocks apart on opposite sides
report 0. `location` is only assigned in `validate()`; a turtle-mounted sensor whose `validate()` never ran
NPEs (swallowed by the `catch (Exception) { printStackTrace(); }` at `:106`, which also turns every error
into "return nil").

### 4.14 ✅ `TileEntityAntenna` blocks HUD methods when nano-bots are disabled

`tiles/TileEntityAntenna.java:88`:

```java
if (!Config.enableSmartHelmet && method < 6) throw …;
else if (!Config.enableNanoBots) throw …;
```

Because of the `else if`, `enableSmartHelmet = true, enableNanoBots = false` makes **all** methods 0–3
(`getPlayers`, `getHUD`, `setLabel`, `getLabel` — pure helmet features) throw "Nano bots have been disabled".
Also `case 2` (`setLabel`) has no `return` and falls through into `case 3`.

### 4.15 ✅ `PeripheralMotionDetector` leaks an event-bus listener per instance

`pocket/peripherals/PeripheralMotionDetector.java:33` registers `this` on `MinecraftForge.EVENT_BUS` in the
constructor and never unregisters. Every pocket-computer (re)creation adds a permanent listener that is
invoked for every `PlayerInteractEvent` forever. `createPeripheral` is called again on every
`IPocketAccess#invalidatePeripheral()`.

### 4.16 ✅ `TurtleDropCollector` registers 30 identical listeners and leaks its map

`PeripheralsPlusPlus.registerUpgrade` (`:219`) registers `u.newInstanceOfListener()` for **every**
`TurtleDropCollector`. `TurtleProjRed` and `TurtleBluePower` both extend it, and 15+15 anonymous subclasses
are registered — so 30 identical `LivingDropsEvent` handlers run for every entity death.

`TurtleDropCollector.map` is a `static HashMap<Entity, ITurtleAccess>` populated on every attack and only
removed when the entity actually dies. Any entity that despawns, is `/kill`ed with `doMobLoot` off, or is
killed by a damage source that does not fire `LivingDropsEvent` leaks its entry **and** the whole
`ITurtleAccess`/`World` graph behind it, for the lifetime of the JVM.

### 4.17 ✅ `PeripheralChunkLoader` does not survive a restart and NPEs on ticket exhaustion

`turtles/peripherals/PeripheralChunkLoader.java`:

* `LoaderHandler.ticketsLoaded` (`:101`) is empty, so Forge hands back the persisted tickets and they are
  dropped → **the chunk loader stops loading chunks after every server restart**.
* `updateTicket` (`:60`) releases the old ticket and then calls `forceChunk(ticket, …)` without checking that
  `requestTicket` returned non-null (`null` when the per-mod ticket limit is reached) → NPE.
* `detach` releases the ticket for *all* attached computers.

### 4.18 ✅ `LuaObjectPlayerInv` permission checks NPE without a card

`lua/LuaObjectPlayerInv.java:276/281/286`:

```java
return !Config.enableInterfacePermissions || permCard.getTagCompound().getBoolean("deposit");
```

`permCard` is `null` when the player has no card in hand, and `getTagCompound()` is `null` for a freshly
crafted card (all three flags are written by `ItemPermissionsCard.onItemRightClick` only **after** the first
use). Either case is an NPE on every `push`/`retrieve`. Also `pushToSlot` requires 3 arguments but ignores
`arguments[0]`.

---

## 5. P2 — medium severity (correctness, robustness)

| # | Location | Finding |
|---|---|---|
| 5.1 ✅ | `utils/Util.java:32` | `iteratorToMap` starts at `i = 1` and stores `i + 1`, so the first Lua key is **2**. Every other helper is 1-based. Currently unused, so latent. |
| 5.2 ✅ | `utils/TurtleUtil.java:62-76` | `getClosestEntity`/`getClosestShearableEntity` never update `lastDistance`, so they return the **last** entity in range, not the closest (`getClosestShearableEntity` is used by `TurtleShear`, `getClosestEntity` by both RedPower-like tool upgrades). |
| 5.3 ✅ | `utils/Util.java:88-93` | `compare(ItemStack, ItemStack)` calls `getEntries()` (an `OreDictionary` lookup + `HashMap` allocation) *inside* the loop, `O(n·m)` allocations per comparison; used by every `oreDictionary.doItemsMatch`. |
| 5.4 ✅ | `utils/Util.java:123` | `keyFromVal` does `map.get(key).equals(val)` → NPE on any `null` value. Unused (dead). |
| 5.5 ✅ | `utils/Util.java:126` | `getPlayer(ign)` dereferences `MinecraftServer.getServer()` (null on the client / during shutdown) and matches on `getDisplayName()` (includes scoreboard prefixes). |
| 5.6 ✅ | `utils/Util.java:139` | `stringToArray` naively `replace("]"…).split(",")`; a message containing `,` or `[]` round-trips wrong (`MessageCommand`). |
| 5.7 | `utils/ChatUtil.java:60` | `getPlayer` also matches on `getDisplayName()`; a team prefix or a nickname mod makes `tell` target the wrong player. |
| 5.8 | `tiles/TileEntityChatBox.java:187` | `tell` assigns `range = (Double) arguments[2]` with **no clamp** to `Config.sayRange`, unlike `say` (`:146`). A Lua program can broadcast to every player in the dimension. |
| 5.9 ✅ | `tiles/TileEntityChatBox.java:245` | `event.message.replace(commandPrefix, "")` removes **every** occurrence, not just the leading prefix (`\say \done` → `say done`). |
| 5.10 ✅ | `tiles/TileEntityChatBox.java:125/175` | The rate limiter is `ticker == Config.sayRate`; `Config.sayRate < 1` blocks permanently, and `ticker` is mutated under `synchronized (this)` in `callMethod` but not in `updateEntity`. |
| 5.11 ✅ | `tiles/TileEntityAntenna.java:52-58` | `antenna_registry` is a plain `static HashMap<UUID, TileEntityAntenna>` written from `validate`/`invalidate`/`updateEntity` on the server thread and read from CC computer threads. Same pattern in `TurtleDropCollector.map`, `MessageCommand.messageStacks`, `CreativeTabPPP.upgrades`, `ComputerCraftRegistry.pocketUpgrades`/`craftingRecipes`, `GuiHelmet.renderStack`, `GuiSmartHelmetOverlay.renderStack`, `RobotHandler.operationList` (the only concurrent one). |
| 5.12 ✅ | `tiles/TileEntityAntenna.java:143` | `huds` (`HashMap<Integer, LuaObjectHUD>`) is keyed by computer id and never cleaned on `detach`; each `getHUD` also sends a `ScaleRequestPacket` that a dead client never answers, so the `pullEvent` coroutine waits forever. |
| 5.13 ✅ | `lua/LuaObjectHUD.java:52` | `Util.getPlayer(player)` can return `null`; every subsequent `player.getDisplayName()` NPEs. `MessageCommand.messageStacks` is keyed by *display name*. |
| 5.14 ✅ | `lua/LuaObjectHUD.java:373` | `stackToArray()` drains the deque with `poll()` instead of iterating, so the HUD state is destroyed even if the packet fails to send. |
| 5.15 | `lua/LuaObjectPlayerInv.java:186` | `getOutputInventory`/`getInputInventory` cast `world.getTileEntity(...)` to `IInventory` for any `BlockContainer` → CCE for a non-inventory container (e.g. a CC computer, a chest next to it is fine but an AE interface is not). They are also called twice per operation (`:145`/`:147`), re-resolving the tile each time. |
| 5.16 | `lua/LuaObjectPlayerInv.java:247` | `addStackToInv` mutates the inventory *and* returns `false` when a partial insert happens with `slot != -1`; the caller then overwrites the source slot with `origStack` → **item duplication**. |
| 5.17 ✅ | `lua/LuaObjectPeripheralWrap.java:31` | `wrapPeripheral(name)` passes `getPeripheralByName(name)`, which returns `null` for an unknown name → NPE inside `getMethodNames()` instead of a `LuaException`. Same in `PeripheralPeripheralContainer:79`. |
| 5.18 ✅ | `tiles/TileEntityAIChatBox.java:130` | `sessions` is never pruned on `invalidate()`; each `BotSessionLuaObject` holds a `ChatterBot`/`ChatterBotSession` for the JVM lifetime. `computers` is an `ArrayList` mutated from `attach`/`detach` and iterated from the `AIChatRequest` worker thread. |
| 5.19 ✅ | `tiles/TileEntityAIChatBox.java:262` | `ArgumentHelper.stringToUUID` calls `UUID.fromString` unguarded → `IllegalArgumentException`, not `LuaException`. `ArgumentHelper.getBotType`/`stringToType` are unused. |
| 5.20 ✅ | `tiles/TileEntitySpeaker.java:105` | `packetInfo` is a single shared 4-element buffer; two `speak()` calls in the same tick drop the first. `synthesize`'s `range` is unclamped (same class of bug as 5.8). |
| 5.21 ✅ | `tiles/TileEntitySpeaker.java:200` | `onSpeechCompletion` broadcasts to *every* computer with `eventTicker == 0 \|\| !lastMessage.equals(text)`, so a second speaker's completion event can unlock the wrong coroutine. |
| 5.22 ✅ | `tiles/TileEntityTimeSensor.java:52` | `new SimpleDateFormat(...)` per call with the default locale/timezone; `getDate` is TZ-dependent while `getTime` is epoch ms. `profiler` is not cleared on `startTimer`, and `stopTimer` without `startTimer` returns `0` silently. |
| 5.23 ✅ | `tiles/TileEntityEnvironmentScanner.java` | `updateEntity` calls `getBiomeGenForCoords` three times per tick per scanner; `getMethodNames` exposes both `getTemperature` and `getTemp` returning the same value; the turtle constructor never updates `yCoord`. |
| 5.24 ✅ | `tiles/TileEntityOreDictionary.java:163` | The whole `callMethod` is wrapped in `catch (Exception e) { printStackTrace(); }` → every `LuaException` is converted into a silent `nil` return. `transmute` has a dead `i` counter. `getMethodNames()` returns an empty array for the (placeable) block variant. |
| 5.25 | `tiles/MountedTileEntity*.java` | `getStackInSlotOnClosing` overrides are now redundant with CF 1.0.1 (§2.2). |
| 5.26 ✅ | `event/handler/PeripheralContainerHandler.java:27` | `Block.getBlockById(id)` can be `null` (removed mod) → `new ItemStack((Block) null)` and a NPE at `peripheral.createTileEntity`. Runs on both sides (no `isRemote` guard) and rebuilds the tooltip on every right-click. |
| 5.27 ✅ | `event/handler/PocketComputerCraftingHandler.java:77` | `getCraftingResult` calls `findUpgradeItem` into the `upgrade` local on every inventory slot; when a non-upgrade slot is found the method returns `null` **after** having already written `upgrade` from a previous slot. It also removes `instanceID`/`sessionID`, which is correct for a fresh computer, but the `matches()` capacity check is `> max` while `getCraftingResult` also uses `>`, so `max + 1` peripherals fit. |
| 5.28 ✅ | `event/handler/RobotHandler.java:80` | `KeyBoardOperation.getKeyField()` returns `null` on `NoSuchFieldException` and the caller catches only `IllegalAccessException` → NPE for an unknown key name. `RobotOperation` with `Config.secondsBeforeReversal <= 0` never calls `operate()`. |
| 5.29 ✅ | `event/handler/SmartHelmetHandler.java:35` | `UUID.fromString(…getString("identifier"))` throws `IllegalArgumentException` when the identifier tag exists but is empty (only `hasTag` is checked). |
| 5.30 ✅ | `smarthelmet/HelmetCommand.java:31` | `getCommandFromName` does `Class.forName("…smarthelmet." + name).newInstance()` on a string that arrives **from the network** (`CommandPacket.fromBytes`). A server can instantiate any class in that package; and it is a reflective allocation per HUD command per packet. Replace with a registry/enum. |
| 5.31 ✅ | `smarthelmet/MessageCommand.java:13` | `messageStack` is `static`, so every smart-helmet wearer on a client shares one message list; `readFromNBT` clears the global stack. `writeToNBT` NPEs when `messageStacks.get(player)` is `null`. |
| 5.32 ✅ | `entities/EntityRidableTurtle.java:36` | A common (both-sides) class imports `net.minecraft.client.Minecraft` and `org.lwjgl.input.Keyboard`. All uses are guarded by `worldObj.isRemote`, which works only because Java resolves those classes lazily; a single refactor that drops a guard is a `NoClassDefFoundError` on the dedicated server. Move to a `@SideOnly(Side.CLIENT)` helper. |
| 5.33 ✅ | `entities/EntityRidableTurtle.java:79` | `item.isItemEqual(new ItemStack(Items.carrot_on_a_stick))` allocates an `ItemStack` **every tick** per ridden turtle. `moveTurtle` ignores `consumeFuel`'s return value and only checks `isAirBlock` (no collision). |
| 5.34 ✅ | `entities/NanoProperties.java:29` | `saveNBTData` uses a `try/catch (NullPointerException)` to test for a null `antennaID`, and `loadNBTData` registers into `TileEntityAntenna.antenna_registry` (a static map) from the entity NBT load path. |
| 5.35 ✅ | `blocks/BlockContainerPPP.java:64` | `dropItems` unconditionally casts the tile entity to `IInventory` → CCE for any `BlockContainerPPP` whose tile is not an inventory. Allocates `new Random()` per dropped item. Sets `itemStack.stackSize = 0` instead of clearing the slot. |
| 5.36 ✅ | `blocks/BlockMEBridge.java:26` | `onBlockPlacedBy` casts `world.getTileEntity(x,y,z)` to `TileEntityMEBridge` without a null/type check, and `ModBlocks.meBridge` is created **eagerly** in the `ModBlocks` static initialiser even when AE2 is absent (every other optional block is created lazily). |
| 5.37 ✅ | `init/ModBlocks.java:73` vs `proxy/CommonProxy.java:60` | The RF charger block is registered only for `ThermalExpansion`, the tile entity for `ThermalExpansion` **or** `BuildCraft|Core`. In a BC-only pack the recipe (`init/Recipes.java:62`) builds `new ItemStack((Block) null)`. |
| 5.38 ✅ | `init/Recipes.java:281` | `new ItemStack(ModItems.permCard), new ItemStack(ModItems.permCard)` — a shapeless 1:1 recipe that resets a permission card by destroying its NBT; it also matches itself as an ingredient, which some recipe viewers render as a cycle. |
| 5.39 ✅ | `init/Recipes.java:58-74` | Dangling `else` (`if (Config.enableRFCharger) if (Loader…ThermalExpansion) … else if (Loader…BuildCraft)`). Correct today, unreadable, and the `new ItemStack(ModBlocks.rfCharger)` can be a null-item stack (5.37). |
| 5.40 ✅ | `network/CommandPacket.java:55` | `HelmetCommand.getCommandFromName` can return `null` (unknown type) and `command.readFromNBT` then NPEs inside the `try` — the NPE is logged and `commands[i]` stays `null`, so a corrupt packet becomes a silent no-op instead of a disconnect. |
| 5.41 | `network/AudioResponsePacket.java:48` | `MinecraftServer.getServer()` is dereferenced in `fromBytes`; safe today only because the packet is `Side.SERVER`-bound. `AudioPacket`/`SynthPacket` also carry an `int world` constructor parameter that is silently ignored (the dimension is re-derived from the position on the other side). |
| 5.42 | `turtles/TurtleProjRed.java:56` | `getProjRedTool` builds `new ItemStack(GameRegistry.findItem(...))`; a missing ProjectRed item yields a null-item stack, and `getIcon`/`useTool` then NPE. Same shape in `TurtleBluePower`. |
| 5.43 | `turtles/TurtleRidable.java:100` | `update()` runs every turtle tick and calls `getEntity()`, which does a `getEntitiesWithinAABB` scan per tick **and** uses `throw new Exception` for the normal "not spawned yet" case. |
| 5.44 ✅ | `turtles/peripherals/PeripheralBarrel.java:144` | `getOreDictEntries` builds `new ItemStack(ITEM_TYPE_STORED)` without a null check → NPE for an empty barrel. `case 0` can compute a negative `amount`. `case 1` compares with `new ItemStack(ITEM_TYPE_STORED)` (meta 0), so damaged items always mismatch. `getUnwrappedUnlocalizedName` is dead. `update()` rewrites the upgrade NBT every tick regardless of `changed`. |
| 5.45 ✅ | `turtles/peripherals/PeripheralGarden.java` | Six near-identical blocks; `new ItemStack(Items.dye, 1, 15)` per call; `blockY = posY + +1` (double unary plus); `ItemDye.applyBonemeal(..., null)` passes a null player into third-party `IGrowable`s. |
| 5.46 ✅ | `turtles/peripherals/PeripheralSignReader.java` | Three copies of the same 8 lines; the `TileEntitySign` cast is unchecked. |
| 5.47 ✅ | `turtles/peripherals/PeripheralResupply.java:56` | `if (arguments.length < 1 \|\| (arguments.length > 1 && arguments.length < 3))` rejects exactly 2 arguments with "Too few arguments"; the link is world-scoped only, so a turtle that changes dimension keeps a stale link. |
| 5.48 ✅ | `pocket/peripherals/PeripheralPeripheralContainer.java:88` | `getPeripherals()` allocates a new `ArrayList` on every call, and `callMethod` calls it up to three times per invocation. |

---

## 6. P3 — low severity, consistency, style

| # | Location | Finding |
|---|---|---|
| 6.1 ✅ | 24 sites | `e.printStackTrace()` instead of the mod logger (`RobotHandler`, `AudioPacket`, `SynthPacket`, `AudioResponsePacket`, `SynthResponsePacket`, `CommandPacket`, `TileEntityAIChatBox`, `TileEntityOreDictionary`, `TileEntityPlayerSensor`, `PeripheralTank`, …). `PeripheralsPlusPlus.LOGGER` exists and is already used in `DynamicMount`. |
| 6.2 ✅ | `PeripheralsPlusPlus.java:214` | `SimpleRunnable.RESTRICT_THREAD_USAGE = false` — deprecated no-op in CF 1.0.1. Delete. |
| 6.3 | 12 sites | `== null` / `!= null` checks written as `!(x == null)`; `if (a) if (b) if (c)` chains up to 5 deep (`ComputerCraftHooks.onPlayerInteract`, `SmartHelmetHandler`, `CommonProxy`). |
| 6.4 | 11 classes | `public boolean equals(IPeripheral other) { return (this == other); }` and `return (other == this);` — identical, both used. Standardise. |
| 6.5 | every peripheral | `getMethodNames()` allocates a fresh `String[]` on every call. CC calls it on every method lookup; cache as a `private static final String[]`. |
| 6.6 | every peripheral | `public static String publicName` + `private String name` + `public String getName()`: the `name`/`getName()` pair is only used by `getLinks` and is otherwise dead. Remove or use `getType()`. |
| 6.7 | `tiles/MountedTileEntity.java` etc. | Five identical copies of `attach`/`detach` (and `IPlusPlusPeripheral` is an interface with a `default` method — the mount logic belongs there). |
| 6.8 | `tiles/TileEntityMEBridge.java:227` + `TileEntityInteractiveSorter.java:215` | Identical `getDefaultSlots`/`isInventoryOnSide`/`getInventoryForSide`. `block instanceof IInventory` is dead in both (a `Block` is never an `IInventory`). |
| 6.9 | `tiles/TileEntityTeleporter.java:206`, `TileEntityPlayerSensor.java:146` | `// FIXME idk what I'm doing` on `equals`. Now correct, comment should go. |
| 6.10 | `reference/Config.java:9` | `exclude = { "enableAPIs", "ENABLE_CONFIG_MESSAGE" }` — `enableAPIs` is read nowhere (dead option), `ENABLE_CONFIG_MESSAGE` is `private static final` and already skipped by CF 1.0.1. |
| 6.11 ✅ | `reference/Config.java:141` | `enableAPIs` is `public static boolean` with a `@Description` but is excluded from the file and never read. |
| 6.12 ✅ | `reference/Reference.java:36` | `TANK_UPGRADE = 126; // TODO Remember to update the id list on the wiki`. Also `GUI_FACTORY_CLASS` points at `client/gui/GUIFactory` which does not exist (it is commented out of `@Mod`). |
| 6.13 | `reference/Reference.java:22` | `FIRST_UNRESERVED_POCKET_UPGRADE_ID = IPocketUpgrade.FIRST_MOD_UPGRADE_ID` (7) is correct, but `registerPocketUpgrade` re-checks `<` instead of `<=` via a second constant; `MOTION_DETECTOR = 7` / `PERIPHERAL_CONTAINER = 8` are then fine only because 7 is legal. |
| 6.14 | `client/gui/GuiHandler.java:24` | `(IInventory) world.getTileEntity(x, y, z)` unchecked → CCE when the block was replaced. |
| 6.15 | `tiles/TileEntityNoteBlock.java:129` | Uses `Location#toChunkCoordinates()`, deprecated in CF 1.0.1. |
| 6.16 ✅ | `client/models/ItemRenderTurtle.java` | Dead class — byte-identical to `ItemRenderAntenna` (only the class name differs) and imported but not used by `ClientProxy`. |
| 6.17 | `client/models/ItemRenderTurtle`, `ModelTurtle`, `ModelNanoBotSwarm` | `ItemRendererHelper`/`GL11` usage never resets state (`glPushMatrix`/`glPopMatrix` missing around `glTranslatef`). |
| 6.18 ✅ | `tiles/containers/ContainerEmpty.java` | Dead class ("Empty container class for GuiContainer because Panda is lazy"). |
| 6.19 ✅ | `src/main/java/com/gtranslate/**` | 10 files of vendored third-party code (`gtranslate`) inside the mod package with no licence header and no attribution in `LICENSE`/`mcmod.info`. Either move to a `embedded(...)` dependency, or relocate under `com.austinv11.peripheralsplusplus.thirdparty` with the original licence. |
| 6.20 ✅ | `PeripheralsPlusPlus.java:107` | `ConfigRegistry.registerConfig` failure logs "Fatal problem…" then only calls `printStackTrace()` — the mod continues with an unloaded config (all defaults, which is fine, but the log says fatal). |
| 6.21 | `PeripheralsPlusPlus.java:187` | `try { … } catch (Exception e) { e.printStackTrace(); }` around both pocket-upgrade registrations: a failure leaves the mod half-registered with no user-visible error. |
| 6.22 | `PeripheralsPlusPlus.java:226-283` | 60 lines of commented-out `registerRedPowerLikeUpgrades` implementation. |
| 6.23 | `PeripheralsPlusPlus.java:64` | `dependencies = "required-after:ComputerCraft@[3.3.0,)"` — the mod is built against 3.3.1 and uses the CC3-only `IPeripheralTargeted`/`IPocketUpgrade`/`MethodResult` APIs, so `3.3.0` is only correct because 3.3.0 already had those. Consider `[3.3.0,4.0)` and pinning in `mcmod.info`. |
| 6.24 | `gradle.properties` | `usesMixinDebug = true` ships mixin export flags in the release task. |
| 6.25 | `.github/workflows/build-and-test.yml` | `client-only: true` — the reusable workflow's server step is skipped, so **no CI job ever boots a dedicated server**. §10.6. |
| 6.26 ✅ | `README.md` | Still carries the 2015 banner "Deprecated! See PeripheralsPlusOne" and a dead `drone.io` badge; contradicts the maintained fork. |
| 6.27 | class naming | `TileEntityrfCharger` (`name` field), `TurtleUtils` vs `TurtleUtil` (two utility classes, one of which has a `getTurtle` and the other a `getPeripheral`), `MountedTileEntity` vs `MountedPeripheral` (not siblings). |
| 6.28 | `lua/**` | `LuaObjectHUD` duplicates `DrawRectangleCommand` construction four times; `AddButtonCommand`/`AddTextFieldCommand` argument validation is copy-pasted 30+ times. A `LuaArguments` helper (`getDouble(i)`, `getString(i, default)`, …) would remove ~600 lines and fix §4.6/§5.31 in one place. |

---

## 7. Dead code and duplication inventory

### 7.1 Dead (remove)

| Item | Evidence |
|---|---|
| `tiles/containers/ContainerEmpty.java` | never referenced |
| `client/models/ItemRenderTurtle.java` | imported, never instantiated; identical to `ItemRenderAntenna` |
| `com/gtranslate/URLCONSTANTS.java` | never referenced |
| `utils/TurtleUtil.getPeripheral(ITurtleAccess, Class)` | never called |
| `utils/TurtleUtil.getPeripheralSide(ITurtleAccess, Class)` | never called |
| `utils/Util.collectionToMap(Collection)` | never called |
| `utils/Util.keyFromVal(HashMap, Object)` | never called |
| `utils/Util.iteratorToMap(Iterator)` | never called (and buggy, §5.1) |
| `utils/TranslateUtils.detectLangName(String)` | never called |
| `tiles/TileEntityTeleporter.onTeleport(byte)` | never called |
| `peripherals/TileEntity*Sensor#readFromNBT`/`writeToNBT` overrides | `super` only (`ChatBox`, `EnvironmentScanner`, `OreDictionary`, `Speaker`) |
| `PeripheralChunkLoader.LoaderHandler` | empty callback (but see §4.17 — it should do something) |
| `PeripheralBarrel.getUnwrappedUnlocalizedName` | never called |
| `TileEntityAIChatBox.ArgumentHelper.getBotType`/`stringToType` | never called |
| `MountedTileEntityInventory.getStackInSlotOnClosing`, `TileEntityResupplyStation.getStackInSlotOnClosing` | superseded by CF 1.0.1 |
| `Config.enableAPIs` | excluded from the config file, never read |
| `Reference.GUI_FACTORY_CLASS` | points at a non-existent class |
| `PeripheralsPlusPlus` 60-line commented block | `:226-283` |

### 7.2 Duplicated (extract)

| Duplicate | Copies | Proposed home |
|---|---|---|
| `attach`/`detach` mounting `/ppp` | 5 | `IPlusPlusPeripheral` default methods (it is already an interface with a default) |
| `isInventoryOnSide` + `getInventoryForSide` + `getDefaultSlots` | 2 (ME bridge, sorter) | `utils/InventoryUtils` |
| 1-based `Map<Integer,Object>` builders | 6 (`Util.arrayToMap` ×3, `EnumSetToMap`, `collectionToMap`, `iteratorToMap`, plus `TileEntityMEBridge.iteratorToMap`, `PeripheralBarrel` 0-based, `TileEntityTeleporter.getLinks` 0-based) | `LuaTable.of(Iterable)`, one implementation |
| Lua argument validation (`instanceof Double` / `LuaException("Bad argument #n")`) | ~150 sites | `lua/LuaArgs` helper |
| Slot-moving loop (find empty/mergeable slot, respect `getMaxStackSize`) | 3 (sorter push/pull, resupply, player-inv) | `utils/InventoryUtils.insert` |
| "Distance to tile entity" | 4 (`ChatUtil` ×2, `TileEntityChatBox.ChatListener` ×3, `TileEntityPlayerSensor`) | `utils/PositionUtils.withinRange` |
| `getMethodNames()` string arrays | 22 | static finals |
| Tool-material registration loops (`TurtleProjRed`/`TurtleBluePower`) | 2 | one factory |

---

## 8. Performance and allocation notes

1. **Per-tick allocations in `updateEntity`**: `TileEntityEnvironmentScanner` (3 biome lookups),
   `PeripheralChunkLoader` (`new ChunkCoordIntPair` twice per tick), `PeripheralBarrel.update()` (NBT write
   per tick), `TileEntityPeripheralContainer.updateEntity` (`markBlockForUpdate` per tick due to §4.9),
   `TurtleRidable.update` (AABB scan + `ItemStack` allocation), `EntityRidableTurtle` (`new ItemStack` per
   tick per input).
2. **30 duplicate `LivingDropsEvent` handlers** (§4.16) — each one iterates the static map for every death.
3. **`new HashMap` for every Lua table** in every `getMethodNames`/`callMethod` path; acceptable for CC's
   table conversion but the *map key* should be an `int`-indexed `Object[]` — CC converts arrays and maps
   identically, and arrays avoid boxing.
4. `TileEntityInteractiveSorter.getItemInfo` deep-copies every NBT tag (`nbtBase.copy()`) and then
   `removeTag`s from the copy (§ `getObjectForNBT` case 9 is a correctness bug as well — it removes while
   iterating, so lists are truncated).
5. `TileEntityChatBox.ChatListener` does a `Vec3.createVectorHelper(...).distanceTo(player.getPosition(1f))`
   per box per chat line — `getPosition` itself allocates a `Vec3`; ~2 allocations per box per message.
6. **Thread-safety**: eleven `static` mutable collections (§5.11) and six `HashMap`s inside tile entities
   (`computers`) are touched from CC's computer threads. Use `ConcurrentHashMap`/`CopyOnWriteArrayList`, or
   hop to the server thread (CF 1.0.1 now ships `minecraft/threading/MainThread` for exactly this).
7. `SimpleRunnable`'s new shared pool is 4+ threads; PPP should not occupy it with blocking network I/O at
   all — see §3.1 and the `SynthPacket` TODO.

---

## 9. Refactor plan

Phased so each step keeps the mod shippable. Ordered by risk/benefit.

### Phase 0 — dependency bump fallout (done in this pass)

1. `AIChatRequest` → `startOnce()`; `AudioPacket.AudioThread` → `startOnce()`; drop
   `RESTRICT_THREAD_USAGE = false` (§3.1).
2. `ContainerRecipe` whitelist + `IPeripheral` probe (§3.2).
3. Per-peripheral mount path; `detach()` only unmounts (§3.3).
4. `TileEntityMEBridge` single node, null-guarded `callMethod` (§3.4).
5. Remove the now-redundant `getStackInSlotOnClosing` overrides; drop the `exclude` entries that CF 1.0.1
   no longer needs; `Location#toChunkCoordinates` → `chunkCoordsFromLocation` (§2.2, §6.15).

### Phase 1 — inventory correctness (highest user impact)

6. `InventoryUtils` with `insert(IInventory, ItemStack, int slot, int amount)` returning the moved count,
   and `matches(ItemStack, ItemStack, boolean ignoreNbt)`; rewrite sorter `push`/`pull`, resupply,
   player-inv and container transfer on top of it, with unit tests (§4.1, §5.15, §5.16, §5.24).
7. Never mutate an `ItemStack` obtained from an inventory without `copy()` + `setInventorySlotContents`
   (sorter, resupply, XP, barrel all do this today).
8. Preserve NBT in every "re-stack" (`TileEntityResupplyStation.resupply` builds a fresh `ItemStack`
   without `copy`).

### Phase 2 — Lua API surface

9. `lua/LuaArgs` helper; convert all 22 peripherals. Removes ~150 hand-written `instanceof` checks and the
   §4.6/§5.31 classes of bug.
10. `LuaTable` helper; standardise on 1-based keys everywhere (`TileEntityTeleporter.getLinks`,
    `PeripheralBarrel.getOreDictEntries` are 0-based today).
11. One `AbstractPppPeripheral` base with: static `getMethodNames()`, `equals`, `attach`/`detach`, a
    `requireEnabled(Config.x)` guard, and a `translate(Throwable)` that logs properly and rethrows
    `LuaException` (§6.1, §6.4, §6.5, §6.7).
12. Delete the `name`/`getName()` pair, or make it the peripheral's display name consistently.

### Phase 3 — lifecycle and state

13. Replace every `static HashMap` with `ConcurrentHashMap` or a server-thread `MainThread.run(...)` hop
    (§5.11, §8.6).
14. Register **one** `TurtleDropCollector` listener; prune `map` on `Entity` death/world unload, or attach the
    turtle reference via `IExtendedEntityProperties` so it dies with the entity (§4.16).
15. `PeripheralMotionDetector`: register the listener on `attach`, unregister on `detach` (§4.15).
16. `TileEntityAIChatBox`: clear `sessions` on `invalidate`; `CopyOnWriteArrayList` for `computers` (§5.18).
17. `TileEntityAntenna`: prune `huds` on `detach`, give the `getHUD` coroutine a timeout (§5.12).
18. `HelmetCommand`: registry map instead of reflection (§5.30).

### Phase 4 — structure

19. Split `com.austinv11.peripheralsplusplus.tiles` (28 classes) into `peripheral/block`, `peripheral/turtle`,
    `peripheral/pocket`, keeping `TileEntity*` as thin adapters.
20. Move `com.gtranslate` out of the mod package or replace it with an `embedded` dependency (§6.19).
21. Delete the `ItemRenderTurtle`/`ContainerEmpty`/`URLCONSTANTS`/dead helpers (§7.1).
22. Rewrite `README.md` (§6.26) and add the `CODE_REVIEW` link.

---

## 10. Test strategy

Modelled directly on `MrFuzzihead/OpenPeripheral` (JUnit 4.13.2 + Mockito 4.11.0 + JaCoCo 0.8.16, tests on a
Java 8 toolchain, a non-shipped `gameTest` source set, and `-PsmokeTest` boot checks). **This is implemented and
green**: `./gradlew check` runs it end to end.

### 10.1 Build wiring

`dependencies.gradle`:

```groovy
testImplementation("junit:junit:4.13.2")
testImplementation("org.mockito:mockito-core:4.11.0")
testImplementation("org.mockito:mockito-inline:4.11.0")   // final classes (ItemStack) on Java 8
```

`addon.gradle.kts` (see the file for the full, commented implementation):

* `apply(plugin = "jacoco")`, `toolVersion = "0.8.16"`, agent enabled on every `Test` task, and every `Test` task
  pinned to a **Java 8 launcher** (the bytecode level the mod ships);
* `jacocoTestReport` (HTML + XML, CSV off) with a class filter that excludes the generated `Tags.class`,
  `client/**`, `mixins/**` and the vendored `gtranslate` package;
* `jacocoTestCoverageVerification` with a floor, wired into `check`;
* a `gameTest` source set on `compileClasspath`/`runtimeClasspath` (with Jabel filtered off, because its
  annotation processor needs a ByteBuddy agent the source set does not have), added to the run tasks only for
  `-PgameTest` / `gameTestServer`, and compiled by every `check`;
* `runServer`/`runClient` aliases to the modern (`*25`) variants — ComputerCraft 3.3.x requires lwjgl3ify,
  which needs Java 17+, so the Java 8 `runServer` cannot load CC at all (this is exactly why the existing CI
  workflow disables its server step);
* `-PsmokeTest` (and `-PsmokeTestDelay`) → `-Dperipheralsplusplus.smokeTest[.delay]`, consumed by
  `SmokeTestHook`.

### 10.2 What is testable without a game

PPP is a thin layer over `World`/`TileEntity`/`IInventory`, so the large majority of the logic is reachable
with mocks. Recommended split:

**Pure / no mocks (fastest, most valuable):**

| Class | Tests |
|---|---|
| `util/Util` | `arrayToMap` (1-based), `EnumSetToMap`, `stringToArray`, `compare` (ore dict), `getEntries`, `keyFromVal`, `iteratorToMap` |
| `mount/DynamicMount` | `normalize`, `exists`, `isDirectory`, `list`, `getSize`, `openForRead`, per-type directory, missing manifest |
| `lua/LuaArgs` (new) | 1-based indices, type errors, defaults |
| `recipe/ContainerRecipe` | `matches` accept/reject matrix, capacity limit, `getCraftingResult` NBT merge |
| `event/handler/PocketComputerCraftingHandler` | `matches`/`getCraftingResult` for the 4 shapes |
| `reference/Config` | field discovery/defaults (via CF `ConfigRegistry` in a temp dir) |
| `init/Recipes` | registration count per `Config` permutation (mock `GameRegistry` statics — needs `mockito-inline`) |
| `utils/TurtleUtil` | `getClosestEntity` distance ordering, `getTurtle` |

**Mockito (`World`, `TileEntity`, `IInventory`, `IComputerAccess`, `ILuaContext`, `ITurtleAccess`):**

| Class | Tests |
|---|---|
| `tiles/TileEntityInteractiveSorter` | `push`/`pull` amounts, full target, sided inventory, non-inventory side, slot 0 vs N, `itemReady` event |
| `tiles/TileEntityResupplyStation` | `resupply` across 56 slots, NBT preservation, wrong meta, full stack |
| `tiles/TileEntityChatBox` | `say`/`tell` range clamping, rate limiter, prefix stripping, disabled config |
| `tiles/TileEntityPlayerSensor` | range filtering, distance formula, `additionalMethods` off |
| `tiles/TileEntityTeleporter` | link NBT round-trip (no duplicates), 0/1-based index, obstruction check |
| `tiles/TileEntityAntenna` | config gate matrix (§4.14), label round-trip, `getHUD` coroutine yield |
| `tiles/TileEntityNoteBlock` | argument validation, per-tick note limit, pitch/volume |
| `tiles/TileEntityOreDictionary` | `combineStacks` limits, `transmute`, `LuaException` propagation |
| `tiles/TileEntityPeripheralContainer` | NBT round-trip, null tile entity, `needsUpdate` |
| `tiles/TileEntityTimeSensor`, `TileEntityEnvironmentScanner` | value mapping |
| `lua/LuaObjectHUD` | all 17 method argument contracts (this is where §4.6 lives) |
| `lua/LuaObjectPlayerInv` | permission matrix, `addStackToInv` partial insert |
| `lua/LuaObjectEntityControl` | player vs mob branch for every method (§4.8), click/key/mouse packing |
| `network/SynthPacket`, `AudioPacket`, `CommandPacket`, `RobotEventPacket` | `toBytes`/`fromBytes` round-trip (this is where §4.5 lives) |
| `utils/TranslateUtils` | `isPrefix`, `getLangFromWord` |
| `event/handler/RobotHandler` | operation lifetime, unknown key name (§5.28) |
| `utils/FakeTurtlePlayer`, `TurtleUtils.getTurtle` | `ITurtleTile`/`ITurtleAccess` shapes |

**Not unit-testable** (require the game): rendering (`client/**`), turtle upgrade registration, block/TE
registration, villager trades, entity spawning. These are covered by the gameTest mod.

### 10.3 Test-class matrix

Landed (141 tests, `./gradlew test`):

| Test class | Source under test | Tests | Guards |
|---|---|---:|---|
| `lua/LuaArgsTest` | `lua/LuaArgs` | 13 | 1-based indices, type errors, defaults, table builder |
| `mount/DynamicMountTest` | `mount/DynamicMount` | 9 | manifests, path normalisation, traversal, per-type paths (§3.3) |
| `mount/PeripheralMountsTest` | `mount/PeripheralMounts` | 9 | CC's mount/unmount contract, suffixed paths, scoped unmount (§3.3) |
| `util/UtilTableTest` | `utils/Util` | 8 | 1-based Lua tables, `stringToArray` round-trip |
| `util/InventoryUtilsTest` | `utils/InventoryUtils` | 21 | NBT-aware matching, exact move amounts, limits, dirty flags (§4.1) |
| `tiles/PlayerSensorTest` | `tiles/TileEntityPlayerSensor` | 12 | range, Euclidean distance, ordering (§4.13) |
| `tiles/TeleporterTest` | `tiles/TileEntityTeleporter` | 7 | NBT round-trip, link retention, 1-based table (§4.12) |
| `turtles/DropCollectorTest` | `turtles/TurtleDropCollector` | 6 | bounded map, single listener (§4.16) |
| `util/TurtleUtilTest` | `utils/TurtleUtil` | 7 | closest-entity distance ordering (§5.2) |
| `smarthelmet/HelmetCommandTest` | `smarthelmet/*` | 7 | registry instead of reflection, NBT round-trip (§5.30) |
| `tiles/TimeSensorTest` | `tiles/TileEntityTimeSensor` | 8 | calendar fields, timer lifecycle (§5.22) |
| `turtles/BarrelTest` | `turtles/peripherals/PeripheralBarrel` | 9 | empty-barrel NPE, meta matching, no per-tick NBT (§5.44) |
| `network/PacketCodecTest` | `network/*` | 6 | `toBytes`/`fromBytes` round-trip, incl. `SynthPacket.rate` (§4.5) |
| `reference/ReferenceTest` | `reference/Reference` | 6 | turtle/pocket id uniqueness and ranges, proxy classes |

Target list, in descending value/effort, to reach the 65 % ratchet:

| Test class | Source under test | Est. tests | Blocks |
|---|---|---:|---|
| `InteractiveSorterTest` | `tiles/TileEntityInteractiveSorter` | 16 | §4.1 |
| `ChatBoxTest` | `tiles/TileEntityChatBox` | 14 | §5.8–5.10 |
| `LuaObjectHudTest` | `lua/LuaObjectHUD` | 22 | §4.6 |
| `LuaObjectPlayerInvTest` | `lua/LuaObjectPlayerInv` | 12 | §4.18, §5.15–5.16 |
| `LuaObjectEntityControlTest` | `lua/LuaObjectEntityControl` | 16 | §4.7, §4.8 |
| `PlayerSensorTest` | `tiles/TileEntityPlayerSensor` | 10 | §4.13 |
| `TeleporterTest` | `tiles/TileEntityTeleporter` | 12 | §4.12 |
| `AntennaTest` | `tiles/TileEntityAntenna` | 10 | §4.14 |
| `NoteBlockTest` | `tiles/TileEntityNoteBlock` | 8 | §4.4 (partly covered now) |
| `ResupplyStationTest` | `tiles/TileEntityResupplyStation` | 10 | §5.47 |
| `ContainerRecipeTest` | `recipe/ContainerRecipe` | 12 | §3.2 |
| `PocketCraftingTest` | `event/handler/PocketComputerCraftingHandler` | 10 | §5.27 |
| `OreDictionaryPeripheralTest` | `tiles/TileEntityOreDictionary` | 10 | §5.24 |
| `PeripheralContainerTest` | `tiles/TileEntityPeripheralContainer` | 8 | §4.9 |
| `ConfigTest` | `reference/Config` | 6 | §6.10–6.11 |
| `RobotHandlerTest` | `event/handler/RobotHandler` | 8 | §5.28 |
| `TurtleUtilTest` | `utils/TurtleUtil`, `TurtleUtils` | 12 | §5.2 |
| `SimpleRunnableContractTest` | `cleverbot/AIChatRequest` | 4 | §3.1 |
| **Total** | | **~262** | |

### 10.4 Coverage gates

The floor in `addon.gradle.kts` is **11 % line / 10 % branch**, just under the measured baseline
(780 / 6406 lines, 361 / 3434 branches). It is a *regression guard*, not a target, and `check` depends on it so CI
cannot drift downwards.

Ratchet plan — raise the floor in the same PR that adds each row above:

| Milestone | Line | Branch |
|---|---:|---:|
| batch 1 (harness landing) | 4 % | 4 % |
| batch 2 | 7 % | 7 % |
| batch 3 (now) | 11 % | 10 % |
| after the chat box/Lua-object tests | 15 % | 12 % |
| after Phase 1–2 refactors (§9) | 35 % | 25 % |
| steady state | 65 % | 50 % |

Excluded from the report (documented in `addon.gradle.kts`):

* `com/austinv11/peripheralsplusplus/Tags.class` (generated),
* `client/**` (rendering, `@SideOnly`),
* `mixins/**` (accessors, no logic),
* `com/gtranslate/**` (vendored).

### 10.5 In-game test mod (`src/gameTest`)

Landed: a separate, non-shipped `@Mod` that exits the game with the assertion status.

```
src/gameTest/java/com/austinv11/peripheralsplusplus/gametest/GameTestMod.java      # @Mod + exit codes
src/gameTest/java/com/austinv11/peripheralsplusplus/gametest/ServerGameTests.java  # assertions
```

The assertions run on the **server thread** at `FMLServerStartedEvent`, where the overworld exists and block
mutations are legal without a tick-loop hand-off. Covered today:

1. every block and item in `ModBlocks`/`ModItems` is in the registry;
2. the `Config` numeric options survive config loading;
3. every declared turtle upgrade id is unique, above CC's reserved range, and disjoint from the
   RedPower-like families (`ComputerCraftAPI` swallows duplicate-id errors, so this cannot be caught in-game);
4. `IPlusPlusPeripheral.Provider` resolves a **placed** chat box and speaker, returns the tile entity from
   `getTarget()`, and returns `null` for stone and air;
5. the docs mount path is per peripheral type (§3.3);
6. a placed peripheral container is a peripheral and returns an (empty) table.

Still to add (needs a tick-loop hand-off, so a worker thread at `FMLServerStartedEvent` rather than the server
thread — CC computers block on `SynchronousExecutor` tasks that only run from the tick):

7. a real CC computer running Lua against a placed peripheral (`peripheral.getNames()`,
   `peripheral.call("left","say","…")`);
8. `mounter.list("/ppp")` with **two** PPP peripherals attached, asserting both directories appear (§3.3);
9. `push`/`pull` moving exactly `amount` items on a real inventory (§4.1);
10. the container craft/`getContainedPeripherals` round trip (§3.2);
11. `pocket.equipBack()` / `unequipBack()` for both pocket upgrades;
12. `/ppp` detach does not throw (would have thrown before §3.3).

Client half (`runClient25 -PgameTest`, non-blocking in CI): open `GuiHelmet`, push a `CommandPacket` through the
real network handler, assert the render stack; exercise the config GUI.

### 10.6 Headless smoke test

`-PsmokeTest` sets `-Dperipheralsplusplus.smokeTest=true`. `SmokeTestHook` then starts a daemon thread that waits
for the first `WorldEvent.Load` on a dedicated server (exiting at load-complete alone races spawn-chunk generation,
so the server never prints `Done`), sleeps `smokeTestDelay` ms (default 2000), prints
`PERIPHERALSPLUSPLUS_SMOKE_TEST_OK`, and calls `FMLCommonHandler.instance().exitJava(0, false)`. It is a no-op
without the property.

`.github/workflows/smoke-test.yml` (added in this pass, mirroring OpenPeripheral):

| Job | Command | Assertion | Blocking |
|---|---|---|---|
| `server-smoke` | `./gradlew runServer25 -PsmokeTest` | `Done (` **and** `PERIPHERALSPLUSPLUS_SMOKE_TEST_OK` in the log | yes |
| `game-test` | `./gradlew gameTestServer` | `PPP_GAMETEST_OK` in the log | yes |
| `client-smoke` | `xvfb-run ./gradlew runClient25 -PsmokeTest` | exit status | no |

This gives the mod its first real server boot check and complements `build-and-test.yml`'s `client-only: true`
(§6.25).

### 10.7 Verification commands

```sh
./gradlew compileJava                     # clean on CC 3.3.1 / CF 1.0.1
./gradlew test                            # JUnit 4 suite (51 tests)
./gradlew check                           # + spotless, checkstyle, coverage floor, gameTest compile
./gradlew jacocoTestReport                # build/reports/jacoco/test/html/index.html
./gradlew jacocoTestCoverageVerification
mkdir -p run/server && echo "eula=true" > run/server/eula.txt
./gradlew runServer25 -PsmokeTest         # boots a real dedicated server
./gradlew gameTestServer                  # in-game assertions
```

---

## 11. Appendix A — findings index

P0: §3.1 `cleverbot/AIChatRequest.java:43` · §3.2 `recipe/ContainerRecipe.java:50,52,70` · §3.3
`tiles/Mounted*.java`, `turtles/peripherals/MountedPeripheral.java:16-21` · §3.4
`tiles/TileEntityMEBridge.java:367` · §3.5 `PeripheralsPlusPlus.java:215`

P1: §4.1 `tiles/TileEntityInteractiveSorter.java:118,119,176` · §4.2
`turtles/peripherals/PeripheralTank.java:91,236` · §4.3 `turtles/peripherals/PeripheralXP.java:180-190` ·
§4.4 `tiles/TileEntityNoteBlock.java:90` · §4.5 `network/SynthPacket.java:60,116` · §4.6
`lua/LuaObjectHUD.java:116,253` · §4.7 `lua/LuaObjectEntityControl.java:209,229,234` · §4.8
`lua/LuaObjectEntityControl.java:86` · §4.9 `tiles/TileEntityPeripheralContainer.java:39,102,123,150` · §4.10
`turtles/peripherals/PeripheralDispenser.java:43-50` · §4.11 `tiles/TileEntityRFCharger.java:64` · §4.12
`tiles/TileEntityTeleporter.java:58,145-152,161,176` · §4.13 `tiles/TileEntityPlayerSensor.java:91-99` · §4.14
`tiles/TileEntityAntenna.java:88` · §4.15 `pocket/peripherals/PeripheralMotionDetector.java:33` · §4.16
`turtles/TurtleDropCollector.java:15,21` + `PeripheralsPlusPlus.java:219` · §4.17
`turtles/peripherals/PeripheralChunkLoader.java:60,101` · §4.18 `lua/LuaObjectPlayerInv.java:276-286`

P2/P3: see the tables in §5 and §6.

## 12. Appendix B — how this review was produced

* Diffed `Computercraft-Legacy-3.3.0-sources.jar` vs `3.3.1`, and `CollectiveFramework-1.0.0-sources.jar` vs
  `1.0.1`, from the Gradle module cache.
* Read the ComputerCraft 3.3.1 `IComputerAccess`/`IComputerAccess` implementations
  (`core/lua/lib/LuaEnvironment.java`, `core/apis/PeripheralAPI.java`) to establish the `mount`/`unmount`
  contract used in §3.3.
* Verified `Entity#setPositionAndRotation2` and `ChunkCoordinates#equals` against the RFG-patched Minecraft
  sources (`build/rfg/mcp_patched_minecraft-sources.jar`) rather than assuming.
* Scripted scans for unreferenced public/private methods, fields, and classes (§7.1), and for
  `printStackTrace`/`synchronized`/static-mutable-collection patterns.
* `./gradlew compileJava --offline --rerun` to confirm the tree compiles against both upgraded dependencies.

## 13. Appendix C — files added or changed in this pass

Fixes:

| File | Change |
|---|---|
| `cleverbot/AIChatRequest.java` | `startOnce()` + named runnable (§3.1) |
| `network/AudioPacket.java` | `startOnce()`, logger instead of `printStackTrace` (§3.1, §6.1) |
| `PeripheralsPlusPlus.java` | dropped `RESTRICT_THREAD_USAGE`, added `loadComplete` → `SmokeTestHook` (§6.2, §10.6) |
| `recipe/ContainerRecipe.java` | whitelist + `IPeripheral` probe, null-safe id lookup, ordered id merge (§3.2) |
| `mount/DynamicMount.java` | per-peripheral `mountPath`, `getDirectory()`, String constructor (§3.3) |
| `tiles/MountedTileEntity.java`, `MountedNetworkedTileEntity.java`, `MountedTileEntityInventory.java`, `turtles/peripherals/MountedPeripheral.java` | delegate to `PeripheralMounts` (§3.3) |
| `tiles/TileEntityMEBridge.java` | single grid node, null-guarded `callMethod` (§3.4) |
| `tiles/TileEntityInteractiveSorter.java` | correct push/pull arithmetic, NBT-aware merging, empty-slot guard (§4.1) |
| `tiles/TileEntityNoteBlock.java` | `LuaArgs` argument handling, pitch fix (§4.4) |
| `network/SynthPacket.java` | correct `rate`/`volume` handling, logger (§4.5) |

New main sources:

| File | Purpose |
|---|---|
| `lua/LuaArgs.java` | shared Lua argument reader (the §6.28 / §9 phase-2 helper) |
| `mount/PeripheralMounts.java` | correct `attach`/`detach` mount bookkeeping |
| `SmokeTestHook.java` | `-PsmokeTest` boot-and-exit hook |

New test sources:

| File | Tests |
|---|---|
| `src/test/java/.../lua/LuaArgsTest.java` | 13 |
| `src/test/java/.../mount/DynamicMountTest.java` | 9 |
| `src/test/java/.../mount/PeripheralMountsTest.java` | 9 |
| `src/test/java/.../util/UtilTableTest.java` | 8 |
| `src/test/java/.../network/PacketCodecTest.java` | 6 |
| `src/test/java/.../reference/ReferenceTest.java` | 6 |
| `src/gameTest/java/.../gametest/GameTestMod.java` | in-game harness |
| `src/gameTest/java/.../gametest/ServerGameTests.java` | 6 in-game checks |

Build / CI:

| File | Change |
|---|---|
| `dependencies.gradle` | JUnit 4.13.2, Mockito 4.11.0 (+ inline) as `testImplementation` |
| `addon.gradle.kts` | JaCoCo, Java 8 test launcher, `gameTest` source set, `gameTestServer`, run aliases, smoke-test properties, coverage floor wired into `check` |
| `.github/workflows/smoke-test.yml` | new: server boot, in-game assertions, Xvfb client (non-blocking) |
| `CODE_REVIEW.md` | this document |

## 14. Appendix D — batch 2 (Phase 1: inventory correctness + remaining P1s)

Everything in §4 is now fixed. This batch collapsed the duplicated inventory logic onto one tested helper and
closed the rest of the P1 list.

| File | Change |
|---|---|
| `utils/InventoryUtils.java` *(new)* | `matches` (NBT-aware), `room`, `slotsFor`, `move`, `at` — the single implementation the sorter, resupply and player interface delegate to; 18 tests |
| `tiles/TileEntityInteractiveSorter.java` | push/pull now call `InventoryUtils.move`; the duplicate `getDefaultSlots`/`isInventoryOnSide`/`getInventoryForSide`/`isSameItem` copies are gone (~80 lines removed) |
| `tiles/TileEntityMEBridge.java` | `retrieve` computes capacity before extracting and refunds anything that did not fit (no more item loss); helpers collapse onto `InventoryUtils.at`; `getDefaultSlots`/`getRemainingSlots` removed |
| `lua/LuaObjectPlayerInv.java` | rewritten on `LuaArgs` + `InventoryUtils.move`; permissions deny instead of NPE-ing; `pushToSlot` honours its first argument; the partial-insert duplication path is gone |
| `lua/LuaObjectHUD.java` | `drawTexture` reads `v` after 6 arguments, `addTextField` validates its 6th |
| `lua/LuaObjectEntityControl.java` | `heal` heals the mob; `setTarget`/`setAttackTarget` bounded to 64 blocks and refuse non-`EntityLivingBase` targets; navigation NPE-guarded |
| `tiles/TileEntityPeripheralContainer.java` | `needsUpdate` reset, `readFromNBT` clears + bounds, `addPeripheral` rejects null/non-peripheral/over-capacity |
| `tiles/TileEntityTeleporter.java` | `readFromNBT` clears and bounds; `addLink` keeps the newest; `getLinks` is 1-based; block updates only on a successful teleport, with the destination block |
| `tiles/TileEntityPlayerSensor.java` | honours the range argument (negative = unlimited), Euclidean distance including Y, nearest-first ordering, no swallowed exceptions |
| `tiles/TileEntityAntenna.java` | correct config gate split (helmet methods vs nano-bot methods); `setLabel` returns |
| `tiles/TileEntityRFCharger.java` | empty-list guard, capacity-clamped fuel, energy extracted from what was actually accepted |
| `turtles/peripherals/PeripheralDispenser.java` | 1-based slot argument, null-safe dispense result, dead statement removed |
| `turtles/peripherals/PeripheralTank.java` | fill/drain honour the container's return values (no more destroyed fluid), unsupported buckets refused |
| `turtles/peripherals/PeripheralXP.java` | empty-slot guard, NBT and stack size preserved, every rolled enchantment kept, non-zero default enchant seed |
| `turtles/peripherals/PeripheralChunkLoader.java` | persisted tickets are re-forced on load, null ticket guarded |
| `pocket/peripherals/PeripheralMotionDetector.java` | listener registered on attach and removed on detach |
| `turtles/TurtleDropCollector.java` | bounded, synchronised map pruned on world unload; one listener instead of 30 |
| `PeripheralsPlusPlus.java` | `registerUpgrade` registers the drop listener once |
| `addon.gradle.kts` | coverage floor raised 4 % → 7 % |

Test suite: **103 tests in 10 classes**, line coverage **7.6 %** (489/6450), branch **7.6 %**, `./gradlew check` green.

## 15. Appendix E — batch 3 (section 5 P2: robustness, thread safety, duplication)

Closed 39 of the 48 P2 items.

**Thread safety (§5.11).** Every static collection that is written from a non-server thread is concurrent now:
`GuiHelmet.renderStack`, `GuiSmartHelmetOverlay.renderStack` (written by the netty packet handler, read on the client
tick), `CreativeTabPPP.upgrades`, `MessageCommand.messageStacks`, `TileEntityAntenna.antenna_registry`/`computers`/
`huds`/`associatedEntities`, `TileEntityChatBox.chatBoxMap`, `ComputerCraftRegistry.pocketUpgrades`. Both GUI map
reads are now a single `get` instead of `containsKey` + `get`.

**Registry misuse (§5.11 cont).** `ComputerCraftRegistry.craftingRecipes` was a `HashMap<ItemStack, Integer>`,
iterated with `isItemEqual`. `ItemStack` does not override `hashCode`, so equal items collided by identity and the map
was never actually a lookup. It is a registration-ordered `List` now, with `findUpgradeFor(ItemStack)`.

**No reflection from the network (§5.30, §5.40).** `HelmetCommand` is a `LinkedHashMap`-backed registry of factories;
an unknown wire name is logged and dropped, and a corrupt packet can no longer leave a null slot that NPEs later.
`NBTTagCompound#setString` rejects `""`, and `readFromNBT` returns `""` for a missing key, so re-serialising a command
threw `IllegalArgumentException` on the server thread — `HelmetCommand#writeString` tolerates it, and
`DrawRectangleCommand` no longer NPEs on a null colour.

**Per-player state (§5.31).** `MessageCommand` lines travel in the command's own NBT list instead of a shared static
`Stack`, so two helmet wearers no longer see each other's messages and a message containing a comma survives.

**Input (§5.32, §5.33).** All client calls moved to `client/RidableTurtleInput` (`@SideOnly`); `EntityRidableTurtle`
no longer imports `Minecraft`/`Keyboard`, and the per-tick `new ItemStack(Items.carrot_on_a_stick)` allocation is a
cached constant.

**Per-tick cost (§5.22, §5.23, §5.44, §5.48).** `TileEntityTimeSensor` uses a `Calendar` instead of building a
`SimpleDateFormat`; `TileEntityEnvironmentScanner` does one biome lookup instead of three and lost its duplicate
`getTemperature`/`getTemp` method pair (a Lua API change: the method list is now 4 entries); `PeripheralBarrel#update`
only touches NBT when something changed; `PeripheralPeripheralContainer` stopped allocating a `List` per call.

**Correctness.** `Util.compare` resolves ore ids once per stack; `Util.getPlayer`/`getPlayers` null-guard the server
and use `getCommandSenderName()`; `TurtleUtil.getClosestEntity`/`getClosestShearableEntity` share one implementation
that actually tracks the closest distance (they returned the *last* candidate); `LuaObjectPeripheralWrap` and both
containers raise "no such peripheral" instead of NPE-ing; `LuaObjectHUD` handles a logged-off player and no longer
drains its command deque on send; `TileEntityAntenna`'s config flags gate the right halves of the API; the pocket
crafting recipe and the container tooltip handler are null-safe; `PeripheralResupply` accepts `link(x, y, z)` and
refuses a cross-dimension link; `PeripheralSignReader`/`PeripheralGarden` collapsed from three/six copies to one
implementation each.

**Registration (§5.35–§5.39).** `BlockContainerPPP#dropItems` no longer cast-fails on a non-inventory tile, allocates
one `Random` per block instead of per item, and clears the slot instead of zeroing the stack size. `ModBlocks.meBridge`
is created lazily like every other optional block; the RF charger block is registered for BuildCraft as well as
ThermalExpansion (the tile entity already was); the AE recipe is guarded on the block existing; the permission card's
1:1 self-recipe is gone.

**Tests.** 103 → **141** in 14 classes. New: `TurtleUtilTest` (7), `HelmetCommandTest` (7), `TimeSensorTest` (8),
`BarrelTest` (9), plus `InventoryUtils.at` and four `CommandPacket` codec cases. Coverage floor 7 % → **11 % line /
10 % branch** (measured 12.2 % / 10.5 %).

**Still open** (8 of 48, all minor):

| # | Remaining work |
|---|---|
| §5.7 | `ChatUtil.getPlayer` still matches on `getDisplayName()`; it needs the same `getCommandSenderName()` change as `Util` |
| §5.8 | `TileEntityChatBox.tell` still does not clamp its range to `Config.sayRange` (it needs `ChatUtil` to expose the clamp) |
| §5.15 | (superseded by the batch-2 `LuaObjectPlayerInv` rewrite — the row is stale and should be struck) |
| §5.16 | (superseded by the batch-2 `InventoryUtils.move` rewrite — the row is stale) |
| §5.25 | The `getStackInSlotOnClosing` override is kept for its `markDirty()` side effect; delete once the container logic moves to `InventoryUtils` |
| §5.41 | The unused `world` constructor parameter on `AudioPacket`/`SynthPacket` |
| §5.42 | `TurtleProjRed.getProjRedTool` still builds an `ItemStack` from a possibly-missing registry item |
| §5.43 | `TurtleRidable.update` still does an AABB scan per tick and uses an exception for the "not spawned yet" case |
