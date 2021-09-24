package com.cavetale.race.util;

import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.captain.Blunderbuss;
import com.cavetale.worldmarker.util.Tags;
import java.util.Arrays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class Items {
    private Items() { }

    public static ItemStack potionItem(ItemStack itemStack, PotionType potionType) {
        PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
        meta.setBasePotionData(new PotionData(potionType));
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static ItemStack potionItem(Material material, PotionType potionType) {
        return potionItem(new ItemStack(material), potionType);
    }

    public static ItemStack potionItem(Material material, PotionEffectType type, int duration) {
        ItemStack itemStack = new ItemStack(material);
        PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
        PotionEffect effect = new PotionEffect(type, duration, 0, true, false, true);
        meta.addCustomEffect(effect, true);
        meta.setColor(type.getColor());
        meta.displayName(Component.text(type.getName()));
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static ItemStack loadedCrossbow(ItemStack loaded) {
        ItemStack itemStack = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) itemStack.getItemMeta();
        meta.addChargedProjectile(loaded);
        meta.displayName(loaded.getItemMeta().displayName());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static ItemStack label(Material material, Component label) {
        return label(new ItemStack(material), label);
    }

    public static ItemStack label(ItemStack item, Component label) {
        item.editMeta(meta -> {
                meta.displayName(label.decoration(TextDecoration.ITALIC, false));
                meta.addItemFlags(ItemFlag.values());
            });
        return item;
    }

    public static ItemStack lightningRod() {
        ItemStack itemStack = new ItemStack(Material.LIGHTNING_ROD);
        itemStack.editMeta(meta -> {
                meta.displayName(Component.text("Strike Lighning", NamedTextColor.GOLD)
                                 .decoration(TextDecoration.ITALIC, false));
                meta.lore(Arrays.asList(Component.text("Strike everyone ahead of", NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false),
                                        Component.text("you with lightning!", NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false)));
            });
        return itemStack;
    }

    public static ItemStack blunderbuss() {
        ItemStack item = Mytems.BLUNDERBUSS.createItemStack();
        item.editMeta(meta -> {
                Tags.set(meta.getPersistentDataContainer(), Blunderbuss.getSingleUseKey(), 1);
            });
        return item;
    }
}
