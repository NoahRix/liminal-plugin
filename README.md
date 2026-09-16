# LiminalGen

A Paper/Spigot plugin that generates **the Liminal** — an endless liminal-space
dimension for Minecraft servers — arranged as concentric "ripple rings" around
spawn, complete with transition corridors, flickering fluorescent lights,
abandoned rail networks, ghost carts, and permission-gated access.

## The Liminal

Levels are stacked **vertically** (each level sits a few blocks above the
previous one) and arranged as concentric **ripple rings** by distance from
spawn. Every chunk belongs to exactly one level:

| Level | Ring (blocks from spawn) | Floor Y | Theme |
|-------|--------------------------|---------|-------|
| **Level 0 — The Lobby** | 0 – 150 | -63 | Classic birch rooms, 4 stacked floors with corner stairwells, sea-lantern lighting |
| **Level 1 — Parking Garage** | 150 – 300 | -63 | Four concrete decks: dead redstone lamps, scattered torches, white stall stripes, centre ramps |
| **Level 2 — Pipe Dreams** | 300 – 450 | -63 | Abandoned power-plant basement: aging copper pipe runs, conduit stubs, coolant puddles and lava seeps |
| **Level 3 — The Rails** | 450+ | -63 | Abandoned rail network: crossing track grid, viaducts, stations, ghost minecarts |

### Transition corridors

Each ring boundary is bridged by a **transition corridor** (default 40 blocks
wide): a flat walkway whose material gradients blend the two levels — every
level shares the same floor height by default (`elevation-step: 0`; a level
may raise itself with a positive step, which makes its inbound corridor ramp).
Gradients are **automatic by default** — the generator interpolates the two
levels' palettes in colour space and picks the nearest real blocks at each
step, so any level pairing works with zero extra configuration. Corridors also
feature perimeter walls with hashed doorways, support pillars, ceiling lights,
and rails (for rail-enabled levels, running flat through the corridor).

### Level 3 — The Rails

- A grid of rail lines: X-lines at `Z ≡ 16 (mod 32)`, Z-lines at
  `X ≡ 16 (mod 32)`, crossing at viaducts with carved archways.
- Powered rails every 8 blocks with stable booster torches.
- **Ghost minecarts** roam empty stretches (spawned on chunk load; 25% carry
  storage).
- Stations every 96 blocks with cryptic signs, loot chests and torches.
- Optional cobwebs beside tracks and configurable broken-track chance.

### Atmosphere

- **Perpetual midnight**: daylight cycle is disabled and time is re-snapped to
  midnight every 5 seconds — the Liminal is always dark.
- **Flickering lights**: registered fluorescent lights flicker via scheduled
  tasks; breaking a light removes it from the flicker set.
- **Fluorescent buzz**: proximity-based sound (requires the bundled resource
  pack, event `liminal.fluorescent_buzz`).
- **Weather off**, PvP off, hunger on, monsters off. Levels **ship clean** —
  loot and hazards are opt-in per level (see below).

### Permissions

Access control is enforced inside the Liminal world only. All nodes default to
op; grant them to groups via LuckPerms (or any permission provider):

| Node | Controls |
|------|----------|
| `liminal.use` | Teleporting to the Liminal (default: everyone) |
| `liminal.build` | Break/place blocks, buckets, flint & steel, tilling, destroying vehicles/item frames/paintings/armor stands |
| `liminal.interact.container` | Opening containers (chests, barrels, furnaces, shulker boxes, anvils, utility tables, ...) |
| `liminal.interact.door` | Doors, trapdoors, fence gates, buttons, levers, bells, beds |
| `liminal.interact.entity` | Riding minecarts (incl. ghost carts) and boats, manipulating item frames, armor stands, animals |
| `liminal.interact` | Parent node — grants all three interact sub-nodes at once |
| `liminal.admin` | Admin commands |

Denials are shown as an action-bar message. Example LuckPerms setup:

```
/lp group default permission set liminal.interact.container true
/lp group default permission set liminal.interact.door true
/lp group builder permission set liminal.build true
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/liminal` | Teleport to the Liminal world | `liminal.use` |
| `/liminaladmin create` | Create the Liminal world | `liminal.admin` |
| `/liminaladmin reload` | Reload configuration | `liminal.admin` |
| `/liminaladmin setlevel <id>` | View level info | `liminal.admin` |
| `/liminaladmin info` | Show plugin info | `liminal.admin` |

## Requirements

- Minecraft 1.21+
- Paper/Spigot server
- Java 21+

## Building

```bash
cd BackroomsPlugin
./gradlew shadowJar
```

The compiled jar will be in `BackroomsPlugin/build/libs/LiminalPlugin-1.0.0.jar`.

## Installation

