# World Backup - Modrinth page copy

Source of truth for the text on https://modrinth.com/mod/fayber-world-backup.
Update here first, then run `modrinth copy fayber-world-backup modrinth-page.md`.

## description

Automatic zip backups of your world, on a schedule or with a command. Works in singleplayer and on servers.

## body

# World Backup

Makes zip backups of your world every so often, or whenever you ask for one. It works in singleplayer and on dedicated servers.

## Features

- Scheduled backups with a configurable interval, or manual only.
- Backups are consistent: the world is saved and copied first, then zipped in the background.
- Keeps the newest backups and deletes older ones, with a configurable limit.
- Scheduled backups can pause while nobody is online.
- Chat messages when a backup starts and finishes, to everyone or to operators only.
- Settings screen through Mod Menu, using Modern Config if it is installed and Cloth Config otherwise.

## Commands

- `/backup create [name]` starts a backup now.
- `/backup status` shows the schedule, the last backup and how much space the backups use.

Operators and the singleplayer host can use them, even with cheats off.

## Notes

- Backups are written to `backups/<world name>/` in the game or server folder.
- The game pauses for as long as it takes to copy the world once, which is a few seconds on large worlds.
- Requires Fabric API.
- Config file: `config/worldbackup.json`.
