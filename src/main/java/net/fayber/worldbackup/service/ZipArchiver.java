package net.fayber.worldbackup.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipArchiver {

    /** Thrown when the cancel flag is set while zipping, usually because the server is stopping. */
    public static class Cancelled extends IOException {
        public Cancelled() {
            super("cancelled");
        }
    }

    /**
     * Zips sourceDir into zipFile with every entry under rootName/. The archive is written to a
     * .tmp file first and renamed when complete, so a crash never leaves a half-written .zip.
     */
    public static void createZip(Path sourceDir, Path zipFile, String rootName, int level, BooleanSupplier cancelled) throws IOException {
        Files.createDirectories(zipFile.getParent());
        Path tmp = zipFile.resolveSibling(zipFile.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                zos.setLevel(Math.max(0, Math.min(9, level)));
                byte[] buffer = new byte[64 * 1024];
                Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (cancelled.getAsBoolean()) {
                            throw new Cancelled();
                        }
                        String name = rootName + "/" + sourceDir.relativize(file).toString().replace('\\', '/');
                        ZipEntry entry = new ZipEntry(name);
                        entry.setLastModifiedTime(attrs.lastModifiedTime());
                        zos.putNextEntry(entry);
                        try (InputStream in = Files.newInputStream(file)) {
                            int n;
                            while ((n = in.read(buffer)) > 0) {
                                if (cancelled.getAsBoolean()) {
                                    throw new Cancelled();
                                }
                                zos.write(buffer, 0, n);
                            }
                        }
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.move(tmp, zipFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
