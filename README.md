# Renk - A Simple Minecraft Color Plugin

Renk is a Minecraft plugin that allows players to customize their display name color.  It uses hex color codes and translates them to the closest supported Minecraft color for team-based name tag coloring.

## Features

- Players can change or reset their color using commands.
- Admins can set the color of other players.
- Player colors are saved to a JSON file (`plugins/Renk/player_colors.json`) and applied when they join.
- Supports hex color codes (e.g., `#FF0000`) and predefined color names (e.g., `red`, `blue`, `green`).
- Utilizes the [MiniMessage](https://docs.advntr.dev/) library for formatting color codes and messages.
- Uses Scoreboard Teams to approximate the player's display name color on their nametag.
- Configuration file (`config.yml`) for customizing messages.

## Commands

- `/renk <#hex | color_name>`: Changes the player's own color to the specified hex color or predefined color name.  Examples: `/renk #FF0000`, `/renk red`.
- `/renk set <player> <#hex | color_name>`: Allows admins to set the color of another player. Examples: `/renk set Kikaru #00FF00`, `/renk set Kikaru green`.
- `/renk reset`: Resets the player's own color to the default.
- `/renk reset <player>`: Allows admins to reset another player's color.

### Predefined Colors
The following color names can be used instead of hex codes: `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`, `dark_purple`, `gold`, `gray`, `dark_gray`, `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow`, `white`.

### Permissions

- `renk.use`: Allows the use of the `/renk` command.
- `renk.set.other`: Allows the use of the `/renk set` command to change the color of other players.
- `renk.reset.other`: Allows the use of the `/renk reset` command to reset the color of other players.

## Configuration
The plugin uses a `config.yml` file located in the `plugins/Renk/` directory to store configurable messages.  You can customize the messages that are displayed to players by modifying this file.  Use color codes with MiniMessage.


## How to Contribute
1. Fork the repository.
2. Create a new branch for your feature or fix.
3. Submit a pull request with a detailed explanation of your changes.

## License
This project is licensed under the GPL-3.0 License.

## Support
If you encounter any issues or need further assistance, feel free to open an issue on the GitHub repository.
