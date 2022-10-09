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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class RaceCommand extends AbstractCommand<RacePlugin> {
    protected RaceCommand(final RacePlugin plugin) {
        super(plugin, "race");
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
        rootNode.addChild("type")
            .caller(this::type)
            .description("Change race type");
        rootNode.addChild("laps")
            .caller(this::laps)
            .description("Change lap number");
        rootNode.addChild("editor")
            .playerCaller(this::editor);
        CommandNode checkpointNode = rootNode.addChild("checkpoint")
            .description("Checkpoint commands");
        checkpointNode.addChild("list")
            .description("List all checkpoints")
            .caller(this::checkpointList);
        checkpointNode.addChild("add")
            .description("Add a new checkpoint")
            .caller(this::checkpointAdd);
        checkpointNode.addChild("remove")
            .description("Remove a checkpoint")
            .caller(this::checkpointRemove);
        checkpointNode.addChild("info")
            .description("Info about current checkpoint")
            .caller(this::checkpointInfo);
        checkpointNode.addChild("swap")
            .description("Swap two checkpoints")
            .caller(this::checkpointSwap);
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
        plugin.getCommand("race").setExecutor(this);
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
        context.message("" + ChatColor.YELLOW + races.size() + " race(s)");
        for (Race race : races) {
            context.message("  " + ChatColor.YELLOW + race.listString());
        }
        return true;
    }

    private boolean info(Player player, String[] args) {
        if (args.length != 0) return false;
        Race race = requireRace(player);
        if (race == null) throw new CommandWarn("There is no race here!");
        ChatColor a = ChatColor.GRAY;
        ChatColor b = ChatColor.WHITE;
        player.sendMessage(a + "worldName " + b + race.tag.worldName);
        player.sendMessage(a + "type " + b + race.tag.type);
        player.sendMessage(a + "area " + b + race.tag.area);
        player.sendMessage(a + "spawnArea " + b + race.tag.spawnArea);
        player.sendMessage(a + "spawnLocation " + b + race.tag.spawnLocation.simpleString());
        player.sendMessage(a + "startVectors " + b + race.tag.startVectors.size());
        player.sendMessage(a + "checkpoints " + b + race.tag.checkpoints.size());
        player.sendMessage(a + "goodies " + b + race.tag.goodies.size());
        player.sendMessage(a + "laps " + b + race.tag.laps);
        player.sendMessage(a + "racing " + b + race.tag.countRacers());
        player.sendMessage(a + "maxDuration " + b + race.formatTimeShort(race.tag.maxDuration * 1000L)
                           + " (" + race.tag.maxDuration + ")");
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
        context.message(ChatColor.YELLOW + race.name + ": Spawn set to your WorldEdit selection and current location");
        return true;
    }

    private boolean edit(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        if (race.tag.phase == Phase.EDIT) {
            race.setPhase(Phase.IDLE);
            context.message("" + ChatColor.YELLOW + race.name + ": Edit mode disabled");
        } else {
            race.setPhase(Phase.EDIT);
            context.message("" + ChatColor.YELLOW + race.name + ": Edit mode enabled");
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
            context.message(ChatColor.YELLOW + race.name + ": Laps updated: " + laps);
        }
        race.startRace();
        context.message(ChatColor.YELLOW + "Race started: "
                        + (race.getRacers().stream()
                           .map(racer -> racer.name)
                           .collect(Collectors.joining(", "))));
        return true;
    }

    private boolean checkpointList(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        context.message("" + ChatColor.YELLOW + race.name + ": " + checkpoints.size() + " checkpoint(s)");
        int i = 0;
        for (Cuboid cuboid : checkpoints) {
            context.message("  " + (i++) + " " + ChatColor.YELLOW + cuboid);
        }
        return true;
    }

    private boolean checkpointAdd(CommandContext context, CommandNode node, String[] args) {
        if (args.length > 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        Cuboid cuboid = Cuboid.requireSelectionOf(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        int index = args.length == 0
            ? checkpoints.size()
            : requireInt(args[0]);
        if (index < 0 || index > checkpoints.size()) {
            throw new CommandWarn("Invalid index: " + index);
        }
        checkpoints.add(index, cuboid);
        race.setCheckpoints(checkpoints);
        race.save();
        context.message("" + ChatColor.YELLOW + race.name + ": Checkpoint added: " + cuboid);
        return true;
    }

    private boolean checkpointInfo(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        for (Cuboid cuboid : checkpoints) {
            if (cuboid.contains(player.getLocation())) {
                int index = checkpoints.indexOf(cuboid);
                context.message(ChatColor.YELLOW + "This is checkpoint #" + index + " " + cuboid);
            }
        }
        return true;
    }

    private boolean checkpointSwap(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 2) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        int indexA = requireInt(args[0]);
        int indexB = requireInt(args[1]);
        if (indexA < 0 || indexA >= checkpoints.size()) {
            throw new CommandWarn("Out of bounds: " + indexA);
        }
        if (indexB < 0 || indexB >= checkpoints.size()) {
            throw new CommandWarn("Out of bounds: " + indexB);
        }
        Cuboid a = checkpoints.get(indexA);
        Cuboid b = checkpoints.get(indexB);
        checkpoints.set(indexA, b);
        checkpoints.set(indexB, a);
        race.setCheckpoints(checkpoints);
        race.save();
        context.message("" + ChatColor.YELLOW + race.name + ": Checkpoints swapped: " + indexA + ", " + indexB);
        return true;
    }

    private boolean checkpointRemove(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        int index = requireInt(args[0]);
        if (index < 0 || index >= checkpoints.size()) {
            throw new CommandWarn("Out of bounds: " + index);
        }
        Cuboid old = checkpoints.remove(index);
        race.setCheckpoints(checkpoints);
        race.save();
        context.message("" + ChatColor.YELLOW + race.name + ": Checkpoint #" + index + " removed: " + old);
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
        context.message(ChatColor.YELLOW + race.name + ": Type updated: " + race.tag.type);
        return true;
    }

    private boolean laps(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        race.tag.laps = requireInt(args[0]);
        race.save();
        context.message(ChatColor.YELLOW + race.name + ": Laps updated: " + race.tag.laps);
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
        context.message(ChatColor.YELLOW + "Race stopped: " + race.name);
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
            player.sendMessage(ChatColor.YELLOW + "Start vectors added: " + count);
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
            player.sendMessage(ChatColor.YELLOW + "Start vectors removed: " + count);
            return true;
        }
        case "clear": {
            Race race = requireRace(player);
            int count = race.tag.startVectors.size();
            race.tag.startVectors.clear();
            if (count > 0) race.save();
            player.sendMessage(ChatColor.YELLOW + "Start vectors cleared: " + count);
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
            player.sendMessage(ChatColor.YELLOW + "Goodie added: " + vec);
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
            player.sendMessage(ChatColor.YELLOW + "Goodie removed: " + vec);
            return true;
        }
        case "clear": {
            Race race = requireRace(player);
            int count = race.tag.goodies.size();
            race.tag.goodies.clear();
            if (count > 0) race.save();
            player.sendMessage(ChatColor.YELLOW + "Goodies cleared: " + count);
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
            int dx = 0;
            int dy = 0;
            int dz = 0;
            if (args.length >= 2) {
                dx = requireInt(args[1]);
                dy = requireInt(args[2]);
                dz = requireInt(args[3]);
            }
            Vec3i vec = Cuboid.requireSelectionOf(player).getMin().add(dx, dy, dz);
            if (race.tag.coins.contains(vec)) {
                throw new CommandWarn("Coin already exists: " + vec);
            }
            race.tag.coins.add(vec);
            race.save();
            player.sendMessage(ChatColor.YELLOW + "Coin added: " + vec);
            return true;
        }
        case "remove": {
            if (args.length != 1) return false;
            Race race = requireRace(player);
            Vec3i vec = Cuboid.requireSelectionOf(player).getMin();
            if (!race.tag.coins.contains(vec)) {
                throw new CommandWarn("No coin here: " + vec);
            }
            race.tag.coins.remove(vec);
            race.save();
            player.sendMessage(ChatColor.YELLOW + "Coin removed: " + vec);
            return true;
        }
        case "clear": {
            if (args.length != 1) return false;
            Race race = requireRace(player);
            int count = race.tag.coins.size();
            race.tag.coins.clear();
            if (count > 0) race.save();
            player.sendMessage(ChatColor.YELLOW + "Coins cleared: " + count);
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
        player.sendMessage(ChatColor.GREEN + "Event area reset: " + race.tag.area);
        return true;
    }

    private boolean mount(Player player, String[] args) {
        Race race = requireRace(player);
        if (!race.tag.type.isMounted()) {
            throw new CommandWarn("Not mounted: " + race.tag.type);
        }
        race.tag.type.spawnVehicle(player.getLocation());
        player.sendMessage(ChatColor.YELLOW + "Vehicle spawned");
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
