package net.fayber.worldbackup.config;

public class BackupConfig {
    public boolean enabled = true;
    public int backupIntervalMinutes = 60;   // 0 = manual backups only
    public int compressionLevel = 6;         // 0 (store only) to 9 (maximum compression)
    public int maxBackupsToKeep = 10;        // Max archives before pruning oldest
    public boolean pauseIfNoPlayers = true;  // Pause scheduler when 0 players on dedicated server
    public String backupDirectory = "backups"; // Storage folder relative to root
    public boolean notifyPlayers = true;     // Broadcast chat messages on start/completion
    public boolean notifyOpsOnly = false;    // Limit broadcasts to operators
}
