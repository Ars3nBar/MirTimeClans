/*
 *    __  __ _        _____ _                
 *   |  \/  (_)_ __  |_   _(_)_ __ ___   ___ 
 *   | |\/| | | '__|   | | | | '_ ` _ \ / _ \
 *   | |  | | | |      | | | | | | | | |  __/
 *   |_|  |_|_|_|      |_| |_|_| |_| |_|\___|
 *                                           
 *   Project: Mir Time | Author: Ars3nBar
 */
package ru.mirtime.clans;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeClans extends JavaPlugin implements CommandExecutor, Listener {

    private File dbFile;
    public YamlConfiguration db;
    private ClanGUI gui;
    private final Map<UUID, String> invites = new HashMap<>();
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.US);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dbFile = new File(getDataFolder(), "clans.yml");
        if (!dbFile.exists()) {
            try { dbFile.createNewFile(); } catch (IOException ignored) {}
        }
        db = YamlConfiguration.loadConfiguration(dbFile);
        gui = new ClanGUI(this);

        if (getCommand("clan") != null) getCommand("clan").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(gui, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String clanId = getPlayerClan(p.getName());
                    if (clanId == null) continue;
                    String effName = db.getString("clans." + clanId + ".effect", "NONE");
                    if (effName.equals("NONE")) continue;
                    PotionEffectType type = PotionEffectType.getByName(effName);
                    if (type != null) {
                        int lvl = getConfig().getInt("effects." + effName + ".level", 0);
                        p.addPotionEffect(new PotionEffect(type, 120, lvl, true, false));
                    }
                }
            }
        }.runTaskTimer(this, 40L, 80L);

        Bukkit.getConsoleSender().sendMessage(color("&#0055ff   __  __ _        _____ _                "));
        Bukkit.getConsoleSender().sendMessage(color("&#0055ff  |  \\/  (_)_ __  |_   _(_)_ __ ___   ___ "));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |\\/| | | '__|   | | | | '_ ` _ \\ / _ \\"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |  | | | |      | | | | | | | | |  __/"));
        Bukkit.getConsoleSender().sendMessage(color("&#00FF66  |_|  |_|_|_|      |_| |_|_| |_| |_|\\___|"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  MirTimeClans v1.0.0 успешно запущен!"));
    }
    public void saveDb() {
        try { db.save(dbFile); } catch (IOException ignored) {}
    }

    public String getMsg(String key) {
        String prefix = getConfig().getString("settings.prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» ");
        String text = getConfig().getString("messages." + key, key);
        return color(prefix + text);
    }

    public void sendMsg(CommandSender p, String key) {
        p.sendMessage(getMsg(key));
    }

    public String getPlayerClan(String playerName) {
        ConfigurationSection sec = db.getConfigurationSection("clans");
        if (sec == null) return null;
        for (String id : sec.getKeys(false)) {
            List<String> members = db.getStringList("clans." + id + ".members");
            for (String m : members) {
                if (m.equalsIgnoreCase(playerName)) return id;
            }
        }
        return null;
    }

    public String getFormattedTag(String clanId) {
        String tag = db.getString("clans." + clanId + ".tag", "CLAN");
        String defCol = getConfig().getString("settings.default-tag-color", "&#00E5FF&l");
        String col = db.getString("clans." + clanId + ".color", defCol);
        return color(col + "[" + tag + "]");
    }

    public void broadcastClan(String clanId, String msg) {
        String cPrefix = getConfig().getString("settings.clan-chat-prefix", "&#0055ff&l[КЛАН] &8» ");
        List<String> members = db.getStringList("clans." + clanId + ".members");
        for (String m : members) {
            Player target = Bukkit.getPlayerExact(m);
            if (target != null) target.sendMessage(color(cPrefix + msg));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mirtime.clans.reload")) return true;
            reloadConfig();
            sendMsg(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player p)) return true;
        String clanId = getPlayerClan(p.getName());

        if (args.length == 0) {
            if (clanId != null) {
                gui.openMain(p, clanId);
            } else {
                p.sendMessage(color("&#0055ff&l&m                                                "));
                p.sendMessage(color(" &#0055ff&l⚡ &#00E5FF&lСИСТЕМА КЛАНОВ &8— &fВы не состоите в клане"));
                p.sendMessage(color(" &#00FF66/clan create <тег> &8— &fСоздать свой клан"));
                p.sendMessage(color(" &#00FF66/clan accept &8— &fПринять приглашение в клан"));
                p.sendMessage(color("&#0055ff&l&m                                                "));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("create")) {
            if (clanId != null) { sendMsg(p, "already-in-clan"); return true; }
            if (args.length < 2) { p.sendMessage(color(getConfig().getString("settings.prefix") + "&fУкажите тег: &#00FF66/clan create <тег>")); return true; }
            String tag = args[1].replaceAll("[^a-zA-Zа-яА-Я0-9]", "");
            int min = getConfig().getInt("settings.min-tag-length", 2);
            int max = getConfig().getInt("settings.max-tag-length", 6);
            if (tag.length() < min || tag.length() > max) {
                p.sendMessage(getMsg("tag-length").replace("%min%", String.valueOf(min)).replace("%max%", String.valueOf(max)));
                return true;
            }
            String id = tag.toLowerCase();
            if (db.contains("clans." + id)) { sendMsg(p, "tag-exists"); return true; }

            db.set("clans." + id + ".tag", tag.toUpperCase());
            db.set("clans." + id + ".color", getConfig().getString("settings.default-tag-color", "&#00E5FF&l"));
            db.set("clans." + id + ".owner", p.getName());
            db.set("clans." + id + ".members", List.of(p.getName()));
            db.set("clans." + id + ".bank", 0L);
            db.set("clans." + id + ".pvp", false);
            saveDb();

            Bukkit.broadcastMessage(getMsg("created").replace("%player%", p.getName()).replace("%tag%", getFormattedTag(id)));
            gui.openMain(p, id);
            return true;
        }
        if (sub.equals("accept")) {
            String invClan = invites.remove(p.getUniqueId());
            if (invClan == null || !db.contains("clans." + invClan)) return true;
            List<String> members = new ArrayList<>(db.getStringList("clans." + invClan + ".members"));
            int maxMembers = getConfig().getInt("settings.max-members", 10);
            if (members.size() >= maxMembers) {
                p.sendMessage(getMsg("clan-full").replace("%max%", String.valueOf(maxMembers)));
                return true;
            }
            members.add(p.getName());
            db.set("clans." + invClan + ".members", members);
            saveDb();
            broadcastClan(invClan, "&#00FF66Игрок &f" + p.getName() + " &#00FF66вступил в клан!");
            return true;
        }

        if (clanId == null) { sendMsg(p, "no-clan"); return true; }
        boolean isOwner = db.getString("clans." + clanId + ".owner", "").equalsIgnoreCase(p.getName());

        if (sub.equals("invite")) {
            if (!isOwner) { sendMsg(p, "only-owner"); return true; }
            if (args.length < 2) return true;
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || getPlayerClan(target.getName()) != null) return true;
            invites.put(target.getUniqueId(), clanId);
            p.sendMessage(color(getConfig().getString("settings.prefix") + "&fПриглашение отправлено игроку &#00FF66" + target.getName()));
            target.sendMessage(color(getConfig().getString("settings.prefix") + "&fВас пригласили в клан " + getFormattedTag(clanId) + "&f! Введите: &#00FF66/clan accept"));
            return true;
        }

        if (sub.equals("sethome")) {
            if (!isOwner) { sendMsg(p, "only-owner"); return true; }
            db.set("clans." + clanId + ".home", p.getLocation());
            saveDb();
            sendMsg(p, "home-set");
            return true;
        }

        if (sub.equals("home")) {
            Location loc = db.getLocation("clans." + clanId + ".home");
            if (loc == null) { sendMsg(p, "home-not-set"); return true; }
            p.teleport(loc);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            sendMsg(p, "home-tp");
            return true;
        }

        if (sub.equals("deposit")) {
            if (args.length < 2) return true;
            try {
                long amount = Long.parseLong(args[1]);
                if (amount <= 0) return true;
                if (getPlayerMoney(p) < amount) { sendMsg(p, "not-enough-money"); return true; }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco take " + p.getName() + " " + amount);
                long bank = db.getLong("clans." + clanId + ".bank", 0);
                db.set("clans." + clanId + ".bank", bank + amount);
                saveDb();
                String msg = getConfig().getString("messages.deposited", "")
                        .replace("%player%", p.getName()).replace("%amount%", fmt(amount));
                broadcastClan(clanId, msg);
            } catch (Exception ignored) {}
            return true;
        }

        if (sub.equals("leave")) {
            if (isOwner) {
                db.set("clans." + clanId, null);
                saveDb();
                p.sendMessage(color(getConfig().getString("settings.prefix") + "&cВы распустили клан."));
            } else {
                List<String> members = new ArrayList<>(db.getStringList("clans." + clanId + ".members"));
                members.removeIf(m -> m.equalsIgnoreCase(p.getName()));
                db.set("clans." + clanId + ".members", members);
                saveDb();
                p.sendMessage(color(getConfig().getString("settings.prefix") + "&cВы покинули клан."));
            }
            return true;
        }

        return true;
    }

    private long getPlayerMoney(Player p) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
                String res = ((String) setPlaceholders.invoke(null, p, "%vault_eco_balance%")).replaceAll("[^0-9.]", "");
                if (!res.isEmpty()) return (long) Double.parseDouble(res);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @EventHandler
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Player attacker) {
            String c1 = getPlayerClan(victim.getName());
            String c2 = getPlayerClan(attacker.getName());
            if (c1 != null && c1.equals(c2) && !db.getBoolean("clans." + c1 + ".pvp", false)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClanChat(AsyncPlayerChatEvent e) {
        if (e.getMessage().startsWith("@")) {
            String clanId = getPlayerClan(e.getPlayer().getName());
            if (clanId != null) {
                e.setCancelled(true);
                broadcastClan(clanId, "&#00E5FF" + e.getPlayer().getName() + "&7: &f" + e.getMessage().substring(1).trim());
            }
        }
    }

    public static String fmt(long num) {
        return FORMAT.format(num);
    }

    public static String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
