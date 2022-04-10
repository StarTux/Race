package com.cavetale.race;

import com.cavetale.core.editor.EditMenuAdapter;
import com.cavetale.core.editor.EditMenuButton;
import com.cavetale.core.editor.EditMenuItem;
import com.cavetale.core.editor.EditMenuNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Serializable Location.
 */
@Getter @NoArgsConstructor @AllArgsConstructor
public final class Position implements EditMenuAdapter {
    @EditMenuItem(hidden = true)
    public static final Position ZERO = new Position(0, 0, 0, 0, 0);
    private double x;
    private double y;
    private double z;
    private float pitch;
    private float yaw;

    public static Position of(final Location location) {
        return new Position(location.getX(), location.getY(), location.getZ(),
                            location.getPitch(), location.getYaw());
    }

    public void load(Location location) {
        x = location.getX();
        y = location.getY();
        z = location.getZ();
        pitch = location.getPitch();
        yaw = location.getYaw();
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String simpleString() {
        return Math.round(x) + "," + Math.round(y) + "," + Math.round(z);
    }

    @Override
    public ItemStack getMenuIcon(EditMenuNode node) {
        return new ItemStack(Material.ENDER_PEARL);
    }

    @Override
    public List<Component> getTooltip(EditMenuNode node) {
        return List.of(text(simpleString(), WHITE));
    }

    @Override
    public List<EditMenuButton> getEditMenuButtons(EditMenuNode node) {
        return List.of(new EditMenuButton[] {
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return new ItemStack(Material.END_CRYSTAL);
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Set to current location", GREEN));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            load(player.getLocation());
                            player.sendMessage(text("Set to current location: " + simpleString(), GREEN));
                        }
                    }
                },
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return new ItemStack(Material.ENDER_PEARL);
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Teleport: " + simpleString(), GREEN));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            player.teleport(toLocation(player.getWorld()));
                            player.sendMessage(text("Teleported to location: " + simpleString(), GREEN));
                        }
                    }
                },
            });
    }
}

