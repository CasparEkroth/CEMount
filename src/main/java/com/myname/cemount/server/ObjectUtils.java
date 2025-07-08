package com.myname.cemount.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ObjectUtils {
    private static final int    BUFFER_SIZE    = 8192;
    private static final String REFS_DIR       = "refs";
    private static final String HEADS_DIR      = "heads";
    private static final String HEAD_FILE      = "HEAD";
    private static final String OBJECTS        = "objects";

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
    /*
    public static String extractFileContents(byte[] compressed) throws IOException {
        byte[] data = zlibDecompress(compressed);
        int idx = 0; // header
        while (idx < data.length && data[idx] != 0){
            idx++;
        }
        return new String(data,idx +1, data.length - idx - 1, UTF_8);
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
    */
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

    public static Map<String, String> parseRemotes(Path configPath) {
        Map<String, String> map = new HashMap<>();
        String key = null;
        try {
            for (String raw : Files.readAllLines(configPath, UTF_8)) {
                String line = raw.trim();
                if (line.startsWith("[remote")) {
                    int firstQuote = line.indexOf('"');
                    int secondQuote = line.indexOf('"', firstQuote + 1);
                    if (firstQuote != -1 && secondQuote != -1 && secondQuote > firstQuote) {
                        key = line.substring(firstQuote + 1, secondQuote);
                    }
                } else if (line.startsWith("url") && key != null) {
                    int equalsIndex = line.indexOf('=');
                    if (equalsIndex != -1) {
                        String url = line.substring(equalsIndex + 1).trim();
                        map.put(key, url);
                        key = null;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("cem: failed to read remotes: " + e.getMessage());
        }

        return map;
    }

    public static String getRef(Path cemDir, String branch) throws IOException {
        Path refPath = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(branch);
        return Files.readString(refPath, UTF_8).trim();
    }

    public static String getBranch(Path cemDir) throws IOException{
        Path headPath = cemDir.resolve(HEAD_FILE);
        String[] parts = Files.readString(headPath, UTF_8).split("/");
        return parts[parts.length - 1].trim();
    }

    public static String[] parseRemote(String remoteUrl){
        // parse tcp://host:port/repoName
        String[] parts = new String[3];
        if(remoteUrl.startsWith("tcp")){
            String without = remoteUrl.substring("tcp://".length());
            int idx1   = without.indexOf(':');
            int slash  = without.lastIndexOf('/');
            String repoName = (slash >= idx1)
                    ? without.substring(slash + 1)
                    : "default";
            String serverPort = without.substring(idx1 + 1,
                    slash < idx1 ? without.length() : slash);
            String serverIP   = without.substring(0, idx1);
            parts[0] = repoName;
            parts[1] = serverPort;
            parts[2] = serverIP;
        }else {
            // handel true Url
            // URI uri = new URI(remoteUrl);
            parts[0] = "";
            parts[1] = "";
            parts[2] = "";
        }
        return parts;
    }

    public static byte[] loadObject(Path cemDir, String sha) throws IOException{
        Path objPath = cemDir.resolve(OBJECTS).resolve(sha.substring(0,2)).resolve(sha.substring(2));
        return Files.readAllBytes(objPath);

    }

    public static List<String> listMissing(Path cemDir,
                                           String haveSha,
                                           String remoteSha) throws IOException {
        Path objectsDir = cemDir.resolve(OBJECTS);
        Set<String> haveSet;
        try (Stream<Path> paths = Files.walk(objectsDir, 2)) {
            haveSet = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }

        Set<String> wantSet = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(remoteSha);

        while (!stack.isEmpty()) {
            String sha = stack.pop();
            if (!wantSet.add(sha)) continue;

            byte[] raw  = loadObject(cemDir, sha);
            byte[] data = zlibDecompress(raw);

            int idx = 0; while (data[idx] != 0) idx++;
            String body = new String(data, idx+1, data.length - (idx+1), UTF_8);

            for (String line : body.split("\n")) {
                if (line.startsWith("tree ") || line.startsWith("parent ")) {
                    stack.push(line.substring(line.indexOf(' ')+1));
                }
            }
        }
        wantSet.removeAll(haveSet);

        return new ArrayList<>(wantSet);
    }

    public static void addToFile(Path filePath, String[] appends) throws IOException {
        List<String> lines = Files.readAllLines(filePath, UTF_8);
        for(String line : appends){
            lines.add(line);
        }
        Set<String> noDupes = new LinkedHashSet<>(lines);
        lines = new ArrayList<>(noDupes);
        Files.write(filePath, lines,UTF_8, StandardOpenOption.CREATE,StandardOpenOption.WRITE);
    }// add a filter for blanc lines

    public static void appendToFile(Path src, Path des) throws IOException {
        List<String> lines = Files.readAllLines(src);
        if(!Files.exists(des)){
            Files.createFile(des);
        }
        Files.write(des,lines, UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }

    public static void updateRef(Path cemDir, String ref, String newHeadSha) throws IOException {
        Path refPath = cemDir.resolve(ref);
        Files.deleteIfExists(refPath);
        Files.createFile(refPath);
        Files.write(refPath,(newHeadSha + "\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
    }

    public static String readObjectText(Path cemDir, String sha) throws IOException {
        byte[] full = zlibDecompress(loadObject(cemDir, sha));
        // skip header (up to the first 0 byte)
        int i = 0;
        while (i < full.length && full[i] != 0) i++;
        return new String(full, i+1, full.length - i - 1, StandardCharsets.UTF_8);
    }

}

