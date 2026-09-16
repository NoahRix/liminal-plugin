package org.derpcraft.liminal.config;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class holding the configuration parameters for a single Liminal level.
 *
 * <p>Each level occupies a vertical slice of the world defined by {@link #minY} and
 * {@link #maxY}. Within that slice, rooms are generated using the configured materials,
 * dimensions, and lighting parameters.</p>
 *
 * <h2>Level layout</h2>
 * <pre>
 *   maxY  ─────────────────────  top of level's Y range
 *           (void / next level above)
 *   ───────────────────────────
 *           ceiling slab (ceilingMaterial)
 *           air space (ceilingHeight blocks)
 *           light blocks embedded in ceiling
 *           walls (wallMaterial) with doorways
 *   ───────────────────────────
 *           floor slab (floorMaterial)
 *           sub-floor space (floorOffset blocks, typically 2)
 *   ───────────────────────────
 *   minY  ─────────────────────  bottom of level's Y range
 * </pre>
 *
 * <p>This class is a plain data holder. The actual generation logic lives in the
 * {@link org.derpcraft.liminal.generator.levels.LiminalLevel} subclasses.</p>
 *
 * @see org.derpcraft.liminal.generator.levels.LiminalLevel
 * @see org.derpcraft.liminal.config.LiminalConfig
 */
public class LevelConfig {

    /** Unique identifier for this level (e.g. "level0", "level1"). */
    private String id;

    /** Cached hash code of the level ID, used as a seed component for room generation. */
    private int idHashCode;

    /** Whether this level is enabled and should be generated. */
    private boolean enabled;

    /** Display name shown to players (e.g. "Level 0 - The Lobby"). */
    private String name;

    /** Minimum Y coordinate of this level's vertical slice (inclusive). */
    private int minY;

    /** Maximum Y coordinate of this level's vertical slice (exclusive). */
    private int maxY;

    /**
     * Inner radius of this level's ring around the world spawn, in blocks (inclusive).
     * The level generates in every chunk whose centre lies at least this far from spawn.
     */
    private int minRadius;

    /**
     * Outer radius of this level's ring around the world spawn, in blocks (exclusive).
     * A negative value means the ring extends to infinity (the level fills everything
     * beyond {@link #minRadius}).
     */
    private int maxRadius;

    /**
     * Vertical offset applied to this level's entire band, in blocks. Levels step
     * upward as the rings expand outward (e.g. 0, 4, 8, 12), and the transition
     * zones between rings ramp smoothly between the two elevations.
     */
    private int elevationStep;

    /**
     * Wall materials for the transition zone ramping into this level, ordered from
     * the previous level's material to this level's material. Empty = derive a
     * two-material gradient from the two levels' wall materials.
     */
    private List<Material> wallGradient = new ArrayList<>();

    /** Floor materials for this level's inbound transition gradient (as above). */
    private List<Material> floorGradient = new ArrayList<>();

    /** Ceiling materials for this level's inbound transition gradient (as above). */
    private List<Material> ceilingGradient = new ArrayList<>();

    /**
     * Whether this level features a rail network. When true, the inbound
     * transition corridor lays powered rail onto its sloped walkway so carts
     * can climb between the two elevations.
     */
    private boolean rails;

    /**
     * Height of the air space between the floor slab and ceiling slab, in blocks.
     * This determines how tall the rooms feel.
     */
    private int ceilingHeight;

    /** Minimum width (X axis) of generated rooms, in blocks. */
    private int roomMinWidth;

    /** Maximum width (X axis) of generated rooms, in blocks. */
    private int roomMaxWidth;

    /** Minimum length (Z axis) of generated rooms, in blocks. */
    private int roomMinLength;

    /** Maximum length (Z axis) of generated rooms, in blocks. */
    private int roomMaxLength;

    /** Material used for room walls. */
    private Material wallMaterial;

    /** Material used for the floor slab. */
    private Material floorMaterial;

    /** Material used for the ceiling slab. */
    private Material ceilingMaterial;

    /** Material used for ceiling-mounted light blocks. */
    private Material lightMaterial;

    /**
     * Spacing between ceiling lights in blocks. Lights are placed at positions where
     * both global X and Z are divisible by this value.
     */
    private int lightSpacing;

    /**
     * Lighting style: {@code ceiling} places light blocks in the ceiling slab,
     * {@code floor-torch} stands torches on the floor.
     */
    private String lightStyle = "ceiling";

    /** Probability (0.0&ndash;1.0) that a placed light flickers; 0 disables flicker. */
    private double lightFlickerChance;

    /**
     * Floor-torch style only: radius (in blocks) around rail lines where
     * decorative torches are skipped to keep the tracks dark.
     */
    private int lightDarkRadius;

    /**
     * Feature list driving the level's decoration (hanging decor, floor pools,
     * wall columns, rail networks, ...). The engine dispatches each entry to a
     * registered feature handler by {@code type}.
     */
    private List<FeatureSpec> features = new ArrayList<>();

    /**
     * Number of floors to stack within the level's Y range. Floors sit one
     * ceiling-slab above each other; 1 is a single-floor level.
     */
    private int floors = 1;

    /** Layout algorithm used by the level (rooms, or parking-garage). */
    private String layoutMode = "rooms";

    /** Vertical offset from the floor base to the walking surface (sub-floor depth). */
    private int floorOffset = 1;

    /** Height of doorway openings in blocks (measured from the floor surface). */
    private int doorwayHeight = 2;

    /** Stairwell settings for multi-floor levels; {@code null} disables stairwells. */
    private StairwellSpec stairwell;

    /**
     * Probability (0.0&ndash;1.0) that a rail cell is left empty (broken track).
     * Only used by rail-based levels; 0 disables broken track entirely.
     */
    private double brokenTrackChance;

    /**
     * Probability (0.0&ndash;1.0) that a cobweb is placed adjacent to a rail.
     * Cobwebs never occupy a rail cell or the column above it.
     */
    private double cobwebChance;

    /**
     * Probability (0.0&ndash;1.0) that a rail chunk spawns ghost minecarts
     * (empty carts that roam the powered network on their own).
     */
    private double ghostCartChance;

    /** Whether ambient loot containers spawn on this level's floors. */
    private boolean lootEnabled;

    /** Probability (0.0&ndash;1.0) per floor block that a loot container spawns. */
    private double lootChance;

    /** Container material for loot: {@code CHEST} or {@code BARREL}. */
    private String lootContainer;

    /** Loot-table entries rolled into spawned containers. */
    private List<LootEntry> lootEntries = new ArrayList<>();

    /** Whether environmental hazards spawn on this level's floors. */
    private boolean hazardsEnabled;

    /** Probability (0.0&ndash;1.0) per floor block that a hazard spawns. */
    private double hazardChance;

    /**
     * Allowed hazard types (WATER, FIRE, COBWEB). An empty list means all
     * types are permitted.
     */
    private List<Material> hazardTypes = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    /** Returns the unique level identifier. */
    public String getId() { return id; }

    /**
     * Sets the level identifier and caches its hash code for seed computation.
     *
     * @param id the level ID (e.g. "level0")
     */
    public void setId(String id) { this.id = id; this.idHashCode = id.hashCode(); }

    /** Returns the cached hash code of the level ID. */
    public int getIdHashCode() { return idHashCode; }

    /** Returns whether this level is enabled. */
    public boolean isEnabled() { return enabled; }

    /** Sets whether this level is enabled. */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Returns the display name of this level. */
    public String getName() { return name; }

    /** Sets the display name of this level. */
    public void setName(String name) { this.name = name; }

    /** Returns the minimum Y coordinate (inclusive). */
    public int getMinY() { return minY; }

    /** Sets the minimum Y coordinate (inclusive). */
    public void setMinY(int minY) { this.minY = minY; }

    /** Returns the maximum Y coordinate (exclusive). */
    public int getMaxY() { return maxY; }

    /** Sets the maximum Y coordinate (exclusive). */
    public void setMaxY(int maxY) { this.maxY = maxY; }

    /** Returns the inner ring radius in blocks (inclusive, measured from world spawn). */
    public int getMinRadius() { return minRadius; }

    /** Sets the inner ring radius in blocks (inclusive, measured from world spawn). */
    public void setMinRadius(int minRadius) { this.minRadius = minRadius; }

    /** Returns the outer ring radius in blocks (exclusive), or a negative value for unlimited. */
    public int getMaxRadius() { return maxRadius; }

    /** Sets the outer ring radius in blocks (exclusive); a negative value means unlimited. */
    public void setMaxRadius(int maxRadius) { this.maxRadius = maxRadius; }

    /** Returns the vertical offset (in blocks) applied to this level's whole band. */
    public int getElevationStep() { return elevationStep; }

    /** Sets the vertical offset (in blocks) applied to this level's whole band. */
    public void setElevationStep(int elevationStep) { this.elevationStep = elevationStep; }

    /** Returns the wall material gradient for the inbound transition (empty = derive from levels). */
    public List<Material> getWallGradient() { return wallGradient; }

    /** Sets the wall material gradient for the inbound transition. */
    public void setWallGradient(List<Material> wallGradient) { this.wallGradient = wallGradient; }

    /** Returns the floor material gradient for the inbound transition (empty = derive from levels). */
    public List<Material> getFloorGradient() { return floorGradient; }

    /** Sets the floor material gradient for the inbound transition. */
    public void setFloorGradient(List<Material> floorGradient) { this.floorGradient = floorGradient; }

    /** Returns the ceiling material gradient for the inbound transition (empty = derive from levels). */
    public List<Material> getCeilingGradient() { return ceilingGradient; }

    /** Sets the ceiling material gradient for the inbound transition. */
    public void setCeilingGradient(List<Material> ceilingGradient) { this.ceilingGradient = ceilingGradient; }

    /** Returns whether this level has a rail network that should climb its inbound ramp. */
    public boolean isRails() { return rails; }

    /** Sets whether this level has a rail network that should climb its inbound ramp. */
    public void setRails(boolean rails) { this.rails = rails; }

    /**
     * Returns the world Y of this level's ground-floor surface block, including
     * the level's elevation step.
     *
     * @return {@code minY + elevationStep + floorOffset}
     */
    public int getFloorSurfaceY() {
        return minY + elevationStep + floorOffset;
    }

    /** Returns the ceiling height in blocks. */
    public int getCeilingHeight() { return ceilingHeight; }

    /** Sets the ceiling height in blocks. */
    public void setCeilingHeight(int ceilingHeight) { this.ceilingHeight = ceilingHeight; }

    /** Returns the minimum room width in blocks. */
    public int getRoomMinWidth() { return roomMinWidth; }

    /** Sets the minimum room width in blocks. */
    public void setRoomMinWidth(int roomMinWidth) { this.roomMinWidth = roomMinWidth; }

    /** Returns the maximum room width in blocks. */
    public int getRoomMaxWidth() { return roomMaxWidth; }

    /** Sets the maximum room width in blocks. */
    public void setRoomMaxWidth(int roomMaxWidth) { this.roomMaxWidth = roomMaxWidth; }

    /** Returns the minimum room length in blocks. */
    public int getRoomMinLength() { return roomMinLength; }

    /** Sets the minimum room length in blocks. */
    public void setRoomMinLength(int roomMinLength) { this.roomMinLength = roomMinLength; }

    /** Returns the maximum room length in blocks. */
    public int getRoomMaxLength() { return roomMaxLength; }

    /** Sets the maximum room length in blocks. */
    public void setRoomMaxLength(int roomMaxLength) { this.roomMaxLength = roomMaxLength; }

    /** Returns the wall material. */
    public Material getWallMaterial() { return wallMaterial; }

    /** Sets the wall material. */
    public void setWallMaterial(Material wallMaterial) { this.wallMaterial = wallMaterial; }

    /** Returns the floor material. */
    public Material getFloorMaterial() { return floorMaterial; }

    /** Sets the floor material. */
    public void setFloorMaterial(Material floorMaterial) { this.floorMaterial = floorMaterial; }

    /** Returns the ceiling material. */
    public Material getCeilingMaterial() { return ceilingMaterial; }

    /** Sets the ceiling material. */
    public void setCeilingMaterial(Material ceilingMaterial) { this.ceilingMaterial = ceilingMaterial; }

    /** Returns the light material. */
    public Material getLightMaterial() { return lightMaterial; }

    /** Sets the light material. */
    public void setLightMaterial(Material lightMaterial) { this.lightMaterial = lightMaterial; }

    /** Returns the light spacing in blocks. */
    public int getLightSpacing() { return lightSpacing; }

    /** Sets the light spacing in blocks. */
    public void setLightSpacing(int lightSpacing) { this.lightSpacing = lightSpacing; }

    /** Returns the lighting style ({@code ceiling} or {@code floor-torch}). */
    public String getLightStyle() { return lightStyle; }

    /** Sets the lighting style ({@code ceiling} or {@code floor-torch}). */
    public void setLightStyle(String lightStyle) { this.lightStyle = lightStyle; }

    /** Returns the light flicker probability (0 disables flicker). */
    public double getLightFlickerChance() { return lightFlickerChance; }

    /** Sets the light flicker probability (0 disables flicker). */
    public void setLightFlickerChance(double lightFlickerChance) { this.lightFlickerChance = lightFlickerChance; }

    /** Returns the rail-adjacent torch exclusion radius (floor-torch style). */
    public int getLightDarkRadius() { return lightDarkRadius; }

    /** Sets the rail-adjacent torch exclusion radius (floor-torch style). */
    public void setLightDarkRadius(int lightDarkRadius) { this.lightDarkRadius = lightDarkRadius; }

    /** Returns the level's feature list. */
    public List<FeatureSpec> getFeatures() { return features; }

    /** Sets the level's feature list. */
    public void setFeatures(List<FeatureSpec> features) { this.features = features; }

    /**
     * Returns the first feature of the given type, or {@code null} when the
     * level does not configure it.
     *
     * @param type the feature type key
     * @return the feature spec, or {@code null}
     */
    public FeatureSpec getFeature(String type) {
        for (FeatureSpec spec : features) {
            if (spec.getType().equals(type)) return spec;
        }
        return null;
    }

    /** Returns the number of stacked floors (1 = single-floor level). */
    public int getFloors() { return floors; }

    /** Sets the number of stacked floors (1 = single-floor level). */
    public void setFloors(int floors) { this.floors = floors; }

    /** Returns the configured layout algorithm. */
    public String getLayoutMode() { return layoutMode; }

    /** Sets the configured layout algorithm. */
    public void setLayoutMode(String layoutMode) { this.layoutMode = layoutMode; }

    /** Returns the sub-floor depth (offset from floor base to walking surface). */
    public int getFloorOffset() { return floorOffset; }

    /** Sets the sub-floor depth (offset from floor base to walking surface). */
    public void setFloorOffset(int floorOffset) { this.floorOffset = floorOffset; }

    /** Returns the doorway height in blocks. */
    public int getDoorwayHeight() { return doorwayHeight; }

    /** Sets the doorway height in blocks. */
    public void setDoorwayHeight(int doorwayHeight) { this.doorwayHeight = doorwayHeight; }

    /** Returns the stairwell settings, or {@code null} when stairwells are disabled. */
    public StairwellSpec getStairwell() { return stairwell; }

    /** Sets the stairwell settings ({@code null} disables stairwells). */
    public void setStairwell(StairwellSpec stairwell) { this.stairwell = stairwell; }

    /** Returns the broken-track probability (0 disables broken track). */
    public double getBrokenTrackChance() { return brokenTrackChance; }

    /** Sets the broken-track probability (0 disables broken track). */
    public void setBrokenTrackChance(double brokenTrackChance) { this.brokenTrackChance = brokenTrackChance; }

    /** Returns the rail-adjacent cobweb probability. */
    public double getCobwebChance() { return cobwebChance; }

    /** Sets the rail-adjacent cobweb probability. */
    public void setCobwebChance(double cobwebChance) { this.cobwebChance = cobwebChance; }

    /** Returns the ghost minecart spawn probability per rail chunk. */
    public double getGhostCartChance() { return ghostCartChance; }

    /** Sets the ghost minecart spawn probability per rail chunk. */
    public void setGhostCartChance(double ghostCartChance) { this.ghostCartChance = ghostCartChance; }

    /** Returns whether ambient loot containers spawn on this level. */
    public boolean isLootEnabled() { return lootEnabled; }

    /** Sets whether ambient loot containers spawn on this level. */
    public void setLootEnabled(boolean lootEnabled) { this.lootEnabled = lootEnabled; }

    /** Returns the per-floor-block loot container spawn probability. */
    public double getLootChance() { return lootChance; }

    /** Sets the per-floor-block loot container spawn probability. */
    public void setLootChance(double lootChance) { this.lootChance = lootChance; }

    /** Returns the loot container material name ({@code CHEST} or {@code BARREL}). */
    public String getLootContainer() { return lootContainer; }

    /** Sets the loot container material name ({@code CHEST} or {@code BARREL}). */
    public void setLootContainer(String lootContainer) { this.lootContainer = lootContainer; }

    /** Returns the loot-table entries rolled into spawned containers. */
    public List<LootEntry> getLootEntries() { return lootEntries; }

    /** Sets the loot-table entries rolled into spawned containers. */
    public void setLootEntries(List<LootEntry> lootEntries) { this.lootEntries = lootEntries; }

    /** Returns whether environmental hazards spawn on this level. */
    public boolean isHazardsEnabled() { return hazardsEnabled; }

    /** Sets whether environmental hazards spawn on this level. */
    public void setHazardsEnabled(boolean hazardsEnabled) { this.hazardsEnabled = hazardsEnabled; }

    /** Returns the per-floor-block hazard spawn probability. */
    public double getHazardChance() { return hazardChance; }

    /** Sets the per-floor-block hazard spawn probability. */
    public void setHazardChance(double hazardChance) { this.hazardChance = hazardChance; }

    /** Returns the allowed hazard types; empty means all types are permitted. */
    public List<Material> getHazardTypes() { return hazardTypes; }

    /** Sets the allowed hazard types; empty means all types are permitted. */
    public void setHazardTypes(List<Material> hazardTypes) { this.hazardTypes = hazardTypes; }

    /**
     * Returns the total height of this level's vertical slice in blocks.
     *
     * @return {@code maxY - minY}
     */
    public int getHeight() { return maxY - minY; }
}
