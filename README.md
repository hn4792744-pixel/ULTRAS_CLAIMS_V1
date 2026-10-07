# Ultras_Claims_v1

Chunk-based claim protection for Paper — author **UC_Hussein**, version **v1**.
Premium, quiet design: a claim head, a clickable green “+” border, a compact dark menu system, a cabin that keeps the claim protected, a chunk map and per-player settings. English and Arabic.

> **Status:** the pure logic (`core/`) is unit-tested. The Bukkit/Paper part was written against the Paper API but could **not be compiled or run in the build environment** (no access to the Paper repository). Build it once on your side and run the checklist at the bottom of *Troubleshooting* before putting it on a live server.

## Installation
1. Requirements: Paper 26.2 (also written to work on 1.21.x), Java 25 (Java 21 works with `-PjavaRelease=21`), optional Vault + an economy plugin.
2. Build: `gradle build` → `build/libs/Ultras_Claims_v1.jar`.
   Different API: `gradle build -PpaperApi=1.21.8-R0.1-SNAPSHOT -PjavaRelease=21`.
3. Put the jar in `plugins/`, start the server, edit `plugins/Ultras_Claims_v1/config.yml`, then `/claim reload`.

## Commands
| Command | Description |
|---|---|
| `/claim` | Get your personal claim head (limit: `limits.max-heads`) |
| `/claim list` | Your claims and the ones you are a member of |
| `/claim tp <id>` | Teleport to a claim (owner, or member with *Teleport*) |
| `/claim info [id]` | Info about the claim you stand in, or by id |
| `/claim wand` | Wand: right click shows nearby borders |
| `/claim map` | The physical claim map item |
| `/claim_setting` | Your personal settings |
| `/claim admin add <player>` | Give a head even when `max-heads` is 0 |
| `/claim admin remove <id>` | Remove a claim (the head block breaks and drops) |
| `/claim admin reset <player>` | Reset the head allowance of a player |
| `/claim admin renew <id> [minutes]` | Add protection time (default `renew.manual-time`) |
| `/claim admin list` | List every claim |
| `/claim reload` | Reload config, messages, sounds, economy, cabin rules |

Tab completion never shows admin commands to players without `ultrasclaims.admin`.

## Permissions
| Permission | Default | |
|---|---|---|
| `ultrasclaims.use` | true | Use the system |
| `ultrasclaims.admin` | op | Admin sub-commands, reload |
| `ultrasclaims.admin.bypass` | op | Ignore protection, open and manage any claim |
| `ultrasclaims.admin.list` | op | `/claim list all` |
| `ultrasclaims.admin.tp` | op | Teleport to any claim |

## Config
Everything is in `config.yml` (commented). Highlights: `limits.*`, `expansion.*`, `economy.*`, `border.*`, `cabin.*`, `renew.*`, `expiry.*`, `protection.defaults`, `members.default-permissions`, `notifications.defaults`, `map.colors`, `sounds`, `prefix`, `colors`.
Layout of the management menu: `gui/layout.yml`. Log file: `logs/claims.log`. Database: `claims.db` (SQLite; tables `claims, claim_chunks, claim_members, member_permissions, claim_settings, cabin_storage, player_settings, claim_notifications`, plus `used_tokens` and `schema_version`).

## Languages
`messages_en.yml` and `messages_ar.yml` hold every text (nothing is hard-coded in Java). Default language: `language.default`; every player chooses theirs in `/claim_setting → Language`. English text is shown in small capitals (`meta.small-caps`). Chat style: `ᴜʟᴛʀᴀs│ ✓ …` success, `× …` error, `! …` warning, `• …` information. Missing keys fall back to English.

## Economy
`economy.type`: `vault` (default, `expansion-cost: 5000`, `currency: USD`), `command` (UC economy through console commands; set `economy.uc.enabled: true`, `check-command`, `take-command`, `balance-regex`), `items` (e.g. `DIAMOND` × 5). The cost is shown before expanding. `admin-bypass-cost: false` — admins pay unless you enable it. If the chosen economy is unavailable, expansion is refused instead of being free.

