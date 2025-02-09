# Renk - Minecraft Color Plugin
Renk is a Minecraft plugin that allows players to change their name color.

## Features
- Players can change or reset their color using a command.
- Admins can set the color of other players.
- Player colors are saved to an JSON file and applied when they join.
- Predefined color options are available (e.g., black, blue, green, etc.).
- The plugin utilizes the [MiniMessage](https://docs.advntr.dev/) library to format color codes and messages.

## Planned Features
- Color approximated team generation to modify name tag colors.

## Commands
- `/renk <#hex>`: Changes the player's color to the specified hex color or predefined color.
- `/renk set <player> <#hex>`: Allows admins to set the color of another player.
- `/renk reset>`: Allows users to reset their own color.
- `/renk reset <player>`: Allows admins to reset the colour of another player.

### Permissions
- `renk.use`: Allows the use of the plugin itself.
- `renk.set.other`: Allows the change of color for other players.
- `renk.reset.other`: Allows reseting another player's color.

## How to Contribute
1. Fork the repository.
2. Create a new branch for your feature or fix.
3. Submit a pull request with a detailed explanation of your changes.

## License
This project is licensed under the GPL-3.0 License.

## Support
If you encounter any issues or need further assistance, feel free to open an issue on the GitHub repository.
