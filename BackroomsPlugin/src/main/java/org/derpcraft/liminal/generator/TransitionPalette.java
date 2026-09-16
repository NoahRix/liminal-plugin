package org.derpcraft.liminal.generator;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Colour palette for automatic transition gradients.
 *
 * <p>Each entry maps a buildable block material to its representative RGB colour
 * (the same palette the web viewer uses, so in-game transitions and the 3D debug
 * viewer stay visually consistent). Given any two materials, the palette can
 * produce a gradient between them by repeatedly taking the nearest-material to
 * an interpolated colour &mdash; no per-level gradient configuration required,
 * and any level pairing works automatically.</p>
 *
 * <h2>How a gradient is built</h2>
 * <ol>
 *   <li>Look up the two endpoint materials' colours.</li>
 *   <li>Sample the interpolated colour at {@link #STEPS} evenly spaced points.</li>
 *   <li>For each sample, pick the palette material whose colour is nearest
 *       (squared Euclidean distance in RGB).</li>
 * </ol>
 *
 * <p>Only <b>crude, solid building blocks</b> take part in gradients: full
 * opaque cubes, free of ores, ores-decor (iron bars, grates, glass), light
 * emitters, falling blocks, blocks with faces, and organic/technical blocks.
 * The full colour table is kept intact because the viewer renders all world
 * blocks; {@link #isGradientBlock} filters what the generator may place.
 * Gradients are memoised per material pair, so the nearest-material search
 * only runs once per pair per session.</p>
 */
public final class TransitionPalette {

    /** Number of gradient samples between the two endpoint materials. */
    public static final int STEPS = 64;

    /**
     * Full-cube materials explicitly banned from gradients despite being
     * technically solid: ores, glowing blocks, blocks with faces, organic and
     * technical blocks &mdash; anything that reads as decoration or resource,
     * not crude construction material.
     */
    private static final Set<String> EXCLUDED = Set.of(
            // Ores and ore-like blocks
            "coal_ore", "deepslate_coal_ore", "deepslate_diamond_ore", "deepslate_gold_ore",
            "deepslate_iron_ore", "diamond_ore", "emerald_ore", "gold_ore", "iron_ore",
            "lapis_ore", "redstone_ore", "gilded_blackstone", "ancient_debris",
            // Light emitters
            "glowstone", "crying_obsidian",
            // Blocks with faces / technical / organic
            "hay_block", "dried_kelp_block", "bookshelf", "barrel", "crafting_table",
            "grass_block", "podzol", "netherite_block");

    /** Material name (Bukkit enum name) to 0xRRGGBB colour. */
    private static final Map<String, Integer> P = new HashMap<>();

    /** Material to colour (resolved once; entries with no valid Material are skipped). */
    private static final List<Entry> PALETTE = new ArrayList<>();

    /** Memoised gradients keyed by "from|to". */
    private static final Map<String, Material[]> GRADIENTS = new HashMap<>();

    static {
        P.put("acacia_log", 0x676157);
        P.put("acacia_planks", 0xBA6337);
        P.put("ancient_debris", 0x5C4033);
        P.put("andesite", 0x84888E);
        P.put("azalea_leaves", 0x5C8136);
        P.put("bamboo_planks", 0xC8B659);
        P.put("barrel", 0x866843);
        P.put("basalt", 0x4B4B4F);
        P.put("birch_leaves", 0x6EA44E);
        P.put("birch_log", 0xD7D3C8);
        P.put("birch_planks", 0xD7CB8D);
        P.put("black_concrete", 0x080A0F);
        P.put("black_terracotta", 0x251610);
        P.put("blackstone", 0x2A252D);
        P.put("blue_concrete", 0x2C2E90);
        P.put("bookshelf", 0x665133);
        P.put("bricks", 0x96584C);
        P.put("brown_concrete", 0x724728);
        P.put("brown_terracotta", 0x4D3324);
        P.put("calcite", 0xE0DED4);
        P.put("cherry_log", 0x33232C);
        P.put("cherry_planks", 0xE2B0AA);
        P.put("chiseled_copper", 0xC16D51);
        P.put("chiseled_polished_blackstone", 0x2B292D);
        P.put("chiseled_stone_bricks", 0x7C7C7C);
        P.put("clay", 0xA4A8B8);
        P.put("coal_ore", 0x5E5E5E);
        P.put("coarse_dirt", 0x7B4F28);
        P.put("cobbled_deepslate", 0x4A4A4E);
        P.put("cobblestone", 0x7D7D7D);
        P.put("copper_block", 0xC16D51);
        P.put("copper_grate", 0xC16D51);
        P.put("cracked_polished_blackstone_bricks", 0x27252A);
        P.put("cracked_stone_bricks", 0x797677);
        P.put("crafting_table", 0x79553A);
        P.put("crimson_planks", 0x7E3A56);
        P.put("crying_obsidian", 0x2C0A54);
        P.put("cut_red_sandstone", 0xAD6033);
        P.put("cut_sandstone", 0xD9CBA4);
        P.put("cyan_concrete", 0x167187);
        P.put("dark_oak_leaves", 0x396E22);
        P.put("dark_oak_log", 0x3A2A17);
        P.put("dark_oak_planks", 0x4F3218);
        P.put("deepslate", 0x4C4C51);
        P.put("deepslate_bricks", 0x44444A);
        P.put("deepslate_coal_ore", 0x3C3C3E);
        P.put("deepslate_diamond_ore", 0x546163);
        P.put("deepslate_gold_ore", 0x544C3D);
        P.put("deepslate_iron_ore", 0x66605E);
        P.put("deepslate_tiles", 0x36363A);
        P.put("diamond_ore", 0x7F9C9C);
        P.put("diorite", 0xC8C8CE);
        P.put("dirt", 0x8B5A2B);
        P.put("dried_kelp_block", 0x33463B);
        P.put("dripstone_block", 0x86685D);
        P.put("emerald_ore", 0x6D9375);
        P.put("end_stone", 0xD6DCB0);
        P.put("end_stone_bricks", 0xDAE0B3);
        P.put("exposed_copper", 0xA97765);
        P.put("gilded_blackstone", 0x2A252D);
        P.put("glowstone", 0xF9D49C);
        P.put("gold_ore", 0x8A7C50);
        P.put("granite", 0x9A6B58);
        P.put("grass_block", 0x7CBD4B);
        P.put("gravel", 0x837F7F);
        P.put("gray_concrete", 0x373A3E);
        P.put("green_concrete", 0x495B24);
        P.put("hay_block", 0xA68F25);
        P.put("iron_bars", 0x9B9B9B);
        P.put("iron_block", 0xD8D8D8);
        P.put("iron_ore", 0xA08B7E);
        P.put("jungle_leaves", 0x3E9E2C);
        P.put("jungle_log", 0x55440F);
        P.put("jungle_planks", 0xB88764);
        P.put("lapis_ore", 0x5A7088);
        P.put("light_blue_concrete", 0x2489C7);
        P.put("light_blue_terracotta", 0x717E8D);
        P.put("light_gray_concrete", 0xC7CFD0);
        P.put("lime_concrete", 0x5EA919);
        P.put("magenta_concrete", 0xAB34AB);
        P.put("mangrove_planks", 0x77442C);
        P.put("moss_block", 0x5A7247);
        P.put("mossy_cobblestone", 0x6D7758);
        P.put("mossy_stone_bricks", 0x757F6A);
        P.put("mud", 0x3C3A3D);
        P.put("mud_bricks", 0x8B6F5B);
        P.put("nether_bricks", 0x2C161A);
        P.put("netherite_block", 0x443B41);
        P.put("netherrack", 0x712F2D);
        P.put("oak_leaves", 0x48962F);
        P.put("oak_log", 0x6F5433);
        P.put("oak_planks", 0xB8945F);
        P.put("obsidian", 0x120B1E);
        P.put("orange_concrete", 0xE06101);
        P.put("orange_terracotta", 0xA25426);
        P.put("oxidized_copper", 0x4FA582);
        P.put("oxidized_copper_grate", 0x4FA582);
        P.put("pink_concrete", 0xD5658F);
        P.put("podzol", 0x5D3F1F);
        P.put("polished_andesite", 0x878B91);
        P.put("polished_basalt", 0x5A5A5F);
        P.put("polished_blackstone", 0x2E2C30);
        P.put("polished_blackstone_bricks", 0x252327);
        P.put("polished_deepslate", 0x48484D);
        P.put("polished_diorite", 0xC9CDD4);
        P.put("polished_granite", 0x9D6D5D);
        P.put("purple_concrete", 0x64209C);
        P.put("purpur_block", 0xA678A6);
        P.put("quartz_block", 0xE8E5E0);
        P.put("red_concrete", 0x8E2121);
        P.put("red_sand", 0xBD6F37);
        P.put("red_sandstone", 0xB06335);
        P.put("red_terracotta", 0x8E3C2E);
        P.put("redstone_ore", 0x7E4A44);
        P.put("reinforced_deepslate", 0x3F4348);
        P.put("sand", 0xDBD3A0);
        P.put("sandstone", 0xDCD0A2);
        P.put("sculk", 0x0D2B33);
        P.put("smooth_basalt", 0x48484C);
        P.put("smooth_quartz", 0xE8E5E0);
        P.put("smooth_red_sandstone", 0xB06335);
        P.put("smooth_sandstone", 0xDCD0A2);
        P.put("smooth_stone", 0x9C9C9C);
        P.put("snow", 0xF0FAFA);
        P.put("spruce_leaves", 0x46683A);
        P.put("spruce_log", 0x3B2A17);
        P.put("spruce_planks", 0x7A5A35);
        P.put("stone", 0x7D7D7D);
        P.put("stone_bricks", 0x7A7A7A);
        P.put("terracotta", 0x985E43);
        P.put("tinted_glass", 0x2F2A33);
        P.put("tuff", 0x6D6E68);
        P.put("warped_planks", 0x2B6963);
        P.put("weathered_copper", 0x6A9D72);
        P.put("white_concrete", 0xE6E9E9);
        P.put("white_terracotta", 0xD1B1A1);
        P.put("yellow_concrete", 0xF1AF15);
        P.put("yellow_terracotta", 0xB98423);

        for (Map.Entry<String, Integer> e : P.entrySet()) {
            Material mat = Material.matchMaterial(e.getKey().toUpperCase(Locale.ROOT));
            if (mat == null || !isGradientBlock(mat)) continue;
            int rgb = e.getValue();
            PALETTE.add(new Entry(mat, rgb));
        }
    }

    /**
     * True when the material may appear inside automatic gradients: a solid,
     * full, opaque cube without gravity, and not on the decoration exclusion
     * list. Structural filters (occluding cube, no gravity) automatically rule
     * out bars, panes, glass, leaves, snow layers and falling blocks; the
     * explicit list rules out ores and other full-cube decoration.
     *
     * @param mat the material to test
     * @return whether the material is a crude, solid building block
     */
    private static boolean isGradientBlock(Material mat) {
        if (!mat.isBlock() || !mat.isOccluding() || mat.hasGravity()) return false;
        return !EXCLUDED.contains(mat.name().toLowerCase(Locale.ROOT));
    }

    private TransitionPalette() {
    }

    /** A palette entry: block material + representative RGB colour. */
    private record Entry(Material material, int rgb) {
    }

    /**
     * Samples the automatic gradient between two materials.
     *
     * @param from  the material at progress 0
     * @param to    the material at progress 1
     * @param t     progress between the two (clamped to 0..1)
     * @param noise per-block dithering value in 0..1, used to blend between the
     *              two nearest gradient samples so transitions look organic
     * @return the material for this position
     */
    public static Material sample(Material from, Material to, double t, double noise) {
        Material[] gradient = gradient(from, to);
        double pos = Math.max(0.0, Math.min(1.0, t)) * (gradient.length - 1);
        int index = (int) pos;
        if (index >= gradient.length - 1) return gradient[gradient.length - 1];
        return noise < (pos - index) ? gradient[index + 1] : gradient[index];
    }

    /**
     * Builds (and memoises) the material gradient between two materials:
     * {@link #STEPS} nearest-material samples of the linear colour interpolation
     * between the two endpoints.
     */
    public static Material[] gradient(Material from, Material to) {
        String key = from.name() + '|' + to.name();
        Material[] cached = GRADIENTS.get(key);
        if (cached != null) return cached;

        int fromRgb = colourOf(from);
        int toRgb = colourOf(to);
        Material[] gradient = new Material[STEPS + 1];
        for (int i = 0; i <= STEPS; i++) {
            double t = (double) i / STEPS;
            int r = (int) Math.round(red(fromRgb) + (red(toRgb) - red(fromRgb)) * t);
            int g = (int) Math.round(green(fromRgb) + (green(toRgb) - green(fromRgb)) * t);
            int b = (int) Math.round(blue(fromRgb) + (blue(toRgb) - blue(fromRgb)) * t);
            gradient[i] = nearest((r << 16) | (g << 8) | b);
        }
        GRADIENTS.put(key, gradient);
        return gradient;
    }

    /** Finds the palette material whose colour is nearest to the given RGB. */
    private static Material nearest(int rgb) {
        Material best = Material.STONE;
        long bestDist = Long.MAX_VALUE;
        for (Entry entry : PALETTE) {
            long dr = red(entry.rgb()) - red(rgb);
            long dg = green(entry.rgb()) - green(rgb);
            long db = blue(entry.rgb()) - blue(rgb);
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = entry.material();
            }
        }
        return best;
    }

    /** Exact colour for an endpoint material (falls back to mid-grey if unknown). */
    private static int colourOf(Material mat) {
        for (Entry entry : PALETTE) {
            if (entry.material() == mat) return entry.rgb();
        }
        return 0x808080;
    }

    private static int red(int rgb) { return (rgb >> 16) & 0xFF; }

    private static int green(int rgb) { return (rgb >> 8) & 0xFF; }

    private static int blue(int rgb) { return rgb & 0xFF; }
}
