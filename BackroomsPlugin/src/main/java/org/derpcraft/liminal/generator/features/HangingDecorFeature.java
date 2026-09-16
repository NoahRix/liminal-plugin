package org.derpcraft.liminal.generator.features;

import org.bukkit.Material;
import org.derpcraft.liminal.config.FeatureSpec;

import java.util.Random;

/**
 * Hanging decoration feature: strands of a material (typically chains) descend
 * from the ceiling inside rooms.
 *
 * <p>Level file parameters:</p>
 * <ul>
 *   <li>{@code material} &ndash; block to hang (default {@code CHAIN})</li>
 *   <li>{@code chance} &ndash; probability per block (default {@code 0.15})</li>
 *   <li>{@code max-length} &ndash; maximum strand length in blocks (default {@code 3});
 *       actual length is uniform in {@code 1..max-length}</li>
 * </ul>
 */
public final class HangingDecorFeature implements PerBlockFeature {

    @Override
    public String type() {
        return "hanging-decor";
    }

    @Override
    public void apply(FeatureContext context, FeatureSpec spec, Random random) {
        double chance = spec.getChance() >= 0 ? spec.getChance() : 0.15;
        if (random.nextDouble() >= chance) return;

        Material material = spec.getMaterial() != null ? spec.getMaterial() : Material.CHAIN;
        int maxLength = spec.getInt("max-length", 3);
        int length = 1 + random.nextInt(maxLength);
        for (int i = 0; i < length; i++) {
            int y = context.ceilingY() - 1 - i;
            if (y > context.floorY() + 1) {
                context.chunkData().setBlock(context.localX(), y, context.localZ(), material);
            }
        }
    }
}
