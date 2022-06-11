package com.cavetale.race;

import com.cavetale.core.font.Unicode;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.WardrobeItem;
import com.cavetale.race.struct.Vec2i;
import com.cavetale.race.util.Rnd;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.title.Title.Times.times;
import static org.bukkit.attribute.Attribute.*;

@RequiredArgsConstructor
public final class Race {
    private final RacePlugin plugin;
    protected final String name;
    protected final Tag tag;
    protected Map<Vec3i, Goody> goodies = new HashMap<>();
    protected Map<Vec3i, Goody> coins = new HashMap<>();
    public static final int MAX_COINS = 20;

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
            tag.rareItemsAvailable = 0;
            tag.finishIndex = 0;
            tag.maxLap = 0;
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
                if (tag.type == RaceType.ELYTRA) {
                    player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
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
                    player.showTitle(Title.title(text("" + secondsLeft, GREEN),
                                                 text("Get Ready", GREEN),
                                                 times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                break;
            case 0:
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(text("GO!", GREEN, TextDecoration.ITALIC),
                                                 text("Good Luck", GREEN),
                                                 times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
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

    private void passThroughBlock(Player player, Racer racer, Block block) {
        if (racer.finished) return;
        Vec3i vec = Vec3i.of(block);
        Cuboid checkpoint = tag.checkpoints.get(racer.checkpointIndex);
        if (checkpoint.contains2(vec)) {
            progressCheckpoint(player, racer);
            if (!racer.finished) {
                checkpoint = tag.checkpoints.get(racer.checkpointIndex);
                setupCheckpoint(racer, checkpoint);
            }
        }
        long now = System.currentTimeMillis();
        if (racer.goodyCooldown < now && GoodyItem.count(player.getInventory()) < 2) {
            for (int y = 0; y < 2; y += 1) {
                onTouchGoody(player, racer, vec.add(0, y - 1, 0));
            }
        }
        if (racer.coins < MAX_COINS) {
            for (int y = 0; y < 2; y += 1) {
                onTouchCoin(player, racer, vec.add(0, y - 1, 0));
            }
        }
    }

    private void onTouchGoody(Player player, Racer racer, Vec3i vector) {
        Goody goody = goodies.get(vector);
        if (goody == null || goody.entity == null || goody.cooldown > 0) return;
        Location loc = goody.entity.getLocation();
        goody.entity.remove();
        goody.entity = null;
        goody.cooldown = 40;
        Firework firework = loc.getWorld().spawn(loc.add(0, 2, 0), Firework.class, e -> {
                e.setPersistent(false);
                e.setFireworkMeta(Fireworks.simpleFireworkMeta());
            });
        firework.detonate();
        giveGoody(player, racer);
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 1.0f, 2.0f);
        racer.goodyCooldown = System.currentTimeMillis() + 3000L;
    }

    private boolean giveGoody(Player player, Racer racer) {
        List<GoodyItem> options = new ArrayList<>();
        List<Double> chances = new ArrayList<>();
        double totalChance = 0.0;
        for (GoodyItem it : GoodyItem.values()) {
            if (it.category == GoodyItem.Category.UNAVAILABLE) {
                continue;
            }
            if (it.category == GoodyItem.Category.LIVING && !tag.type.mountIsAlive()) {
                continue;
            }
            if (it.category == GoodyItem.Category.RARE && tag.rareItemsAvailable <= 0) {
                continue;
            }
            double chance = it.goodyPredicate.chance(this, player, racer);
            if (chance < 0.01) continue;
            options.add(it);
            chances.add(chance);
            totalChance += chance;
        }
        if (options.isEmpty()) return false;
        GoodyItem choice = options.get(options.size() - 1);
        double roll = Rnd.nextDouble() * totalChance;
        for (int i = 0; i < options.size(); i += 1) {
            roll -= chances.get(i);
            if (roll <= 0) {
                choice = options.get(i);
                break;
            }
        }
        if (choice.category == GoodyItem.Category.RARE) {
            tag.rareItemsAvailable -= 1;
        }
        player.getInventory().addItem(choice.createItemStack());
        player.sendMessage(choice.lore.get(0));
        return true;
    }

    private void onTouchCoin(Player player, Racer racer, Vec3i vector) {
        Goody coin = coins.get(vector);
        if (coin == null || coin.entity == null || coin.cooldown > 0) return;
        Location loc = coin.entity.getLocation();
        coin.entity.remove();
        coin.entity = null;
        coin.cooldown = 20 * 60;
        setCoins(player, racer, racer.coins + 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 2.0f);
    }

    protected void setCoins(Player player, Racer racer, int value) {
        racer.coins = value;
        updateVehicleSpeed(player, racer, player.getVehicle());
    }

    private static final UUID SPEED_BONUS_UUID = UUID.fromString("5209c14a-7d9c-41e9-858a-2b6da987486a");

    protected void updateVehicleSpeed(Player player, Racer racer, Entity vehicle) {
        if (vehicle == null) return;
        if (vehicle instanceof Attributable attributable) {
            AttributeInstance inst = attributable.getAttribute(GENERIC_MOVEMENT_SPEED);
            if (inst == null) return;
            for (AttributeModifier modifier : List.copyOf(inst.getModifiers())) {
                if (SPEED_BONUS_UUID.equals(modifier.getUniqueId())) {
                    inst.removeModifier(modifier);
                }
            }
            if (racer.coins >= 0) {
                double factor = 0.5 * ((double) racer.coins / (double) MAX_COINS);
                inst.addModifier(new AttributeModifier(SPEED_BONUS_UUID,
                                                       "race_coins",
                                                       factor,
                                                       AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    private int getEventScore(int finishIndex) {
        return Math.max(1, tag.racerCount - finishIndex);
    }

    private void progressCheckpoint(Player player, Racer racer) {
        racer.checkpointIndex += 1;
        racer.checkpointDistanceIncreaseTicks = 0;
        if (racer.checkpointIndex >= tag.checkpoints.size()) {
            racer.checkpointIndex = 0;
            racer.lap += 1;
            if (racer.lap > tag.maxLap) {
                tag.maxLap += 1;
                tag.rareItemsAvailable += 1;
            }
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
                    player.sendMessage(text().color(GOLD)
                                       .append(text("You earned "))
                                       .append(text(score, BLUE))
                                       .append(text(" points for a total of "))
                                       .append(text(totalScore, BLUE)));
                    if (racer.finishIndex == 0) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " "
                                               + String.join(" ", tag.type.getWinnerTitles()));
                    }
                }
                player.showTitle(Title.title(text("#" + (racer.finishIndex + 1), GREEN),
                                             text(formatTime(racer.finishTime), GREEN),
                                             times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))));
                for (Player target : getPresentPlayers()) {
                    target.sendMessage(ChatColor.GREEN + player.getName()
                                       + " finished #" + (racer.finishIndex + 1)
                                       + " in " + formatTime(racer.finishTime));
                }
                player.setGameMode(GameMode.SPECTATOR);
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
                if (player.getVehicle() != null) player.getVehicle().remove();
                clearInventory(player);
            } else {
                player.showTitle(Title.title(text((racer.lap + 1) + "/" + tag.laps, GREEN),
                                             text("Lap " + (racer.lap + 1), GREEN),
                                             times(Duration.ofMillis(500),
                                                   Duration.ofMillis(1000),
                                                   Duration.ofMillis(0))));
            }
        }
        if (tag.type == RaceType.ELYTRA && player.isGliding()) {
            player.boostElytra(new ItemStack(Material.FIREWORK_ROCKET));
        }
    }

    private void tickRace(int ticks) {
        if (tag.maxDuration > 0) {
            if (getTime() > tag.maxDuration * 1000L) {
                for (Player player : getPresentPlayers()) {
                    player.showTitle(Title.title(text("Timeout", RED),
                                                 text("The race is over", RED),
                                                 times(Duration.ZERO,
                                                       Duration.ofSeconds(2),
                                                       Duration.ofSeconds(1))));
                    player.sendMessage(text("Timeout! The race is over", RED));
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
            Entity vehicle = player.getVehicle();
            Location loc = vehicle != null
                ? vehicle.getLocation()
                : player.getLocation();
            passThroughBlock(player, racer, loc.getBlock());
            try {
                Cuboid cp = tag.checkpoints.get(racer.checkpointIndex);
                Vec3i pos = Vec3i.of(loc.getBlock());
                int oldCheckpointDistance = racer.checkpointDistance;
                Vec3i center = cp.getCenter();
                racer.checkpointDistance = pos.distanceSquared(center);
                if (racer.checkpointDistance > oldCheckpointDistance) {
                    racer.checkpointDistanceIncreaseTicks += 1;
                } else if (racer.checkpointDistance < oldCheckpointDistance) {
                    racer.checkpointDistanceIncreaseTicks = 0;
                }
                if (racer.checkpointDistanceIncreaseTicks >= 10 && (tag.phaseTicks % 20) == 0) {
                    player.showTitle(Title.title(text("Reverse", RED),
                                                 text("Turn Around", RED),
                                                 times(Duration.ZERO, Duration.ofMillis(750), Duration.ZERO)));
                }
                if ((ticks % 20) == 0) {
                    List<Vec3i> vecs = cp.enumerate();
                    Location particleLocation = Rnd.pick(vecs).toLocation(getWorld()).add(0.0, 0.5, 0.0);
                    player.spawnParticle(Particle.FIREWORKS_SPARK, particleLocation, 20, 0.0, 0.0, 0.0, 0.2);
                }
                Vector playerDirection = loc.getDirection();
                Vec3i direction = center.subtract(pos);
                double playerAngle = Math.atan2(playerDirection.getZ(), playerDirection.getX());
                double targetAngle = Math.atan2((double) direction.z, (double) direction.x);
                if (Double.isFinite(playerAngle) && Double.isFinite(targetAngle)) {
                    double angle = targetAngle - playerAngle;
                    if (angle > Math.PI) angle -= 2.0 * Math.PI;
                    if (angle < -Math.PI) angle += 2.0 * Math.PI;
                    if (angle < Math.PI * -0.25) {
                        player.sendActionBar(Mytems.ARROW_LEFT.component);
                    } else if (angle > Math.PI * 0.25) {
                        player.sendActionBar(Mytems.ARROW_RIGHT.component);
                    } else {
                        player.sendActionBar(Mytems.ARROW_UP.component);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (tag.type == RaceType.ELYTRA) {
                if (player.isGliding()) {
                    getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation(), 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (tag.type.isMounted()) {
                if (vehicle == null) {
                    if (racer.remountCooldown > 0) {
                        racer.remountCooldown -= 1;
                    } else {
                        vehicle = tag.type.spawnVehicle(player.getLocation());
                        if (vehicle != null) {
                            vehicle.addPassenger(player);
                            updateVehicleSpeed(player, racer, vehicle);
                        }
                    }
                }
            }
            if (racer.isInvincible()) {
                racer.invincibleTicks -= 1;
                if (racer.invincibleTicks <= 0) {
                    player.removePotionEffect(PotionEffectType.GLOWING);
                    if (vehicle != null) {
                        vehicle.setGlowing(false);
                        if (vehicle instanceof LivingEntity living) {
                            living.removePotionEffect(PotionEffectType.GLOWING);
                        }
                    }
                }
            }
        }
        Collections.sort(tag.racers);
        for (int i = 0; i < tag.racers.size(); i += 1) {
            tag.racers.get(i).rank = i;
        }
        updateGoodies();
    }

    private void setupCheckpoint(Racer racer, Cuboid checkpoint) {
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

    private Location getStartLocation(Racer racer) {
        Vec3i vector = racer.startVector;
        Cuboid firstCheckpoint = tag.checkpoints.get(0);
        float yaw = Yaw.yaw(vector, firstCheckpoint);
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
            player.sendMessage(text("You joined the race!", GREEN));
            player.sendActionBar(text("You joined the race!", GREEN));
            clearInventory(player);
            player.getInventory().addItem(GoodyItem.WAYPOINT.createItemStack());
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(getStartLocation(racer));
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setFlySpeed(0.0f);
            player.showTitle(Title.title(text("Race", GREEN, TextDecoration.ITALIC),
                                         text("The Race Begins", GREEN),
                                         times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1))));
            player.setWalkSpeed(0f);
            if (tag.type == RaceType.STRIDER) {
                player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK));
            }
            if (tag.type == RaceType.PIG) {
                player.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK));
            }
            player.getInventory().setItem(8, GoodyItem.RETURN.createItemStack());
            if (tag.type == RaceType.HORSE) {
                player.getInventory().setHelmet(Mytems.COWBOY_HAT.createItemStack());
            } else if (tag.type == RaceType.BOAT || tag.type == RaceType.ICE_BOAT) {
                player.getInventory().setHelmet(Mytems.PIRATE_HAT.createItemStack());
            }
        }
        tag.racerCount = tag.racers.size();
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

    public void stopRace() {
        for (Racer racer : tag.racers) {
            Player player = racer.getPlayer();
            if (player != null) {
                clearInventory(player);
                if (player.getVehicle() != null) {
                    player.getVehicle().remove();
                }
                player.setGameMode(GameMode.SPECTATOR);
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
            }
        }
        tag.racers.clear();
        tag.phase = Phase.IDLE;
        clearGoodies();
    }

    public Cuboid getLastCheckpoint(Racer racer) {
        if (racer.checkpointIndex == 0) return tag.checkpoints.get(tag.checkpoints.size() - 1);
        if (racer.checkpointIndex > tag.checkpoints.size()) return tag.checkpoints.get(0);
        return tag.checkpoints.get(racer.checkpointIndex - 1);
    }

    protected void onUseItem(Player player, ItemStack item, PlayerInteractEvent event) {
        if (tag.phase != Phase.RACE) return;
        Racer racer = getRacer(player);
        if (racer == null) return;
        GoodyItem goodyItem = GoodyItem.of(item);
        if (goodyItem == null) return;
        if (goodyItem.goodyConsumer.use(this, player, racer, item)) {
            event.setCancelled(true);
        }
    }

    public void teleportToLastCheckpoint(Player player) {
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
        player.sendActionBar(text("Returned to last checkpoint!", LIGHT_PURPLE));
        player.playSound(loc, Sound.ENTITY_ENDER_PEARL_THROW, SoundCategory.MASTER, 0.5f, 1.0f);
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
        player.setFlySpeed(0.1f);
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

    protected void updateGoodies() {
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
                goody.entity = location.getWorld().spawn(location, ArmorStand.class, e -> {
                        e.setPersistent(false);
                        e.setVisible(false);
                        e.setGravity(false);
                        e.setMarker(true);
                        e.setSmall(true);
                        e.getEquipment().setHelmet(Mytems.BOSS_CHEST.createIcon());
                    });
            } else if (goody.entity instanceof ArmorStand armorStand) {
                EulerAngle euler = armorStand.getHeadPose();
                euler = euler.setY(euler.getY() + 0.15);
                armorStand.setHeadPose(euler);
            }
        }
        for (Vec3i vector : tag.coins) {
            Goody coin = coins.computeIfAbsent(vector, v -> new Goody(v));
            if (coin.cooldown > 0) {
                coin.cooldown -= 1;
                continue;
            }
            if (coin.entity == null || coin.entity.isDead()) {
                coin.entity = null;
                Location location = coin.where.toLocation(getWorld()).add(0.0, 0.125, 0.0);
                if (!location.isChunkLoaded()) continue;
                coin.entity = location.getWorld().dropItem(location, Mytems.GOLDEN_COIN.createIcon(), e -> {
                        e.setPersistent(false);
                        e.setGravity(false);
                        e.setCanPlayerPickup(false);
                        e.setCanMobPickup(false);
                        e.setUnlimitedLifetime(true);
                        e.setWillAge(false);
                        e.setPickupDelay(0);
                        e.setVelocity(new Vector().zero());
                    });
            } else if (coin.entity instanceof Item item) {
                item.teleport(coin.where.toLocation(getWorld()).add(0.0, 0.125, 0.0));
                item.setVelocity(new Vector().zero());
            }
        }
    }

    protected void clearGoodies() {
        for (Goody goody : goodies.values()) {
            if (goody.entity != null) {
                goody.entity.remove();
                goody.entity = null;
            }
        }
        goodies.clear();
        for (Goody coin : coins.values()) {
            if (coin.entity != null) {
                coin.entity.remove();
                coin.entity = null;
            }
        }
        coins.clear();
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

    protected void loadAllRaceChunks() {
        World world = getWorld();
        if (world == null) return;
        final int radius = 8;
        HashSet<Vec2i> chunksToLoad = new HashSet<>();
        for (Cuboid cuboid : tag.checkpoints) {
            int ax = (cuboid.getAx() >> 4) - radius;
            int bx = (cuboid.getBx() >> 4) + radius;
            int az = (cuboid.getAz() >> 4) - radius;
            int bz = (cuboid.getBz() >> 4) + radius;
            for (int z = az; z <= bz; z += 1) {
                for (int x = ax; x <= bx; x += 1) {
                    chunksToLoad.add(new Vec2i(x, z));
                }
            }
        }
        plugin.getLogger().info("[" + name + "] Creating " + chunksToLoad.size() + " chunk tickets...");
        int count = 0;
        for (Vec2i vec : chunksToLoad) {
            if (world.addPluginChunkTicket(vec.x, vec.z, plugin)) {
                count += 1;
            }
        }
        plugin.getLogger().info("[" + name + "] " + count + "/" + chunksToLoad.size() + " chunk tickets created!");
    }

    protected void unloadAllRaceChunks() {
        World world = getWorld();
        if (world == null) return;
        world.removePluginChunkTickets(plugin);
    }

    public static void clearInventory(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i += 1) {
            ItemStack item = player.getInventory().getItem(i);
            Mytems mytems = Mytems.forItem(item);
            if (mytems != null && mytems.getMytem() instanceof WardrobeItem) {
                continue;
            }
            player.getInventory().clear(i);
        }
    }

    protected void sidebar(Player player, List<Component> lines) {
        int i = 0;
        List<Racer> racers = getRacers();
        Racer theRacer = getRacer(player);
        if (theRacer != null) {
            lines.add(text("You are #" + (theRacer.rank + 1) + "/" + tag.countAllRacers(), GREEN));
            if (!tag.coins.isEmpty()) {
                lines.add(join(noSeparators(), Mytems.GOLDEN_COIN.component, text(" " + theRacer.coins, WHITE), text("/" + MAX_COINS, GRAY)));
            }
            if (!theRacer.finished) {
                lines.add(text("Lap " + (theRacer.lap + 1) + "/" + tag.laps, GREEN));
            } else {
                lines.add(text(formatTime(theRacer.finishTime), GREEN));
            }
            if (player.getVehicle() instanceof Attributable ride) {
                double speed = ride.getAttribute(GENERIC_MOVEMENT_SPEED).getValue();
                lines.add(join(noSeparators(), text("Speed ", GRAY), text((int) Math.round(speed * 100.0), GOLD)));
            }
        }
        String time = formatTimeShort(getTime());
        String maxTime = tag.maxDuration > 0
            ? formatTimeShort(tag.maxDuration * 1000L)
            : "" + Unicode.INFINITY.character;
        lines.add(join(noSeparators(), text("Time ", GREEN), text(time, YELLOW), text("/", WHITE), text(maxTime, RED)));
        for (Racer racer : racers) {
            Player racerPlayer = racer.getPlayer();
            Component playerName = racerPlayer != null ? racerPlayer.displayName() : text(racer.name);
            int index = i++;
            if (index > 9) break;
            if (racer.finished) {
                lines.add(join(noSeparators(), text("" + (index + 1) + " "), playerName).color(GOLD));
            } else if (!tag.coins.isEmpty()) {
                lines.add(join(noSeparators(), text(index + 1 + " "),
                               Mytems.GOLDEN_COIN.component, text(racer.coins + " ", YELLOW),
                               playerName).color(WHITE));
            } else {
                lines.add(join(noSeparators(), text(index + 1 + " "), playerName).color(WHITE));
            }
        }
    }

    /**
     * Attempt to damage the player's vehicle.
     */
    protected boolean damageVehicle(Player player, Racer racer, double amount, boolean clearCoinsOnDeath) {
        if (racer.isInvincible()) return false;
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            if (vehicle instanceof LivingEntity living) {
                final double health = living.getHealth();
                if (health - amount >= 0.5) {
                    living.setHealth(health - amount);
                } else {
                    vehicle.remove();
                    racer.remountCooldown = 60;
                    if (clearCoinsOnDeath) {
                        setCoins(player, racer, 0);
                    }
                    player.sendMessage(text("You lost your vehicle", RED));
                }
            } else {
                vehicle.remove();
                if (clearCoinsOnDeath) {
                    setCoins(player, racer, 0);
                }
                player.sendMessage(text("You lost your vehicle", RED));
            }
            return true;
        }
        if (player.isGliding()) {
            player.setGliding(false);
            racer.remountCooldown = 60;
            if (clearCoinsOnDeath) {
                setCoins(player, racer, 0);
            }
            return true;
        }
        return false;
    }

    protected void onPlayerDamage(Player player, EntityDamageEvent event) {
        event.setCancelled(true);
        Racer racer = getRacer(player);
        if (racer == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Bukkit.getScheduler().runTask(plugin, () -> teleportToLastCheckpoint(player));
        }
    }

    protected void onPlayerVehicleDamage(Player player, Racer racer, EntityDamageEvent event) {
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent event2) {
            if (event2.getDamager() instanceof Firework) {
                return;
            }
        }
        if (racer.isInvincible()) {
            return;
        }
        if (event.getCause() == DamageCause.ENTITY_EXPLOSION || event.getCause() == DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
            damageVehicle(player, racer, 20.0, true);
        } else if (event.getCause() == DamageCause.CONTACT) {
            event.setCancelled(false);
        } else {
            damageVehicle(player, racer, event.getFinalDamage(), true);
        }
    }
}
