package com.myname.cemount.commands;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

public class AddCommand {
    public static void execute(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: cem add <paths>…");
            return;
        }
        for (String p : args) {
            Path file = Path.of(p);
            if (!Files.exists(file)) {
                System.err.println("cem add: pathspec '" + p + "' did not match any files");
                continue;
            }
            // your logic to hash the blob, write to .cemount/objects, update index…
            System.out.println("added " + p);
        }
    }
    private String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class Commit {
        private final String sha;
        private final Instant timestamp;
        private final String message;
        private final Map<String, String> tree; // path → object SHA

        public Commit(String sha, Instant timestamp, String message, Map<String,String> tree) {
            this.sha = sha;
            this.timestamp = timestamp;
            this.message = message;
            this.tree = tree;
        }

        public String getSha() { return sha; }
        public Instant getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
        public Map<String,String> getTree() { return tree; }
    }
}
