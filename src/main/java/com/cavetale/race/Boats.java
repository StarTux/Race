package com.cavetale.race;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.Tag;

public final class Boats {
    static final List<Material> MATS = Stream.of(Material.values())
        .filter(m -> Tag.ITEMS_BOATS.isTagged(m))
        .collect(Collectors.toList());

    private Boats() { }

    public static Material randomBoat() {
        Random random = ThreadLocalRandom.current();
        return MATS.get(random.nextInt(MATS.size()));
    }

    public static boolean isBoat(Material mat) {
        return MATS.contains(mat);
    }
}
