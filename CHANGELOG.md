# PlayerShop Changelog

## [1.0.25] - 2026-05-29

### Changed — Stock GUI replaces Restock GUI (`ShopGUI.java`)

**Owner listing click now opens a chest-like stock GUI instead of the old restock flow.**

**Stock GUI ("Stock: \<ItemName\>", aqua title, 54 slots):**
- Replaces `RestockSession` entirely with `StockSession`.
- Opening: virtual stock is materialised as real item stacks in slots 0–44
  (e.g. stock=100 on a stackable item → slot 0 = 64, slot 1 = 36).
- Stock area (slots 0–44): owner freely takes items out or puts matching items in.
  Non-matching items on cursor, NUMBER_KEY hotbar swap, or shift-click from
  player inventory are rejected with "Only \<ItemName\> can be deposited here."
- Slot 45: **Remove Listing** button (barrier, red).
  - Stock > 0: "Withdraw all stock before removing this listing." — GUI stays open.
  - Stock = 0: listing removed immediately, owner GUI reopens, hologram refreshed.
- Slots 46–53: inert guard slots — no item movement allowed.
- **No confirm button.** Closing the GUI (Esc, new GUI open, disconnect) is the save trigger.
- On close: all items in slots 0–44 are counted; that count becomes the new listing stock.
  Any non-matching items that somehow entered are returned to the player defensively.
  `shops.yml` is saved and holograms refreshed on every close.
- Disconnect safety: `PlayerQuitEvent` triggers the same stock-save logic as close.
- Full custom ItemStack metadata preserved (no rebuild from material, same clone path).
- Drag events onto the bottom row (slots 45–53) are cancelled.
- Owner GUI listing lore updated: "Click to restock" → "Click to manage stock".

**Buying behaviour: unchanged.**

---

## [1.0.24] - 2026-05-29

### Fixed — Custom plugin item data preservation (`ShopStorage.java`)

---

## [1.0.23] - 2026-05-29

### Fixed — Buyer GUI click handling (`ShopGUI.java`)

**Root causes identified and fixed:**

1. **SHIFT_LEFT misclassified as LEFT**: The previous code checked `ct == ClickType.LEFT`
   before shift-click detection. In some Bukkit versions / edge cases, a shift-left-click
   could be routed to the buy-1 branch instead of the stack-buy branch.
   **Fix**: `event.isShiftClick()` is now checked BEFORE `ClickType.LEFT`.

2. **RIGHT click not reliably caught**: `ct == ClickType.RIGHT` equality fails for
   any Bedrock-equivalent or mapped click type that Geyser/Paper might reclassify.
   **Fix**: Replaced with `event.isRightClick()` which uses Bukkit's own click-category
   method and correctly matches all right-click variants.

3. **SHIFT_RIGHT not consistently routed**: Was grouped with SHIFT_LEFT, meaning
   shift-right-click triggered the stack-buy flow. Now SHIFT_RIGHT opens the amount
   selector (same as plain right-click), separate from SHIFT_LEFT's stack-buy flow.

4. **Unusual click types not guarded**: DROP, CONTROL_DROP, MIDDLE, NUMBER_KEY, etc.
   could fall through to listing logic on unusual inputs.
   **Fix**: All non-actionable click types are discarded early before routing.

**Buyer controls after fix:**
- `LEFT` = buy 1
- `SHIFT_LEFT` = stack-buy flow (confirmation or immediate depending on auto-confirm toggle)
- `RIGHT` = open amount selector
- `SHIFT_RIGHT` = open amount selector (same as RIGHT)
- All other click types = cancelled, no action

---

### Fixed — Custom plugin item data preservation (`ShopStorage.java`)

**Root cause:** `YamlConfiguration.set(path, ItemStack)` serializes via Bukkit's YAML
codec which cannot round-trip Paper 1.21 item components, Adventure-API display names
stored as `Component`, PersistentDataContainer entries from some plugins, or custom NBT
that has no `ItemMeta` equivalent. Any item listed in a shop and saved to `shops.yml`
would lose this data on the next server restart.

**Fix:** Items are now serialized using `ItemStack.serializeAsBytes()` (Paper's full
Minecraft registry codec), Base64-encoded, and stored as `sell-item-data` in `shops.yml`.
On load, `ItemStack.deserializeBytes()` restores the item exactly. Both encode paths
(save and V1 migration write) now use this format via the new `serializeItem()` helper.

