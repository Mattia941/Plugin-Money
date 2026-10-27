package net.mythmc;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MythMC extends JavaPlugin implements CommandExecutor {

    // Prefix con &4&l ᴍʏᴛʜᴍᴄ in Small Caps
    private final String PREFIX = "§4§lᴍʏᴛʜᴍᴄ §8» ";

    private final Map<UUID, Map<String, Double>> balances = new HashMap<>();

    private final List<String> CURRENCIES = Arrays.asList(
            "mythsmp", "moneysmp",
            "mythsurvival", "moneysurvival",
            "mythkitpvp", "moneykitpvp"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        getCommand("mythmc").setExecutor(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MythMCExpansion(this).register();
        }

        getLogger().info("MythMC abilitato con successo!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    public double getBalance(Player player, String currency) {
        currency = currency.toLowerCase();
        if (!CURRENCIES.contains(currency)) return 0.0;
        
        Map<String, Double> userBal = balances.getOrDefault(player.getUniqueId(), new HashMap<>());
        return userBal.getOrDefault(currency, 0.0);
    }

    public void setBalance(Player player, String currency, double amount) {
        currency = currency.toLowerCase();
        if (!CURRENCIES.contains(currency)) return;

        Map<String, Double> userBal = balances.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        userBal.put(currency, Math.max(0, amount));
        saveData();
    }

    public void addBalance(Player player, String currency, double amount) {
        setBalance(player, currency, getBalance(player, currency) + amount);
    }

    public boolean removeBalance(Player player, String currency, double amount) {
        double current = getBalance(player, currency);
        if (current < amount) return false;
        setBalance(player, currency, current - amount);
        return true;
    }

    private void loadData() {
        FileConfiguration config = getConfig();
        if (config.contains("players")) {
            for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Double> map = new HashMap<>();
                for (String curr : CURRENCIES) {
                    map.put(curr, config.getDouble("players." + uuidStr + "." + curr, 0.0));
                }
                balances.put(uuid, map);
            }
        }
    }

    public void saveData() {
        FileConfiguration config = getConfig();
        for (Map.Entry<UUID, Map<String, Double>> entry : balances.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, Double> currEntry : entry.getValue().entrySet()) {
                config.set("players." + uuidStr + "." + currEntry.getKey(), currEntry.getValue());
            }
        }
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            if (sender instanceof Player player) {
                player.sendMessage(PREFIX + "§fI tuoi saldi attuali:");
                player.sendMessage("§e&lSMP §8» §fMythSMP: §a" + (int)getBalance(player, "mythsmp") + " §8| §fMoneySMP: §a" + (int)getBalance(player, "moneysmp"));
                player.sendMessage("§a&lSURVIVAL §8» §fMythSurvival: §a" + (int)getBalance(player, "mythsurvival") + " §8| §fMoneySurvival: §a" + (int)getBalance(player, "moneysurvival"));
                player.sendMessage("§c&lKITPVP §8» §fMythKitPvP: §a" + (int)getBalance(player, "mythkitpvp") + " §8| §fMoneyKitPvP: §a" + (int)getBalance(player, "moneykitpvp"));
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("pay")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + "§cSolo i giocatori possono inviare denaro!");
                return true;
            }
            if (args.length < 4) {
                player.sendMessage(PREFIX + "§cUso corretto: /mythmc pay <giocatore> <valuta> <quantità>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(PREFIX + "§cGiocatore non trovato!");
                return true;
            }

            String currency = args[2].toLowerCase();
            if (!CURRENCIES.contains(currency)) {
                player.sendMessage(PREFIX + "§cValuta non valida! Valute: " + String.join(", ", CURRENCIES));
                return true;
            }

            try {
                double amount = Double.parseDouble(args[3]);
                if (amount <= 0) {
                    player.sendMessage(PREFIX + "§cInserisci una cifra positiva!");
                    return true;
                }

                if (removeBalance(player, currency, amount)) {
                    addBalance(target, currency, amount);
                    player.sendMessage(PREFIX + "§aHai inviato §e" + (int)amount + " §adi §e" + currency + " §aa §e" + target.getName());
                    target.sendMessage(PREFIX + "§aHai ricevuto §e" + (int)amount + " §adi §e" + currency + " §ada §e" + player.getName());
                } else {
                    player.sendMessage(PREFIX + "§cNon hai abbastanza saldo!");
                }
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + "§cQuantità non valida!");
            }
            return true;
        }

        if (subCommand.equals("give") && sender.hasPermission("mythmc.admin")) {
            if (args.length < 4) {
                sender.sendMessage(PREFIX + "§cUso: /mythmc give <giocatore> <valuta> <quantità>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            String currency = args[2].toLowerCase();
            if (target == null || !CURRENCIES.contains(currency)) {
                sender.sendMessage(PREFIX + "§cGiocatore o valuta non trovata!");
                return true;
            }
            try {
                double amount = Double.parseDouble(args[3]);
                addBalance(target, currency, amount);
                sender.sendMessage(PREFIX + "§aDati §e" + (int)amount + " §adi §e" + currency + " §aa §e" + target.getName());
                target.sendMessage(PREFIX + "§aTi sono stati aggiunti §e" + (int)amount + " §adi §e" + currency + "!");
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cQuantità non valida!");
            }
            return true;
        }

        if (subCommand.equals("take") && sender.hasPermission("mythmc.admin")) {
            if (args.length < 4) {
                sender.sendMessage(PREFIX + "§cUso: /mythmc take <giocatore> <valuta> <quantità>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            String currency = args[2].toLowerCase();
            if (target == null || !CURRENCIES.contains(currency)) {
                sender.sendMessage(PREFIX + "§cGiocatore o valuta non trovata!");
                return true;
            }
            try {
                double amount = Double.parseDouble(args[3]);
                removeBalance(target, currency, amount);
                sender.sendMessage(PREFIX + "§aRimossi §e" + (int)amount + " §adi §e" + currency + " §aa §e" + target.getName());
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cQuantità non valida!");
            }
            return true;
        }

        if (subCommand.equals("set") && sender.hasPermission("mythmc.admin")) {
            if (args.length < 4) {
                sender.sendMessage(PREFIX + "§cUso: /mythmc set <giocatore> <valuta> <quantità>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            String currency = args[2].toLowerCase();
            if (target == null || !CURRENCIES.contains(currency)) {
                sender.sendMessage(PREFIX + "§cGiocatore o valuta non trovata!");
                return true;
            }
            try {
                double amount = Double.parseDouble(args[3]);
                setBalance(target, currency, amount);
                sender.sendMessage(PREFIX + "§aImpostato il saldo di §e" + target.getName() + " §aper §e" + currency + " §aa §e" + (int)amount);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cQuantità non valida!");
            }
            return true;
        }

        if (subCommand.equals("reset") && sender.hasPermission("mythmc.admin")) {
            if (args.length < 3) {
                sender.sendMessage(PREFIX + "§cUso: /mythmc reset <giocatore> <valuta>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            String currency = args[2].toLowerCase();
            if (target != null && CURRENCIES.contains(currency)) {
                setBalance(target, currency, 0);
                sender.sendMessage(PREFIX + "§aResettato §e" + currency + " §aper §e" + target.getName());
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "§fComandi disponibili:");
        sender.sendMessage("§e/mythmc pay <player> <valuta> <quantità> §7- Invia monete");
        if (sender.hasPermission("mythmc.admin")) {
            sender.sendMessage("§c/mythmc give <player> <valuta> <quantità> §7- Aggiungi monete");
            sender.sendMessage("§c/mythmc take <player> <valuta> <quantità> §7- Rimuovi monete");
            sender.sendMessage("§c/mythmc set <player> <valuta> <quantità> §7- Imposta monete");
            sender.sendMessage("§c/mythmc reset <player> <valuta> §7- Resetta saldo");
        }
    }

    private static class MythMCExpansion extends PlaceholderExpansion {
        private final MythMC plugin;

        public MythMCExpansion(MythMC plugin) { this.plugin = plugin; }

        @Override public @NotNull String getIdentifier() { return "mythmc"; }
        @Override public @NotNull String getAuthor() { return "MythMC Team"; }
        @Override public @NotNull String getVersion() { return "1.0"; }
        @Override public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) return "0";
            return String.valueOf((int) plugin.getBalance(player, identifier));
        }
    }
}
