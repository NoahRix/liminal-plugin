package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.config.RailNetworkSpec;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The abandoned rail network feature.
 *
 * <p>A grid of minecart lines stretches across the level, kept alive by
 * redstone torches bolted to the track beds. The rails are fully powered so
 * players (and things) can still ride them &mdash; which is exactly the problem:
 * the network works perfectly, but nobody remembers who it was built for.</p>
 *
 * <h2>Geometry (all knobbed via the level file)</h2>
 * <ul>
 *   <li><b>X lines</b> run along the X axis at world Z &equiv; {@code line-offset}
 *       (mod {@code grid})</li>
 *   <li><b>Z lines</b> run along the Z axis at world X &equiv; {@code line-offset}
 *       (mod {@code grid})</li>
 *   <li>Lines cross every {@code grid} blocks. The Z line passes <b>over</b> the
 *       X line on a short viaduct (rails climb {@code viaduct-height} blocks onto
 *       a beam, then descend) so both axes stay fully traversable. The X line
 *       passes beneath with two blocks of clearance.</li>
 *   <li>Every {@code power-every}th rail is a {@link Material#POWERED_RAIL} with
 *       a stable redstone torch beside it. Booster torches are <b>never</b>
 *       registered as flickering &mdash; killing one would strand minecarts
 *       mid-network.</li>
 *   <li>Stations every {@code station-every} blocks along an X line: a platform
 *       with sign posts (filled in by the populator with cryptic messages) and
 *       torches.</li>
 * </ul>
 *
 * <h2>Creepy details</h2>
 * <ul>
 *   <li><b>Broken track</b> &mdash; {@code broken-track-chance} (default 0):
 *       some rail cells are simply missing, as if the line was interrupted.</li>
 *   <li><b>Cobwebs</b> &mdash; {@code cobweb-chance} to find cobwebs beside a
 *       rail. Cobwebs never occupy a rail cell or the column above it.</li>
 *   <li><b>Ghost minecarts</b> &mdash; {@code ghost-cart-chance}: handled at
 *       runtime by the chunk-load listener, which queries
 *       {@link #poweredRailCells} so the spawner agrees with the generator
 *       without any stored state.</li>
 * </ul>
 */
public final class RailNetworkFeature implements IntegralFeature {

    @Override
    public String type() {
        return "rail-network";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        RailNetworkSpec rails = new RailNetworkSpec(spec);

        Random railRand = new Random(
                seed ^ ((long) chunkStartX * 0x1b873593L) ^ ((long) chunkStartZ * 0xcc9e2d51L)
                ^ config.getIdHashCode() ^ 0x5a3f0c77L);

        int grid = rails.grid();
        int lineOffset = rails.lineOffset();
        int powerEvery = rails.powerEvery();
        int viaductHalf = rails.viaductHalf();

        int railY = floorY + 1; // rails sit directly on the floor slab
        List<int[]> railCells = new ArrayList<>();

        for (int localX = 0; localX < 16; localX++) {
            int wx = chunkStartX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int wz = chunkStartZ + localZ;
                int xm = Math.floorMod(wx, grid);
                int zm = Math.floorMod(wz, grid);
                boolean onXLine = zm == lineOffset;
                boolean onZLine = xm == lineOffset;
                if (!onXLine && !onZLine) continue;

                if (onXLine && onZLine) {
                    // Crossing: plain rail keeps the X line continuous through the
                    // junction; the Z line hops over on the viaduct instead.
                    placeRail(chunkData, wx, railY, wz, false, true, Material.RAIL, config);
                    railCells.add(new int[]{wx, railY, wz});
                    continue;
                }

                // Z-line cells inside a viaduct approach are placed by placeViaduct.
                if (onZLine && Math.abs(zm - lineOffset) <= viaductHalf) continue;

                boolean powered = onXLine
                        ? Math.floorMod(wx, powerEvery) == 0
                        : Math.floorMod(wz, powerEvery) == 0;

                // Broken track: leave a gap (config-gated, default never).
                if (railRand.nextDouble() < rails.brokenTrackChance()) continue;

                placeRail(chunkData, wx, railY, wz, powered, onXLine, null, config);
                railCells.add(new int[]{wx, railY, wz});
            }
        }

        // Viaducts: Z lines bridge over X lines at every crossing.
        for (int localX = 0; localX < 16; localX++) {
            int wx = chunkStartX + localX;
            if (Math.floorMod(wx, grid) != lineOffset) continue;
            // Both crossings that could reach into this chunk (the viaduct spans
            // the crossing's chunk and its northern neighbour).
            for (long base = Math.floorDiv((long) chunkStartZ - viaductHalf, grid);
                 base <= Math.floorDiv((long) chunkStartZ + 15 + viaductHalf, grid);
                 base++) {
                int crossingZ = (int) (base * grid + lineOffset);
                for (int dz = -viaductHalf; dz <= viaductHalf; dz++) {
                    int wz = crossingZ + dz;
                    int lz = wz - chunkStartZ;
                    if (lz < 0 || lz >= 16) continue;
                    placeViaductCell(chunkData, wx, railY, wz, dz, config, rails);
                }
            }
        }

        // Stations: platforms along X lines every station-every blocks (station
        // coordinates are multiples of 16, so a station never spans a chunk border).
        int stationEvery = rails.stationEvery();
        for (int localX = 0; localX < 16; localX++) {
            int wx = chunkStartX + localX;
            if (Math.floorMod(wx, stationEvery) != 0) continue;
            for (int localZ = 0; localZ < 16; localZ++) {
                int wz = chunkStartZ + localZ;
                if (Math.floorMod(wz, grid) != lineOffset) continue;
                placeStation(chunkData, wx, railY, wz, rails);
            }
        }

        // Cobwebs beside the rails (never on a rail cell or in the column above one).
        for (int[] cell : railCells) {
            if (railRand.nextDouble() >= rails.cobwebChance()) continue;
            int side = railRand.nextInt(4);
            int sx = cell[0] + (side == 0 ? 1 : side == 1 ? -1 : 0);
            int sz = cell[2] + (side == 2 ? 1 : side == 3 ? -1 : 0);
            int lx = sx - chunkStartX;
            int lz = sz - chunkStartZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) continue;
            if (chunkData.getType(lx, cell[1], lz) == Material.AIR) {
                chunkData.setBlock(lx, cell[1], lz, Material.COBWEB);
            }
        }
    }

    /**
     * Places a rail (optionally powered) on the track bed with an explicit
     * shape. Rails placed with a bare material default to a north-south shape,
     * which derails carts on X-running lines (no block updates happen during
     * world generation to correct them). The archway carve keeps three blocks
     * above the rail clear so riders fit through walls.
     *
     * @param chunkData      the mutable chunk data
     * @param x              world X of the rail
     * @param railY          world Y of the rail block
     * @param z              world Z of the rail
     * @param powered        whether to place a powered rail (with a booster torch)
     * @param onXLine        whether this cell belongs to an X-running line
     * @param forcedMaterial explicit material override (null = rail or powered rail)
     * @param config         the owning level's configuration
     */
    private void placeRail(ChunkGenerator.ChunkData chunkData, int x, int railY, int z,
                           boolean powered, boolean onXLine, Material forcedMaterial,
                           LevelConfig config) {
        int lx = x & 15;
        int lz = z & 15;
        org.bukkit.block.data.Rail data;
        if (forcedMaterial != null) {
            data = (org.bukkit.block.data.Rail) forcedMaterial.createBlockData();
        } else if (powered) {
            data = (org.bukkit.block.data.Rail) Material.POWERED_RAIL.createBlockData();
            ((org.bukkit.block.data.Powerable) data).setPowered(true); // belt-and-braces: the torch beside it also powers this
        } else {
            data = (org.bukkit.block.data.Rail) Material.RAIL.createBlockData();
        }
        data.setShape(onXLine ? org.bukkit.block.data.Rail.Shape.EAST_WEST
                              : org.bukkit.block.data.Rail.Shape.NORTH_SOUTH);
        chunkData.setBlock(lx, railY, lz, data);
        // Archway: keep three blocks above the rail clear so riders fit through
        // walls comfortably.
        chunkData.setBlock(lx, railY + 1, lz, Material.AIR);
        chunkData.setBlock(lx, railY + 2, lz, Material.AIR);
        chunkData.setBlock(lx, railY + 3, lz, Material.AIR);

        if (powered && forcedMaterial == null) {
            placeBoosterTorch(chunkData, x, railY, z, onXLine);
        }
    }

    /**
     * Places a stable redstone torch beside a powered rail to keep it active.
     * The torch is offset <b>perpendicular</b> to the line (placing it along the
     * line would occupy the next track cell and be overwritten by the rail).
     * The torch is never registered for flicker &mdash; dying boosters would
     * strand minecarts mid-network.
     */
    private void placeBoosterTorch(ChunkGenerator.ChunkData chunkData, int x, int railY, int z, boolean onXLine) {
        int lx = onXLine ? (x & 15) : ((x + 1) & 15);
        int lz = onXLine ? ((z + 1) & 15) : (z & 15);
        chunkData.setBlock(lx, railY, lz, Material.REDSTONE_TORCH);
    }

    /**
     * Builds one cell of the viaduct where a Z line bridges over an X line.
     *
     * <p>The ramps run the full {@code viaduct-half} span: the outermost ramp
     * cells sit at floor level (height 0) and each cell toward the crossing
     * rises one block, so floor-level rails connect to the climb with no step.
     * The beam itself is a single flat cell at {@code railY + viaduct-height}
     * over the crossing (the X line passes beneath with clearance). Ramp cells
     * use explicit ascending shapes (world generation performs no block
     * updates, so shapes must be set, not inferred). A booster pylon stands
     * east of every non-centre cell, topped with a stable redstone torch that
     * powers the adjacent powered rail.</p>
     *
     * @param chunkData the mutable chunk data
     * @param x         world X of the crossing (the Z line's axis)
     * @param railY     world Y of floor-level rails
     * @param z         world Z of this viaduct cell
     * @param dz        offset from the crossing centre (-half..+half)
     * @param config    the owning level's configuration
     * @param rails     the rail network configuration
     */
    private void placeViaductCell(ChunkGenerator.ChunkData chunkData, int x, int railY, int z, int dz,
                                  LevelConfig config, RailNetworkSpec rails) {
        Material wallMat = config.getWallMaterial();
        int viaductHeight = rails.viaductHeight();
        int height = Math.max(0, viaductHeight - Math.abs(dz));
        int railAt = railY + height;
        int lx = x & 15;
        int lz = z & 15;

        org.bukkit.block.data.Rail data =
                (org.bukkit.block.data.Rail) Material.POWERED_RAIL.createBlockData();
        ((org.bukkit.block.data.Powerable) data).setPowered(true);
        if (dz == 0) {
            data.setShape(org.bukkit.block.data.Rail.Shape.NORTH_SOUTH); // flat beam over the crossing
        } else if (dz < 0) {
            // North-side ramp: rises toward the crossing (+Z) — ascending south.
            data.setShape(org.bukkit.block.data.Rail.Shape.ASCENDING_SOUTH);
        } else {
            // South-side ramp: rises toward the crossing (-Z) — ascending north.
            data.setShape(org.bukkit.block.data.Rail.Shape.ASCENDING_NORTH);
        }

        chunkData.setBlock(lx, railAt - 1, lz, wallMat);
        chunkData.setBlock(lx, railAt, lz, data);

        // Open arch above the viaduct: where a room wall crosses the crossing
        // column, the beam would otherwise sit buried inside the wall. Carve
        // everything above the rail up to (but not including) the ceiling slab
        // so the viaduct always passes through walls as an open gateway.
        int ceilingY = railY - 1 + config.getCeilingHeight();
        for (int y = railAt + 1; y < ceilingY; y++) {
            chunkData.setBlock(lx, y, lz, Material.AIR);
        }

        // Booster pylon east of the track. The centre column (dz == 0) is skipped:
        // the X line passes through it; the beam is powered from its ends anyway
        // (the data also carries powered=true).
        if (dz != 0) {
            int plx = (x + 1) & 15;
            for (int y = railY; y < railAt; y++) {
                chunkData.setBlock(plx, y, lz, wallMat);
            }
            chunkData.setBlock(plx, railAt, lz, Material.REDSTONE_TORCH);
            // Keep the pylon slot open through walls as well.
            for (int y = railAt + 1; y < ceilingY; y++) {
                chunkData.setBlock(plx, y, lz, Material.AIR);
            }
        }
    }

    /**
     * Builds a small abandoned station platform beside an X line: a platform
     * strip with sign posts (text is filled in later by the populator), a
     * supply chest, and torches. All materials and dimensions come from the
     * rail network configuration.
     */
    private void placeStation(ChunkGenerator.ChunkData chunkData, int x, int railY, int z, RailNetworkSpec rails) {
        int platformLength = rails.platformLength();
        int platformDepth = rails.platformDepth();
        int signCount = Math.max(0, Math.min(rails.signCount(), 2));

        // Platform strip on the +Z side of the track (stays inside this chunk).
        for (int dx = 0; dx < platformLength; dx++) {
            for (int dz = 2; dz < 2 + platformDepth; dz++) {
                chunkData.setBlock((x + dx) & 15, railY - 1, (z + dz) & 15, rails.platform());
            }
        }

        // Sign posts (empty; the populator writes the cryptic messages).
        int[] signOffsets = {1, 4};
        for (int i = 0; i < signCount; i++) {
            chunkData.setBlock((x + signOffsets[i]) & 15, railY, (z + 2) & 15, rails.signMaterial());
        }

        // Lighting (no supply chest — chests are removed from all levels).
        chunkData.setBlock((x + 0) & 15, railY, (z + 2) & 15, rails.torchMaterial());
        chunkData.setBlock((x + platformLength - 1) & 15, railY, (z + 2) & 15, rails.torchMaterial());
    }

    /**
     * Returns the world positions of the powered rails inside a chunk, along
     * with their travel axis, so ghost minecarts can be spawned on them at
     * load time.
     *
     * <p>Static and deterministic so the runtime spawner agrees with the
     * generator without needing any stored state. The Y component is a -1
     * marker; the caller resolves it from the level's floor surface.</p>
     *
     * @param rails  the rail network configuration
     * @param chunkX the chunk's X coordinate
     * @param chunkZ the chunk's Z coordinate
     * @return list of {@code {x, -1, z, axis}} where axis 0 = X line, 1 = Z line
     */
    public static List<int[]> poweredRailCells(RailNetworkSpec rails, int chunkX, int chunkZ) {
        List<int[]> cells = new ArrayList<>();
        int grid = rails.grid();
        int lineOffset = rails.lineOffset();
        int powerEvery = rails.powerEvery();
        int chunkStartX = chunkX * 16;
        int chunkStartZ = chunkZ * 16;
        for (int i = 0; i < 16; i++) {
            int wx = chunkStartX + i;
            int wz = chunkStartZ + i;
            if (Math.floorMod(wz, grid) == lineOffset && Math.floorMod(wx, powerEvery) == 0
                    && Math.floorMod(wx, grid) != lineOffset) {
                cells.add(new int[]{wx, -1, wz, 0});
            }
            if (Math.floorMod(wx, grid) == lineOffset && Math.floorMod(wz, powerEvery) == 0
                    && Math.floorMod(wz, grid) != lineOffset) {
                cells.add(new int[]{wx, -1, wz, 1});
            }
        }
        return cells;
    }

    /**
     * True when a world position sits on a rail line cell (either axis).
     *
     * @param rails the rail network configuration
     * @param x     world X
     * @param z     world Z
     * @return whether (x, z) is part of the rail grid
     */
    public static boolean isRailLine(RailNetworkSpec rails, int x, int z) {
        return Math.floorMod(x, rails.grid()) == rails.lineOffset()
                || Math.floorMod(z, rails.grid()) == rails.lineOffset();
    }
}
