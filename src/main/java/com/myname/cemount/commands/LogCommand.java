package com.myname.cemount.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.Inflater;

public class LogCommand {

    private static final String CEM_DIR        = ".cemount";
    private static final String OBJECTS_SUBDIR = "objects";
    private static final String REFS_DIR       = "refs";
    private static final String HEADS_DIR      = "heads";
    private static final String HEAD_FILE      = "HEAD";

    public static void execute(String[] args) {
        // Expect no arguments
        if (args.length != 0) {
            System.err.println("Usage: cem log");
            return;
        }

        Path repoRoot  = Paths.get("").toAbsolutePath().normalize();
        Path cemDir    = repoRoot.resolve(CEM_DIR);
        Path objects   = cemDir.resolve(OBJECTS_SUBDIR);
        Path refsHeads = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR);
        Path headFile  = cemDir.resolve(HEAD_FILE);

         if (Files.notExists(cemDir) || Files.notExists(objects) || Files.notExists(refsHeads) || Files.notExists(headFile)) {
             System.err.println("cem log: repository not found. Run `cem init` first.");
             return;
        }

        String headContents;
        try {
            headContents = Files.readString(headFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            System.err.println("cem log: cannot read HEAD: " + e.getMessage());
            return;
        }


        String commitSha = null;
        if (headContents.startsWith("ref: ")) {
            Path branchRef = cemDir.resolve(headContents.substring(5).trim());
            try {
                commitSha = Files.readString(branchRef, StandardCharsets.US_ASCII).trim();
            } catch (IOException e) {
                System.err.println("cem log: cannot read branch ref: " + e.getMessage());
                return;
            }
        } else {
            commitSha = headContents;
        }

         if (commitSha == null || commitSha.length() != 40) {
             System.err.println("cem log: invalid commit SHA.");
             return;
        }

        String dirName  = commitSha.substring(0, 2);
        String fileName = commitSha.substring(2);
        Path commitPath = objects.resolve(dirName).resolve(fileName);

         if (Files.notExists(commitPath)) {
             System.err.println("cem log: commit object not found for SHA: " + commitSha);
             return;
        }

        byte[] compressed;
        try {
            compressed = Files.readAllBytes(commitPath);
        } catch (IOException e) {
            System.err.println("cem log: cannot read commit object: " + e.getMessage());
            return;
        }

        byte[] storeBytes;
        try {
            storeBytes = zlibDecompress(compressed);
        } catch (IOException e) {
            System.err.println("cem log: decompression failed: " + e.getMessage());
            return;
        }

        int nullIdx = -1;
        for (int i = 0; i < storeBytes.length; i++) {
            if (storeBytes[i] == 0) {
                nullIdx = i;
                break;
            }
        }

         if (nullIdx < 0) {
             System.err.println("cem log: malformed commit object (no null).");
             return;
        }
        String commitContent = new String(
                storeBytes,
                nullIdx + 1,
                storeBytes.length - (nullIdx + 1),
                StandardCharsets.UTF_8
        );

        String[] lines = commitContent.split("\n");

         if (lines.length < 2 || !lines[0].startsWith("timestamp: ") || !lines[1].startsWith("message:   ")) {
             System.err.println("cem log: malformed commit body.");
             return;
         }

        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(lines[0].substring(11).trim());
        } catch (Exception e) {
            System.err.println("cem log: invalid timestamp.");
            return;
        }

        String message = lines[1].substring(11);

        int i = 2;
        while (i < lines.length && !lines[i].isBlank()) {
            i++;
        }
        List<String> files = new ArrayList<>();
        for (int j = i + 1; j < lines.length; j++) {
            String ln = lines[j].trim();
            if (ln.isEmpty()) continue;
            int sp = ln.indexOf(' ');
            if (sp < 0) continue;
            files.add(ln.substring(sp + 1));
        }

        // --- OUTPUT ---
        System.out.println("commit " + commitSha);
        String isoDate = DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(epochSeconds));
        System.out.println("Date:   " + isoDate);
        System.out.println();
        System.out.println("    " + message);
        System.out.println();
        for (String f : files) {
            System.out.println("    " + f);
        }
    }

    private static byte[] zlibDecompress(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) break; // no more data
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            inflater.end();
        }
    }
}
