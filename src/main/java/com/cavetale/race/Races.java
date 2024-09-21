package com.cavetale.race;

import com.cavetale.core.util.Json;
import com.winthier.creative.BuildWorld;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Races manager.
 */
@RequiredArgsConstructor
public final class Races {
    public static final String RACE_JSON = "race.json";
    private final RacePlugin plugin;
    private final Map<String, Race> worldRaceMap = new HashMap<>();
    private int ticks;

    public Race loadWorld(World world, BuildWorld buildWorld) {
        final File file = new File(world.getWorldFolder(), RACE_JSON);
        if (!file.exists()) return null;
        Tag tag = Json.load(file, Tag.class);
        if (tag == null) {
            plugin.getLogger().warning("Failed to load race: " + file);
            return null;
        }
        final Race race = new Race(plugin, buildWorld, world.getName(), world, file, tag);
        if (tag.fix()) {
            plugin.getLogger().info("Race fixed: " + buildWorld.getName());
            race.save();
        }
        race.onEnable();
        worldRaceMap.put(race.getWorldName(), race);
        return race;
    }

    public Race unloadWorld(World world) {
        final Race race = worldRaceMap.remove(world.getName());
        if (race == null) return null;
        race.save();
        race.onDisable();
        return race;
    }

    public Race create(World world, BuildWorld buildWorld) {
        final File file = new File(world.getWorldFolder(), RACE_JSON);
        final Race race = new Race(plugin, buildWorld, world.getName(), world, file, new Tag());
        race.onEnable();
        worldRaceMap.put(race.getWorldName(), race);
        return race;
    }

    public Race start(World world, BuildWorld buildWorld) {
        final Race race = loadWorld(world, buildWorld);
        race.getTag().setPhase(Phase.IDLE);
        race.startRace(plugin.getLobbyWorld().getPlayers());
        if (plugin.getSave().isEvent()) {
            plugin.getSave().setEventRaceWorld(race.getWorldName());
        }
        return race;
    }

    public void unloadAll() {
        for (Race race : all()) {
            race.onDisable();
        }
        worldRaceMap.clear();
    }

    public void saveAll() {
        for (Race race : all()) {
            race.save();
        }
    }

    public Race at(Location loc) {
        final Race race = inWorld(loc.getWorld());
        if (race == null || !Race.containsHorizontal(race.tag.area, loc)) {
            return null;
        }
        return race;
    }

    public Race at(Block block) {
        final Race race = inWorld(block.getWorld());
        if (race == null || !Race.containsHorizontal(race.tag.area, block)) {
            return null;
        }
        return race;
    }

    public Race inWorld(String worldName) {
        return worldRaceMap.get(worldName);
    }

    public Race inWorld(World world) {
        return worldRaceMap.get(world.getName());
    }

    public void tick() {
        for (Race race : all()) {
            race.tick(ticks);
        }
        ticks += 1;
    }

    public boolean isRace(Location loc) {
        return at(loc) != null;
    }

    public boolean isRace(Block block) {
        return at(block) != null;
    }

    public List<Race> all() {
        return List.copyOf(worldRaceMap.values());
    }

    public List<String> getWorldNames() {
        return List.copyOf(worldRaceMap.keySet());
    }
}
