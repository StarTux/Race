package com.cavetale.race;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.font.Unicode;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.font.Glyph;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.cavetale.mytems.item.trophy.TrophyType;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
        if (event.getEntity() instanceof Player player) {
            race.onPlayerDamage(player, event);
        } else if (event.getEntity() instanceof EnderCrystal) {
            event.setCancelled(true);
            return;
        } else {
            onVehicleDamage(event.getEntity(), race, event);
        }
    }

    private void onVehicleDamage(Entity entity, Race race, EntityDamageEvent event) {
        List<Entity> passengers = entity.getPassengers();
        if (passengers == null || passengers.isEmpty()) return;
        if (passengers.get(0) instanceof Player player) {
            Racer racer = race.getRacer(player);
            if (racer == null) return;
            race.onPlayerVehicleDamage(player, racer, event);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Race race = plugin.races.at(event.getPlayer().getLocation());
        if (race != null) race.onPlayerDeath(event.getPlayer(), event);
    }

    // Respawn at world spawn
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Race race = plugin.races.at(player.getLocation());
        if (race == null) return;
        race.onPlayerRespawn(player, event);
    }

    // No item damage
    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (plugin.races.isRace(event.getPlayer().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Firework) {
            event.setCancelled(true);
            return;
        }
        Projectile proj = event.getEntity();
        Race race = plugin.races.at(proj.getLocation());
        if (race != null) race.onProjectileHit(proj, event);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // This event appears not to fire
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null) return;
        event.blockList().clear();
        race.onEntityExplode(event.getEntity(), event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (NetworkServer.current() != NetworkServer.RACE) return;
        if (!plugin.races.isRace(event.getBlock())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (NetworkServer.current() != NetworkServer.RACE) return;
        if (!plugin.races.isRace(event.getBlock())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerHud(PlayerHudEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (player.getSpectatorTarget() instanceof Player) {
                player = (Player) player.getSpectatorTarget();
            }
        }
        Race race = plugin.races.at(player.getLocation());
        List<Component> lines = new ArrayList<>();
        if (race == null || !race.isRacing()) {
            if (plugin.getSave().isEvent()) {
                eventSidebar(player, event, lines);
            }
        } else {
            race.sidebar(player, lines);
        }
        if (!lines.isEmpty()) {
            event.sidebar(PlayerHudPriority.HIGHEST, lines);
        }
        if (race != null) {
            switch (race.getTag().getPhase()) {
            case START:
                race.onPlayerHudStart(player, event);
                break;
            case RACE:
                race.onPlayerHud(player, event);
                break;
            case FINISH:
                race.onPlayerHudFinish(player, event);
                break;
            default:
                break;
            }
        }
    }

    private void eventSidebar(Player player, PlayerHudEvent event, List<Component> lines) {
        List<UUID> uuids = plugin.getSave().rankScores();
        lines.add(join(noSeparators(), Mytems.GOLDEN_CUP.component, text("Grand Prix", GOLD), Mytems.GOLDEN_CUP.component));
        int playerScore = plugin.getSave().getScores().getOrDefault(player.getUniqueId(), 0);
        lines.add(text("Your Score ", WHITE)
                  .append(text(playerScore, BLUE)));
        int placement = 0;
        int lastScore = -1;
        List<TrophyType> trophies = TrophyType.of(TrophyCategory.CUP);
        for (int i = 0; i < 10; i += 1) {
            final int score;
            final Component name;
            if (i < uuids.size()) {
                UUID uuid = uuids.get(i);
                score = plugin.getSave().getScores().get(uuid);
                Player thePlayer = Bukkit.getPlayer(uuid);
                name = thePlayer != null
                    ? thePlayer.displayName()
                    : text(PlayerCache.nameForUuid(uuid));
            } else {
                score = 0;
                name = text("???", DARK_GRAY);
            }
            if (lastScore != score) {
                lastScore = score;
                placement += 1;
            }
            TrophyType trophy = trophies.get(Math.min(placement, trophies.size()) - 1);
            lines.add(join(noSeparators(),
                           trophy,
                           Glyph.toComponent("" + placement),
                           text(Unicode.subscript("" + score), trophy.quality.textColor),
                           space(),
                           name));
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
        if (race == null || !race.isMounted() || !race.isRacing()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        Racer racer = race.getRacer(player);
        if (racer != null && racer.racing && !racer.finished) {
            boolean dead = event.getDismounted().isDead();
            if (!dead) {
                event.setCancelled(true);
            } else {
                racer.remountCooldown = 100;
                race.resetCoinsOnDeath(player, racer);
                event.getDismounted().remove();
                player.sendMessage(text("You lost your vehicle", RED));
            }
        } else {
            event.getDismounted().remove();
        }
    }

    @EventHandler
    private void onVehicleDamage(VehicleDamageEvent event) {
        Race race = plugin.races.at(event.getVehicle().getLocation());
        if (race == null || !race.isMounted() || !race.isRacing()) return;
        List<Entity> passengers = event.getVehicle().getPassengers();
        if (passengers.isEmpty() || !(passengers.get(0) instanceof Player player)) return;
        Racer racer = race.getRacer(player);
        if (racer == null) return;
        if (racer.isInvincible()) event.setCancelled(true);
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
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        Racer racer = race.getRacer(player);
        if (racer == null || !racer.isRacing()) return;
        if (race.getRaceType().isMounted() && racer.racing && player.getVehicle() == null && event.hasChangedBlock() && !(event instanceof PlayerTeleportEvent)) {
            event.setCancelled(true);
            return;
        }
        race.onMoveFromTo(player, event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        Race race = plugin.races.at(event.getFrom());
        if (race == null) return;
        Player player = event.getPlayer();
        race.onPlayerJump(player, event);
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
        if (event.getVehicle() instanceof Boat boat) {
            Location to = event.getTo().clone().add(0.0, 0.55, 0.0);
            Block block = to.getBlock();
            if (block.isLiquid()
                && block.getBlockData() instanceof Levelled level
                && level.getLevel() == 0) {
                Vector velo = event.getTo().toVector().subtract(event.getFrom().toVector());
                if (velo.getY() < 0) {
                    boat.setVelocity(velo.setY(0.25));
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null || !race.isMounted() || !race.isRacing()) return;
        List<Entity> passengerList = event.getEntity().getPassengers();
        if (passengerList == null || passengerList.isEmpty()) return;
        if (!(passengerList.get(0) instanceof Player)) return;
        Player player = (Player) passengerList.get(0);
        Racer racer = race.getRacer(player);
        if (racer == null || !racer.racing || racer.finished) return;
        racer.remountCooldown = 100;
        race.resetCoinsOnDeath(player, racer);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        Race race = plugin.races.at(projectile.getLocation());
        if (race == null) return;
        if (!(projectile.getShooter() instanceof Player player)) return;
        if (!race.isRacing()) {
            if (!player.isOp()) event.setCancelled(true);
            return;
        }
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
    void onEntityPickupItem(EntityPickupItemEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (NetworkServer.current() != NetworkServer.RACE) return;
        Race race = plugin.races.at(event.getPlayer().getLocation());
        if (race == null) return;
        if (event.getRightClicked() instanceof Boat) {
            if (race.tag.type == RaceType.BOAT || race.tag.type == RaceType.ICE_BOAT) {
                event.setCancelled(true);
            }
        } else if (event.getRightClicked().getType() == EntityType.CAMEL) {
            if (race.tag.type == RaceType.CAMEL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null) return;
        List<LivingEntity> entities = event.getAffectedEntities();
        if (event.getEntity().getSource() instanceof Player) {
            Player shooter = (Player) event.getEntity().getSource();
            for (Iterator<LivingEntity> iter = entities.iterator(); iter.hasNext();) {
                LivingEntity it = iter.next();
                if (it.equals(shooter)) {
                    iter.remove();
                    continue;
                }
                if (shooter.getVehicle() != null && shooter.getVehicle().equals(it)) {
                    iter.remove();
                    continue;
                }
                if (it instanceof Player player) {
                    Racer racer = race.getRacer(player);
                    if (racer != null && racer.isInvincible()) iter.remove();
                    continue;
                }
                List<Entity> passengers = it.getPassengers();
                if (!passengers.isEmpty() && passengers.get(0) instanceof Player player) {
                    Racer racer = race.getRacer(player);
                    if (racer != null && racer.isInvincible()) iter.remove();
                }
            }
        }
    }

    @EventHandler
    void onPlayerToggleGlide(EntityToggleGlideEvent event) {
        Race race = plugin.races.at(event.getEntity().getLocation());
        if (race == null || race.tag.type != RaceType.ELYTRA) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Racer racer = race.getRacer(player);
        if (racer == null || !racer.racing || racer.finished) return;
        if (event.isGliding()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isGliding()) {
                        player.fireworkBoost(new ItemStack(Material.FIREWORK_ROCKET));
                    }
                });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (plugin.getSave().isEvent()) {
            if (plugin.getSave().getEventRaceWorld() == null) {
                event.setSpawnLocation(plugin.getLobbyWorld().getSpawnLocation());
                return;
            }
            final Race race = plugin.races.inWorld(plugin.getSave().getEventRaceWorld());
            if (race != null && race.getWorld() != null) {
                event.setSpawnLocation(race.getSpawnLocation());
            }
        }
    }

    @EventHandler
    void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        Race race = plugin.races.at(event.getVehicle().getLocation());
        if (race == null) return;
        List<Entity> passengersA = event.getVehicle().getPassengers();
        if (passengersA.isEmpty() || !(passengersA.get(0) instanceof Player playerA)) return;
        Racer racerA = race.getRacer(playerA);
        if (racerA == null) return;
        List<Entity> passengersB = event.getEntity().getPassengers();
        if (passengersB.isEmpty() || !(passengersB.get(0) instanceof Player playerB)) return;
        Racer racerB = race.getRacer(playerB);
        if (racerB == null) return;
        if (racerA.isInvincible() && !racerB.isInvincible()) {
            race.damageVehicle(playerB, racerB, 20.0, true);
        }
        if (racerB.isInvincible() && !racerA.isInvincible()) {
            race.damageVehicle(playerA, racerA, 20.0, true);
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerFlowerPotManipulate(PlayerFlowerPotManipulateEvent event) {
        if (!plugin.races.isRace(event.getFlowerpot())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }
}