**Audit results by area:**

| Area | Finding | Action |
|---|---|---|
| `Listing` constructor | Uses `template.clone()` + normalize amount — correct ✓ | No change |
| Duplicate listing check | Uses `isSimilar()` on in-memory objects — correct ✓ | No change |
| **`ShopStorage.readListings()`** | Used `getItemStack("sell-item")` (YAML codec) — loses custom data | **Fixed** |
| **`ShopStorage.saveShop()`** | Used `set("sell-item", item)` (YAML codec) — loses custom data | **Fixed** |
| **`ShopStorage.migrateV1()`** | Used `set("sell-item", item)` (YAML codec) — loses custom data | **Fixed** |
| `executePurchase` | Calls `listing.getTemplate()` (fresh clone) — buyer gets full item ✓ | No change |
| Restock matching | Uses `isSimilar()` — correct ✓ | No change |
| GUI display builders | Clone modified for display only; stored template unaffected ✓ | No change |

**Backward compatibility:**
- On load: `sell-item-data` (binary) is preferred; `sell-item` (YAML) used as fallback.
- Existing `shops.yml` entries with `sell-item` continue loading correctly.
- On the next save triggered by any shop operation, data migrates to `sell-item-data`.
- If `serializeAsBytes()` fails for any item, the YAML fallback is used and a warning
  is logged — no listings are lost.

**Known limitation (display only, not a data loss issue):**
GUI display builders call `getItemMeta()` → modify lore → `setItemMeta()` on display
clones. Some exotic Paper 1.21 item components with no Bukkit meta equivalent may not
appear in the GUI view. The actual item stored in the `Listing` and given to buyers is
always a fresh `getTemplate()` clone from the binary-serialized data and is unaffected.

---

## [1.0.23] - 2026-05-29

### Fixed — Buyer GUI click handling (`ShopGUI.java`)

**Root cause audit — four bugs found in `BuyerSession` listing-area click routing:**

**Bug 1 — No guard for unusual click types (fixed)**
`DOUBLE_CLICK`, `UNKNOWN`, `SWAP_OFFHAND`, `CREATIVE`, `MIDDLE`, `DROP`,
`CONTROL_DROP`, and `NUMBER_KEY` fell through the entire if/else chain unhandled.
They were cancelled at the top of the case but then reached the listing logic,
potentially interfering with state. An explicit early-return guard is now inserted
before any listing logic runs.

**Bug 2 — `SHIFT_LEFT` and `SHIFT_RIGHT` grouped together (fixed)**
The previous `else if (ct == ClickType.SHIFT_LEFT || ct == ClickType.SHIFT_RIGHT)`
branch sent BOTH to the stack-buy flow. The spec requires:
- `SHIFT_LEFT` → stack-buy (confirm GUI or immediate, based on toggle)
- `SHIFT_RIGHT` → amount selector (same as plain RIGHT)
These are now handled separately inside an `event.isShiftClick()` branch.

**Bug 3 — `ClickType.LEFT` checked before shift (fixed)**
The if/else chain previously checked `ct == ClickType.LEFT` first. Any edge case
where `SHIFT_LEFT` is mis-classified as `LEFT` (e.g. some Geyser/Bedrock routing)
would incorrectly buy 1 item instead of opening the stack-buy flow.
`event.isShiftClick()` is now checked first, guaranteeing shift-clicks are routed
before the LEFT branch is evaluated.

**Bug 4 — `ct == ClickType.RIGHT` instead of `event.isRightClick()` (fixed)**
`event.isRightClick()` matches both `RIGHT` and `SHIFT_RIGHT`. Since `SHIFT_RIGHT`
is now handled in the shift branch and returns before reaching this point, the
effective behavior is the same as before — but the method is more robust and
explicitly documents intent.

**Resulting ClickType routing (listing slots 0-44):**
| ClickType | Action |
|---|---|
| `DOUBLE_CLICK`, `UNKNOWN`, `SWAP_OFFHAND`, `CREATIVE`, `MIDDLE`, `DROP`, `CONTROL_DROP`, `NUMBER_KEY` | Discarded (already cancelled) |
| `SHIFT_LEFT` | Stack-buy: confirm GUI (auto-confirm OFF) or immediate (auto-confirm ON) |
| `SHIFT_RIGHT` | Amount selector |
| `LEFT` | Buy 1 |
| `RIGHT` | Amount selector |
| Any other | Ignored |

