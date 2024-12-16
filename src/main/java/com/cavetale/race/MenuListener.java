package com.cavetale.race;

import com.cavetale.core.menu.MenuItemEntry;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.util.Items;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class MenuListener implements Listener {
    public static final String MENU_KEY = "race:race";
    public static final String MENU_PERMISSION = "race.race";
    private final RacePlugin plugin;

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    private void onMenuItem(MenuItemEvent event) {
        if (!event.getPlayer().hasPermission(MENU_PERMISSION)) {
            return;
        }
        event.addItem(builder -> builder
                      .priority(MenuItemEntry.Priority.SERVER)
                      .key(MENU_KEY)
                      .command("race")
                      .icon(Items.iconize(Mytems.SNEAKERS.createIcon(List.of(text("Race", YELLOW))))));
    }
}
