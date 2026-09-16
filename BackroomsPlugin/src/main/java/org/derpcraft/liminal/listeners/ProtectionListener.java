package org.derpcraft.liminal.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.derpcraft.liminal.LiminalPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Gatekeeps building and interaction inside the Liminal world via permission
 * nodes intended to be managed through LuckPerms (or any permission provider).
 *
 * <h2>Permission nodes</h2>
 * <table>
 *   <tr><td>Node</td><td>Controls</td></tr>
 *   <tr><td>{@code liminal.build}</td><td>Breaking and placing blocks, buckets,
 *       flint &amp; steel, tilling/path-making, log stripping, destroying
 *       vehicles, item frames, paintings and armor stands</td></tr>
 *   <tr><td>{@code liminal.interact.container}</td><td>Opening containers
 *       (chests, barrels, furnaces, shulker boxes, anvils, crafting and
 *       utility tables, jukeboxes, lecterns, beacons, ...)</td></tr>
 *   <tr><td>{@code liminal.interact.door}</td><td>Doors, trapdoors, fence
 *       gates, buttons, levers, bells, beds, note blocks, cakes</td></tr>
 *   <tr><td>{@code liminal.interact.entity}</td><td>Riding and manipulating
 *       entities: minecarts (including ghost carts), boats, item frames,
 *       armor stands, animals, villagers</td></tr>
 *   <tr><td>{@code liminal.interact}</td><td>Parent node &mdash; grants all
 *       three interact sub-nodes at once (also honoured directly in code, so
 *       granting the parent works even without child resolution)</td></tr>
 * </table>
 *
 * <p>All checks run at {@link EventPriority#LOWEST} with
 * {@code ignoreCancelled = true} so downstream listeners (flicker light
 * bookkeeping, ghost cart spawning) never observe denied actions. Denials
 * surface as an action-bar message.</p>
 */
public class ProtectionListener implements Listener {

    /** Permission guarding world modification. */
    public static final String PERM_BUILD = "liminal.build";

    /** Parent permission granting every interact sub-node. */
    public static final String PERM_INTERACT = "liminal.interact";

    /** Permission for opening container blocks. */
    public static final String PERM_CONTAINER = "liminal.interact.container";

    /** Permission for using doors, buttons and other switch blocks. */
    public static final String PERM_DOOR = "liminal.interact.door";

    /** Permission for interacting with entities. */
    public static final String PERM_ENTITY = "liminal.interact.entity";

    /** Container blocks gated behind {@link #PERM_CONTAINER}. */
    private static final Set<String> CONTAINER_BLOCKS = Set.of(
            "CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL", "FURNACE",
            "BLAST_FURNACE", "SMOKER", "HOPPER", "DROPPER", "DISPENSER",
            "JUKEBOX", "LECTERN", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL",
            "ENCHANTING_TABLE", "BEACON", "CRAFTING_TABLE", "LOOM",
            "STONECUTTER", "GRINDSTONE", "SMITHING_TABLE", "CARTOGRAPHY_TABLE",
            "BREWING_STAND", "COMPOSTER", "DECORATED_POT", "CRAFTER");

    /** Usable/switch blocks gated behind {@link #PERM_DOOR}. */
    private static final Set<String> USABLE_BLOCKS = Set.of(
            "LEVER", "BELL", "NOTE_BLOCK", "CAKE", "REPEATER", "COMPARATOR",
            "DAYLIGHT_DETECTOR", "RESPAWN_ANCHOR", "LODESTONE");

    /** Items whose right-click use modifies the world (gated behind build). */
    private static final Set<String> WORLD_EDIT_ITEMS = Set.of(
            "FLINT_AND_STEEL", "SHEARS");

    private static final String DENY_BUILD = "You don't have permission to build here.";
    private static final String DENY_INTERACT = "You don't have permission to interact with that.";

    private final LiminalPlugin plugin;
    private final String worldName;

    /**
     * Constructs a new protection listener.
     *
     * @param plugin the owning plugin instance
     */
    public ProtectionListener(LiminalPlugin plugin) {
        this.plugin = plugin;
        this.worldName = plugin.getLiminalConfig().getLiminalWorldName();
    }

    // -------------------------------------------------------------------------
    // Build: blocks and world-modifying item uses
    // -------------------------------------------------------------------------

    /** Requires {@code liminal.build} for breaking blocks. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!inLiminal(player) || hasBuild(player)) return;
        event.setCancelled(true);
        deny(player, DENY_BUILD);
    }

    /** Requires {@code liminal.build} for placing blocks. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!inLiminal(player) || hasBuild(player)) return;
        event.setCancelled(true);
        deny(player, DENY_BUILD);
    }

    /** Requires {@code liminal.build} for emptying buckets (placing liquids). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!inLiminal(event.getPlayer()) || hasBuild(event.getPlayer())) return;
        event.setCancelled(true);
        deny(event.getPlayer(), DENY_BUILD);
    }

    /** Requires {@code liminal.build} for filling buckets. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!inLiminal(event.getPlayer()) || hasBuild(event.getPlayer())) return;
        event.setCancelled(true);
        deny(event.getPlayer(), DENY_BUILD);
    }

    /** Requires {@code liminal.build} for destroying vehicles (minecarts, boats). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player player)) return;
        if (!inLiminal(player) || hasBuild(player)) return;
        event.setCancelled(true);
        deny(player, DENY_BUILD);
    }

    /** Requires {@code liminal.build} for destroying item frames, paintings, armor stands and carts. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlaceableDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!inLiminal(player) || hasBuild(player)) return;

        String type = event.getEntityType().name();
        if (type.equals("ITEM_FRAME") || type.equals("GLOW_ITEM_FRAME")
                || type.equals("PAINTING") || type.equals("ARMOR_STAND")
                || type.endsWith("MINECART")) {
            event.setCancelled(true);
            deny(player, DENY_BUILD);
        }
    }

    // -------------------------------------------------------------------------
    // Interact: containers, doors, entities
    // -------------------------------------------------------------------------

    /**
     * Routes right-click block interactions: containers need
     * {@code ...container}, doors and switches need {@code ...door}, and
     * world-modifying item uses (flint &amp; steel, hoes, shovels, axes,
     * shears) need {@code liminal.build}. Plain right-clicks stay ungated.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return; // main hand only, one denial per click
        Player player = event.getPlayer();
        if (!inLiminal(player)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        String mat = block.getType().name();

        if (isContainer(mat)) {
            if (!hasContainer(player)) {
                event.setCancelled(true);
                deny(player, DENY_INTERACT);
            }
            return;
        }

        if (isUsableSwitch(mat)) {
            if (!hasDoor(player)) {
                event.setCancelled(true);
                deny(player, DENY_INTERACT);
            }
            return;
        }

        // World-modifying item uses (fire, tilling, paths, log stripping).
        ItemStack item = event.getItem();
        if (item != null && isWorldEditItem(item.getType())) {
            if (!hasBuild(player)) {
                event.setCancelled(true);
                deny(player, DENY_BUILD);
            }
        }
    }

    /** Requires an entity interact permission for right-clicking entities. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!hasEntityAccess(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer(), DENY_INTERACT);
        }
    }

    /** Requires an entity interact permission for armor stand / item frame manipulation. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!hasEntityAccess(event.getPlayer())) {
            event.setCancelled(true);
            deny(event.getPlayer(), DENY_INTERACT);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** True when the player is inside the Liminal world. */
    private boolean inLiminal(Player player) {
        return player.getWorld().getName().equals(worldName);
    }

    /** True when the player may modify the world. */
    private boolean hasBuild(Player player) {
        return player.hasPermission(PERM_BUILD);
    }

    /** True when the player may open containers (parent node grants everything). */
    private boolean hasContainer(Player player) {
        return player.hasPermission(PERM_CONTAINER) || player.hasPermission(PERM_INTERACT);
    }

    /** True when the player may use doors and switches (parent node grants everything). */
    private boolean hasDoor(Player player) {
        return player.hasPermission(PERM_DOOR) || player.hasPermission(PERM_INTERACT);
    }

    /** True when the player may interact with entities (parent node grants everything). */
    private boolean hasEntityAccess(Player player) {
        return player.hasPermission(PERM_ENTITY) || player.hasPermission(PERM_INTERACT);
    }

    /** True when the block material opens an inventory-style container. */
    private static boolean isContainer(String material) {
        return CONTAINER_BLOCKS.contains(material) || material.endsWith("SHULKER_BOX");
    }

    /** True when the block material is a door, gate, button, lever or similar switch. */
    private static boolean isUsableSwitch(String material) {
        return USABLE_BLOCKS.contains(material)
                || material.endsWith("_DOOR")
                || material.endsWith("_TRAPDOOR")
                || material.endsWith("_FENCE_GATE")
                || material.endsWith("_BUTTON")
                || material.endsWith("_BED")
                || material.endsWith("_CANDLE");
    }

    /** True when the held item modifies the world on right-click. */
    private static boolean isWorldEditItem(Material material) {
        String name = material.name();
        return WORLD_EDIT_ITEMS.contains(name)
                || name.endsWith("_HOE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE");
    }

    /** Shows the denial feedback as an action bar message. */
    private void deny(Player player, String message) {
        player.sendActionBar(net.kyori.adventure.text.Component.text(message,
                net.kyori.adventure.text.format.NamedTextColor.RED));
    }
}
