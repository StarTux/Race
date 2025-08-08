package com.cavetale.race;

import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.core.util.Json;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.vote.MapVote;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import static com.cavetale.mytems.util.Text.wrapLore;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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

    public void create(BuildWorld buildWorld, Consumer<Race> callback) {
        buildWorld.makeLocalCopyAsync(world -> {
                final Race race = create(world, buildWorld);
                callback.accept(race);
            });
    }

    public Race create(World world, BuildWorld buildWorld) {
        final File file = new File(world.getWorldFolder(), RACE_JSON);
        final Race race = new Race(plugin, buildWorld, world.getName(), world, file, new Tag());
        race.onEnable();
        worldRaceMap.put(race.getWorldName(), race);
        return race;
    }

    public void start(BuildWorld buildWorld, Consumer<Race> callback) {
        buildWorld.makeLocalCopyAsync(world -> {
                final Race race = start(world, buildWorld);
                callback.accept(race);
            });
    }

    public Race start(World world, BuildWorld buildWorld) {
        final Race race = loadWorld(world, buildWorld);
        race.loadAllRaceChunks();
        race.getTag().setPhase(Phase.IDLE);
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

    public static Map<BuildWorld, Tag> getAllRaceTags(boolean confirmationRequired) {
        final Map<BuildWorld, Tag> result = new IdentityHashMap<>();
        for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(MinigameMatchType.RACE, confirmationRequired)) {
            final Tag tag = Json.load(new File(buildWorld.getCreativeWorldFolder(), Races.RACE_JSON), Tag.class);
            if (tag == null) continue;
            result.put(buildWorld, tag);
        }
        return result;
    }

    public static Map<RaceType, List<BuildWorld>> sortRaceTypes(Map<BuildWorld, Tag> raceTags) {
        final Map<RaceType, List<BuildWorld>> result = new EnumMap<>(RaceType.class);
        for (Map.Entry<BuildWorld, Tag> entry : raceTags.entrySet()) {
            final BuildWorld buildWorld = entry.getKey();
            final Tag tag = entry.getValue();
            result.computeIfAbsent(tag.getType(), t -> new ArrayList<>()).add(buildWorld);
        }
        return result;
    }

    public static Tag getRaceTag(BuildWorld buildWorld) {
        return Json.load(new File(buildWorld.getCreativeWorldFolder(), Races.RACE_JSON), Tag.class);
    }

    public boolean startMapVote() {
        if (MapVote.isActive(MinigameMatchType.RACE)) {
            return false;
        }
        final boolean isEvent = plugin.getSave().isEvent();
        final Map<BuildWorld, Tag> tagMap = getAllRaceTags(true);
        MapVote.start(MinigameMatchType.RACE, v -> {
                v.setTitle(isEvent
                           ? plugin.getGrandPrixTitle()
                           : plugin.getRaceTitle());
                v.setLobbyWorld(plugin.getLobbyWorld());
                v.setCallback(result -> {
                        plugin.getRaces().start(result.getBuildWorldWinner(), race -> {
                                race.startRace(plugin.getLobbyWorld().getPlayers());
                                if (isEvent) {
                                    plugin.getSave().setEventRaceWorld(race.getWorldName());
                                }
                            });
                    });
                v.setMapTooltipHandler(builder -> {
                        final Tag tag = tagMap.get(builder.getBuildWorld());
                        if (tag == null) return;
                        final RaceType raceType = tag.getType();
                        if (raceType == null) return;
                        final List<Component> lines = new ArrayList<>(builder.getDescription());
                        lines.add(empty());
                        lines.add(textOfChildren(ItemKinds.icon(raceType.createIcon()),
                                                 text(raceType.getDisplayName(), GOLD)));
                        lines.addAll(wrapLore(raceType.getDescription(), c -> c.color(GRAY)));
                        builder.setDescription(lines);
                    });
            });
        return true;
    }
}
