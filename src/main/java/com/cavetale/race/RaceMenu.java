package com.cavetale.race;

import com.cavetale.core.item.ItemKinds;
import com.cavetale.race.sql.SQLPlayerMapRecord;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.mytems.util.Text.wrapLore;
import static com.cavetale.race.RacePlugin.racePlugin;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

/**
 * Display all race types.
 */
@RequiredArgsConstructor
public final class RaceMenu {
    private final Player player;
    private final RaceType raceType;
    private final Map<BuildWorld, Tag> raceTags = Races.getAllRaceTags(true);
    private final Map<RaceType, List<BuildWorld>> typeMap = Races.sortRaceTypes(raceTags);

    public void open() {
        final ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
                final List<BuildWorld> buildWorlds = typeMap.get(raceType);
                Collections.sort(buildWorlds, Comparator.comparing(BuildWorld::getName, String.CASE_INSENSITIVE_ORDER));
                final List<Component> worldLines = new ArrayList<>(buildWorlds.size());
                for (BuildWorld buildWorld : buildWorlds) {
                    final Tag tag = raceTags.get(buildWorld);
                    String raw = buildWorld.getName();
                    if (raw.length() > 16) raw = raw.substring(0, 16);
                    final List<Component> tooltip = new ArrayList<>();
                    tooltip.add(text(buildWorld.getName(), BLUE));
                    if (racePlugin().hasRecords()) {
                        final SQLPlayerMapRecord yourRecord = racePlugin().getRecords().find(buildWorld.getPath(), player.getUniqueId());
                        tooltip.add(textOfChildren(text(tiny("your record"), GRAY),
                                                   space(),
                                                   (yourRecord != null
                                                    ? text(Race.formatTime(yourRecord.getTime()), GREEN)
                                                    : text("N/A", DARK_RED))));
                    }
                    if (buildWorld.getRow().getDescription() != null) {
                        tooltip.addAll(wrapLore(buildWorld.getRow().getDescription(), c -> c.color(LIGHT_PURPLE)));
                    }
                    final String command = "/race view " + buildWorld.getPath();
                    worldLines.add(text(raw, BLUE)
                                   .hoverEvent(showText(join(separator(newline()), tooltip)))
                                   .clickEvent(runCommand(command)));
                    final List<Component> pages = new ArrayList<>();
                    final int pageSize = 8;
                    for (int i = 0; i < worldLines.size(); i += pageSize) {
                        final List<Component> lines = new ArrayList<>();
                        lines.add(textOfChildren(ItemKinds.icon(raceType.createIcon()),
                                                 text(raceType.getDisplayName(), DARK_BLUE, BOLD))
                                  .hoverEvent(showText(text("Back to categories", GRAY)))
                                  .clickEvent(runCommand("/race")));
                        lines.add(empty());
                        for (int j = 0; j < pageSize; j += 1) {
                            final int index = i + j;
                            if (index >= worldLines.size()) break;
                            lines.add(worldLines.get(index));
                        }
                        pages.add(join(separator(newline()), lines));
                    }
                    meta.pages(pages);
                    meta.author(text("Cavetale"));
                    meta.title(text("Race"));
                }
            });
        player.closeInventory();
        player.openBook(book);
    }
}
