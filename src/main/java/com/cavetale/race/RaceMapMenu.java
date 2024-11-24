package com.cavetale.race;

import com.cavetale.core.font.GuiOverlay;
import com.cavetale.mytems.util.Gui;
import com.winthier.creative.BuildWorld;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.mytems.util.Items.tooltip;
import static com.cavetale.race.RacePlugin.racePlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class RaceMapMenu {
    private final Player player;
    private Gui gui;
    private final BuildWorld buildWorld;
    private Tag tag;

    public void open() {
        tag = Races.getRaceTag(buildWorld);
        gui = new Gui(racePlugin())
            .size(6 * 9)
            .layer(GuiOverlay.BLANK, YELLOW)
            .title(text(buildWorld.getName(), BLACK));
        gui.setItem(6, 4,
                    tooltip(new ItemStack(Material.CLOCK),
                            List.of(text("Time Trial", BLUE))),
                    click -> {
                        if (!click.isLeftClick()) return;
                        player.performCommand("race timetrial " + buildWorld.getPath());
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1f);
                    });
        gui.setItem(Gui.OUTSIDE, null, click -> {
                if (!click.isLeftClick()) return;
                new RaceMenu(player, tag.getType()).open();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1f);
            });
        gui.open(player);
    }

    private void onClickRaceType(RaceType raceType, InventoryClickEvent event) {
        if (!event.isLeftClick()) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        new RaceMenu(player, raceType).open();
    }
}
