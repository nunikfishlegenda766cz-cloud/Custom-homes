package cz.tvojeserver.donuthome;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DonutHome extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Set<UUID> confirmDelete = new HashSet<>();
    private final String GUI_TITLE = ChatColor.DARK_GRAY + "ʜᴏᴍᴇs";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("home").setExecutor(this);
        getCommand("sethome").setExecutor(this);
        getCommand("delhome").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("DonutHome plugin byl uspesne zapnut!");
    }

    private int getMaxHomes(Player player) {
        if (player.hasPermission("donuthome.homes.5") || player.isOp()) return 5;
        if (player.hasPermission("donuthome.homes.3")) return 3;
        return getConfig().getInt("default-homes", 2);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (command.getName().equalsIgnoreCase("home")) {
            if (args.length == 0) {
                openHomesGUI(player);
                return true;
            }
            int slot = parseSlot(args[0]);
            if (slot < 1 || slot > getMaxHomes(player)) {
                player.sendMessage(ChatColor.RED + "Neplatné číslo domova!");
                return true;
            }
            startTeleport(player, slot);
            return true;
        }

        if (command.getName().equalsIgnoreCase("sethome")) {
            int slot = (args.length > 0) ? parseSlot(args[0]) : 1;
            if (slot < 1 || slot > getMaxHomes(player)) {
                player.sendMessage(ChatColor.RED + "Nemáš odemčený slot #" + slot + "!");
                return true;
            }
            setHome(player, slot);
            return true;
        }

        if (command.getName().equalsIgnoreCase("delhome")) {
            int slot = (args.length > 0) ? parseSlot(args[0]) : 1;
            deleteHome(player, slot);
            return true;
        }

        return false;
    }

    private int parseSlot(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void openHomesGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        int maxHomes = getMaxHomes(player);
        int[] displaySlots = {11, 12, 13, 14, 15};

        for (int i = 0; i < displaySlots.length; i++) {
            int homeNum = i + 1;
            int invSlot = displaySlots[i];
            String path = "homes." + player.getUniqueId() + "." + homeNum;

            if (homeNum > maxHomes) {
                inv.setItem(invSlot, createItem(Material.RED_STAINED_GLASS_PANE,
                        ChatColor.RED + "" + ChatColor.BOLD + "ʜᴏᴍᴇ #" + homeNum + " (Uzamčeno)",
                        Arrays.asList(ChatColor.GRAY + "Tento slot vyžaduje vyšší rank.")));
            } else if (getConfig().contains(path + ".world")) {
                String world = getConfig().getString(path + ".world");
                int x = (int) getConfig().getDouble(path + ".x");
                int y = (int) getConfig().getDouble(path + ".y");
                int z = (int) getConfig().getDouble(path + ".z");

                inv.setItem(invSlot, createItem(Material.LIME_BED,
                        ChatColor.GREEN + "" + ChatColor.BOLD + "ʜᴏᴍᴇ #" + homeNum,
                        Arrays.asList(
                                ChatColor.GRAY + "Svět: " + ChatColor.WHITE + world,
                                ChatColor.GRAY + "Souřadnice: " + ChatColor.AQUA + x + ", " + y + ", " + z,
                                "",
                                ChatColor.GREEN + "▸ Levý klik: " + ChatColor.WHITE + "Teleportovat",
                                ChatColor.RED + "▸ Pravý klik: " + ChatColor.WHITE + "Smazat domov"
                        )));
            } else {
                inv.setItem(invSlot, createItem(Material.GRAY_STAINED_GLASS_PANE,
                        ChatColor.GRAY + "" + ChatColor.BOLD + "ʜᴏᴍᴇ #" + homeNum + " (Volné)",
                        Arrays.asList(
                                ChatColor.GRAY + "Zatím nenastaveno.",
                                "",
                                ChatColor.YELLOW + "▸ Klikni pro nastavení na tvé pozici"
                        )));
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int rawSlot = event.getRawSlot();
        int homeNum = rawSlot - 10; // sloty 11..15 -> home 1..5
        if (homeNum < 1 || homeNum > 5) return;

        if (clicked.getType() == Material.RED_STAINED_GLASS_PANE) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            setHome(player, homeNum);
            openHomesGUI(player);
            return;
        }

        if (clicked.getType() == Material.LIME_BED) {
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                deleteHome(player, homeNum);
                openHomesGUI(player);
            } else {
                player.closeInventory();
                startTeleport(player, homeNum);
            }
        }
    }

    private void setHome(Player player, int slot) {
        Location loc = player.getLocation();
        String path = "homes." + player.getUniqueId() + "." + slot;
        getConfig().set(path + ".world", loc.getWorld().getName());
        getConfig().set(path + ".x", loc.getX());
        getConfig().set(path + ".y", loc.getY());
        getConfig().set(path + ".z", loc.getZ());
        getConfig().set(path + ".yaw", (double) loc.getYaw());
        getConfig().set(path + ".pitch", (double) loc.getPitch());
        saveConfig();

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GREEN + "✔ Domov #" + slot + " byl nastaven!"));
    }

    private void deleteHome(Player player, int slot) {
        String path = "homes." + player.getUniqueId() + "." + slot;
        if (!getConfig().contains(path)) {
            player.sendMessage(ChatColor.RED + "Domov #" + slot + " neexistuje.");
            return;
        }
        getConfig().set(path, null);
        saveConfig();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.RED + "✖ Domov #" + slot + " byl smazán!"));
    }

    private void startTeleport(Player player, int slot) {
        String path = "homes." + player.getUniqueId() + "." + slot;
        if (!getConfig().contains(path + ".world")) {
            player.sendMessage(ChatColor.RED + "Domov #" + slot + " není nastaven!");
            return;
        }

        World world = Bukkit.getWorld(getConfig().getString(path + ".world"));
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Svět tohoto domova nebyl nalezen!");
            return;
        }

        Location target = new Location(
                world,
                getConfig().getDouble(path + ".x"),
                getConfig().getDouble(path + ".y"),
                getConfig().getDouble(path + ".z"),
                (float) getConfig().getDouble(path + ".yaw"),
                (float) getConfig().getDouble(path + ".pitch")
        );

        if (pendingTeleports.containsKey(player.getUniqueId())) {
            pendingTeleports.get(player.getUniqueId()).cancel();
        }

        int delay = getConfig().getInt("teleport-delay", 5);

        BukkitTask task = new BukkitRunnable() {
            int secondsLeft = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    pendingTeleports.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    player.teleport(target);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.GREEN + "✔ Teleportován na domov #" + slot + "!"));
                    pendingTeleports.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.YELLOW + "Teleportace za " + ChatColor.AQUA + ChatColor.BOLD + secondsLeft + "s" + ChatColor.YELLOW + " (Nehýbej se!)"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
                secondsLeft--;
            }
        }.runTaskTimer(this, 0L, 20L);

        pendingTeleports.put(player.getUniqueId(), task);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!pendingTeleports.containsKey(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

            pendingTeleports.remove(player.getUniqueId()).cancel();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.RED + "✖ Teleportace zrušena, pohnul jsi se!"));
        }
    }
}
