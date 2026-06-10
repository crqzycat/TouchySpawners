package touchy_spawners.touchySpawners;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class TouchySpawners extends JavaPlugin implements Listener {

    private NamespacedKey stackKey;
    private NamespacedKey labelKey;

    @Override
    public void onEnable() {
        stackKey = new NamespacedKey(this, "stack_count");
        labelKey = new NamespacedKey(this, "spawner_label");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TouchySpawners enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TouchySpawners disabled.");
    }

    // ── PLACE ────────────────────────────────────────────────────────────────

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
            newSpawner.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, 1);
            // spawnCount stays at vanilla default (4) for a single spawner — no change needed
            newSpawner.update(true, false);
        }
    }

    // ── STACK ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.SPAWNER) return;
        if (!hand.hasItemMeta()) return;

        CreatureSpawner blockSpawner = (CreatureSpawner) block.getState();
        EntityType blockType = blockSpawner.getSpawnedType();

        if (!(hand.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!meta.hasBlockState()) return;
        if (!(meta.getBlockState() instanceof CreatureSpawner itemState)) return;
        EntityType itemType = itemState.getSpawnedType();

        if (blockType == null || itemType == null || blockType != itemType) return;

        event.setCancelled(true);

        int current = getBlockStackCount(blockSpawner);
        int newCount = current + 1;

        // Re-fetch state to apply changes
        CreatureSpawner toUpdate = (CreatureSpawner) block.getState();
        toUpdate.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, newCount);
        toUpdate.setSpawnCount(4 * newCount);
        toUpdate.update(true, false);

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        updateLabel(block, newCount);
    }

    // ── BREAK ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        // Always remove the label — regardless of gamemode or tool
        removeLabel(block);

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasSilkTouch(tool)) return;

        event.setDropItems(false);
        event.setExpToDrop(0);

        CreatureSpawner cs = (CreatureSpawner) block.getState();
        EntityType spawnedType = cs.getSpawnedType();
        int count = getBlockStackCount(cs);

        ItemStack drop = buildSpawnerItem(spawnedType);
        drop.setAmount(count);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private void updateLabel(Block block, int count) {
        removeLabel(block);
        if (count <= 1) return;

        Location loc = block.getLocation().add(0.5, 0.85, 0.5);
        ArmorStand as = (ArmorStand) block.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setInvisible(true);
        as.setGravity(false);
        as.setSmall(true);
        as.setMarker(true);
        as.setCanPickupItems(false);
        as.customName(Component.text(count + "x Spawner").color(NamedTextColor.AQUA));
        as.setCustomNameVisible(true);
        as.getPersistentDataContainer().set(labelKey, PersistentDataType.INTEGER, 1);
    }

    private void removeLabel(Block block) {
        Location center = block.getLocation().add(0.5, 0.85, 0.5);
        block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6).stream()
            .filter(e -> e instanceof ArmorStand)
            .filter(e -> e.getPersistentDataContainer().has(labelKey, PersistentDataType.INTEGER))
            .forEach(Entity::remove);
    }

    private ItemStack buildSpawnerItem(EntityType type) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner state = (CreatureSpawner) meta.getBlockState();
        state.setSpawnedType(type);
        meta.setBlockState(state);
        item.setItemMeta(meta);
        return item;
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
