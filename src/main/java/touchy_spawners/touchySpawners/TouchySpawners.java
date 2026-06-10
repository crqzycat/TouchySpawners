package touchy_spawners.touchySpawners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

public final class TouchySpawners extends JavaPlugin implements Listener {

    private NamespacedKey stackKey;

    // Base vanilla spawn count — we multiply this by the stack size
    private static final int BASE_SPAWN_COUNT = 4;

    @Override
    public void onEnable() {
        stackKey = new NamespacedKey(this, "stack_count");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TouchySpawners enabled – Silk Touch + stacking active!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TouchySpawners disabled.");
    }

    // ── BREAKING ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasSilkTouch(tool)) return;

        event.setDropItems(false);
        event.setExpToDrop(0);

        CreatureSpawner cs = (CreatureSpawner) block.getState();
        EntityType spawnedType = cs.getSpawnedType();

        // How many spawners are stacked in this block?
        int existingStack = getBlockStackCount(cs);

        // Check if the player already holds the same spawner type — merge if so
        ItemStack inHand = player.getInventory().getItemInMainHand();
        // inHand is the tool here; check offhand and inventory instead
        ItemStack merged = tryMergeIntoInventory(player, spawnedType, existingStack);
        if (merged == null) {
            // Nothing to merge into — drop a new item
            ItemStack drop = buildSpawnerItem(spawnedType, existingStack);
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
        // If merged, item was already updated in inventory — no drop needed
    }

    // ── PLACING ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;
        if (!item.hasItemMeta()) return;

        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!meta.hasBlockState()) return;
        if (!(meta.getBlockState() instanceof CreatureSpawner storedState)) return;

        EntityType storedType = storedState.getSpawnedType();
        if (storedType == null) return;

        int stackCount = getItemStackCount(item);

        Block placed = event.getBlockPlaced();
        if (placed.getState() instanceof CreatureSpawner newSpawner) {
            newSpawner.setSpawnedType(storedType);
            newSpawner.setSpawnCount(BASE_SPAWN_COUNT * stackCount);
            // Store stack count in the block too so breaking it gives back the right count
            newSpawner.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, stackCount);
            newSpawner.update(true, false);
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    /**
     * Try to find a matching spawner item in the player's inventory and add
     * the incoming stack count to it. Returns the updated ItemStack or null
     * if no matching item was found.
     */
    private ItemStack tryMergeIntoInventory(Player player, EntityType type, int incomingCount) {
        for (ItemStack inv : player.getInventory().getContents()) {
            if (inv == null || inv.getType() != Material.SPAWNER) continue;
            if (!inv.hasItemMeta()) continue;
            if (!(inv.getItemMeta() instanceof BlockStateMeta meta)) continue;
            if (!meta.hasBlockState()) continue;
            if (!(meta.getBlockState() instanceof CreatureSpawner cs)) continue;
            if (cs.getSpawnedType() != type) continue;

            // Same type found — merge
            int current = getItemStackCount(inv);
            int newCount = current + incomingCount;
            setItemStackCount(inv, newCount);
            return inv;
        }
        return null;
    }

    private ItemStack buildSpawnerItem(EntityType type, int count) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();

        CreatureSpawner state = (CreatureSpawner) meta.getBlockState();
        state.setSpawnedType(type);
        meta.setBlockState(state);

        // Stack count in PDC
        meta.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, count);

        // Lore showing stack size — no custom_name to avoid warnings
        updateLore(meta, count);

        item.setItemMeta(meta);
        return item;
    }

    private void setItemStackCount(ItemStack item, int count) {
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        meta.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, count);
        updateLore(meta, count);
        item.setItemMeta(meta);
    }

    private void updateLore(BlockStateMeta meta, int count) {
        Component loreLine = Component.text("Stacked: " + count + "x")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        meta.lore(List.of(loreLine));
    }

    private int getItemStackCount(ItemStack item) {
        if (!item.hasItemMeta()) return 1;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer val = pdc.get(stackKey, PersistentDataType.INTEGER);
        return val != null ? val : 1;
    }

    private int getBlockStackCount(CreatureSpawner cs) {
        Integer val = cs.getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return val != null ? val : 1;
    }

    private boolean hasSilkTouch(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        return tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }
}
