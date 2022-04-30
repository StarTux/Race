package com.cavetale.race;

import com.cavetale.core.editor.EditMenuAdapter;
import com.cavetale.core.editor.EditMenuButton;
import com.cavetale.core.editor.EditMenuNode;
import java.util.List;
import lombok.Value;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Value
public final class Vec3i implements EditMenuAdapter {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    public static final Vec3i ONE = new Vec3i(1, 1, 1);
    public final int x;
    public final int y;
    public final int z;

    public static Vec3i of(Block block) {
        return new Vec3i(block.getX(), block.getY(), block.getZ());
    }

    public static Vec3i of(Location loc) {
        return new Vec3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Block toBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    public Location toLocation(World world) {
        return toBlock(world).getLocation().add(0.5, 0, 0.5);
    }

    public Vec3i add(int dx, int dy, int dz) {
        return new Vec3i(x + dx, y + dy, z + dz);
    }

    public Vec3i subtract(Vec3i o) {
        return new Vec3i(x - o.x, y - o.y, z - o.z);
    }

    public Vec3i multiply(int mul) {
        return new Vec3i(x * mul, y * mul, z * mul);
    }

    public int distanceSquared(Vec3i other) {
        int dx = other.x - x;
        int dy = other.y - y;
        int dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public int simpleDistance(Vec3i other) {
        return Math.max(Math.abs(other.y - y),
                        Math.max(Math.abs(other.x - x),
                                 Math.abs(other.z - z)));
    }

    @Override
    public String toString() {
        return "" + x + "," + y + "," + z;
    }

    public boolean contains(Location loc) {
        return x == loc.getBlockX() && y == loc.getBlockY() && z == loc.getBlockZ();
    }

    @Override
    public List<EditMenuButton> getEditMenuButtons(EditMenuNode node) {
        return List.of(new EditMenuButton[] {
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return new ItemStack(Material.ENDER_PEARL);
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Teleport: " + toString(), GREEN));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            player.teleport(toLocation(player.getWorld()));
                            player.sendMessage(text("Teleported to location: " + toString(), GREEN));
                        }
                    }
                },
            });
    }
}
