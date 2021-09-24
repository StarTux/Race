package com.cavetale.race;

import com.cavetale.core.util.Json;
import com.cavetale.race.struct.Area;
import com.cavetale.race.struct.AreasFile;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RacePlugin extends JavaPlugin {
    protected final RaceCommand raceCommand = new RaceCommand(this);
    protected final Races races = new Races(this);
    protected final EventListener eventListener = new EventListener(this);
    protected int ticks;
    protected Save save;
    protected File saveFile;

    public File getSaveFolder() {
        return new File(getDataFolder(), "races");
    }

    @Override
    public void onEnable() {
        saveFile = new File(getDataFolder(), "save.json");
        getSaveFolder().mkdirs();
        raceCommand.enable();
        eventListener.enable();
        races.load();
        load();
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1, 1);
    }

    @Override
    public void onDisable() {
        races.onDisable();
        races.save();
        save();
    }

    void onTick() {
        races.tick(ticks);
        ticks += 1;
    }

    boolean consoleCommand(String cmd) {
        getLogger().info("Console command: " + cmd);
        return getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
    }

    protected void load() {
        save = Json.load(saveFile, Save.class, Save::new);
    }

    protected void save() {
        getDataFolder().mkdirs();
        Json.save(saveFile, save);
    }

    protected void scoreRanking() {
        World world = Bukkit.getWorlds().get(0);
        AreasFile areasFile;
        areasFile = Json.load(new File(new File(Bukkit.getWorlds().get(0).getWorldFolder(), "areas"), "Race.json"),
                              AreasFile.class, () -> null);
        if (areasFile == null) return;
        Location spawnLocation = world.getSpawnLocation();
        List<UUID> uuids = save.rankScores();
        int winnerIndex = 0;
        for (Area area : areasFile.getAreas().getWinner()) {
            int index = winnerIndex++;
            if (uuids.size() <= index) break;
            UUID uuid = uuids.get(index);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Location location = area.min.toLocation(world);
            location.setDirection(spawnLocation.toVector().subtract(location.toVector()).normalize());
            player.teleport(location);
        }
        for (Area area : areasFile.getAreas().getViewer()) {
            List<Vec3i> viewerVecs = area.enumerate();
            Collections.shuffle(viewerVecs);
            int viewerIndex = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                int index = viewerIndex++;
                int indexOf = uuids.indexOf(player.getUniqueId());
                if (indexOf >= 0 && indexOf < winnerIndex) continue;
                Vec3i viewerVec = viewerVecs.get(index % viewerVecs.size());
                Location location = viewerVec.toLocation(world);
                location.setDirection(spawnLocation.toVector().subtract(location.toVector()).normalize());
                player.teleport(location);
            }
            break;
        }
    }
}
