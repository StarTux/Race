package com.cavetale.race;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.minigame.MinigameFlag;
import com.cavetale.core.event.minigame.MinigameMatchCompleteEvent;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.font.Unicode;
import com.cavetale.core.money.Money;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.WardrobeItem;
import com.cavetale.mytems.item.font.Glyph;
import com.cavetale.mytems.item.medieval.WitchBroom;
import com.cavetale.mytems.util.Entities;
import com.cavetale.race.util.Rnd;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
import com.winthier.creative.review.MapReview;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import static com.cavetale.core.font.Unicode.subscript;
import static com.cavetale.core.font.Unicode.superscript;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;

@Getter
@RequiredArgsConstructor
public final class Race {
    private final RacePlugin plugin;
    private final BuildWorld buildWorld;
    protected final String worldName;
    private final World world;
    private final File saveFile;
    protected final Tag tag;
    protected Map<Vec3i, Goody> goodies = new HashMap<>();
    protected Map<Vec3i, Coin> coins = new HashMap<>();
    protected Map<Vec3i, Bogey> creepers = new HashMap<>();
    protected Map<Vec3i, Bogey> skeletons = new HashMap<>();
    protected Mytems coinItem;
    @Setter private boolean timeTrial;
    @Setter private boolean practice;
    private static NamespacedKey speedBonusKey;
    private static NamespacedKey stepHeightKey;;

    public static void staticEnable() {
        speedBonusKey = new NamespacedKey(RacePlugin.racePlugin(), "speed_bonus");
        stepHeightKey = new NamespacedKey(RacePlugin.racePlugin(), "step_height");
    }

    public void onEnable() {
        this.coinItem = tag.type.getCoinItem();
        if (tag.phase == Phase.IDLE) return;
        for (Racer racer : tag.racers) {
            Player player = racer.getPlayer();
            if (player == null) continue;
            updateVehicleSpeed(player, racer);
        }
    }

    public void onDisable() {
        for (Racer racer : tag.racers) {
            Player player = racer.getPlayer();
            if (player == null) continue;
            resetSpeed(player);
            if (player.getVehicle() != null) {
                player.getVehicle().remove();
            }
        }
        clearEntities();
    }

    private void log(String msg) {
        plugin.getLogger().info("[" + worldName + "] " + msg);
    }

    protected void tick(int ticks) {
        switch (tag.phase) {
        case EDIT: tickEdit(ticks); break;
        case START: tickStart(ticks); break;
        case RACE: tickRace(ticks); break;
        case FINISH: tickFinish(ticks); break;
        default: break;
        }
        tag.phaseTicks += 1;
    }

    public void setPhase(final Phase newPhase) {
        tag.phase = newPhase;
        tag.phaseTicks = 0;
        switch (newPhase) {
        case START:
            if (timeTrial || practice) {
                tag.totalPhaseTicks = 20 * 10;
            } else {
                tag.totalPhaseTicks = 20 * 30;
            }
            break;
        case IDLE:
            clearEntities();
            break;
        case FINISH:
            final MinigameMatchCompleteEvent event = new MinigameMatchCompleteEvent(MinigameMatchType.RACE);
            for (Racer racer : tag.racers) {
                if (!racer.finished) continue;
                event.addPlayerUuid(racer.uuid);
                if (racer.finishIndex == 0) {
                    event.addWinnerUuid(racer.uuid);
                }
            }
            if (plugin.getSave().isEvent()) {
                event.addFlags(MinigameFlag.EVENT);
            }
            event.callEvent();
        default:
            break;
        }
    }

