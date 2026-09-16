package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.derpcraft.liminal.config.FeatureSpec;
import org.derpcraft.liminal.config.LevelConfig;
import org.derpcraft.liminal.generator.levels.LiminalLevel;

import java.util.List;
import java.util.Random;

/**
 * Abandoned power-plant pipe runs: horizontal copper pipes along room walls
 * just below the ceiling, with an aging mix of copper oxidation stages and
 * occasional support brackets.
 *
 * <p>Every wall of every room independently rolls the configured chance to
 * carry a pipe bundle: {@code rows} stacked pipe lines running one block in
 * from the wall face, starting {@code height-offset} blocks below the ceiling.
 * Pipe material is chosen per segment (a run of {@code segment-length} cells)
 * from the configured aging mix — independently per row — and every
 * {@code bracket-spacing} cells a bracket column ties the bundle up to the
 * ceiling.</p>
 *
 * <p>All decisions derive from the room's grid geometry and per-cell indices
 * (never from chunk-local state), so a pipe run continues seamlessly across
 * the chunk borders a large room spans.</p>
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code chance} &ndash; probability per wall (default {@code 0.6})</li>
 *   <li>{@code height-offset} &ndash; blocks below the ceiling for the top row (default {@code 2})</li>
 *   <li>{@code rows} &ndash; stacked pipe lines per bundle (default {@code 1})</li>
 *   <li>{@code materials} &ndash; the segment material mix (default CUT/EXPOSED/WEATHERED/OXIDIZED copper)</li>
 *   <li>{@code segment-length} &ndash; cells per material segment (default {@code 6})</li>
 *   <li>{@code bracket} &ndash; support bracket material (default {@code YELLOW_TERRACOTTA})</li>
 *   <li>{@code bracket-spacing} &ndash; cells between brackets (default {@code 8})</li>
 * </ul>
 */
public final class PipesFeature implements IntegralFeature {

    /** Default aging mix for pipe segments. */
    private static final List<Material> DEFAULT_MATERIALS = List.of(
            Material.CUT_COPPER, Material.EXPOSED_COPPER,
            Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER);

    @Override
    public String type() {
        return "pipes";
    }

    @Override
    public void generate(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                         int floorY, int ceilingY, int chunkStartX, int chunkStartZ,
                         LevelConfig config, long seed, FeatureSpec spec, LiminalLevel level) {
        if (!layout.overlapsRoom()) {
            return;
        }

        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.6;
        int heightOffset = spec.getInt("height-offset", 2);
        int rows = Math.max(1, spec.getInt("rows", 1));
        int segmentLength = Math.max(1, spec.getInt("segment-length", 6));
        int bracketSpacing = Math.max(1, spec.getInt("bracket-spacing", 8));
        Material bracket = spec.getMaterial("bracket", Material.YELLOW_TERRACOTTA);
        List<Material> materials = spec.getMaterialList("materials", DEFAULT_MATERIALS);

        int topRowY = ceilingY - heightOffset;
        if (topRowY - (rows - 1) <= floorY + 1) {
            return;
        }

        // One seeded decision stream per room (never per chunk): walls roll
        // for existence here, cells derive their segment material from stable
        // per-index hashes, so runs match across chunk borders.
        long roomSeed = seed
                ^ ((long) layout.roomStartX() * 0x6C62272EL)
                ^ ((long) layout.roomStartZ() * 0x2545F491L)
                ^ config.getIdHashCode() ^ 0x3C6EF372L;
        Random wallRand = new Random(roomSeed);

        for (int side = 0; side < 4; side++) {
            if (wallRand.nextDouble() >= chance) continue;
            drawWallPipe(chunkData, layout, side, topRowY, rows, chunkStartX, chunkStartZ,
                    roomSeed, materials, segmentLength, bracket, bracketSpacing, ceilingY);
        }
    }

    /**
     * Draws one wall's pipe bundle: iterates the wall's interior-adjacent
     * cells and writes only the ones inside this chunk. Cell materials come
     * from per-index hashes (segment-bucketed, independently per row);
     * bracket columns tie the bundle to the ceiling.
     *
     * @param chunkData      the mutable chunk data
     * @param layout         the room layout
     * @param side           the wall (0 = north, 1 = south, 2 = west, 3 = east)
     * @param topRowY        the Y coordinate of the bundle's top pipe row
     * @param rows           the number of stacked pipe rows
     * @param chunkStartX    world X of the chunk's western edge
     * @param chunkStartZ    world Z of the chunk's northern edge
     * @param roomSeed       the room's decision seed
     * @param materials      the aging mix for segments
     * @param segmentLength  cells per material segment
     * @param bracket        the bracket material
     * @param bracketSpacing cells between brackets
     * @param ceilingY       the Y coordinate of the ceiling slab
     */
    private void drawWallPipe(ChunkGenerator.ChunkData chunkData, LiminalLevel.RoomLayout layout,
                              int side, int topRowY, int rows, int chunkStartX, int chunkStartZ,
                              long roomSeed, List<Material> materials, int segmentLength,
                              Material bracket, int bracketSpacing, int ceilingY) {
        // Interior-adjacent wall line: one block in from the wall face.
        // Sides 0/1 run along X (north/south walls), sides 2/3 along Z (west/east).
        int fixedX = side < 2 ? 0 : (side == 2 ? layout.roomStartX() + 1 : layout.roomEndX() - 2);
        int fixedZ = side < 2 ? (side == 0 ? layout.roomStartZ() + 1 : layout.roomEndZ() - 2) : 0;
        int from = side < 2 ? layout.roomStartX() + 1 : layout.roomStartZ() + 1;
        int to = side < 2 ? layout.roomEndX() - 2 : layout.roomEndZ() - 2;

        for (int i = from; i <= to; i++) {
            int x = (side < 2) ? i : fixedX;
            int z = (side < 2) ? fixedZ : i;

            int localX = x - chunkStartX;
            int localZ = z - chunkStartZ;
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) continue;

            // Segment material: stable per side + row + segment bucket.
            int bucket = Math.floorDiv(i, segmentLength);
            for (int row = 0; row < rows; row++) {
                int rowY = topRowY - row;
                Random cellRand = new Random(roomSeed
                        ^ ((long) side * 9176L) ^ ((long) bucket * 541L) ^ ((long) row * 7349L));
                Material material = materials.get(cellRand.nextInt(materials.size()));
                chunkData.setBlock(localX, rowY, localZ, material);
            }

            // Support bracket column up to the ceiling every bracketSpacing cells.
            if (Math.floorMod(i, bracketSpacing) == 0) {
                for (int y = topRowY + 1; y < ceilingY; y++) {
                    chunkData.setBlock(localX, y, localZ, bracket);
                }
            }
        }
    }
}