---

## [1.0.22] - 2026-05-29

### Added — Owner chest removal, viewer close, confirmation GUI, auto-confirm toggle

**1. Owner chest removal by breaking (`ShopProtectListener.java`):**
- Owner breaks a linked shop chest → chest breaks normally, chest location removed from
  `PlayerShop`, shop index updated, holograms refreshed, `shops.yml` saved.
- Message (chests remain): "Shop chest removed."
- Message (last chest): "Shop chest removed. Your shop data has been preserved."
- All listings, stock, and `PlayerShop` data are preserved in every case.
- Owner can sneak+axe a new chest later to re-link — existing listings and stock reappear immediately.
- Non-owners and admins (non-owner) remain blocked (unchanged protection).

**2. Close active viewers on chest removal (`ShopGUI.java`):**
- Before the chest is removed, all players with an active GUI tied to that shop receive:
  "This shop was closed." and have their inventory closed.
- Deposit, pricing, and restock sessions automatically return held items through the
  existing `InventoryCloseEvent` path — no item loss.

**3. Owner GUI cleanup (`ShopGUI.java`):**
- "Remove Shop" barrier button removed entirely (chest-breaking is now the removal workflow).
- "Sell" button moved from slot 45 (left) to slot 49 (center) of the bottom row.

**4. Shift-click purchase confirmation GUI (`ShopGUI.java`):**
- Shift-clicking a listing now opens a 27-slot "Confirm Purchase" GUI instead of
  buying immediately.
- Shows: item preview with quantity set to the purchase amount, price per item, total cost.
- "Finish" (right, slot 26): executes purchase, returns to buyer GUI.
- "Cancel" (left, slot 18): returns to buyer GUI with no purchase.
- Quantity is re-validated by `safeQuantity` at execution time — safe even if state
  changed while the confirmation was open.
- Closing with Esc returns the player to their normal inventory (no purchase).

**5. Auto-confirm toggle in buyer GUI (`ShopGUI.java`):**
- A toggle button at slot 49 (center of buyer GUI bottom row):
  - Name: "Auto-confirm Stack Purchases"
  - OFF (default, gray concrete): shift-click opens confirmation GUI.
  - ON (lime concrete): shift-click immediately buys the max safe stack amount.
- Lore (both states): "When enabled, shift-click purchases happen immediately."
- State is per-player, in-memory only — resets on server restart. No persistence yet.
- Toggle is clicked with left-click; updates in-place without reopening the GUI.

---

## [1.0.21] - 2026-05-29

### Fixed — Bug fixes and GUI cleanup only

**A) Hologram restart fix (`HologramManager.java`, `PlayerShopPlugin.java`):**
- `HologramManager` now implements `Listener` and is registered as an event listener.
- `createOrUpdate` skips unloaded chunks (`!chunk.isLoaded()`) instead of attempting
  to spawn armor stands silently. Spawning in unloaded chunks previously produced
  no entities, leaving chests without holograms after restart.
- New `ChunkLoadEvent` handler: when any chunk loads, the manager checks whether a
  registered shop chest falls within it. If so, `createOrUpdate` is called for that
  shop — holograms now appear when players approach, not just on server start.
- `HologramManager` registered as event listener in `PlayerShopPlugin.onEnable`.

**B) Buyer right-click / amount selector fix (`ShopGUI.java`):**
- `onInventoryClick` changed from `ignoreCancelled = true` to `ignoreCancelled = false`.
  With `true`, any NORMAL-priority listener (protection plugins, etc.) cancelling the
  event would silently prevent our HIGHEST-priority handler from running, swallowing
  right-clicks in the buyer GUI.
- Added out-of-stock guard for right-click: clicking an out-of-stock listing now shows
  a "cannot purchase" message instead of opening the amount selector with all buttons
  disabled (which looked broken).

**C) Glass pane removal (`ShopGUI.java`):**
- Removed all `fill(inventory, makeFiller())` calls from every GUI renderer.
  Empty inventory slots are visually cleaner on Bedrock clients and are safe because
  all click handlers cancel item movement regardless.
