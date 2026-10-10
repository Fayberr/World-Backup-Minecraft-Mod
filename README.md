# World Backup

Fabric mod for Minecraft **26.2**.

Makes zip backups of your world on a schedule or on demand. It works in
singleplayer and on dedicated servers.

## How a backup works

The world is saved and copied to a temporary snapshot folder on the server thread,
so region files cannot change while they are being copied. The snapshot is then
zipped on a worker thread, written to a `.tmp` file first and renamed when it is
complete. This means the game stalls for about as long as it takes to copy the
world once, and large worlds can stall for a few seconds. If the server stops
during a backup, the zip is cancelled and the temporary files are removed.

Backups go to `<game folder>/backups/<world folder name>/`, named with the date and
time. The oldest ones are deleted once the limit is reached.

## Commands

Operators and the singleplayer host (even with cheats off) can use them. Guests on
a LAN world cannot.

- `/backup create [name]`: start a backup now.
- `/backup status`: whether a backup is running, the schedule, the last backup,
  and how many backups are stored and how much space they use.

## Configuration

Settings are stored in `config/worldbackup.json`: interval in minutes (`0` for
manual only), compression level, number of backups to keep (`0` keeps all),
whether to skip scheduled backups while no one is online, the backup folder, and
chat messages (to everyone or to operators only). If Mod Menu is installed there is
a settings screen, built with Modern Config when that is installed and with Cloth
Config otherwise.

## Building

Needs JDK 25. Run `./gradlew build`. The Modern Config dependency is resolved from
`mavenLocal()`, so publish it first with `./gradlew publishToMavenLocal` in the
Modern Config repository.

## License

GPL-3.0-or-later.
