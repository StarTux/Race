package com.cavetale.race;

import com.cavetale.race.util.Items;
import com.cavetale.race.util.Rnd;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

@RequiredArgsConstructor
public final class Race {
    private final RacePlugin plugin;
    final String name;
    final Tag tag;
    Map<Vec3i, Goody> goodies = new HashMap<>();
    final Random random = new Random();

    public enum Phase {
        IDLE,
        EDIT,
        START,
        RACE,
        FINISH;
    }

    public static final class Tag {
        String worldName = "";
        RaceType type = RaceType.WALK;
        Cuboid area = Cuboid.ZERO;
        Position spawnLocation = Position.ZERO;
        List<Vec3i> startVectors = new ArrayList<>();
        List<Vec3i> goodies = new ArrayList<>();
        Cuboid spawnArea = Cuboid.ZERO;
        Phase phase = Phase.IDLE;
        List<Cuboid> checkpoints = new ArrayList<>();
        int phaseTicks;
        List<Racer> racers = new ArrayList<>();
        long startTime = 0;
        int finishIndex = 0;
        int laps = 1;
        boolean event;
        int rareItemsAvailable = 0;
    }

    public void onDisable() {
        for (Racer racer : tag.racers) {
            Player player = racer.getPlayer();
            if (player == null) continue;
            player.setWalkSpeed(0.2f);
            if (player.getVehicle() != null) {
                player.getVehicle().remove();
            }
        }
        clearGoodies();
    }

    void tick(int ticks) {
        if (getWorld() == null) {
            setPhase(Phase.IDLE);
            return;
        }
        switch (tag.phase) {
        case EDIT: tickEdit(ticks); break;
        case START: tickStart(ticks); break;
        case RACE: tickRace(ticks); break;
        case FINISH: tickFinish(ticks); break;
        default: break;
        }
        tag.phaseTicks += 1;
    }

    void setPhase(final Phase phase) {
        tag.phase = phase;
        tag.phaseTicks = 0;
        clearGoodies();
    }

    void tickEdit(int ticks) {
        World world = getWorld();
        tag.area.highlight(world, ticks, 4, 1, loc -> world.spawnParticle(Particle.BARRIER, loc, 1, 0.0, 0.0, 0.0, 0.0));
        tag.spawnArea.highlight(world, ticks, 4, 4, loc -> world.spawnParticle(Particle.VILLAGER_HAPPY, loc, 1, 0.0, 0.0, 0.0, 0.0));
        for (Cuboid area : tag.checkpoints) {
            area.highlight(world, ticks, 4, 8, loc -> world.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0));
        }
        for (Vec3i v : tag.startVectors) {
            Location loc = v.toLocation(getWorld());
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
        updateGoodies();
    }