- Removed dead `fill()` and `makeFiller()` methods.

**D) GUI title colors (`ShopGUI.java`):**
- Owner GUI: `GOLD` → `WHITE`
- Buyer GUI: `GREEN` → `WHITE`
- Pricing GUI: `GOLD` → `AQUA`
- Deposit, Restock, Amount Selector: already `AQUA` — unchanged.

**E) Out-of-stock owner notification (`ShopGUI.java`):**
- After a purchase decrements a listing's stock to exactly 0, the owner (if online)
  receives an additional red notification:
  "Your \<item\> listing is now out of stock!"
- A subtle sound (`Sound.UI_TOAST_OUT` at pitch 0.8) accompanies the notification.
- The normal sale notification still fires before the out-of-stock alert.
- If the owner is offline, the notification is not sent (no deferred storage).

---

## [1.0.20] - 2026-05-29

### Changed — Pricing model: price per item (breaking model correction)

**All pricing is now per item, not per stack.**

**Pricing GUI:**
- Price display now reads `"$X.XX per item"` instead of `"$X.XX per stack"`.
- All +/- buttons adjust the price of a single item.
- Default starting price remains $1.00 (now $1.00 per item, not per stack).

**Owner GUI — listing lore:**
- Shows `"$X.XX each"` instead of `"$X.XX per stack"`.

**Buyer GUI — listing lore:**
- Removed the separate "per stack" and "per item" lines.
- Now shows a single clean line: `"$X.XX each"`.
- All three click-action hints remain unchanged.

**Amount Selector:**
- Preview slot shows `"$X.XX each"` (single line, no per-stack line).
- Button totals (`Total: $X.XX`) calculated correctly as `pricePerItem × qty`.

**Purchase math (all paths):**
- Buying 1: cost = `pricePerItem`
- Buying N (shift-click / amount selector): cost = `pricePerItem × N`
- Balance check for "can afford" uses `pricePerItem` directly.

**Data model:**
- `Listing`: field renamed `pricePerStack` → `pricePerItem`;
  getter `getPricePerStack()` → `getPricePerItem()`;
  setter `setPricePerStack()` → `setPricePerItem()`.

**Storage:**
- New key: `price-per-item` (replaces the old `price` key in `shops.yml`).
- The old `price` key is no longer written. Existing entries are migrated on first load.

**Automatic migration on load (`ShopStorage`):**
- If a listing section contains `price-per-item`, it is loaded directly (new format).
- If a listing section contains only `price` (old format), the value is automatically
  converted: `pricePerItem = price / template.getMaxStackSize()`.
  Examples:
  - Dirt: `price: 64.0` → `price-per-item: 1.0` (64 / 64)
  - Cobblestone: `price: 32.0` → `price-per-item: 0.5` (32 / 64)
  - Diamond Sword: `price: 200.0` → `price-per-item: 200.0` (200 / 1)
- The converted value is written back to disk on the next `saveShop` call.
- V1 migration (`shops.*` → `player-shops.*`) also converts prices to per-item.
- No data is lost; no manual action required.

---

## [1.0.19] - 2026-05-29

### Changed — UI cleanup and double chest prevention

**A) Confirm/Cancel swapped in all GUIs:**
- Cancel/Back is now consistently on the **left** (slot 45).
- Finish/Confirm is now consistently on the **right** (slot 53).
- Applies to: New Listing deposit GUI, Pricing GUI, Restock GUI, and Amount Selector GUI.
- Owner GUI (Sell / Remove Shop) was not changed — it is not a confirm/cancel pair.
- Amount Selector: Back moved from slot 53 to slot 45; buy buttons shifted right by one
  (Buy 1→46, Buy 8→47, Buy 16→48, Buy 32→49, Buy Max Stack→50, Buy Max→51).

**B) Finish button:**
- All confirm/continue buttons replaced with LIME_CONCRETE named **"Finish"**.
- Lore: "Click when you're done."
- Replaces: green terracotta "Continue" (deposit GUI), "Confirm" (pricing GUI),
  "Confirm Restock" (restock GUI).
- Amount Selector buy buttons remain GREEN_TERRACOTTA (they are selection buttons, not confirmations).

