package net.fayber.worldbackup.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipArchiver {

    public static void createZipArchive(Path sourceDir, File zipFile, int compressionLevel) throws IOException {
        if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            zos.setLevel(Math.max(0, Math.min(9, compressionLevel)));
            zipDirectory(sourceDir.toFile(), sourceDir.toFile(), zos);
        }
    }

    private static void zipDirectory(File rootDir, File currentDir, ZipOutputStream zos) throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        byte[] buffer = new byte[8192];
        for (File file : files) {
            // Skip lock files and active backup destination to avoid recursive archiving
            if (file.getName().equals("session.lock") || file.getName().equals("backups")) {
                continue;
            }

            if (file.isDirectory()) {
                zipDirectory(rootDir, file, zos);
            } else {
                String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }
}
