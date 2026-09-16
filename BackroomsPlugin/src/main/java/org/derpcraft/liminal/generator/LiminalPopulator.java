package org.derpcraft.liminal.generator;

import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LiminalConfig;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.config.LootEntry;
import org.derpcraft.liminal.config.RailNetworkSpec;
import org.derpcraft.liminal.generator.levels.LiminalLevel;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * Block populator for the Liminal world.
 *
 * <p>Runs after the chunk noise generation phase and adds small-scale decorations
 * that benefit from knowing the surrounding blocks. Currently handles:</p>
 * <ul>
 *   <li><b>Loot containers</b> &ndash; chests or barrels placed on the floor and
 *       filled from the level's per-level loot table</li>
 *   <li><b>Hazards</b> &ndash; water puddles, fire patches, and cobwebs, filtered by
 *       the level's configured hazard types</li>
 * </ul>
 *
 * <p>Loot and hazards are gated twice: by the global {@code gameplay}
 * master switches and by each level's own {@code loot.enabled}/{@code
 * hazards.enabled} flags and chances (levels ship clean by default).</p>
 *
 * <p>Population is deterministic: the same seed and chunk coordinates always produce
 * the same decorations.</p>
 *
 * @see LiminalChunkGenerator
 */
public class LiminalPopulator extends BlockPopulator {

    /** Plugin configuration holding generation settings. */
    private final LiminalConfig config;

    /**
     * Constructs a new Liminal block populator.
     *
     * @param config the plugin configuration
     */
    public LiminalPopulator(LiminalConfig config) {
        this.config = config;
    }

    /**
     * Populates a chunk with loot containers and hazards.
     *
     * <p>Only the level whose ripple ring contains this chunk is populated: the
     * populator walks the chunk's ground-floor surface blocks and randomly places
     * loot or hazard blocks based on the configured probabilities.</p>
     *
     * @param worldInfo    information about the world
     * @param random       the chunk-specific random
     * @param chunkX       the chunk's X coordinate
     * @param chunkZ       the chunk's Z coordinate
     * @param limitedRegion the mutable region for this chunk (with surrounding context)
     */
    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random,
                         int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        // Transition corridor chunks carry no ambient loot or hazards.
        if (config.getTransitionForChunk(chunkX, chunkZ) != null) {
            return;
        }

        LevelConfig level = config.getLevelForChunk(chunkX, chunkZ);
        if (level == null || !level.isEnabled()) return;

        LiminalLevel instance = config.getLevelInstanceById(level.getId());
        if (instance == null) return;

        long seed = config.getGenerationSeed() != 0 ? config.getGenerationSeed() : worldInfo.getSeed();
        Random seededRandom = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Rail levels: fill in station sign text and station supply chests.
        FeatureSpec railSpec = level.getFeature("rail-network");
        if (railSpec != null) {
            decorateRails(limitedRegion, chunkX, chunkZ, instance, new RailNetworkSpec(railSpec), seededRandom);
            return;
        }

        // Parking garage levels: fill in deck number signs (P1..P4) and skip
        // ambient loot/hazards — garages hold nothing but concrete and dust.
        if (level.getFeature("parking-lines") != null) {
            decorateGarageSigns(limitedRegion, chunkX, chunkZ, instance);
            return;
        }

        int floorSurfaceY = instance.getFloorSurfaceY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX * 16 + x;
                int globalZ = chunkZ * 16 + z;

                if (!limitedRegion.isInRegion(globalX, floorSurfaceY + 1, globalZ)) continue;

                Material floorBelow = limitedRegion.getType(globalX, floorSurfaceY, globalZ);
                if (floorBelow != level.getFloorMaterial()) continue;

                if (config.isLootGeneration() && level.isLootEnabled()
                        && seededRandom.nextDouble() < level.getLootChance()) {
                    placeLoot(limitedRegion, globalX, floorSurfaceY + 1, globalZ, seededRandom, level);
                }