**C) New Listing deposit GUI — help item:**
- A compass appears at the bottom-middle slot (slot 49) of the deposit GUI.
- Named **"How to Add Items"** (aqua, bold).
- Lore: "Place items or stacks of the same kind." / "Then click Finish to continue."
- Informational only — always cancelled, cannot be moved or taken.

**D) Double chest prevention:**
- **Placing a chest** adjacent to a registered PlayerShop chest is now blocked.
  Message: "You cannot turn a PlayerShop into a double chest."
  Handled by a new `BlockPlaceEvent` handler in `ShopProtectListener` — checks all 4
  horizontal neighbours before placement completes.
- **Creating or linking a shop chest** that is already part of a double chest is now rejected.
  Message: "PlayerShop chests must be single chests."
  Handled at the top of `handleCreate` in `ShopInteractListener`.

---

## [1.0.18] - 2026-05-28

### Added — Buyer click actions: shift-buy and amount selector (Phase 4, Slice 3)

**Buyer GUI — three click modes per listing:**
- **Left-click**: buy 1 item (unchanged behaviour from v1.0.16).
- **Shift-click** (left or right): buy up to one full stack of that item in a single action.
  The quantity is capped automatically by listing stock, buyer inventory space, and buyer balance.
  If the buyer can only safely obtain fewer than a full stack, the maximum safe amount is bought.
  No purchase is made if the safe quantity is 0.
- **Right-click**: open the "Select Amount" GUI for that listing.

**Lore updated**: buyer listing items now show all three click-action hints when in stock.

**Amount Selector GUI ("Select Amount", aqua title):**
- 54-slot GUI with listing preview at slot 22 (price, per-item cost, stock).
- Six purchase buttons in the bottom row:
  - **Buy 1** (slot 45), **Buy 8** (46), **Buy 16** (47), **Buy 32** (48)
  - **Buy \<maxStack\>** (49) — reflects the item's actual max stack (e.g. "Buy 16" for ender pearls)
  - **Buy Max** (50) — buys the largest safe quantity (≤ one stack)
  - **Back** (53, arrow) — returns to the buyer GUI without purchasing
- Button states:
  - Green (GREEN_TERRACOTTA): full amount available
  - Yellow (YELLOW_STAINED_GLASS_PANE): capped — less than requested is available; lore shows actual qty
  - Red (RED_STAINED_GLASS_PANE): unavailable — lore shows "Not available"
- Each non-disabled button shows "Total: $X.XX" in lore.
- Clicking a button re-validates at execution time, then executes and returns to buyer GUI.
- All top-inventory interactions (drag, non-left-click) are cancelled — no item movement possible.

**Safety (all purchase paths):**
- `safeQuantity` pre-checks stock, inventory space (including partial stacks), and balance
  before committing anything. All three limits are applied simultaneously.
- Withdraw happens before item is given; refund path fires if `addItem` overflows.
- Stock is decremented, owner is paid, and `shops.yml` is saved atomically per purchase.
- Owner online notification includes quantity and total price.
- All purchases are capped at one max stack — no overbuying possible.

**Refactored internals:**
- `inventoryCapacity(Player, ItemStack)`: returns total units that fit, counting empty slots
  and space in partial stacks. Replaces the binary `hasInventorySpace` check for multi-buy.
- `safeQuantity(Player, Listing, int)`: single helper used by all three buy paths.
- `executePurchase(Player, PlayerShop, Listing, int)`: unified purchase method that accepts
  an arbitrary safe quantity and returns boolean success. Replaces the old BuyerSession-bound
  single-item method.

**Not implemented:** buy-all-total-stock, confirmation screen, cart system,
owner editing/removing listings.

---

## [1.0.17] - 2026-05-27

### Added — Restock existing listings (Phase 4, Slice 2)

**Restock GUI ("Restock: <ItemName>", yellow title):**
- Owner clicking a listing slot in the owner GUI now opens a "Restock Listing" GUI
  instead of doing nothing.
- Slots 0-44 are a free deposit area (same layout as New Listing deposit GUI).
- Slot 22 shows a non-interactive preview of the listing's template item with lore:
  "Deposit <ItemName> to add stock" (gray) and current "Stock: N" (white).
- Slot 45 (green Confirm): collects all deposited items, validates each against the
  listing template via `isSimilar()`, adds matching amounts to stock, saves, refreshes
  holograms, and reopens the owner GUI.
