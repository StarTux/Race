package com.cavetale.race;

import com.cavetale.core.editor.EditMenuAdapter;
import com.cavetale.core.editor.EditMenuNode;
import com.cavetale.mytems.Mytems;
import com.cavetale.race.util.Rnd;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pig;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Strider;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public enum RaceType implements EditMenuAdapter {
    WALK(() -> new ItemStack(Material.IRON_BOOTS)),
    STRIDER(() -> new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK)),
    PARKOUR(() -> new ItemStack(Material.TRIDENT)),
    ICE_BOAT(() -> new ItemStack(Material.ICE)),
    BOAT(() -> new ItemStack(Material.OAK_BOAT)),
    HORSE(() -> new ItemStack(Material.IRON_HORSE_ARMOR)),
    HALLOWEEN_HORSE(() -> new ItemStack(Material.ZOMBIE_HEAD)),
    PIG(() -> new ItemStack(Material.CARROT_ON_A_STICK)),
    ELYTRA(() -> new ItemStack(Material.ELYTRA)),
    BROOM(Mytems.WITCH_BROOM::createIcon),
    SONIC(Mytems.SNEAKERS::createIcon),
    CAMEL(() -> new ItemStack(Material.CAMEL_SPAWN_EGG)),
    ;

    public final Supplier<ItemStack> iconSupplier;

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
            return location.getWorld().spawn(location, Boat.class, e -> {
                    e.setPersistent(false);
                    Boat.Type theType = Rnd.pick(Boat.Type.values());
                    e.setBoatType(theType);
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
                    e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3375);
                    e.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(0.7);
                    e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
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
                e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3375);
                e.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(0.7);
                e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
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
                    e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.175);
                    e.setJumpStrength(0.3);
                    e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
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
                    e.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.30);
                    e.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(15.0);
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
}