                if (config.isHazards() && level.isHazardsEnabled()
                        && seededRandom.nextDouble() < level.getHazardChance()) {
                    placeHazard(limitedRegion, globalX, floorSurfaceY + 1, globalZ, seededRandom, level);
                }
            }
        }
    }

    /** Cryptic messages the abandoned stations announce. */
    private static final String[] STATION_MESSAGES = {
            "DO NOT BOARD",
            "THE NEXT STOP IS YOURS",
            "WE ARE STILL WAITING",
            "SERVICE DISCONTINUED",
            "THE TUNNEL REMEMBERS",
            "MIND THE GAP",
            "NO EXIT",
            "LAST STOP. PROMISE.",
            "THE CARS COME BACK EMPTY",
            "TICKETS ARE MEMORIES",
    };

    /**
     * Decorates a parking garage chunk: every wall sign placed by the
     * {@code deck-signs} feature gets its deck number written ({@code P1},
     * {@code P2}, ...). The deck is derived from the sign's height, so upper
     * decks need no extra bookkeeping.
     */
    private void decorateGarageSigns(@NotNull LimitedRegion region, int chunkX, int chunkZ,
                                     @NotNull LiminalLevel level) {
        FeatureSpec spec = level.getConfig().getFeature("deck-signs");
        if (spec == null) return;

        Material signMat = spec.getMaterial("material", Material.OAK_WALL_SIGN);
        int groundY = level.getFloorSurfaceY();
        int floorHeight = level.getConfig().getCeilingHeight() + 1;
        int decks = Math.max(1, level.getConfig().getFloors());
        int topY = groundY + (decks - 1) * floorHeight + spec.getInt("height", 2);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX * 16 + x;
                int globalZ = chunkZ * 16 + z;
                for (int y = groundY; y <= topY; y++) {
                    if (!region.isInRegion(globalX, y, globalZ)) continue;
                    if (region.getType(globalX, y, globalZ) != signMat) continue;

                    int deck = (y - groundY) / floorHeight + 1;
                    if (deck < 1 || deck > decks) continue;
                    org.bukkit.block.BlockState state = region.getBlockState(globalX, y, globalZ);
                    if (state instanceof org.bukkit.block.Sign sign) {
                        sign.setLine(0, "P" + deck);
                        sign.update();
                    }
                }
            }
        }
    }

    /**
     * Decorates a rail-level chunk: writes cryptic text onto the station sign
     * posts and fills station chests with the previous crew's supplies. All
     * materials and supplies come from the level's rail-network spec, so any
     * rail-enabled level gets stations automatically.
     */
    private void decorateRails(@NotNull LimitedRegion region, int chunkX, int chunkZ,
                               @NotNull LiminalLevel level, @NotNull RailNetworkSpec rails,
                               @NotNull Random random) {
        int floorSurfaceY = level.getFloorSurfaceY();
        Material signMat = rails.signMaterial();
        Material chestMat = rails.chestMaterial();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX * 16 + x;
                int globalZ = chunkZ * 16 + z;
                if (!region.isInRegion(globalX, floorSurfaceY + 1, globalZ)) continue;

                Material type = region.getType(globalX, floorSurfaceY + 1, globalZ);
                if (type == signMat) {
                    org.bukkit.block.BlockState state = region.getBlockState(globalX, floorSurfaceY + 1, globalZ);
                    if (state instanceof org.bukkit.block.Sign sign) {
                        sign.setLine(1, STATION_MESSAGES[random.nextInt(STATION_MESSAGES.length)]);
                        sign.update();
                    }
                } else if (type == chestMat) {
                    org.bukkit.block.BlockState state = region.getBlockState(globalX, floorSurfaceY + 1, globalZ);
                    if (state instanceof org.bukkit.block.Chest chest) {
                        LootEntry.fillInventory(chest.getInventory(), rails.supplies(), random);
                        chest.update();
                    }
                }
            }
        }
    }

    /**
     * Places a loot container of the level's configured type at the given
     * position and fills it from the level's loot table. Each table entry
     * rolls independently: when its {@code chance} passes, a random amount
     * between the entry's {@code min} and {@code max} is added.
     *
     * @param region the mutable region
     * @param x      the block X coordinate
     * @param y      the block Y coordinate
     * @param z      the block Z coordinate
     * @param random the random source
     * @param level  the level configuration providing container type and loot table
     */
    private void placeLoot(@NotNull LimitedRegion region, int x, int y, int z,
                           @NotNull Random random, @NotNull LevelConfig level) {
        if (!region.isInRegion(x, y, z)) return;
        if (region.getType(x, y, z) != Material.AIR) return;

        Material container = Material.matchMaterial(level.getLootContainer());
        if (container != Material.CHEST && container != Material.BARREL) {
            container = Material.CHEST;
        }
        region.setType(x, y, z, container);

        org.bukkit.block.BlockState state = region.getBlockState(x, y, z);
        Inventory inventory = null;
        if (state instanceof Chest chest) {
            inventory = chest.getInventory();
        } else if (state instanceof Barrel barrel) {
            inventory = barrel.getInventory();
        }
        if (inventory == null) return;

        LootEntry.fillInventory(inventory, level.getLootEntries(), random);
        state.update();
    }

    /**
     * Places a hazard block at the given position. The hazard type is picked
     * uniformly from the level's configured hazard types:
     *
     * <ul>
     *   <li><b>WATER</b> &ndash; water (flooding hazard)</li>
     *   <li><b>FIRE</b> &ndash; fire (burn hazard, only if floor is present below)</li>
     *   <li><b>COBWEB</b> &ndash; cobweb (movement hazard, placed as a 2-high column)</li>
     * </ul>
     *
     * @param region the mutable region
     * @param x      the block X coordinate
     * @param y      the block Y coordinate
     * @param z      the block Z coordinate
     * @param random the random source
     * @param level  the level configuration providing allowed hazard types
     */
    private void placeHazard(@NotNull LimitedRegion region, int x, int y, int z,
                             @NotNull Random random, @NotNull LevelConfig level) {
        if (!region.isInRegion(x, y, z)) return;
        if (region.getType(x, y, z) != Material.AIR) return;

        List<Material> types = level.getHazardTypes();
        if (types.isEmpty()) return;
        Material type = types.get(random.nextInt(types.size()));

        switch (type) {
            case WATER:
                if (region.getType(x, y, z) == Material.AIR) {
                    region.setType(x, y, z, Material.WATER);
                }
                break;
            case FIRE:
                if (region.isInRegion(x, y - 1, z)
                        && region.getType(x, y - 1, z) == level.getFloorMaterial()) {
                    region.setType(x, y, z, Material.FIRE);
                }
                break;
            case COBWEB:
                region.setType(x, y, z, Material.COBWEB);
                if (region.isInRegion(x, y + 1, z)) {
                    region.setType(x, y + 1, z, Material.COBWEB);
                }
                break;
            default:
                break;
        }
    }
}
