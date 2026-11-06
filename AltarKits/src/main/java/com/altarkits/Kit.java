package com.altarkits;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** A kit. Reads straight from kits.yml so admin edits apply instantly. */
public class Kit {
    public final String id;
    public final ConfigurationSection sec;

    public Kit(String id, ConfigurationSection sec) {
        this.id = id;
        this.sec = sec;
    }

    public String name() { return sec.getString("name", id); }
    public int slot() { return sec.getInt("slot", -1); }
    public int cooldown() { return sec.getInt("cooldown", 0); }
    public String permission() { return sec.getString("permission", ""); }
    public String storeText() { return sec.getString("store-text", "&7Buy this kit on our store!"); }
    public List<String> description() { return sec.getStringList("description"); }

    public Material icon() {
        Material m = Material.matchMaterial(sec.getString("icon", "DIAMOND_SWORD"));
        return m == null ? Material.IRON_INGOT : m;
    }
}
