package com.cavetale.race;

import com.cavetale.core.command.CommandContext;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class RaceCommand implements TabExecutor {
    private final RacePlugin plugin;
    private CommandNode root = new CommandNode("race");

    void enable() {
        root.addChild("list")
            .caller(this::list)
            .description("List all races");
        root.addChild("info")
            .playerCaller(this::info)
            .description("Info about current race course");
        root.addChild("create")
            .caller(this::create)
            .description("Create a new race");
        root.addChild("setspawn")
            .caller(this::setspawn)
            .description("Set race spawn location");
        root.addChild("edit")
            .caller(this::edit)
            .description("Edit mode");
        root.addChild("start")
            .caller(this::start)
            .description("Start a race");
        root.addChild("stop")
            .caller(this::stop)
            .description("Stop a race");
        root.addChild("debug")
            .caller(this::debug)
            .description("Debug command");
        root.addChild("type")
            .caller(this::type)
            .description("Change race type");
        root.addChild("laps")
            .caller(this::laps)
            .description("Change lap number");
        CommandNode checkpointNode = root.addChild("checkpoint")
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
        root.addChild("startvector")
            .completableList(Arrays.asList("clear", "add", "remove"))
            .caller(this::startvector);
        root.addChild("goodies")
            .completableList(Arrays.asList("clear", "add", "remove"))
            .caller(this::goodies);
        root.addChild("event").denyTabCompletion()
            .description("Toggle event mode")
            .playerCaller(this::event);
        root.addChild("eventrace").denyTabCompletion()
            .description("Make current race the event race")
            .playerCaller(this::eventRace);
        root.addChild("setarea").denyTabCompletion()
            .description("Set race area")
            .playerCaller(this::setArea);
        root.addChild("mount").denyTabCompletion()
            .description("Summon a mount for yourself")
            .playerCaller(this::mount);
        root.addChild("maxduration").arguments("<seconds>")
            .playerCaller(this::maxDuration);
        // score
        CommandNode scoreNode = root.addChild("score")
            .description("Grand Prix Scores");
        scoreNode.addChild("reset").denyTabCompletion()
            .description("Reset all scores")
            .senderCaller(this::scoreReset);
        scoreNode.addChild("pedestal").denyTabCompletion()
            .description("Put winners on pedestals")
            .senderCaller(this::scorePedestal);
        plugin.getCommand("race").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return root.call(new CommandContext(sender, command, label, args), args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return root.complete(new CommandContext(sender, command, label, args), args);
    }

    Cuboid requireWorldEditSelection(Player player) {
        Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) {
            throw new CommandWarn("Make a WorldEdit selection first!");
        }
        return cuboid;
    }

    Race requireRace(Player player) {
        Race race = plugin.races.at(player.getLocation());
        if (race == null) {
            throw new CommandWarn("You're not within a race area!");
        }
        return race;
    }

    int requireInt(String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Number expected: " + arg);
        }
    }

    boolean list(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        List<Race> races = plugin.races.all();
        context.message("" + ChatColor.YELLOW + races.size() + " race(s)");
        for (Race race : races) {
            context.message("  " + ChatColor.YELLOW + race.listString());
        }
        return true;
    }

    boolean info(Player player, String[] args) {
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
        return true;
    }

    boolean create(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        Race race = plugin.races.named(name);
        Player player = context.requirePlayer();
        Cuboid cuboid = requireWorldEditSelection(player);
        if (race == null) {
            race = new Race(plugin, name, new Tag());
            plugin.races.add(race);
        }
        race.setWorld(player.getWorld());
        race.setArea(cuboid);
        race.setSpawnLocation(player.getLocation());
        race.save();
        context.message(ChatColor.YELLOW + "Race created: " + race.listString());
        return true;
    }

    boolean setspawn(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        Cuboid cuboid = requireWorldEditSelection(player);
        race.setSpawnLocation(player.getLocation());
        race.setSpawnArea(cuboid);
        race.save();
        context.message(ChatColor.YELLOW + race.name + ": Spawn set to your WorldEdit selection and current location");
        return true;
    }

    boolean edit(CommandContext context, CommandNode node, String[] args) {
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

    boolean start(CommandContext context, CommandNode node, String[] args) {
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

    boolean checkpointList(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        context.message("" + ChatColor.YELLOW + race.name + ": " + checkpoints.size() + " checkpoint(s)");
        int i = 0;
        for (Cuboid cuboid : checkpoints) {
            context.message("  " + (i++) + " " + ChatColor.YELLOW + cuboid.getShortInfo());
        }
        return true;
    }

    boolean checkpointAdd(CommandContext context, CommandNode node, String[] args) {
        if (args.length > 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        Cuboid cuboid = requireWorldEditSelection(player);
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
        context.message("" + ChatColor.YELLOW + race.name + ": Checkpoint added: " + cuboid.getShortInfo());
        return true;
    }

    boolean checkpointInfo(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        List<Cuboid> checkpoints = race.getCheckpoints();
        for (Cuboid cuboid : checkpoints) {
            if (cuboid.contains(player.getLocation())) {
                int index = checkpoints.indexOf(cuboid);
                context.message(ChatColor.YELLOW + "This is checkpoint #" + index + " " + cuboid.getShortInfo());
            }
        }
        return true;
    }

    boolean checkpointSwap(CommandContext context, CommandNode node, String[] args) {
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

    boolean checkpointRemove(CommandContext context, CommandNode node, String[] args) {
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

    boolean type(CommandContext context, CommandNode node, String[] args) {
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

    boolean laps(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        race.tag.laps = requireInt(args[0]);
        race.save();
        context.message(ChatColor.YELLOW + race.name + ": Laps updated: " + race.tag.laps);
        return true;
    }

    boolean debug(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        context.message(Json.serialize(race.tag));
        return true;
    }

    boolean stop(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 0) return false;
        Player player = context.requirePlayer();
        Race race = requireRace(player);
        race.stopRace();
        context.message(ChatColor.YELLOW + "Race stopped: " + race.name);
        race.save();
        return true;
    }

    boolean startvector(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        switch (args[0]) {
        case "add": {
            Race race = requireRace(player);
            Cuboid cuboid = requireWorldEditSelection(player);
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
            Cuboid cuboid = requireWorldEditSelection(player);
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

    boolean goodies(CommandContext context, CommandNode node, String[] args) {
        if (args.length != 1) return false;
        Player player = context.requirePlayer();
        switch (args[0]) {
        case "add": {
            Race race = requireRace(player);
            Vec3i vec = Vec3i.of(player.getLocation());
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
            Vec3i vec = Vec3i.of(player.getLocation());
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

    boolean event(Player player, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 1) {
            try {
                plugin.save.event = Boolean.parseBoolean(args[0]);
            } catch (IllegalArgumentException iae) {
                player.sendMessage(Component.text("Invalid boolean: " + args[0], NamedTextColor.RED));
                return true;
            }
            plugin.save();
        }
        player.sendMessage(Component.text("Event mode is ")
                           .append(plugin.save.event
                                   ? Component.text("Enabled", NamedTextColor.GREEN)
                                   : Component.text("Disabled", NamedTextColor.GREEN)));
        return true;
    }

    boolean eventRace(Player player, String[] args) {
        if (args.length != 0) return false;
        Race race = requireRace(player);
        plugin.save.eventRace = race.name;
        plugin.save();
        player.sendMessage(Component.text("Event race set to " + plugin.save.eventRace,
                                          NamedTextColor.YELLOW));
        return true;
    }

    boolean setArea(Player player, String[] args) {
        Race race = requireRace(player);
        Cuboid cuboid = requireWorldEditSelection(player);
        race.tag.area = cuboid;
        race.save();
        player.sendMessage(ChatColor.GREEN + "Event area reset: " + race.tag.area);
        return true;
    }

    boolean mount(Player player, String[] args) {
        Race race = requireRace(player);
        if (!race.tag.type.isMounted()) {
            throw new CommandWarn("Not mounted: " + race.tag.type);
        }
        race.tag.type.spawnVehicle(player.getLocation());
        player.sendMessage(ChatColor.YELLOW + "Vehicle spawned");
        return true;
    }

    boolean maxDuration(Player player, String[] args) {
        Race race = requireRace(player);
        race.tag.setMaxDuration(requireInt(args[0]));
        race.save();
        player.sendMessage(Component.text("Max duration set to " + race.tag.getMaxDuration() + " seconds",
                                          NamedTextColor.YELLOW));
        return true;
    }

    boolean scoreReset(CommandSender sender, String[] args) {
        plugin.save.scores.clear();
        plugin.save();
        sender.sendMessage(Component.text("All scores were reset!", NamedTextColor.YELLOW));
        return true;
    }

    boolean scorePedestal(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Putting winners on pedestals...", NamedTextColor.YELLOW));
        plugin.scoreRanking();
        return true;
    }
}
