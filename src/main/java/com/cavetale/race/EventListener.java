package com.cavetale.race;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.spigotmc.event.entity.EntityDismountEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final RacePlugin plugin;

    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null) return;
        if (event.getEntity() instanceof Player) {
            race.onDamage((Player) event.getEntity(), event);
        }
        if (event.getEntity() instanceof EnderCrystal) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null) return;
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
        }
        if (event.getDamager() instanceof AbstractArrow && event.getEntity() instanceof Player && event.getCause() == DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
            Player player = (Player) event.getEntity();
            Racer racer = race.getRacer(player);
            if (racer == null) return;
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                vehicle.remove();
                player.sendMessage(ChatColor.RED + "Mount destroyed");
                racer.remountCooldown = 60;
            }
            event.getDamager().remove();
        }
        if (event.getDamager() instanceof Firework) {
            event.setCancelled(true);
        }
        if (race.tag.type == RaceType.PIG && event.getCause() == DamageCause.ENTITY_EXPLOSION
            && event.getEntity() instanceof Pig && event.getDamager() instanceof TNTPrimed) {
            Pig pig = (Pig) event.getEntity();
            Player player = null;
            Racer racer = null;
            for (Entity passenger : pig.getPassengers()) {
                if (passenger instanceof Player) {
                    player = (Player) passenger;
                    racer = race.getRacer(player);
                    if (racer != null) break;
                }
            }
            if (racer == null) return;
            pig.setHealth(0.0);
            player.sendMessage(ChatColor.RED + "Mount destroyed");
            racer.remountCooldown = 10;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.races.isRace(event.getEntity().getLocation())) return;
    }

    // Respawn at world spawn
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Race race = plugin.races.at(player.getLocation());
        if (race == null) return;
        event.setRespawnLocation(race.getSpawnLocation());
    }

    // No item damage
    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (plugin.races.isRace(event.getPlayer().getLocation())) {
            if (event.getItem().getType() == Material.CARROT_ON_A_STICK) return;
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        Race race = plugin.races.at(proj.getLocation());
        if (race == null) return;
        switch (race.getRaceType()) {
        case BOAT:
        case ICE_BOAT:
            if (proj instanceof AbstractArrow) {
                proj.getWorld().createExplosion(proj, 3.0f);
                proj.remove();
            }
            break;
        default:
            break;
        }
    }

    @EventHandler
    public void onProjectileCollide(ProjectileCollideEvent event) {
        if (event.getEntity() instanceof Firework) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.races.isRace(event.getEntity().getLocation())) return;
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.races.isRace(event.getBlock())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.races.isRace(event.getBlock())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerSidebar(PlayerSidebarEvent event) {
        Player player = event.getPlayer();
        Race race = plugin.races.at(player.getLocation());
        if (race == null) return;
        List<String> lines = new ArrayList<>();
        int i = 0;
        List<Racer> racers = race.getRacers();
        Racer theRacer = race.getRacer(player);
        if (theRacer != null) {
            if (!theRacer.finished) {
                lines.add(ChatColor.GREEN + "Lap " + (theRacer.lap + 1) + "/" + race.tag.laps);
                lines.add(ChatColor.GREEN + "You are #" + (theRacer.rank + 1));
                lines.add(ChatColor.GREEN + "Time " + ChatColor.WHITE + race.formatTimeShort(race.getTime()));
            } else {
                lines.add(ChatColor.GREEN + race.formatTime(theRacer.finishTime));
            }
            if (race.tag.type == RaceType.PIG) {
                if (player.getVehicle() instanceof Pig) {
                    Pig pig = (Pig) player.getVehicle();
                    double speed = pig.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getValue();
                    lines.add(ChatColor.GREEN + "Speed " + ChatColor.WHITE + (int) Math.round(speed * 100.0));
                }
            }
        }
        for (Racer racer : racers) {
            int index = i++;
            if (index > 8) break;
            if (racer.finished) {
                if (racer.rank < 3) {
                    lines.add("" + ChatColor.GOLD + ChatColor.BOLD + "#" + (index + 1) + " " + racer.name);
                } else {
                    lines.add("" + ChatColor.GOLD + "#" + (index + 1) + " " + racer.name);
                }
            } else {
                lines.add("" + ChatColor.GREEN + "#" + (index + 1) + ChatColor.WHITE + " " + racer.name);
            }
        }
        if (!lines.isEmpty()) {
            event.addLines(plugin, Priority.LOW, lines);
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (!plugin.races.isRace(event.getEntity().getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.getDismounted().remove();
        Player player = (Player) event.getEntity();
        Racer racer = race.getRacer(player);
        if (racer == null) return;
        player.sendMessage(ChatColor.RED + "Dismounted");
        switch (race.tag.type) {
        case PIG:
            racer.remountCooldown = 10;
            break;
        default:
            racer.remountCooldown = 40;
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Race race = plugin.races.at(event.getLocation());
        if (race == null) return;
        switch (event.getSpawnReason()) {
        case MOUNT:
        case NATURAL:
        case BREEDING:
            event.setCancelled(true);
        default:
            break;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            break;
        default: return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;
        Player player = event.getPlayer();
        Race race = plugin.races.at(player.getLocation());
        if (race == null) return;
        race.onUseItem(player, item, event);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!plugin.races.isRace(event.getPlayer().getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Race race = plugin.races.at(player.getLocation());
        if (race != null) {
            race.onQuit(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Race race = plugin.races.at(event.getFrom());
        if (race == null) return;
        Player player = event.getPlayer();
        race.onMoveFromTo(player, event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        Race race = plugin.races.at(event.getFrom());
        if (race == null) return;
        List<Entity> passengerList = event.getVehicle().getPassengers();
        if (passengerList == null || passengerList.isEmpty()) return;
        if (!(passengerList.get(0) instanceof Player)) return;
        Player player = (Player) passengerList.get(0);
        race.onMoveFromTo(player, event.getFrom(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        Race race = plugin.races.at(projectile.getLocation());
        if (race == null) return;
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player) projectile.getShooter();
        Racer racer = race.getRacer(player);
        if (racer == null) {
            event.setCancelled(true);
            return;
        }
        if (race.tag.type.isMounted() && player.getVehicle() == null) {
            event.setCancelled(true);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack itemStack;
                itemStack = player.getInventory().getItemInMainHand();
                if (itemStack != null && itemStack.getType() == Material.CROSSBOW) {
                    CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
                    if (!meta.hasChargedProjectiles()) {
                        player.getInventory().setItemInMainHand(null);
                    }
                }
                itemStack = player.getInventory().getItemInOffHand();
                if (itemStack != null && itemStack.getType() == Material.CROSSBOW) {
                    CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
                    if (!meta.hasChargedProjectiles()) {
                        player.getInventory().setItemInOffHand(null);
                    }
                }
            });
    }

    @EventHandler
    void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Race race = plugin.races.at(event.getPlayer().getLocation());
        if (race == null) return;
        event.setCancelled(true);
    }

    @EventHandler
    void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntityType() != EntityType.DROPPED_ITEM) return;
        Race race = plugin.races.at(event.getLocation());
        if (race == null) return;
        event.setCancelled(true);
    }
}
