package org.derpcraft.liminal.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LiminalConfig;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.config.RailNetworkSpec;

import java.util.List;
import java.util.Random;

/**
 * Generates the transition corridors between adjacent level rings.
 *
 * <p>Where two level rings meet, a corridor as wide as the configured
 * {@code transition-width} blends one level into the next:</p>
 *
 * <ul>
 *   <li><b>Sloped walkway</b> &mdash; the floor rises smoothly from the inner
 *       level's elevation to the outer level's (4-block steps by default),
 *       climbing one block every {@code width / |step|} blocks so it stays
 *       walkable and climbable by powered rails.</li>
 *   <li><b>Material gradient</b> &mdash; walls, floors and ceilings dither
 *       through a gradient palette (from level material &rarr; transition
 *       material &rarr; to level material) based on how far into the corridor
 *       the block sits.</li>
 *   <li><b>Perimeter walls</b> &mdash; circular walls at both corridor edges
 *       (with regular doorway openings) separate the corridor from the open
 *       plazas of the pure levels on either side.</li>
 *   <li><b>Rails</b> &mdash; when the outer level has a rail network, the grid
 *       lines climb the slope with per-cell ascending shapes and stable booster
 *       torches. Viaducts are skipped on the slope (crossings place a plain
 *       east-west rail instead), keeping every shape physically valid.</li>
 * </ul>
 */
public class TransitionZoneGenerator {

    /** The plugin configuration (transition width, level definitions). */
    private final LiminalConfig config;

    /** Generation seed for deterministic dithering and doorway placement. */
    private final long seed;

    /**
     * Constructs a transition zone generator.
     *
     * @param config the plugin configuration
     * @param seed   the world generation seed
     */
    public TransitionZoneGenerator(LiminalConfig config, long seed) {
        this.config = config;
        this.seed = seed;
    }

    /**
     * Generates the corridor for one chunk of a transition zone.
     *
     * @param chunkData    the mutable chunk data
     * @param chunkStartX  world X of the chunk's western edge
     * @param chunkStartZ  world Z of the chunk's northern edge
     * @param t            the boundary pair this chunk belongs to
     */
    public void generate(ChunkGenerator.ChunkData chunkData,
                         int chunkStartX, int chunkStartZ,
                         LiminalConfig.TransitionInfo t) {
        double half = config.getTransitionWidth() / 2.0;
        double inner = t.boundary() - half;
        double outer = t.boundary() + half;

        LevelConfig from = t.from();
        LevelConfig to = t.to();

        // Configured gradients on the TO level (its inbound transition) take
        // precedence; otherwise the automatic colour-space palette blends the
        // two levels' own materials — any level pairing works without config.
        List<Material> wallGrad = to.getWallGradient();
        List<Material> floorGrad = to.getFloorGradient();
        List<Material> ceilGrad = to.getCeilingGradient();
        boolean wallAuto = wallGrad == null || wallGrad.isEmpty();
        boolean floorAuto = floorGrad == null || floorGrad.isEmpty();
        boolean ceilAuto = ceilGrad == null || ceilGrad.isEmpty();
        if (wallAuto) wallGrad = List.of(from.getWallMaterial(), to.getWallMaterial());
        if (floorAuto) floorGrad = List.of(from.getFloorMaterial(), to.getFloorMaterial());
        if (ceilAuto) ceilGrad = List.of(from.getCeilingMaterial(), to.getCeilingMaterial());

        Random rand = new Random(seed
                ^ ((long) chunkStartX * 0x1b873593L)
                ^ ((long) chunkStartZ * 0xcc9e2d51L)
                ^ 0x2f9e17bL);

        int spacing = Math.max(3, to.getLightSpacing());

        for (int localX = 0; localX < 16; localX++) {
            int wx = chunkStartX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int wz = chunkStartZ + localZ;
                double dist = Math.sqrt((double) wx * wx + (double) wz * wz);
                double progress = clamp((dist - inner) / (outer - inner));

                int slab = floorYAt(dist, t);
                int ceilHeight = from.getCeilingHeight()
                        + (int) Math.round(progress * (to.getCeilingHeight() - from.getCeilingHeight()));
                int ceilY = slab + ceilHeight;

                // Floor and ceiling with gradient materials.
                double noise = dither(wx, wz);
                chunkData.setBlock(localX, slab, localZ,
                        pickMaterial(floorGrad, floorAuto, from.getFloorMaterial(), to.getFloorMaterial(), progress, noise));
                chunkData.setBlock(localX, ceilY, localZ,
                        pickMaterial(ceilGrad, ceilAuto, from.getCeilingMaterial(), to.getCeilingMaterial(), progress, noise));

                boolean onInnerWall = dist <= inner && crossesContour(wx, wz, inner);
                boolean onOuterWall = dist <= outer && crossesContour(wx, wz, outer);

                if (onInnerWall || onOuterWall) {
                    // Perimeter wall with regular doorway openings.
                    if (hash(wx, wz) % 8 != 0) {
                        Material wallMat = pickMaterial(wallGrad, wallAuto, from.getWallMaterial(), to.getWallMaterial(), progress, noise);
                        for (int y = slab + 1; y < ceilY; y++) {
                            chunkData.setBlock(localX, y, localZ, wallMat);
                        }
                    }

                    continue; // no props inside wall columns
                }

                // Occasional support pillars mid-corridor.
                if (dist > inner + 6 && dist < outer - 6 && hash(wx, wz) % 37 == 0) {
                    Material wallMat = pickMaterial(wallGrad, wallAuto, from.getWallMaterial(), to.getWallMaterial(), progress, noise);
                    for (int y = slab + 1; y < ceilY; y++) {
                        chunkData.setBlock(localX, y, localZ, wallMat);
                    }
                    continue;
                }

                // Ceiling lights on the light grid, swapping materials mid-way.
                if (mod(wx, spacing) == 0 && mod(wz, spacing) == 0) {
                    Material light = progress < 0.5 ? from.getLightMaterial() : to.getLightMaterial();
                    if (light != null) {
                        chunkData.setBlock(localX, ceilY, localZ, light);
                    }
                }
            }
        }

