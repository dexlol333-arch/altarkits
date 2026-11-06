package com.altarkits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KitManager {
    private final KitsPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;
    public final Map<String, Kit> kits = new LinkedHashMap<>();
    private int nextSlot = 0;
    // GUI slots used by kits (7 per row, 4 rows inside the border)
    private static final int[] SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};

    public KitManager(KitsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
    }

    // ---------- text helpers ----------
    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static String fmt(int s) {
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m " + String.format("%02d", sec) + "s";
        if (m > 0) return m + "m " + String.format("%02d", sec) + "s";
        return sec + "s";
    }

    // ---------- load / save ----------
    public void load() {
        if (!file.exists()) generateDefaults();
        cfg = YamlConfiguration.loadConfiguration(file);
        kits.clear();
        ConfigurationSection s = cfg.getConfigurationSection("kits");
        if (s == null) return;
        for (String id : s.getKeys(false)) kits.put(id, new Kit(id, s.getConfigurationSection(id)));
    }

    public void save() {
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public Kit get(String id) { return id == null ? null : kits.get(id.toLowerCase()); }

    // ---------- default kits ----------
    private ConfigurationSection add(YamlConfiguration y, String id, String name, String icon, String perm, int cd, String... desc) {
        ConfigurationSection s = y.createSection("kits." + id);
        s.set("name", name);
        s.set("icon", icon);
        s.set("slot", SLOTS[nextSlot++]);
        s.set("permission", perm);
        s.set("cooldown", cd);
        s.set("description", Arrays.asList(desc));
        return s;
    }

    private void generateDefaults() {
        YamlConfiguration y = new YamlConfiguration();

        // FREE infinite kit (no permission, no cooldown, ever)
        add(y, "free", "&a&lFREE KIT", "DIAMOND_SWORD", "", 0,
                "&7Maxed diamond kit.", "&aFree forever &7- no cooldown!");

        // RANK KITS - placeholders, edit with /kitadmin saveinv <kit> once you send me the pictures
        String[][] ranks = {
                {"vip", "&a&lVIP KIT", "kit.rank.vip", "&aVIP"},
                {"vipplus", "&b&lVIP+ KIT", "kit.rank.vipplus", "&bVIP+"},
                {"platinum", "&f&lPLATINUM KIT", "kit.rank.platinum", "&fPlatinum"},
                {"elite", "&6&lELITE KIT", "kit.rank.elite", "&6Elite"}};
        for (String[] r : ranks) {
            ConfigurationSection s = add(y, r[0], r[1], "NETHERITE_SWORD", r[2], 1800,
                    "&7Exclusive " + r[3] + " &7rank kit.");
            s.set("armor", "NETHERITE");
            s.set("store-text", "&7Requires the " + r[3] + " &7rank. &eBuy it on our store!");
        }

        // ALTAR KITS: {id, display name, fallback weapon material}
        String[][] altars = {
                {"boneblade", "Boneblade", "NETHERITE_SWORD"}, {"bloodlust", "Bloodlust", "NETHERITE_SWORD"},
                {"vulcanscrossbow", "Vulcan's Crossbow", "CROSSBOW"}, {"nightpiercer", "Nightpiercer", "NETHERITE_SWORD"},
                {"hyperion", "Hyperion", "IRON_SWORD"}, {"pureblade", "Pureblade", "DIAMOND_SWORD"},
                {"earthgauntlet", "Earth Gauntlet", "NETHERITE_AXE"}, {"cutlass", "Cutlass", "IRON_SWORD"},
                {"shadowblade", "Shadowblade", "NETHERITE_SWORD"}, {"paladinbattleaxe", "Paladin Battleaxe", "NETHERITE_AXE"},
                {"windweaver", "Windweaver", "NETHERITE_SWORD"}, {"witherbone", "Witherbone", "NETHERITE_SWORD"},
                {"wandofillusion", "Wand of Illusion", "BLAZE_ROD"}, {"palecrossbow", "Pale Crossbow", "CROSSBOW"}};
        for (String[] a : altars) {
            ConfigurationSection s = add(y, a[0], "&6&l" + a[1].toUpperCase() + " KIT", a[2], "", 25 * 60,
                    "&7Comes with the &6" + a[1] + "&7 instead of a sword.",
                    "&7Prot 3, Strength 2, everything else maxed.");
            s.set("weapon.material", a[2]);
            s.set("weapon.name", "&6&l" + a[1]);
            // Tip: hold the REAL legendary item and run /kitadmin setweapon <kit>
        }

        // COPPER KITS: diamond kit with exactly ONE copper armor piece
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        for (String p : pieces) {
            String nice = p.charAt(0) + p.substring(1).toLowerCase();
            ConfigurationSection s = add(y, "copper_" + p.toLowerCase(), "&c&lCOPPER " + p + " KIT", "COPPER_" + p, "", 35 * 60,
                    "&7Maxed diamond kit with ONE &6Copper " + nice + "&7.",
                    "&eThis kit has a 35 minute cooldown.");
            s.set("armor-override.slot", p);
            s.set("armor-override.material", "COPPER_" + p);
        }
        try { plugin.getDataFolder().mkdirs(); y.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------- access / claim ----------
    public boolean hasAccess(Player p, Kit k) {
        return k.permission().isEmpty() || p.hasPermission(k.permission());
    }

    public boolean claim(Player p, Kit k) {
        if (!hasAccess(p, k)) {
            p.sendMessage(c("&c✘ You don't own the " + k.name() + "&c. " + k.storeText()));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        int rem = plugin.cooldowns().get(p.getUniqueId(), k.id);
        if (rem > 0 && !p.hasPermission("kit.bypass.cooldown")) {
            p.sendMessage(c("&c✘ " + k.name() + " &cis on cooldown for &e" + fmt(rem) + "&c."));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        give(p, k);
        if (k.cooldown() > 0) plugin.cooldowns().set(p.getUniqueId(), k.id, k.cooldown());
        p.sendMessage(c("&a✔ You equipped the " + k.name() + "&a!"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        return true;
    }

    // ---------- building the kit ----------
    private static Enchantment ench(String n) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(n.toLowerCase()));
    }

    private static ItemStack enchanted(Material m, String... list) {
        ItemStack it = new ItemStack(m);
        for (String e : list) {
            String[] sp = e.split(":");
            Enchantment en = ench(sp[0]);
            if (en != null) it.addUnsafeEnchantment(en, Integer.parseInt(sp[1]));
        }
        return it;
    }

    private static ItemStack pot(Material m, PotionType t, int amt) {
        ItemStack i = new ItemStack(m, amt);
        PotionMeta pm = (PotionMeta) i.getItemMeta();
        pm.setBasePotionType(t);
        i.setItemMeta(pm);
        return i;
    }

    private static ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack weaponFor(Kit k) {
        ItemStack saved = k.sec.getItemStack("weapon.item");
        if (saved != null) return saved.clone();
        String matName = k.sec.getString("weapon.material", "DIAMOND_SWORD");
        Material m = Material.matchMaterial(matName);
        if (m == null) m = Material.DIAMOND_SWORD;
        ItemStack it;
        if (m == Material.CROSSBOW) {
            it = enchanted(m, "QUICK_CHARGE:3", "PIERCING:4", "UNBREAKING:3", "MENDING:1");
        } else if (m.name().endsWith("_AXE")) {
            it = enchanted(m, "SHARPNESS:5", "UNBREAKING:3", "MENDING:1", "EFFICIENCY:5");
        } else {
            it = enchanted(m, "SHARPNESS:5", "UNBREAKING:3", "FIRE_ASPECT:2", "LOOTING:3", "SWEEPING_EDGE:3", "MENDING:1");
        }
        String nm = k.sec.getString("weapon.name");
        return nm == null ? it : named(it, nm);
    }

    private ItemStack armorPiece(String base, String piece) {
        Material m = Material.matchMaterial(base + "_" + piece);
        if (m == null) m = Material.matchMaterial("DIAMOND_" + piece);
        List<String> e = new ArrayList<>(List.of("PROTECTION:3", "UNBREAKING:3", "MENDING:1"));
        if (piece.equals("BOOTS")) e.add("FEATHER_FALLING:4");
        if (piece.equals("HELMET")) { e.add("RESPIRATION:3"); e.add("AQUA_AFFINITY:1"); }
        return enchanted(m, e.toArray(new String[0]));
    }

    public void give(Player p, Kit k) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);

        ConfigurationSection custom = k.sec.getConfigurationSection("custom");
        if (custom != null && !custom.getKeys(false).isEmpty()) {
            // Admin-saved inventory (/kitadmin saveinv)
            for (String key : custom.getKeys(false)) {
                ItemStack it = custom.getItemStack(key);
                if (it != null) inv.setItem(Integer.parseInt(key), it.clone());
            }
            plugin.giveSelector(p);
            return;
        }

        String base = k.sec.getString("armor", "DIAMOND");
        String oSlot = k.sec.getString("armor-override.slot", "");
        String oMat = k.sec.getString("armor-override.material", "");
        String[] pieces = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        ItemStack[] armor = new ItemStack[4]; // boots, legs, chest, helmet
        for (int i = 0; i < 4; i++) {
            String piece = pieces[i];
            ItemStack it;
            if (piece.equals(oSlot) && Material.matchMaterial(oMat) != null) {
                it = armorPiece(oMat.replace("_" + piece, ""), piece); // ONE copper piece
            } else {
                it = armorPiece(base, piece);
            }
            armor[3 - i] = it;
        }
        inv.setArmorContents(armor);

        inv.setItem(0, weaponFor(k));
        inv.setItem(1, enchanted(Material.BOW, "POWER:5", "PUNCH:2", "FLAME:1", "INFINITY:1", "UNBREAKING:3"));
        inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 64));
        inv.setItem(3, new ItemStack(Material.WATER_BUCKET));
        plugin.giveSelector(p); // slot 8

        List<ItemStack> rest = new ArrayList<>();
        rest.add(new ItemStack(Material.GOLDEN_APPLE, 64));
        for (int i = 0; i < 3; i++) rest.add(new ItemStack(Material.WATER_BUCKET)); // 4 water total
        rest.add(new ItemStack(Material.ARROW, 64));
        rest.add(new ItemStack(Material.COBWEB, 64));
        rest.add(new ItemStack(Material.OAK_LOG, 64));
        rest.add(new ItemStack(Material.COOKED_BEEF, 64));
        rest.add(pot(Material.POTION, PotionType.STRONG_STRENGTH, 1));
        rest.add(pot(Material.POTION, PotionType.STRONG_STRENGTH, 1));
        rest.add(pot(Material.POTION, PotionType.SWIFTNESS, 1));
        rest.add(pot(Material.POTION, PotionType.SWIFTNESS, 1));
        rest.add(pot(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 1));
        for (int i = 0; i < 14; i++) rest.add(pot(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 1));

        for (ItemStack left : inv.addItem(rest.toArray(new ItemStack[0])).values())
            p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    /** Admin: save the admin's current inventory as the kit's contents. */
    public int saveInventory(Player admin, Kit k) {
        k.sec.set("custom", null);
        int n = 0;
        PlayerInventory inv = admin.getInventory();
        for (int i = 0; i <= 40; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR || plugin.isSelector(it)) continue;
            k.sec.set("custom." + i, it);
            n++;
        }
        save();
        return n;
    }
}
