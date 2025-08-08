package com.cavetale.race;

import com.cavetale.core.editor.EditMenuAdapter;
import com.cavetale.core.editor.EditMenuNode;
import com.cavetale.mytems.Mytems;
import com.cavetale.race.util.Rnd;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pig;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.entity.boat.OakBoat;
import org.bukkit.inventory.ItemStack;

@Getter
@RequiredArgsConstructor
public enum RaceType implements EditMenuAdapter {
    WALK("Walking", () -> new ItemStack(Material.IRON_BOOTS),
         "Let's walk!"),
    STRIDER("Strider", () -> new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK),
            "Steer your strider with a warped fungus on a stick."),
    PARKOUR("Parkour", () -> new ItemStack(Material.TRIDENT),
            "Walk on foot and try to complete the parkour."),
    ICE_BOAT("Ice Boats", () -> new ItemStack(Material.ICE),
             "A high speed boat race on ice!"),
    BOAT("Boat Race", () -> new ItemStack(Material.OAK_BOAT),
         "A boat race on water."),
    HORSE("Horses", () -> new ItemStack(Material.IRON_HORSE_ARMOR),
          "Collect golden coins to speed up and heal your horse."),
    HALLOWEEN_HORSE("Halloween Horses", () -> new ItemStack(Material.ZOMBIE_HEAD),
                    "Collect golden coins to speed up and heal your horse."),
    PIG("Pig Race", () -> new ItemStack(Material.CARROT_ON_A_STICK),
        "Steer your pig with a carrot on a stick. Collect golden coins to speed up and heal your piggy."),
    ELYTRA("Elytra", () -> new ItemStack(Material.ELYTRA),
           "You get an elytra as soon as the countdown ends, so make sure to double jump instantly! Do not miss any of the golden ring checkpoints. They give you an additional boost."),
    BROOM("Broom Race", Mytems.WITCH_BROOM::createIcon,
          "You get your broom as soon as the countdown ends. Remount your broom by right clicking it. Collect golden coins to speed up."),
    SONIC("Sneakers", Mytems.SNEAKERS::createIcon,
         "We run on foot with special sneakers. Collect golden hoops to heal up and increase your speed. Do not miss any of the golden ring checkpoints!"),
    CAMEL("Camel Race", () -> new ItemStack(Material.CAMEL_SPAWN_EGG),
          "Camels can lunge forwards. Collect golden coins to speed up and heal your camel."),
    ;

    private final String displayName;
    private final Supplier<ItemStack> iconSupplier;
    private final String description;

    public boolean isMounted() {
        switch (this) {
        case STRIDER:
        case ICE_BOAT:
        case BOAT:
        case HORSE:
        case PIG:
        case CAMEL:
            return true;
        default:
            return false;
        }
    }

    public boolean mountIsAlive() {
        return isMounted() && this != ICE_BOAT && this != BOAT;
    }

    public boolean playerIsDamageable() {
        return this == RaceType.SONIC;
    }

    public boolean playerHasSpeed() {
        return this == RaceType.SONIC;
    }

    protected Vehicle spawnVehicle(Location location) {
        switch (this) {
        case STRIDER:
            return location.getWorld().spawn(location, Strider.class, e -> {
                    e.setPersistent(false);
                    e.setShivering(false);
                    e.setAdult();
                    e.setAgeLock(true);
                    e.setSaddle(true);
                });
        case BOAT:
        case ICE_BOAT: {
            while (location.getBlock().isLiquid()
                   && location.getBlock().getBlockData() instanceof Levelled level
                   && level.getLevel() == 0) {
                location = location.add(0.0, 1.0, 0.0);
            }
            if (!location.getBlock().getCollisionShape().getBoundingBoxes().isEmpty()) {
                location = location.add(0.0, 1.0, 0.0);
            }
            // TODO Boat Types
            return location.getWorld().spawn(location, OakBoat.class, e -> {
                    e.setPersistent(false);
                });
        }
        case HORSE: {
            Horse horse = location.getWorld().spawn(location, Horse.class, e -> {
                    e.setPersistent(false);
                    e.setAdult();
                    e.setAgeLock(true);
                    e.setColor(Rnd.pick(Horse.Color.values()));
                    e.setStyle(Rnd.pick(Horse.Style.values()));
                    double variance = 0.01;
                    e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3375);
                    e.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.7);
                    e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                    e.setHealth(20.0);
                    e.setTamed(true);
                    e.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                });
            return horse;
        }
        case HALLOWEEN_HORSE: {
            final Consumer<AbstractHorse> horseConsumer = (AbstractHorse e) -> {
                e.setPersistent(false);
                e.setAdult();
                e.setAgeLock(true);
                double variance = 0.01;
                e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3375);
                e.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.7);
                e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                e.setHealth(20.0);
                e.setTamed(true);
                e.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            };
            return Rnd.nextBoolean()
                ? location.getWorld().spawn(location, SkeletonHorse.class, e -> horseConsumer.accept(e))
                : location.getWorld().spawn(location, ZombieHorse.class, e -> horseConsumer.accept(e));
        }
        case CAMEL: {
            Camel camel = location.getWorld().spawn(location, Camel.class, e -> {
                    e.setPersistent(false);
                    e.setAdult();
                    e.setAgeLock(true);
                    double variance = 0.01;
                    e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.175);
                    e.setJumpStrength(0.3);
                    e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
                    e.setHealth(20.0);
                    e.setTamed(true);
                    e.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                });
            return camel;
        }
        case PIG: {
            Pig pig = location.getWorld().spawn(location, Pig.class, e -> {
                    e.setPersistent(false);
                    e.setAdult();
                    e.setAgeLock(true);
                    e.setSaddle(true);
                    e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.30);
                    e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(15.0);
                });
            return pig;
        }
        default:
            return null;
        }
    }

    public List<String> getWinnerTitles() {
        switch (this) {
        case HORSE:
            return List.of("Jockey", "Equestrian", "JollyJumper", "Secretariat");
        case ICE_BOAT:
            return List.of("Drifter");
        case BOAT:
            return List.of("Sailor");
        case PIG:
            return List.of("PigRacer", "BaconRacer");
        case CAMEL:
            return List.of("Cameleer");
        default:
            return List.of("Falcon");
        }
    }

    public Mytems getCoinItem() {
        switch (this) {
        case SONIC: return Mytems.GOLDEN_HOOP;
        default: return Mytems.GOLDEN_COIN;
        }
    }

    @Override
    public ItemStack getMenuIcon(EditMenuNode node) {
        return iconSupplier.get();
    }

    public ItemStack createIcon() {
        return iconSupplier.get();
    }
}