- Slot 53 (red Cancel): returns all deposited items and reopens the owner GUI.

**Item validation:**
- Cursor placement into deposit area: rejected if item doesn't match listing template.
- NUMBER_KEY hotbar swap into deposit area: rejected if hotbar item doesn't match.
- SHIFT+click from bottom inventory: rejected if item doesn't match.
- Drag events touching deposit area: rejected if dragged item doesn't match.
- Non-matching items cannot enter the deposit area.

**Safety:**
- Preview slot (22) is always cancelled — can never be extracted.
- `handleRestockConfirm` clears each slot before counting stock — no double-credit risk.
- Non-matching items that somehow reach confirm (defensive path) are returned via
  `giveOrDrop` instead of being silently discarded.
- Confirm with zero items deposited: shows rejection message, keeps GUI open.
- DOUBLE_CLICK from bottom inventory is cancelled (prevents collecting from deposit area).
- Session transition safety: new session is registered before `openInventory()` call
  so `InventoryCloseEvent` for the old GUI exits early without double-cleanup.

**Owner GUI updates:**
- Listing lore now includes "Click to restock" (aqua) as a fourth lore line.
- Clicking any listing slot opens the restock GUI; empty slots do nothing.

---

## [1.0.16] - 2026-05-27

### Added — Public buyer GUI and buy-one flow (Phase 4, Slice 1)

**Buyer GUI ("<OwnerName>'s Shop", green title):**
- Non-owner right-clicking a linked shop chest now opens a 54-slot buyer GUI
  instead of the previous "not yet open" chat message.
- Slots 0-44 display the shop's listings (same grid as owner GUI).
- Each listing shows: price per stack (gold), price per item (yellow), stock count,
  and "Left-click to buy 1" (green) — or "Out of stock" (red) when depleted.
- Empty shop (no listings): Paper placeholder at slot 22 — "No items for sale."
- Bottom row is all filler — no action buttons in buyer GUI yet.
- Owner right-clicking their own chest still opens the owner GUI (unchanged).

**Purchase flow (left-click a listing):**
1. Re-checks stock at purchase time (guards against stale GUI).
2. Price per item = `pricePerStack / material max stack size`
   (e.g. dirt at $64/stack → $1.00 each; diamond sword at $200 → $200.00 each).
3. Rejects if: out of stock | insufficient balance | inventory full.
4. Withdraws from buyer → gives item → deducts stock → deposits to owner → saves.
5. If `addItem` fails despite the space check, money is refunded automatically.
6. Buyer GUI slot refreshes immediately with the updated stock count.
7. Buyer receives confirmation message; owner is notified if online.

**Safety:**
- Withdraw happens before item is given — no item-without-payment dupe risk.
- Refund path guards against any post-check inventory failure.
- Inventory space check is non-destructive (`getStorageContents` + `isSimilar`
  scan for partial stacks before committing anything).
- Economy uses UUIDs — owner receives payment even when offline.

**Not implemented:** right-click quantity selector, shift-click buy-all, owner
restock, remove listing.

---

## [1.0.15] - 2026-05-27

### Added — Shared listing creation with virtual stock (Phase 3, Slice 3)

**Listing creation on Confirm:**
- Pricing GUI Confirm now creates a real `Listing` inside the `PlayerShop` instead
  of returning items.
- Deposited items become initial virtual stock — they are consumed and NOT returned.
- Stock is global across the entire `PlayerShop`; physical chest inventories are
  not involved.
- After creation: shop is saved to `shops.yml`, holograms refresh across all linked
  chests, and the owner GUI reopens automatically showing the new listing.
- Success message: "Listing created! N item(s) added to stock."

**Duplicate listing guard:**
- If an identical item/meta is already listed, creation is rejected:
  - Deposited items are returned safely.
  - Message: "This item already has a listing. Click the existing listing to restock it."

**Owner GUI listing lore updated:**
- Each listing now shows "Stock: N" instead of "Stock system coming soon."
- Lore order: price per stack → stock count → short ID.

**`Listing` model updated:**
- New `stock` field (virtual, global). Constructor: `Listing(id, template, price, stock)`.
- Added `getStock()`, `setStock(int)`, `addStock(int)`.

**`shops.yml` format updated:**
- Each listing entry now includes `stock: N`.
- Old entries without `stock:` load with stock = 0 (backwards compatible).
- `migrateV1()` creates migrated listings with stock = 0.

