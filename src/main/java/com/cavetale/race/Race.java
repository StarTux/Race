package com.cavetale.race;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public final class Race {
    private final RacePlugin plugin;
    final String name;
    final Tag tag;

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
        Cuboid spawnArea = Cuboid.ZERO;
        Phase phase = Phase.IDLE;
        List<Cuboid> checkpoints = new ArrayList<>();
        int phaseTicks;
        List<Racer> racers = new ArrayList<>();
        long startTime = 0;
        int finishIndex = 0;
        int laps = 1;
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

    void setPhase(Phase phase) {
        tag.phase = phase;
        tag.phaseTicks = 0;
    }

    void tickEdit(int ticks) {
        World world = getWorld();
        tag.area.highlight(world, ticks, 4, 1, loc -> world.spawnParticle(Particle.BARRIER, loc, 1, 0.0, 0.0, 0.0, 0.0));
        tag.spawnArea.highlight(world, ticks, 4, 4, loc -> world.spawnParticle(Particle.VILLAGER_HAPPY, loc, 1, 0.0, 0.0, 0.0, 0.0));
        for (Cuboid area : tag.checkpoints) {
            area.highlight(world, ticks, 4, 8, loc -> world.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0));
        }
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
                racer.getPlayer().getInventory().clear();
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
        for (Player player : getRacePlayers()) {
            if (!tag.spawnArea.contains(player.getLocation())) {
                player.sendMessage(ChatColor.RED + "Please wait...");
                player.teleport(getSpawnLocation());
            }
        }
    }

    void tickRace(int ticks) {
        for (Player player : getEligiblePlayers()) {
            if (getRacer(player) != null) continue;
            if (tag.spawnArea.contains(player.getLocation())) {
                tag.racers.add(new Racer(player));
                player.sendMessage(ChatColor.GREEN + "You joined the race!");
                player.sendActionBar(ChatColor.GREEN + "You joined the race!");
                player.getInventory().clear();
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
        pruneRacers();
        for (Racer racer : tag.racers) {
            if (racer.finished) continue;
            Player player = racer.getPlayer();
            if (player == null) continue;
            Cuboid checkpoint = tag.checkpoints.get(racer.checkpointIndex);
            Location loc = player.getLocation();
            if (checkpoint.contains(player.getEyeLocation()) || checkpoint.contains(player.getLocation().add(0, 2, 0))) {
                racer.checkpointIndex += 1;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
                if (racer.checkpointIndex >= tag.checkpoints.size()) {
                    racer.checkpointIndex = 0;
                    racer.lap += 1;
                    if (racer.lap >= tag.laps) {
                        racer.finished = true;
                        racer.finishTime = System.currentTimeMillis() - tag.startTime;
                        racer.finishIndex = tag.finishIndex++;
                        player.sendTitle("" + ChatColor.GREEN + "#" + (racer.finishIndex + 1),
                                         "" + ChatColor.GREEN + formatTime(racer.finishTime));
                        for (Player target : getPresentPlayers()) {
                            target.sendMessage(ChatColor.GREEN + player.getName()
                                               + " finished #" + (racer.finishIndex + 1)
                                               + " in " + formatTime(racer.finishTime));
                        }
                        plugin.consoleCommand("ml add " + player.getName());
                        continue;
                    } else {
                        player.sendTitle("" + ChatColor.GREEN + racer.lap + "/" + tag.laps,
                                         "" + ChatColor.GREEN + "Lap " + (racer.lap + 1));
                    }
                }
                checkpoint = tag.checkpoints.get(racer.checkpointIndex);
            }
            setupCheckpoint(racer, checkpoint);
        }
        Collections.sort(tag.racers);
        if (tag.type == RaceType.STRIDER) {
            for (Racer racer : tag.racers) {
                Player player = racer.getPlayer();
                if (player == null) continue;
                if (racer.finished) continue;
                if (player.getVehicle() == null) {
                    if (racer.remountCooldown > 0) {
                        racer.remountCooldown -= 1;
                    } else {
                        Strider strider = getWorld().spawn(player.getLocation(), Strider.class, e -> {
                                e.setShivering(false);
                                e.setAdult();
                                e.setAgeLock(true);
                            });
                        strider.addPassenger(player);
                    }
                }
                if (!player.getInventory().contains(Material.WARPED_FUNGUS_ON_A_STICK)) {
                    player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK));
                }
                if (!player.getInventory().contains(Material.COMPASS)) {
                    player.getInventory().addItem(new ItemStack(Material.COMPASS));
                }
            }
        }
        if (tag.type == RaceType.PARKOUR) {
            for (Racer racer : tag.racers) {
                Player player = racer.getPlayer();
                if (player == null) continue;
                if (!player.getInventory().contains(Material.ENDER_PEARL)) {
                    player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
                }
                if (!player.getInventory().contains(Material.COMPASS)) {
                    player.getInventory().addItem(new ItemStack(Material.COMPASS));
                }
            }
        }
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
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return true;
                Location loc = player.getLocation();
                if (!isIn(loc.getWorld()) || !tag.area.containsHorizontal(loc)) return true;
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
            .filter(p -> tag.area.containsHorizontal(p.getLocation()))
            .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
            .collect(Collectors.toList());
    }

    public List<Player> getPresentPlayers() {
        return getWorld().getPlayers().stream()
            .filter(p -> tag.area.containsHorizontal(p.getLocation()))
            .collect(Collectors.toList());
    }

    public void startRace() {
        tag.racers.clear();
        for (Player player : getEligiblePlayers()) {
            tag.racers.add(new Racer(player));
            player.sendMessage(ChatColor.GREEN + "You joined the race!");
            player.sendActionBar(ChatColor.GREEN + "You joined the race!");
            player.getInventory().clear();
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(getSpawnLocation());
            player.sendTitle("" + ChatColor.GREEN + ChatColor.ITALIC + "Race",
                             "" + ChatColor.GREEN + "The Race Begins");
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
}
