package me.kikaru.renk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Renk extends JavaPlugin implements TabExecutor, TabCompleter {

    private static final String DATA_FILE_PATH = "plugins/Renk/player_colors.json";
    // Gson for handling JSON serialization and deserialization.  `setPrettyPrinting` makes the JSON file more human-readable.
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, String> playerColors = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        Objects.requireNonNull(this.getCommand("renk")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("renk")).setTabCompleter(this);

        loadData();

        Bukkit.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                String colorHex = playerColors.get(player.getUniqueId()); // Directly access the map

                if (colorHex != null) {
                    setPlayerColor(player, colorHex);
                }
            }
        }, this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        MiniMessage miniMessage = MiniMessage.miniMessage();

        // Handles different command arguments using if/else if statements
        // "renk reset" - Resets the color of the command sender
        if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
            return handleSelfColorReset(sender);
            // "renk <color>" - Changes the color of the command sender
        } else if (args.length == 1) {
            return handleSelfColorChange(sender, args[0]);
            // "renk reset <player>" - Resets the color of another player (admin only)
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return handleAdminResetColor(sender, args[1]);
            // "renk set <player> <color>" - Sets the color of another player (admin only)
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return handleAdminSetColor(sender, args[1], args[2]);
            // Invalid arguments
        } else {
            sender.sendMessage(miniMessage.deserialize(getMessage("usage", Map.of())));
            return true;
        }
    }

    // Handles the "renk reset" command for resetting the sender's own color.
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

    // Handles the "renk reset <player>" command for resetting another player's color (admin only).
    private boolean handleAdminResetColor(CommandSender sender, String targetName) {
        // Checks if the sender has the required permission.
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

        Map<String, String> replacements = new HashMap<>();
        replacements.put("player", target.getName());

        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_reset", replacements)));
        target.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_reset_notify", Map.of())));

        return true;
    }

    // Handles the "renk <color>" command for changing the sender's own color.
    private boolean handleSelfColorChange(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("player_only", Map.of())));
            return true;
        }

        // Tries to get the hex code from the color name.  If `input` is already a hex code, it's used directly.
        String hex = input.startsWith("#") ? input : getColorFromMap(input.toLowerCase());

        // Validates the color input to ensure it's a valid hex code.
        if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("invalid_color", Map.of())));
            return true;
        }

        setPlayerColor(player, hex);
        savePlayerColor(player.getUniqueId(), hex);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("output_color", hex);
        replacements.put("input_color", input);
        replacements.put("player", player.getName());

        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("color_changed", replacements)));

        return true;
    }

    // Handles the "renk set <player> <color>" command for setting another player's color (admin only).
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

        // Tries to get the hex code from the color name. If `input` is already a hex code, it's used directly.
        String hex = input.startsWith("#") ? input : getColorFromMap(input.toLowerCase());

        // Validates the color input to ensure it's a valid hex code.
        if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("invalid_color", Map.of())));
            return true;
        }

        setPlayerColor(target, hex);
        savePlayerColor(target.getUniqueId(), hex);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("player", target.getName());
        replacements.put("output_color", hex);
        replacements.put("input_color", input);

        sender.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_set", replacements)));

        replacements.clear();
        replacements.put("output_color", hex);
        replacements.put("input_color", input);

        target.sendMessage(MiniMessage.miniMessage().deserialize(getMessage("admin_color_notify", replacements)));

        return true;
    }

    // Sets the player's display name and tab list name to the specified color.
    private void setPlayerColor(Player player, String hex) {
        // Validates the provided hex code.  If it's invalid, defaults to white.
        if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) {
            player.sendMessage("Your color setting is invalid or missing, setting to default.");
            hex = "#FFFFFF"; // Default color (white)
        }

        MiniMessage miniMessage = MiniMessage.miniMessage();
        // Constructs the colored name using MiniMessage's `<color>` tag.
        Component coloredName = miniMessage.deserialize("<color:" + hex + ">" + player.getName() + "</color>");

        // Set the display name in chat
        player.displayName(coloredName);

        // Set the tab list name
        player.playerListName(coloredName);
    }

    private void resetPlayerColor(Player player) {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        Component defaultName = miniMessage.deserialize(player.getName());

        // Set the display name in chat
        player.displayName(defaultName);

        // Set the tab list name
        player.playerListName(defaultName);
    }

    // Loads player color data from the JSON file.
    private void loadData() {
        // Creates a File object representing the data file.
        File dataFile = new File(DATA_FILE_PATH);
        // If the data file doesn't exist, initialize the playerColors map and return.
        if (!dataFile.exists()) {
            playerColors.clear();
            return;
        }

        // Reads the data from the JSON file using a FileReader and Gson.
        try (FileReader reader = new FileReader(dataFile)) {
            // Defines the type of data to be read from the JSON file (Map<UUID, String>).
            Type type = new TypeToken<Map<UUID, String>>() {}.getType();
            // Deserializes the JSON data into a Map<UUID, String> object.
            Map<UUID, String> loadedData = gson.fromJson(reader, type);

            // If data was loaded, populate the playerColors map.
            if (loadedData != null) {
                playerColors.clear(); // Clear existing data
                playerColors.putAll(loadedData); // Add all loaded data
            } else {
                playerColors.clear(); // Ensure it's empty if loading fails
            }
            getLogger().info("Loaded player color data from JSON.");

            // Handles potential IOExceptions during file reading.
        } catch (IOException e) {
            getLogger().severe("Error loading player color data: " + e.getMessage());
            e.printStackTrace();
            playerColors.clear();
        }
    }

    // Saves player color data to the JSON file.
    private void saveData() {
        File dataFile = new File(DATA_FILE_PATH);
        File parentDir = dataFile.getParentFile();

        // Creates the parent directory if it doesn't exist.
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                getLogger().severe("Failed to create plugin data folder!");
                return;
            }
        }

        // Writes the player color data to the JSON file using a FileWriter and Gson.
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(playerColors, writer);
            getLogger().info("Saved player color data to JSON.");
            // Handles potential IOExceptions during file writing.
        } catch (IOException e) {
            getLogger().severe("Error saving player color data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Saves a player's color to the data store.
    private void savePlayerColor(UUID playerUUID, String colorHex) {
        playerColors.put(playerUUID, colorHex);
        // Saves the data to the JSON file asynchronously to prevent blocking the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(this, this::saveData);
    }

    private void removePlayerColor(UUID playerUUID) {
        playerColors.remove(playerUUID);
        Bukkit.getScheduler().runTaskAsynchronously(this, this::saveData);
    }

    // Maps color names to their corresponding hex codes.
    private String getColorFromMap(String colorName) {
        return switch (colorName) {
            case "black" -> "#000000";
            case "dark_blue" -> "#0000AA";
            case "dark_green" -> "#00AA00";
            case "dark_aqua" -> "#00AAAA";
            case "dark_red" -> "#AA0000";
            case "dark_purple" -> "#AA00AA";
            case "gold" -> "#FFAA00";
            case "gray" -> "#AAAAAA";
            case "dark_gray" -> "#555555";
            case "blue" -> "#5555FF";
            case "green" -> "#55FF55";
            case "aqua" -> "#55FFFF";
            case "red" -> "#FF5555";
            case "light_purple" -> "#FF55FF";
            case "yellow" -> "#FFFF55";
            case "white" -> "#FFFFFF";
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "reset");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
        }
        return List.of();
    }

    private String getMessage(String key, Map<String, String> replacements) {
        String message = getConfig().getString("messages." + key, key);
        // Replaces placeholders in the message with values from the `replacements` map.
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }
}