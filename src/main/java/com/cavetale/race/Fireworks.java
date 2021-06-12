package com.cavetale.race;

import java.util.Random;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.Bukkit;

final class Fireworks {
    static Random random = new Random();

    private Fireworks() { }

    public static Color randomColor() {
        return Color.fromBGR(random.nextInt(256),
                             random.nextInt(256),
                             random.nextInt(256));
    }

    public static FireworkEffect.Type randomFireworkEffectType() {
        switch (random.nextInt(10)) {
        case 0: return FireworkEffect.Type.CREEPER;
        case 1: return FireworkEffect.Type.STAR;
        case 2: case 3: return FireworkEffect.Type.BALL_LARGE;
        case 4: case 5: case 6: return FireworkEffect.Type.BURST;
        case 7: case 8: case 9: return FireworkEffect.Type.BALL;
        default: return FireworkEffect.Type.BALL;
        }
    }

    public static FireworkMeta randomFireworkMeta(FireworkEffect.Type type, int amount) {
        FireworkMeta meta = (FireworkMeta) Bukkit.getServer().getItemFactory()
            .getItemMeta(Material.FIREWORK_ROCKET);
        FireworkEffect.Builder builder = FireworkEffect.builder().with(type);
        for (int i = 0; i < amount; i += 1) {
            builder.withColor(randomColor());
            meta.addEffect(builder.build());
        }
        meta.setPower(0);
        return meta;
    }

    public static FireworkMeta simpleFireworkMeta() {
        return randomFireworkMeta(FireworkEffect.Type.BURST, 1);
    }
}
