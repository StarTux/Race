package com.cavetale.race;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.winthier.creative.BuildWorld;
import java.util.List;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class RaceCommand extends AbstractCommand<RacePlugin> {
    protected RaceCommand(final RacePlugin plugin) {
        super(plugin, "race");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Race Command");
        if (!NetworkServer.RACE.isThisServer()) {
            rootNode.description("Join the race server")
                .playerCaller(this::racePort);
            return;
        }
        rootNode.playerCaller(this::race);
        rootNode.addChild("view").arguments("<map>")
            .hidden(true)
            .description("View map details")
            .playerCaller(this::view);
        rootNode.addChild("timetrial").arguments("<map>")
            .hidden(true)
            .description("Start a time trial")
            .playerCaller(this::timeTrial);
    }

    /**
     * The command on non-race servers.
     */
    private void racePort(Player player) {
        player.performCommand("server race");
    }

    private void race(Player player) {
        new RaceTypeMenu(player).open();
    }

    private BuildWorld requireRaceMap(String path) {
        final BuildWorld buildWorld = BuildWorld.findWithPath(path);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != MinigameMatchType.RACE || !buildWorld.getRow().isPurposeConfirmed()) {
            throw new CommandWarn("Invalid map: " + path);
        }
        return buildWorld;
    }

    private boolean view(Player player, String[] args) {
        if (args.length != 1) return false;
        if (plugin.getRaces().inWorld(player.getWorld()) != null) {
            throw new CommandWarn("You're already in a race");
        }
        final BuildWorld buildWorld = requireRaceMap(args[0]);
        new RaceMapMenu(player, buildWorld).open();
        return true;
    }

    private boolean timeTrial(Player player, String[] args) {
        if (args.length != 1) return false;
        if (plugin.getRaces().inWorld(player.getWorld()) != null) {
            throw new CommandWarn("You're already in a race");
        }
        final BuildWorld buildWorld = requireRaceMap(args[0]);
        plugin.getRaces().start(buildWorld, race -> {
                race.setTimeTrial(true);
                race.loadAllRaceChunks();
                race.getTag().setPhase(Phase.IDLE);
                race.startRace(List.of(player));
            });
        return true;
    }
}
