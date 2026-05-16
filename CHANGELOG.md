# PlayerShop Changelog

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
