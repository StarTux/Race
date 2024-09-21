package com.cavetale.race;

import com.winthier.creative.BuildWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import static com.cavetale.race.RacePlugin.racePlugin;

/**
 * Implement all the behavior that's exclusive to the creative server.
 */
public final class CreativeServerListener implements Listener {
    public void enable() {
        for (World world : Bukkit.getWorlds()) {
            final BuildWorld buildWorld = BuildWorld.in(world);
            if (buildWorld == null) continue;
            racePlugin().getRaces().loadWorld(world, buildWorld);
        }
        Bukkit.getPluginManager().registerEvents(this, racePlugin());
    }

    public void disable() {
    }

    @EventHandler
    private void onWorldLoad(WorldLoadEvent event) {
        final World world = event.getWorld();
        final BuildWorld buildWorld = BuildWorld.in(world);
        if (buildWorld == null) return;
        racePlugin().getRaces().loadWorld(world, buildWorld);
    }

    @EventHandler
    private void onWorldUnload(WorldUnloadEvent event) {
        final World world = event.getWorld();
        racePlugin().getRaces().unloadWorld(world);
    }
}
