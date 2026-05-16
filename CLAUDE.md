# PlayerShop — Claude Context

## Project purpose

PlayerShop is a Paper 1.21 plugin that lets players turn any chest into a player-owned shop. Shops are created by shift+right-clicking a chest with a shovel, stocking items, and setting a price through a GUI. Buyers right-click the chest to open a buy GUI. Economy runs through Vault. Holograms float above each shop chest showing live stock and price info.

---

## Build

No Gradle. Java 25 is incompatible with Gradle 8.x. All builds use `build.sh`.

```bash
bash build.sh
```

The script:
1. Reads the version from `version.properties`
2. Stamps `${pluginVersion}` into `plugin.yml` via sed
3. Compiles all `.java` sources with `javac --release 21`
4. Packages into a jar

**Classpath:** jars are pulled from `../WaypointSystem/libs/` (shared lib folder). Required:
- `paper-api.jar`
- `vault-api.jar`
- `adventure-api.jar`, `adventure-key.jar`, `examination-api.jar`
- `bungeecord-chat.jar`
- `guava.jar`
- `jetbrains-annotations.jar`

**Output:** `build/libs/PlayerShop-{version}.jar`

**Version bump:** edit `version.properties` before building.

---

## Java / Paper target

- Java: 21 (`--release 21`)
- Paper API: 1.21
- `api-version: '1.21'` in plugin.yml

---

## Dependencies

- **Vault** (soft) — economy. If Vault is absent, shops still work but no money changes hands.
- **DecentHolograms** (soft) — listed in plugin.yml for future swap-in; current holograms are armor stand based and need no external plugin.

---

## Important design rules

- All GUI interactions use `EventPriority.HIGHEST` with item-use and block-use cancelled to prevent dupes.
- `COLLECT_TO_CURSOR` and `MOVE_TO_OTHER_INVENTORY` from the shop chest bottom inventory are also cancelled.
- Proportional pricing: price is set per stack (64 items). Per-item price = `price / 64.0`. Buyer total = `round(qty × perItem, 2 decimal places)`.
- Physical chest setup: owner shifts+shovels the chest, stocks items, closes it. On close, all items must be the same type or setup aborts.
- Session state (price editing, buyer purchase amount) is tracked in `Map<UUID, Session>` in `ShopGUI`. Sessions are sealed interface types.
- Shop data persists in `shops.yml`.
- Hologram lines update on every purchase and shop modification. `HologramManager.createOrUpdate(shop)` is the single entry point for updates.
- Shop protect listener blocks break, explosion, hopper, piston, and fire interactions on shop chests.

---

## Current version

1.0.5

---

## Known issues / next TODOs

- No known bugs in 1.0.5.
- DecentHolograms integration not yet implemented (armor stand holograms are the current system).
- No expiry or auto-cleanup for empty shops.
- Shop search or list command could be useful at scale.