    void tickStart(int ticks) {
        pruneRacers();
        int ticksLeft = 200 - tag.phaseTicks;
        if (ticksLeft < 0) {
            setPhase(Phase.RACE);
            tag.startTime = System.currentTimeMillis();
            tag.rareItemsAvailable = tag.laps * 3;
            tag.finishIndex = 0;
            for (Racer racer : tag.racers) {
                setupCheckpoint(racer, tag.checkpoints.get(0));
                Player player = racer.getPlayer();
                if (!tag.type.isMounted()) {
                    player.setWalkSpeed(0.2f);
                }
                player.setFlying(false);
                player.setAllowFlight(false);
                player.setFlySpeed(0.1f);
            }
            return;
        }
        int secondsLeft = ticksLeft / 20;
        if (ticksLeft % 20 == 0) {
            switch (secondsLeft) {
            case 5:
            case 4:
            case 3:
            case 2:
            case 1:
                for (Player player : getPresentPlayers()) {
                    player.sendTitle("" + ChatColor.GREEN + secondsLeft,
                                     "" + ChatColor.GREEN + "Get Ready");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                break;
            case 0:
                for (Player player : getPresentPlayers()) {
                    player.sendTitle("" + ChatColor.GREEN + ChatColor.ITALIC + "GO!",
                                     "" + ChatColor.GREEN + "Good luck");
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                break;
            default: break;
            }
        }
        for (Racer racer : tag.racers) {
            Player player = racer.getPlayer();
            Location location = player.getLocation();
            if (!racer.startVector.contains(location)) {
                player.sendMessage(ChatColor.RED + "Please wait...");
                player.teleport(getStartLocation(racer));
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed(0.0f);
            }
        }
    }

    void passThroughBlock(Player player, Racer racer, Block block) {
        if (racer.finished) return;
        Vec3i vec = Vec3i.of(block);
        Cuboid checkpoint = tag.checkpoints.get(racer.checkpointIndex);
        if (checkpoint.contains(vec)) {
            progressCheckpoint(player, racer);
            if (!racer.finished) {
                checkpoint = tag.checkpoints.get(racer.checkpointIndex);
                setupCheckpoint(racer, checkpoint);
            }
        }
        long now = System.currentTimeMillis();
        if (racer.goodyCooldown > now) return;
        for (int y = -1; y <= 1; y += 1) {
            for (int z = -1; z <= 1; z += 1) {
                for (int x = -1; x <= 1; x += 1) {
                    Vec3i vec2 = vec.add(x, y, z);
                    Goody goody = goodies.get(vec2);
                    if (goody == null || goody.entity == null || goody.cooldown > 0) continue;
                    Location loc = goody.entity.getLocation();
                    goody.entity.remove();
                    goody.entity = null;
                    goody.cooldown = 200;
                    Firework firework = loc.getWorld().spawn(loc.add(0, 2, 0), Firework.class, e -> {
                            e.setPersistent(false);
                            e.setFireworkMeta(Fireworks.simpleFireworkMeta());
                        });
                    firework.detonate();
                    giveGoody(player, racer);
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 2.0f);
                    if (racer.rank == 0) {
                        racer.goodyCooldown = now + 10000L;
                    } else if (racer.rank < 3) {
                        racer.goodyCooldown = now + 7500L;
                    } else {
                        racer.goodyCooldown = now + 5000L;
                    }
                    return;
                }
            }
        }
    }

    ItemStack getHorseGoody(Player player) {
        ItemStack item = null;
        int chance = 1;
        if (Rnd.nextInt(chance++) == 0) {
            item = Items.loadedCrossbow(Items.potionItem(Material.TIPPED_ARROW, PotionEffectType.SLOW, 80));
        }
        if (Rnd.nextInt(chance++) == 0) {
            item = Items.loadedCrossbow(Items.potionItem(Material.TIPPED_ARROW, PotionEffectType.LEVITATION, 60));
        }
        if (Rnd.nextInt(chance++) == 0) {
            item = Items.potionItem(Material.SPLASH_POTION, PotionEffectType.SPEED, 200);
        }
        if (Rnd.nextInt(chance++) == 0) {
            item = Items.potionItem(Material.SPLASH_POTION, PotionEffectType.INVISIBILITY, 200);
        }
        return item;
    }

    @Value
    public static final class GoodyDrop {
        private final int weight;
        private final ItemStack item;
    }

    void giveGoody(Player player, Racer racer) {
        switch (tag.type) {
        case BOAT: {
            if (tag.rareItemsAvailable > 0 && racer.rank > 3 && random.nextDouble() < 0.05) {
                tag.rareItemsAvailable -= 1;
                ItemStack itemStack = new ItemStack(Material.LIGHTNING_ROD);
                itemStack.editMeta(meta -> {
                        meta.displayName(Component.text("Strike Lighning", NamedTextColor.GOLD)
                                         .decoration(TextDecoration.ITALIC, false));
                        meta.lore(Arrays.asList(Component.text("Strike everyone ahead of", NamedTextColor.GRAY)
                                                .decoration(TextDecoration.ITALIC, false),
                                                Component.text("you with lightning!", NamedTextColor.GRAY)
                                                .decoration(TextDecoration.ITALIC, false)));
                    });
                player.getInventory().addItem(itemStack);
                return;
            }
            ItemStack itemStack = new ItemStack(Material.CROSSBOW);
            CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
            meta.addChargedProjectile(new ItemStack(Material.SPECTRAL_ARROW));
            itemStack.setItemMeta(meta);
            player.getInventory().addItem(itemStack);
            break;
        }
        case ICE_BOAT: {
            ItemStack itemStack = new ItemStack(Material.CROSSBOW);
            CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
            meta.addChargedProjectile(new ItemStack(Material.SPECTRAL_ARROW));
            itemStack.setItemMeta(meta);
            player.getInventory().addItem(itemStack);
            break;
        }
        case HORSE:
        case PIG: {
            List<GoodyDrop> pool = new ArrayList<>();
            if (!player.getInventory().contains(Material.CARROT_ON_A_STICK, 4)) {
                pool.add(new GoodyDrop(10, new ItemStack(Material.CARROT_ON_A_STICK)));
            }
            if (racer.rank < 2) {
                ItemStack slowSplashPotion = new ItemStack(Material.LINGERING_POTION);
                PotionMeta meta = (PotionMeta) slowSplashPotion.getItemMeta();
                meta.setBasePotionData(new PotionData(PotionType.SLOWNESS, false, false));
                slowSplashPotion.setItemMeta(meta);
                pool.add(new GoodyDrop(1, slowSplashPotion));
                pool.add(new GoodyDrop(1, new ItemStack(Material.TNT)));
                pool.add(new GoodyDrop(3, new ItemStack(Material.CARROT_ON_A_STICK)));
            }
            if (racer.rank < 3) {
                ItemStack speedSplashPotion = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) speedSplashPotion.getItemMeta();
                //meta.setBasePotionData(new PotionData(PotionType.SPEED, false, false));
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 0, true, true, true), true);
                meta.setDisplayName(ChatColor.WHITE + "Speed");
                speedSplashPotion.setItemMeta(meta);
                pool.add(new GoodyDrop(5, speedSplashPotion));
            } else if (racer.rank < 5) {
                ItemStack speedSplashPotion = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) speedSplashPotion.getItemMeta();
                //meta.setBasePotionData(new PotionData(PotionType.SPEED, false, true)); // upgraded
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 1, true, true, true), true);
                meta.setDisplayName(ChatColor.WHITE + "Speed II");
                speedSplashPotion.setItemMeta(meta);
                pool.add(new GoodyDrop(5, speedSplashPotion));
            } else if (racer.rank < 9) {
                ItemStack speedSplashPotion = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) speedSplashPotion.getItemMeta();
                //meta.setBasePotionData(new PotionData(PotionType.SPEED, true, false)); // extended
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 2, true, true, true), true);
                meta.setDisplayName(ChatColor.WHITE + "Speed III");
                speedSplashPotion.setItemMeta(meta);
                pool.add(new GoodyDrop(5, speedSplashPotion));
            } else if (racer.rank < 11) {
                ItemStack speedSplashPotion = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) speedSplashPotion.getItemMeta();
                //meta.setBasePotionData(new PotionData(PotionType.SPEED, true, false)); // extended
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 3, true, true, true), true);
                meta.setDisplayName(ChatColor.WHITE + "Speed IV");
                speedSplashPotion.setItemMeta(meta);
                pool.add(new GoodyDrop(5, speedSplashPotion));
            } else {
                ItemStack speedSplashPotion = new ItemStack(Material.SPLASH_POTION);
                PotionMeta meta = (PotionMeta) speedSplashPotion.getItemMeta();
                //meta.setBasePotionData(new PotionData(PotionType.SPEED, true, false)); // extended
                meta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 4, true, true, true), true);
                meta.setDisplayName(ChatColor.WHITE + "Speed 5");
                speedSplashPotion.setItemMeta(meta);
                pool.add(new GoodyDrop(6, speedSplashPotion));
            }
            if (pool.isEmpty()) return;
            int total = 0;
            for (GoodyDrop drop : pool) total += drop.weight;
            int roll = random.nextInt(total);
            for (GoodyDrop drop : pool) {
                roll -= drop.weight;
                if (roll <= 0) {
                    player.getInventory().addItem(drop.item.clone());
                    break;
                }
            }
            break;
        }
        default:
            break;
        }
    }

    void progressCheckpoint(Player player, Racer racer) {
        racer.checkpointIndex += 1;
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
        if (racer.checkpointIndex >= tag.checkpoints.size()) {
            racer.checkpointIndex = 0;
            racer.lap += 1;
            if (racer.lap >= tag.laps) {
                racer.finished = true;
                racer.finishTime = System.currentTimeMillis() - tag.startTime;
                racer.finishIndex = tag.finishIndex++;
                plugin.getLogger().info("[" + name + "] " + player.getName() + " finished #" + (racer.finishIndex + 1));
                if (tag.event) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                    if (racer.finishIndex < 3) {
                        switch (tag.type) {
                        case HORSE:
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " Jockey");
                            break;
                        case ICE_BOAT:
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " Drifter");
                            break;
                        case BOAT:
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " Sailor");
                            break;
                        case PIG:
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " PigRacer BaconRacer");
                            break;
                        default:
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " Falcon");
                            break;
                        }
                    }
                }
                player.sendTitle("" + ChatColor.GREEN + "#" + (racer.finishIndex + 1),
                                 "" + ChatColor.GREEN + formatTime(racer.finishTime));
                for (Player target : getPresentPlayers()) {
                    target.sendMessage(ChatColor.GREEN + player.getName()
                                       + " finished #" + (racer.finishIndex + 1)
                                       + " in " + formatTime(racer.finishTime));
                }
                player.setGameMode(GameMode.SPECTATOR);
                if (player.getVehicle() != null) player.getVehicle().remove();
                player.getInventory().clear();
            } else {
                player.sendTitle("" + ChatColor.GREEN + (racer.lap + 1) + "/" + tag.laps,
                                 "" + ChatColor.GREEN + "Lap " + (racer.lap + 1));
            }
        }
    }

    void tickRace(int ticks) {
        for (Player player : getWorld().getPlayers()) {
            if (getRacer(player) != null) continue;
            if (!tag.area.contains(player.getLocation())) continue;
            if (player.isOp()) continue;
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        pruneRacers();
        for (Racer racer : tag.racers) {
            if (!racer.racing) continue;
            if (racer.finished) continue;
            Player player = racer.getPlayer();
            if (player == null) continue;
            Cuboid checkpoint = tag.checkpoints.get(racer.checkpointIndex);
            Location loc = player.getLocation();
            passThroughBlock(player, racer, loc.getBlock());
            passThroughBlock(player, racer, loc.getBlock().getRelative(0, 2, 0));
            try {
                Cuboid cp = tag.checkpoints.get(racer.checkpointIndex);
                Vec3i pos = Vec3i.of(racer.getPlayer().getLocation().getBlock());
                racer.checkpointDistance = pos.distanceSquared(cp.getCenter());
                cp.highlight(player.getWorld(), ticks, 8, 8, l -> player.spawnParticle(Particle.END_ROD, l, 1, 0.0, 0.0, 0.0, 0.0));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Collections.sort(tag.racers);
        for (int i = 0; i < tag.racers.size(); i += 1) {
            tag.racers.get(i).rank = i;
        }
        if (tag.type.isMounted()) {
            for (Racer racer : tag.racers) {
                Player player = racer.getPlayer();
                if (player == null) continue;
                if (racer.finished) continue;
                if (player.getVehicle() == null) {
                    if (racer.remountCooldown > 0) {
                        racer.remountCooldown -= 1;
                    } else {
                        Vehicle vehicle = tag.type.spawnVehicle(player.getLocation());
                        if (vehicle != null) vehicle.addPassenger(player);
                    }
                }
                if (tag.type == RaceType.PIG) {
                    if (!player.getInventory().contains(Material.CARROT_ON_A_STICK)) {
                        player.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK));
                    }
                    player.getInventory().remove(Material.FISHING_ROD);
                }
            }
        }
        updateGoodies();
    }

    void setupCheckpoint(Racer racer, Cuboid checkpoint) {
        racer.checkpointIndex = tag.checkpoints.indexOf(checkpoint);
        Vec3i center = checkpoint.getCenter();
        Vec3i pos = Vec3i.of(racer.getPlayer().getLocation().getBlock());
        racer.checkpointDistance = pos.distanceSquared(center);
        Player player = racer.getPlayer();
        player.setCompassTarget(center.toBlock(getWorld()).getLocation());
    }

    void pruneRacers() {
        tag.racers.removeIf(racer -> {
                if (racer.finished) return false;
                Player player = racer.getPlayer();
                if (player == null || !player.isValid()) return true;
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                    player.setWalkSpeed(0.2f);
                    return true;
                }
                Location loc = player.getLocation();
                if (!isIn(loc.getWorld()) || !tag.area.containsHorizontal(loc)) {
                    player.setWalkSpeed(0.2f);
                    return true;
                }
                return false;
            });
    }

    void tickFinish(int ticks) {
    }

    public File getSaveFile() {
        return new File(plugin.getSaveFolder(), name + ".json");
    }

    public void save() {
        Json.save(getSaveFile(), tag, true);
    }

    public World getWorld() {
        return Bukkit.getWorld(tag.worldName);
    }

    public boolean isIn(World world) {
        return tag.worldName.equals(world.getName());
    }

    public boolean isIn(String worldName) {
        return tag.worldName.equals(worldName);
    }

    public Location getSpawnLocation() {
        return tag.spawnLocation.toLocation(getWorld());
    }

    public String listString() {
        return name
            + " at=" + tag.worldName + ":" + tag.spawnLocation.simpleString()
            + " phase=" + tag.phase;
    }

    public void setArea(Cuboid cuboid) {
        tag.area = cuboid;
    }

    public void setWorld(World world) {
        tag.worldName = world.getName();
    }

    public void setSpawnLocation(Location loc) {
        tag.spawnLocation = Position.of(loc);
    }

    public void setSpawnArea(Cuboid area) {
        tag.spawnArea = area;
    }

    public List<Cuboid> getCheckpoints() {
        return new ArrayList<>(tag.checkpoints);
    }

    public void setCheckpoints(List<Cuboid> checkpoints) {
        tag.checkpoints = new ArrayList<>(checkpoints);
    }

    public List<Player> getEligiblePlayers() {
        return getWorld().getPlayers().stream()
            .filter(p -> !(p.isPermissionSet("group.streamer") && p.hasPermission("group.streamer")))
            .filter(p -> tag.area.containsHorizontal(p.getLocation()))
            .collect(Collectors.toList());
    }

    public List<Player> getPresentPlayers() {
        return getWorld().getPlayers().stream()
            .filter(p -> tag.area.containsHorizontal(p.getLocation()))
            .collect(Collectors.toList());
    }

    Location getStartLocation(Racer racer) {
        Vec3i vector = racer.startVector;
        Cuboid firstCheckpoint = tag.checkpoints.get(0);
        float yaw;
        if (vector.x > firstCheckpoint.bx) {
            // face west
            yaw = 90f;
        } else if (vector.z > firstCheckpoint.bz) {
            // face north
            yaw = 180f;
        } else if (vector.x < firstCheckpoint.ax) {
            // face east
            yaw = 270f;
        } else if (vector.z < firstCheckpoint.az) {
            // face south
            yaw = 0f;
        } else {
            yaw = 0f;
        }
        Location location = vector.toLocation(getWorld());
        location.setYaw(yaw);
        return location;
    }

    public void startRace() {
        tag.racers.clear();
        Vec3i center = tag.checkpoints.get(0).getCenter();
        Collections.sort(tag.startVectors, (a, b) -> Integer.compare(center.simpleDistance(a), center.simpleDistance(b)));
        int startVectorIndex = 0;
        List<Player> players = getEligiblePlayers();
        Collections.shuffle(players);
        for (Player player : players) {
            Racer racer = new Racer(player);
            racer.racing = true;
            tag.racers.add(racer);
            if (startVectorIndex >= tag.startVectors.size()) startVectorIndex = 0;
            racer.startVector = tag.startVectors.get(startVectorIndex++);
            player.sendMessage(ChatColor.GREEN + "You joined the race!");
            player.sendActionBar(ChatColor.GREEN + "You joined the race!");
            player.getInventory().clear();
            player.getInventory().addItem(new ItemStack(Material.COMPASS));
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(getStartLocation(racer));
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setFlySpeed(0.0f);
            player.sendTitle("" + ChatColor.GREEN + ChatColor.ITALIC + "Race",
                             "" + ChatColor.GREEN + "The Race Begins");
            player.setWalkSpeed(0f);
            if (tag.type == RaceType.STRIDER) {
                player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK));
            }
            if (tag.type == RaceType.PIG) {
                player.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK));
            }
            if (tag.type == RaceType.PARKOUR) {
                player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
            }
        }
        tag.startTime = System.currentTimeMillis();
        setPhase(Phase.START);
    }

    public void setLaps(int laps) {
        tag.laps = laps;
    }

    public List<Racer> getRacers() {
        return new ArrayList<>(tag.racers);
    }

    public List<Player> getRacePlayers() {
        return tag.racers.stream()
            .map(Racer::getPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static String formatTime(long millis) {
        int secs = ((int) (millis / 1000)) % 60;
        int mins = (int) (millis / 1000 / 60);
        int mils = (int) (millis % 1000L);
        String milString = "" + mils;
        if (milString.isEmpty()) milString = "00";
        if (milString.length() > 2) milString = milString.substring(0, 2);
        while (milString.length() < 2) milString = milString + "0";
        if (mins > 0) {
            return String.format("%d:%02d.%s", mins, secs, milString);
        } else {
            return String.format("%d.%s", secs, milString);
        }
    }

    public static String formatTimeShort(long millis) {
        int secs = ((int) (millis / 1000)) % 60;
        int mins = (int) (millis / 1000 / 60);
        if (mins > 0) {
            return String.format("%d:%02d", mins, secs);
        } else {
            return String.format("%d", secs);
        }
    }

    public Racer getRacer(Player player) {
        for (Racer racer : tag.racers) {
            if (racer.uuid.equals(player.getUniqueId())) return racer;
        }
        return null;
    }

    void stopRace() {
        tag.racers.clear();
        tag.phase = Phase.IDLE;
        clearGoodies();
    }

    Cuboid getLastCheckpoint(Racer racer) {
        if (racer.checkpointIndex == 0) return tag.spawnArea;
        if (racer.checkpointIndex > tag.checkpoints.size()) return tag.spawnArea;
        return tag.checkpoints.get(racer.checkpointIndex - 1);
    }

    void onUseItem(Player player, ItemStack item, PlayerInteractEvent event) {
        if (tag.phase != Phase.RACE) return;
        Racer racer = getRacer(player);
        if (racer == null) return;
        if (item.getType() == Material.ENDER_PEARL && tag.type == RaceType.PARKOUR) {
            event.setCancelled(true);
            teleportToLastCheckpoint(player);
            return;
        }
        if (item.getType() == Material.TNT) {
            item.subtract(1);
            player.getWorld().spawn(player.getEyeLocation(), TNTPrimed.class, e -> {
                    e.setFuseTicks(80);
                });
        }
        if (item.getType() == Material.LIGHTNING_ROD) {
            item.subtract(1);
            int count = 0;
            for (Racer otherRacer : tag.racers) {
                if (racer == otherRacer) continue;
                if (racer.rank <= otherRacer.rank) continue;
                Player otherPlayer = otherRacer.getPlayer();
                if (otherPlayer != null) {
                    otherPlayer.getWorld().strikeLightningEffect(otherPlayer.getLocation());
                    Entity vehicle = otherPlayer.getVehicle();
                    if (vehicle != null) vehicle.remove();
                    otherRacer.remountCooldown = 60;
                    otherPlayer.sendMessage(Component.text("Struck by lightning!", NamedTextColor.GOLD));
                    count += 1;
                }
            }
            player.sendMessage(Component.text("Struck " + count + " players ahead of you with lightning!", NamedTextColor.GOLD));
        }
    }

    void teleportToLastCheckpoint(Player player) {
        Racer racer = getRacer(player);
        if (racer == null) return;
        Cuboid checkpoint = getLastCheckpoint(racer);
        Block block = checkpoint.getBottomBlock(getWorld());
        while (!block.isPassable()) block = block.getRelative(0, 1, 0);
        Location loc = block.getLocation().add(0.5, 0.0, 0.5);
        Location ploc = player.getLocation();
        loc.setYaw(ploc.getYaw());
        loc.setPitch(ploc.getPitch());
        player.teleport(loc);
        player.sendActionBar(ChatColor.GREEN + "Returned to last checkpoint!");
        player.playSound(loc, Sound.ENTITY_ENDER_PEARL_THROW, SoundCategory.MASTER, 0.5f, 1.0f);
    }

    void onDamage(Player player, EntityDamageEvent event) {
        event.setCancelled(true);
        Racer racer = getRacer(player);
        if (racer == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Bukkit.getScheduler().runTask(plugin, () -> teleportToLastCheckpoint(player));
        }
    }

    public long getTime() {
        switch (tag.phase) {
        case RACE:
        case FINISH:
            return System.currentTimeMillis() - tag.startTime;
        default: return 0;
        }
    }

    public void onQuit(Player player) {
        Racer racer = getRacer(player);
        if (racer != null) return;
        tag.racers.remove(racer);
        player.setWalkSpeed(0.2f);
    }

    public void onMoveFromTo(Player player, Location from, Location to) {
        if (tag.phase != Phase.RACE) return;
        if (!from.getWorld().equals(to.getWorld())) return;
        Racer racer = getRacer(player);
        if (racer == null) return;
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 0.01) return;
        BlockIterator iter = new BlockIterator(from.getWorld(), from.toVector(), direction.normalize(), 0.0, 0);
        Cuboid checkpoint = tag.checkpoints.get(racer.checkpointIndex);
        int count = 0;
        Block end = to.getBlock();
        while (iter.hasNext()) {
            if (count++ >= 16) break;
            Block block = iter.next();
            passThroughBlock(player, racer, block);
            if (block.equals(end)) break;
        }
    }

    void updateGoodies() {
        for (Vec3i vector : tag.goodies) {
            Goody goody = goodies.computeIfAbsent(vector, v -> new Goody(v));
            if (goody.cooldown > 0) {
                goody.cooldown -= 1;
                continue;
            }
            if (goody.entity == null || !goody.entity.isValid()) {
                goody.entity = null;
                Location location = goody.where.toLocation(getWorld());
                if (!location.isChunkLoaded()) continue;
                goody.entity = location.getWorld().spawn(location, EnderCrystal.class, e -> {
                        e.setPersistent(false);
                        e.setShowingBottom(false);
                    });
            }
        }
    }

    void clearGoodies() {
        for (Goody goody : goodies.values()) {
            if (goody.entity != null) {
                goody.entity.remove();
                goody.entity = null;
            }
        }
        goodies.clear();
    }

    public RaceType getRaceType() {
        return tag.type;
    }
}