**Not implemented:** buyer GUI, purchases, restocking, listing editor, remove listing,
remove stock.

---

## [1.0.14] - 2026-05-27

### Added — New Listing Pricing GUI (Phase 3, Slice 2)

**Deposit → Pricing transition:**
- Clicking Continue in the deposit GUI now validates before proceeding:
  - Empty deposit (no items): "Add at least one item before continuing." — GUI stays open.
  - Mixed item types: "All deposited items must be the same type." — GUI stays open.
  - Valid: deposit slots cleared, items held in PricingSession, pricing GUI opens.
- Session handoff is dupe-safe: deposit slots are set to null before
  `openPricingGUI` is called. The subsequent InventoryCloseEvent for the old GUI
  finds a mismatched inventory and does nothing.

**Pricing GUI ("Set Listing Price"):**
- Row 1 (slots 9-17): `[-$10][-$1][-$0.10][ITEM][+$0.10][+$1][+$10]` flanked by
  filler. Red panes for decrease, lime panes for increase.
- Row 2 (slot 22): Gold ingot showing current price (`$X.XX per stack`). Updates
  live on every button click.
- Item preview (slot 13): shows the deposited item (amount 1) with lore
  "Total deposited: N".
- Row 5: **Confirm** (slot 45, green terracotta) | filler×7 | **Cancel** (slot 53,
  barrier). Confirm position is consistent with Continue in the deposit GUI.
- Price stored as integer cents internally (no floating-point drift).
- Price clamped at $0.00 minimum; no upper limit.

**All exit paths return items:**
- Confirm: returns deposited items, closes, sends "Listing creation coming soon."
- Cancel: returns deposited items, closes.
- Escape / natural close: returns deposited items.
- Player quit: items dropped at logout location.
- `depositedItems.clear()` after return prevents double-return on any race.

**DOUBLE_CLICK guard extended:** DOUBLE_CLICK from the player's *bottom* inventory
is now also cancelled during a DepositSession — it would otherwise pull matching
items out of deposit slots.

**Not implemented:** listing creation, stock saving, buying, restocking, remove
button behaviour.

---

## [1.0.13] - 2026-05-27

### Added — New Listing Deposit GUI (Phase 3, Slice 1)

**Sell button now opens a deposit GUI:**
- Owner clicks Sell (slot 45) in the owner GUI → opens a 54-slot GUI titled
  "New Listing: Add Item".
- Slots 0–44 are a free deposit area: owner can place, remove, drag, and
  shift-click items in freely.
- Bottom row: **Continue** (slot 45, green terracotta) and **Cancel** (slot 53,
  barrier). Slots 46–52 are inert filler.

**Item safety:**
- **Cancel** or closing the GUI returns all deposited items to the owner's
  inventory. Overflow drops naturally at the player's feet.
- **Continue** also returns all items (pricing not yet implemented) and sends
  "Pricing coming soon."
- Each deposit slot is cleared before the item is returned to prevent any dupe
  risk if addItem fails partway.
- Player quit while deposit GUI is open: items returned / dropped at logout
  location.

**Not implemented:**
- Pricing, listing creation, stock saving, buying, remove button behaviour.

**Owner GUI Sell button lore updated:** "Coming soon." → "Create a new listing."

---

## [1.0.12] - 2026-05-27

### Changed — Owner GUI: listing display (Phase 2, Slice 1)

**Listing grid (slots 0–44):**
- Owner GUI now renders the player's shared listings across the top 5 rows.
- Each listing slot shows the item's material with lore:
  - Price per stack (formatted `$X.XX per stack`)
  - Shortened listing ID (first 8 chars, for debug)
  - "Stock system coming soon."
- Clicking any listing slot sends "Listing editor coming soon." — no editing yet.
- Maximum 45 listings displayed (slots 0–44). Pagination not yet implemented.

**Empty state:**
- If the player has no listings, a Paper item appears in slot 22 (grid center)
  with name "No listings yet." and lore "Click Sell to add items soon."

**Bottom row (unchanged):**
- Sell (slot 45, emerald) and Remove (slot 53, barrier) still send "Coming soon."

**Not implemented:**
- Stocking, virtual inventory, buying, pricing editor, remove button behaviour.

