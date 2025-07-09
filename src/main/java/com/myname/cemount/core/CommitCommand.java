package com.myname.cemount.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.Deflater;

public class CommitCommand {
    private static final String CEM_DIR        = ".cemount";
    private static final String OBJECTS_SUBDIR = "objects";
    private static final String REFS_DIR       = "refs";
    private static final String HEADS_DIR      = "heads";
    private static final String HEAD_FILE      = "HEAD";
    private static final String INDEX_TXT      = "index.txt";

    private static final String ECHO_FILE      = "ECHO";

    public static void execute(String[] args) throws IOException {
        // Require exactly: cem commit -m "some message"
        if (args.length < 2 || !args[0].equals("-m")) {
            System.err.println("Usage: cem commit -m \"commit message\"");
            return;
        }
        String commitMessage = args[1];

        Path repoRoot  = Paths.get("").toAbsolutePath().normalize();
        Path cemDir    = repoRoot.resolve(CEM_DIR);
        Path objects   = cemDir.resolve(OBJECTS_SUBDIR);
        Path refsHeads = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR);
        Path headFile  = cemDir.resolve(HEAD_FILE);
        Path indexTxt  = cemDir.resolve(INDEX_TXT);

        Path echoDir   = cemDir.resolve(ECHO_FILE);

        if(Files.notExists(echoDir)){
            Files.createDirectories(echoDir);
        }

        if (Files.notExists(headFile)) {
            try {
                Files.writeString(headFile, "ref: refs/heads/main\n", StandardCharsets.US_ASCII);
            } catch (IOException e) {
                System.err.println("cem commit: failed to initialize HEAD: " + e.getMessage());
                return;
            }
        }

        if (Files.notExists(cemDir) ||
                Files.notExists(objects) ||
                Files.notExists(refsHeads) ||
                Files.notExists(headFile))
        {
            System.err.println("cem commit: no repository found. Run `cem init` first.");
            return;
        }

        List<Staged> staged;
        try {
            if (Files.notExists(indexTxt)) {
                System.err.println("cem commit: nothing to commit (no staged files)");
                return;
            }
            staged = readIndex(indexTxt);
            if (staged.isEmpty()) {
                System.err.println("cem commit: nothing to commit (index is empty)");
                return;
            }
        } catch (IOException e) {
            System.err.println("cem commit: cannot read index.txt: " + e.getMessage());
            return;
        }

        long nowEpoch = Instant.now().getEpochSecond();
        StringBuilder body = new StringBuilder();
        body.append("timestamp: ").append(nowEpoch).append('\n');
        body.append("message:   ").append(commitMessage).append('\n');
        body.append('\n');
        for (Staged s : staged) {
            body.append(s.sha).append(' ').append(s.path).append('\n');
        }
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        String header = "commit " + bodyBytes.length + "\0";
        byte[] hdrBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] store    = new byte[hdrBytes.length + bodyBytes.length];
        System.arraycopy(hdrBytes, 0, store, 0, hdrBytes.length);
        System.arraycopy(bodyBytes, 0, store, hdrBytes.length, bodyBytes.length);

        String commitSha = sha1Hex(store);
        String dirName   = commitSha.substring(0, 2);
        String fileName  = commitSha.substring(2);
        Path commitDir   = echoDir.resolve(dirName);
        Path commitFile  = commitDir.resolve(fileName);

        //Write it zlib‐compressed under .cemount/objects/xx/yyyy...
        try {
            if (Files.notExists(commitFile)) {
                Files.createDirectories(commitDir);
                byte[] compressed = zlibCompress(store);
                Files.write(commitFile, compressed);
            }
        } catch (IOException e) {
            System.err.println("cem commit: cannot write commit object: " + e.getMessage());
            return;
        }

        String headContents;
        try {
            headContents = Files.readString(headFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            System.err.println("cem commit: cannot read HEAD: " + e.getMessage());
            return;
        }
        if (headContents.startsWith("ref: ")) {
            Path branchRef = cemDir.resolve(headContents.substring(5).trim());
            try {
                Files.writeString(branchRef, commitSha + "\n", StandardCharsets.US_ASCII);
            } catch (IOException e) {
                System.err.println("cem commit: cannot update branch ref: " + e.getMessage());
                return;
            }
            System.out.println("[master " + commitSha.substring(0, 7) + "] " + commitMessage);
        } else {
            try {
                Files.writeString(headFile, commitSha + "\n", StandardCharsets.US_ASCII);
            } catch (IOException e) {
                System.err.println("cem commit: cannot update HEAD: " + e.getMessage());
                return;
            }
            System.out.println("[detached HEAD " + commitSha.substring(0, 7) + "] " + commitMessage);
        }

        try {
            Files.writeString(indexTxt, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("cem commit: warning: could not clear index.txt: " + e.getMessage());
        }
    }

    private static List<Staged> readIndex(Path indexTxt) throws IOException {
        List<Staged> out = new ArrayList<>();
        for (String line : Files.readAllLines(indexTxt, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split(" ", 2);
            if (parts.length < 2) continue;
            out.add(new Staged(parts[0], parts[1]));
        }
        return out;
    }
    private static class Staged {
        final String sha;
        final String path;
        Staged(String sha, String path) {
            this.sha  = sha;
            this.path = path;
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
    private static byte[] zlibCompress(byte[] input) throws IOException {
        Deflater def = new Deflater();
        def.setInput(input);
        def.finish();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            while (!def.finished()) {
                int count = def.deflate(buf);
                baos.write(buf, 0, count);
            }
            return baos.toByteArray();
        } finally {
            def.end();
        }
    }
    @SuppressWarnings("unused")
    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