1. Place the compiled jar in your server's `plugins/` folder as
   `LiminalPlugin.jar` (only one copy — duplicate jars cause "ambiguous plugin
   name" errors).
2. Start/restart the server.
3. Create the world registered to the `LiminalGen` generator (e.g. via
   MultiWorld or `/liminaladmin create`).
4. Players can use `/liminal` to enter.

## Configuration

Global settings live in `plugins/LiminalGen/config.yml`:

- `liminal-world-name` — the world the plugin generates and protects
- `gameplay` — master switches and server rules (difficulty, PvP, hunger,
  weather, monsters); `loot-generation` and `hazards` gate the per-level
  features below
- `generation` — seed, grid cell size, `transition-width` (corridor width),
  stairwell chance
- `bluemap` — dedicated map, markers, overlay sets

### Per-level files

Each level's configuration lives in its own file under
`plugins/LiminalGen/levels/` — `level0.yml`, `level1.yml`, `level2.yml`,
`level3.yml` (the filename stem is the level ID). Missing files are recreated
from the jar's defaults on startup; a legacy inline `levels:` section in
`config.yml` is migrated into individual files automatically. Edits apply via
`/liminaladmin reload`.

Each level file covers:

- **Rings** — `min-radius`/`max-radius`, `elevation-step`, Y range
- **Palette** — `room` (walls/floor/ceiling), `lighting` (material, spacing)
- **Layout** — `layout.floors` (stacked floors), `layout.floor-offset`
  (sub-floor depth), `layout.doorway-height`, and optional
  `layout.stairwell` (`style`: `spiral` corner stairwell or `ramp` straight
  centre ramp; `chance`, `stairs`, `cap-light`) for multi-floor levels
- Multi-floor levels are sealed at their ring cutoffs automatically
- **Lighting style** — `lighting.style`: `ceiling` (light blocks in the
  ceiling slab) or `floor-torch` (torches standing on the floor); plus
  `flicker-chance` (0 disables flicker) and `dark-radius` (floor-torch only:
  skip torches this many blocks from rail lines)
- **Features** — a list of typed decoration blocks (see below)
- **Loot & hazards** — per-level opt-in (see below)
- **Transitions** — optional `transition:` gradient overrides

### Feature reference

The `features:` list drives everything decorative. Each entry needs a `type`;
handlers own their parameters and defaults, so new feature types can be added
without touching the engine:

| Type | Purpose | Key parameters |
|------|---------|----------------|
| `stray-chest` | Rare loose chest per chunk | `chance` (per chunk; 0.001 granularity) |
| `hanging-decor` | Strands hanging from the ceiling (chains) | `material`, `chance`, `max-length` |
| `floor-pool` | Liquid pools on room floors (water, lava) | `material`, `chance` |
| `wall-columns` | Vertical columns against room walls (pipes) | `material`, `chance`, `max-height` |
| `rail-network` | The abandoned rail grid: lines, powered rails, viaducts, stations, cobwebs, ghost carts | `grid`, `line-offset`, `power-every`, `station-every`, `viaduct-height`, `viaduct-half`, `cobweb-chance`, `broken-track-chance`, `ghost-cart-chance`, `station.*` |
| `pipes` | Horizontal copper pipe bundles along room walls | `chance`, `height-offset`, `rows`, `materials`, `segment-length`, `bracket`, `bracket-spacing` |
| `parking-lines` | Stall stripes painted into deck floors | `material`, `spacing` |

Per-block features (`hanging-decor`, `floor-pool`, `wall-columns`) roll in
file order for every room-interior block; the `rail-network` feature also
makes its level's inbound transition corridor climbable by rail and enables
ghost minecarts anywhere the level is loaded.

**Rail-network stations** are configurable too:

```yaml
- type: rail-network
  grid: 32
  line-offset: 16
  power-every: 8
  station-every: 96
  station:
    platform: POLISHED_BASALT
    platform-length: 6      # blocks along the line
    platform-depth: 3       # blocks away from the track
    sign: OAK_SIGN
    sign-count: 2
    chest: CHEST
    torch: REDSTONE_TORCH
    supplies:               # station chest contents
      - {material: RAIL, min: 4, max: 13, chance: 0.6}
    cart-supplies:          # ghost chest-cart contents
      - {material: COAL, min: 3, max: 8, chance: 0.5}
```

**Loot tables and hazards are per level and disabled out of the box** (levels
ship clean). To opt in, set `enabled: true` and add entries:

```yaml
loot:
  enabled: true
  chance: 0.005       # per floor block — chance a loot container spawns
  container: CHEST    # CHEST or BARREL
  entries:
    - material: BOOK
      min: 1
      max: 2
      chance: 0.5     # chance this entry is rolled into a container
    - material: CANDLE
      min: 1
      max: 3
      chance: 0.4

hazards:
  enabled: true
  chance: 0.003       # per floor block — chance a hazard spawns
  types: [WATER, FIRE, COBWEB]   # omit to allow all three
```

Hazard types: `WATER` (puddle), `FIRE` (burn patch), `COBWEB` (2-high web
column). Adding a brand-new level (e.g. offices, buildings, parking lots) is
a new `levels/<id>.yml` — existing feature handlers compose; new mechanics
are added as new feature handlers in the plugin.

## Integrations

- **BlueMap** — renders the Liminal as a dedicated 3D map with marker sets
  (special rooms, hazards, stairwells, loot)
- **MultiWorld** — manages the Liminal as a separate world
- **WorldEdit** — supported via softdepend
- **WorldGuard** — hook present (stub; protection is currently handled by the
  plugin's own permission system above)

## Project Structure

```
liminal-plugin/
├── BackroomsPlugin/     # Paper/Spigot plugin (LiminalGen)
└── sources/
    └── BackroomsMod/    # Third-party mod source (reference only, untouched)
```

## License

MIT
