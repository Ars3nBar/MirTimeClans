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

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<Integer, String> slotToColor = new HashMap<>();
    private final Map<Integer, String> slotToEffect = new HashMap<>();
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.US);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dbFile = new File(getDataFolder(), "clans.yml");
        if (!dbFile.exists()) {
            try { dbFile.createNewFile(); } catch (IOException ignored) {}
        }
        db = YamlConfiguration.loadConfiguration(dbFile);

        if (getCommand("clan") != null) getCommand("clan").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

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

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ClanPlaceholder(this).register();
        }
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
                openMain(p, clanId);
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
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            openMain(p, id);
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

            TextComponent msg = new TextComponent(color(getConfig().getString("settings.prefix") + "&fВас пригласили в клан " + getFormattedTag(clanId) + " "));
            TextComponent btn = new TextComponent(color("&#00FF66&l[✔ ВСТУПИТЬ]"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept"));
            target.spigot().sendMessage(msg, btn);
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
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
    public void openMain(Player p, String clanId) {
        String title = getConfig().getString("gui.main-title", "&#0055ff&l⚡ Клан &8| %tag%")
                .replace("%tag%", getFormattedTag(clanId));
        Inventory inv = Bukkit.createInventory(null, 45, color(title));
        fillFrame(inv);

        String owner = db.getString("clans." + clanId + ".owner", "Неизвестно");
        List<String> members = db.getStringList("clans." + clanId + ".members");
        int maxMembers = getConfig().getInt("settings.max-members", 10);
        long bank = db.getLong("clans." + clanId + ".bank", 0);
        boolean pvp = db.getBoolean("clans." + clanId + ".pvp", false);
        String activeEff = db.getString("clans." + clanId + ".effect", "NONE");

        inv.setItem(13, item(Material.BEACON, "&#0055ff&l[★] &#00E5FF&lИнформация о клане", List.of(
                " &#0055ff● &fТег: " + getFormattedTag(clanId),
                " &#0055ff● &fЛидер: &#00FF66" + owner,
                " &#0055ff● &fУчастников: &#00E5FF" + members.size() + " / " + maxMembers,
                " &#0055ff● &fСостав: &7" + String.join(", ", members),
                " &#0055ff● &fКазна клана: &#00FF66" + fmt(bank) + " монет",
                " &#0055ff● &fАктивный эффект: &#00E5FF" + getEffectDisplayName(activeEff)
        )));

        inv.setItem(20, item(Material.ENDER_PEARL, "&#0055ff&l[⌂] &#00E5FF&lКлановый дом (/clan home)", List.of(
                " &#00FF66➥ Нажмите&f, чтобы телепортироваться",
                " &7Установить точку: &#00E5FF/clan sethome"
        )));

        inv.setItem(21, item(Material.NAME_TAG, "&#0055ff&l[✎] &#00E5FF&lНастройка цвета тега", List.of(
                " &#00FF66➥ Нажмите&f, чтобы выбрать цвет",
                " &7Меняет цвет вашего тега в чате и табе!"
        )));

        inv.setItem(23, item(Material.BREWING_STAND, "&#0055ff&l[✦] &#00FF66&lЭффекты клана", List.of(
                " &#00FF66➥ Нажмите&f, чтобы выбрать бафф",
                " &7Даёт постоянный эффект всем участникам",
                " &7клана в сети за счёт казны!"
        )));

        inv.setItem(24, item(pvp ? Material.DIAMOND_SWORD : Material.SHIELD,
                "&#0055ff&l[⚔] &fОгонь по своим: " + (pvp ? "&c&lВКЛЮЧЕН" : "&#00FF66&lВЫКЛЮЧЕН"),
                List.of(" &#00FF66➥ Нажмите&f, чтобы переключить PvP в клане")));

        inv.setItem(31, item(Material.GOLD_INGOT, "&#0055ff&l[$] &#00FF66&lПополнение казны", List.of(
                " &#0055ff● &fБаланс клана: &#00FF66" + fmt(bank),
                " &7Положить деньги: &#00E5FF/clan deposit <сумма>"
        )));

        p.openInventory(inv);
    }

    public void openColors(Player p, String clanId) {
        String title = getConfig().getString("gui.colors-title", "&#0055ff&l⚡ Выбор цвета тега");
        Inventory inv = Bukkit.createInventory(null, 45, color(title));
        fillFrame(inv);
        slotToColor.clear();

        String rawTag = db.getString("clans." + clanId + ".tag", "CLAN");
        ConfigurationSection sec = getConfig().getConfigurationSection("colors");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int slot = sec.getInt(key + ".slot", -1);
                if (slot < 0 || slot >= 45) continue;
                Material mat = Material.matchMaterial(sec.getString(key + ".material", "PAPER"));
                if (mat == null) mat = Material.PAPER;
                String code = sec.getString(key + ".code", "&f");
                String name = sec.getString(key + ".name", key).replace("%preview%", code + rawTag);
                inv.setItem(slot, item(mat, name, List.of(" &#00FF66➥ Нажмите&f, чтобы установить")));
                slotToColor.put(slot, code);
            }
        }

        inv.setItem(40, item(Material.ARROW, "&c[←] &fНазад в меню клана", null));
        p.openInventory(inv);
    }

    public void openEffects(Player p, String clanId) {
        String title = getConfig().getString("gui.effects-title", "&#0055ff&l⚡ Эффекты клана");
        Inventory inv = Bukkit.createInventory(null, 45, color(title));
        fillFrame(inv);
        slotToEffect.clear();

        long bank = db.getLong("clans." + clanId + ".bank", 0);
        ConfigurationSection sec = getConfig().getConfigurationSection("effects");
        if (sec != null) {
            for (String effKey : sec.getKeys(false)) {
                int slot = sec.getInt(effKey + ".slot", -1);
                if (slot < 0 || slot >= 45) continue;
                Material mat = Material.matchMaterial(sec.getString(effKey + ".material", "POTION"));
                if (mat == null) mat = Material.POTION;
                String dName = sec.getString(effKey + ".display-name", effKey);
                long cost = sec.getLong(effKey + ".cost", 100000L);

                List<String> lore = new ArrayList<>();
                for (String line : sec.getStringList(effKey + ".lore")) lore.add(" " + line);
                lore.add(" &7В казне: &#00FF66" + fmt(bank));
                lore.add(" &#00FF66➥ Нажмите&f, чтобы активировать");

                inv.setItem(slot, item(mat, "&#00E5FF&l" + dName + " &8(&#00FF66" + fmt(cost) + " монет&8)", lore));
                slotToEffect.put(slot, effKey);
            }
        }

        inv.setItem(40, item(Material.ARROW, "&c[←] &fНазад в меню клана", null));
        p.openInventory(inv);
    }
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        String mainPrefix = color(getConfig().getString("gui.main-title", "").split("%tag%")[0]);
        String colTitle = color(getConfig().getString("gui.colors-title", ""));
        String effTitle = color(getConfig().getString("gui.effects-title", ""));

        if (!title.startsWith(mainPrefix) && !title.equals(colTitle) && !title.equals(effTitle)) return;
        e.setCancelled(true);

        String clanId = getPlayerClan(p.getName());
        if (clanId == null) { p.closeInventory(); return; }
        boolean isOwner = db.getString("clans." + clanId + ".owner", "").equalsIgnoreCase(p.getName());
        int slot = e.getRawSlot();

        if (title.equals(colTitle)) {
            if (slot == 40) { openMain(p, clanId); return; }
            if (!isOwner) { sendMsg(p, "only-owner"); return; }
            String newColor = slotToColor.get(slot);
            if (newColor != null) {
                db.set("clans." + clanId + ".color", newColor);
                saveDb();
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                p.sendMessage(getMsg("color-changed").replace("%tag%", getFormattedTag(clanId)));
                openMain(p, clanId);
            }
            return;
        }

        if (title.equals(effTitle)) {
            if (slot == 40) { openMain(p, clanId); return; }
            if (!isOwner) { sendMsg(p, "only-owner"); return; }
            String eff = slotToEffect.get(slot);
            if (eff != null) {
                long cost = getConfig().getLong("effects." + eff + ".cost", 100000L);
                long bank = db.getLong("clans." + clanId + ".bank", 0);
                if (bank < cost) {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    sendMsg(p, "not-enough-bank");
                    return;
                }
                db.set("clans." + clanId + ".bank", bank - cost);
                db.set("clans." + clanId + ".effect", eff);
                saveDb();
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                String msg = getConfig().getString("messages.effect-bought", "")
                        .replace("%effect%", getEffectDisplayName(eff));
                broadcastClan(clanId, msg);
                openMain(p, clanId);
            }
            return;
        }

        switch (slot) {
            case 20 -> { p.closeInventory(); Bukkit.dispatchCommand(p, "clan home"); }
            case 21 -> openColors(p, clanId);
            case 23 -> openEffects(p, clanId);
            case 24 -> {
                if (!isOwner) { sendMsg(p, "only-owner"); return; }
                boolean cur = db.getBoolean("clans." + clanId + ".pvp", false);
                db.set("clans." + clanId + ".pvp", !cur);
                saveDb();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openMain(p, clanId);
            }
        }
    }

    private String getEffectDisplayName(String eff) {
        if (eff == null || eff.equals("NONE")) return "Нет";
        return getConfig().getString("effects." + eff + ".display-name", eff);
    }

    private void fillFrame(Inventory inv) {
        ItemStack b = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack c = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        for (int s : new int[]{0,1,7,8,9,17,18,26,27,35,36,37,43,44}) inv.setItem(s, b);
        for (int s : new int[]{2,3,4,5,6,38,39,40,41,42}) inv.setItem(s, c);
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
class ClanPlaceholder extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

    private final MirTimeClans plugin;

    public ClanPlaceholder(MirTimeClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "mirtimeclans";
    }

    @Override
    public String getAuthor() {
        return "Ars3nBar";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(org.bukkit.OfflinePlayer player, String params) {
        if (player == null) return "";
        String clanId = plugin.getPlayerClan(player.getName());
        if (clanId == null) return "";

        if (params.equalsIgnoreCase("tag")) {
            return plugin.getFormattedTag(clanId) + " ";
        }
        if (params.equalsIgnoreCase("raw_tag")) {
            return plugin.db.getString("clans." + clanId + ".tag", "");
        }
        return "";
    }
}
