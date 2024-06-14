package com.cavetale.race;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.editor.EditMenuDelegate;
import com.cavetale.core.editor.EditMenuNode;
import com.cavetale.core.editor.Editor;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.struct.Cuboid;
import com.cavetale.core.struct.Vec3i;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class RaceEditCommand extends AbstractCommand<RacePlugin> {
    protected RaceEditCommand(final RacePlugin plugin) {
        super(plugin, "raceedit");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("list")
            .caller(this::list)
            .description("List all races");
        rootNode.addChild("info")
            .playerCaller(this::info)
            .description("Info about current race course");
        rootNode.addChild("create").arguments("<name>")
            .description("Create a new race")
            .playerCaller(this::create);
        rootNode.addChild("setspawn")
            .caller(this::setspawn)
            .description("Set race spawn location");
        rootNode.addChild("edit")
            .caller(this::edit)
            .description("Edit mode");
        rootNode.addChild("start")
            .caller(this::start)
            .description("Start a race");
        rootNode.addChild("stop")
            .caller(this::stop)
            .description("Stop a race");
        rootNode.addChild("debug")
            .caller(this::debug)
            .description("Debug command");
        rootNode.addChild("type").arguments("<type>")
            .description("Change race type")
            .completers(CommandArgCompleter.enumLowerList(RaceType.class))
            .caller(this::type);
        rootNode.addChild("laps").arguments("<laps>")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .description("Change lap number")
            .caller(this::laps);
        rootNode.addChild("editor")
            .playerCaller(this::editor);
        // Checkpoints
        CommandNode checkpointNode = rootNode.addChild("cp")
            .alias("checkpoint")
            .description("Checkpoint commands");
        checkpointNode.addChild("list").denyTabCompletion()
            .description("List all checkpoints")
            .playerCaller(this::checkpointList);
        checkpointNode.addChild("add").arguments("[index]")
            .completers(this::completeCheckpointIndex)
            .description("Add a new checkpoint")
            .playerCaller(this::checkpointAdd);
        checkpointNode.addChild("remove").arguments("<index>")
            .completers(this::completeCheckpointIndex)
            .description("Remove a checkpoint")
            .playerCaller(this::checkpointRemove);
        checkpointNode.addChild("info").arguments("<index>")
            .completers(this::completeCheckpointIndex)
            .description("Print checkpoint info info")
            .playerCaller(this::checkpointInfo);
        checkpointNode.addChild("swap").arguments("<from> <to>")
            .completers(this::completeCheckpointIndex)
            .description("Swap two checkpoints")
            .playerCaller(this::checkpointSwap);
        checkpointNode.addChild("here").denyTabCompletion()
            .description("Print checkpoints at your current location")
            .playerCaller(this::checkpointHere);
        checkpointNode.addChild("tp").arguments("<index>")
            .completers(this::completeCheckpointIndex)
            .description("Teleport to a checkpoint")
            .playerCaller(this::checkpointTeleport);
        checkpointNode.addChild("set").arguments("<index>")
            .completers(this::completeCheckpointIndex)
            .description("Update a checkpoint with a new area")
            .playerCaller(this::checkpointSet);
        //
        rootNode.addChild("startvector")
            .completableList(Arrays.asList("clear", "add", "remove"))
            .caller(this::startvector);
        rootNode.addChild("goodies")
            .completableList(Arrays.asList("clear", "add", "remove"))
            .caller(this::goodies);
        rootNode.addChild("coins")
            .completableList(Arrays.asList("clear", "add", "remove"))
            .caller(this::coins);
        rootNode.addChild("event").denyTabCompletion()
            .description("Toggle event mode")
            .playerCaller(this::event);
        rootNode.addChild("eventrace").denyTabCompletion()
            .description("Make current race the event race")
            .playerCaller(this::eventRace);
        rootNode.addChild("setarea").denyTabCompletion()
            .description("Set race area")
            .playerCaller(this::setArea);
        rootNode.addChild("mount").denyTabCompletion()
            .description("Summon a mount for yourself")
            .playerCaller(this::mount);
        rootNode.addChild("maxduration").arguments("<seconds>")
            .playerCaller(this::maxDuration);
        rootNode.addChild("playerreset").denyTabCompletion()
            .playerCaller(this::playerReset);
        rootNode.addChild("teleport").arguments("<race>")
            .alias("tp")
            .completers(CommandArgCompleter.supplyList(() -> plugin.races.names()))
            .playerCaller(this::teleport);
        rootNode.addChild("give").arguments("<player> <goody>")
            .completers(CommandArgCompleter.NULL,
                        CommandArgCompleter.enumLowerList(GoodyItem.class))
            .senderCaller(this::give);
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
    }

    private Race requireRace(Player player) {
        Race race = plugin.races.at(player.getLocation());
        if (race == null) {
            throw new CommandWarn("You're not within a race area!");
        }
        return race;
    }

    private int requireInt(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Number expected: " + arg);
        }
    }

    private boolean list(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        List<Race> races = plugin.races.all();
        context.message(text(races.size() + " race(s)", YELLOW));
        for (Race race : races) {
            context.message(text("  " + race.listString()));
        }
        return true;
    }

    private boolean info(Player player, String[] args) {
        if (args.length != 0) return false;
        Race race = requireRace(player);
        if (race == null) throw new CommandWarn("There is no race here!");
        player.sendMessage(textOfChildren(text("worldName ", GRAY), text(race.tag.worldName, WHITE)));
        player.sendMessage(textOfChildren(text("type ", GRAY), text(race.tag.type.name(), WHITE)));
        player.sendMessage(textOfChildren(text("area ", GRAY), text(race.tag.area.toString(), WHITE)));
        player.sendMessage(textOfChildren(text("spawnArea ", GRAY), text(race.tag.spawnArea.toString(), WHITE)));
        player.sendMessage(textOfChildren(text("spawnLocation ", GRAY), text(race.tag.spawnLocation.simpleString(), WHITE)));
        player.sendMessage(textOfChildren(text("startVectors ", GRAY), text(race.tag.startVectors.size(), WHITE)));
        player.sendMessage(textOfChildren(text("checkpoints ", GRAY), text(race.tag.checkpoints.size(), WHITE)));
        player.sendMessage(textOfChildren(text("goodies ", GRAY), text(race.tag.goodies.size(), WHITE)));
        player.sendMessage(textOfChildren(text("laps ", GRAY), text(race.tag.laps, WHITE)));
        player.sendMessage(textOfChildren(text("racing ", GRAY), text(race.tag.countRacers(), WHITE)));
        player.sendMessage(textOfChildren(text("maxDuration ", GRAY), text(race.formatTimeShort(race.tag.maxDuration * 1000L) + " (" + race.tag.maxDuration + ")", WHITE)));
        return true;
    }

    private boolean create(Player player, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        Race race = plugin.races.named(name);
        Cuboid cuboid = Cuboid.requireSelectionOf(player);
        if (race == null) {
            race = new Race(plugin, name, new Tag());
            plugin.races.add(race);
        }
        race.setWorld(player.getWorld());
        race.setArea(cuboid);
        race.setSpawnLocation(player.getLocation());
        race.save();
        player.sendMessage(text("Race created: " + race.listString(), YELLOW));
        return true;
    }

    private boolean setspawn(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        Cuboid cuboid = Cuboid.requireSelectionOf(player);
        race.setSpawnLocation(player.getLocation());
        race.setSpawnArea(cuboid);
        race.save();
        context.message(text(race.name + ": Spawn set to your WorldEdit selection and current location", YELLOW));
        return true;
    }

    private boolean edit(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        if (race.tag.phase == Phase.EDIT) {
            race.setPhase(Phase.IDLE);
            context.message(text(race.name + ": Edit mode disabled", YELLOW));
        } else {
            race.setPhase(Phase.EDIT);
            context.message(text(race.name + ": Edit mode enabled", YELLOW));
        }
        return true;
    }

    private boolean start(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0 && args.length != 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        if (args.length >= 1) {
            int laps = requireInt(args[0]);
            if (laps <= 0) throw new CommandWarn("Not positive: " + laps);
            race.setLaps(laps);
            race.save();
            context.message(text(race.name + ": Laps updated: " + laps, YELLOW));
        }
        race.startRace();
        final List<String> names = new ArrayList<>();
        for (Racer racer : race.getRacers()) {
            names.add(racer.getName());
        }
        context.message(text("Race started: " + String.join(", ", names), YELLOW));
        return true;
    }

    private List<String> completeCheckpointIndex(CommandContext context, CommandNode node, String arg) {
        if (!context.isPlayer()) return List.of();
        final Race race = plugin.races.at(context.player.getLocation());
        if (race == null) return List.of();
        final int size = race.getCheckpoints().size();
        final List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i += 1) {
            final String name = "" + i;
            if (name.contains(arg)) {
                result.add(name);
            }
        }
        return result;
    }

    private void checkpointList(Player player) {
        Race race = requireRace(player);
        List<Checkpoint> checkpoints = race.getCheckpoints();
        player.sendMessage(text(race.name + ": " + checkpoints.size() + " checkpoint(s)", YELLOW));
        for (int i = 0; i < checkpoints.size(); i += 1) {
            final Checkpoint checkpoint = checkpoints.get(i);
            final String cmd = "/raceedit cp tp " + i;
            player.sendMessage(textOfChildren(text(" " + i), text(" " + checkpoint, YELLOW))
                               .insertion(cmd)
                               .hoverEvent(showText(text(cmd, GRAY)))
                               .clickEvent(suggestCommand(cmd)));
        }
    }

    private boolean checkpointAdd(Player player, String[] args) {
        if (args.length > 1) return false;
        Race race = requireRace(player);
        Cuboid cuboid = Cuboid.requireSelectionOf(player);
        List<Checkpoint> checkpoints = race.getCheckpoints();
        int index = args.length == 0
            ? checkpoints.size()
            : requireInt(args[0]);
        if (index < 0 || index > checkpoints.size()) {
            throw new CommandWarn("Invalid index: " + index);
        }
        checkpoints.add(index, new Checkpoint(cuboid));
        race.setCheckpoints(checkpoints);
        race.save();
        final String cmd = "/raceedit cp tp " + index;
        player.sendMessage(text(race.name + ": Checkpoint added: " + cuboid, YELLOW)
                           .insertion(cmd)
                           .hoverEvent(showText(text(cmd, GRAY)))
                           .clickEvent(suggestCommand(cmd)));
        return true;
    }

    private boolean checkpointRemove(Player player, String[] args) {
        if (args.length != 1) return false;
        Race race = requireRace(player);
        List<Checkpoint> checkpoints = race.getCheckpoints();
        int index = requireInt(args[0]);
        if (index < 0 || index >= checkpoints.size()) {
            throw new CommandWarn("Out of bounds: " + index);
        }
        Checkpoint old = checkpoints.remove(index);
        race.setCheckpoints(checkpoints);
        race.save();
        player.sendMessage(text(race.name + ": Checkpoint #" + index + " removed: " + old, YELLOW));
        return true;
    }

    private boolean checkpointInfo(Player player, String[] args) {
        if (args.length != 1) return false;
        final Race race = requireRace(player);
        final int index = CommandArgCompleter.requireInt(args[0], i -> i >= 0 && i < race.getCheckpoints().size());
        final Checkpoint checkpoint = race.getCheckpoints().get(index);
        final String cmd = "/raceedit cp tp " + index;
        player.sendMessage(textOfChildren(text(race.name + ":", YELLOW),
                                          text(" checkpoint #" + index, GRAY),
                                          text(" " + checkpoint, WHITE))
                           .insertion(cmd)
                           .hoverEvent(showText(text(cmd, GRAY)))
                           .clickEvent(suggestCommand(cmd)));
        return true;
    }

    private boolean checkpointSwap(Player player, String[] args) {
        if (args.length != 2) return false;
        Race race = requireRace(player);
        List<Checkpoint> checkpoints = race.getCheckpoints();
        int indexA = requireInt(args[0]);
        int indexB = requireInt(args[1]);
        if (indexA < 0 || indexA >= checkpoints.size()) {
            throw new CommandWarn("Out of bounds: " + indexA);
        }
        if (indexB < 0 || indexB >= checkpoints.size()) {
            throw new CommandWarn("Out of bounds: " + indexB);
        }
        Checkpoint a = checkpoints.get(indexA);
        Checkpoint b = checkpoints.get(indexB);
        checkpoints.set(indexA, b);
        checkpoints.set(indexB, a);
        race.setCheckpoints(checkpoints);
        race.save();
        player.sendMessage(text(race.name + ": Checkpoints swapped: " + indexA + ", " + indexB, YELLOW));
        return true;
    }

    private void checkpointHere(Player player) {
        final Race race = requireRace(player);
        final List<Checkpoint> checkpoints = race.getCheckpoints();
        final Location location = player.getLocation();
        int count = 0;
        for (int i = 0; i < checkpoints.size(); i += 1) {
            Checkpoint cp = checkpoints.get(i);
            if (!cp.getArea().contains(location)) continue;
            final String cmd = "/raceedit cp tp " + i;
            player.sendMessage(textOfChildren(text(race.name + ":", YELLOW),
                                              text("Checkpoint here: ", GRAY),
                                              text("#" + i, YELLOW))
                               .insertion(cmd)
                               .hoverEvent(showText(text(cmd, GRAY)))
                               .clickEvent(suggestCommand(cmd)));
            count += 1;
        }
        if (count == 0) {
            throw new CommandWarn("There are no checkpoints here");
        }
    }

    private boolean checkpointTeleport(Player player, String[] args) {
        if (args.length != 1) return false;
        final Race race = requireRace(player);
        final int index = CommandArgCompleter.requireInt(args[0], i -> i >= 0 && i < race.getCheckpoints().size());
        final Checkpoint checkpoint = race.getCheckpoints().get(index);
        final Location location = checkpoint.getArea().getCenterExact().toLocation(player.getWorld());
        player.teleport(location);
        final String cmd = "/raceedit cp tp " + index;
        player.sendMessage(textOfChildren(text("Teleported to checkpoint #" + index, YELLOW),
                                          text(" " + checkpoint, WHITE))
                           .insertion(cmd)
                           .hoverEvent(showText(text(cmd, GRAY)))
                           .clickEvent(suggestCommand(cmd)));
        return true;
    }

    private boolean checkpointSet(Player player, String[] args) {
        if (args.length != 1) return false;
        final Race race = requireRace(player);
        final List<Checkpoint> checkpoints = race.getCheckpoints();
        final int index = CommandArgCompleter.requireInt(args[0], i -> i >= 0 && i < checkpoints.size());
        final Cuboid selection = Cuboid.requireSelectionOf(player);
        checkpoints.set(index, new Checkpoint(selection));
        race.setCheckpoints(checkpoints);
        race.save();
        final String cmd = "/raceedit cp tp " + index;
        player.sendMessage(text("Updated checkpoint #" + index + ": " + selection, YELLOW)
                           .insertion(cmd)
                           .hoverEvent(showText(text(cmd, GRAY)))
                           .clickEvent(suggestCommand(cmd)));
        return true;
    }

    private boolean type(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        try {
            race.tag.type = RaceType.valueOf(args[0].toUpperCase());
        } catch (IllegalStateException iae) {
            throw new CommandWarn("Invalid type: " + args[0]);
        }
        race.save();
        context.message(text(race.name + ": Type updated: " + race.tag.type, YELLOW));
        return true;
    }

    private boolean laps(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        race.tag.laps = requireInt(args[0]);
        race.save();
        context.message(text(race.name + ": Laps updated: " + race.tag.laps, YELLOW));
        return true;
    }

    private void editor(Player player) {
        Race race = requireRace(player);
        Editor.get().open(plugin, player, race.tag, new EditMenuDelegate() {
                @Override public Runnable getSaveFunction(EditMenuNode node) {
                    return race::save;
                }
            });
    }

    private boolean debug(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        context.message(Json.serialize(race.tag));
        return true;
    }

    private boolean stop(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        race.stopRace();
        context.message(text("Race stopped: " + race.name, YELLOW));
        race.save();
        return true;
    }

    private boolean startvector(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        switch (args[0]) {
        case "add": {
            Race race = requireRace(player);
            Cuboid cuboid = Cuboid.requireSelectionOf(player);
            int count = 0;
            for (Vec3i vector : cuboid.enumerate()) {
                if (!race.tag.startVectors.contains(vector)) {
                    race.tag.startVectors.add(vector);
                    count += 1;
                }
            }
            if (count > 0) race.save();
            player.sendMessage(text("Start vectors added: " + count, YELLOW));
            return true;
        }
        case "remove": {
            Race race = requireRace(player);
            Cuboid cuboid = Cuboid.requireSelectionOf(player);
            int count = 0;
            for (Vec3i vector : cuboid.enumerate()) {
                if (race.tag.startVectors.contains(vector)) {
                    race.tag.startVectors.remove(vector);
                    count += 1;
                }
            }
            if (count > 0) race.save();
            player.sendMessage(text("Start vectors removed: " + count, YELLOW));
            return true;
        }
        case "clear": {
            Race race = requireRace(player);
            int count = race.tag.startVectors.size();
            race.tag.startVectors.clear();
            if (count > 0) race.save();
            player.sendMessage(text("Start vectors cleared: " + count, YELLOW));
            return true;
        }
        default: return false;
        }
    }

    private boolean goodies(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        switch (args[0]) {
        case "add": {
            Race race = requireRace(player);
            Vec3i vec = Cuboid.requireSelectionOf(player).getMin();
            if (race.tag.goodies.contains(vec)) {
                throw new CommandWarn("Goodie already exists: " + vec);
            }
            race.tag.goodies.add(vec);
            race.save();
            player.sendMessage(text("Goodie added: " + vec, YELLOW));
            return true;
        }
        case "remove": {
            Race race = requireRace(player);
            Vec3i vec = Cuboid.requireSelectionOf(player).getMin();
            if (!race.tag.goodies.contains(vec)) {
                throw new CommandWarn("No goodie here: " + vec);
            }
            race.tag.goodies.remove(vec);
            race.save();
            player.sendMessage(text("Goodie removed: " + vec, YELLOW));
            return true;
        }
        case "clear": {
            Race race = requireRace(player);
            int count = race.tag.goodies.size();
            race.tag.goodies.clear();
            if (count > 0) race.save();
            player.sendMessage(text("Goodies cleared: " + count, YELLOW));
            return true;
        }
        default: return false;
        }
    }

    private boolean coins(CommandContext context, CommandNode node, String[] args) {
        if (args.length < 1) return false;
        Player player = context.requirePlayer();
        switch (args[0]) {
        case "add": {
            if (args.length != 1 && args.length != 4) return false;
            Race race = requireRace(player);
            if (args.length >= 2) {
                Vec3i vec = new Vec3i(requireInt(args[1]),
                                      requireInt(args[2]),
                                      requireInt(args[3]));
                if (race.tag.coins.contains(vec)) {
                    throw new CommandWarn("Coin already exists: " + vec);
                }
                race.tag.coins.add(vec);
                race.save();
                player.sendMessage(text("Coin added: " + vec, YELLOW));
            } else {
                int count = 0;
                for (Vec3i vec : Cuboid.requireSelectionOf(player).enumerate()) {
                    if (race.tag.coins.contains(vec)) {
                        continue;
                    } else {
                        race.tag.coins.add(vec);
                        count += 1;
                    }
                }
                if (count > 0) race.save();
                player.sendMessage(text("Coins added: " + count, YELLOW));
            }
            return true;
        }
        case "remove": {
            if (args.length != 1) return false;
            Race race = requireRace(player);
            Cuboid cuboid = Cuboid.requireSelectionOf(player);
            if (!race.tag.coins.removeAll(cuboid.enumerate())) {
                throw new CommandWarn("No coins here: " + cuboid);
            }
            race.clearEntities();
            race.save();
            player.sendMessage(text("Coins removed: " + cuboid, YELLOW));
            return true;
        }
        case "clear": {
            if (args.length != 1) return false;
            Race race = requireRace(player);
            int count = race.tag.coins.size();
            race.tag.coins.clear();
            if (count > 0) race.save();
            player.sendMessage(text("Coins cleared: " + count, YELLOW));
            return true;
        }
        default: return false;
        }
    }

    private boolean event(Player player, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                plugin.save.event = Boolean.parseBoolean(args[0]);
            } catch (IllegalArgumentException iae) {
                player.sendMessage(Component.text("Invalid boolean: " + args[0], RED));
                return true;
            }
            plugin.save();
        }
        player.sendMessage(Component.text("Event mode is ")
                           .append(plugin.save.event
                                   ? Component.text("Enabled", GREEN)
                                   : Component.text("Disabled", GREEN)));
        return true;
    }

    private boolean eventRace(Player player, String[] args) {
        if (args.length == 1 && args[0].equals("reset")) {
            plugin.save.eventRace = null;
            plugin.save();
            player.sendMessage(Component.text("Event race reset", YELLOW));
            return true;
        }
        if (args.length != 0) return false;
        Race race = requireRace(player);
        plugin.save.eventRace = race.name;
        plugin.save();
        player.sendMessage(Component.text("Event race set to " + plugin.save.eventRace,
                                          YELLOW));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (player == online) continue;
            online.teleport(race.getSpawnLocation());
        }
        return true;
    }

    private boolean setArea(Player player, String[] args) {
        Race race = requireRace(player);
        Cuboid cuboid = Cuboid.requireSelectionOf(player);
        race.tag.area = cuboid;
        race.save();
        player.sendMessage(text("Event area reset: " + race.tag.area, GREEN));
        return true;
    }

    private boolean mount(Player player, String[] args) {
        Race race = requireRace(player);
        if (!race.tag.type.isMounted()) {
            throw new CommandWarn("Not mounted: " + race.tag.type);
        }
        race.tag.type.spawnVehicle(player.getLocation());
        player.sendMessage(text("Vehicle spawned", YELLOW));
        return true;
    }

    private boolean maxDuration(Player player, String[] args) {
        if (args.length != 1) return false;
        Race race = requireRace(player);
        race.tag.setMaxDuration(requireInt(args[0]));
        race.save();
        player.sendMessage(Component.text("Max duration set to " + race.tag.getMaxDuration() + " seconds",
                                          YELLOW));
        return true;
    }

    private boolean scoreReset(CommandSender sender, String[] args) {
        plugin.save.scores.clear();
        plugin.save();
        sender.sendMessage(Component.text("All scores were reset!", YELLOW));
        return true;
    }

    private boolean scorePedestal(CommandSender sender, String[] args) {
        plugin.save.eventRace = null;
        plugin.save();
        sender.sendMessage(Component.text("Putting winners on pedestals...", YELLOW));
        plugin.scoreRanking(false);
        return true;
    }

    private boolean scoreReward(CommandSender sender, String[] args) {
        plugin.save.eventRace = null;
        plugin.save();
        sender.sendMessage(Component.text("Giving winners rewards...", YELLOW));
        plugin.scoreRanking(true);
        return true;
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int amount = requireInt(args[1]);
        plugin.save.scores.compute(target.uuid, (u, i) -> i != null ? i + amount : amount);
        plugin.save();
        sender.sendMessage("Score of " + target.name + " changed to " + plugin.save.scores.get(target.uuid));
        return true;
    }

    private boolean playerReset(CommandSender sender, String[] args) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setWalkSpeed(0.2f);
        }
        sender.sendMessage("Players walk speed reset");
        return true;
    }

    protected boolean teleport(Player player, String[] args) {
        if (args.length != 1) return false;
        Race race = plugin.races.named(args[0]);
        if (race == null) {
            throw new CommandWarn("Race not found: " + args[0]);
        }
        player.teleport(race.getSpawnLocation());
        player.sendMessage(Component.text("Teleported to race " + race.name));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) throw new CommandWarn("Player not found: " + args[0]);
        GoodyItem goody;
        try {
            goody = GoodyItem.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Goody not found: " + args[1]);
        }
        target.getInventory().addItem(goody.createItemStack());
        sender.sendMessage(text("Gave " + goody + " to " + target.getName(), YELLOW));
        return true;
    }
}