        // Rails climb the slope when the outer level has a rail network (and
        // the inner level does not, so the two networks don't overlap).
        FeatureSpec toRail = to.getFeature("rail-network");
        if (toRail != null && from.getFeature("rail-network") == null) {
            generateCorridorRails(chunkData, chunkStartX, chunkStartZ, t, inner, outer,
                    new RailNetworkSpec(toRail));
        }
    }

    /**
     * Lays rail onto the corridor's sloped walkway for grid cells, with
     * per-cell shapes derived from the local slope and stable booster torches.
     * Rails stop short of both corridor edges so carts never hit a wall. The
     * grid geometry comes from the outer level's rail spec, so any rail-enabled
     * level pairs seamlessly with its corridor.
     */
    private void generateCorridorRails(ChunkGenerator.ChunkData chunkData,
                                       int chunkStartX, int chunkStartZ,
                                       LiminalConfig.TransitionInfo t,
                                       double inner, double outer,
                                       RailNetworkSpec rails) {
        Random rand = new Random(seed
                ^ ((long) chunkStartX * 0x6a09e667L)
                ^ ((long) chunkStartZ * 0xbb67ae85L)
                ^ 0x5a3f0c77L);

        int grid = rails.grid();
        int lineOffset = rails.lineOffset();
        int powerEvery = rails.powerEvery();

        for (int localX = 0; localX < 16; localX++) {
            int wx = chunkStartX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int wz = chunkStartZ + localZ;
                double dist = Math.sqrt((double) wx * wx + (double) wz * wz);
                if (dist < inner + 6 || dist > outer - 6) continue;

                boolean onXLine = Math.floorMod(wz, grid) == lineOffset;
                boolean onZLine = Math.floorMod(wx, grid) == lineOffset;
                boolean crossing = onXLine && onZLine;
                if (!onXLine && !onZLine) continue;

                // Broken track (config-gated, default never).
                if (rand.nextDouble() < rails.brokenTrackChance()) continue;

                int railY = floorYAt(dist, t) + 1;

                if (crossing) {
                    // Slope crossings keep the X line running; the Z line gaps here.
                    setRail(chunkData, wx, railY, wz, false, true, t);
                    continue;
                }

                boolean powered = onXLine
                        ? Math.floorMod(wx, powerEvery) == 0
                        : Math.floorMod(wz, powerEvery) == 0;
                setRail(chunkData, wx, railY, wz, powered, onXLine, t);
            }
        }
    }

    /**
     * Writes one rail cell with a shape derived from the local slope: the
     * neighbour cells' rail heights decide between flat and ascending shapes
     * (world generation performs no block updates, so shapes must be set
     * explicitly). Powered rails carry {@code powered=true} in their block data
     * and get a stable booster torch on the floor beside them, offset
     * perpendicular to the line so it never occupies the next track cell.
     */
    private void setRail(ChunkGenerator.ChunkData chunkData, int x, int railY, int z,
                         boolean powered, boolean onXLine, LiminalConfig.TransitionInfo t) {
        org.bukkit.block.data.Rail data;
        if (powered) {
            data = (org.bukkit.block.data.Rail) Material.POWERED_RAIL.createBlockData();
            ((org.bukkit.block.data.Powerable) data).setPowered(true);
        } else {
            data = (org.bukkit.block.data.Rail) Material.RAIL.createBlockData();
        }

        if (onXLine) {
            int yWest = floorYAt(distAt(x - 1, z), t) + 1;
            int yEast = floorYAt(distAt(x + 1, z), t) + 1;
            if (yEast > railY) {
                data.setShape(org.bukkit.block.data.Rail.Shape.ASCENDING_EAST);
            } else if (yWest > railY) {
                data.setShape(org.bukkit.block.data.Rail.Shape.ASCENDING_WEST);
            } else {
                data.setShape(org.bukkit.block.data.Rail.Shape.EAST_WEST);
            }
        } else {
            int yNorth = floorYAt(distAt(x, z - 1), t) + 1;
            int ySouth = floorYAt(distAt(x, z + 1), t) + 1;
            if (ySouth > railY) {
                data.setShape(org.bukkit.block.data.Rail.Shape.ASCENDING_SOUTH);
            } else if (yNorth > railY) {
                data.setShape(org.bukkit.block.data.Rail.Shape.ASCENDING_NORTH);
            } else {
                data.setShape(org.bukkit.block.data.Rail.Shape.NORTH_SOUTH);
            }
        }

        chunkData.setBlock(x & 15, railY, z & 15, data);

        if (powered) {
            int tx = onXLine ? x : x + 1;
            int tz = onXLine ? z + 1 : z;
            chunkData.setBlock(tx & 15, railY, tz & 15, Material.REDSTONE_TORCH);
        }
    }

    /**
     * Returns the corridor floor slab Y for a world position: the inner level's
     * elevation before the corridor, the outer level's after it, and a linear
     * ramp (rounded to whole blocks) inside it.
     */
    private int floorYAt(double dist, LiminalConfig.TransitionInfo t) {
        double half = config.getTransitionWidth() / 2.0;
        double inner = t.boundary() - half;
        double outer = t.boundary() + half;
        int fromY = t.from().getFloorSurfaceY();
        int toY = t.to().getFloorSurfaceY();
        if (dist <= inner) return fromY;
        if (dist >= outer) return toY;
        double progress = (dist - inner) / (outer - inner);
        return fromY + (int) Math.round(progress * (toY - fromY));
    }

    /** Euclidean distance from the world origin for a world position. */
    private static double distAt(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /**
     * True when the position is inside the contour (dist &le; contour) but one of
     * its four horizontal neighbours is outside &mdash; i.e. this cell is part of
     * the circular wall face. Uses pure distance math so walls are consistent
     * across chunk borders.
     */
    private boolean crossesContour(int x, int z, double contour) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > contour) return false;
        return Math.sqrt((double) (x + 1) * (x + 1) + (double) z * z) > contour
                || Math.sqrt((double) (x - 1) * (x - 1) + (double) z * z) > contour
                || Math.sqrt((double) x * x + (double) (z + 1) * (z + 1)) > contour
                || Math.sqrt((double) x * x + (double) (z - 1) * (z - 1)) > contour;
    }

    /**
     * Selects a transition material at the given progress: a configured gradient
     * (on the TO level) is dithered through directly; otherwise the automatic
     * colour-space palette samples the blend between the two levels' materials.
     */
    private Material pickMaterial(List<Material> gradient, boolean automatic,
                                  Material fromMat, Material toMat,
                                  double progress, double noise) {
        if (automatic) {
            return TransitionPalette.sample(fromMat, toMat, progress, noise);
        }
        return pick(gradient, progress, noise);
    }

    /**
     * Dithers through a configured gradient: early positions favour the first
     * material, late positions the last, with per-block noise between the
     * adjacent palette entries.
     */
    private Material pick(List<Material> gradient, double progress, double noise) {
        if (gradient.isEmpty()) return Material.AIR;
        if (gradient.size() == 1) return gradient.get(0);
        double band = clamp(progress) * (gradient.size() - 1);
        int lo = (int) Math.min(band, gradient.size() - 2);
        double frac = band - lo;
        return noise < (1.0 - frac) ? gradient.get(lo) : gradient.get(lo + 1);
    }

    /** Deterministic hash of a world position for dithering and prop placement. */
    private static int hash(int x, int z) {
        return x * 374761393 + z * 668265263;
    }

    /** Per-block dithering noise in 0..1 for gradient blending. */
    private static double dither(int x, int z) {
        return (hash(x, z) % 1000) / 1000.0;
    }

    /** Positive modulo. */
    private static int mod(int value, int m) {
        return Math.floorMod(value, m);
    }

    /** Clamps a value into [0, 1]. */
    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
