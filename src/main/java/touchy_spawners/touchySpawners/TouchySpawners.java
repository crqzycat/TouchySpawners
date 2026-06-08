package touchy_spawners.touchySpawners;

import org.bukkit.GameMode;
import org.bukkit.Material;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class TouchySpawners extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TouchySpawners enabled – Silk Touch spawners active!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TouchySpawners disabled.");
    }

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

        // Build spawner item — no custom_name, only BlockStateMeta to avoid the warning
        ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
        CreatureSpawner spawnerState = (CreatureSpawner) meta.getBlockState();
        spawnerState.setSpawnedType(spawnedType);
        meta.setBlockState(spawnerState);
        spawnerItem.setItemMeta(meta);

        block.getWorld().dropItemNaturally(block.getLocation(), spawnerItem);
    }

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

        Block placed = event.getBlockPlaced();
        if (placed.getState() instanceof CreatureSpawner newSpawner) {
            newSpawner.setSpawnedType(storedType);
            newSpawner.update(true, false);
        }
    }

    private boolean hasSilkTouch(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        return tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }
}
