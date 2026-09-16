package org.derpcraft.liminal.generator.features;

import org.derpcraft.liminal.config.FeatureSpec;

import java.util.Random;

/**
 * A feature that decorates individual room-interior blocks.
 *
 * <p>Per-block features are executed by the level engine in a single
 * interleaved pass over the chunk: for every interior block, each configured
 * feature rolls once against the <b>shared</b> feature random, in the order
 * the features appear in the level file. This keeps deterministic output
 * stable regardless of how many features a level stacks.</p>
 */
public interface PerBlockFeature {

    /**
     * Returns the feature type key this handler serves (the {@code type}
     * value in the level file's {@code features:} list).
     *
     * @return the feature type key
     */
    String type();

    /**
     * Applies the feature to a single block.
     *
     * <p>Implementations must consume the shared random in a fixed order and
     * may only write blocks that belong to this feature.</p>
     *
     * @param context the per-block context
     * @param spec    the feature configuration
     * @param random  the shared per-chunk feature random
     */
    void apply(FeatureContext context, FeatureSpec spec, Random random);
}
