package com.myname.cemount.commands;

import com.myname.cemount.server.ObjectUtils;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.Deflater;

public class AddCommand {
    private static final String CEM_DIR           = ".cemount";
    private static final String OBJECTS_SUBDIR    = "objects";
    private static final String INDEX_TXT         = "index.txt";

    public static void execute(String[] args) {
        if (args.length > 0) {
            System.err.println("Usage: cem add");
            return;
        }
        Path repoRoot = Paths.get("").toAbsolutePath().normalize();
        Path cemDir   = repoRoot.resolve(CEM_DIR);
        Path objects  = cemDir.resolve(OBJECTS_SUBDIR);
        Path indexTxt = cemDir.resolve(INDEX_TXT);

        if (Files.notExists(cemDir) || Files.notExists(objects)) {
            System.err.println("cem add: no repository found. Run `cem init` first.");
            return;
        }

        try {
            // Walk every file under repoRoot
            try (Stream<Path> allPaths = Files.walk(repoRoot)) {
                allPaths
                .filter(Files::isRegularFile)
                // ...and skip anything under .cemount itself
                .filter(path -> !path.startsWith(cemDir))
                .forEach(fileOnDisk -> {
                    try {
                        byte[] content = Files.readAllBytes(fileOnDisk);
                        String header   = "blob " + content.length + "\0";
                        byte[] hdrBs    = header.getBytes(StandardCharsets.UTF_8);

                        byte[] store = new byte[hdrBs.length + content.length];
                        System.arraycopy(hdrBs,0, store,0, hdrBs.length);
                        System.arraycopy(content,0, store, hdrBs.length, content.length);

                        String blobSha = sha1Hex(store);
                        String dirName = blobSha.substring(0, 2);
                        String fileName= blobSha.substring(2);

                        Path objectDir  = objects.resolve(dirName);
                        Path objectFile = objectDir.resolve(fileName);
                        if (Files.notExists(objectFile)) {
                            Files.createDirectories(objectDir);
                            byte[] compressed = ObjectUtils.zlibCompress(store);
                            Files.write(objectFile, compressed);
                        }
                        Path relPath = repoRoot.relativize(fileOnDisk.toAbsolutePath().normalize());
                        String rel    = relPath.toString().replace(File.separatorChar, '/');
                        System.out.printf("added %s%n", rel);

                        List<String> lines = Files.exists(indexTxt)
                                ? Files.readAllLines(indexTxt, StandardCharsets.UTF_8)
                                : new ArrayList<>();

                        List<String> keep = new ArrayList<>();
                        for (String line : lines) {
                            int sp = line.indexOf(' ');
                            if (sp < 0) continue;
                            String existingPath = line.substring(sp + 1);
                            if (!existingPath.equals(rel)) {
                                keep.add(line);
                            }
                        }
                        keep.add(blobSha + " " + rel);

                        Files.createDirectories(indexTxt.getParent());
                        Files.write(indexTxt, keep, StandardCharsets.UTF_8);

                    } catch (IOException e) {
                        System.err.printf("cem add: I/O error adding %s: %s%n",
                                fileOnDisk, e.getMessage());
                    } catch (Exception e) {
                        System.err.printf("cem add: error for %s: %s%n", fileOnDisk, e.getMessage());
                    }
                });
            }

        } catch (IOException e) {
            System.err.println("cem add: failed to traverse repository: " + e.getMessage());
        }
    }

    private static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
