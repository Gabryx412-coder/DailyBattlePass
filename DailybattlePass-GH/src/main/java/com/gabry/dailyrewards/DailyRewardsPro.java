package com.gabry.dailyrewards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Classe principale del plugin DailyRewardsPro.
 * Gestisce tutte le funzionalità del plugin, inclusi ricompense giornaliere, BattlePass,
 * GUI personalizzate, gestione dei file di configurazione e dati dei giocatori.
 * Tutte le logiche sono contenute in questa singola classe, come richiesto.
 */
public class DailyRewardsPro extends JavaPlugin implements Listener {

    private FileConfiguration rewardsConfig;

    // File di configurazione per i dati dei giocatori (data ultimo riscatto, XP BattlePass)
    private File dataFile;
    private FileConfiguration dataConfig;

    // Mappa per memorizzare i dati dell'ultimo riscatto di ciascun giocatore per un accesso rapido.
    // UUID del giocatore -> Data dell'ultimo riscatto (LocalDate)
    private final Map<UUID, LocalDate> lastClaimDates = new HashMap<>();

    // Mappa per memorizzare l'XP del BattlePass di ciascun giocatore.
    // UUID del giocatore -> Punti esperienza
    private final Map<UUID, Integer> playerBattlePassXp = new HashMap<>();

    // Mappa che associa gli UUID dei giocatori agli inventari GUI aperti, per gestire le interazioni.
    private final Map<UUID, Inventory> openClaimGUIs = new HashMap<>();
    private final Map<UUID, Inventory> openBattlePassGUIs = new HashMap<>();

    // Mappa per memorizzare le ricompense caricate dal file rewards.yml.
    // Nome ricompensa (chiave) -> Dettagli ricompensa (oggetto RewardItem)
    private final Map<String, RewardItem> availableRewards = new HashMap<>();

    // Livelli del BattlePass con le soglie XP e le ricompense associate.
    // Numero livello -> Soglia XP
    private final Map<Integer, Integer> battlePassLevelThresholds = new TreeMap<>();
    // Numero livello -> Lista di ItemStack per le ricompense di quel livello
    private final Map<Integer, List<ItemStack>> battlePassLevelRewards = new HashMap<>();
    // Numero livello -> Lista di comandi da eseguire al raggiungimento del livello
    private final Map<Integer, List<String>> battlePassLevelCommands = new HashMap<>();

    // Configurazione dei messaggi del plugin.
    private final String prefix = ChatColor.GOLD + "[DRP] " + ChatColor.RESET;
    private String noPermissionMessage = ChatColor.RED + "Non hai il permesso di fare questo!";
    private String claimedTodayMessage = ChatColor.YELLOW + "Hai già riscattato la tua ricompensa giornaliera. Riprova domani!";
    private String rewardReceivedMessage = ChatColor.GREEN + "Hai ricevuto una ricompensa giornaliera!";
    private String claimGUITitle = ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "Ricompense Giornaliere";
    private String battlePassGUITitle = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "BattlePass Progressi";
    private String battlePassLevelUpMessage = ChatColor.LIGHT_PURPLE + "Congratulazioni! Hai raggiunto il livello %level% del BattlePass!";
    private String battlePassXpGainMessage = ChatColor.AQUA + "+%xp% XP BattlePass!";
    private String battlePassMaxLevelMessage = ChatColor.GOLD + "Hai raggiunto il livello massimo del BattlePass!";
    private String battlePassClaimedRewardMessage = ChatColor.GRAY + "Ricompensa riscattata.";
    private String battlePassUnclaimedRewardMessage = ChatColor.GREEN + "Clicca per riscattare!";
    private String battlePassLockedRewardMessage = ChatColor.RED + "Livello non raggiunto.";
    private String battlePassXPDisplayFormat = ChatColor.BLUE + "XP: %current_xp% / %next_level_xp%";
    private String battlePassLevelDisplayFormat = ChatColor.DARK_GREEN + "Livello: %level%";

    // --- Sezione Ciclo di Vita del Plugin ---

    @Override
    public void onEnable() {
        // Registra questa classe come Listener per gli eventi di Bukkit.
        getServer().getPluginManager().registerEvents(this, this);

        // Registra i comandi del plugin.
        Objects.requireNonNull(getCommand("claim")).setExecutor(this);
        Objects.requireNonNull(getCommand("battlepass")).setExecutor(this);

        // Inizializza i file di configurazione.
        setupConfigFiles();

        // Carica le ricompense e i dati dei giocatori all'avvio.
        loadRewardsFromFile();
        loadPlayerDataFromFile();

        // Carica la configurazione del BattlePass.
        loadBattlePassConfig();

        // Log di successo all'abilitazione del plugin.
        getLogger().log(Level.INFO, prefix + "DailyRewardsPro abilitato con successo!");
        getLogger().log(Level.INFO, prefix + "Sviluppato da Gabry.");
    }

    @Override
    public void onDisable() {
        // Salva i dati dei giocatori prima che il plugin venga disabilitato.
        savePlayerDataToFile();

        // Log di disabilitazione del plugin.
        getLogger().log(Level.INFO, prefix + "DailyRewardsPro disabilitato con successo!");
    }

    // --- Sezione Gestione File ---

