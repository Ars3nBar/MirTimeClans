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

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeClans extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private File clansFile;
    private YamlConfiguration db;
    private Object econ;

    private final Map<UUID, String> invites = new HashMap<>();
    private final Set<UUID> toggleChat = new HashSet<>();
    private final Map<String, Inventory> openStorages = new HashMap<>();
    private final Map<UUID, String> viewingStorageClan = new HashMap<>();
    private final Map<Integer, String> slotToColor = new HashMap<>();
    private final Map<Integer, String> slotToStyle = new HashMap<>();
    private final Map<Integer, String> slotToEffect = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        clansFile = new File(getDataFolder(), "clans.yml");
        if (!clansFile.exists()) {
            try { clansFile.createNewFile(); } catch (IOException ignored) {}
        }
        db = YamlConfiguration.loadConfiguration(clansFile);
        setupEconomy();
        registerPlaceholder();

        for (String cmd : List.of("clan", "c", "cc")) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                if (cmd.equals("clan")) getCommand(cmd).setTabCompleter(this);
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String clanId = getPlayerClan(p.getName());
                    if (clanId == null) continue;
                    List<String> activeEffects = db.getStringList("clans." + clanId + ".effects");
                    for (String effName : activeEffects) {
                        PotionEffectType type = PotionEffectType.getByName(effName.toUpperCase());
                        if (type != null) {
                            int amp = getConfig().getInt("effects." + effName + ".level", 0);
                            p.addPotionEffect(new PotionEffect(type, 120, amp, true, false));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 80L, 80L);

        Bukkit.getConsoleSender().sendMessage(color("&#0055ff   __  __ _        _____ _                "));
        Bukkit.getConsoleSender().sendMessage(color("&#0055ff  |  \\/  (_)_ __  |_   _(_)_ __ ___   ___ "));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |\\/| | | '__|   | | | | '_ ` _ \\ / _ \\"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |  | | | |      | | | | | | | | |  __/"));
        Bukkit.getConsoleSender().sendMessage(color("&#00FF66  |_|  |_|_|_|      |_| |_|_| |_| |_|\\___|"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  MirTimeClans v2.1.0 успешно запущен!"));
    }
    private void setupEconomy() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = getServer().getServicesManager().getRegistration(econClass);
            if (rsp != null) econ = rsp.getProvider();
        } catch (Exception ignored) {}
    }

    private double getBalance(Player p) {
        if (econ == null) return 0.0;
        try {
            return (double) econ.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(econ, p);
        } catch (Exception e) { return 0.0; }
    }

    private void withdraw(Player p, double amount) {
        if (econ == null) return;
        try {
            econ.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(econ, p, amount);
        } catch (Exception ignored) {}
    }

    private void deposit(Player p, double amount) {
        if (econ == null) return;
        try {
            econ.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(econ, p, amount);
        } catch (Exception ignored) {}
    }

    private String msg(String key) {
        String p = getConfig().getString("settings.prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» ");
        String m = getConfig().getString("messages." + key, "");
        return color(p + m);
    }

    private String getFormattedTag(String clanId) {
        String raw = db.getString("clans." + clanId + ".raw-tag", clanId.toUpperCase());
        String cCode = db.getString("clans." + clanId + ".color", getConfig().getString("settings.default-tag-color", "&#00E5FF"));
        String sCode = db.getString("clans." + clanId + ".style", getConfig().getString("settings.default-tag-style", "&l"));
        return cCode + sCode + raw;
    }

    private void registerPlaceholder() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
            Class<?> expClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            Object expansion = Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{expClass}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getIdentifier" -> { return "mirtimeclans"; }
                    case "getAuthor" -> { return "Ars3nBar"; }
                    case "getVersion" -> { return "2.1.0"; }
                    case "persist" -> { return true; }
                    case "canRegister" -> { return true; }
                    case "onRequest", "onPlaceholderRequest" -> {
                        OfflinePlayer op = (OfflinePlayer) args;
                        String id = ((String) args).toLowerCase();
                        if (op == null) return "";
                        String clanId = getPlayerClan(op.getName());
                        if (clanId == null) return "";
                        if (id.equals("tag")) {
                            String fmt = getConfig().getString("settings.tag-format", "&8[%tag%&8] ");
                            return color(fmt.replace("%tag%", getFormattedTag(clanId)));
                        }
                        if (id.equals("name")) return color(getFormattedTag(clanId));
                        if (id.equals("level")) return String.valueOf(db.getInt("clans." + clanId + ".level", 1));
                        if (id.equals("kills")) return String.valueOf(db.getInt("clans." + clanId + ".kills", 0));
                        return "";
                    }
                }
                return null;
            });
            expClass.getMethod("register").invoke(expansion);
        } catch (Exception ignored) {}
    }

    private void saveDb() {
        try { db.save(clansFile); } catch (IOException ignored) {}
    }

    private String getPlayerClan(String playerName) {
        return db.getString("players." + playerName.toLowerCase());
    }

    private String getRole(String clanId, String playerName) {
        String leader = db.getString("clans." + clanId + ".leader", "");
        if (leader.equalsIgnoreCase(playerName)) return "LEADER";
        for (String off : db.getStringList("clans." + clanId + ".officers")) {
            if (off.equalsIgnoreCase(playerName)) return "OFFICER";
        }
        return "MEMBER";
    }

    private String getRolePrefix(String role) {
        return switch (role) {
            case "LEADER" -> "&c[Лидер]";
            case "OFFICER" -> "&e[Офицер]";
            default -> "&7[Боец]";
        };
    }

    private void sendClanMessage(String clanId, String text) {
        String colored = color(text);
        for (String m : db.getStringList("clans." + clanId + ".members")) {
            Player p = Bukkit.getPlayerExact(m);
            if (p != null) p.sendMessage(colored);
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String prefix = color(getConfig().getString("settings.prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));
        String cName = cmd.getName().toLowerCase();

        if (cName.equals("cc")) {
            String clanId = getPlayerClan(p.getName());
            if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
            if (toggleChat.remove(p.getUniqueId())) {
                p.sendMessage(prefix + color("&7Режим постоянного кланового чата &cвыключен&7."));
            } else {
                toggleChat.add(p.getUniqueId());
                p.sendMessage(prefix + color("&7Режим постоянного кланового чата &#00FF66включён&7! Все сообщения идут в клан."));
            }
            return true;
        }

        if (cName.equals("c")) {
            String clanId = getPlayerClan(p.getName());
            if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
            if (args.length == 0) {
                p.sendMessage(prefix + color("&fИспользование: &#00FF66/c <сообщение> &7или &#00E5FF/cc"));
                return true;
            }
            String text = String.join(" ", args);
            String fmt = getConfig().getString("settings.clan-chat-format", "&#0055ff&l[КЛАН] %rank% &#00E5FF%player% &8» &f%message%");
            sendClanMessage(clanId, fmt.replace("%rank%", getRolePrefix(getRole(clanId, p.getName()))).replace("%player%", p.getName()).replace("%message%", text));
            return true;
        }

        if (args.length == 0) {
            String clanId = getPlayerClan(p.getName());
            if (clanId == null) sendHelp(p);
            else openClanMenu(p, clanId);
            return true;
        }

        String sub = List.of(args).get(0).toLowerCase();
        if (sub.equals("reload") && p.hasPermission("mirtime.clans.admin")) {
            reloadConfig();
            p.sendMessage(msg("reloaded"));
            return true;
        }
        if (sub.equals("top")) {
            sendClanTop(p);
            return true;
        }

        String clanId = getPlayerClan(p.getName());
        switch (sub) {
            case "help" -> sendHelp(p);

            case "create" -> {
                if (clanId != null) { p.sendMessage(msg("already-in-clan")); return true; }
                if (args.length < 2) { p.sendMessage(prefix + color("&fИспользование: &#00FF66/clan create <тег>")); return true; }
                String raw = ChatColor.stripColor(color(List.of(args).get(1)));
                int min = getConfig().getInt("settings.min-tag-length", 2);
                int max = getConfig().getInt("settings.max-tag-length", 8);
                if (raw.length() < min || raw.length() > max) {
                    p.sendMessage(msg("tag-length").replace("%min%", String.valueOf(min)).replace("%max%", String.valueOf(max)));
                    return true;
                }
                String id = raw.toLowerCase().replaceAll("[^a-zа-яё0-9_]", "");
                if (id.isEmpty() || db.contains("clans." + id)) { p.sendMessage(msg("tag-exists")); return true; }
                double cost = getConfig().getDouble("settings.create-cost", 25000.0);
                if (econ != null && getBalance(p) < cost) { p.sendMessage(msg("not-enough-money")); return true; }
                if (econ != null) withdraw(p, cost);

                db.set("clans." + id + ".raw-tag", raw);
                db.set("clans." + id + ".color", getConfig().getString("settings.default-tag-color", "&#00E5FF"));
                db.set("clans." + id + ".style", getConfig().getString("settings.default-tag-style", "&l"));
                db.set("clans." + id + ".leader", p.getName());
                db.set("clans." + id + ".level", 1);
                db.set("clans." + id + ".bank", 0.0);
                db.set("clans." + id + ".kills", 0);
                db.set("clans." + id + ".pvp", false);
                db.set("clans." + id + ".members", List.of(p.getName()));
                db.set("players." + p.getName().toLowerCase(), id);
                saveDb();

                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                Bukkit.broadcastMessage(msg("created").replace("%player%", p.getName()).replace("%tag%", color(getFormattedTag(id))));
            }

            case "color", "style" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (!getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return true; }
                openColorsMenu(p, clanId);
            }

            case "effects" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                openEffectsMenu(p, clanId);
            }

            case "motd" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (!getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return true; }
                if (args.length < 2) {
                    p.sendMessage(prefix + color("&fИспользование: &#00FF66/clan motd <текст_объявления>"));
                    return true;
                }
                String motd = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                db.set("clans." + clanId + ".motd", motd);
                saveDb();
                sendClanMessage(clanId, prefix + "&#00E5FF&l[ОБЪЯВЛЕНИЕ КЛАНА] &f" + motd);
            }

            case "leader" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (!getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return true; }
                if (args.length < 2) return true;
                String targetName = List.of(args).get(1);
                if (!clanId.equals(getPlayerClan(targetName)) || targetName.equalsIgnoreCase(p.getName())) {
                    p.sendMessage(prefix + color("&cУкажите участника вашего клана!"));
                    return true;
                }
                db.set("clans." + clanId + ".leader", targetName);
                List<String> officers = new ArrayList<>(db.getStringList("clans." + clanId + ".officers"));
                officers.removeIf(s -> s.equalsIgnoreCase(targetName));
                officers.add(p.getName());
                db.set("clans." + clanId + ".officers", officers);
                saveDb();
                sendClanMessage(clanId, prefix + "&#00FF66&l[НОВЫЙ ЛИДЕР] &f" + p.getName() + " &eпередал лидерство клана игроку &#00FF66" + targetName + "&e!");
            }
            case "invite" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (getRole(clanId, p.getName()).equals("MEMBER")) { p.sendMessage(msg("only-officer")); return true; }
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayer(List.of(args).get(1));
                if (target == null || getPlayerClan(target.getName()) != null) {
                    p.sendMessage(prefix + color("&cИгрок не в сети или уже состоит в клане!"));
                    return true;
                }
                int lvl = db.getInt("clans." + clanId + ".level", 1);
                int maxMembers = getConfig().getInt("levels." + lvl + ".max-members", 10);
                if (db.getStringList("clans." + clanId + ".members").size() >= maxMembers) {
                    p.sendMessage(msg("clan-full").replace("%max%", String.valueOf(maxMembers)));
                    return true;
                }
                invites.put(target.getUniqueId(), clanId);
                p.sendMessage(prefix + color("&#00FF66Приглашение отправлено игроку &f" + target.getName() + "&#00FF66!"));

                TextComponent tc = new TextComponent(color(prefix + "&fВас пригласили в клан " + getFormattedTag(clanId) + "&f! "));
                TextComponent btn = new TextComponent(color("&#00FF66&l[✔ ВСТУПИТЬ]"));
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept"));
                target.spigot().sendMessage(tc, btn);
            }

            case "accept" -> {
                String invClan = invites.remove(p.getUniqueId());
                if (invClan == null || !db.contains("clans." + invClan)) {
                    p.sendMessage(prefix + color("&cУ вас нет активных приглашений в клан."));
                    return true;
                }
                if (clanId != null) { p.sendMessage(msg("already-in-clan")); return true; }
                List<String> members = new ArrayList<>(db.getStringList("clans." + invClan + ".members"));
                members.add(p.getName());
                db.set("clans." + invClan + ".members", members);
                db.set("players." + p.getName().toLowerCase(), invClan);
                saveDb();
                sendClanMessage(invClan, prefix + "&#00FF66Игрок &f" + p.getName() + " &#00FF66вступил в клан!");
            }

            case "kick" -> {
                if (clanId == null || getRole(clanId, p.getName()).equals("MEMBER") || args.length < 2) return true;
                String targetName = List.of(args).get(1);
                if (!clanId.equals(getPlayerClan(targetName)) || getRole(clanId, targetName).equals("LEADER")) return true;
                removeMember(clanId, targetName);
                sendClanMessage(clanId, prefix + "&cИгрок &f" + targetName + " &cисключён из клана.");
            }

            case "promote", "demote" -> {
                if (clanId == null || !getRole(clanId, p.getName()).equals("LEADER") || args.length < 2) {
                    p.sendMessage(msg("only-owner"));
                    return true;
                }
                String targetName = List.of(args).get(1);
                if (!clanId.equals(getPlayerClan(targetName)) || targetName.equalsIgnoreCase(p.getName())) return true;
                List<String> officers = new ArrayList<>(db.getStringList("clans." + clanId + ".officers"));
                officers.removeIf(s -> s.equalsIgnoreCase(targetName));
                if (sub.equals("promote")) {
                    officers.add(targetName);
                    sendClanMessage(clanId, prefix + "&#00E5FFИгрок &f" + targetName + " &#00E5FFназначен Офицером!");
                } else {
                    sendClanMessage(clanId, prefix + "&eИгрок &f" + targetName + " &eпонижен до Бойца.");
                }
                db.set("clans." + clanId + ".officers", officers);
                saveDb();
            }

            case "leave" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (getRole(clanId, p.getName()).equals("LEADER")) {
                    p.sendMessage(prefix + color("&cЛидер не может просто выйти! Передайте лидерство (&e/clan leader&c) или распустите (&e/clan disband&c)."));
                    return true;
                }
                removeMember(clanId, p.getName());
                p.sendMessage(prefix + color("&7Вы покинули клан."));
                sendClanMessage(clanId, prefix + "&eИгрок &f" + p.getName() + " &eпокинул клан.");
            }

            case "disband" -> {
                if (clanId == null || !getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return true; }
                for (String m : db.getStringList("clans." + clanId + ".members")) db.set("players." + m.toLowerCase(), null);
                db.set("clans." + clanId, null);
                saveDb();
                p.sendMessage(prefix + color("&cВаш клан был распущен."));
            }
            case "sethome" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (getRole(clanId, p.getName()).equals("MEMBER")) { p.sendMessage(msg("only-officer")); return true; }
                Location l = p.getLocation();
                db.set("clans." + clanId + ".home.world", l.getWorld().getName());
                db.set("clans." + clanId + ".home.x", l.getX());
                db.set("clans." + clanId + ".home.y", l.getY());
                db.set("clans." + clanId + ".home.z", l.getZ());
                db.set("clans." + clanId + ".home.yaw", l.getYaw());
                db.set("clans." + clanId + ".home.pitch", l.getPitch());
                saveDb();
                sendClanMessage(clanId, msg("home-set"));
            }

            case "home" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (!db.contains("clans." + clanId + ".home.world")) { p.sendMessage(msg("home-not-set")); return true; }
                World w = Bukkit.getWorld(db.getString("clans." + clanId + ".home.world", ""));
                if (w == null) return true;
                Location dest = new Location(w, db.getDouble("clans." + clanId + ".home.x"), db.getDouble("clans." + clanId + ".home.y"), db.getDouble("clans." + clanId + ".home.z"), (float) db.getDouble("clans." + clanId + ".home.yaw"), (float) db.getDouble("clans." + clanId + ".home.pitch"));
                int delay = getConfig().getInt("settings.home-teleport-delay", 3);
                Location startLoc = p.getLocation().getBlock().getLocation();
                p.sendMessage(prefix + color("&fТелепортация на базу через &#00E5FF" + delay + " сек.&f Не двигайтесь!"));
                new BukkitRunnable() {
                    int left = delay;
                    @Override
                    public void run() {
                        if (!p.isOnline() || !p.getLocation().getBlock().getLocation().equals(startLoc)) {
                            p.sendMessage(prefix + color("&cТелепортация отменена — вы сдвинулись!"));
                            cancel();
                            return;
                        }
                        if (left <= 0) {
                            p.teleport(dest);
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.1f);
                            p.sendMessage(msg("home-tp"));
                            cancel();
                            return;
                        }
                        left--;
                    }
                }.runTaskTimer(this, 0L, 20L);
            }

            case "rally" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                if (getRole(clanId, p.getName()).equals("MEMBER")) { p.sendMessage(msg("only-officer")); return true; }
                Location l = p.getLocation();
                sendClanMessage(clanId, prefix + "&c&l[СБОР КЛАНА] &f" + p.getName() + " &eсозывает всех на координаты: &#00FF66" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " &8(&7Мир: " + l.getWorld().getName() + "&8)");
            }

            case "storage" -> {
                if (clanId == null) { p.sendMessage(msg("no-clan")); return true; }
                openClanStorage(p, clanId);
            }

            case "deposit" -> {
                if (clanId == null || econ == null || args.length < 2) return true;
                double amt;
                try { amt = Double.parseDouble(List.of(args).get(1)); } catch (Exception e) { return true; }
                if (amt <= 0 || getBalance(p) < amt) { p.sendMessage(msg("not-enough-money")); return true; }
                withdraw(p, amt);
                double bank = db.getDouble("clans." + clanId + ".bank", 0.0) + amt;
                db.set("clans." + clanId + ".bank", bank);
                saveDb();
                sendClanMessage(clanId, msg("deposited").replace("%player%", p.getName()).replace("%amount%", String.valueOf((int) amt)));
            }

            case "withdraw" -> {
                if (clanId == null || econ == null || !getRole(clanId, p.getName()).equals("LEADER") || args.length < 2) { p.sendMessage(msg("only-owner")); return true; }
                double amt;
                try { amt = Double.parseDouble(List.of(args).get(1)); } catch (Exception e) { return true; }
                double bank = db.getDouble("clans." + clanId + ".bank", 0.0);
                if (amt <= 0 || bank < amt) { p.sendMessage(msg("not-enough-bank")); return true; }
                db.set("clans." + clanId + ".bank", bank - amt);
                saveDb();
                deposit(p, amt);
                p.sendMessage(prefix + color("&#00FF66Вы сняли из казны клана &f" + (int) amt + " монет&#00FF66."));
            }

            case "upgrade" -> {
                if (clanId == null || !getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return true; }
                upgradeClan(p, clanId);
            }
        }
        return true;
    }
    private void sendClanTop(Player p) {
        ConfigurationSection sec = db.getConfigurationSection("clans");
        p.sendMessage(color("&#0055ff&l&m                                                "));
        p.sendMessage(color(" &#0055ff&l🏆 &#00E5FF&lТОП-10 КЛАНОВ СЕРВЕРА MIR-TIME"));
        if (sec == null || sec.getKeys(false).isEmpty()) {
            p.sendMessage(color(" &7Кланы ещё не созданы."));
        } else {
            List<String> ids = new ArrayList<>(sec.getKeys(false));
            ids.sort((a, b) -> {
                int scoreA = db.getInt("clans." + a + ".level", 1) * 100 + db.getInt("clans." + a + ".kills", 0);
                int scoreB = db.getInt("clans." + b + ".level", 1) * 100 + db.getInt("clans." + b + ".kills", 0);
                return Integer.compare(scoreB, scoreA);
            });
            for (int i = 0; i < Math.min(10, ids.size()); i++) {
                String id = ids.get(i);
                int lvl = db.getInt("clans." + id + ".level", 1);
                int kills = db.getInt("clans." + id + ".kills", 0);
                p.sendMessage(color(" &#00E5FF#" + (i + 1) + " &8[" + getFormattedTag(id) + "&8] &7— &fУр. &#00FF66" + lvl + " &8| &c" + kills + " убийств"));
            }
        }
        p.sendMessage(color("&#0055ff&l&m                                                "));
    }

    private void removeMember(String clanId, String playerName) {
        List<String> members = new ArrayList<>(db.getStringList("clans." + clanId + ".members"));
        members.removeIf(s -> s.equalsIgnoreCase(playerName));
        List<String> officers = new ArrayList<>(db.getStringList("clans." + clanId + ".officers"));
        officers.removeIf(s -> s.equalsIgnoreCase(playerName));
        db.set("clans." + clanId + ".members", members);
        db.set("clans." + clanId + ".officers", officers);
        db.set("players." + playerName.toLowerCase(), null);
        saveDb();
    }

    private void upgradeClan(Player p, String clanId) {
        String prefix = color(getConfig().getString("settings.prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));
        int curLvl = db.getInt("clans." + clanId + ".level", 1);
        int nextLvl = curLvl + 1;
        if (!getConfig().contains("levels." + nextLvl)) {
            p.sendMessage(prefix + color("&#00FF66Ваш клан уже прокачан до максимального уровня!"));
            return;
        }
        double cost = getConfig().getDouble("levels." + nextLvl + ".cost", 50000.0);
        double bank = db.getDouble("clans." + clanId + ".bank", 0.0);
        if (bank < cost) { p.sendMessage(msg("not-enough-bank")); return; }
        db.set("clans." + clanId + ".bank", bank - cost);
        db.set("clans." + clanId + ".level", nextLvl);
        saveDb();
        int newMax = getConfig().getInt("levels." + nextLvl + ".max-members", 15);
        sendClanMessage(clanId, prefix + "&#00FF66&l[УРОВЕНЬ ПОВЫШЕН!] &fКлан достиг &#00E5FF" + nextLvl + " уровня&f! Лимит бойцов: &#00FF66" + newMax);
    }

    private void openClanStorage(Player p, String clanId) {
        String title = getConfig().getString("gui.storage-title", "&#0055ff&l📦 Склад клана &8| %tag%").replace("%tag%", getFormattedTag(clanId));
        Inventory inv = openStorages.computeIfAbsent(clanId, id -> {
            Inventory created = Bukkit.createInventory(null, 27, color(title));
            List<?> saved = db.getList("clans." + id + ".storage");
            if (saved != null) {
                for (int i = 0; i < Math.min(27, saved.size()); i++) {
                    if (saved.get(i) instanceof ItemStack is) created.setItem(i, is);
                }
            }
            return created;
        });
        viewingStorageClan.put(p.getUniqueId(), clanId);
        p.openInventory(inv);
    }

    @EventHandler
    public void onStorageClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        String clanId = viewingStorageClan.remove(p.getUniqueId());
        if (clanId == null) return;
        Inventory inv = openStorages.get(clanId);
        if (inv != null) {
            db.set("clans." + clanId + ".storage", Arrays.asList(inv.getContents()));
            saveDb();
        }
    }

    private void openClanMenu(Player p, String clanId) {
        String tag = getFormattedTag(clanId);
        int lvl = db.getInt("clans." + clanId + ".level", 1);
        double bank = db.getDouble("clans." + clanId + ".bank", 0.0);
        int kills = db.getInt("clans." + clanId + ".kills", 0);
        boolean pvp = db.getBoolean("clans." + clanId + ".pvp", false);
        List<String> members = db.getStringList("clans." + clanId + ".members");
        int maxMembers = getConfig().getInt("levels." + lvl + ".max-members", 10);

        String title = getConfig().getString("gui.main-title", "&#0055ff&l⚡ &#00E5FF&lКлан &8| %tag%").replace("%tag%", tag);
        Inventory inv = Bukkit.createInventory(null, 54, color(title));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        for (int s : new int[]{0,1,7,8,9,17,18,26,27,35,36,44,45,53}) inv.setItem(s, blue);
        for (int s : new int[]{2,3,5,6}) inv.setItem(s, cyan);

        inv.setItem(4, item(Material.SHIELD, "&#00E5FF&lИНФОРМАЦИЯ О КЛАНЕ", List.of(
                " &#0055ff● &fТег: " + tag,
                " &#0055ff● &fЛидер: &#00FF66" + db.getString("clans." + clanId + ".leader"),
                " &#0055ff● &fУровень: &#00E5FF" + lvl + " &8(&fБойцов: " + members.size() + "/" + maxMembers + "&8)",
                " &#0055ff● &fКазна: &#00FF66" + (int) bank + " монет",
                " &#0055ff● &fУбийств клана: &c" + kills
        )));

        inv.setItem(46, item(Material.ENDER_PEARL, "&#00FF66&l[🏠] База клана", List.of("&7Телепортироваться на клановый дом (/clan home).")));
        inv.setItem(47, item(Material.CHEST, "&#00E5FF&l[📦] Общий склад", List.of("&7Открыть хранилище ресурсов на 27 слотов.")));
        inv.setItem(48, item(Material.NAME_TAG, "&#00E5FF&l[🎨] Стиль и цвет тега", List.of("&7Выбрать цвет, &lжирный&7, &oкурсив&7, &nподчёркнутый&7 или &mзачёркнутый&7.")));
        inv.setItem(49, item(Material.BREWING_STAND, "&#00FF66&l[🧪] Эффекты клана", List.of("&7Купить пассивные баффы для всех участников.")));
        inv.setItem(50, item(Material.EXPERIENCE_BOTTLE, "&6&l[⬆] Улучшить клан", List.of("&7Повысить уровень и расширить слоты клана.")));
        inv.setItem(51, item(pvp ? Material.DIAMOND_SWORD : Material.GOLDEN_APPLE,
                pvp ? "&c&l[⚔] Огонь по своим: ВКЛЮЧЁН" : "&#00FF66&l[🛡] Огонь по своим: ВЫКЛЮЧЕН",
                List.of("&7Нажмите, чтобы переключить PvP внутри клана.")));
        inv.setItem(52, item(Material.BELL, "&c&l[📢] Общий сбор", List.of("&7Отправить соклановцам ваши координаты (/clan rally).")));

        int[] grid = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        for (int i = 0; i < Math.min(members.size(), grid.length); i++) {
            String mName = members.get(i);
            String role = getRole(clanId, mName);
            boolean online = Bukkit.getPlayerExact(mName) != null;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(mName));
                meta.setDisplayName(color((online ? "&#00FF66● &f" : "&7● &7") + mName));
                meta.setLore(List.of(
                        color(" &fРанг: " + getRolePrefix(role)),
                        color(" &fСтатус: " + (online ? "&#00FF66В сети" : "&cОффлайн"))
                ));
                skull.setItemMeta(meta);
            }
            inv.setItem(grid[i], skull);
        }
        p.openInventory(inv);
    }
    private void openColorsMenu(Player p, String clanId) {
        String title = getConfig().getString("gui.colors-title", "&#0055ff&l⚡ Клан: &#00E5FF&lСтиль и цвет тега");
        Inventory inv = Bukkit.createInventory(null, 45, color(title));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        for (int s : new int[]{0,1,7,8,9,17,18,26,27,35,36,37,43,44}) inv.setItem(s, blue);
        inv.setItem(4, item(Material.BOOK, "&#00E5FF&lТЕКУЩИЙ ТЕГ: &8[" + getFormattedTag(clanId) + "&8]", List.of("&7Верхний ряд — выбор цвета.", "&7Нижний ряд — выбор шрифта (жирный, курсив и т.д.).")));
        inv.setItem(40, item(Material.BARRIER, "&c&l[◀ Назад в меню клана]", null));

        slotToColor.clear();
        slotToStyle.clear();
        String raw = db.getString("clans." + clanId + ".raw-tag", clanId.toUpperCase());
        String curColor = db.getString("clans." + clanId + ".color", "&#00E5FF");
        String curStyle = db.getString("clans." + clanId + ".style", "&l");

        ConfigurationSection cSec = getConfig().getConfigurationSection("colors");
        if (cSec != null) {
            for (String key : cSec.getKeys(false)) {
                int slot = cSec.getInt(key + ".slot", 10);
                Material mat = Material.matchMaterial(cSec.getString(key + ".material", "LIGHT_BLUE_DYE"));
                if (mat == null) mat = Material.LIGHT_BLUE_DYE;
                String code = cSec.getString(key + ".code", "&#00E5FF");
                double cost = cSec.getDouble(key + ".cost", 0.0);

                inv.setItem(slot, item(mat, cSec.getString(key + ".name", key), List.of(
                        "&7Предпросмотр: &8[" + code + curStyle + raw + "&8]",
                        "&fЦена из казны: &#00FF66" + (int) cost + " монет",
                        "",
                        "&#00FF66➥ Нажмите&f, чтобы применить цвет"
                )));
                slotToColor.put(slot, key);
            }
        }

        ConfigurationSection sSec = getConfig().getConfigurationSection("styles");
        if (sSec != null) {
            for (String key : sSec.getKeys(false)) {
                int slot = sSec.getInt(key + ".slot", 29);
                Material mat = Material.matchMaterial(sSec.getString(key + ".material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                String code = sSec.getString(key + ".code", "");
                double cost = sSec.getDouble(key + ".cost", 0.0);

                inv.setItem(slot, item(mat, sSec.getString(key + ".name", key), List.of(
                        "&7Предпросмотр: &8[" + curColor + code + raw + "&8]",
                        "&fЦена из казны: &#00FF66" + (int) cost + " монет",
                        "",
                        "&#00E5FF➥ Нажмите&f, чтобы применить шрифт"
                )));
                slotToStyle.put(slot, key);
            }
        }
        p.openInventory(inv);
    }

    private void openEffectsMenu(Player p, String clanId) {
        String title = getConfig().getString("gui.effects-title", "&#0055ff&l⚡ Клан: &#00FF66&lЭффекты клана");
        Inventory inv = Bukkit.createInventory(null, 45, color(title));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        for (int s : new int[]{0,1,7,8,9,17,18,26,27,35,36,37,43,44}) inv.setItem(s, blue);
        inv.setItem(40, item(Material.BARRIER, "&c&l[◀ Назад в меню клана]", null));

        slotToEffect.clear();
        List<String> owned = db.getStringList("clans." + clanId + ".effects");
        ConfigurationSection sec = getConfig().getConfigurationSection("effects");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int slot = sec.getInt(key + ".slot", 20);
                Material mat = Material.matchMaterial(sec.getString(key + ".material", "SUGAR"));
                if (mat == null) mat = Material.SUGAR;
                String dName = sec.getString(key + ".display-name", key);
                double cost = sec.getDouble(key + ".cost", 250000.0);
                boolean active = owned.contains(key);

                List<String> lore = new ArrayList<>(sec.getStringList(key + ".lore"));
                lore.add("");
                if (active) {
                    lore.add("&#00FF66&l[✔ АКТИВНО ДЛЯ ВСЕГО КЛАНА]");
                } else {
                    lore.add("&fЦена из казны: &#00FF66" + (int) cost + " монет");
                    lore.add("&#00E5FF➥ Нажмите&f, чтобы купить навсегда");
                }
                inv.setItem(slot, item(mat, (active ? "&#00FF66&l" : "&#00E5FF&l") + dName, lore));
                slotToEffect.put(slot, key);
            }
        }
        p.openInventory(inv);
    }
    @EventHandler
    public void onClanMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        String clanId = getPlayerClan(p.getName());

        if (title.contains("Клан |")) {
            e.setCancelled(true);
            if (clanId == null) return;
            int slot = e.getRawSlot();
            if (slot == 46) { p.closeInventory(); Bukkit.dispatchCommand(p, "clan home"); }
            else if (slot == 47) { p.closeInventory(); openClanStorage(p, clanId); }
            else if (slot == 48) {
                if (!getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return; }
                openColorsMenu(p, clanId);
            }
            else if (slot == 49) { openEffectsMenu(p, clanId); }
            else if (slot == 50) { p.closeInventory(); upgradeClan(p, clanId); }
            else if (slot == 51) {
                if (!getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return; }
                boolean cur = db.getBoolean("clans." + clanId + ".pvp", false);
                db.set("clans." + clanId + ".pvp", !cur);
                saveDb();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openClanMenu(p, clanId);
            }
            else if (slot == 52) { p.closeInventory(); Bukkit.dispatchCommand(p, "clan rally"); }
        } else if (title.contains("Стиль и цвет тега")) {
            e.setCancelled(true);
            if (clanId == null) return;
            if (e.getRawSlot() == 40) { openClanMenu(p, clanId); return; }

            String cKey = slotToColor.get(e.getRawSlot());
            if (cKey != null) {
                double cost = getConfig().getDouble("colors." + cKey + ".cost", 0.0);
                double bank = db.getDouble("clans." + clanId + ".bank", 0.0);
                if (bank < cost) { p.sendMessage(msg("not-enough-bank")); return; }
                db.set("clans." + clanId + ".bank", bank - cost);
                db.set("clans." + clanId + ".color", getConfig().getString("colors." + cKey + ".code", "&#00E5FF"));
                saveDb();
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                sendClanMessage(clanId, msg("color-changed").replace("%tag%", getFormattedTag(clanId)));
                openColorsMenu(p, clanId);
                return;
            }

            String sKey = slotToStyle.get(e.getRawSlot());
            if (sKey != null) {
                double cost = getConfig().getDouble("styles." + sKey + ".cost", 0.0);
                double bank = db.getDouble("clans." + clanId + ".bank", 0.0);
                if (bank < cost) { p.sendMessage(msg("not-enough-bank")); return; }
                db.set("clans." + clanId + ".bank", bank - cost);
                db.set("clans." + clanId + ".style", getConfig().getString("styles." + sKey + ".code", ""));
                saveDb();
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                sendClanMessage(clanId, msg("color-changed").replace("%tag%", getFormattedTag(clanId)));
                openColorsMenu(p, clanId);
            }
        } else if (title.contains("Эффекты клана")) {
            e.setCancelled(true);
            if (clanId == null) return;
            if (e.getRawSlot() == 40) { openClanMenu(p, clanId); return; }
            String eKey = slotToEffect.get(e.getRawSlot());
            if (eKey == null) return;

            if (!getRole(clanId, p.getName()).equals("LEADER")) { p.sendMessage(msg("only-owner")); return; }
            List<String> owned = new ArrayList<>(db.getStringList("clans." + clanId + ".effects"));
            if (owned.contains(eKey)) return;

            double cost = getConfig().getDouble("effects." + eKey + ".cost", 250000.0);
            double bank = db.getDouble("clans." + clanId + ".bank", 0.0);
            if (bank  &7— Создать новый клан"));
        p.sendMessage(color(" &#00FF66/clan color &7— Цвет и шрифт тега (жирный, курсив и др.)"));
        p.sendMessage(color(" &#00FF66/clan effects &7— Пассивные эффекты для всего клана"));
        p.sendMessage(color(" &#00FF66/clan storage &7— Общий склад клана на 27 слотов"));
        p.sendMessage(color(" &#00FF66/clan top &7— Топ-10 сильнейших кланов сервера"));
        p.sendMessage(color(" &#00FF66/clan motd <текст> &7— Задать объявление при входе"));
        p.sendMessage(color(" &#00FF66/clan leader <ник> &7— Передать лидерство клана"));
        p.sendMessage(color(" &#00FF66/clan sethome &8| &f/clan home &7— База клана"));
        p.sendMessage(color(" &#00FF66/clan deposit <сумма> &7— Пополнить казну клана"));
        p.sendMessage(color(" &#00FF66/clan upgrade &7— Прокачать уровень клана"));
        p.sendMessage(color(" &#00E5FF/c <текст> &8| &f/cc &7— Клановый чат"));
        p.sendMessage(color("&#0055ff&l&m                                                "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "color", "effects", "storage", "top", "motd", "leader", "invite", "accept", "kick", "promote", "demote", "leave", "disband", "sethome", "home", "rally", "deposit", "withdraw", "upgrade", "reload");
        }
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote", "leader").contains(List.of(args).get(0).toLowerCase())) {
            return null;
        }
        return List.of();
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> list = new ArrayList<>();
                for (String s : lore) list.add(color(s));
                meta.setLore(list);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
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
