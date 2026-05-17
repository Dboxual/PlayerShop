# PlayerShop

Set up your own item shop using a chest. No commands needed — just place, stock, and sell.

---

## Features

- Create a shop from **any chest** with a shovel
- Set your item and price through a **clean GUI** — no commands required
- Buyers see a hologram above your chest with price and stock info
- **Per-stack pricing** with clear per-item breakdown
- Money goes straight to you (even if you're offline)
- Full **double-chest** support

---

## How It Works

### Creating a Shop

1. Hold a **Shovel** and **Shift+right-click** a chest
2. The chest opens — put in the items you want to sell, then close it
3. A **price-setting GUI** opens automatically
4. Use the **green buttons** to increase the price and **red buttons** to decrease it
5. Hit **Confirm** — your shop is live

> All items in the chest must be the same type when you close it.

### Restocking

Just **right-click** your shop chest normally to open it and add more items.

### Price GUI

The price GUI gives you fine control over your price:

| Button | Change |
|---|---|
| Green (+0.10) | +$0.10 |
| Green (+1) | +$1.00 |
| Green (+10) | +$10.00 |
| Green (+100) | +$100.00 |
| Red buttons | Same amounts, decrease |

Hit **Reset** to go back to $0. Hit **Confirm** to save.

### Buying from a Shop

1. **Right-click** a shop chest
2. Choose how many you want to buy
3. Hit **Confirm** — items go to your inventory, money goes to the owner

---

## Holograms

Every shop displays a floating label above the chest showing:

- Owner name
- Item being sold
- **Price per stack** (and per-item breakdown)
- Current **stock count**
- "Right-click to buy"

Unconfigured shops display: *Shop / Place items to sell*

---

## Commands

These are **admin-only** commands:

| Command | Description |
|---|---|
| `/playershop reload` | Reload plugin config |
| `/playershop remove` | Force-remove a shop |
| `/playershop info` | View shop info |

Normal players don't need any commands — everything is GUI-based.

---

## Tips

- Price is **per stack** — if you set $10, players pay $10 for a full stack (proportional for smaller amounts)
- You receive payment **even if you're offline**
- Breaking your shop chest **deletes the shop** and its hologram — be careful
- You can move your items out of the chest at any time to restock different items (just redo setup)
- **Free shops** ($0.00) are blocked by default — an admin can enable them in config

---

## Changelog Summary

**v1.0.6** — Fixed price buttons firing multiple times per click. Eliminated floating-point rounding errors on ±$0.10. Item and price-display positions corrected in the GUI.

**v1.0.5** — Full price GUI redesign using colored glass panes. Free-shop guard added.

**v1.0.4** — Setup flow rebuilt: stock items → close chest → price GUI. Per-stack pricing introduced. Buyer amount GUI redesigned.

**v1.0.3** — Hologram system added. Buyer stock-view screen added.

**v1.0.2** — Owner right-click restocking fixed.

**v1.0.1** — Price setting moved fully into GUI (no commands needed for players).

**v1.0.0** — Initial release: chest shops, Vault economy, dupe protection, YAML persistence.
