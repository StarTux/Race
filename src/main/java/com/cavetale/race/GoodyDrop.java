package com.cavetale.race;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.bukkit.inventory.ItemStack;

@Value @AllArgsConstructor
public final class GoodyDrop {
    public final double weight;
    public final ItemStack item;
    public final boolean rare;

    public GoodyDrop(final double weight, final ItemStack item) {
        this(weight, item, false);
    }
}
