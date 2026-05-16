# PlayerShop

A Paper 1.21 plugin for player-owned chest shops. Players turn any chest into a shop, set a price, stock items, and let other players buy directly from the chest. Everything is GUI-based — no commands needed for normal players.

---

## Features

- **Chest-based shops** — any chest can become a shop; the chest is the stock
- **Physical setup flow** — shift+right-click the chest with a shovel to enter setup, stock items, close the chest, then set a price in the GUI
- **GUI price editor** — red glass panes decrease, green glass panes increase; adjustable by ±0.10 / ±1 / ±10 / ±100 per click
- **Buyer GUI** — single screen to pick quantity and confirm; shows live price total and available stock
- **Per-stack pricing** — price is set per 64 items; partial quantities are priced proportionally
- **Floating holograms** — armor stand holograms above every chest showing owner, item, price per stack, stock count, and "Right-click to buy"
- **Double-chest support** — full double-chest inventory is available as shop stock
- **Dupe-safe** — HIGHEST priority event cancellation on all GUI interactions
- **Vault economy** — buyer pays, owner receives (works for offline owners too)
- **Shop protection** — shop chests cannot be broken, exploded, hoppered, pistoned, or burned by other players

---

## How to Create a Shop

1. Place a chest anywhere.
2. **Shift+right-click** the chest while holding any **shovel**.
3. A hologram appears. The chest opens — stock it with items you want to sell (all items must be the same type).
4. Close the chest. The price GUI opens automatically.
5. Set your price per stack using the glass pane buttons, then click **Confirm**.
6. Done. Other players can now right-click your chest to buy.

To **restock**: right-click your own shop chest normally (no shovel needed).

---

## Commands

All commands require `playershop.admin`.

| Command | Description |
|---|---|
| `/playershop reload` | Reload config and refresh all holograms |
| `/playershop remove <x> <y> <z> [world]` | Delete a shop by location |
| `/playershop info <x> <y> <z> [world]` | Show shop details |

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `playershop.use` | true | Create and manage your own shops |
| `playershop.admin` | op | Admin commands (reload, remove, info) |

---

## Configuration

```yaml
# config.yml defaults
max-shops-per-player: 5       # how many shops one player can own
holograms:
  enabled: true               # toggle floating holograms on/off
settings:
  allow-free-shops: false     # if true, players can set a $0.00 price
```

---

## Installation

1. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin (e.g. EssentialsX).
2. Drop `PlayerShop-x.x.x.jar` into your server's `plugins/` folder.
3. Restart the server.
4. Configure `plugins/PlayerShop/config.yml` as needed.

---

## Compatibility

- Paper 1.21
- Java 21
- Vault (soft-depend — shops work without it but no money changes hands)
- Geyser/Bedrock compatible (fully GUI-based, no chat input required)

---

## Current Status

Stable — v1.0.5
