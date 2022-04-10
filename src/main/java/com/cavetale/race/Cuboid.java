package com.cavetale.race;

import com.cavetale.core.editor.EditMenuAdapter;
import com.cavetale.core.editor.EditMenuButton;
import com.cavetale.core.editor.EditMenuException;
import com.cavetale.core.editor.EditMenuItem;
import com.cavetale.core.editor.EditMenuNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

@Getter @AllArgsConstructor @NoArgsConstructor
public final class Cuboid implements EditMenuAdapter {
    @EditMenuItem(hidden = true)
    public static final Cuboid ZERO = new Cuboid(0, 0, 0, 0, 0, 0);
    private int ax;
    private int ay;
    private int az;
    private int bx;
    private int by;
    private int bz;

    public void load(Cuboid other) {
        ax = other.ax;
        ay = other.ay;
        az = other.az;
        bx = other.bx;
        by = other.by;
        bz = other.bz;
    }

    public boolean contains(int x, int y, int z) {
        return x >= ax && x <= bx
            && y >= ay && y <= by
            && z >= az && z <= bz;
    }

    public boolean containsHorizontal(int x, int z) {
        return x >= ax && x <= bx
            && z >= az && z <= bz;
    }

    public boolean contains(Block block) {
        return contains(block.getX(), block.getY(), block.getZ());
    }

    public boolean containsHorizontal(Block block) {
        return containsHorizontal(block.getX(), block.getZ());
    }

    public boolean contains(Location loc) {
        return contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean containsHorizontal(Location loc) {
        return containsHorizontal(loc.getBlockX(), loc.getBlockZ());
    }

    public boolean contains(Vec3i v) {
        return contains(v.x, v.y, v.z);
    }

    public String getShortInfo() {
        return ax + "," + ay + "," + az + "-" + bx + "," + by + "," + bz;
    }

    @Override
    public String toString() {
        return getShortInfo();
    }

    public int getSizeX() {
        return bx - ax + 1;
    }

    public int getSizeY() {
        return by - ay + 1;
    }

    public int getSizeZ() {
        return bz - az + 1;
    }

    public int getVolume() {
        return getSizeX() * getSizeY() * getSizeZ();
    }

    /**
     * Highlight this cuboid. This may be called every tick and with
     * the provided arguments will do the rest. A balanced interval
     * and scale are required to make the highlight contiguous while
     * reducint lag.
     * @param the current time in ticks
     * @param interval interval between ticks
     * @param scale how many inbetween dots to make over time
     * @param callback method to call for every point
     * @return true if the callback was called (probably many times),
     *   false if we're waiting for the interval.
     */
    public boolean highlight(World world, int timer, int interval, int scale, Consumer<Location> callback) {
        if (timer % interval != 0) return false;
        double offset = (double) ((timer / interval) % scale) / (double) scale;
        return highlight(world, offset, callback);
    }

    /**
     * Highlight this cuboid. This is a utility function for the other highlight method but may be called on its own, probably with an offset of 0.
     * @param world the world
     * @param offset the offset to highlight, between each corner point and the next
     * @param callback will be called for each point
     */
    public boolean highlight(World world, double offset, Consumer<Location> callback) {
        if (!world.isChunkLoaded(ax >> 4, az >> 4)) return false;
        Block origin = world.getBlockAt(ax, ay, az);
        Location loc = origin.getLocation();
        int sizeX = getSizeX();
        int sizeY = getSizeY();
        int sizeZ = getSizeZ();
        for (int y = 0; y < sizeY; y += 1) {
            double dy = (double) y + offset;
            callback.accept(loc.clone().add(0, dy, 0));
            callback.accept(loc.clone().add(0, dy, sizeZ));
            callback.accept(loc.clone().add(sizeX, dy, 0));
            callback.accept(loc.clone().add(sizeX, dy, sizeZ));
        }
        for (int z = 0; z < sizeZ; z += 1) {
            double dz = (double) z + offset;
            callback.accept(loc.clone().add(0, 0, dz));
            callback.accept(loc.clone().add(0, sizeY, dz));
            callback.accept(loc.clone().add(sizeX, 0, dz));
            callback.accept(loc.clone().add(sizeX, sizeY, dz));
        }
        for (int x = 0; x < sizeX; x += 1) {
            double dx = (double) x + offset;
            callback.accept(loc.clone().add(dx, 0, 0));
            callback.accept(loc.clone().add(dx, 0, sizeZ));
            callback.accept(loc.clone().add(dx, sizeY, 0));
            callback.accept(loc.clone().add(dx, sizeY, sizeZ));
        }
        return true;
    }

    public Vec3i getMin() {
        return new Vec3i(ax, ay, az);
    }

    public Vec3i getMax() {
        return new Vec3i(bx, by, bz);
    }

    public Vec3i getCenter() {
        return new Vec3i((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
    }

    public List<Vec3i> enumerate() {
        List<Vec3i> result = new ArrayList<>((bx - ax + 1) * (by - ay + 1) * (bz - az + 1));
        for (int y = ay; y <= by; y += 1) {
            for (int z = az; z <= bz; z += 1) {
                for (int x = ax; x <= bx; x += 1) {
                    result.add(new Vec3i(x, y, z));
                }
            }
        }
        return result;
    }

    public Block getBottomBlock(World world) {
        return world.getBlockAt((ax + bx) / 2, ay, (az + bz) / 2);
    }

    @Override
    public List<EditMenuButton> getEditMenuButtons(EditMenuNode node) {
        return List.of(new EditMenuButton[] {
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return new ItemStack(Material.ENDER_PEARL);
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Teleport", GREEN));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            player.teleport(getCenter().toLocation(player.getWorld()));
                            player.sendMessage(text("Teleported: " + getShortInfo(), GREEN));
                        }
                    }
                },
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return new ItemStack(Material.WOODEN_AXE);
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Set to selection", GREEN));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            Cuboid other = WorldEdit.getSelection(player);
                            if (other == null) throw new EditMenuException("No selection!");
                            load(other);
                            player.sendMessage(text("Set to selection: " + getShortInfo(), GREEN));
                        }
                    }
                },
            });
    }
}
