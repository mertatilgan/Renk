package me.kikaru.renk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Renk plugin: Allows players to customize their display name color.
 */
public final class Renk extends JavaPlugin implements TabExecutor, TabCompleter {

    //region Constants
    private static final String DATA_FILE_PATH = "plugins/Renk/player_colors.json";
    private static final String DEFAULT_HEX_COLOR = "#FFFFFF";
    //endregion

    //region Fields
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerColorData> playerColors = new HashMap<>();
    private Scoreboard scoreboard;
    private Map<String, String> predefinedColors;
    //endregion

    //region Plugin Lifecycle
    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        // Load colors from config
        loadColorsFromConfig();

        // Register command and tab completer
        PluginCommand renkCommand = getCommand("renk");
        if (renkCommand != null) {
            renkCommand.setExecutor(this);
            renkCommand.setTabCompleter(this);
        } else {
            getLogger().severe("Could not register the 'renk' command.");
        }

        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        loadData();

        // Register event listener for player join
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
    }

    @Override
    public void onDisable() {
        saveData();
    }
    //endregion

    //region Color Loading
    private void loadColorsFromConfig() {
        predefinedColors = new HashMap<>();
        ConfigurationSection colorsSection = getConfig().getConfigurationSection("colors");
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                predefinedColors.put(key.toLowerCase(), colorsSection.getString(key));
            }
        }
    }
    //endregion

    //region Command Handling
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        // Using a MiniMessage instance for text formatting
        MiniMessage miniMessage = MiniMessage.miniMessage();

        if (command.getName().equalsIgnoreCase("renk")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
                return handleSelfColorReset(sender);
            } else if (args.length == 1) {
                return handleSelfColorChange(sender, args[0]);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                return handleAdminResetColor(sender, args[1]);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                return handleAdminSetColor(sender, args[1], args[2]);
            } else {
                sender.sendMessage(miniMessage.deserialize(getMessage("usage", Map.of())));
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("renk")) {
            if (args.length == 1) {
                return new ArrayList<>(predefinedColors.keySet());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                return new ArrayList<>(predefinedColors.keySet());
            }
        }
        return List.of();
    }
    //endregion

    //region Color Management
    private String getColorFromMap(String colorName) {
        return predefinedColors.getOrDefault(colorName.toLowerCase(), null);
    }
    //endregion

    //region Command Handlers

    /**
     * Handles the "renk reset" command for resetting the sender's own color.
     *
     * @param sender The command sender.
     * @return True if the command was handled successfully, false otherwise.
     */
    private boolean handleSelfColorReset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("player_only", Map.of())));
            return true;
        }

        resetPlayerColor(player);
        removePlayerColor(player.getUniqueId());
        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("color_reset", Map.of())));

        return true;
    }

    /**
     * Handles the "renk reset <player>" command for resetting another player's color (admin only).
     *
     * @param sender     The command sender.
     * @param targetName The name of the player to reset the color for.
     * @return True if the command was handled successfully, false otherwise.
     */
    private boolean handleAdminResetColor(CommandSender sender, String targetName) {
        if (!sender.hasPermission("renk.reset.other")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("no_permission", Map.of())));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("player_not_found", Map.of())));
            return true;
        }

        resetPlayerColor(target);
        removePlayerColor(target.getUniqueId());

        Map<String, String> replacements = Map.of("player", target.getName());

        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_reset", replacements)));
        target.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_reset_notify", Map.of())));

        return true;
    }

    /**
     * Handles the "renk <color>" command for changing the sender's own color.
     *
     * @param sender The command sender.
     * @param input  The color input (hex code or color name).
     * @return True if the command was handled successfully, false otherwise.
     */
    private boolean handleSelfColorChange(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("player_only", Map.of())));
            return true;
        }

        String hex = input.startsWith("#") ? input : getPredefinedColor(input.toLowerCase());
        if (hex == null || !isValidHexColor(hex)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("invalid_color", Map.of())));
            return true;
        }

        setPlayerColor(player, hex);

        Map<String, String> replacements = Map.of(
                "output_color", hex,
                "input_color", input,
                "player", player.getName()
        );
        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("color_changed", replacements)));
        return true;
    }

    /**
     * Handles the "renk set <player> <color>" command for setting another player's color (admin only).
     *
     * @param sender     The command sender.
     * @param targetName The name of the player to set the color for.
     * @param input      The color input (hex code or color name).
     * @return True if the command was handled successfully, false otherwise.
     */
    private boolean handleAdminSetColor(CommandSender sender, String targetName, String input) {
        if (!sender.hasPermission("renk.set.other")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("no_permission", Map.of())));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("player_not_found", Map.of())));
            return true;
        }

        String hex = input.startsWith("#") ? input : getPredefinedColor(input.toLowerCase());
        if (hex == null || !isValidHexColor(hex)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("invalid_color", Map.of())));
            return true;
        }

        setPlayerColor(target, hex);

        Map<String, String> replacements = Map.of(
                "player", target.getName(),
                "output_color", hex,
                "input_color", input
        );
        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_set", replacements)));

        target.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_notify", Map.of(
                "output_color", hex,
                "input_color", input
        ))));
        return true;
    }
    //endregion

    //region Color Management
    private void setPlayerColor(Player player, String hex) {
        if (hex == null || !isValidHexColor(hex)) {
            player.sendMessage("Your color setting is invalid or missing, setting to default.");
            hex = DEFAULT_HEX_COLOR;
        }

        // Find closest Minecraft color
        String minecraftColor = findClosestMinecraftColor(hex);

        // Setup team with Minecraft color
        setupTeam(player, minecraftColor);

        // Set display name with hex color
        MiniMessage miniMessage = MiniMessage.miniMessage();
        Component coloredName = miniMessage.deserialize("<color:" + hex + ">" + player.getName() + "</color>");
        player.displayName(coloredName);
        player.playerListName(coloredName);

        // Save both colors
        PlayerColorData colorData = new PlayerColorData(hex, minecraftColor);
        savePlayerColor(player.getUniqueId(), colorData);
    }

    private void resetPlayerColor(Player player) {
        // Remove from team
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }

        MiniMessage miniMessage = MiniMessage.miniMessage();
        Component defaultName = miniMessage.deserialize(player.getName());
        player.displayName(defaultName);
        player.playerListName(defaultName);
    }

    private boolean isValidHexColor(String hex) {
        return hex.matches("^#([A-Fa-f0-9]{6})$");
    }
    //endregion

    //region Team Management
    private void setupTeam(Player player, String minecraftColor) {
        String teamName = "renk_teams_" + minecraftColor;
        Team team = scoreboard.getTeam(teamName);

        // Create team if it doesn't exist
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.color(NamedTextColor.NAMES.value(minecraftColor.toLowerCase()));
        }

        // Remove player from any existing teams
        for (Team existingTeam : scoreboard.getTeams()) {
            if (existingTeam.hasEntry(player.getName())) {
                existingTeam.removeEntry(player.getName());
            }
        }

        // Add player to new team
        team.addEntry(player.getName());
    }
    //endregion

    //region Data Persistence
    private void loadData() {
        File dataFile = new File(DATA_FILE_PATH);
        if (!dataFile.exists()) {
            playerColors.clear();
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<UUID, PlayerColorData>>() {
            }.getType();
            Map<UUID, PlayerColorData> loadedData = gson.fromJson(reader, type);
            playerColors.clear();
            if (loadedData != null) {
                playerColors.putAll(loadedData);
            }
            getLogger().info("Loaded player color data from JSON.");
        } catch (IOException e) {
            getLogger().severe("Error loading player color data: " + e.getMessage());
            e.printStackTrace();
            playerColors.clear();
        }
    }

    private void saveData() {
        File dataFile = new File(DATA_FILE_PATH);
        File parentDir = dataFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            getLogger().severe("Failed to create plugin data folder!");
            return;
        }

        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(playerColors, writer);
            getLogger().info("Saved player color data to JSON.");
        } catch (IOException e) {
            getLogger().severe("Error saving player color data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void savePlayerColor(UUID playerUUID, PlayerColorData colorData) {
        playerColors.put(playerUUID, colorData);
        Bukkit.getScheduler().runTaskAsynchronously(this, this::saveData);
    }

    private void removePlayerColor(UUID playerUUID) {
        playerColors.remove(playerUUID);
        Bukkit.getScheduler().runTaskAsynchronously(this, this::saveData);
    }
    //endregion

    //region Color Conversion
    private String findClosestMinecraftColor(String hexColor) {
        // Remove the # prefix if present
        hexColor = hexColor.replace("#", "");

        // Convert hex to RGB
        int r = Integer.parseInt(hexColor.substring(0, 2), 16);
        int g = Integer.parseInt(hexColor.substring(2, 4), 16);
        int b = Integer.parseInt(hexColor.substring(4, 6), 16);

        // Define Minecraft colors with their RGB values
        Map<String, int[]> minecraftColors = new HashMap<>();
        minecraftColors.put("black", new int[]{0, 0, 0});
        minecraftColors.put("dark_blue", new int[]{0, 0, 170});
        minecraftColors.put("dark_green", new int[]{0, 170, 0});
        minecraftColors.put("dark_aqua", new int[]{0, 170, 170});
        minecraftColors.put("dark_red", new int[]{170, 0, 0});
        minecraftColors.put("dark_purple", new int[]{170, 0, 170});
        minecraftColors.put("gold", new int[]{255, 170, 0});
        minecraftColors.put("gray", new int[]{170, 170, 170});
        minecraftColors.put("dark_gray", new int[]{85, 85, 85});
        minecraftColors.put("blue", new int[]{85, 85, 255});
        minecraftColors.put("green", new int[]{85, 255, 85});
        minecraftColors.put("aqua", new int[]{85, 255, 255});
        minecraftColors.put("red", new int[]{255, 85, 85});
        minecraftColors.put("light_purple", new int[]{255, 85, 255});
        minecraftColors.put("yellow", new int[]{255, 255, 85});
        minecraftColors.put("white", new int[]{255, 255, 255});

        String closestColor = null;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<String, int[]> entry : minecraftColors.entrySet()) {
            int[] color = entry.getValue();
            double distance = Math.sqrt(
                    Math.pow(r - color[0], 2) +
                            Math.pow(g - color[1], 2) +
                            Math.pow(b - color[2], 2)
            );

            if (distance < minDistance) {
                minDistance = distance;
                closestColor = entry.getKey();
            }
        }

        return closestColor;
    }

    private String getPredefinedColor(String colorName) {
        return predefinedColors.get(colorName);
    }
    //endregion

    //region Configuration
    private String getMessage(String key, Map<String, String> replacements) {
        String message = getConfig().getString("messages." + key, key);
        // Replaces placeholders in the message with values from the `replacements` map.
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }

    private void loadPredefinedColors() {
        predefinedColors = new HashMap<>();
        if (getConfig().getConfigurationSection("colors") != null) {
            for (String key : getConfig().getConfigurationSection("colors").getKeys(false)) {
                String colorCode = getConfig().getString("colors." + key);
                if (colorCode != null) {
                    predefinedColors.put(key.toLowerCase(), colorCode);
                }
            }
        } else {
            // Set default colors if not configured. Maybe this option too should be configurable with a bool in config.yml?
            predefinedColors.put("black", "#000000");
            predefinedColors.put("dark_blue", "#0000AA");
            predefinedColors.put("dark_green", "#00AA00");
            predefinedColors.put("dark_aqua", "#00AAAA");
            predefinedColors.put("dark_red", "#AA0000");
            predefinedColors.put("dark_purple", "#AA00AA");
            predefinedColors.put("gold", "#FFAA00");
            predefinedColors.put("gray", "#AAAAAA");
            predefinedColors.put("dark_gray", "#555555");
            predefinedColors.put("blue", "#5555FF");
            predefinedColors.put("green", "#55FF55");
            predefinedColors.put("aqua", "#55FFFF");
            predefinedColors.put("red", "#FF5555");
            predefinedColors.put("light_purple", "#FF55FF");
            predefinedColors.put("yellow", "#FFFF55");
            predefinedColors.put("white", "#FFFFFF");
        }
    }
    //endregion

    //region Inner Classes

    /**
     * Listener for player join events to apply the color on join.
     */
    private class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            PlayerColorData colorData = playerColors.get(player.getUniqueId());
            if (colorData != null) {
                setPlayerColor(player, colorData.hexColor);
            }
        }
    }
    //endregion
}

/**
 * Represents player color data, storing the hex color and Minecraft color.
 */
class PlayerColorData {
    String hexColor;
    String minecraftColor;

    public PlayerColorData(String hexColor, String minecraftColor) {
        this.hexColor = hexColor;
        this.minecraftColor = minecraftColor;
    }
}