package com.cavetale.race;

import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.util.Gui;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.mytems.util.Items.clearAttributes;
import static com.cavetale.mytems.util.Items.tooltip;
import static com.cavetale.race.RacePlugin.racePlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Display all races of a type.
 */
@RequiredArgsConstructor
public final class RaceTypeMenu {
    private final Player player;
    private Gui gui;
    private final Map<BuildWorld, Tag> raceTags = Races.getAllRaceTags(true);
    private final Map<RaceType, List<BuildWorld>> typeMap = Races.sortRaceTypes(raceTags);

    public void open() {
        gui = new Gui(racePlugin())
            .size(6 * 9)
            .layer(GuiOverlay.BLANK, YELLOW)
            .layer(GuiOverlay.TOP_BAR, GOLD)
            .title(text("Select a category", BLACK));
        final List<RaceType> typeList = new ArrayList<>();
        for (RaceType raceType : RaceType.values()) {
            if (typeMap.get(raceType) == null) continue;
            typeList.add(raceType);
        }
        Collections.sort(typeList, Comparator.comparing(RaceType::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < typeList.size(); i += 1) {
            final RaceType raceType = typeList.get(i);
            final List<BuildWorld> worldList = typeMap.get(raceType);
            if (worldList == null || worldList.isEmpty()) continue;
            final ItemStack icon = raceType.createIcon();
            icon.editMeta(meta -> {
                    meta.setMaxStackSize(99);
                    tooltip(meta, List.of(text(raceType.getDisplayName(), GOLD),
                                          textOfChildren(text(tiny("race tracks "), GRAY),
                                                         text(worldList.size(), GOLD))));
                    clearAttributes(meta);
                });
            final int guiy = 2 + 2 * (i / 4);
            final int guix = 1 + 2 * (i % 4);
            icon.setAmount(Math.max(1, Math.min(99, worldList.size())));
            gui.setItem(guix, guiy, icon, click -> this.onClickRaceType(raceType, click));
        }
        gui.setItem(6, 0,
                    tooltip(Mytems.VOTE_FIREWORK.createItemStack(),
                            List.of(text("Start Map Vote", BLUE),
                                    text("Everybody in the Race", GRAY),
                                    text("lobby can choose one", GRAY),
                                    text("map.", GRAY),
                                    text(""),
                                    text("The race map is chosen", GRAY),
                                    text("based on all votes after", GRAY),
                                    text("one minute.", GRAY))),
                    click -> {
                        if (!click.isLeftClick()) return;
                        player.performCommand("race startvote");
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                    });
        gui.setItem(2, 0, Mytems.TURN_LEFT.createIcon(List.of(text("Return to lobby", GRAY))), click -> {
                if (!click.isLeftClick()) return;
                player.eject();
                player.leaveVehicle();
                player.teleport(racePlugin().getLobbyWorld().getSpawnLocation());
                Race.resetSpeed(player);
                Race.clearInventory(player);
                Race.resetHealthAndFood(player);
            });
        gui.setItem(Gui.OUTSIDE, null, click -> {
                if (!click.isLeftClick()) return;
                MenuItemEvent.openMenu(player);
            });
        gui.open(player);
    }

    private void onClickRaceType(RaceType raceType, InventoryClickEvent event) {
        if (!event.isLeftClick()) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        new RaceMenu(player, raceType).open();
    }
}
