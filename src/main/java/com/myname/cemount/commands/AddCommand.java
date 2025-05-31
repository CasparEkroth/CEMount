package com.myname.cemount.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.Deflater;

public class AddCommand {
    private static final String CEM_DIR = ".cemount";
    private static final String OBJECTS_SUBDIR = "objects";

    public static void execute(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: cem add <paths>…");
            return;
        }

        // Assume the user runs "cem add" from the repo root,
        Path repoRoot = Paths.get("").toAbsolutePath().normalize();
        Path cemDir = repoRoot.resolve(CEM_DIR);
        Path objectsDir = cemDir.resolve(OBJECTS_SUBDIR);

        if (Files.notExists(cemDir) || Files.notExists(objectsDir)) {
            System.err.println("cem add: error: no CEMount repository found. Have you run `cem init`?");
            return;
        }

        for (String p : args) {
            Path fileOnDisk = Paths.get(p);
            if (!Files.exists(fileOnDisk) || !Files.isRegularFile(fileOnDisk)) {
                System.err.println("cem add: pathspec '" + p + "' did not match any files");
                continue;
            }
            try{
                byte[] content = Files.readAllBytes(fileOnDisk);
                String header = "blob" + content.length + "\0";
                byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

                byte[] storeBytes = new byte[headerBytes.length + content.length];
                System.arraycopy(headerBytes,0,storeBytes,0,headerBytes.length);
                System.arraycopy(content,0,storeBytes,headerBytes.length,content.length);

                String shaHex = sha1Hex(storeBytes);

                //Map SHA‐1 to object‐path: .cemount/objects/xx/yyyy
                String dirName  = shaHex.substring(0, 2);
                String fileName = shaHex.substring(2);
                Path objectDir  = objectsDir.resolve(dirName);
                Path objectFile = objectDir.resolve(fileName);

                if (Files.exists(objectFile)) {
                    // Blob is already in the object store; no need to rewrite
                    System.out.printf("added %s (blob already exists)%n", p);
                    continue;
                }
                Files.createDirectories(objectDir);
                byte[] compressed = zlibCompress(storeBytes);
                Files.write(objectFile, compressed);

                System.out.println("added " + p);
            }catch (IOException e) {
                System.err.printf("cem add: failed to add %s: %s%n", p, e.getMessage());
            }catch (Exception e){
                System.err.printf("cem add: unexpected error for %s: %s%n", p, e.getMessage());
            }
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
            throw new RuntimeException("Failed to compute SHA-1: " + e.getMessage(), e);
        }
    }
    private static byte[] zlibCompress(byte[] input) throws IOException {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } finally {
            deflater.end();
        }
    }

}
