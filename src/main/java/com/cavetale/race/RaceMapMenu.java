package com.cavetale.race;

import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.font.Glyph;
import com.cavetale.mytems.util.Gui;
import com.cavetale.race.sql.SQLPlayerMapRecord;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.mytems.util.Items.colorized;
import static com.cavetale.mytems.util.Items.iconize;
import static com.cavetale.mytems.util.Items.tooltip;
import static com.cavetale.mytems.util.Text.wrapLore;
import static com.cavetale.race.RacePlugin.racePlugin;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;

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
            .layer(GuiOverlay.BLANK, color(0x8080ff))
            .layer(GuiOverlay.CHECKERED_8, color(0xa0a0ff))
            .layer(GuiOverlay.RACE_MAP_MENU, YELLOW)
            .layer(GuiOverlay.TOP_BAR, BLUE)
            .title(text(buildWorld.getName(), BLACK));
        { // Info
            final List<Component> tooltip = new ArrayList<>();
            tooltip.add(text(buildWorld.getName(), WHITE));
            tooltip.add(textOfChildren(text(tiny("rating "), GRAY),
                                       buildWorld.getStarRatingComponent()));
            if (buildWorld.getRow().getDescription() != null) {
                tooltip.add(empty());
                tooltip.addAll(wrapLore(buildWorld.getRow().getDescription(), c -> c.color(GRAY)));
            }
            gui.setItem(0, Mytems.INFO_BUTTON.createIcon(tooltip));
        }
        { // Time Trial
            final List<Component> tooltip = new ArrayList<>();
            tooltip.addAll(List.of(text("Time Trial", YELLOW),
                                   text("Race against the clock", GRAY),
                                   text("and try to beat your", GRAY),
                                   text("own record.", GRAY)));
            if (racePlugin().hasRecords()) {
                final SQLPlayerMapRecord yourRecord = racePlugin().getRecords().find(buildWorld.getPath(), player.getUniqueId());
                tooltip.add(textOfChildren(text(tiny("your record"), GRAY),
                                           space(),
                                           (yourRecord != null
                                            ? text(Race.formatTime(yourRecord.getTime()), GREEN)
                                            : text("N/A", DARK_RED))));
                int nextRank = 1;
                long lastTime = -1L;
                for (SQLPlayerMapRecord record : racePlugin().getRecords().rank(buildWorld.getPath())) {
                    final int rank = nextRank++;
                    if (rank > 10) break;
                    if (lastTime != record.getTime()) {
                        lastTime = record.getTime();
                    }
                    tooltip.add(textOfChildren(Glyph.toComponent("" + record.getRank()),
                                               space(),
                                               text(Race.formatTime(record.getTime()), WHITE),
                                               space(),
                                               text(PlayerCache.nameForUuid(record.getPlayer()), GRAY)));
                }
            }
            gui.setItem(6, 3, tooltip(new ItemStack(Material.CLOCK), tooltip),
                        click -> {
                            if (!click.isLeftClick()) return;
                            player.performCommand("race timetrial " + buildWorld.getPath());
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1f);
                        });
        }
        gui.setItem(2, 3,
                    iconize(colorized(Mytems.PLAY_BUTTON.createIcon(List.of(text("Practice", GREEN),
                                                                            text("Race this map on your", GRAY),
                                                                            text("own, with all goodies", GRAY),
                                                                            text("available, just like", GRAY),
                                                                            text("during a Grand Prix.", GRAY))),
                                      GREEN)),
                    click -> {
                        if (!click.isLeftClick()) return;
                        player.performCommand("race practice " + buildWorld.getPath());
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
