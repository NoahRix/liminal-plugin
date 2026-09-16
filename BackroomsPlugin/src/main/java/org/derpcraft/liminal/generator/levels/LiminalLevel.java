package org.derpcraft.liminal.generator.levels;

import org.derpcraft.liminal.LiminalPlugin;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.config.StairwellSpec;
import org.derpcraft.liminal.effects.FlickerManager;
import org.derpcraft.liminal.generator.features.FeatureContext;
import org.derpcraft.liminal.generator.features.FloorPoolFeature;
import org.derpcraft.liminal.generator.features.HangingDecorFeature;
import org.derpcraft.liminal.generator.features.IntegralFeature;
import org.derpcraft.liminal.generator.features.ParkingLinesFeature;
import org.derpcraft.liminal.generator.features.PerBlockFeature;
import org.derpcraft.liminal.generator.features.PipesFeature;
import org.derpcraft.liminal.generator.features.RailNetworkFeature;
import org.derpcraft.liminal.generator.features.SupportPillarsFeature;
import org.derpcraft.liminal.generator.features.ElevatorFeature;
import org.derpcraft.liminal.generator.features.DeckSignsFeature;
import org.derpcraft.liminal.generator.features.ElevatorHubFeature;
import org.derpcraft.liminal.generator.features.DeckTrimFeature;
import org.derpcraft.liminal.generator.features.FloorDrainFeature;
import org.derpcraft.liminal.generator.features.WallColumnFeature;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The spec-driven level engine: generates any Liminal level entirely from its
 * {@link LevelConfig} (parsed from the level's YML file).
 *
 * <h2>Generation pipeline (per floor)</h2>
 * <ol>
 *   <li>{@link #generateFloorAndCeiling} &ndash; lay the floor slab and ceiling slab</li>
 *   <li>Carve the stairwell opening rising from the floor below (multi-floor levels)</li>
 *   <li>{@link #calculateRoomLayout} &ndash; deterministic room grid from the seed</li>
 *   <li>{@link #generateWallsAndDoorways} &ndash; walls with doorway cut-outs</li>
 *   <li>{@link #generateLighting} &ndash; ceiling lights or floor torches, with optional flicker</li>
 *   <li>{@link #generateLootChest} &ndash; the {@code stray-chest} feature</li>
 *   <li>{@link #generateFeatures} &ndash; integral and per-block feature handlers</li>
 *   <li>{@link #generateStairwell} &ndash; corner spiral stairwell (multi-floor levels)</li>
 * </ol>
 *
 * <h2>Features</h2>
 * <p>The level's {@code features:} list drives decoration. Per-block features
 * (hanging decor, floor pools, wall columns) share one deterministic random and
 * roll in file order for every room-interior block; integral features (rail
 * networks) run their own pass with their own random. The {@code stray-chest}
 * feature is consumed by the pipeline itself. Stairwells are configured under
 * {@code layout.stairwell} because they interleave with the floor loop.</p>
 */
public class LiminalLevel {

    /** Per-block feature handlers, keyed by their {@code type} value. */
    private static final Map<String, PerBlockFeature> PER_BLOCK_FEATURES = Map.of(
            "hanging-decor", new HangingDecorFeature(),
            "floor-pool", new FloorPoolFeature(),
            "wall-columns", new WallColumnFeature(),
            "floor-drains", new FloorDrainFeature());

    /** Integral feature handlers, keyed by their {@code type} value. */
    private static final Map<String, IntegralFeature> INTEGRAL_FEATURES = Map.of(
            "rail-network", new RailNetworkFeature(),
            "pipes", new PipesFeature(),
            "parking-lines", new ParkingLinesFeature(),
            "support-pillars", new SupportPillarsFeature(),
            "elevators", new ElevatorFeature(),
            "deck-signs", new DeckSignsFeature(),
            "elevator-hub", new ElevatorHubFeature(),
            "deck-trim", new DeckTrimFeature());

    /** Configuration data for this level (Y range, materials, room sizes, etc.). */
    private final LevelConfig config;

    /** World seed for deterministic generation. */
    private final long seed;

    /**
     * Pending ramp anchor (world coords) from the floor below; the pillar
     * feature skips cells within the ramp's emergence zone to avoid collisions.
     * Set by generateStairwell when a ramp is placed, consumed by the next
     * deck's floor loop.
     */
    private int[] pendingRampAnchor = null;

    /**
     * Constructs a new level engine backed by the given configuration.
     *
     * @param config the {@link LevelConfig} holding material choices, Y bounds, room dimensions
     * @param seed   the world generation seed used for deterministic randomness
     */
    public LiminalLevel(LevelConfig config, long seed) {
        this.config = config;
        this.seed = seed;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link LevelConfig} that describes this level's parameters.
     *
     * @return the level configuration, never {@code null}
     */
    public LevelConfig getConfig() {
        return config;
    }

    /**
     * Returns the world anchor of the ramp placed on the floor below (if any),
     * so features on the current floor (pillars, etc.) can skip the ramp's
     * emergence zone. {@code null} when no ramp was placed.
     */
    public int[] getPendingRampAnchor() {
        return pendingRampAnchor;
    }

    /**
     * Generates all blocks for this level within a single chunk.
     *
     * <p>This is the main entry point called by {@code LiminalChunkGenerator}. It
     * orchestrates the full generation pipeline for every stacked floor.</p>
     *
     * @param chunkData    the mutable chunk data to write blocks into
     * @param chunkStartX  the world X coordinate of the chunk's western edge
     * @param chunkStartZ  the world Z coordinate of the chunk's northern edge
     * @param chunkEndX    the world X coordinate just past the chunk's eastern edge ({@code chunkStartX + 16})
     * @param chunkEndZ    the world Z coordinate just past the chunk's southern edge ({@code chunkStartZ + 16})
     * @param worldMinY    the minimum Y coordinate of the world
     * @param worldMaxY    the maximum Y coordinate of the world
     */
    public void generate(ChunkGenerator.ChunkData chunkData,
                         int chunkStartX, int chunkStartZ,
                         int chunkEndX, int chunkEndZ,
                         int worldMinY, int worldMaxY) {

        // Levels step upward as the rings expand outward; the transition
        // corridors between rings ramp smoothly between the two elevations.
        int elevation = config.getElevationStep();
        int effectiveMinY = Math.max(config.getMinY() + elevation, worldMinY);
        int effectiveMaxY = Math.min(config.getMaxY() + elevation, worldMaxY);

        int floorHeight = config.getCeilingHeight() + 1;

        // pendingStairwellOpening carries the local chunk coordinates of a
        // stairwell built on the floor below; the opening through the CURRENT
        // floor's slab is carved right after this floor's slab is laid
        // (otherwise the slab would seal the shaft shut).
        int[] pendingStairwellOpening = null;
        pendingRampAnchor = null;

        for (int floorIndex = 0; floorIndex < config.getFloors(); floorIndex++) {
            int floorBaseY = effectiveMinY + (floorIndex * floorHeight);
            int floorY = floorBaseY + getFloorOffset();
            int ceilingY = floorY + config.getCeilingHeight();

            if (ceilingY > effectiveMaxY) {
                break; // No more room for this floor
            }
            if (floorY >= ceilingY) {
                continue; // Skip invalid floors
            }

            generateFloorAndCeiling(chunkData, floorY, ceilingY);

            if (pendingStairwellOpening != null) {
                carveStairwellOpening(chunkData, pendingStairwellOpening[0], pendingStairwellOpening[1], floorY,
                        pendingStairwellOpening[2] == 1, chunkStartX, chunkStartZ);
                pendingStairwellOpening = null;
            }

            boolean parkingGarage = "parking-garage".equalsIgnoreCase(config.getLayoutMode());
            RoomLayout layout = parkingGarage
                    ? calculateGarageLayout(chunkStartX, chunkStartZ)
                    : calculateRoomLayout(chunkStartX, chunkStartZ, chunkEndX, chunkEndZ, floorIndex);

            if (parkingGarage) {
                generateParkingGarageDeck(chunkData, floorY, ceilingY, chunkStartX, chunkStartZ, floorIndex);
            } else {
                generateWallsAndDoorways(chunkData, layout, floorY, ceilingY, chunkStartX, chunkStartZ);
                generateLighting(chunkData, layout, ceilingY, chunkStartX, chunkStartZ);
            }

            generateLootChest(chunkData, layout, floorY, chunkStartX, chunkStartZ);

            generateFeatures(chunkData, layout, floorY, ceilingY, chunkStartX, chunkStartZ);

            // Generate stairwell if this isn't the top floor and the room qualifies
            StairwellSpec stairwell = config.getStairwell();
            if (!parkingGarage && stairwell != null && floorIndex < config.getFloors() - 1 && layout.overlapsRoom()) {
                pendingStairwellOpening = generateStairwell(
                        chunkData, layout, floorY, ceilingY, chunkStartX, chunkStartZ, floorIndex, stairwell);
                if (pendingStairwellOpening != null && pendingStairwellOpening[2] == 1) {
                    pendingRampAnchor = pendingStairwellOpening.clone();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pipeline steps
    // -------------------------------------------------------------------------

    /**
     * Returns the vertical offset from the level's floor base to the walking
     * surface (the sub-floor depth).
     *
     * @return the configured floor offset
     */
    private int getFloorOffset() {
        return config.getFloorOffset();
    }

    /**
     * Returns the world Y coordinate of this level's ground-floor surface block
     * (the block players walk on for the bottom-most floor), including the
     * level's elevation step.
     *
     * @return {@code config.getMinY() + config.getElevationStep() + getFloorOffset()}
     */
    public int getFloorSurfaceY() {
        return config.getMinY() + config.getElevationStep() + getFloorOffset();
    }

    /**
     * Returns the height of doorway openings in blocks (measured from the floor surface).
     *
     * @return the configured doorway height
     */
    private int getDoorwayHeight() {
        return config.getDoorwayHeight();
    }

    /**
     * Lays the floor slab and ceiling slab across the entire chunk column for this level.
     *
     * @param chunkData the mutable chunk data
     * @param floorY    the Y coordinate of the floor surface
     * @param ceilingY  the Y coordinate of the ceiling surface
     */
    private void generateFloorAndCeiling(ChunkGenerator.ChunkData chunkData, int floorY, int ceilingY) {
        Material floorMat = config.getFloorMaterial();
        Material ceilingMat = config.getCeilingMaterial();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                chunkData.setBlock(localX, floorY, localZ, floorMat);
                chunkData.setBlock(localX, ceilingY, localZ, ceilingMat);
            }
        }
    }

    /**
     * Calculates the room layout for this chunk using a deterministic grid-based approach.
     *
     * <p>The world is divided into a grid whose cell size equals the level's maximum room
     * dimensions. Each cell is seeded to produce a room of random size within the configured
     * min/max range. The method then checks whether the current chunk overlaps the generated
     * room and computes doorway positions if it does.</p>
     *
     * @param chunkStartX world X of the chunk's western edge
     * @param chunkStartZ world Z of the chunk's northern edge
     * @param chunkEndX   world X just past the chunk's eastern edge
     * @param chunkEndZ   world Z just past the chunk's southern edge
     * @return a {@link RoomLayout} describing the room and its relationship to this chunk
     */
    private RoomLayout calculateRoomLayout(int chunkStartX, int chunkStartZ,
                                             int chunkEndX, int chunkEndZ) {
        return calculateRoomLayout(chunkStartX, chunkStartZ, chunkEndX, chunkEndZ, 0);
    }

    /** Returns a chunk-local layout for shared feature handlers on open decks. */
    private RoomLayout calculateGarageLayout(int chunkStartX, int chunkStartZ) {
        return new RoomLayout(chunkStartX - 1, chunkStartZ - 1,
                chunkStartX + 17, chunkStartZ + 17, 18, 18, 9, true);
    }

    /** Builds the repeated garage bays, columns, beams, stalls, and lights. */
    private void generateParkingGarageDeck(ChunkGenerator.ChunkData chunkData,
                                            int floorY, int ceilingY,
                                            int chunkStartX, int chunkStartZ,
                                            int floorIndex) {
        FeatureSpec spec = config.getFeature("parking-garage");
        if (spec == null) return;

        int boundaryPeriod = 16;
        int beamDepth = Math.max(1, Math.min(2, spec.getInt("beam-depth", 1)));
        int bandBottom = Math.max(1, spec.getInt("column-band-bottom", 2));
        int bandHeight = Math.max(1, spec.getInt("column-band-height", 2));

        Material columnMat = spec.getMaterial("column-material", Material.LIGHT_GRAY_CONCRETE);
        Material baseMat = spec.getMaterial("column-base-material", Material.POLISHED_DEEPSLATE);
        Material bandMat = spec.getMaterial("column-band-material", Material.RED_CONCRETE);
        Material beamMat = spec.getMaterial("beam-material", Material.LIGHT_GRAY_CONCRETE);
        Material lineMat = spec.getMaterial("stall-line-material", Material.WHITE_CONCRETE);

        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkStartX + localX;
            int xCell = Math.floorMod(worldX, boundaryPeriod);
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunkStartZ + localZ;
                int zCell = Math.floorMod(worldZ, boundaryPeriod);
                int lightRotation = getGarageLightRotation(worldX, worldZ, boundaryPeriod);
                // 1×1 pillars at exact corners: (0,0), (15,0), (0,15), (15,15)
                boolean column = (xCell == 0 || xCell == 15) && (zCell == 0 || zCell == 15);
                // Beams along perimeter edges (excluding pillar positions)
                boolean beamLine = (xCell == 0 || xCell == 15 || zCell == 0 || zCell == 15) && !column;

                if (column) {
                    for (int y = floorY + 1; y < ceilingY; y++) {
                        Material material = y < floorY + bandBottom ? baseMat
                                : y < floorY + bandBottom + bandHeight ? bandMat : columnMat;
                        chunkData.setBlock(localX, y, localZ, material);
                    }
                } else if (beamLine) {
                    for (int y = ceilingY - beamDepth; y < ceilingY; y++) {
                        chunkData.setBlock(localX, y, localZ, beamMat);
                    }
                }

                if (!column) {
                    placeParkingMarking(chunkData, localX, localZ, worldX, worldZ,
                            floorY, boundaryPeriod, lineMat);
                }

                if (isGarageLightAnchor(worldX, worldZ, boundaryPeriod, lightRotation)) {
                    chunkData.setBlock(localX, ceilingY, localZ, Material.SEA_LANTERN);
                }
            }
        }
    }

    /**
     * Selects one of four light rotations from the bay's angle around world
     * origin. Boundaries fall at 45, 135, 225, and 315 degrees.
     */
    private int getGarageLightRotation(int worldX, int worldZ, int boundaryPeriod) {
        int bayX = Math.floorDiv(worldX, boundaryPeriod);
        int bayZ = Math.floorDiv(worldZ, boundaryPeriod);
        double angle = Math.atan2(bayZ + 0.5, bayX + 0.5);
        return Math.floorMod((int) Math.floor((angle + Math.PI / 4.0) / (Math.PI / 2.0)), 4);
    }

    /** Returns true for four fixtures inside the 14-block clear bay interior. */
    private boolean isGarageLightAnchor(int worldX, int worldZ, int boundaryPeriod, int rotation) {
        int tileX = Math.floorMod(worldX, boundaryPeriod);
        int tileZ = Math.floorMod(worldZ, boundaryPeriod);
        int localX = tileX;
        int localZ = tileZ;
        if (rotation == 1) {
            localX = tileZ;
            localZ = boundaryPeriod - 1 - tileX;
        } else if (rotation == 2) {
            localX = boundaryPeriod - 1 - tileX;
            localZ = boundaryPeriod - 1 - tileZ;
        } else if (rotation == 3) {
            localX = boundaryPeriod - 1 - tileZ;
            localZ = tileX;
        }
        // 4 lights offset 2 blocks diagonally from corners: (2,2), (13,2), (2,13), (13,13)
        return (localX == 2 || localX == 13) && (localZ == 2 || localZ == 13);
    }

    /** Places the parking spot markings based on quadrant rotation. */
    private void placeParkingMarking(ChunkGenerator.ChunkData chunkData,
                                     int localX, int localZ,
                                     int worldX, int worldZ,
                                     int floorY, int boundaryPeriod,
                                     Material lineMat) {
        // Determine which bay this cell belongs to
        int bayX = Math.floorDiv(worldX, boundaryPeriod);
        int bayZ = Math.floorDiv(worldZ, boundaryPeriod);
        
        // Determine quadrant rotation (0=North, 1=East, 2=South, 3=West)
        int rotation;
        if (bayX >= 0 && bayZ < 0) {
            rotation = 0; // Top-right (North)
        } else if (bayX >= 0 && bayZ >= 0) {
            rotation = 1; // Bottom-right (East)
        } else if (bayX < 0 && bayZ >= 0) {
            rotation = 2; // Bottom-left (South)
        } else {
            rotation = 3; // Top-left (West)
        }
        
        // Get position within the bay (0-15)
        int bayLocalX = Math.floorMod(worldX, boundaryPeriod);
        int bayLocalZ = Math.floorMod(worldZ, boundaryPeriod);
        
        // Rotate coordinates based on quadrant
        int rotatedX = bayLocalX;
        int rotatedZ = bayLocalZ;
        
        switch (rotation) {
            case 1: // East (90° clockwise)
                rotatedX = bayLocalZ;
                rotatedZ = boundaryPeriod - 1 - bayLocalX;
                break;
            case 2: // South (180°)
                rotatedX = boundaryPeriod - 1 - bayLocalX;
                rotatedZ = boundaryPeriod - 1 - bayLocalZ;
                break;
            case 3: // West (270° clockwise)
                rotatedX = boundaryPeriod - 1 - bayLocalZ;
                rotatedZ = bayLocalX;
                break;
            default: // North (0°)
                break;
        }
        
        // Check if this position should have a white marking
        boolean isMarking = false;
        
        // Z=1 and Z=14: Border rows (white)
        if (rotatedZ == 1 || rotatedZ == 14) {
            isMarking = true;
        }
        // Z=2-6 and Z=9-13: Stall rows
        else if ((rotatedZ >= 2 && rotatedZ <= 6) || (rotatedZ >= 9 && rotatedZ <= 13)) {
            // X=1 and X=14: Border columns (white)
            if (rotatedX == 1 || rotatedX == 14) {
                isMarking = true;
            }
            // X=5 and X=10: Stall dividers (white)
            else if (rotatedX == 5 || rotatedX == 10) {
                isMarking = true;
            }
            // X=2-4, 6-9, 11-13: Stall interiors (gray, no marking)
        }
        // Z=7-8: Aisle (gray, no marking)
        
        if (isMarking) {
            chunkData.setBlock(localX, floorY, localZ, lineMat);
        }
    }

    /**
     * Calculates the room layout for this chunk on a specific floor of a multi-floor level.
     *
     * <p>The floor index is mixed into the room seed so stacked floors of the same level
     * get independent room layouts (otherwise every floor would have perfectly aligned
     * walls). The result stays deterministic: chunks within the same grid cell on the same
     * floor always compute the identical room.</p>
     *
     * @param chunkStartX world X of the chunk's western edge
     * @param chunkStartZ world Z of the chunk's northern edge
     * @param chunkEndX   world X just past the chunk's eastern edge
     * @param chunkEndZ   world Z just past the chunk's southern edge
     * @param floorIndex  the floor being generated (0 = bottom); single-floor levels pass 0
     * @return a {@link RoomLayout} describing the room and its relationship to this chunk
     */
    private RoomLayout calculateRoomLayout(int chunkStartX, int chunkStartZ,
                                             int chunkEndX, int chunkEndZ, int floorIndex) {
        int roomMaxWidth = config.getRoomMaxWidth();
        int roomMaxLength = config.getRoomMaxLength();

        int gridX = Math.floorDiv(chunkStartX, roomMaxWidth);
        int gridZ = Math.floorDiv(chunkStartZ, roomMaxLength);

        long roomSeed = seed ^ ((long) gridX * 0x4f4f4f4fL) ^ ((long) gridZ * 0x2f2f2f2fL)
                      ^ config.getIdHashCode() ^ (floorIndex * 0x5f356495L);
        Random roomRand = new Random(roomSeed);

        int roomWidth = config.getRoomMinWidth() + roomRand.nextInt(roomMaxWidth - config.getRoomMinWidth() + 1);
        int roomLength = config.getRoomMinLength() + roomRand.nextInt(roomMaxLength - config.getRoomMinLength() + 1);

        int roomStartX = gridX * roomMaxWidth;
        int roomStartZ = gridZ * roomMaxLength;
        int roomEndX = roomStartX + roomWidth;
        int roomEndZ = roomStartZ + roomLength;

        boolean overlaps = chunkEndX > roomStartX && chunkStartX < roomEndX
                        && chunkEndZ > roomStartZ && chunkStartZ < roomEndZ;

        int doorwayPos = Math.max(roomWidth, roomLength) / 2;

        return new RoomLayout(
                roomStartX, roomStartZ, roomEndX, roomEndZ,
                roomWidth, roomLength, doorwayPos, overlaps
        );
    }

    /**
     * Generates the walls around a room and carves doorway openings.
     *
     * <p>Walls are placed on the four edges of the room rectangle. Each wall has a single
     * doorway opening centred on that edge. The doorway is player-height (from
     * {@code layout.doorway-height}); the upper portion of the doorway is filled with the
     * wall material to maintain the enclosed feeling.</p>
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param floorY       the Y coordinate of the floor surface
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void generateWallsAndDoorways(ChunkGenerator.ChunkData chunkData,
                                            RoomLayout layout,
                                            int floorY, int ceilingY,
                                            int chunkStartX, int chunkStartZ) {
        if (!layout.overlapsRoom()) {
            return;
        }

        Material wallMat = config.getWallMaterial();
        int doorwayHeight = getDoorwayHeight();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int globalX = chunkStartX + localX;
                int globalZ = chunkStartZ + localZ;

                boolean isWall = false;
                boolean isDoorway = false;

                if (globalX == layout.roomStartX() || globalX == layout.roomEndX() - 1 ||
                    globalZ == layout.roomStartZ() || globalZ == layout.roomEndZ() - 1) {
                    isWall = true;

                    // 3-block-wide doorways centered on the doorway position
                    int doorwayOffset = layout.doorwayPos();
                    if (globalX == layout.roomStartX() && Math.abs(globalZ - (layout.roomStartZ() + doorwayOffset)) <= 1) {
                        isDoorway = true;
                    } else if (globalX == layout.roomEndX() - 1 && Math.abs(globalZ - (layout.roomStartZ() + doorwayOffset)) <= 1) {
                        isDoorway = true;
                    } else if (globalZ == layout.roomStartZ() && Math.abs(globalX - (layout.roomStartX() + doorwayOffset)) <= 1) {
                        isDoorway = true;
                    } else if (globalZ == layout.roomEndZ() - 1 && Math.abs(globalX - (layout.roomStartX() + doorwayOffset)) <= 1) {
                        isDoorway = true;
                    }
                }

                for (int blockY = floorY + 1; blockY < ceilingY; blockY++) {
                    if (isDoorway) {
                        if (blockY <= floorY + doorwayHeight) {
                            chunkData.setBlock(localX, blockY, localZ, Material.AIR);
                        } else {
                            chunkData.setBlock(localX, blockY, localZ, wallMat);
                        }
                    } else if (isWall) {
                        chunkData.setBlock(localX, blockY, localZ, wallMat);
                    } else {
                        chunkData.setBlock(localX, blockY, localZ, Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Places the level's lights, dispatching on the configured lighting style.
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void generateLighting(ChunkGenerator.ChunkData chunkData,
                                    RoomLayout layout,
                                    int ceilingY,
                                    int chunkStartX, int chunkStartZ) {
        if ("floor-torch".equals(config.getLightStyle())) {
            generateFloorTorchLighting(chunkData, layout, ceilingY, chunkStartX, chunkStartZ);
        } else {
            generateCeilingLighting(chunkData, layout, ceilingY, chunkStartX, chunkStartZ);
        }
    }

    /**
     * Ceiling lighting style: light blocks embedded in the ceiling slab at the
     * configured spacing, plus optional flicker registration.
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void generateCeilingLighting(ChunkGenerator.ChunkData chunkData,
                                         RoomLayout layout,
                                         int ceilingY,
                                         int chunkStartX, int chunkStartZ) {
        if (!layout.overlapsRoom()) {
            return;
        }

        Material lightMat = config.getLightMaterial();
        boolean isParkingGarage = "parking-garage".equalsIgnoreCase(config.getLayoutMode());

        if (isParkingGarage) {
            // Bay-aligned lighting: 4 lights per 16×16 bay
            // 14×14 interior (1-14): lights at (2,2), (13,2), (2,13), (13,13)
            int baySpacing = 16;
            int[][] bayLightOffsets = {
                {2, 2}, {13, 2}, {2, 13}, {13, 13}
            };

            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int globalX = chunkStartX + localX;
                    int globalZ = chunkStartZ + localZ;

                    // Calculate position within current bay
                    int bayX = Math.floorDiv(globalX, baySpacing) * baySpacing;
                    int bayZ = Math.floorDiv(globalZ, baySpacing) * baySpacing;
                    int offsetX = globalX - bayX;
                    int offsetZ = globalZ - bayZ;

                    // Check if this position matches any of the 8 light positions
                    for (int[] offset : bayLightOffsets) {
                        if (offsetX == offset[0] && offsetZ == offset[1]) {
                            chunkData.setBlock(localX, ceilingY, localZ, lightMat);
                            break;
                        }
                    }
                }
            }
        } else {
            // Standard grid lighting for non-parking-garage levels
            int spacing = config.getLightSpacing();

            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int globalX = chunkStartX + localX;
                    int globalZ = chunkStartZ + localZ;

                    if (globalX % spacing == 0 && globalZ % spacing == 0) {
                        chunkData.setBlock(localX, ceilingY, localZ, lightMat);
                    }
                }
            }
        }

        registerCeilingFlicker(layout, ceilingY, chunkStartX, chunkStartZ);
    }

    /**
     * Marks a deterministic share of ceiling lights as flickering for the
     * fluorescent light effect.
     *
     * @param layout       the computed room layout for this chunk
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void registerCeilingFlicker(RoomLayout layout, int ceilingY,
                                        int chunkStartX, int chunkStartZ) {
        double chance = config.getLightFlickerChance();
        FlickerManager flickerManager = LiminalPlugin.getFlickerManager();
        if (chance <= 0 || flickerManager == null || !layout.overlapsRoom()) {
            return;
        }

        LiminalPlugin plugin = LiminalPlugin.getInstance();
        if (plugin == null) {
            return;
        }

        World world = plugin.getServer().getWorld(plugin.getLiminalConfig().getLiminalWorldName());
        if (world == null) {
            return;
        }

        boolean isParkingGarage = "parking-garage".equalsIgnoreCase(config.getLayoutMode());
        // ceilingY is mixed in so each floor gets its own flicker pattern
        Random flickerRand = new Random(
                seed ^ ((long) chunkStartX * 0x85ebca6bL) ^ ((long) chunkStartZ * 0xc2b2ae35L)
                ^ config.getIdHashCode() ^ ((long) ceilingY * 0x27d4eb2fL));

        if (isParkingGarage) {
            // Bay-aligned flicker: same 4 positions per 16×16 bay
            int baySpacing = 16;
            int[][] bayLightOffsets = {
                {2, 2}, {13, 2}, {2, 13}, {13, 13}
            };

            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int globalX = chunkStartX + localX;
                    int globalZ = chunkStartZ + localZ;

                    int bayX = Math.floorDiv(globalX, baySpacing) * baySpacing;
                    int bayZ = Math.floorDiv(globalZ, baySpacing) * baySpacing;
                    int offsetX = globalX - bayX;
                    int offsetZ = globalZ - bayZ;

                    for (int[] offset : bayLightOffsets) {
                        if (offsetX == offset[0] && offsetZ == offset[1]) {
                            if (flickerRand.nextDouble() < chance) {
                                flickerManager.markFlickering(new Location(world, globalX, ceilingY, globalZ));
                            }
                            break;
                        }
                    }
                }
            }
        } else {
            // Standard grid flicker
            int spacing = config.getLightSpacing();

            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int globalX = chunkStartX + localX;
                    int globalZ = chunkStartZ + localZ;

                    if (globalX % spacing == 0 && globalZ % spacing == 0) {
                        if (flickerRand.nextDouble() < chance) {
                            flickerManager.markFlickering(new Location(world, globalX, ceilingY, globalZ));
                        }
                    }
                }
            }
        }
    }

    /**
     * Floor-torch lighting style: torches standing on the floor at the
     * configured spacing, skipping a configurable radius around rail lines
     * (requires a {@code rail-network} feature), with optional flicker that
     * swaps the torch for air.
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void generateFloorTorchLighting(ChunkGenerator.ChunkData chunkData,
                                            RoomLayout layout,
                                            int ceilingY,
                                            int chunkStartX, int chunkStartZ) {
        int spacing = config.getLightSpacing();
        Material torchMat = config.getLightMaterial() != null ? config.getLightMaterial() : Material.REDSTONE_TORCH;
        int floorSurfaceY = getFloorSurfaceY();

        FeatureSpec rail = config.getFeature("rail-network");
        int grid = rail != null ? rail.getInt("grid", 32) : 32;
        int lineOffset = rail != null ? rail.getInt("line-offset", 16) : 16;
        int darkRadius = config.getLightDarkRadius();

        FlickerManager flickerManager = LiminalPlugin.getFlickerManager();
        LiminalPlugin plugin = LiminalPlugin.getInstance();
        World world = null;
        if (plugin != null) {
            world = plugin.getServer().getWorld(plugin.getLiminalConfig().getLiminalWorldName());
        }

        Random flickerRand = new Random(
                seed ^ ((long) chunkStartX * 0x85ebca6bL) ^ ((long) chunkStartZ * 0xc2b2ae35L)
                ^ config.getIdHashCode() ^ ((long) ceilingY * 0x27d4eb2fL));

        for (int localX = 0; localX < 16; localX++) {
            int wx = chunkStartX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int wz = chunkStartZ + localZ;
                if (Math.floorMod(wx, spacing) != 0 || Math.floorMod(wz, spacing) != 0) continue;
                if (darkRadius > 0 && rail != null
                        && (Math.abs(Math.floorMod(wx, grid) - lineOffset) <= darkRadius
                         || Math.abs(Math.floorMod(wz, grid) - lineOffset) <= darkRadius)) {
                    continue; // keep the tracks dark
                }

                chunkData.setBlock(localX, floorSurfaceY + 1, localZ, torchMat);

                if (world != null && flickerManager != null
                        && flickerRand.nextDouble() < config.getLightFlickerChance()) {
                    flickerManager.markFlickering(new Location(world, wx, floorSurfaceY + 1, wz),
                            torchMat, Material.AIR);
                }
            }
        }
    }

    /**
     * Places a stray loot chest at a rare, deterministic position within the chunk.
     *
     * <p>Driven by the level's {@code stray-chest} feature entry; levels without
     * one keep the historical 0.4% chance. The position is derived from a seeded
     * random to remain deterministic across server restarts.</p>
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param floorY       the Y coordinate of the floor surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void generateLootChest(ChunkGenerator.ChunkData chunkData,
                                     RoomLayout layout,
                                     int floorY,
                                     int chunkStartX, int chunkStartZ) {
        FeatureSpec stray = config.getFeature("stray-chest");
        double chance = stray != null && stray.getChance() >= 0 ? stray.getChance() : 0.004;
        if (chance <= 0) {
            return;
        }

        // floorY is mixed in so multi-floor levels don't place chests at the
        // same relative position on every floor.
        Random chunkRand = new Random(
                (long) chunkStartX * 341873128712L + (long) chunkStartZ * 132897987541L
                ^ seed ^ config.getIdHashCode() ^ (floorY * 0x2545f491L)
        );

        if (chunkRand.nextInt(1000) < (int) Math.round(chance * 1000)) {
            int chestX = chunkRand.nextInt(16);
            int chestZ = chunkRand.nextInt(16);
            chunkData.setBlock(chestX, floorY + 1, chestZ, Material.CHEST);
        }
    }

    /**
     * Runs the level's configured features: integral features first (each with
     * its own deterministic random), then the shared per-block pass.
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param floorY       the Y coordinate of the floor surface
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void generateFeatures(ChunkGenerator.ChunkData chunkData,
                                    RoomLayout layout,
                                    int floorY, int ceilingY,
                                    int chunkStartX, int chunkStartZ) {
        for (FeatureSpec spec : config.getFeatures()) {
            if (spec.getType().equals("stray-chest") || spec.getType().equals("parking-garage")) continue;
            if (PER_BLOCK_FEATURES.containsKey(spec.getType())) continue; // handled in the per-block pass

            IntegralFeature handler = INTEGRAL_FEATURES.get(spec.getType());
            if (handler != null) {
                handler.generate(chunkData, layout, floorY, ceilingY, chunkStartX, chunkStartZ, config, seed, spec, this);
            } else {
                org.bukkit.Bukkit.getLogger().warning("[LiminalGen] Unknown feature type '" + spec.getType()
                        + "' in level " + config.getId() + " (skipped)");
            }
        }

        runPerBlockFeatures(chunkData, layout, floorY, ceilingY, chunkStartX, chunkStartZ);
    }

    /**
     * Runs the per-block features in a single interleaved pass: for every
     * room-interior block, each configured feature rolls once against the
     * shared feature random, in level-file order.
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param floorY       the Y coordinate of the floor surface
     * @param ceilingY     the Y coordinate of the ceiling surface
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     */
    private void runPerBlockFeatures(ChunkGenerator.ChunkData chunkData,
                                     RoomLayout layout,
                                     int floorY, int ceilingY,
                                     int chunkStartX, int chunkStartZ) {
        List<FeatureSpec> perBlock = new ArrayList<>();
        for (FeatureSpec spec : config.getFeatures()) {
            if (PER_BLOCK_FEATURES.containsKey(spec.getType())) perBlock.add(spec);
        }
        if (perBlock.isEmpty() || !layout.overlapsRoom()) {
            return;
        }

        Random featureRand = new Random(
                seed ^ ((long) chunkStartX * 0x9e3779b9L) ^ ((long) chunkStartZ * 0x517cc1b7L) ^ config.getIdHashCode()
        );

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int globalX = chunkStartX + localX;
                int globalZ = chunkStartZ + localZ;

                boolean insideRoom = globalX > layout.roomStartX() && globalX < layout.roomEndX() - 1
                                  && globalZ > layout.roomStartZ() && globalZ < layout.roomEndZ() - 1;

                if (!insideRoom) {
                    continue;
                }

                FeatureContext context = new FeatureContext(
                        chunkData, layout, localX, localZ, globalX, globalZ, floorY, ceilingY);
                for (FeatureSpec spec : perBlock) {
                    PER_BLOCK_FEATURES.get(spec.getType()).apply(context, spec, featureRand);
                }
            }
        }
    }

    /**
     * Generates a stairwell connecting this floor to the next floor above.
     *
     * <p>The stairwell is a spiral staircase tucked into the <b>corner</b> of
     * the room: every floor's room anchors at the same grid-cell origin (only the
     * room sizes vary), so the shaft always sits at that shared corner with a
     * 1-block inset. Because every floor's wall lines run at offset 0 or at least
     * {@code roomMinWidth - 1} blocks away from the origin, no wall of any floor
     * above or below can ever cross the shaft column &mdash; collisions are
     * impossible by construction.</p>
     *
     * <p>Structure: a central support pillar topped with the configured cap
     * light, stairs winding up around it (south &rarr; west &rarr; north &rarr;
     * east), wall columns on the north/east/west faces, corners left open for
     * the climb, and the <b>south</b> face open as the entrance (facing the room
     * interior). When several floors in a row have stairwells they stack into a
     * continuous climbable tower at the same corner.</p>
     *
     * <p>The shaft clears the current floor's ceiling; the passage through the
     * <b>next</b> floor's slab is carved later (see {@link #carveStairwellOpening})
     * because that floor's slab is laid after this method runs and would otherwise
     * seal the shaft shut.</p>
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param floorY       the Y coordinate of the current floor surface
     * @param ceilingY     the Y coordinate of the current floor's ceiling
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     * @param floorIndex   the index of the current floor (0 = bottom)
     * @param spec         the stairwell configuration
     * @return {@code {worldX, worldZ, style}} — the world anchor of the shaft,
     *         whose opening must be carved in the next floor's slab (style 0 =
     *         spiral 3x3 hole, 1 = ramp emergence trench), or {@code null} when
     *         this room has no stairwell (or the shaft would span a chunk border)
     */
    private int[] generateStairwell(ChunkGenerator.ChunkData chunkData,
                                    RoomLayout layout,
                                    int floorY, int ceilingY,
                                    int chunkStartX, int chunkStartZ,
                                    int floorIndex, StairwellSpec spec) {
        // Determine if this room has a stairwell. Ramp styles roll per ROOM
        // (rooms span chunk borders, so every overlapping chunk must reach
        // the same decision); spiral styles keep the per-chunk roll.
        long stairwellSeed;
        if (spec.isRamp()) {
            stairwellSeed = seed ^ ((long) layout.roomStartX() * 0x6a09e667L)
                    ^ ((long) layout.roomStartZ() * 0xbb67ae85L)
                    ^ config.getIdHashCode() ^ (floorIndex * 0x9e3779b9L);
        } else {
            stairwellSeed = seed ^ ((long) chunkStartX * 0x6a09e667L)
                    ^ ((long) chunkStartZ * 0xbb67ae85L)
                    ^ config.getIdHashCode() ^ (floorIndex * 0x9e3779b9L);
        }
        Random stairwellRand = new Random(stairwellSeed);

        if (stairwellRand.nextDouble() >= spec.chance()) {
            return null; // No stairwell in this room
        }

        if (spec.isRamp()) {
            return generateRamp(chunkData, layout, floorY, ceilingY, chunkStartX, chunkStartZ, spec);
        }

        // Anchor the shaft at the room's minimum corner (shared by every floor,
        // so stacked stairwells align into a tower and no wall line of any floor
        // can cross the shaft column). The centre sits 2 blocks in from both
        // walls, giving a 1-block air gap between the shaft walls and the room's
        // own west/north walls.
        int roomCenterX = layout.roomStartX() + 2;
        int roomCenterZ = layout.roomStartZ() + 2;

        // Check if the stairwell position is within this chunk (with room for
        // the full 3x3 shaft, so shaft centres hugging a chunk border are skipped)
        int localX = roomCenterX - chunkStartX;
        int localZ = roomCenterZ - chunkStartZ;

        if (localX < 1 || localX > 14 || localZ < 1 || localZ > 14) {
            return null; // Stairwell is in a different chunk or spans the border
        }

        Material wallMat = config.getWallMaterial();
        int floorHeight = config.getCeilingHeight() + 1;
        int nextFloorY = floorY + floorHeight;

        // Clear the shaft volume (air space + this floor's ceiling). The floor
        // surface at floorY is left intact so the shaft has a solid base, and
        // the next floor's slab (at nextFloorY) is carved by carveStairwellOpening.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = floorY + 1; y < nextFloorY; y++) {
                    chunkData.setBlock(localX + dx, y, localZ + dz, Material.AIR);
                }
            }
        }

        // Central support pillar with a light on top so the shaft is lit
        for (int y = floorY + 1; y < ceilingY; y++) {
            chunkData.setBlock(localX, y, localZ, wallMat);
        }
        chunkData.setBlock(localX, ceilingY, localZ, spec.capLight());

        // Enclose the north, east and west faces (corners stay open so the
        // spiral climb has room to turn). The south face stays open as the
        // entrance, facing the room interior. Stairs below overwrite their
        // own positions in these columns.
        for (int y = floorY + 1; y <= ceilingY; y++) {
            chunkData.setBlock(localX, y, localZ - 1, wallMat);     // north
            chunkData.setBlock(localX + 1, y, localZ, wallMat);     // east
            chunkData.setBlock(localX - 1, y, localZ, wallMat);     // west
        }

        // Spiral stairs winding up around the pillar (south -> west -> north -> east)
        chunkData.setBlock(localX, floorY + 1, localZ + 1, spec.stairs());
        chunkData.setBlock(localX - 1, floorY + 2, localZ, spec.stairs());
        chunkData.setBlock(localX, floorY + 3, localZ - 1, spec.stairs());
        chunkData.setBlock(localX + 1, ceilingY, localZ, spec.stairs());

        return new int[]{roomCenterX, roomCenterZ, 0};
    }

    /**
     * Carves the opening through a floor's slab for the shaft rising from the
     * floor below, so players can step out of it onto this floor: a 3x3 hole
     * for spiral stairwells, or a 3-wide trench along the ramp's run for ramp
     * styles (the ramp climbs through the deck above).
     *
     * @param chunkData the mutable chunk data
     * @param localX    local chunk X of the shaft centre (recorded by generateStairwell)
     * @param localZ    local chunk Z of the shaft centre
     * @param floorY    the Y coordinate of this floor's surface (the slab to carve)
     * @param ramp      whether the shaft is a straight ramp (trench carve)
     */
    private void carveStairwellOpening(ChunkGenerator.ChunkData chunkData,
                                       int worldX, int worldZ, int floorY, boolean ramp,
                                       int chunkStartX, int chunkStartZ) {
        for (int dx = -1; dx <= 1; dx++) {
            int lx = worldX - chunkStartX + dx;
            if (lx < 0 || lx > 15) continue;
            int from = ramp ? 0 : -1;
            int to = ramp ? 7 : 1;
            for (int dz = from; dz <= to; dz++) {
                int lz = worldZ - chunkStartZ + dz;
                if (lz < 0 || lz > 15) continue;
                chunkData.setBlock(lx, floorY, lz, Material.AIR);
            }
        }
    }

    /**
     * Generates a straight parking-garage ramp: a 3-wide run rising one deck
     * toward +Z over four (stair, landing) pairs — a 1:2 gradient, walkable
     * and far less steep than a pure stair run. The run is centred on the
     * room's centre column and may straddle a chunk border: every chunk
     * overlapping the room places the run cells it owns (trench clear +
     * stair/landing blocks) from the same room-deterministic geometry, so the
     * ramp assembles seamlessly across chunks. The next deck's slab is carved
     * open along the run when it is laid (see {@link #carveStairwellOpening},
     * which is likewise cross-chunk). Climbing the run leaves the player in
     * the carved trench just below the next deck; a final step out of the
     * trench completes the emergence.
     *
     * @param chunkData    the mutable chunk data
     * @param layout       the computed room layout for this chunk
     * @param floorY       the Y coordinate of the current deck's surface
     * @param ceilingY     the Y coordinate of the current deck's ceiling
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     * @param spec         the stairwell configuration
     * @return {@code {worldX, worldZ, style=1}} — the world anchor of the run's
     *         first cell (its carve starts there), or {@code null} when the
     *         ramp does not fit this room
     */
    private int[] generateRamp(ChunkGenerator.ChunkData chunkData,
                               RoomLayout layout,
                               int floorY, int ceilingY,
                               int chunkStartX, int chunkStartZ,
                               StairwellSpec spec) {
        // The run starts 4 cells before the room's centre and runs 8 cells
        // (+Z), so the ramp straddles the room's midpoint and the step-off
        // lands in the room's interior.
        int centerX = layout.roomStartX() + layout.roomWidth() / 2;
        int centerZ = layout.roomStartZ() + layout.roomLength() / 2;
        int runStart = centerZ - 4;

        if (centerX - 1 <= layout.roomStartX() || centerX + 1 >= layout.roomEndX() - 1) return null;
        if (runStart < layout.roomStartZ() + 1 || runStart + 8 >= layout.roomEndZ() - 1) return null;

        Material stairs = spec.stairs();
        Material landing = spec.landingBlock();

        // Place the run cells this chunk owns; neighbours place the rest.
        for (int j = 0; j < 8; j++) {
            int lz = runStart + j - chunkStartZ;
            if (lz < 0 || lz > 15) continue;
            int pair = j / 2;
            boolean stairCell = (j % 2) == 0;
            for (int dx = -1; dx <= 1; dx++) {
                int lx = centerX - chunkStartX + dx;
                if (lx < 0 || lx > 15) continue;
                // Clear the trench through the deck's interior and ceiling.
                for (int y = floorY + 1; y <= ceilingY; y++) {
                    chunkData.setBlock(lx, y, lz, Material.AIR);
                }
                if (stairCell) {
                    org.bukkit.block.data.type.Stairs data =
                            (org.bukkit.block.data.type.Stairs) stairs.createBlockData();
                    data.setFacing(org.bukkit.block.BlockFace.SOUTH);
                    chunkData.setBlock(lx, floorY + 1 + pair, lz, data);
                } else {
                    chunkData.setBlock(lx, floorY + 1 + pair, lz, landing);
                }
            }
        }

        return new int[]{centerX, runStart, 1};
    }

    // -------------------------------------------------------------------------
    // Room layout record
    // -------------------------------------------------------------------------

    /**
     * Immutable record describing the room that overlaps (or doesn't) a given chunk.
     *
     * <p>Computed by {@link #calculateRoomLayout} and passed through the generation
     * pipeline so each step has access to the same room geometry without recomputation.</p>
     *
     * @param roomStartX  world X of the room's western edge
     * @param roomStartZ  world Z of the room's northern edge
     * @param roomEndX    world X just past the room's eastern edge
     * @param roomEndZ    world Z just past the room's southern edge
     * @param roomWidth   the room's width in blocks (X axis)
     * @param roomLength  the room's length in blocks (Z axis)
     * @param doorwayPos  the offset from the room corner to the centre of each doorway
     * @param overlaps    whether this room overlaps the current chunk
     */
    public record RoomLayout(
            int roomStartX, int roomStartZ,
            int roomEndX, int roomEndZ,
            int roomWidth, int roomLength,
            int doorwayPos,
            boolean overlapsRoom
    ) {
    }
}
