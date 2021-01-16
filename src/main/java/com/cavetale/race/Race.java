package com.cavetale.race;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

@RequiredArgsConstructor
public final class Race {
    private final RacePlugin plugin;
    final String name;
    final Tag tag;
    Map<Vec3i, Goody> goodies = new HashMap<>();

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
        updateGoodies();
    }

    void tickStart(int ticks) {
        pruneRacers();
        int ticksLeft = 200 - tag.phaseTicks;
        if (ticksLeft < 0) {
            setPhase(Phase.RACE);
            tag.startTime = System.currentTimeMillis();
            tag.finishIndex = 0;
            for (Racer racer : tag.racers) {
                setupCheckpoint(racer, tag.checkpoints.get(0));
                Player player = racer.getPlayer();
                if (!tag.type.isMounted()) {
                    player.setWalkSpeed(0.2f);
                }
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
                            e.setFireworkMeta(Fireworks.randomFireworkMeta());
                        });
                    firework.detonate();
                    giveGoody(player);
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 2.0f);
                }
            }
        }
    }

    void giveGoody(Player player) {
        ItemStack itemStack = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
        meta.addChargedProjectile(new ItemStack(Material.SPECTRAL_ARROW));
        itemStack.setItemMeta(meta);
        player.getInventory().addItem(itemStack);
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
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                if (racer.finishIndex == 0) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " Falcon");
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
                player.sendTitle("" + ChatColor.GREEN + racer.lap + "/" + tag.laps,
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
        checkpoint.highlight(getWorld(), tag.phaseTicks, 8, 2, loc -> player.spawnParticle(Particle.CLOUD, loc, 1, 0.0, 0.0, 0.0, 0.0));
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
            .filter(p -> !"Cavetale".equals(p.getName()))
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
            player.sendTitle("" + ChatColor.GREEN + ChatColor.ITALIC + "Race",
                             "" + ChatColor.GREEN + "The Race Begins");
            player.setWalkSpeed(0f);
            if (tag.type == RaceType.STRIDER) {
                player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK));
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
        if (item.getType() == Material.ENDER_PEARL && tag.type == RaceType.PARKOUR) {
            event.setCancelled(true);
            teleportToLastCheckpoint(player);
            return;
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
}