## Cabin
`cabin.enabled: false` removes the button, all timers and all cabin messages; claims never expire.
- Accepted items and their values: `cabin.items: MATERIAL: {minimum, time}` (`minimum` items = one step, `time` = protection per step, e.g. `3s`, `5m`, `2h`).
- Hand-in: click an accepted item in your own inventory (or drop the cursor stack on the menu). Items become a **server-side deposit**, never a real item inside the menu, so they cannot be duplicated or stolen. Click a deposit to take it back.
- After `cabin.process-delay-seconds` (10) the plugin verifies, takes only whole minimums, adds the time, logs it and refreshes menu and timers. With `cabin.require-confirmation: true` a **Confirm** button is used instead.
- Warnings start at 1 hour and repeat every 10 minutes (`ᴜʟᴛʀᴀs│ ! ᴄʟᴀɪᴍ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ ᴇxᴘɪʀᴇs ɪɴ 58m`), once per threshold.
- When time ends there is a **30 minute grace period** (`expiry.grace-minutes`): protection is off and only the old owner (or an admin) can renew. If it is not renewed, the claim is removed, the head block breaks and drops, and the area is claimable. `expiry.archive` keeps or deletes the database rows.
- Deposits of a removed claim are returned to the depositor (on the next join if offline).

## Expansion
The border shows one green “+” per direction. Left or right click expands by one chunk (cost check, permission check, no overlap, `limits.max-expansion-per-claim: 100`). Chat: `ᴜʟᴛʀᴀs│ ✓ ᴄʟᴀɪᴍ ᴇxᴘᴀɴᴅᴇᴅ` then `ᴜʟᴛʀᴀs│ • ᴘʀᴏɢʀᴇss: 5 / 100`. `expansion.require-confirmation: true` opens a confirmation menu with the cost. Expansions are serialised per claim and per target chunk, so two players cannot overlap or be charged twice.
Border: virtual Display/Interaction entities visible only to the viewer, `display-time: 30` (restarts on expansion), size/colour/glow/pulse/animation configurable, follows the player's height, cleaned up on quit/disable/reload, optional full beam.

## Protection
Priority: **admin bypass → owner → member permission → visitor setting → default deny.** Covered: block break/place, buckets, containers (chest/barrel/hopper/others), doors/trapdoors/gates, buttons, levers, redstone, pistons across borders, item pickup/drop, PvP, mob/animal damage, villagers, vehicles, fishing, fire, lava, water, explosions, TNT, creepers, enderman grief, crop trample, hoppers across borders, dispensers, tree growth across borders, ender pearl/chorus into claims. Heads are immune to explosions, pistons, fire and physics.
Heads: personalised, never stackable, duplicate-proof (each head carries a one-time token stored in the database), only the owner can place them, members cannot break them unless the owner grants *Break head*. Breaking a head (sneak + left click) removes the claim and drops a **new** head; re-placing it starts from scratch.

## GUI
Click the head (left or right): Border, Members, Settings, Cabin, Map, Notifications, Info, Close. Every menu has Back; lists have Previous/Next, “Page x/y”, Search (type in chat, `cancel` aborts) and Filter. Menus are `InventoryHolder`s; every click, drag, shift-click, number key, double click and creative pick is cancelled.
Icons are normal Minecraft items (no resource pack needed); change them in `gui/layout.yml` under `icons:`. Map cells use coloured glass panes (state tiles, not buttons).
Map menu: colours from `map.colors`, zoom ×1/×2/×4, previous/up/center/down/next, “you are here”, click an own/member chunk for its settings, click a free chunk next to your claim to open the confirm menu (Cancel returns to the map). Physical map: `/claim map`.

## Troubleshooting
- *Plugin does not start*: check the console for the first red line; the database must be writable (`claims.db`).
- *No economy*: install Vault plus an economy plugin, or choose `command`/`items`.
- *A claim vanished*: with `head.verify-on-chunk-load` a claim whose head block was removed by an external tool is cleaned up; see `logs/claims.log` (`STALE_CLAIM_REMOVED`).
- *Pre-launch checklist (not run here)*: place a head, expand with “+”, add a member and toggle a permission, deposit stone in the cabin, let a claim expire into grace and renew, break a head and re-place it, `/claim reload`, restart the server.

## Compatibility
Written for Paper 26.2 and the 1.21.x API (`api-version: '1.21'`), Java 25. Not compatible with Spigot/CraftBukkit (Paper API, Adventure, Display entities). Optional: Vault.

## Update notes
- Border "+": one per outer face of every claimed chunk (none on inner/claimed faces; blocked faces are not clickable).
- Map: clicking a free chunk next to your claim opens the Confirm menu; Cancel returns to the Map, no charge before Confirm.
- GUI: vanilla materials as icons (no resource pack needed), small-caps names with calm ✓ ! × markers.
- Chat prefix: `ULTRAS │` in normal capitals with a red gradient; message bodies stay small caps.
