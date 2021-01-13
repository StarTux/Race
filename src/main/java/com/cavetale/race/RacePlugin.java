package com.cavetale.race;

import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class RacePlugin extends JavaPlugin {
    final RaceCommand raceCommand = new RaceCommand(this);
    final Races races = new Races(this);
    final EventListener eventListener = new EventListener(this);
    int ticks;

    public File getSaveFolder() {
        return new File(getDataFolder(), "races");
    }

    @Override
    public void onEnable() {
        getSaveFolder().mkdirs();
        raceCommand.enable();
        eventListener.enable();
        races.load();
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1, 1);
    }

    @Override
    public void onDisable() {
        races.onDisable();
        races.save();
    }

    void onTick() {
        races.tick(ticks);
        ticks += 1;
    }

    boolean consoleCommand(String cmd) {
        getLogger().info("Console command: " + cmd);
        return getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
    }
}
