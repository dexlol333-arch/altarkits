package com.altarkits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class KitGui implements Listener {
    private final KitsPlugin plugin;

    public KitGui(KitsPlugin plugin) {
        this.plugin = plugin;
        // refresh open menus every second so the countdown is live
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) fill(p, h);
            }
        }, 20L, 20L);
    }

    public static class Holder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Kit> map = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, KitManager.c("&8&lKITS"));
        fill(p, h);
        p.openInventory(h.inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private ItemStack simple(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(KitManager.c(name));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(KitManager::c).toList());
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Player p, Holder h) {
        Inventory inv = h.inv;
        ItemStack gray = simple(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, gray);
        ItemStack corner = simple(Material.ORANGE_TERRACOTTA, " ");
        for (int s : new int[]{0, 8, 45, 53}) inv.setItem(s, corner);
        inv.setItem(4, simple(Material.CHEST, "&6&lALTAR KITS",
                "&7Pick a loadout and fight!", "", "&aGreen &7= available", "&cRed &7= cooldown / locked"));

        h.map.clear();
        for (Kit k : plugin.kitManager().kits.values()) {
            int s = k.slot();
            if (s < 0 || s > 53) continue;
            inv.setItem(s, icon(p, k));
            h.map.put(s, k);
        }
    }

    private ItemStack icon(Player p, Kit k) {
        ItemStack saved = k.sec.getItemStack("weapon.item"); // real legendary item shows in the GUI
        ItemStack it = saved != null ? saved.clone() : new ItemStack(k.icon());
        it.setAmount(1);

        List<String> lore = new ArrayList<>();
        for (String d : k.description()) lore.add(d);
        lore.add("");
        lore.add(k.cooldown() <= 0 ? "&7Cooldown: &aNone (infinite)" : "&7Cooldown: &e" + KitManager.fmt(k.cooldown()));
        lore.add("");
        int rem = plugin.cooldowns().get(p.getUniqueId(), k.id);
        if (!plugin.kitManager().hasAccess(p, k)) {
            lore.add("&c✘ Locked");
            lore.add(k.storeText());
        } else if (rem > 0 && !p.hasPermission("kit.bypass.cooldown")) {
            lore.add("&c✘ On cooldown: &e" + KitManager.fmt(rem));
        } else {
            lore.add("&a✔ Available");
            lore.add("&eClick to equip!");
        }

        ItemMeta meta = it.getItemMeta();
        meta.displayName(KitManager.c(k.name()));
        meta.lore(lore.stream().map(KitManager::c).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        Kit k = h.map.get(e.getRawSlot());
        if (k == null) return;
        if (plugin.kitManager().claim(p, k)) p.closeInventory(); // applied instantly
        else fill(p, h);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
