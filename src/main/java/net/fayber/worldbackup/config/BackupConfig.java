package net.fayber.worldbackup.config;

public class BackupConfig {
    public boolean enabled = true;
    public int backupIntervalMinutes = 60;     // 0 = manual backups only
    public int compressionLevel = 6;           // 0 (store only) to 9 (maximum compression)
    public int maxBackupsToKeep = 10;          // 0 = never delete old backups
    public boolean pauseIfNoPlayers = true;    // skip scheduled backups while the server is empty
    public String backupDirectory = "backups"; // relative paths resolve against the game or server folder
    public boolean notifyPlayers = true;       // chat messages when a backup starts and finishes
    public boolean notifyOpsOnly = false;      // limit those messages to operators and the singleplayer host
}
