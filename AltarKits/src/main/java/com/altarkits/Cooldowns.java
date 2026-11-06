package com.altarkits;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Cooldowns are stored as REMAINING SECONDS and only tick down while the player
 * is online on THIS server. Each server keeps its own cooldowns.yml.
 */
public class Cooldowns {
    private final File file;
    private final Map<UUID, Map<String, Integer>> data = new HashMap<>();

    public Cooldowns(KitsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "cooldowns.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String u : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(u);
            if (s == null) continue;
            Map<String, Integer> m = new HashMap<>();
            for (String k : s.getKeys(false)) m.put(k, s.getInt(k));
            try { data.put(UUID.fromString(u), m); } catch (IllegalArgumentException ignored) {}
        }
    }

    public int get(UUID id, String kit) {
        Map<String, Integer> m = data.get(id);
        return m == null ? 0 : m.getOrDefault(kit, 0);
    }

    public void set(UUID id, String kit, int seconds) {
        data.computeIfAbsent(id, x -> new HashMap<>()).put(kit, seconds);
    }

    public void clear(UUID id, String kitOrNull) {
        if (kitOrNull == null) data.remove(id);
        else { Map<String, Integer> m = data.get(id); if (m != null) m.remove(kitOrNull); }
    }

    /** Called once per second. Only online players' cooldowns go down. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Integer> m = data.get(p.getUniqueId());
            if (m == null) continue;
            Iterator<Map.Entry<String, Integer>> it = m.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> e = it.next();
                int v = e.getValue() - 1;
                if (v <= 0) it.remove(); else e.setValue(v);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        data.forEach((u, m) -> m.forEach((k, v) -> { if (v > 0) y.set(u + "." + k, v); }));
        try { y.save(file); } catch (IOException ex) { ex.printStackTrace(); }
    }
}
