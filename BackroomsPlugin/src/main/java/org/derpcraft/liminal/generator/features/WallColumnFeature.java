package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.derpcraft.liminal.config.FeatureSpec;

import java.util.Random;

/**
 * Wall column feature: vertical columns of a material (typically iron bars,
 * simulating exposed pipes) rising against the inner face of room walls.
 *
 * <p>The roll only happens for blocks adjacent to a wall, so the shared
 * feature random is untouched elsewhere.</p>
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code material} &ndash; block to stack (default {@code IRON_BARS})</li>
 *   <li>{@code chance} &ndash; probability per wall-adjacent block (default {@code 0.03})</li>
 *   <li>{@code max-height} &ndash; maximum column height (default {@code 4});
 *       actual height is uniform in {@code 2..max-height}</li>
 * </ul>
 */
public final class WallColumnFeature implements PerBlockFeature {

    @Override
    public String type() {
        return "wall-columns";
    }

    @Override
    public void apply(FeatureContext context, FeatureSpec spec, Random random) {
        boolean nearWall = context.globalX() == context.layout().roomStartX() + 1
                || context.globalX() == context.layout().roomEndX() - 2
                || context.globalZ() == context.layout().roomStartZ() + 1
                || context.globalZ() == context.layout().roomEndZ() - 2;
        if (!nearWall) return;

        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.03;
        if (random.nextDouble() >= chance) return;

        Material material = spec.getMaterial() != null ? spec.getMaterial() : Material.IRON_BARS;
        int maxHeight = spec.getInt("max-height", 4);
        int height = 2 + random.nextInt(maxHeight - 1);
        for (int i = 0; i < height; i++) {
            int y = context.floorY() + 1 + i;
            if (y < context.ceilingY()) {
                context.chunkData().setBlock(context.localX(), y, context.localZ(), material);
            }
        }
    }
}
