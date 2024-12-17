package com.cavetale.race;

import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.playercache.PlayerCache;
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
import static com.cavetale.mytems.util.Items.tooltip;
import static com.cavetale.race.RacePlugin.racePlugin;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
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
        { // Time Trial
            final List<Component> tooltip = new ArrayList<>();
            tooltip.addAll(List.of(text("Time Trial", BLUE),
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
                int nextDisplayRank = 0;
                long lastTime = -1L;
                for (SQLPlayerMapRecord record : racePlugin().getRecords().rank(buildWorld.getPath())) {
                    final int rank = nextRank++;
                    if (rank > 10) break;
                    if (lastTime != record.getTime()) {
                        nextDisplayRank += 1;
                        lastTime = record.getTime();
                    }
                    final int displayRank = nextDisplayRank;
                    tooltip.add(textOfChildren(Glyph.toComponent("" + displayRank),
                                               space(),
                                               text(Race.formatTime(record.getTime()), WHITE),
                                               space(),
                                               text(PlayerCache.nameForUuid(record.getPlayer()), GRAY)));
                }
            }
            gui.setItem(6, 2, tooltip(new ItemStack(Material.CLOCK), tooltip),
                        click -> {
                            if (!click.isLeftClick()) return;
                            player.performCommand("race timetrial " + buildWorld.getPath());
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5f, 1f);
                        });
        }
        gui.setItem(2, 2,
                    tooltip(new ItemStack(Material.LEAD),
                            List.of(text("Practice", BLUE),
                                    text("Race this map on your", GRAY),
                                    text("own, with all goodies", GRAY),
                                    text("available, just like", GRAY),
                                    text("during a Grand Prix.", GRAY))),
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
