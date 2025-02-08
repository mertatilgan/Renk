package me.kikaru.renk;

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
import java.sql.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Renk extends JavaPlugin implements TabExecutor, TabCompleter {

    private static final String DB_PATH = "plugins/Renk/user_colors.db";
    private Connection connection;

    @Override
    public void onEnable() {
        Objects.requireNonNull(this.getCommand("renk")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("renk")).setTabCompleter(this);

        // Ensure the database and connection are set up
        setupDatabase();

        // Listen for player join events to apply their color
        Bukkit.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                String colorHex = getPlayerColor(player.getUniqueId());

                // If a color exists in the database, apply it to the player's display name
                if (colorHex != null) {
                    setPlayerColor(player, colorHex);
                }
            }
        }, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            return handleSelfColorChange(sender, args[0]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return handleAdminSetColor(sender, args[1], args[2]);
        } else {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            String usageMessage = "Usage: /renk <color:yellow>#HEX</color> or /renk set <color:green><player></color> <color:yellow>#HEX</color>";
            sender.sendMessage(miniMessage.deserialize(usageMessage));
            return true;
        }
    }

    private boolean handleSelfColorChange(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            String message = "<color:red>Only players can use this command!</color>";
            sender.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        String hex = input.startsWith("#") ? input : getColorFromMap(input.toLowerCase());

        if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            String message = "<color:red>Invalid color!</color> Use a <color:yellow>hex code</color> or one of: <color:yellow>black, dark_blue, dark_green, etc.</color>";
            sender.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        setPlayerColor(player, hex);
        savePlayerColor(player.getUniqueId(), hex);

        // Use MiniMessage to apply color to the word 'changed'
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String message = "Your color has been changed to<color:" + hex + "> " + input + "</color>!";
        player.sendMessage(miniMessage.deserialize(message));

        return true;
    }

    private boolean handleAdminSetColor(CommandSender sender, String targetName, String input) {
        if (!sender.hasPermission("renk.set")) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            String message = "<color:red>You don't have permission to use this command!</color>";
            sender.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            String message = "<color:red>Player not found!</color>";
            sender.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        String hex = input.startsWith("#") ? input : getColorFromMap(input.toLowerCase());

        if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) {
            MiniMessage miniMessage = MiniMessage.miniMessage();
            String message = "<color:red>Invalid color!</color> Use a <color:yellow>hex code</color> or one of: <color:yellow>black, dark_blue, dark_green, etc.</color>";
            sender.sendMessage(miniMessage.deserialize(message));
            return true;
        }

        setPlayerColor(target, hex);
        savePlayerColor(target.getUniqueId(), hex);

        // Use MiniMessage to apply color to the word 'changed'
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String message = "Set " + target.getName() + "'s color to<color:" + hex + "> " + input + "</color>!";
        sender.sendMessage(miniMessage.deserialize(message));

        String targetMessage = "Your color has been changed by an admin to<color:" + hex + "> " + input + "</color>!";
        target.sendMessage(miniMessage.deserialize(targetMessage));

        return true;
    }


    private void setPlayerColor(Player player, String hex) {
        if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) {
            player.sendMessage("Your color setting is invalid or missing, setting to default.");
            hex = "#FFFFFF"; // Default color (white)
        }

        MiniMessage miniMessage = MiniMessage.miniMessage();
        Component coloredName = miniMessage.deserialize("<color:" + hex + ">" + player.getName() + "</color>");

        // Set the display name in chat
        player.displayName(coloredName);

        // Set the tab list name
        player.playerListName(coloredName);

        // Set the name below the head
        Bukkit.getScheduler().runTask(this, () -> player.customName(coloredName));
    }

    private void setupDatabase() {
        File dbFile = new File(DB_PATH);
        File parentDir = dbFile.getParentFile();

        // Ensure the parent directory exists
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                getLogger().severe("Failed to create plugin folder!");
                return;
            }
        }

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_colors (uuid TEXT PRIMARY KEY, color TEXT);");
            getLogger().info("SQLite database connected and table created (if not exists).");
        } catch (SQLException e) {
            getLogger().severe("Error while setting up the database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void savePlayerColor(UUID playerUUID, String colorHex) {
        try {
            PreparedStatement stmt = connection.prepareStatement("INSERT OR REPLACE INTO player_colors (uuid, color) VALUES (?, ?)");
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, colorHex);
            stmt.executeUpdate();
        } catch (SQLException e) {
            getLogger().severe("Error saving player color: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getPlayerColor(UUID playerUUID) {
        String colorHex = null;
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT color FROM player_colors WHERE uuid = ?");
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                colorHex = rs.getString("color");
            }
        } catch (SQLException e) {
            getLogger().severe("Error loading player color: " + e.getMessage());
            e.printStackTrace();
        }
        return colorHex;
    }

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
            return List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
        }
        return List.of();
    }
}
