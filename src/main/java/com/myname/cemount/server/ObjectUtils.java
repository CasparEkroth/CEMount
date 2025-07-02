package com.myname.cemount.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.InflaterInputStream;

public class ObjectUtils {
    private static final int    BUFFER_SIZE    = 8192;

    public static byte[] zlibDecompress(byte[] compressed) throws IOException {
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[BUFFER_SIZE];
            int r;
            while ((r = in.read(buf)) != -1){
                if(r > 0) {
                    out.write(buf, 0, r);
                }
            }
            return out.toByteArray();
        }
    }

    public static String extractFileContents(byte[] compressed) throws IOException {
        byte[] data = zlibDecompress(compressed);
        int idx = 0; // header
        while (idx < data.length && data[idx] != 0){
            idx++;
        }
        return new String(data,idx +1, data.length - idx - 1, StandardCharsets.UTF_8);
    }
    public static void printObject(Path objPath) throws IOException {
        byte[] compressed = Files.readAllBytes(objPath);
        String contents = extractFileContents(compressed);
        System.out.println("----- file contents -----");
        System.out.println(contents);
    }

    public static String buildSha1(Path objectPath) throws IOException {
        byte[] compressed = Files.readAllBytes(objectPath);
        byte[] fullBlob = zlibDecompress(compressed);
        return sha1Hex(fullBlob);
    }

    public static String buildSha1(Path base, Path file) throws IOException {
        String sha = buildSha1(file);
        // Optional check:
        Path rel = base.relativize(file);
        String expected = rel.getName(0).toString() + rel.getName(1).toString();
        if (!sha.equals(expected)) {
            System.err.printf("[ObjectUtils] checksum mismatch: path suggests %s, computed %s%n", expected, sha);
        }
        return sha;
    }
    /**
     * Compute SHA-1 digest and return lowercase hex string.
     */
    private static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }
    /*----------------------------------------------------------------------*/
    /*   Server-side storage of received objects (PushCommand helper)       */
    /*----------------------------------------------------------------------*/

    /**
     * Store a raw zlib-compressed object buffer under .cemount/objects/xx/yy...
     * Automatically creates subdirectories.
     * @param objectsRoot Path to repoRoot/.cemount/objects
     * @param rawCompressed must be the exact bytes read from client (compressed "blob <size>\0data").
     * @return the SHA-1 hash of the full blob (matching Git), lowercase hex
     * @throws IOException on I/O errors
     */
    public static String storeObject(Path objectsRoot, byte[] rawCompressed) throws IOException {
        byte[] full = zlibDecompress(rawCompressed);
        String sha = sha1Hex(full);

        String dir = sha.substring(0, 2);
        String name = sha.substring(2);
        Path targetDir = objectsRoot.resolve(dir);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(name);

        Files.write(targetFile, rawCompressed, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        return sha;
    }
}
