# Renk - Minecraft Color Plugin
Renk is a Minecraft plugin that allows players to change their display name color in the game. Players can choose from a variety of predefined colors or specify their own hex color codes. Admins can also set the colors of other players.

## Features
- Players can change their display name color using a command.
- Admins can set the color of other players.
- Player colors are saved to an SQLite database and applied when they join.
- Predefined color options are available (e.g., black, blue, green, etc.).
- The plugin utilizes the [MiniMessage](https://docs.advntr.dev/) library to format color codes and messages.

## Planned Features
- Configurable messages through a `config.yml` file for customizing plugin messages (e.g., error and success messages) and the ability to set predefined color names with their associated hex values.

## Commands
- `/renk <#hex>`: Changes the player's color to the specified hex color or predefined color.
- `/renk set <player> <#hex>`: Allows admins to set the color of another player.

### Permissions
- `renk.use`: Allows the use of the plugin itself.
- `renk.set`: Allows the change of color for other players.

## Setup and Installation
1. Download and place the `Renk` plugin `.jar` file into the `plugins` folder of your Paper server.
2. The plugin will automatically create an SQLite database (`user_colors.db`) in the `plugins/Renk/` folder to store player colors.

## How to Contribute
1. Fork the repository.
2. Create a new branch for your feature or fix.
3. Submit a pull request with a detailed explanation of your changes.

## License
This project is licensed under the MIT License.

## Support
If you encounter any issues or need further assistance, feel free to open an issue on the GitHub repository.