---

## [1.0.11] - 2026-05-27

### Changed — Access-control cleanup: owner vs non-owner separation

**Block break messages differentiated:**
- Owner or admin breaking their own shop chest: "Use the Remove button in your shop
  menu to remove this shop chest."
- Non-owner breaking someone else's shop chest: "You cannot break another player's
  shop."
- Previously all players received the same generic message regardless of ownership.

**Owner GUI access (unchanged, confirmed correct):**
- Owner or admin right-clicking a shop chest → owner GUI (Sell + Remove buttons).
- Non-owner right-clicking a shop chest → chat message only, no GUI opened.
  Owner GUI buttons (Sell, Remove) are never visible to non-owners.

**Non-owner right-click (unchanged):**
- "This shop is not yet open for purchases." — buyer GUI pending virtual-inventory
  redesign in a future slice.

---

## [1.0.10] - 2026-05-27

### Changed — v2 Architecture: shared PlayerShop data layer

**Core model replacement:**
- `Shop.java` (per-chest) deleted and replaced by `PlayerShop.java` (per player)
  and `Listing.java` (per item/price pair).
- A player now owns ONE `PlayerShop` that holds a list of chest locations and a
  list of listings. Physical chests are access points only — stock is no longer
  tied to a specific chest inventory.
- `ShopManager` rewritten: primary index is `ownerUuid → PlayerShop`; a secondary
  `chestIndex` maps any chest location key to the owning player's UUID.
  New lookups: `getPlayerShop(UUID)`, `getPlayerShopByChest(Location)`.

**Storage migration (shops.yml):**
- New format: `player-shops.<ownerUUID>.{owner-name, chests[], listings{}}`.
- On first startup with old data, the migration reader groups every old per-chest
  entry by `owner-uuid`, merges them into a single `PlayerShop`, and writes the
  new format. The old `shops.*` section is **preserved untouched** — no data is
  ever deleted during migration.
- If a player had multiple old shop entries (multiple chests), they all merge into
  one `PlayerShop` with multiple linked chests and one listing per old entry.

**Multi-chest creation:**
- Shift+axe on a plain chest when the player already has a `PlayerShop` now links
  that chest instead of being blocked by the shop limit. The chest limit config
  key (`max-shops-per-player`) now governs linked chests per player.

**Hologram:**
- `HologramManager` now keys by `ownerUuid`. `createOrUpdate(PlayerShop)` spawns
  one hologram group above every linked chest (all showing identical lines).

**GUI / interactions:**
- `ShopGUI` stripped to `OwnerSession` only. Buyer purchase flow removed pending
  virtual-inventory redesign in a future slice.
- Non-owner right-click shows: "This shop is not yet open for purchases."

**Unchanged:**
- Protection (block break, explosion, hopper, piston, fire) — logic identical.
- `ChestUtil`, `EconomyManager`, `plugin.yml`, `config.yml` — untouched.

---

## [1.0.9] - 2026-05-27

### Fixed — Block break cancelled for all players on shop chests

Breaking a shop chest is now fully cancelled for everyone — owner, admin, and
non-owner alike. All players receive: "Use the shop remove button to remove this
shop." The previous behaviour (owner/admin break deletes the shop) is removed
until the Remove button is wired up.

---

## [1.0.8] - 2026-05-27

### Changed — v2 Slice 1: axe creation + owner GUI scaffold

**Shop creation:**
- Trigger changed from shift+shovel to shift+axe (any axe tier).
- Shop is created immediately on shift+axe — no physical chest setup flow.
- After creation the owner management GUI opens automatically.

**Owner GUI:**
- Right-clicking your own shop chest now opens a 54-slot owner management GUI
  instead of the vanilla chest.
- Shift+axe on an existing shop also opens the owner GUI.
- Bottom row: **Sell** (slot 45, emerald) and **Remove Shop** (slot 53, barrier).
  Both send "Coming soon." — full functionality in later slices.

**Hologram:**
- Unconfigured shops now show `<OwnerName>'s Shop / Right-click to manage`
  instead of the generic "Shop / Place items to sell".

**Unchanged:**
- Buyer purchase flow (BuyerSession / executePurchase) is untouched.
- PriceSession and double-chest normalization are untouched.
- shops.yml format is unchanged.

---

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
