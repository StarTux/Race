package com.cavetale.race;

import com.cavetale.mytems.Mytems;
import com.cavetale.race.util.Items;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
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
        tag.area.highlight(world, ticks, 4, 1, loc -> world.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0));
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
            tag.racers.removeIf(r -> !r.isOnline());
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
                    player.showTitle(Title.title(Component.text("" + secondsLeft, NamedTextColor.GREEN),
                                                 Component.text("Get Ready", NamedTextColor.GREEN),
                                                 Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                break;
            case 0:
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(Component.text("GO!", NamedTextColor.GREEN, TextDecoration.ITALIC),
                                                 Component.text("Good Luck", NamedTextColor.GREEN),
                                                 Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
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
                    racer.goodyCooldown = now + 3000L;
                    return;
                }
            }
        }
    }

    void giveGoody(Player player, Racer racer) {
        List<GoodyDrop> pool = new ArrayList<>();
        switch (tag.type) {
        case BOAT: {
            if (tag.rareItemsAvailable > 0 && racer.rank > 3 && random.nextDouble() < 0.05) {
                tag.rareItemsAvailable -= 1;
                ItemStack itemStack = Items.lightningRod();
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
            putHorseGoodies(pool, player, racer);
            break;
        case PIG: {
            if (!player.getInventory().contains(Material.CARROT_ON_A_STICK, 2)) {
                pool.add(new GoodyDrop(10, new ItemStack(Material.CARROT_ON_A_STICK)));
            }
            putHorseGoodies(pool, player, racer);
            break;
        }
        default:
            break;
        }
        if (tag.rareItemsAvailable <= 0) pool.removeIf(GoodyDrop::isRare);
        if (pool.isEmpty()) return;
        double total = 0;
        for (GoodyDrop drop : pool) total += drop.weight;
        double roll = random.nextDouble() * total;
        GoodyDrop theDrop = null;
        for (GoodyDrop drop : pool) {
            roll -= drop.weight;
            if (roll < 0.0) {
                theDrop = drop;
                break;
            }
        }
        if (theDrop == null) theDrop = pool.get(pool.size() - 1);
        if (theDrop.rare) {
            tag.rareItemsAvailable -= 1;
        }
        player.getInventory().addItem(theDrop.item.clone());
    }

    void putHorseGoodies(List<GoodyDrop> pool, Player player, Racer racer) {
        int count = tag.countRacers();
        int rank = racer.rank;
        if (rank <= count / 4) {
            pool.add(new GoodyDrop(0.5, Items.potionItem(Material.LINGERING_POTION, PotionType.SLOWNESS)));
            pool.add(new GoodyDrop(0.5, Items.potionItem(Material.LINGERING_POTION, PotionType.POISON)));
            pool.add(new GoodyDrop(0.5, Items.potionItem(Material.LINGERING_POTION, PotionType.INSTANT_DAMAGE)));
            pool.add(new GoodyDrop(2, Items.label(Material.TNT,
                                                  Component.text("TNT Trap", NamedTextColor.DARK_RED))));
            pool.add(new GoodyDrop(2, Items.label(Items.potionItem(Material.POTION, PotionType.INSTANT_HEAL),
                                                  Component.text("Health Boost", NamedTextColor.AQUA))));
        } else {
            pool.add(new GoodyDrop(0.05, Items.lightningRod(), true));
        }
        pool.add(new GoodyDrop(2, Items.label(Items.potionItem(Material.POTION, PotionType.SPEED),
                                              Component.text("Speed Boost", NamedTextColor.GREEN))));
        pool.add(new GoodyDrop(1, Items.blunderbuss()));
    }

    private int getEventScore(int finishIndex) {
        return Math.max(0, 10 - finishIndex);
    }

    void progressCheckpoint(Player player, Racer racer) {
        racer.checkpointIndex += 1;
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.2f, 2.0f);
        racer.checkpointDistanceIncreaseTicks = 0;
        if (racer.checkpointIndex >= tag.checkpoints.size()) {
            racer.checkpointIndex = 0;
            racer.lap += 1;
            if (racer.lap >= tag.laps) {
                racer.finished = true;
                racer.finishTime = System.currentTimeMillis() - tag.startTime;
                racer.finishIndex = tag.finishIndex++;
                if (player.getVehicle() != null) player.getVehicle().remove();
                plugin.getLogger().info("[" + name + "] " + player.getName() + " finished #" + (racer.finishIndex + 1));
                if (plugin.save.event) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                    int score = getEventScore(racer.finishIndex);
                    int totalScore = plugin.save.scores.compute(player.getUniqueId(), (u, i) -> (i != null ? i : 0) + score);
                    player.sendMessage(Component.text().color(NamedTextColor.GOLD)
                                       .append(Component.text("You earned "))
                                       .append(Component.text(score, NamedTextColor.BLUE))
                                       .append(Component.text(" points for a total of "))
                                       .append(Component.text(totalScore, NamedTextColor.BLUE)));
                }
                player.showTitle(Title.title(Component.text("#" + (racer.finishIndex + 1), NamedTextColor.GREEN),
                                             Component.text(formatTime(racer.finishTime), NamedTextColor.GREEN),
                                             Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))));
                for (Player target : getPresentPlayers()) {
                    target.sendMessage(ChatColor.GREEN + player.getName()
                                       + " finished #" + (racer.finishIndex + 1)
                                       + " in " + formatTime(racer.finishTime));
                }
                player.setGameMode(GameMode.SPECTATOR);
                if (player.getVehicle() != null) player.getVehicle().remove();
                player.getInventory().clear();
            } else {
                player.showTitle(Title.title(Component.text((racer.lap + 1) + "/" + tag.laps, NamedTextColor.GREEN),
                                             Component.text("Lap " + (racer.lap + 1), NamedTextColor.GREEN),
                                             Title.Times.of(Duration.ofMillis(500),
                                                            Duration.ofMillis(1000),
                                                            Duration.ofMillis(0))));
            }
        }
        if (tag.type == RaceType.ELYTRA && player.isGliding()) {
            player.boostElytra(new ItemStack(Material.FIREWORK_ROCKET));
        }
    }

    void tickRace(int ticks) {
        if (tag.maxDuration > 0) {
            if (getTime() > tag.maxDuration * 1000L) {
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(Component.text("Timeout", NamedTextColor.RED),
                                                 Component.text("The race is over", NamedTextColor.RED),
                                                 Title.Times.of(Duration.ZERO,
                                                                Duration.ofSeconds(2),
                                                                Duration.ofSeconds(1))));
                    player.sendMessage(Component.text("Timeout! The race is over", NamedTextColor.RED));
                }
                stopRace();
            }
        }
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
                int oldCheckpointDistance = racer.checkpointDistance;
                racer.checkpointDistance = pos.distanceSquared(cp.getCenter());
                if (racer.checkpointDistance > oldCheckpointDistance) {
                    racer.checkpointDistanceIncreaseTicks += 1;
                } else if (racer.checkpointDistance < oldCheckpointDistance) {
                    racer.checkpointDistanceIncreaseTicks = 0;
                }
                if (racer.checkpointDistanceIncreaseTicks >= 10 && (tag.phaseTicks % 20) == 0) {
                    player.showTitle(Title.title(Component.text("Reverse", NamedTextColor.RED),
                                                 Component.text("Turn Around", NamedTextColor.RED),
                                                 Title.Times.of(Duration.ZERO, Duration.ofMillis(750), Duration.ZERO)));
                }
                if ((ticks % 20) == 0) {
                    List<Vec3i> vecs = cp.enumerate();
                    Location center = vecs.get(random.nextInt(vecs.size())).toLocation(getWorld()).add(0.0, 0.5, 0.0);
                    player.spawnParticle(Particle.FIREWORKS_SPARK, center, 20, 0.0, 0.0, 0.0, 0.2);
                }
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
        if (tag.type == RaceType.ELYTRA) {
            for (Racer racer : tag.racers) {
                Player player = racer.getPlayer();
                if (player == null || racer.finished || !racer.racing) continue;
                ItemStack elytra = player.getInventory().getChestplate();
                if (elytra == null || elytra.getType() != Material.ELYTRA) {
                    player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
                } else if (player.isGliding()) {
                    getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation(), 1, 0.0, 0.0, 0.0, 0.0);
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
        return tag.phase
            + " " + tag.type
            + " " + name
            + " " + tag.worldName;
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
            player.sendMessage(Component.text("You joined the race!", NamedTextColor.GREEN));
            player.sendActionBar(Component.text("You joined the race!", NamedTextColor.GREEN));
            player.getInventory().clear();
            player.getInventory().addItem(Items.label(Material.COMPASS,
                                                      Component.text("Points at next Checkpoint", NamedTextColor.YELLOW)));
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(getStartLocation(racer));
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setFlySpeed(0.0f);
            player.showTitle(Title.title(Component.text("Race", NamedTextColor.GREEN, TextDecoration.ITALIC),
                                         Component.text("The Race Begins", NamedTextColor.GREEN),
                                         Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1))));
            player.setWalkSpeed(0f);
            if (tag.type == RaceType.STRIDER) {
                player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK));
            }
            if (tag.type == RaceType.PIG) {
                player.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK));
            }
            player.getInventory().setItem(8, Items.label(Material.ENDER_PEARL,
                                                         Component.text("Return to Checkpoint", NamedTextColor.LIGHT_PURPLE)));
            if (tag.type == RaceType.HORSE) {
                player.getInventory().setHelmet(Mytems.COWBOY_HAT.createItemStack());
            } else if (tag.type == RaceType.BOAT || tag.type == RaceType.ICE_BOAT) {
                player.getInventory().setHelmet(Mytems.PIRATE_HAT.createItemStack());
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
        return String.format("%d:%02d", mins, secs);
    }

    public Racer getRacer(Player player) {
        for (Racer racer : tag.racers) {
            if (racer.uuid.equals(player.getUniqueId())) return racer;
        }
        return null;
    }

    void stopRace() {
        for (Racer racer : tag.racers) {
            Player player = racer.getPlayer();
            if (player != null) {
                if (player.getVehicle() != null) {
                    player.getVehicle().remove();
                }
            }
        }
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
        if (item.getType() == Material.ENDER_PEARL) {
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
                    if (vehicle != null) {
                        if (vehicle instanceof LivingEntity) {
                            ((LivingEntity) vehicle).damage(15.0);
                        } else {
                            vehicle.remove();
                        }
                    }
                    otherRacer.remountCooldown = 60;
                    otherPlayer.sendMessage(Component.text("Struck by lightning!", NamedTextColor.GOLD));
                    count += 1;
                }
            }
            player.sendMessage(Component.text("Struck " + count + " players ahead of you with lightning!", NamedTextColor.GOLD));
        }
        if (item.getType() == Material.POTION) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            PotionData potionData = meta.getBasePotionData();
            if (potionData != null && potionData.getType() == PotionType.SPEED) {
                if (player.getVehicle() instanceof LivingEntity) {
                    event.setCancelled(true);
                    item.subtract(1);
                    double strength = (double) racer.rank / (double) tag.countRacers();
                    LivingEntity target = (LivingEntity) player.getVehicle();
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                                                            Math.max(100, (int) (600.0 * strength)),
                                                            2, true, true, true));
                    player.sendMessage(Component.text("Your ride got a speed boost!",
                                                      NamedTextColor.GREEN, TextDecoration.ITALIC));
                    Location loc = player.getLocation();
                    Location locAhead = player.getEyeLocation().add(loc.getDirection().normalize());
                    player.playSound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.0f, 2.0f);
                    player.spawnParticle(Particle.SPELL_MOB, locAhead, 32, 0.5, 0.5, 0.5, 1.0);
                    return;
                }
            }
            if (potionData != null && potionData.getType() == PotionType.INSTANT_HEAL) {
                if (player.getVehicle() instanceof LivingEntity) {
                    event.setCancelled(true);
                    item.subtract(1);
                    player.sendMessage(Component.text("Your ride was healed!",
                                                      NamedTextColor.AQUA, TextDecoration.ITALIC));
                    LivingEntity target = (LivingEntity) player.getVehicle();
                    target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                    target.removePotionEffect(PotionEffectType.POISON);
                    Location loc = player.getLocation();
                    Location locAhead = player.getEyeLocation().add(loc.getDirection().normalize());
                    player.playSound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.0f, 2.0f);
                    player.spawnParticle(Particle.HEART, locAhead, 8, 0.5, 0.5, 0.5, 0.0);
                    return;
                }
            }
        }
    }

    void teleportToLastCheckpoint(Player player) {
        Racer racer = getRacer(player);
        if (racer == null) return;
        Cuboid checkpoint = getLastCheckpoint(racer);
        Location loc;
        if (checkpoint.equals(Cuboid.ZERO)) {
            loc = racer.startVector.toLocation(getWorld());
        } else {
            Block block = checkpoint.getBottomBlock(getWorld());
            while (!block.isPassable()) block = block.getRelative(0, 1, 0);
            loc = block.getLocation().add(0.5, 0.0, 0.5);
        }
        Location ploc = player.getLocation();
        loc.setYaw(ploc.getYaw());
        loc.setPitch(ploc.getPitch());
        if (player.getVehicle() != null) {
            player.getVehicle().remove();
        }
        player.teleport(loc);
        player.sendActionBar(Component.text("Returned to last checkpoint!", NamedTextColor.LIGHT_PURPLE));
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
            if (goody.entity == null || goody.entity.isDead()) {
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

    public boolean isRacing() {
        return tag.phase == Phase.RACE;
    }

    public boolean isMounted() {
        return tag.type.isMounted();
    }
}
