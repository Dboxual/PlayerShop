# PlayerShop Changelog

## [1.0.7] - 2026-05-21

### Investigation: PlayerInteractEvent guard

**Root cause confirmed: no cross-plugin interference exists.** The guard `!(block.getBlockData() instanceof Chest)` already returns immediately for every non-chest block. Signs (Bridge queue signs, any other signs), Pinpoint objects, and all other non-chest blocks physically cannot reach any `event.setCancelled(true)` call in `ShopInteractListener`.

### Changed

- **`ShopInteractListener`** — Refactored block eligibility into a named `isEligibleBlock(block)` helper (checks `org.bukkit.block.data.type.Chest`) so the guard is explicit and auditable.
- **Debug logging** — When `settings.debug: true`, logs every interaction at the start of the handler:
  - For non-chest blocks: logs block type, location, and "no action taken, event untouched" — confirms PlayerShop skips signs/pinpoints.
  - For chest blocks: logs primary location, shop present/absent, `shovelShift`, and event-cancelled state before processing.
  - Per-branch decision logged (setup mode, buyer GUI, vanilla, new shop creation).
- **`build.sh` fixed** — Classpath was pointing to a non-existent `WaypointSystem/libs` directory. Now points to `../Bridge-Plugin/libs`. Added Windows path separator auto-detection (`;` vs `:`) matching Bridge-Plugin's build script pattern.

---

## [1.0.6] - 2026-05-16

### Fixed
- **Price GUI — duplicate click handling** — `onInventoryClick` now uses `ignoreCancelled = true` and filters out non-intentional `ClickType`s (`DOUBLE_CLICK`, `SWAP_OFFHAND`, `UNKNOWN`, `CREATIVE`). Bottom-inventory clicks are fully cancelled instead of silently ignored. Each button press now adjusts the price exactly once.
- **Price GUI — floating-point drift on ±0.10** — `PriceSession` now stores price as integer cents (`int pendingCents`) instead of `double`. All delta arithmetic is integer (e.g. +0.10 → +10 cents). No rounding errors; three +0.10 clicks give exactly $0.30.
- **Price GUI — item/display swap** — The item being sold now appears in the center of row 1 (slot 13). The price-display block now sits in the center of row 0 (slot 4), flanked directly by the +/− buttons. All buttons, reset, confirm, and glass-pane layout are unchanged.

---

## [1.0.5] - 2026-05-16

### Changed
- **Price GUI redesigned** — all adjustment buttons are now stained glass panes. Red panes (−100 / −10 / −1 / −0.10) on the left, item preview in the center, green panes (+0.10 / +1 / +10 / +100) on the right. Price display moved to the center of row 1. Reset moved to bottom-left (slot 18), Confirm moved to bottom-right (slot 26).
- **Free-shop guard** — confirming a $0.00 price is now blocked by default. Set `settings.allow-free-shops: true` in config.yml to permit it.

### Added
- `settings.allow-free-shops` config option (default: `false`).

---

## [1.0.4] - 2026-05-16

### Changed (breaking redesign)
- **Setup flow completely reworked** — shift+right-click with a shovel now opens the physical chest directly. Owner stocks the items, closes the chest, then a price GUI appears automatically. No separate owner GUI or item-picker GUI needed.
- **Item validation on close** — all items in the chest must be the same type when the owner closes during setup. Mixed items produce a warning and abort setup without changing the shop.
- **Price is now per stack** — hologram, price GUI title, and buy GUI all display and calculate price as per-stack with proportional per-item breakdown.
- **Buyer GUI redesigned** — single 27-slot screen: left side decrease buttons (−1 stack / −8 / −1), center item display showing selected amount, right side increase buttons (+1 / +8 / +1 stack), price total row, confirm row. No intermediate stock-view screen.
- **Holograms** — unconfigured shop shows "Shop / Place items to sell"; configured shop shows owner name / item / price per stack / stock count / "Right-click to buy".

### Added
- **Purchase sounds** — buyer hears a subtle orb-pickup ping on successful purchase; owner receives a villager-trade sound + message if they are online.
- **Owner restock flow** — regular right-click on your own shop chest opens it normally for restocking (unchanged from v1.0.2, but now the only owner interaction besides setup).

### Removed
- Owner GUI (set item / set price / stock / delete slots) — replaced by the new chest-based setup flow.
- Buyer stock-view intermediate screen — buyer goes directly to the amount-select GUI.

---

## [1.0.3] - 2026-05-16

### Added
- **Hologram system** — invisible armor stand holograms spawn above every shop chest showing owner name, item, price, stock count, and "Right-click to buy". Toggled via `holograms.enabled` in config.yml. DecentHolograms listed as soft-depend for future swap-in.
- **Buyer stock view** — right-clicking a shop chest now opens a stock-info GUI first, showing item details, price, and stock. Click the displayed item to proceed to the buy amount screen.
- **Buy amount GUI** — dedicated screen with Buy 1 / 8 / 16 / 32 / 64 / Max buttons plus a quantity display, Back, and Confirm. Max auto-calculates from balance, chest stock, and inventory space.
- **Owner item selector** — clicking "Set Item" in the owner GUI shows the actual chest contents; the owner clicks any item to set it as the sell item — no cursor tricks or chat required (Bedrock-compatible).

### Changed
- Non-owners right-clicking an unconfigured shop now receive a "hasn't been set up yet" message instead of an empty GUI.

### Fixed
- Holograms are properly cleaned up when a shop is deleted (block break, `/playershop remove`).
- Hologram stock count updates live after each purchase.

---

## [1.0.2] - 2026-05-16

### Fixed
- Regular right-click on a shop chest now opens the physical chest for the **owner/admin** so they can stock it normally
- Regular right-click by a **non-owner** on a shop chest still opens the buyer GUI
- Regular right-click on a **non-shop chest** is fully vanilla (unchanged)
- Only shift+right-click with a shovel opens the setup GUI

---

## [1.0.1] - 2026-05-16

### Changed
- **Owner GUI completely redesigned** — four dedicated slots: Set Item, Set Price, View Stock, Delete Shop
- **Price setting is now fully GUI-based** — no commands required for normal players (Bedrock-compatible)
- New **price-edit sub-GUI** opens when clicking Set Price:
  - Row of delta buttons: −1000 / −100 / −10 / −1 / [price display] / +1 / +10 / +100 / +1000
  - Reset, Back (discard), and Confirm buttons
  - Pressing ESC or Back returns to the owner GUI without saving
  - Confirm saves the price and returns to the owner GUI
- **Stock count** now displayed in the owner GUI; click to refresh
- `/playershop` command is now **admin-only** (`playershop.admin`): `reload`, `remove`, `info`
- Removed `/playershop setprice` — price is set entirely through the GUI

### Fixed
- Dupe safety: price-edit GUI inherits the same HIGHEST-priority cancel logic as main GUI

---

## [1.0.0] - 2026-05-16

- Initial clean rebuild
- Shift+right-click chest with shovel to create/manage shop
- Right-click to open buyer GUI; click item to purchase
- Dupe-safe GUI (HIGHEST-priority cancel on all top-inventory interactions)
- Vault economy: buyer pays, owner receives (supports offline owner)
- Full double-chest stock support via block-data ChestUtil
- ShopProtectListener: break, explosion, fire, hopper, piston protection
- YAML persistence (shops.yml)
