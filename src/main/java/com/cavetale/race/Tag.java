package com.cavetale.race;

import com.cavetale.core.editor.EditMenuAdapter;
import com.cavetale.core.editor.EditMenuButton;
import com.cavetale.core.editor.EditMenuException;
import com.cavetale.core.editor.EditMenuItem;
import com.cavetale.core.editor.EditMenuNode;
import com.cavetale.mytems.Mytems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter @Setter
public final class Tag implements EditMenuAdapter {
    // Actual settings
    @EditMenuItem(settable = false)
    protected String worldName = "";
    protected RaceType type = RaceType.WALK;
    @EditMenuItem(description = "Must contain the whole race track")
    protected Cuboid area = Cuboid.ZERO;
    @EditMenuItem(description = "Where players spawn in")
    protected Position spawnLocation = Position.ZERO;
    @EditMenuItem(description = "Where individual racers will start the race")
    protected List<Vec3i> startVectors = new ArrayList<>();
    @EditMenuItem(description = "Where goodie chests will spawn")
    protected Set<Vec3i> goodies = new HashSet<>();
    @EditMenuItem(description = "Where coins will spawn")
    protected Set<Vec3i> coins = new HashSet<>();
    @EditMenuItem(description = "Mostly unused")
    protected Cuboid spawnArea = Cuboid.ZERO;
    @EditMenuItem(description = "Racers must touch checkpoints on after the other."
                  + " Their size does not matter.")
    protected List<Cuboid> checkpoints = new ArrayList<>();
    @EditMenuItem(description = "How many laps to race."
                  + " Laps greater than one requires that"
                  + " the first checkpoint is reachable from the last.")
    protected int laps = 1;
    @EditMenuItem(description = "Race duration in seconds. 0 disables timeout.")
    protected long maxDuration = 0; // seconds
    // Racing
    @EditMenuItem(settable = false, description = "ONLY USED FOR RACING")
    protected Phase phase = Phase.IDLE;
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected int phaseTicks;
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected List<Racer> racers = new ArrayList<>();
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected long startTime = 0L;
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected int racerCount = 0;
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected int finishIndex = 0;
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected int rareItemsAvailable = 0;
    @EditMenuItem(hidden = true, description = "ONLY USED FOR RACING")
    protected int maxLap = 0;

    public int countRacers() {
        int count = 0;
        for (Racer racer : racers) {
            if (racer.racing && !racer.finished && racer.isOnline()) count += 1;
        }
        return count;
    }

    public int countAllRacers() {
        int count = 0;
        for (Racer racer : racers) {
            if (racer.racing) count += 1;
        }
        return count;
    }

    @Override
    public Object createNewValue(EditMenuNode node, String fieldName, int valueIndex) {
        switch (fieldName) {
        case "checkpoints": {
            Cuboid cuboid = WorldEdit.getSelection(node.getContext().getPlayer());
            if (cuboid == null) throw new EditMenuException("No selection!");
            node.getContext().getPlayer().sendMessage(text("New checkpoint: " + cuboid, GREEN));
            return cuboid;
        }
        case "startVectors":
        case "goodies":
        case "coins": {
            Cuboid cuboid = WorldEdit.getSelection(node.getContext().getPlayer());
            if (cuboid == null) {
                throw new EditMenuException("No selection!");
            }
            if (cuboid.getVolume() != 1) {
                throw new EditMenuException("1x1x1 selection required!");
            }
            Vec3i result = cuboid.getMin();
            node.getContext().getPlayer().sendMessage(text("New vector: " + result, GREEN));
            return result;
        }
        default: return null;
        }
    }

    @Override
    public List<EditMenuButton> getEditMenuButtons(EditMenuNode node) {
        return List.of(new EditMenuButton[] {
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return Mytems.BLUNDERBUSS.createIcon();
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("(Re)set start vectors", GREEN),
                                       text("Create start vectors", GRAY),
                                       text("in current selection", GRAY));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            Cuboid cuboid = WorldEdit.getSelection(player);
                            if (cuboid == null) throw new EditMenuException("No selection!");
                            startVectors.clear();
                            startVectors.addAll(cuboid.enumerate());
                            player.sendMessage(text(startVectors.size() + " start vectors created", GREEN));
                        }
                    }
                },
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return new ItemStack(Material.SPYGLASS);
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Toggle edit mode", GREEN),
                                       text("Turn off and on again", GRAY),
                                       text("to refresh goodies ;)", GRAY));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            Race race = RacePlugin.instance.races.at(player.getLocation());
                            if (race == null) return;
                            if (phase == Phase.EDIT) {
                                race.setPhase(Phase.IDLE);
                                player.sendMessage(text("Edit mode disabled", RED));
                            } else {
                                race.setPhase(Phase.EDIT);
                                player.sendMessage(text("Edit mode enabled", GREEN));
                            }
                        }
                    }
                },
                new EditMenuButton() {
                    @Override public ItemStack getMenuIcon() {
                        return Mytems.REDO.createIcon();
                    }

                    @Override public List<Component> getTooltip() {
                        return List.of(text("Reload goodies", GREEN),
                                       text("Reload track goodies", GRAY));
                    }

                    @Override public void onClick(Player player, ClickType click) {
                        if (click.isLeftClick()) {
                            Race race = RacePlugin.instance.races.at(player.getLocation());
                            if (race == null) return;
                            race.clearGoodies();
                        }
                    }
                },
            });
    }
}
