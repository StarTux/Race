package com.cavetale.race.command;

public interface CommandCall {
    boolean call(CommandContext context, CommandNode node, String[] args);
}
