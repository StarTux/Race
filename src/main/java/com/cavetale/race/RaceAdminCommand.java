package com.cavetale.race;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.cavetale.race.sql.SQLPlayerMapRecord;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
import com.winthier.creative.review.MapReview;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Only loaded on the Race server because several commands will copy
 * or delete race worlds.
 */
public final class RaceAdminCommand extends AbstractCommand<RacePlugin> {
    protected RaceAdminCommand(final RacePlugin plugin) {
        super(plugin, "raceadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.description("Race Admin Command");
        rootNode.addChild("start").arguments("<race>")
            .description("Start a race")
            .completers(CommandArgCompleter.supplyList(RaceAdminCommand::listRaceWorldPaths))
            .senderCaller(this::start);
        rootNode.addChild("startvote").denyTabCompletion()
            .description("Start a map vote")
            .senderCaller(this::startVote);
        rootNode.addChild("startreview").denyTabCompletion()
            .description("Start a map review")
            .playerCaller(this::startReview);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop current race")
            .playerCaller(this::stop);
        rootNode.addChild("event").denyTabCompletion()
            .description("Toggle event mode")
            .playerCaller(this::event);
        // score
        CommandNode scoreNode = rootNode.addChild("score")
            .description("Grand Prix Scores");
        scoreNode.addChild("reset").denyTabCompletion()
            .description("Reset all scores")
            .senderCaller(this::scoreReset);
        scoreNode.addChild("pedestal").denyTabCompletion()
            .description("Put winners on pedestals")
            .senderCaller(this::scorePedestal);
        scoreNode.addChild("reward").denyTabCompletion()
            .description("Give winners rewards")
            .senderCaller(this::scoreReward);
        scoreNode.addChild("add").arguments("<player> <amount>")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> true))
            .description("Modify player score")
            .senderCaller(this::scoreAdd);
        // records
        CommandNode recordNode = rootNode.addChild("record")
            .description("Timing records");
        recordNode.addChild("show").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(RaceAdminCommand::listRaceWorldPaths))
            .description("Print map records")
            .senderCaller(this::recordShow);
        recordNode.addChild("clear").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(RaceAdminCommand::listRaceWorldPaths))
            .description("Clear all map records")
            .senderCaller(this::recordClear);
        recordNode.addChild("delete").arguments("<world> <player>")
            .completers(CommandArgCompleter.supplyList(RaceAdminCommand::listRaceWorldPaths),
                        CommandArgCompleter.PLAYER_CACHE)
            .description("Delete a map record")
            .senderCaller(this::recordDelete);
    }

    private static List<String> listRaceWorldPaths() {
        final List<String> result = new ArrayList<>();
        for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(MinigameMatchType.RACE, false)) {
            result.add(buildWorld.getPath());
        }
        return result;
    }

    private BuildWorld requireRaceWorld(String path) {
        final BuildWorld buildWorld = BuildWorld.findWithPath(path);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != MinigameMatchType.RACE) {
            throw new CommandWarn("Race world not found: " + path);
        }
        return buildWorld;
    }

    private boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String path = args[0];
        final BuildWorld buildWorld = requireRaceWorld(args[0]);
        plugin.getRaces().start(buildWorld, race -> {
                plugin.getSave().setEventRaceWorld(race.getWorldName());
                race.startRace(plugin.getLobbyWorld().getPlayers());
            });
        sender.sendMessage(text("Starting race in " + buildWorld.getName(), YELLOW));
        return true;
    }

    private void startVote(CommandSender sender) {
        if (!plugin.getRaces().startMapVote()) {
            throw new CommandWarn("Map vote already active");
        }
        sender.sendMessage(text("Map vote started", YELLOW));
    }

    private void startReview(Player player) {
        final Race race = plugin.getRaces().inWorld(player.getWorld());
        if (race == null) throw new CommandWarn("There is no race here");
        MapReview.start(race.getWorld(), race.getBuildWorld())
            .remindAllOnce();
        player.sendMessage(text("Map Review started", YELLOW));
    }

    private void stop(Player player) {
        final Race race = plugin.getRaces().inWorld(player.getWorld());
        if (race == null) throw new CommandWarn("There is no race here");
        race.stopRace();
        race.removeAllPlayers();
        plugin.getRaces().unloadWorld(race.getWorld());
        Files.deleteWorld(race.getWorld());
    }

    private boolean event(Player player, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            plugin.getSave().setEvent(CommandArgCompleter.requireBoolean(args[0]));
            plugin.save();
        }
        player.sendMessage(Component.text("Event mode is ")
                           .append(plugin.getSave().isEvent()
                                   ? Component.text("Enabled", GREEN)
                                   : Component.text("Disabled", GREEN)));
        return true;
    }

    private boolean scoreReset(CommandSender sender, String[] args) {
        plugin.getSave().getScores().clear();
        plugin.save();
        sender.sendMessage(Component.text("All scores were reset!", YELLOW));
        return true;
    }

    private boolean scorePedestal(CommandSender sender, String[] args) {
        plugin.getSave().setEventRaceWorld(null);
        plugin.save();
        sender.sendMessage(Component.text("Putting winners on pedestals...", YELLOW));
        plugin.scoreRanking(false);
        return true;
    }

    private boolean scoreReward(CommandSender sender, String[] args) {
        plugin.getSave().setEventRaceWorld(null);
        plugin.save();
        sender.sendMessage(Component.text("Giving winners rewards...", YELLOW));
        final int rewardCount = Highscore.reward(
            plugin.getSave().getScores(),
            "race_grand_prix",
            TrophyCategory.CUP,
            plugin.getGrandPrixTitle(),
            hi -> "You earned " + hi.score + " points"
        );
        sender.sendMessage(text("Rewarded " + rewardCount + " players", YELLOW));
        Highscore.rewardMoneyWithFeedback(sender, plugin, plugin.getSave().getScores(), "Grand Prix");
        return true;
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        final PlayerCache target = PlayerCache.require(args[0]);
        final int amount = CommandArgCompleter.requireInt(args[1]);
        plugin.getSave().getScores().compute(target.uuid, (u, i) -> i != null ? i + amount : amount);
        plugin.save();
        sender.sendMessage("Score of " + target.name + " changed to " + plugin.getSave().getScores().get(target.uuid));
        return true;
    }

    private boolean recordShow(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final BuildWorld buildWorld = requireRaceWorld(args[0]);
        final List<SQLPlayerMapRecord> rows = plugin.getRecords().rank(buildWorld.getPath());
        if (rows == null || rows.isEmpty()) {
            throw new CommandWarn("No records found for world " + buildWorld.getPath());
        }
        sender.sendMessage(text("Found " + rows.size() + " records of world " + buildWorld.getPath(), YELLOW));
        int index = 0;
        for (SQLPlayerMapRecord row : rows) {
            sender.sendMessage(textOfChildren(text(++index, BLUE),
                                              text(" " + Race.formatTime(row.getTime()), YELLOW),
                                              text(" " + PlayerCache.nameForUuid(row.getPlayer()), WHITE),
                                              text(" " + row.getDate(), GRAY)));
        }
        return true;
    }

    private boolean recordClear(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final BuildWorld buildWorld = requireRaceWorld(args[0]);
        final List<SQLPlayerMapRecord> rows = plugin.getRecords().clear(buildWorld.getPath());
        if (rows == null || rows.isEmpty()) {
            throw new CommandWarn("No records found for world " + buildWorld.getPath());
        }
        sender.sendMessage(text("Deleted " + rows.size() + " records of world " + buildWorld.getPath(), YELLOW));
        return true;
    }

    private boolean recordDelete(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        final BuildWorld buildWorld = requireRaceWorld(args[0]);
        final PlayerCache playerCache = PlayerCache.require(args[1]);
        final SQLPlayerMapRecord row = plugin.getRecords().delete(buildWorld.getPath(), playerCache.getUuid());
        if (row == null) {
            throw new CommandWarn("No record for " + playerCache.getName() + " in " + buildWorld.getPath());
        }
        sender.sendMessage(text("Deleted record of " + playerCache.getName() + " in " + buildWorld.getPath()
                                + ": " + Race.formatTime(row.getTime()) + ", " + row.getDate(),
                                YELLOW));
        return true;
    }
}
