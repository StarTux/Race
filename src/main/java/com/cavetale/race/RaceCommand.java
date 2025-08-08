package com.cavetale.race;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.winthier.creative.BuildWorld;
import java.util.List;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
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
        rootNode.addChild("practice").arguments("<map>")
            .hidden(true)
            .description("Start a practice run")
            .playerCaller(this::practice);
        rootNode.addChild("startvote").denyTabCompletion()
            .hidden(true)
            .description("Start a map vote")
            .playerCaller(this::startVote);
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

    private void requirePlayerFreedom(Player player) {
        if (plugin.getSave().isEvent()) {
            throw new CommandWarn("Cannot start a race during events");
        }
        if (!plugin.getLobbyWorld().equals(player.getWorld())) {
            throw new CommandWarn("You're not in the lobby");
        }
    }

    private boolean view(Player player, String[] args) {
        if (args.length != 1) return false;
        final BuildWorld buildWorld = requireRaceMap(args[0]);
        new RaceMapMenu(player, buildWorld).open();
        return true;
    }

    private boolean timeTrial(Player player, String[] args) {
        if (args.length != 1) return false;
        requirePlayerFreedom(player);
        final BuildWorld buildWorld = requireRaceMap(args[0]);
        plugin.getRaces().start(buildWorld, race -> {
                race.setTimeTrial(true);
                race.startRace(List.of(player));
            });
        return true;
    }

    private boolean practice(Player player, String[] args) {
        if (args.length != 1) return false;
        requirePlayerFreedom(player);
        final BuildWorld buildWorld = requireRaceMap(args[0]);
        plugin.getRaces().start(buildWorld, race -> {
                race.setPractice(true);
                race.startRace(List.of(player));
            });
        return true;
    }

    private void startVote(Player player) {
        requirePlayerFreedom(player);
        if (!plugin.getRaces().startMapVote()) {
            throw new CommandWarn("Map vote already active!");
        }
        player.sendMessage(text("Map vote started in the lobby", GREEN));
    }
}