    /**
     * Prepara i file di configurazione del plugin.
     * Crea i file predefiniti se non esistono e li carica.
     */
    private void setupConfigFiles() {
        // Crea la cartella dei dati del plugin se non esiste.
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Inizializza il file rewards.yml
        // --- Sezione Variabili Globali ---
        // File di configurazione per le ricompense
        File rewardsFile = new File(getDataFolder(), "rewards.yml");
        if (!rewardsFile.exists()) {
            // Copia il file predefinito dalla risorsa JAR se non esiste.
            saveResource("rewards.yml", false);
            getLogger().log(Level.INFO, "Copiato rewards.yml predefinito.");
        }
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
        getLogger().log(Level.INFO, "Caricato rewards.yml.");

        // Inizializza il file data.yml
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            // Copia il file predefinito dalla risorsa JAR se non esiste.
            saveResource("data.yml", false);
            getLogger().log(Level.INFO, "Copiato data.yml predefinito.");
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        getLogger().log(Level.INFO, "Caricato data.yml.");
    }

    /**
     * Carica le ricompense dal file `rewards.yml`.
     * Le ricompense vengono memorizzate nella mappa `availableRewards`.
     */
    private void loadRewardsFromFile() {
        availableRewards.clear(); // Pulisce la mappa prima di ricaricare

        // Ottiene la sezione 'rewards' dal file di configurazione.
        if (!rewardsConfig.isConfigurationSection("rewards")) {
            getLogger().log(Level.WARNING, "La sezione 'rewards' non è presente in rewards.yml. Nessuna ricompensa caricata.");
            return;
        }

        // Itera su ogni chiave (nome della ricompensa) nella sezione 'rewards'.
        for (String rewardKey : Objects.requireNonNull(rewardsConfig.getConfigurationSection("rewards")).getKeys(false)) {
            try {
                // Percorso completo per i dettagli della ricompensa.
                String path = "rewards." + rewardKey;

                // Estrae i dettagli della ricompensa dal file.
                String displayName = ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(rewardsConfig.getString(path + ".display_name", "&cErrore: Nome non trovato")));
                Material material = Material.matchMaterial(Objects.requireNonNull(rewardsConfig.getString(path + ".material", "STONE")));
                int amount = rewardsConfig.getInt(path + ".amount", 1);
                List<String> lore = rewardsConfig.getStringList(path + ".lore").stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                List<String> commands = rewardsConfig.getStringList(path + ".commands");
                int xpReward = rewardsConfig.getInt(path + ".xp_reward", 0);

                // Crea un nuovo oggetto RewardItem e lo aggiunge alla mappa.
                availableRewards.put(rewardKey, new RewardItem(displayName, material, amount, lore, commands, xpReward));
                getLogger().log(Level.INFO, "Caricata ricompensa: " + rewardKey);

            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Errore durante il caricamento della ricompensa '" + rewardKey + "' da rewards.yml: " + e.getMessage());
            }
        }
        getLogger().log(Level.INFO, "Caricate " + availableRewards.size() + " ricompense da rewards.yml.");
    }

    /**
     * Carica i dati dei giocatori (ultima data di riscatto e XP BattlePass) dal file `data.yml`.
     */
    private void loadPlayerDataFromFile() {
        lastClaimDates.clear();
        playerBattlePassXp.clear();

        if (!dataConfig.isConfigurationSection("players")) {
            getLogger().log(Level.WARNING, "La sezione 'players' non è presente in data.yml. Nessun dato giocatore caricato.");
            return;
        }

        for (String uuidStr : Objects.requireNonNull(dataConfig.getConfigurationSection("players")).getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidStr);
                String path = "players." + uuidStr;

                // Carica l'ultima data di riscatto.
                String dateString = dataConfig.getString(path + ".last_claim_date");
                if (dateString != null) {
                    try {
                        lastClaimDates.put(playerUUID, LocalDate.parse(dateString));
                    } catch (DateTimeParseException e) {
                        getLogger().log(Level.WARNING, "Formato data non valido per " + uuidStr + " in data.yml. Ignorato. Errore: " + e.getMessage());
                    }
                }

                // Carica l'XP del BattlePass.
                int xp = dataConfig.getInt(path + ".battlepass_xp", 0);
                playerBattlePassXp.put(playerUUID, xp);

            } catch (IllegalArgumentException e) {
                getLogger().log(Level.WARNING, "UUID non valido trovato in data.yml: " + uuidStr + ". Ignorato. Errore: " + e.getMessage());
            }
        }
        getLogger().log(Level.INFO, "Caricati i dati di " + lastClaimDates.size() + " giocatori da data.yml.");
    }

    /**
     * Salva i dati correnti dei giocatori (ultima data di riscatto e XP BattlePass) nel file `data.yml`.
     */
    private void savePlayerDataToFile() {
        // Pulisce la sezione 'players' per evitare dati obsoleti.
        dataConfig.set("players", null);

        // Salva le date di riscatto.
        lastClaimDates.forEach((uuid, date) ->
                dataConfig.set("players." + uuid.toString() + ".last_claim_date", date.toString()));

        // Salva l'XP del BattlePass.
        playerBattlePassXp.forEach((uuid, xp) ->
                dataConfig.set("players." + uuid.toString() + ".battlepass_xp", xp));

        try {
            dataConfig.save(dataFile);
            getLogger().log(Level.INFO, "Dati dei giocatori salvati in data.yml.");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Impossibile salvare data.yml: " + e.getMessage());
        }
    }

    /**
     * Carica la configurazione dei livelli del BattlePass.
     * Questa configurazione è attualmente hardcoded per semplicità e per rispettare il vincolo del singolo file,
     * ma in un plugin più complesso potrebbe essere caricata da un file di configurazione separato.
     */
    private void loadBattlePassConfig() {
        // Definizione delle soglie XP per ogni livello
        battlePassLevelThresholds.put(1, 0); // Livello 1 sbloccato all'inizio
        battlePassLevelThresholds.put(2, 100);
        battlePassLevelThresholds.put(3, 250);
        battlePassLevelThresholds.put(4, 450);
        battlePassLevelThresholds.put(5, 700);
        battlePassLevelThresholds.put(6, 1000);
        battlePassLevelThresholds.put(7, 1350);
        battlePassLevelThresholds.put(8, 1750);
        battlePassLevelThresholds.put(9, 2200);
        battlePassLevelThresholds.put(10, 2700);
        battlePassLevelThresholds.put(11, 3250);
        battlePassLevelThresholds.put(12, 3850);
        battlePassLevelThresholds.put(13, 4500);
        battlePassLevelThresholds.put(14, 5200);
        battlePassLevelThresholds.put(15, 6000);
        battlePassLevelThresholds.put(16, 6850);
        battlePassLevelThresholds.put(17, 7750);
        battlePassLevelThresholds.put(18, 8700);
        battlePassLevelThresholds.put(19, 9700);
        battlePassLevelThresholds.put(20, 10800);
        battlePassLevelThresholds.put(21, 12000);
        battlePassLevelThresholds.put(22, 13300);
        battlePassLevelThresholds.put(23, 14700);
        battlePassLevelThresholds.put(24, 16200);
        battlePassLevelThresholds.put(25, 17800);
        battlePassLevelThresholds.put(26, 19500);
        battlePassLevelThresholds.put(27, 21300);
        battlePassLevelThresholds.put(28, 23200);
        battlePassLevelThresholds.put(29, 25200);
        battlePassLevelThresholds.put(30, 27300);

        // Definizione delle ricompense per ogni livello (ItemStack)
        // Livello 1: Ricompensa iniziale
        battlePassLevelRewards.put(1, Collections.singletonList(createGuiItem(Material.WOODEN_PICKAXE, "&aPicozza di Legno", Arrays.asList("&7Per i tuoi primi blocchi.", "&7Livello 1 Reward."))));
        battlePassLevelCommands.put(1, Arrays.asList("give %player% wooden_pickaxe 1"));

        // Livello 2: Ferro
        battlePassLevelRewards.put(2, Collections.singletonList(createGuiItem(Material.IRON_INGOT, "&fLingotto di Ferro", Arrays.asList("&7Utile per strumenti migliori.", "&7Livello 2 Reward."))));
        battlePassLevelCommands.put(2, Arrays.asList("give %player% iron_ingot 3"));

        // Livello 3: Pane
        battlePassLevelRewards.put(3, Collections.singletonList(createGuiItem(Material.BREAD, "&6Pane", Arrays.asList("&7Per ripristinare la fame.", "&7Livello 3 Reward."))));
        battlePassLevelCommands.put(3, Arrays.asList("give %player% bread 5"));

        // Livello 4: Carota d'oro
        battlePassLevelRewards.put(4, Collections.singletonList(createGuiItem(Material.GOLDEN_CARROT, "&eCarota d'Oro", Arrays.asList("&7Ti dà visione notturna temporanea.", "&7Livello 4 Reward."))));
        battlePassLevelCommands.put(4, Arrays.asList("give %player% golden_carrot 2"));

        // Livello 5: Diamante
        battlePassLevelRewards.put(5, Collections.singletonList(createGuiItem(Material.DIAMOND, "&bDiamante", Arrays.asList("&7Una risorsa preziosa!", "&7Livello 5 Reward."))));
        battlePassLevelCommands.put(5, Arrays.asList("give %player% diamond 1"));

        // Livello 6: Blocco di Quarzo
        battlePassLevelRewards.put(6, Collections.singletonList(createGuiItem(Material.QUARTZ_BLOCK, "&fBlocco di Quarzo", Arrays.asList("&7Per costruzioni eleganti.", "&7Livello 6 Reward."))));
        battlePassLevelCommands.put(6, Arrays.asList("give %player% quartz_block 16"));

        // Livello 7: Incudine
        battlePassLevelRewards.put(7, Collections.singletonList(createGuiItem(Material.ANVIL, "&7Incudine", Arrays.asList("&7Per riparare i tuoi oggetti.", "&7Livello 7 Reward."))));
        battlePassLevelCommands.put(7, Arrays.asList("give %player% anvil 1"));

        // Livello 8: Ender Pearl
        battlePassLevelRewards.put(8, Collections.singletonList(createGuiItem(Material.ENDER_PEARL, "&5Ender Pearl", Arrays.asList("&7Per teletrasportarti rapidamente.", "&7Livello 8 Reward."))));
        battlePassLevelCommands.put(8, Arrays.asList("give %player% ender_pearl 4"));

        // Livello 9: Shulker Box
        battlePassLevelRewards.put(9, Collections.singletonList(createGuiItem(Material.SHULKER_BOX, "&dShulker Box", Arrays.asList("&7Un contenitore portatile.", "&7Livello 9 Reward."))));
        battlePassLevelCommands.put(9, Arrays.asList("give %player% shulker_box 1"));

        // Livello 10: Elytra
        battlePassLevelRewards.put(10, Collections.singletonList(createGuiItem(Material.ELYTRA, "&aElytra", Arrays.asList("&7Vola nei cieli!", "&7Livello 10 Reward."))));
        battlePassLevelCommands.put(10, Arrays.asList("give %player% elytra 1"));

        // Aggiungi più livelli e ricompense per raggiungere la lunghezza richiesta
        // Esempio per Livello 11
        battlePassLevelRewards.put(11, Collections.singletonList(createGuiItem(Material.DIAMOND_PICKAXE, "&bPicozza di Diamante", Arrays.asList("&7Utile per minare ossidiana.", "&7Livello 11 Reward."))));
        battlePassLevelCommands.put(11, Arrays.asList("give %player% diamond_pickaxe 1"));

        // Livello 12: Pozione di Forza
        battlePassLevelRewards.put(12, Collections.singletonList(createGuiItem(Material.POTION, "&cPozione di Forza II", Arrays.asList("&7Aumenta il tuo danno.", "&7Livello 12 Reward."))));
        battlePassLevelCommands.put(12, Arrays.asList("give %player% potion{Potion:strength} 1"));

        // Livello 13: Bottiglia di XP
        battlePassLevelRewards.put(13, Collections.singletonList(createGuiItem(Material.EXPERIENCE_BOTTLE, "&eBottiglia di XP", Arrays.asList("&7Ti dà esperienza.", "&7Livello 13 Reward."))));
        battlePassLevelCommands.put(13, Arrays.asList("give %player% experience_bottle 16"));

        // Livello 14: Blocco di Smeraldo
        battlePassLevelRewards.put(14, Collections.singletonList(createGuiItem(Material.EMERALD_BLOCK, "&2Blocco di Smeraldo", Arrays.asList("&7Per i tuoi scambi.", "&7Livello 14 Reward."))));
        battlePassLevelCommands.put(14, Arrays.asList("give %player% emerald_block 2"));

        // Livello 15: Nettuno
        battlePassLevelRewards.put(15, Collections.singletonList(createGuiItem(Material.TRIDENT, "&bTridente", Arrays.asList("&7Un'arma potente.", "&7Livello 15 Reward."))));
        battlePassLevelCommands.put(15, Arrays.asList("give %player% trident 1"));

        for (int i = 16; i <= 30; i++) {
            Material currentMaterial;
            String itemName;
            List<String> itemCommands;
            int itemAmount = 1;

            if (i % 5 == 0) { // Ricompense "speciali" ogni 5 livelli
                currentMaterial = Material.NETHERITE_INGOT;
                itemName = "&5Lingotto di Netherite";
                itemCommands = Arrays.asList("give %player% netherite_ingot 1");
            } else if (i % 3 == 0) {
                currentMaterial = Material.DIAMOND_BLOCK;
                itemName = "&bBlocco di Diamante";
                itemCommands = Arrays.asList("give %player% diamond_block 1");
            } else {
                currentMaterial = Material.GOLD_BLOCK;
                itemName = "&eBlocco d'Oro";
                itemCommands = Arrays.asList("give %player% gold_block 1");
            }

            battlePassLevelRewards.put(i, Collections.singletonList(createGuiItem(currentMaterial, itemName + " (Livello " + i + ")", Arrays.asList("&7Una ricompensa per il tuo duro lavoro.", "&7Livello " + i + " Reward."))));
            battlePassLevelCommands.put(i, itemCommands);
        }

        getLogger().log(Level.INFO, "Configurazione BattlePass caricata con " + battlePassLevelThresholds.size() + " livelli.");
    }

    /**
     * Rappresenta una singola ricompensa configurabile.
     * Questa è una classe interna per rispettare il vincolo del singolo file Java.
     */
    private static class RewardItem {
        private final String displayName;
        private final Material material;
        private final int amount;
        private final List<String> lore;
        private final List<String> commands;
        private final int xpReward;

        public RewardItem(String displayName, Material material, int amount, List<String> lore, List<String> commands, int xpReward) {
            this.displayName = displayName;
            this.material = material;
            this.amount = amount;
            this.lore = lore;
            this.commands = commands;
            this.xpReward = xpReward;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public int getAmount() { return amount; }
        public List<String> getLore() { return lore; }
        public List<String> getCommands() { return commands; }
        public int getXpReward() { return xpReward; }
    }

    // --- Sezione Gestione GUI ---

    /**
     * Crea un ItemStack personalizzato per le GUI.
     *
     * @param material Il materiale dell'item.
     * @param name Il nome visualizzato dell'item.
     * @param lore La lista di stringhe per la lore dell'item.
     * @return L'ItemStack creato.
     */
    private ItemStack createGuiItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material, 1); // Quantità 1 è standard per GUI display
        final ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            meta.setLore(lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Apre la GUI delle ricompense giornaliere per un giocatore.
     * La GUI è un inventario personalizzato che mostra un placeholder per la ricompensa.
     *
     * @param player Il giocatore a cui aprire la GUI.
     */
    private void openDailyRewardGUI(Player player) {
        // La GUI ha una dimensione fissa di 9 slot (una riga).
        final Inventory gui = Bukkit.createInventory(null, 9, claimGUITitle);

        // Prepara l'item da visualizzare nella GUI.
        ItemStack rewardDisplayItem;
        List<String> lore = new ArrayList<>();
        lore.add(""); // Aggiungi una riga vuota per spaziatura

        // Controlla se il giocatore ha già riscattato la ricompensa oggi.
        if (hasPlayerClaimedToday(player.getUniqueId())) {
            rewardDisplayItem = createGuiItem(Material.RED_WOOL, ChatColor.RED + "Già Riscattato Oggi",
                    Arrays.asList(ChatColor.GRAY + "Hai già ottenuto la tua ricompensa", ChatColor.GRAY + "giornaliera. Riprova domani!"));
        } else {
            rewardDisplayItem = createGuiItem(Material.LIME_WOOL, ChatColor.GREEN + "Clicca per Riscattare",
                    Arrays.asList(ChatColor.GRAY + "Clicca qui per riscattare la tua", ChatColor.GRAY + "ricompensa giornaliera!"));
        }

        // Posiziona l'item al centro della GUI (slot 4).
        gui.setItem(4, rewardDisplayItem);

        // Memorizza l'inventario aperto per il giocatore.
        openClaimGUIs.put(player.getUniqueId(), gui);
        player.openInventory(gui);
        getLogger().log(Level.INFO, "Aperta Daily Reward GUI per " + player.getName());
    }

    /**
     * Apre la GUI del BattlePass per un giocatore.
     * La GUI mostra i progressi del giocatore e le ricompense sbloccate/bloccate.
     *
     * @param player Il giocatore a cui aprire la GUI.
     */
    private void openBattlePassGUI(Player player) {
        // La GUI ha una dimensione di 54 slot (6 righe).
        final Inventory gui = Bukkit.createInventory(null, 54, battlePassGUITitle);
        UUID playerUUID = player.getUniqueId();
        int playerCurrentXP = playerBattlePassXp.getOrDefault(playerUUID, 0);
        int playerCurrentLevel = calculateBattlePassLevel(playerCurrentXP);

        // Calcola il livello massimo definito.
        Optional<Integer> maxLevelOptional = battlePassLevelThresholds.keySet().stream().max(Comparator.naturalOrder());
        int maxLevel = maxLevelOptional.orElse(1); // Default a 1 se non ci sono livelli definiti.

        // Crea gli item per ogni livello del BattlePass.
        for (Map.Entry<Integer, Integer> entry : battlePassLevelThresholds.entrySet()) {
            int level = entry.getKey();
            int requiredXP = entry.getValue();

            // Determina lo stato della ricompensa.
            boolean isLevelReached = playerCurrentXP >= requiredXP;
            // Verifica se la ricompensa di questo livello è già stata riscattata.
            // Utilizzo un approccio semplice: se il giocatore ha superato il livello, consideriamo la ricompensa riscattabile o già riscattata.
            // Per una gestione più granulare (es. ricompense specifiche per livello già riscattate), si dovrebbe memorizzare anche questo dato.
            // Per ora, assumiamo che le ricompense dei livelli raggiunti siano "disponibili" per il riscatto finché non vengono cliccate.

            Material displayMaterial;
            String displayName;
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Livello Richiesto: " + level);
            lore.add(ChatColor.GRAY + "XP Richiesta: " + requiredXP);

            if (isLevelReached) {
                // Controlla se il giocatore ha già riscattato la ricompensa per questo livello.
                // Questa è una semplificazione. Idealmente, avremmo un set di livelli riscattati per ogni giocatore.
                // Per ora, assumiamo che un clic nella GUI BattlePass comporti il riscatto se il livello è sbloccato.
                // Aggiungeremo una logica per memorizzare i livelli riscattati per singolo giocatore.
                if (hasBattlePassRewardClaimed(playerUUID, level)) {
                    displayMaterial = Material.GRAY_STAINED_GLASS_PANE; // Già riscattato
                    displayName = battlePassClaimedRewardMessage;
                    lore.add(ChatColor.DARK_GRAY + "Hai già ottenuto questa ricompensa.");
                } else {
                    displayMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE; // Clicca per riscattare
                    displayName = ChatColor.GREEN + battlePassUnclaimedRewardMessage;
                    lore.add(ChatColor.GREEN + "Clicca per ottenere la ricompensa!");
                }

                // Aggiunge la descrizione della ricompensa effettiva.
                if (battlePassLevelRewards.containsKey(level) && !battlePassLevelRewards.get(level).isEmpty()) {
                    ItemStack rewardItem = battlePassLevelRewards.get(level).get(0);
                    lore.add(ChatColor.GRAY + "Ricompensa: " + ChatColor.RESET + rewardItem.getItemMeta().getDisplayName());
                }

            } else {
                displayMaterial = Material.RED_STAINED_GLASS_PANE; // Livello non raggiunto
                displayName = ChatColor.RED + battlePassLockedRewardMessage;
                lore.add(ChatColor.RED + "Hai bisogno di più XP per sbloccare!");
            }

            // Aggiungi XP attuale e XP per il prossimo livello (se non è l'ultimo)
            lore.add(""); // Spaziatore
            lore.add(ChatColor.BLUE + "La tua XP: " + playerCurrentXP);
            if (level < maxLevel) {
                // Trova il prossimo livello più alto e la sua soglia.
                int nextLevel = battlePassLevelThresholds.keySet().stream()
                        .filter(l -> l > level)
                        .min(Comparator.naturalOrder())
                        .orElse(maxLevel + 1); // Se non c'è un livello superiore, considera maxLevel+1

                int nextLevelXP = battlePassLevelThresholds.getOrDefault(nextLevel, requiredXP); // Fallback a requiredXP

                if (nextLevelXP > playerCurrentXP) {
                    lore.add(ChatColor.BLUE + "Prossimo livello (" + nextLevel + "): " + nextLevelXP + " XP");
                    lore.add(ChatColor.BLUE + "Mancano: " + (nextLevelXP - playerCurrentXP) + " XP");
                }
            }


            ItemStack levelItem = createGuiItem(displayMaterial, displayName, lore);

            // Calcola la posizione nell'inventario.
            // Questo esempio distribuisce i livelli su più righe.
            // Puoi personalizzare il layout come preferisci.
            int slot = (level - 1) * 2; // Ogni livello occupa 2 slot, per esempio, per distanziarli un po'.
            if (slot < gui.getSize()) { // Assicurati di non andare oltre la dimensione dell'inventario
                gui.setItem(slot, levelItem);
            }
        }

        // Mostra i progressi generali del giocatore in una sezione della GUI.
        ItemStack playerProgressItem = createGuiItem(Material.CLOCK, ChatColor.YELLOW + "Il tuo Progresso BattlePass",
                Arrays.asList(
                        ChatColor.GRAY + "--------------------",
                        battlePassLevelDisplayFormat.replace("%level%", String.valueOf(playerCurrentLevel)),
                        battlePassXPDisplayFormat.replace("%current_xp%", String.valueOf(playerCurrentXP))
                                .replace("%next_level_xp%", String.valueOf(getNextLevelXPThreshold(playerCurrentLevel))),
                        ChatColor.GRAY + "--------------------"
                ));
        gui.setItem(49, playerProgressItem); // Posizione fissa per i progressi (es. in basso al centro)


        openBattlePassGUIs.put(playerUUID, gui);
        player.openInventory(gui);
        getLogger().log(Level.INFO, "Aperta BattlePass GUI per " + player.getName());
    }

    /**
     * Ottiene la soglia XP per il livello successivo.
     *
     * @param currentLevel Il livello attuale del giocatore.
     * @return La XP richiesta per il prossimo livello, o la XP del livello attuale se è il massimo.
     */
    private int getNextLevelXPThreshold(int currentLevel) {
        int maxLevel = battlePassLevelThresholds.keySet().stream().max(Comparator.naturalOrder()).orElse(1);
        if (currentLevel >= maxLevel) {
            return battlePassLevelThresholds.get(maxLevel); // Se al livello massimo, mostra la sua soglia.
        }
        return battlePassLevelThresholds.getOrDefault(currentLevel + 1, 0);
    }

    // --- Sezione Gestione Comandi ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + ChatColor.RED + "Solo i giocatori possono usare questo comando.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("claim")) {
            if (!player.hasPermission("dailyrewardspro.claim")) {
                player.sendMessage(noPermissionMessage);
                return true;
            }
            openDailyRewardGUI(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("battlepass")) {
            if (!player.hasPermission("dailyrewardspro.battlepass")) {
                player.sendMessage(noPermissionMessage);
                return true;
            }
            openBattlePassGUI(player);
            return true;
        }

        return false;
    }

    // --- Sezione Gestione Eventi ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Inizializza l'XP del BattlePass per i nuovi giocatori o carica quelli esistenti.
        if (!playerBattlePassXp.containsKey(playerUUID)) {
            playerBattlePassXp.put(playerUUID, dataConfig.getInt("players." + playerUUID.toString() + ".battlepass_xp", 0));
        }

        // Aggiungi XP per il login giornaliero.
        addBattlePassXp(player, 5); // Esempio: 5 XP per il login
        getLogger().log(Level.INFO, "Player " + player.getName() + " joined. XP: " + playerBattlePassXp.get(playerUUID));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        Inventory clickedInventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        // Previene modifiche accidentali all'inventario del plugin.
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // --- Gestione GUI Ricompense Giornaliere ---
        if (openClaimGUIs.containsKey(playerUUID) && clickedInventory.equals(openClaimGUIs.get(playerUUID))) {
            event.setCancelled(true); // Impedisce al giocatore di prendere gli item dalla GUI.

            if (clickedItem.getType() == Material.LIME_WOOL) { // L'item per riscattare
                // Controlla se il giocatore ha già riscattato oggi.
                if (hasPlayerClaimedToday(playerUUID)) {
                    player.sendMessage(claimedTodayMessage);
                    player.closeInventory();
                    return;
                }

                // Assegna una ricompensa casuale.
                giveRandomDailyReward(player);

                // Aggiorna la data dell'ultimo riscatto.
                lastClaimDates.put(playerUUID, LocalDate.now());
                savePlayerDataToFile(); // Salva immediatamente dopo il riscatto.

                player.sendMessage(rewardReceivedMessage);
                player.closeInventory();

                // Rimuovi l'inventario dalla mappa dopo la chiusura.
                openClaimGUIs.remove(playerUUID);

            } else if (clickedItem.getType() == Material.RED_WOOL) { // L'item "già riscattato"
                player.sendMessage(claimedTodayMessage);
                event.setCancelled(true); // Impedisce interazione con l'item.
            }
            return;
        }

        // --- Gestione GUI BattlePass ---
        if (openBattlePassGUIs.containsKey(playerUUID) && clickedInventory.equals(openBattlePassGUIs.get(playerUUID))) {
            event.setCancelled(true); // Impedisce modifiche.

            // Identifica il livello cliccato
            // Basandosi sulla posizione, possiamo risalire al livello.
            // Questo richiede che la logica di posizionamento nella openBattlePassGUI() sia coerente.
            int clickedSlot = event.getRawSlot();

            // Calcola il livello in base allo slot cliccato.
            // Se ogni livello occupa 2 slot e inizia dallo slot 0, allora:
            int clickedLevel = (clickedSlot / 2) + 1;

            // Assicurati che il livello sia valido e presente nella configurazione.
            if (!battlePassLevelThresholds.containsKey(clickedLevel)) {
                getLogger().log(Level.WARNING, "Il giocatore " + player.getName() + " ha cliccato uno slot non valido per il BattlePass: " + clickedSlot);
                return;
            }

            int playerXP = playerBattlePassXp.getOrDefault(playerUUID, 0);
            int requiredXPForLevel = battlePassLevelThresholds.get(clickedLevel);

            if (playerXP >= requiredXPForLevel) {
                // Livello raggiunto, ora controlla se è già riscattato.
                if (!hasBattlePassRewardClaimed(playerUUID, clickedLevel)) {
                    // Riscattabile!
                    getLogger().log(Level.INFO, player.getName() + " ha riscattato la ricompensa del livello " + clickedLevel);
                    giveBattlePassReward(player, clickedLevel);
                    setBattlePassRewardClaimed(playerUUID, clickedLevel); // Marchia come riscattato
                    savePlayerDataToFile(); // Salva dopo il riscatto

                    player.sendMessage(prefix + ChatColor.GREEN + "Hai riscattato la ricompensa del BattlePass per il livello " + clickedLevel + "!");
                    // Riapri la GUI per mostrare lo stato aggiornato
                    openBattlePassGUI(player);
                } else {
                    player.sendMessage(prefix + ChatColor.YELLOW + "Hai già riscattato la ricompensa per il livello " + clickedLevel + ".");
                }
            } else {
                player.sendMessage(prefix + ChatColor.RED + "Non hai ancora raggiunto il livello " + clickedLevel + " del BattlePass.");
            }
        }
    }

    // --- Sezione Gestione Ricompense Giornaliere ---

    /**
     * Controlla se un giocatore ha già riscattato la ricompensa giornaliera oggi.
     *
     * @param playerUUID L'UUID del giocatore.
     * @return true se il giocatore ha riscattato oggi, false altrimenti.
     */
    private boolean hasPlayerClaimedToday(UUID playerUUID) {
        LocalDate lastClaimDate = lastClaimDates.get(playerUUID);
        return lastClaimDate != null && lastClaimDate.isEqual(LocalDate.now());
    }

    /**
     * Assegna una ricompensa giornaliera casuale al giocatore.
     * La ricompensa viene scelta dalla lista `availableRewards`.
     *
     * @param player Il giocatore a cui dare la ricompensa.
     */
    private void giveRandomDailyReward(Player player) {
        if (availableRewards.isEmpty()) {
            player.sendMessage(prefix + ChatColor.RED + "Nessuna ricompensa giornaliera configurata. Contatta un amministratore.");
            getLogger().log(Level.WARNING, "Il file rewards.yml è vuoto o malformato. Nessuna ricompensa da dare.");
            return;
        }

        // Seleziona una ricompensa casuale.
        List<RewardItem> rewardsList = new ArrayList<>(availableRewards.values());
        Random random = new Random();
        RewardItem chosenReward = rewardsList.get(random.nextInt(rewardsList.size()));

        // Dai l'item al giocatore.
        ItemStack rewardItem = new ItemStack(chosenReward.getMaterial(), chosenReward.getAmount());
        ItemMeta meta = rewardItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(chosenReward.getDisplayName());
            meta.setLore(chosenReward.getLore());
            rewardItem.setItemMeta(meta);
        }

        // Assicurati che l'inventario del giocatore non sia pieno.
        // Se l'inventario è pieno, droppa l'item a terra.
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), rewardItem);
            player.sendMessage(prefix + ChatColor.YELLOW + "Il tuo inventario è pieno, la ricompensa è stata droppata a terra!");
        } else {
            player.getInventory().addItem(rewardItem);
        }

        // Esegui i comandi associati alla ricompensa.
        for (String cmd : chosenReward.getCommands()) {
            String processedCmd = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
        }

        // Aggiungi XP al BattlePass per aver riscattato la ricompensa.
        addBattlePassXp(player, chosenReward.getXpReward());
    }

    // --- Sezione Gestione BattlePass ---

    /**
     * Aggiunge punti XP al BattlePass di un giocatore e controlla il level up.
     *
     * @param player Il giocatore a cui aggiungere XP.
     * @param xpToAdd La quantità di XP da aggiungere.
     */
    private void addBattlePassXp(Player player, int xpToAdd) {
        UUID playerUUID = player.getUniqueId();
        int currentXP = playerBattlePassXp.getOrDefault(playerUUID, 0);
        int previousLevel = calculateBattlePassLevel(currentXP);

        int newXP = currentXP + xpToAdd;
        playerBattlePassXp.put(playerUUID, newXP);

        int newLevel = calculateBattlePassLevel(newXP);

        // Notifica il guadagno di XP.
        player.sendMessage(prefix + battlePassXpGainMessage.replace("%xp%", String.valueOf(xpToAdd)));

        // Controlla se il giocatore è salito di livello.
        if (newLevel > previousLevel) {
            player.sendMessage(prefix + battlePassLevelUpMessage.replace("%level%", String.valueOf(newLevel)));
            getLogger().log(Level.INFO, player.getName() + " ha raggiunto il livello BattlePass " + newLevel + " con " + newXP + " XP.");

            // Loop attraverso ogni livello sbloccato tra il precedente e il nuovo.
            for (int level = previousLevel + 1; level <= newLevel; level++) {
                if (battlePassLevelRewards.containsKey(level)) {
                    // Non dare la ricompensa automaticamente, ma rendila riscattabile tramite GUI.
                    // Se desideri che siano automatiche, sposta giveBattlePassReward qui.
                    // Per ora, solo la notifica del livello.
                    getLogger().log(Level.INFO, "Ricompensa per il livello " + level + " sbloccata per " + player.getName() + ".");
                }
            }
        }
        savePlayerDataToFile(); // Salva i dati XP aggiornati.
    }

    /**
     * Calcola il livello BattlePass di un giocatore basandosi sulla sua XP.
     *
     * @param xp I punti esperienza del giocatore.
     * @return Il livello BattlePass raggiunto.
     */
    private int calculateBattlePassLevel(int xp) {
        int currentLevel = 1;
        // Ordina i livelli in modo decrescente per trovare il livello più alto raggiunto.
        List<Integer> sortedLevels = new ArrayList<>(battlePassLevelThresholds.keySet());
        sortedLevels.sort(Collections.reverseOrder());

        for (int level : sortedLevels) {
            if (xp >= battlePassLevelThresholds.get(level)) {
                currentLevel = level;
                break;
            }
        }
        return currentLevel;
    }

    /**
     * Controlla se la ricompensa per un dato livello del BattlePass è già stata riscattata.
     * Questo richiede un campo nel data.yml per ogni giocatore che tenga traccia dei livelli riscattati.
     *
     * @param playerUUID L'UUID del giocatore.
     * @param level Il livello del BattlePass da controllare.
     * @return true se la ricompensa è stata riscattata, false altrimenti.
     */
    private boolean hasBattlePassRewardClaimed(UUID playerUUID, int level) {
        String path = "players." + playerUUID.toString() + ".battlepass_claimed_levels";
        List<Integer> claimedLevels = dataConfig.getIntegerList(path);
        return claimedLevels.contains(level);
    }

    /**
     * Marchia una ricompensa di un livello BattlePass come riscattata per un giocatore.
     *
     * @param playerUUID L'UUID del giocatore.
     * @param level Il livello del BattlePass la cui ricompensa è stata riscattata.
     */
    private void setBattlePassRewardClaimed(UUID playerUUID, int level) {
        String path = "players." + playerUUID.toString() + ".battlepass_claimed_levels";
        List<Integer> claimedLevels = dataConfig.getIntegerList(path);
        if (!claimedLevels.contains(level)) {
            claimedLevels.add(level);
            dataConfig.set(path, claimedLevels);
            getLogger().log(Level.INFO, "Livello BattlePass " + level + " segnato come riscattato per " + playerUUID.toString());
        }
    }

    /**
     * Assegna le ricompense di un determinato livello del BattlePass al giocatore.
     *
     * @param player Il giocatore a cui dare le ricompense.
     * @param level Il livello del BattlePass per cui dare le ricompense.
     */
    private void giveBattlePassReward(Player player, int level) {
        // Assegna gli item definiti per il livello.
        List<ItemStack> itemsToGive = battlePassLevelRewards.get(level);
        if (itemsToGive != null && !itemsToGive.isEmpty()) {
            for (ItemStack item : itemsToGive) {
                // Clona l'item per sicurezza prima di darlo.
                ItemStack clonedItem = item.clone();
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), clonedItem);
                    player.sendMessage(prefix + ChatColor.YELLOW + "Il tuo inventario è pieno, la ricompensa del BattlePass è stata droppata a terra!");
                } else {
                    player.getInventory().addItem(clonedItem);
                }
            }
        }

        // Esegui i comandi definiti per il livello.
        List<String> commandsToExecute = battlePassLevelCommands.get(level);
        if (commandsToExecute != null && !commandsToExecute.isEmpty()) {
            for (String cmd : commandsToExecute) {
                String processedCmd = cmd.replace("%player%", player.getName());
                // Esegui il comando sul thread principale per evitare problemi.
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                    }
                }.runTask(this);
            }
        }
        getLogger().log(Level.INFO, "Date ricompense del BattlePass per il livello " + level + " a " + player.getName());
    }
}
