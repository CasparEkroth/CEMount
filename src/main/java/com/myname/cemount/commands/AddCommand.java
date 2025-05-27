package com.myname.cemount.commands;

import com.myname.cemount.core.Repository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class AddCommand {
    private final Repository repo;

    public AddCommand(Repository repo) {
        this.repo = repo;
    }

    public void run(String[] paths) {
        for (String p : paths) {
            try {
                Path file = Path.of(p);
                byte[] data = Files.readAllBytes(file);
                String sha = sha1Hex(data);
                repo.getObjects().put(sha, data);
                repo.getStagedPaths().add(p);
                System.out.println("Added " + p + " as " + sha);
            } catch (IOException e) {
                System.err.println("Error adding " + p + ": " + e.getMessage());
            }
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
}

