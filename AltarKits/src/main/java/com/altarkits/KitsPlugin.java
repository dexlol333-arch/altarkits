package com.altarkits;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class KitsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private KitManager kitManager;
    private Cooldowns cooldowns;
    private KitGui gui;
    private NamespacedKey selectorKey, botKey;

    public KitManager kitManager() { return kitManager; }
    public Cooldowns cooldowns() { return cooldowns; }

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        selectorKey = new NamespacedKey(this, "selector");
        botKey = new NamespacedKey(this, "kitbot");
        kitManager = new KitManager(this);
        kitManager.load();
        cooldowns = new Cooldowns(this);
        gui = new KitGui(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(gui, this);
        for (String c : List.of("kits", "kitadmin", "bot")) {
            getCommand(c).setExecutor(this);
            getCommand(c).setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(this, cooldowns::tick, 20L, 20L);       // 1 tick/sec, online players only
        Bukkit.getScheduler().runTaskTimer(this, cooldowns::save, 1200L, 1200L);   // autosave
    }

    @Override
    public void onDisable() { if (cooldowns != null) cooldowns.save(); }

    // ---------- selector sword ----------
    public boolean isSelector(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }

    private ItemStack selectorItem() {
        ItemStack it = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = it.getItemMeta();
        m.displayName(KitManager.c("&6&lALTAR KITS"));
        m.lore(List.of(KitManager.c("&7Right-click to open the kits menu")));
        m.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public void giveSelector(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isSelector(it)) return;
        ItemStack s = selectorItem();
        ItemStack in8 = p.getInventory().getItem(8);
        if (in8 == null || in8.getType() == Material.AIR) p.getInventory().setItem(8, s);
        else p.getInventory().addItem(s);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { giveSelector(e.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> giveSelector(e.getPlayer()), 2L);
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) { e.getDrops().removeIf(this::isSelector); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { cooldowns.save(); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) {
        if (isSelector(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }
    @EventHandler public void onInvClick(InventoryClickEvent e) {
        if (isSelector(e.getCurrentItem()) && e.getClickedInventory() != null
                && e.getClickedInventory().getType() != InventoryType.PLAYER) e.setCancelled(true);
    }
    @EventHandler public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (isSelector(e.getItem())) { e.setCancelled(true); gui.open(e.getPlayer()); }
    }
    @EventHandler public void onBotClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getRightClicked() instanceof Mannequin m
                && m.getPersistentDataContainer().has(botKey, PersistentDataType.STRING)) {
            e.setCancelled(true);
            gui.open(e.getPlayer());
        }
    }

    // ---------- commands ----------
    private void msg(CommandSender s, String m) { s.sendMessage(KitManager.c(m)); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase()) {
            case "kits" -> {
                if (s instanceof Player p) gui.open(p); else msg(s, "&cPlayers only.");
            }
            case "bot" -> bot(s, a);
            case "kitadmin" -> admin(s, a);
        }
        return true;
    }

    private void bot(CommandSender s, String[] a) {
        if (!s.hasPermission("kit.admin")) { msg(s, "&cNo permission."); return; }
        if (!(s instanceof Player p)) { msg(s, "&cPlayers only."); return; }
        if (a.length >= 1 && a[0].equalsIgnoreCase("remove")) {
            for (Entity en : p.getNearbyEntities(6, 6, 6)) {
                if (en instanceof Mannequin m && m.getPersistentDataContainer().has(botKey, PersistentDataType.STRING)) {
                    m.remove(); msg(p, "&aRemoved the nearest bot."); return;
                }
            }
            msg(p, "&cNo bot within 6 blocks."); return;
        }
        if (a.length < 2 || !a[0].equalsIgnoreCase("spawn")) { msg(p, "&eUsage: /bot spawn <username> | /bot remove"); return; }
        String name = a[1];
        Location loc = p.getLocation();
        loc.setYaw(loc.getYaw() + 180f);
        loc.setPitch(0f);
        msg(p, "&7Looking up &f" + name + "&7...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerProfile prof = Bukkit.createProfile(name);
            boolean ok;
            try { ok = prof.complete(true); } catch (Exception ex) { ok = false; }
            final boolean found = ok;
            Bukkit.getScheduler().runTask(this, () -> {
                if (!found || prof.getId() == null) { msg(p, "&cCould not find a Minecraft account named " + name + "."); return; }
                ResolvableProfile rp = ResolvableProfile.resolvableProfile()
                        .uuid(prof.getId()).name(prof.getName()).addProperties(prof.getProperties()).build();
                loc.getWorld().spawn(loc, Mannequin.class, m -> {
                    m.setProfile(rp);
                    m.setImmovable(true);
                    m.setInvulnerable(true);
                    m.setPersistent(true);
                    m.setSilent(true);
                    m.customName(KitManager.c("&6&l◆ KITS ◆"));
                    m.setCustomNameVisible(true);
                    m.setDescription(Component.text().append(KitManager.c("&fPick a loadout and fight"))
                            .append(Component.newline()).append(KitManager.c("&7▸ Click to choose a kit")).build());
                    m.getPersistentDataContainer().set(botKey, PersistentDataType.STRING, "kits");
                    var eq = m.getEquipment();
                    eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                    eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                    eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                    eq.setBoots(new ItemStack(Material.IRON_BOOTS));
                    eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                });
                msg(p, "&aSpawned kit bot with the skin of &f" + prof.getName() + "&a.");
            });
        });
    }

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("kit.admin")) { msg(s, "&cNo permission (kit.admin)."); return; }
        if (a.length == 0) {
            msg(s, "&6/kitadmin &elist &7| &ereload &7| &esaveinv <kit> &7| &eclearinv <kit> &7| &esetweapon <kit>");
            msg(s, "&6/kitadmin &esetcooldown <kit> <sec> &7| &esetperm <kit> <perm|none> &7| &esetname <kit> <name>");
            msg(s, "&6/kitadmin &eseticon <kit> &7| &esetslot <kit> <0-53> &7| &eresetcd <player> [kit]");
            return;
        }
        String sub = a[0].toLowerCase();
        if (sub.equals("list")) {
            msg(s, "&6Kits: &f" + String.join(", ", kitManager.kits.keySet())); return;
        }
        if (sub.equals("reload")) { kitManager.load(); msg(s, "&aReloaded kits.yml."); return; }
        if (sub.equals("resetcd")) {
            if (a.length < 2) { msg(s, "&eUsage: /kitadmin resetcd <player> [kit]"); return; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
            cooldowns.clear(op.getUniqueId(), a.length > 2 ? a[2].toLowerCase() : null);
            msg(s, "&aCooldown(s) reset for " + a[1] + "."); return;
        }
        if (a.length < 2) { msg(s, "&cSpecify a kit id. &7(/kitadmin list)"); return; }
        Kit k = kitManager.get(a[1]);
        if (k == null) { msg(s, "&cUnknown kit. &7(/kitadmin list)"); return; }
        Player p = s instanceof Player pl ? pl : null;
        switch (sub) {
            case "saveinv" -> {
                if (p == null) { msg(s, "&cPlayers only."); return; }
                int n = kitManager.saveInventory(p, k);
                msg(s, "&aSaved " + n + " items from your inventory/armor as kit &f" + k.id + "&a. Players now get exactly this.");
            }
            case "clearinv" -> { k.sec.set("custom", null); kitManager.save(); msg(s, "&aKit &f" + k.id + " &anow uses the default loadout."); }
            case "setweapon" -> {
                if (p == null) { msg(s, "&cPlayers only."); return; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) { msg(s, "&cHold the weapon item first."); return; }
                ItemStack save = hand.clone(); save.setAmount(1);
                k.sec.set("weapon.item", save); kitManager.save();
                msg(s, "&aWeapon of kit &f" + k.id + " &aset to your held item (replaces the sword).");
            }
            case "setcooldown" -> {
                if (a.length < 3) { msg(s, "&eUsage: /kitadmin setcooldown <kit> <seconds>"); return; }
                try { k.sec.set("cooldown", Math.max(0, Integer.parseInt(a[2]))); kitManager.save(); msg(s, "&aCooldown set."); }
                catch (NumberFormatException ex) { msg(s, "&cNumber needed."); }
            }
            case "setperm" -> {
                if (a.length < 3) { msg(s, "&eUsage: /kitadmin setperm <kit> <permission|none>"); return; }
                k.sec.set("permission", a[2].equalsIgnoreCase("none") ? "" : a[2]); kitManager.save(); msg(s, "&aPermission set.");
            }
            case "setname" -> {
                if (a.length < 3) { msg(s, "&eUsage: /kitadmin setname <kit> <name with & colors>"); return; }
                k.sec.set("name", String.join(" ", Arrays.copyOfRange(a, 2, a.length))); kitManager.save(); msg(s, "&aName set.");
            }
            case "seticon" -> {
                if (p == null) { msg(s, "&cPlayers only."); return; }
                Material m = p.getInventory().getItemInMainHand().getType();
                if (m == Material.AIR) { msg(s, "&cHold an item."); return; }
                k.sec.set("icon", m.name()); kitManager.save(); msg(s, "&aIcon set.");
            }
            case "setslot" -> {
                if (a.length < 3) { msg(s, "&eUsage: /kitadmin setslot <kit> <0-53>"); return; }
                try { k.sec.set("slot", Integer.parseInt(a[2])); kitManager.save(); msg(s, "&aSlot set."); }
                catch (NumberFormatException ex) { msg(s, "&cNumber needed."); }
            }
            default -> msg(s, "&cUnknown subcommand.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("bot")) {
            if (a.length == 1) out.addAll(List.of("spawn", "remove"));
        } else if (cmd.getName().equalsIgnoreCase("kitadmin")) {
            if (a.length == 1) out.addAll(List.of("list", "reload", "saveinv", "clearinv", "setweapon", "setcooldown", "setperm", "setname", "seticon", "setslot", "resetcd"));
            else if (a.length == 2 && !a[0].equalsIgnoreCase("resetcd")) out.addAll(kitManager.kits.keySet());
            else if (a.length == 3 && a[0].equalsIgnoreCase("resetcd")) out.addAll(kitManager.kits.keySet());
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