    private void tickEdit(int ticks) {
        tag.area.highlight(world, ticks, 4, 1, loc -> world.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0));
        tag.spawnArea.highlight(world, ticks, 4, 4, loc -> world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0.0, 0.0, 0.0, 0.0));
        for (Checkpoint checkpoint : tag.checkpoints) {
            checkpoint.area.highlight(world, ticks, 4, 8, loc -> world.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0));
        }
        for (Vec3i v : tag.startVectors) {
            Location loc = v.toCenterFloorLocation(world);
            world.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
        updateEntities();
    }

    private void tickStart(int ticks) {
        pruneRacers();
        final int ticksLeft = tag.totalPhaseTicks - tag.phaseTicks;
        final long now = System.currentTimeMillis();
        if (ticksLeft < 0) {
            setPhase(Phase.RACE);
            tag.startTime = now;
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
                if (tag.type == RaceType.BROOM) {
                    player.getInventory().addItem(Mytems.WITCH_BROOM.createItemStack());
                    ((WitchBroom) Mytems.WITCH_BROOM.getMytem()).startFlying(player);
                }
                if (tag.type == RaceType.SONIC) {
                    player.getInventory().setBoots(Mytems.GOLDEN_SNEAKERS.createItemStack());
                }
                racer.lapStartTime = now;
            }
            return;
        }
        final int secondsLeft = ticksLeft / 20;
        if (ticksLeft % 20 == 0) {
            switch (secondsLeft) {
            case 5:
            case 4:
            case 3:
            case 2:
            case 1:
                for (Player player : getPresentPlayers()) {
                    player.showTitle(title(text("" + secondsLeft, GREEN),
                                           text("Get Ready", GREEN),
                                           times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.2f, 2.0f);
                }
                break;
            case 0:
                for (Player player : getPresentPlayers()) {
                    player.showTitle(title(text("GO!", GREEN, TextDecoration.ITALIC),
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
                player.sendMessage(text("Please wait...", RED));
                player.teleport(getStartLocation(racer));
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed(0.0f);
            }
        }
        updateEntities();
    }

    private void passThroughBlock(Player player, Racer racer, Block block) {
        if (racer.finished) return;
        Vec3i vec = Vec3i.of(block);
        long now = System.currentTimeMillis();
        if (racer.goodyCooldown < now && GoodyItem.count(player.getInventory()) < 2) {
            for (int y = -1; y < 2; y += 1) {
                for (int x = -1; x < 2; x += 1) {
                    for (int z = -1; z < 2; z += 1) {
                        onTouchGoody(player, racer, vec.add(x, y, z));
                        if (racer.goodyCooldown >= now || GoodyItem.count(player.getInventory()) >= 2) {
                            break;
                        }
                    }
                }
            }
        }
        for (int y = -1; y < 2; y += 1) {
            for (int x = -1; x < 2; x += 1) {
                for (int z = -1; z < 2; z += 1) {
                    onTouchCoin(player, racer, vec.add(x, y, z));
                }
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
        Firework firework = world.spawn(loc.add(0, 2, 0), Firework.class, e -> {
                e.setPersistent(false);
                Entities.setTransient(e);
                e.setFireworkMeta(Fireworks.simpleFireworkMeta());
            });
        firework.detonate();
        giveGoody(player, racer);
        world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 1.0f, 2.0f);
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
        ItemStack itemStack = choice == GoodyItem.COIN
            ? choice.createItemStack(coinItem.createIcon())
            : choice.createItemStack();
        player.getInventory().addItem(itemStack);
        player.sendMessage(choice.lore.get(0));
        return true;
    }

    private void onTouchCoin(Player player, Racer racer, Vec3i vector) {
        Coin coin = coins.get(vector);
        if (coin == null || coin.collectedBy.contains(player.getUniqueId())) return;
        coin.collectedBy.add(player.getUniqueId());
        if (coin.entity != null) {
            player.hideEntity(plugin, coin.entity);
        }
        final Location loc = coin.entity.getLocation();
        setCoins(player, racer, racer.coins + 1);
        player.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 0.1f, 2.0f);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        if (tag.type.playerIsDamageable()) {
            player.setHealth(Math.min(player.getHealth() + 1.0, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        }
    }

    protected void setCoins(Player player, Racer racer, int value) {
        if (tag.coins.isEmpty()) return;
        racer.coins = value;
        updateVehicleSpeed(player, racer);
        player.showTitle(title(empty(), textOfChildren(coinItem, text(value, GOLD)),
                               times(Duration.ofSeconds(0), Duration.ofSeconds(1), Duration.ofSeconds(1))));
    }

    protected void resetCoinsOnDeath(Player player, Racer racer) {
        final int loss = Math.max(1, racer.coins / 10);
        final int playerCoins = Math.max(0, racer.coins - loss);
        setCoins(player, racer, playerCoins);
    }

    protected void updateVehicleSpeed(Player player, Racer racer) {
        final int maxCoins = tag.coins.size();
        final double factor = racer.coins > 0 && maxCoins > 0
            ? Math.max(0.0, Math.min(1.0, (double) racer.coins / (double) maxCoins))
            : 0.0;
        if (tag.type == RaceType.BROOM) {
            ((WitchBroom) Mytems.WITCH_BROOM.getMytem()).getSessionData(player).setSpeedFactor(1.0 + 1.0 * factor);
        } else if (player.getVehicle() instanceof Attributable attributable) {
            AttributeInstance inst = attributable.getAttribute(Attribute.MOVEMENT_SPEED);
            if (inst == null) return;
            for (AttributeModifier modifier : List.copyOf(inst.getModifiers())) {
                if (speedBonusKey.equals(modifier.getKey())) {
                    inst.removeModifier(modifier);
                }
            }
            if (racer.coins >= 0) {
                inst.addModifier(new AttributeModifier(speedBonusKey, 1.5 * factor, Operation.MULTIPLY_SCALAR_1));
            }
        } else if (tag.type.playerHasSpeed()) {
            AttributeInstance inst = player.getAttribute(Attribute.MOVEMENT_SPEED);
            for (AttributeModifier modifier : List.copyOf(inst.getModifiers())) {
                if (speedBonusKey.equals(modifier.getKey())) {
                    inst.removeModifier(modifier);
                }
            }
            if (racer.coins >= 0) {
                inst.addModifier(new AttributeModifier(speedBonusKey, 1.5 * factor, Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    private int getEventScore(int finishIndex) {
        return Math.max(1, tag.racerCount - finishIndex);
    }

    private void progressCheckpoint(Player player, Racer racer) {
        racer.checkpointIndex += 1;
        if (racer.checkpointIndex >= tag.checkpoints.size()) {
            racer.checkpointIndex = 0;
            racer.lap += 1;
            if (racer.lap > tag.maxLap) {
                tag.maxLap += 1;
                tag.rareItemsAvailable += 1;
            }
            if (racer.lap >= tag.laps) {
                onFinish(player, racer);
            } else {
                final long now = System.currentTimeMillis();
                final long lapTime = now - racer.lapStartTime;
                final String timeString = formatTime(lapTime);
                final String lapString = "" + (racer.lap + 1);
                player.sendMessage(textOfChildren(text("Lap ", GRAY),
                                                  text(lapString, GREEN),
                                                  text("/", GRAY),
                                                  text(tag.laps, GREEN),
                                                  space(),
                                                  text(timeString, GREEN)));
                racer.lapStartTime = System.currentTimeMillis();
            }
        }
        if (tag.type == RaceType.ELYTRA && player.isGliding()) {
            player.fireworkBoost(new ItemStack(Material.FIREWORK_ROCKET));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.1f, 2.0f);
    }

    private void onFinish(Player player, Racer racer) {
        if (player.getVehicle() != null) player.getVehicle().remove();
        racer.finished = true;
        racer.finishTime = System.currentTimeMillis() - tag.startTime;
        racer.finishIndex = tag.finishIndex++;
        final String timeString = formatTime(racer.finishTime);
        final String rankString = "" + (racer.finishIndex + 1);
        plugin.getLogger().info("[" + worldName + "] " + player.getName() + " finished #" + rankString + " " + timeString);
        final Component announcement;
        if (timeTrial && plugin.hasRecords()) {
            final Records.InsertResult result = plugin.getRecords().set(buildWorld.getPath(), racer.getUuid(), racer.finishTime);
            plugin.getLogger().info("[" + worldName + "] Time trial pb:" + result.isPersonalBest() + " wr:" + result.isWorldRecord());
            if (result.isWorldRecord()) {
                announcement = textOfChildren(tag.type.getCoinItem(),
                                              space(),
                                              text("World Record! ", GOLD, BOLD),
                                              player.displayName(),
                                              space(),
                                              text(timeString, GOLD));
                player.showTitle(title(text("World Record!", GOLD), text(timeString, GOLD)));
            } else if (result.isPersonalBest()) {
                announcement = textOfChildren(tag.type.getCoinItem(),
                                              space(),
                                              text("Personal Best: ", GREEN, BOLD),
                                              player.displayName(),
                                              space(),
                                              Glyph.toComponent("" + result.getRow().getRank()),
                                              text(st("" + result.getRow().getRank())),
                                              space(),
                                              text(timeString, GREEN));
                player.showTitle(title(text("Personal Best", GREEN), text(timeString, GREEN)));
            } else {
                announcement = textOfChildren(tag.type.getCoinItem(),
                                              space(),
                                              player.displayName(),
                                              text(" finished in ", GRAY),
                                              text(timeString, GREEN));
            }
        } else {
            announcement = textOfChildren(tag.type.getCoinItem(),
                                          space(),
                                          player.displayName(),
                                          text(" finished #", GRAY),
                                          Glyph.toComponent(rankString),
                                          text(" in ", GRAY),
                                          text(timeString, GREEN));
            player.showTitle(title(textOfChildren(Glyph.toComponent(rankString), text(st(rankString), GRAY)),
                                   text(timeString, GREEN),
                                   times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        for (Player target : getPresentPlayers()) {
            target.sendMessage("");
            target.sendMessage(announcement);
            target.sendMessage("");
        }
        if (plugin.getSave().isEvent()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            int score = getEventScore(racer.finishIndex);
            int totalScore = plugin.getSave().getScores().compute(player.getUniqueId(), (u, i) -> (i != null ? i : 0) + score);
            player.sendMessage(text().color(GOLD)
                               .append(text("You earned "))
                               .append(text(score, BLUE))
                               .append(text(" points for a total of "))
                               .append(text(totalScore, BLUE)));
            if (racer.finishIndex == 0) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " "
                                       + String.join(" ", tag.type.getWinnerTitles()));
            }
            final int money = switch (racer.finishIndex) {
            case 0 -> 10_000;
            case 1 -> 5000;
            case 2 -> 2000;
            default -> 1000;
            } + (racer.coins * 10);
            if (money > 0) {
                plugin.getLogger().info(player.getName() + " receives " + money + " Coins");
                Money.get().give(player.getUniqueId(), (double) money, plugin, "Grand Prix");
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 0.5f, 2.0f);
        player.setGameMode(GameMode.SPECTATOR);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        if (player.getVehicle() != null) player.getVehicle().remove();
        clearInventory(player);
    }

    private void tickRace(int ticks) {
        if (tag.maxDuration > 0) {
            if (getTime() > tag.maxDuration * 1000L) {
                for (Player player : getPresentPlayers()) {
                    player.showTitle(title(text("Timeout", RED),
                                           text("The race is over", RED),
                                           times(Duration.ZERO,
                                                 Duration.ofSeconds(2),
                                                 Duration.ofSeconds(1))));
                    player.sendMessage(text("Timeout! The race is over", RED));
                }
                setPhase(Phase.FINISH); // MinigameMatchCompleteEvent
                stopRace();
                MapReview.start(world, buildWorld)
                    .remindAllOnce();
            }
        }
        if (tag.countRacers() == 0) {
                for (Player player : getPresentPlayers()) {
                    player.sendMessage(empty());
                    player.sendMessage(text("The race is finished", GREEN));
                    player.sendMessage(empty());
                }
                setPhase(Phase.FINISH); // MinigameMatchCompleteEvent
                stopRace();
                MapReview.start(world, buildWorld)
                    .remindAllOnce();
        }
        for (Player player : world.getPlayers()) {
            if (getRacer(player) != null) continue;
            if (!tag.area.contains(player.getLocation())) continue;
            if (player.isOp()) continue;
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        pruneRacers();
        for (Racer racer : tag.racers) tickRacer(racer, ticks);
        Collections.sort(tag.racers);
        for (int i = 0; i < tag.racers.size(); i += 1) {
            tag.racers.get(i).rank = i;
        }
        updateEntities();
    }

    private void tickRacer(Racer racer, int ticks) {
        if (!racer.racing) return;
        if (racer.finished) return;
        Player player = racer.getPlayer();
        if (player == null) return;
        Entity vehicle = player.getVehicle();
        Location loc = vehicle != null && vehicle.getType() != EntityType.ARMOR_STAND
            ? vehicle.getLocation()
            : player.getLocation();
        passThroughBlock(player, racer, loc.getBlock());
        Checkpoint checkpoint = tag.checkpoints.get(racer.checkpointIndex);
        if (checkpoint.area.contains(player.getLocation())) {
            progressCheckpoint(player, racer);
            if (!racer.finished) {
                checkpoint = tag.checkpoints.get(racer.checkpointIndex);
                setupCheckpoint(racer, checkpoint);
            }
        }
        try {
            Vec3i pos = Vec3i.of(loc.getBlock());
            Vec3i center = checkpoint.area.getCenter();
            racer.checkpointDistance = pos.distanceSquared(center);
            if ((ticks % 20) == 0) {
                List<Vec3i> vecs = checkpoint.area.enumerate();
                Location particleLocation = Rnd.pick(vecs).toCenterLocation(world).add(0.0, 0.5, 0.0);
                player.spawnParticle(Particle.FIREWORK, particleLocation, 20, 0.0, 0.0, 0.0, 0.2);
            }
            Vector playerDirection = loc.getDirection();
            Vec3i direction = center.subtract(pos);
            double playerAngle = Math.atan2(playerDirection.getZ(), playerDirection.getX());
            double targetAngle = Math.atan2((double) direction.z, (double) direction.x);
            boolean backwards = false;
            if (Double.isFinite(playerAngle) && Double.isFinite(targetAngle)) {
                double angle = targetAngle - playerAngle;
                if (angle > Math.PI) angle -= 2.0 * Math.PI;
                if (angle < -Math.PI) angle += 2.0 * Math.PI;
                if (Math.abs(angle) > Math.PI * 0.5) {
                    backwards = true;
                    racer.backwardsTicks += 1;
                    if (racer.backwardsTicks > 60 && (tag.phaseTicks % 30) < 15) {
                        player.sendActionBar(text("TURN AROUND", DARK_RED, BOLD));
                    } else {
                        player.sendActionBar(Mytems.ARROW_DOWN.component);
                    }
                } else if (angle < Math.PI * -0.25) {
                    player.sendActionBar(Mytems.ARROW_LEFT.component);
                } else if (angle > Math.PI * 0.25) {
                    player.sendActionBar(Mytems.ARROW_RIGHT.component);
                } else if (angle < Math.PI * -0.125) {
                    player.sendActionBar(Mytems.TURN_LEFT.component);
                } else if (angle > Math.PI * 0.125) {
                    player.sendActionBar(Mytems.TURN_RIGHT.component);
                } else {
                    player.sendActionBar(Mytems.ARROW_UP.component);
                }
            }
            if (!backwards) racer.backwardsTicks = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (tag.type == RaceType.ELYTRA) {
            if (player.isGliding()) {
                world.spawnParticle(Particle.WAX_OFF, player.getLocation(), 1, 0.0, 0.0, 0.0, 0.0);
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
                        updateVehicleSpeed(player, racer);
                    }
                }
            }
        }
        if (tag.type == RaceType.SONIC) {
            if (player.isSwimming() || player.getLocation().getBlock().isLiquid()) {
                teleportToLastCheckpoint(player);
                resetCoinsOnDeath(player, racer);
                player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_JUMP, SoundCategory.MASTER, 1.0f, 1.0f);
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
        if (tag.type == RaceType.BROOM && player.getVehicle() != null) {
            updateVehicleSpeed(player, racer);
        }
    }

    private void setupCheckpoint(Racer racer, Checkpoint checkpoint) {
        racer.checkpointIndex = tag.checkpoints.indexOf(checkpoint);
        Vec3i center = checkpoint.area.getCenter();
        Vec3i pos = Vec3i.of(racer.getPlayer().getLocation().getBlock());
        racer.checkpointDistance = pos.distanceSquared(center);
        Player player = racer.getPlayer();
        player.setCompassTarget(center.toBlock(world).getLocation());
    }

    protected static boolean containsHorizontal(Cuboid cuboid, Location location) {
        final int x = location.getBlockX();
        final int z = location.getBlockZ();
        return x >= cuboid.ax && x <= cuboid.bx
            && z >= cuboid.az && z <= cuboid.bz;
    }

    protected static boolean containsHorizontal(Cuboid cuboid, Block block) {
        final int x = block.getX();
        final int z = block.getZ();
        return x >= cuboid.ax && x <= cuboid.bx
            && z >= cuboid.az && z <= cuboid.bz;
    }

    protected void pruneRacers() {
        tag.racers.removeIf(racer -> {
                if (racer.finished) return false;
                Player player = racer.getPlayer();
                if (player == null || !player.isOnline()) return true;
                if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
                    resetSpeed(player);
                    return true;
                }
                Location loc = player.getLocation();
                if (!isIn(loc.getWorld()) || !containsHorizontal(tag.area, loc)) {
                    resetSpeed(player);
                    return true;
                }
                return false;
            });
    }

    protected void tickFinish(int ticks) {
        if (tag.phaseTicks < 20 * 60 && !world.getPlayers().isEmpty()) return;
        MapReview.stop(world);
        setPhase(Phase.IDLE);
        if (plugin.isRaceServer()) {
            if (plugin.getSave().isEvent()) {
                plugin.scoreRanking(false);
            }
            removeAllPlayers();
            plugin.getRaces().unloadWorld(world);
            Files.deleteWorld(world);
        }
    }

    public void save() {
        Json.save(saveFile, tag, true);
    }

    public boolean isIn(World theWorld) {
        return theWorld.equals(world);
    }

    public boolean isIn(String theWorldName) {
        return worldName.equals(theWorldName);
    }

    public Location getSpawnLocation() {
        return tag.spawnLocation.toLocation(getWorld());
    }

    public String listString() {
        return tag.phase
            + " " + tag.type
            + " w:" + worldName
            + " bw:" + buildWorld.getPath();
    }

    public void setArea(Cuboid cuboid) {
        tag.area = cuboid;
    }

    public void setSpawnLocation(Location loc) {
        tag.spawnLocation = Position.of(loc);
    }

    public void setSpawnArea(Cuboid area) {
        tag.spawnArea = area;
    }

    public List<Checkpoint> getCheckpoints() {
        return new ArrayList<>(tag.checkpoints);
    }

    public void setCheckpoints(List<Checkpoint> checkpoints) {
        tag.checkpoints = new ArrayList<>(checkpoints);
    }

    public List<Player> getEligiblePlayers() {
        return getWorld().getPlayers().stream()
            .filter(p -> !(p.isPermissionSet("group.streamer") && p.hasPermission("group.streamer")))
            .filter(p -> containsHorizontal(tag.area, p.getLocation()))
            .collect(Collectors.toList());
    }

    public List<Player> getPresentPlayers() {
        return getWorld().getPlayers().stream()
            .filter(p -> containsHorizontal(tag.area, p.getLocation()))
            .collect(Collectors.toList());
    }

    private Location getStartLocation(Racer racer) {
        Vec3i vector = racer.startVector;
        Checkpoint firstCheckpoint = tag.checkpoints.get(0);
        float yaw = Yaw.yaw(vector, firstCheckpoint.area);
        Location location = vector.toCenterFloorLocation(getWorld());
        location.setYaw(yaw);
        return location;
    }

    private static int simpleDistance(Vec3i a, Vec3i b) {
        return Math.max(Math.abs(b.y - a.y),
                        Math.max(Math.abs(b.x - a.x),
                                 Math.abs(b.z - a.z)));
    }

    public void startRace() {
        startRace(getEligiblePlayers());
    }

    public void startRace(List<Player> players) {
        tag.racers.clear();
        Vec3i center = tag.checkpoints.get(0).area.getCenter();
        Collections.sort(tag.startVectors, (a, b) -> Integer.compare(simpleDistance(center, a),
                                                                     simpleDistance(center, b)));
        int startVectorIndex = 0;
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
            //player.getInventory().setItem(7, GoodyItem.WAYPOINT.createItemStack());
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(getStartLocation(racer));
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setFlySpeed(0.0f);
            player.setWalkSpeed(0f);
            player.showTitle(title(text("Race", GREEN, TextDecoration.ITALIC),
                                   text("The Race Begins", GREEN),
                                   times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(1))));
            resetHealthAndFood(player);
            player.getInventory().setItem(0, GoodyItem.RETURN.createItemStack());
            if (tag.type == RaceType.STRIDER) {
                player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK));
            }
            if (tag.type == RaceType.PIG) {
                player.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK));
            }
            if (tag.type == RaceType.HORSE) {
                player.getInventory().setHelmet(Mytems.COWBOY_HAT.createItemStack());
            } else if (tag.type == RaceType.CAMEL) {
                player.getInventory().setHelmet(Mytems.STRAW_HAT.createItemStack());
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
        final long seconds = millis / 1000L;
        final long minutes = seconds / 60L;
        return String.format("%02d'%02d\"%03d", minutes, seconds % 60L, millis % 1000L);
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
                resetSpeed(player);
            }
        }
        clearEntities();
    }

    public void removeAllPlayers() {
        for (Player player : world.getPlayers()) {
            player.eject();
            player.teleport(plugin.getLobbyWorld().getSpawnLocation());
        }
    }

    public static void resetSpeed(Player player) {
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        AttributeInstance inst = player.getAttribute(Attribute.MOVEMENT_SPEED);
        for (AttributeModifier modifier : List.copyOf(inst.getModifiers())) {
            if (speedBonusKey.equals(modifier.getKey())) {
                inst.removeModifier(modifier);
            } else if (stepHeightKey.equals(modifier.getKey())) {
                inst.removeModifier(modifier);
            }
        }
        player.setFallDistance(0);
    }

    public static void resetHealthAndFood(Player player) {
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public Checkpoint getLastCheckpoint(Racer racer) {
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

    private static Block getBottomBlock(Cuboid cuboid, World world) {
        return world.getBlockAt((cuboid.ax + cuboid.bx) / 2, cuboid.ay, (cuboid.az + cuboid.bz) / 2);
    }

    public void teleportToLastCheckpoint(Player player) {
        Racer racer = getRacer(player);
        if (racer == null) return;
        final Location loc;
        if (racer.checkpointIndex == 0 && racer.lap == 0) {
            loc = racer.startVector.toCenterFloorLocation(getWorld());
        } else {
            final Checkpoint checkpoint = getLastCheckpoint(racer);
            if (checkpoint.area.equals(Cuboid.ZERO)) {
                loc = racer.startVector.toCenterFloorLocation(getWorld());
            } else {
                Block block = getBottomBlock(checkpoint.area, getWorld());
                while (!block.isPassable()) block = block.getRelative(0, 1, 0);
                loc = block.getLocation().add(0.5, 0.0, 0.5);
            }
        }
        final Location ploc = player.getLocation();
        loc.setYaw(ploc.getYaw());
        loc.setPitch(ploc.getPitch());
        if (player.getVehicle() != null) {
            player.getVehicle().remove();
        }
        player.teleport(loc);
        player.sendActionBar(text("Returned to last checkpoint!", LIGHT_PURPLE));
        player.playSound(loc, Sound.ENTITY_ENDER_PEARL_THROW, SoundCategory.MASTER, 0.5f, 1.0f);
        if (tag.type == RaceType.BROOM) {
            ((WitchBroom) Mytems.WITCH_BROOM.getMytem()).startFlying(player);
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
        resetSpeed(player);
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
        int count = 0;
        Block end = to.getBlock();
        while (iter.hasNext()) {
            if (count++ >= 16) break;
            Block block = iter.next();
            passThroughBlock(player, racer, block);
            if (block.equals(end)) break;
        }
        Checkpoint checkpoint = tag.checkpoints.get(racer.checkpointIndex);
        BoundingBox bb = checkpoint.area.toBoundingBox();
        Vector a = from.toVector();
        Vector b = to.toVector();
        RayTraceResult rayTraceResult = bb.rayTrace(a, b.clone().subtract(a), a.distance(b));
        if (rayTraceResult != null) {
            progressCheckpoint(player, racer);
            if (!racer.finished) {
                checkpoint = tag.checkpoints.get(racer.checkpointIndex);
                setupCheckpoint(racer, checkpoint);
            }
        }
    }

    protected void updateEntities() {
        if (!timeTrial || tag.phase == Phase.EDIT) {
            for (Vec3i vector : tag.goodies) {
                Goody goody = goodies.computeIfAbsent(vector, v -> new Goody(v));
                if (goody.cooldown > 0) {
                    goody.cooldown -= 1;
                    continue;
                }
                if (goody.entity == null || goody.entity.isDead()) {
                    goody.entity = null;
                    Location location = goody.where.toCenterLocation(getWorld());
                    if (!location.isChunkLoaded()) continue;
                    goody.entity = location.getWorld().spawn(location, ItemDisplay.class, e -> {
                            e.setPersistent(false);
                            Entities.setTransient(e);
                            e.setBrightness(new ItemDisplay.Brightness(15, 15));
                            e.setShadowRadius(1.0f);
                            e.setShadowStrength(0.5f);
                            e.setBrightness(new ItemDisplay.Brightness(15, 15));
                            e.setItemStack(Mytems.BOSS_CHEST.createIcon());
                        });
                } else if (goody.entity instanceof ItemDisplay itemDisplay) {
                    itemDisplay.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f),
                                                                     new AxisAngle4f(goody.rotation, 0f, 1f, 0f),
                                                                     new Vector3f(1f, 1f, 1f),
                                                                     new AxisAngle4f(0f, 0f, 0f, 0f)));
                    goody.rotation += 0.1f;
                }
            }
        }
        for (Vec3i vector : tag.coins) {
            Coin coin = coins.computeIfAbsent(vector, v -> new Coin(v));
            if (coin.entity == null || coin.entity.isDead()) {
                coin.entity = null;
                final Location location = coin.where.toCenterLocation(getWorld());
                if (!location.isChunkLoaded()) continue;
                coin.entity = location.getWorld().spawn(location, ItemDisplay.class, e -> {
                        e.setPersistent(false);
                        Entities.setTransient(e);
                        e.setBrightness(new ItemDisplay.Brightness(15, 15));
                        e.setShadowRadius(0.35f);
                        e.setShadowStrength(0.5f);
                        e.setBillboard(ItemDisplay.Billboard.CENTER);
                        e.setBrightness(new ItemDisplay.Brightness(15, 15));
                        e.setItemStack(coinItem.createIcon());
                    });
                for (UUID uuid : coin.collectedBy) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        player.hideEntity(plugin, coin.entity);
                    }
                }
            }
        }
        for (Cuboid cuboid : tag.creepers) {
            Vec3i v = cuboid.getMin();
            Vec3i w = cuboid.getMax();
            Bogey bogey = creepers.computeIfAbsent(v, vv -> new Bogey(vv));
            if (bogey.entity == null || bogey.entity.isDead() || bogey.entity.getLocation().getBlock().isLiquid()) {
                if (bogey.cooldown > 0) {
                    bogey.cooldown -= 1;
                    continue;
                } else {
                    bogey.entity = null;
                    final boolean backwards = ThreadLocalRandom.current().nextBoolean();
                    Location location = !backwards
                        ? v.toCenterFloorLocation(getWorld())
                        : w.toCenterFloorLocation(getWorld());
                    if (!location.isChunkLoaded()) continue;
                    bogey.entity = location.getWorld().spawn(location, Creeper.class, e -> {
                            e.setPersistent(false);
                            Entities.setTransient(e);
                            e.setMaxFuseTicks(1);
                        });
                    bogey.backwards = backwards;
                    bogey.cooldown = 20 * 10;
                }
            } else if (bogey.entity instanceof Creeper creeper) {
                if (creeper.getTarget() != null) {
                    continue;
                }
                if (!bogey.backwards) {
                    if (w.contains(creeper.getLocation())) {
                        bogey.backwards = true;
                    } else {
                        creeper.getPathfinder().moveTo(w.toCenterFloorLocation(getWorld()));
                    }
                } else {
                    if (v.contains(creeper.getLocation())) {
                        bogey.backwards = false;
                    } else {
                        creeper.getPathfinder().moveTo(v.toCenterFloorLocation(getWorld()));
                    }
                }
            }
        }
        for (Cuboid cuboid : tag.skeletons) {
            Vec3i v = cuboid.getMin();
            Vec3i w = cuboid.getMax();
            Bogey bogey = skeletons.computeIfAbsent(v, vv -> new Bogey(vv));
            if (bogey.cooldown > 0) {
                bogey.cooldown -= 1;
                continue;
            }
            if (bogey.entity == null || bogey.entity.isDead() || bogey.entity.getLocation().getBlock().isLiquid()) {
                bogey.entity = null;
                final boolean backwards = ThreadLocalRandom.current().nextBoolean();
                Location location = !backwards
                    ? v.toCenterFloorLocation(getWorld())
                    : w.toCenterFloorLocation(getWorld());
                if (!location.isChunkLoaded()) continue;
                bogey.entity = location.getWorld().spawn(location, Skeleton.class, e -> {
                        e.setPersistent(false);
                        Entities.setTransient(e);
                        e.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                        e.getEquipment().setChestplate(null);
                        e.getEquipment().setLeggings(null);
                        e.getEquipment().setBoots(null);
                        e.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                        e.setShouldBurnInDay(false);
                    });
                bogey.backwards = backwards;
            } else if (bogey.entity instanceof Skeleton skeleton) {
                if (skeleton.getTarget() != null) {
                    continue;
                }
                if (!bogey.backwards) {
                    if (w.contains(skeleton.getLocation())) {
                        bogey.backwards = true;
                    } else {
                        skeleton.getPathfinder().moveTo(w.toCenterFloorLocation(getWorld()));
                    }
                } else {
                    if (v.contains(skeleton.getLocation())) {
                        bogey.backwards = false;
                    } else {
                        skeleton.getPathfinder().moveTo(v.toCenterFloorLocation(getWorld()));
                    }
                }
            }
        }
    }

    protected void clearEntities() {
        for (Goody goody : goodies.values()) {
            if (goody.entity != null) {
                goody.entity.remove();
                goody.entity = null;
            }
        }
        goodies.clear();
        for (Coin coin : coins.values()) {
            if (coin.entity != null) {
                coin.entity.remove();
                coin.entity = null;
            }
        }
        coins.clear();
        for (Bogey bogey : creepers.values()) {
            if (bogey.entity != null) {
                bogey.entity.remove();
                bogey.entity = null;
            }
        }
        creepers.clear();
        for (Bogey bogey : skeletons.values()) {
            if (bogey.entity != null) {
                bogey.entity.remove();
                bogey.entity = null;
            }
        }
        skeletons.clear();
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
        final int radius = 8;
        HashSet<Vec2i> chunksToLoad = new HashSet<>();
        for (Checkpoint checkpoint : tag.checkpoints) {
            int ax = (checkpoint.area.getAx() >> 4) - radius;
            int bx = (checkpoint.area.getBx() >> 4) + radius;
            int az = (checkpoint.area.getAz() >> 4) - radius;
            int bz = (checkpoint.area.getBz() >> 4) + radius;
            for (int z = az; z <= bz; z += 1) {
                for (int x = ax; x <= bx; x += 1) {
                    chunksToLoad.add(new Vec2i(x, z));
                }
            }
        }
        log("Creating " + chunksToLoad.size() + " chunk tickets...");
        int count = 0;
        for (Vec2i vec : chunksToLoad) {
            if (world.addPluginChunkTicket(vec.x, vec.z, plugin)) {
                count += 1;
            }
        }
        log("" + count + "/" + chunksToLoad.size() + " chunk tickets created!");
    }

    protected void unloadAllRaceChunks() {
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
            if (tag.type.mountIsAlive() && player.getVehicle() instanceof Attributable ride) {
                double speed = ride.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
                lines.add(textOfChildren(text(tiny("speed "), GRAY), text((int) Math.round(speed * 100.0), GOLD)));
            } else if (tag.type.playerHasSpeed()) {
                double speed = player.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
                lines.add(textOfChildren(text(tiny("speed "), GRAY), text((int) Math.round(speed * 100.0), GOLD)));
            }
        }
        String time = formatTimeShort(getTime());
        String maxTime = tag.maxDuration > 0
            ? formatTimeShort(tag.maxDuration * 1000L)
            : "" + Unicode.INFINITY.character;
        lines.add(textOfChildren(text(tiny("time "), GRAY), text(time, YELLOW), text("/", WHITE), text(maxTime, RED)));
        if (!practice && !timeTrial) {
            for (Racer racer : racers) {
                Player racerPlayer = racer.getPlayer();
                int index = i++;
                if (index > 9) break;
                if (racer.finished) {
                    lines.add(textOfChildren(text(index + 1, GOLD),
                                             space(),
                                             text(racer.name, YELLOW)));
                } else if (!tag.coins.isEmpty()) {
                    lines.add(textOfChildren(text((index + 1) + " ", WHITE),
                                             coinItem.component,
                                             text(racer.coins, YELLOW),
                                             space(),
                                             text(racer.name, GRAY)));
                } else {
                    lines.add(textOfChildren(text(index + 1, WHITE),
                                             space(),
                                             text(racer.name, GRAY)));
                }
            }
        }
    }

    private String st(String in) {
        if (in.endsWith("11")) return "th";
        if (in.endsWith("12")) return "th";
        if (in.endsWith("13")) return "th";
        if (in.endsWith("1")) return "st";
        if (in.endsWith("2")) return "nd";
        if (in.endsWith("3")) return "rd";
        return "th";
    }

    protected void onPlayerHudStart(Player player, PlayerHudEvent event) {
        Racer racer = getRacer(player);
        if (racer == null || !racer.racing) return;
        final int ticksLeft = tag.totalPhaseTicks - tag.phaseTicks;
        event.bossbar(PlayerHudPriority.HIGH, text("Get Ready", GREEN),
                      BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                      1f - ((float) tag.phaseTicks / (float) tag.totalPhaseTicks));
    }

    protected void onPlayerHud(Player player, PlayerHudEvent event) {
        Racer racer = getRacer(player);
        if (racer == null || !racer.racing) return;
        final long time = racer.finished
            ? racer.finishTime
            : System.currentTimeMillis() - tag.startTime;
        final long seconds = time / 1000L;
        final long minutes = seconds / 60L;
        List<Component> titleComponents = new ArrayList<>();
        if (tag.racers.size() > 1 && !practice && !timeTrial) {
            final String rank = "" + (racer.rank + 1);
            titleComponents.add(textOfChildren(Glyph.toComponent(rank),
                                               text(subscript(st(rank) + "/" + tag.racers.size()), GRAY)));
        }
        if (tag.laps > 1) {
            final int showLap = Math.min(tag.laps, racer.lap + 1);
            titleComponents.add(textOfChildren(text(tiny("lap"), GRAY),
                                               text(superscript(showLap) + "/" + subscript(tag.laps), GOLD)));
        }
        String timeString = String.format("%02d'%02d\"%03d", minutes, seconds % 60L, time % 1000L);
        titleComponents.add(textOfChildren(text(tiny("time"), GRAY),
                                           text(timeString, GREEN)));
        if (!tag.coins.isEmpty()) {
            titleComponents.add(textOfChildren(coinItem,
                                               text(racer.coins, GOLD),
                                               text("/", GRAY),
                                               text(tag.coins.size(), GOLD)));
        }
        event.bossbar(PlayerHudPriority.HIGH, join(separator(space()), titleComponents),
                      BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                      (float) racer.checkpointIndex / (float) tag.checkpoints.size());
    }

    protected void onPlayerHudFinish(Player player, PlayerHudEvent event) {
        Racer racer = getRacer(player);
        if (racer == null || !racer.racing) return;
        event.bossbar(PlayerHudPriority.HIGH,
                      textOfChildren(text(tiny("your time "), GRAY),
                                     (racer.finished
                                      ? text(formatTime(racer.finishTime), GREEN)
                                      : text("N/A", DARK_RED))),
                      BossBar.Color.GREEN, BossBar.Overlay.PROGRESS,
                      1f);
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
                        resetCoinsOnDeath(player, racer);
                    }
                }
            } else {
                vehicle.remove();
                racer.remountCooldown = 60;
                if (clearCoinsOnDeath) {
                    resetCoinsOnDeath(player, racer);
                }
            }
            return true;
        }
        if (player.isGliding()) {
            player.setGliding(false);
            racer.remountCooldown = 60;
            if (clearCoinsOnDeath) {
                resetCoinsOnDeath(player, racer);
            }
            return true;
        }
        return false;
    }

    protected void onPlayerDamage(Player player, EntityDamageEvent event) {
        Racer racer = getRacer(player);
        if (racer == null) {
            event.setCancelled(true);
            return;
        }
        if (racer.isInvincible()) {
            event.setCancelled(true);
        } else if (tag.type == RaceType.SONIC && event.getCause() == DamageCause.FALL) {
            event.setCancelled(true);
        } else if (tag.type.playerIsDamageable()) {
            return;
        } else {
            event.setCancelled(true);
        }
    }

    protected void onPlayerDeath(Player player, PlayerDeathEvent event) {
        Racer racer = getRacer(player);
        if (racer == null) {
            return;
        }
        resetCoinsOnDeath(player, racer);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(false);
        event.setDroppedExp(0);
    }

    protected void onPlayerRespawn(Player player, PlayerRespawnEvent event) {
        if (tag.phase == Phase.IDLE) return;
        Racer racer = getRacer(player);
        if (racer == null) {
            event.setRespawnLocation(getSpawnLocation());
            return;
        }
        Checkpoint checkpoint = getLastCheckpoint(racer);
        Location loc;
        if (checkpoint.area.equals(Cuboid.ZERO)) {
            loc = racer.startVector.toCenterFloorLocation(getWorld());
        } else {
            Block block = getBottomBlock(checkpoint.area, getWorld());
            while (!block.isPassable()) block = block.getRelative(0, 1, 0);
            loc = block.getLocation().add(0.5, 0.0, 0.5);
        }
        Location ploc = player.getLocation();
        loc.setYaw(ploc.getYaw());
        loc.setPitch(ploc.getPitch());
        event.setRespawnLocation(loc);
    }

    protected void onPlayerVehicleDamage(Player player, Racer racer, EntityDamageEvent event) {
        if (tag.phase == Phase.IDLE) return;
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

    protected void onPlayerJump(Player player, PlayerJumpEvent jump) {
        if (tag.phase == Phase.IDLE) return;
        Racer racer = getRacer(player);
        if (racer == null || !racer.racing) return;
    }

    protected void onEntityExplode(Entity entity, EntityExplodeEvent event) {
        // This event appears not to fire
        for (Bogey bogey : creepers.values()) {
            if (entity == bogey.entity) {
                bogey.cooldown = 20 * 30;
                return;
            }
        }
    }

    protected void onProjectileHit(Projectile proj, ProjectileHitEvent event) {
        if (proj instanceof AbstractArrow arrow) {
            if (arrow.getShooter() instanceof Player) {
                proj.getWorld().createExplosion(proj, 3.0f);
                proj.remove();
            } else if (event.getHitEntity() != null && !(event.getHitEntity() instanceof Player)) {
                event.setCancelled(true);
                proj.remove();
            }
        }
    }

    public static Race inWorld(String worldName) {
        return RacePlugin.racePlugin().getRaces().inWorld(worldName);
    }

    public static Race inWorld(World world) {
        return RacePlugin.racePlugin().getRaces().inWorld(world);
    }
}
