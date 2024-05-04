package com.cavetale.race;

import com.cavetale.mytems.Mytems;
import com.cavetale.worldmarker.util.Tags;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import static com.cavetale.mytems.util.Items.tooltip;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static org.bukkit.Sound.*;
import static org.bukkit.SoundCategory.*;
import static org.bukkit.attribute.Attribute.*;

@RequiredArgsConstructor
public enum GoodyItem {
    WAYPOINT(Category.UNAVAILABLE,
             () -> new ItemStack(Material.COMPASS),
             List.of(text("Points at next Checkpoint", YELLOW)),
             (race, player, racer) -> 0.0,
             (race, player, racer, item) -> true),
    RETURN(Category.UNAVAILABLE,
           Mytems.REDO::createIcon,
           List.of(text("Return to Checkpoint", LIGHT_PURPLE)),
           (race, player, racer) -> 0.0,
           (race, player, racer, item) -> {
               race.teleportToLastCheckpoint(player);
               return true;
           }),
    BOMB(Category.REGULAR,
         Mytems.BOMB::createIcon,
         List.of(text("Explosive Trap", DARK_RED)),
         (race, player, racer) -> 0.05 / (double) race.tag.racerCount,
         (race, player, racer, item) -> {
             item.subtract(1);
             TNTPrimed tnt = player.getWorld().spawn(player.getEyeLocation(), TNTPrimed.class, e -> {
                     e.setPersistent(false);
                     e.setFuseTicks(60);
                 });
             player.playSound(tnt.getLocation(), ENTITY_TNT_PRIMED, MASTER, 1.0f, 1.5f);
             return true;
         }),
    HEAL(Category.LIVING,
         () -> potionItem(Material.POTION, PotionType.INSTANT_HEAL),
         List.of(text("Health Boost", AQUA)),
         (race, player, racer) -> 1.0,
         (race, player, racer, item) -> {
             if (player.getVehicle() instanceof LivingEntity target) {
                 item.subtract(1);
                 target.setHealth(target.getAttribute(GENERIC_MAX_HEALTH).getValue());
                 target.removePotionEffect(PotionEffectType.POISON);
                 Location loc = player.getLocation();
                 Location locAhead = player.getEyeLocation().add(loc.getDirection().normalize());
                 player.playSound(loc, ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.0f, 2.0f);
                 player.spawnParticle(Particle.HEART, locAhead, 8, 0.5, 0.5, 0.5, 0.0);
                 player.sendMessage(text("Your ride was healed!", AQUA));
             }
             return true;
         }),
    SPEED(Category.LIVING,
          () -> potionItem(Material.POTION, PotionType.SPEED),
          List.of(text("Speed Boost", BLUE)),
          (race, player, racer) -> {
              return switch (racer.rank) {
              case 0 -> 0.0;
              case 1 -> 0.25;
              case 2 -> 0.5;
              default -> 1.0;
              };
          },
          (race, player, racer, item) -> {
              if (player.getVehicle() instanceof LivingEntity target) {
                  item.subtract(1);
                  target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                                                          200 + racer.rank * 20, 2, true, true, true));
                  Location loc = player.getLocation();
                  Location locAhead = player.getEyeLocation().add(loc.getDirection().normalize());
                  player.playSound(loc, ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2.0f, 2.0f);
                  player.spawnParticle(Particle.SPELL_MOB, locAhead, 32, 0.5, 0.5, 0.5, 1.0);
                  player.sendMessage(text("Your ride got a speed boost!", BLUE));
              }
              return true;
          }),
    SLOW(Category.LIVING,
         () -> potionItem(Material.LINGERING_POTION, PotionType.SLOWNESS),
         List.of(text("Slowness Trap", GRAY)),
         (race, player, racer) -> 0.1,
         (race, player, racer, item) -> false),
    POISON(Category.LIVING,
         () -> potionItem(Material.LINGERING_POTION, PotionType.POISON),
         List.of(text("Poison Trap", GRAY)),
         (race, player, racer) -> 0.1,
         (race, player, racer, item) -> false),
    CROSSBOW(Category.REGULAR,
             () -> loadedCrossbow(),
             List.of(text("Exploding Arrow", RED)),
             (race, player, racer) -> 0.15 / race.tag.racerCount,
             (race, player, racer, item) -> {
                 item.subtract(1);
                 player.launchProjectile(SpectralArrow.class);
                 player.playSound(player.getLocation(), ITEM_CROSSBOW_SHOOT, 1.0f, 1.5f);
                 return true;
             }),
    STAR(Category.REGULAR,
         Mytems.STAR::createIcon,
         List.of(text("Invincibility", GOLD)),
         (race, player, racer) -> {
             return racer.rank == 0
                 ? 0.1
                 : (double) racer.rank / (double) race.tag.racerCount;
         },
         (race, player, racer, item) -> {
             item.subtract(1);
             int duration = 200;
             racer.invincibleTicks = duration;
             player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                                                     duration, 0, true, true, true));
             Entity vehicle = player.getVehicle();
             if (vehicle instanceof LivingEntity target) {
                 target.removePotionEffect(PotionEffectType.POISON);
                 target.removePotionEffect(PotionEffectType.SLOW);
                 target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                                                         duration, 0, true, true, true));
             } else if (vehicle != null) {
                 vehicle.setGlowing(true);
             }
             player.playSound(player.getLocation(), BLOCK_RESPAWN_ANCHOR_CHARGE, MASTER, 1.0f, 1.5f);
             player.playSound(player.getLocation(), BLOCK_RESPAWN_ANCHOR_CHARGE, MASTER, 1.0f, 2.0f);
             return true;
         }),
    COIN(Category.REGULAR,
         Mytems.GOLDEN_COIN::createIcon,
         List.of(text("Coin", GOLD),
                 text("Collect up to", GRAY),
                 text(Race.MAX_COINS + " coins to", GRAY),
                 text("increase speed", GRAY)),
         (race, player, racer) -> race.tag.coins.isEmpty() ? 0.0 : 1.0,
         (race, player, racer, item) -> {
             item.subtract(1);
             race.setCoins(player, racer, racer.coins + 1 + racer.rank);
             player.playSound(player.getLocation(), ENTITY_PLAYER_LEVELUP, MASTER, 0.5f, 2.0f);
             return true;
         }),
    LIGHTNING(Category.RARE,
              Mytems.LIGHTNING::createIcon,
              List.of(text("Strike Lighning", AQUA),
                      text("Strike everyone ahead of", GRAY),
                      text("you with lightning!", GRAY)),
              (race, player, racer) -> {
                  return racer.rank == 0
                      ? 0.0
                      : 0.05 * ((double) racer.rank / (double) race.tag.racerCount);
              },
              (race, player, racer, item) -> {
                  item.subtract(1);
                  int count = 0;
                  for (Racer otherRacer : race.tag.racers) {
                      if (racer == otherRacer) continue;
                      if (racer.rank <= otherRacer.rank) continue;
                      Player otherPlayer = otherRacer.getPlayer();
                      if (otherPlayer == null) continue;
                      if (race.damageVehicle(otherPlayer, otherRacer, 10.0, false)) {
                          otherPlayer.getWorld().strikeLightningEffect(otherPlayer.getLocation());
                          otherPlayer.sendMessage(text("Struck by lightning!", RED));
                          count += 1;
                      }
                  }
                  player.sendMessage(text(count + " players ahead of you were struck with lightning!", GOLD));
                  return true;
              });

    public final Category category;
    private final Supplier<ItemStack> baseItemSupplier;
    protected final List<Component> lore;
    protected final GoodyPredicate goodyPredicate;
    protected final GoodyConsumer goodyConsumer;

    public enum Category {
        UNAVAILABLE,
        LIVING,
        REGULAR,
        RARE;
    }

    @FunctionalInterface
    protected interface GoodyConsumer {
        /**
         * Use this item.
         * @return true if the event should be cancelled.  False
         * otherwise.
         */
        boolean use(Race race, Player player, Racer racer, ItemStack item);
    }

    @FunctionalInterface
    protected interface GoodyPredicate {
        double chance(Race race, Player player, Racer racer);
    }

    public ItemStack createItemStack() {
        return createItemStack(baseItemSupplier.get());
    }

    public ItemStack createItemStack(ItemStack base) {
        ItemStack result = tooltip(base, lore);
        result.editMeta(meta -> {
                Tags.set(meta.getPersistentDataContainer(),
                         new NamespacedKey(RacePlugin.instance, "goody"),
                         name());
            });
        return result;
    }

    public static GoodyItem of(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String key = Tags.getString(meta.getPersistentDataContainer(),
                                    new NamespacedKey(RacePlugin.instance, "goody"));
        if (key == null) return null;
        try {
            return valueOf(key);
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    public static int count(Inventory inv) {
        int count = 0;
        for (ItemStack item : inv) {
            GoodyItem it = of(item);
            if (it == null || it.category == Category.UNAVAILABLE) continue;
            count += item.getAmount();
        }
        return count;
    }

    private static ItemStack potionItem(ItemStack itemStack, PotionType potionType) {
        PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
        meta.setBasePotionType(potionType);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private static ItemStack potionItem(Material material, PotionType potionType) {
        ItemStack itemStack  = new ItemStack(material);
        itemStack.editMeta(meta -> {
                if (meta instanceof PotionMeta potionMeta) {
                    potionMeta.setBasePotionType(potionType);
                }
            });
        return itemStack;
    }

    private static ItemStack loadedCrossbow() {
        ItemStack itemStack = new ItemStack(Material.CROSSBOW);
        itemStack.editMeta(meta -> {
                if (meta instanceof CrossbowMeta xbowMeta) {
                    xbowMeta.addChargedProjectile(new ItemStack(Material.SPECTRAL_ARROW));
                }
            });
        return itemStack;
    }
}
