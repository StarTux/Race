package com.cavetale.race;

import com.cavetale.area.struct.Area;
import com.cavetale.area.struct.AreasFile;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.struct.Vec3i;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.SQLTrophy;
import com.cavetale.fam.trophy.Trophies;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.TextColor.color;

@Getter
public final class RacePlugin extends JavaPlugin {
    protected static RacePlugin instance;
    protected final RaceCommand raceCommand = new RaceCommand(this);
    protected final RaceEditCommand raceEditCommand = new RaceEditCommand(this);
    protected final RaceAdminCommand raceAdminCommand = new RaceAdminCommand(this);
    protected final Races races = new Races(this);
    protected final EventListener eventListener = new EventListener(this);
    protected Save save;
    protected File saveFile;
    private CreativeServerListener creativeServerListener;
    private RaceServerListener raceServerListener;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveFile = new File(getDataFolder(), "save.json");
        raceCommand.enable();
        raceEditCommand.enable();
        eventListener.enable();
        load();
        getServer().getScheduler().runTaskTimer(this, races::tick, 1, 1);
        switch (NetworkServer.current()) {
        case CREATIVE:
            creativeServerListener = new CreativeServerListener();
            creativeServerListener.enable();
            break;
        case RACE:
            raceServerListener = new RaceServerListener();
            raceServerListener.enable();
            raceAdminCommand.enable();
            break;
        default: break;
        }
    }

    @Override
    public void onDisable() {
        races.saveAll();
        races.unloadAll();
        save();
        if (creativeServerListener != null) {
            creativeServerListener.disable();
        }
    }

    protected void load() {
        save = Json.load(saveFile, Save.class, Save::new);
        if (save == null) save = new Save();
    }

    protected void save() {
        getDataFolder().mkdirs();
        Json.save(saveFile, save);
    }

    protected void scoreRanking(boolean giveRewards) {
        final World world = getLobbyWorld();
        AreasFile areasFile = AreasFile.load(world, "Race");
        if (areasFile == null) return;
        Location spawnLocation = world.getSpawnLocation();
        List<UUID> uuids = save.rankScores();
        int winnerIndex = 0;
        for (Area area : areasFile.getAreas().get("winner")) {
            int index = winnerIndex++;
            if (uuids.size() <= index) break;
            UUID uuid = uuids.get(index);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Location location = area.min.toCenterFloorLocation(world);
            location.setDirection(spawnLocation.toVector().subtract(location.toVector()).normalize());
            player.teleport(location);
            player.setGameMode(GameMode.ADVENTURE);
            Fireworks.spawnFirework(location.add(0, 2, 0));
            if (giveRewards && index <= 2) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + player.getName() + " GrandPrix");
            }
        }
        for (Area area : areasFile.getAreas().get("viewer")) {
            List<Vec3i> viewerVecs = area.enumerate();
            Collections.shuffle(viewerVecs);
            int viewerIndex = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                int index = viewerIndex++;
                int indexOf = uuids.indexOf(player.getUniqueId());
                if (indexOf >= 0 && indexOf < winnerIndex) continue;
                Vec3i viewerVec = viewerVecs.get(index % viewerVecs.size());
                Location location = viewerVec.toCenterFloorLocation(world);
                location.setDirection(spawnLocation.toVector().subtract(location.toVector()).normalize());
                player.teleport(location);
                player.setGameMode(GameMode.ADVENTURE);
            }
            break;
        }
        if (giveRewards) {
            var title = join(noSeparators(),
                             text("G", color(0xffff00)),
                             text("r", color(0xffff2b)),
                             text("a", color(0xffff55)),
                             text("n", color(0xffff80)),
                             text("d", color(0xffffaa)),
                             space(),
                             text("P", color(0xffff80)),
                             text("r", color(0xffff55)),
                             text("i", color(0xffff2b)),
                             text("x", color(0xffff00)));
            List<SQLTrophy> trophies = new ArrayList<>();
            int i = 0;
            int placement = 0;
            int lastScore = -1;
            for (UUID uuid : uuids) {
                final int score = save.getScores().getOrDefault(uuid, 0);
                if (score == 0) break;
                if (lastScore != score) {
                    lastScore = score;
                    placement += 1;
                }
                trophies.add(new SQLTrophy(uuid,
                                           "race_grand_prix",
                                           placement,
                                           TrophyCategory.CUP,
                                           title,
                                           "You earned " + score + " points"));
            }
            Trophies.insertTrophies(trophies);
        }
    }

    public static RacePlugin racePlugin() {
        return instance;
    }

    public static boolean isCreativeServer() {
        return instance.creativeServerListener != null;
    }

    public static boolean isRaceServer() {
        return instance.raceServerListener != null;
    }

    public World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }
}
