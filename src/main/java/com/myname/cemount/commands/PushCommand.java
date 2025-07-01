package com.myname.cemount.commands;

import com.myname.cemount.server.ObjectUtils;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class PushCommand {
    private static final String CEM_DIR    = ".cemount";
    private static final String CONFIG     = "config";
    private static final String REFS_DIR   = "refs";
    private static final String HEADS_DIR  = "heads";
    private static final String OBJECTS    = "objects";
    private static final String HEAD_FILE  = "HEAD";

    public static void execute(String[] args) throws IOException {
        if(args.length != 2){
            System.err.println("usage: cem push <remote> <branch>");
            return;
        }
        String remoteName = args[0];
        String branch = args[1];

        Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
        Path configPath = repoRoot.resolve(CEM_DIR).resolve(CONFIG);
        Map<String, String> remote = parseRemotes(configPath);

        if(!remote.containsKey(remoteName)){
            System.err.printf("fatal: no such remote '%s'%n", remoteName);
            return;
        }
        String remoteUrl = remote.get(remoteName);
        Path cemDir = repoRoot.resolve(CEM_DIR);
        String localSha = resolveLocalRef(cemDir, branch);

        if (localSha == null) {
            System.err.printf("fatal: unknown local branch '%s'%n", branch);
            return;
        }
        List<String> commits = findCommitsToPush(localSha, cemDir);
        if(commits.isEmpty()){
            System.out.println("Everything up-to-date.");
            return;
        }

        if(remoteUrl.startsWith("tcp://")){
            pushOverTcp(remoteUrl, branch, localSha, commits, cemDir);
        }else{
            pushOverFileSystem(remoteUrl, branch, localSha, commits, cemDir);
        }
    }

    private static void pushOverFileSystem(String remoteUrl, String branch, String localSha, List<String> commits, Path cemDir){

        /// ...
    }

    private static void pushOverTcp(String remoteUrl,
                                    String branch,
                                    String localSha,
                                    List<String> commits,
                                    Path cemDir) throws IOException {
        // parse tcp://host:port/repoName
        String without = remoteUrl.substring("tcp://".length());
        int idx1   = without.indexOf(':');
        int slash  = without.lastIndexOf('/');
        String repoName = (slash >= idx1)
                ? without.substring(slash + 1)
                : "default";
        String serverPort = without.substring(idx1 + 1,
                slash < idx1 ? without.length() : slash);
        String serverIP   = without.substring(0, idx1);

        try (Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), UTF_8));
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), UTF_8))
        ) {
            OutputStream rawOut = socket.getOutputStream();
            out.write("PUSH " + repoName + " " + branch + "\n");
            out.flush();

            out.write("COMMITS " + commits.size() + "\n");
            for (String sha : commits) {
                out.write("COMMIT " + sha + "\n");
            }
            out.flush();
            Set<String> toPush = new LinkedHashSet<>(commits);

            for (String commitSha : commits) {
                Path cObj = cemDir.resolve(OBJECTS)
                        .resolve(commitSha.substring(0,2))
                        .resolve(commitSha.substring(2));
                byte[] full = ObjectUtils.zlibDecompress(Files.readAllBytes(cObj));
                int i = 0;
                while (i < full.length && full[i] != 0) i++;
                String body = new String(full, i+1, full.length-i-1, UTF_8);

                for (String line : body.split("\n")) {
                    String[] parts = line.split(" ", 2);
                    if (parts[0].matches("[0-9a-f]{40}")) {
                        toPush.add(parts[0]);
                    }
                    else if (parts[0].equals("tree") && parts.length > 1) {
                        toPush.add(parts[1]);
                    }
                }
            }

            collectObjectsRecursively(toPush, cemDir.resolve(OBJECTS));

            for (String sha : toPush) {
                Path obj = cemDir.resolve(OBJECTS)
                        .resolve(sha.substring(0,2))
                        .resolve(sha.substring(2));
                byte[] compressed = Files.readAllBytes(obj);

                out.write("OBJECT " + sha + " " + compressed.length + "\n");
                out.flush();
                rawOut.write(compressed);
                rawOut.write('\n');
                rawOut.flush();
            }

            out.write("UPDATE_REF " + branch + " " + localSha + "\n");
            out.flush();
            String response = in.readLine();
            System.out.println("[client] ◀ response: " + response);
            if (!response.startsWith("OK")) {
                System.err.println("Push failed: " + response);
            } else {
                System.out.println("Push successful.");
            }
        }
    }

    private static List<String> findCommitsToPush(String localSha, Path cemDir) throws IOException {
        List<String> toPush = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Deque<String> stack = new ArrayDeque<>();
        stack.push(localSha);

        while (!stack.isEmpty()) {
            String sha = stack.pop();
            if (seen.contains(sha)) continue;
            seen.add(sha);
            toPush.add(sha);

            Path objectPath = cemDir.resolve("objects")
                    .resolve(sha.substring(0, 2))
                    .resolve(sha.substring(2));
            if (!Files.exists(objectPath)) continue;

            byte[] compressed = Files.readAllBytes(objectPath);
            byte[] raw = ObjectUtils.zlibDecompress(compressed);
            String content = new String(raw, UTF_8);

            for (String line : content.split("\n")) {
                if (line.startsWith("parent: ")) {
                    String parentSha = line.substring(8).trim();
                    stack.push(parentSha);
                }
            }
        }

        Collections.reverse(toPush);
        return toPush;
    }

    private static String resolveLocalRef(Path cemDir, String branch) {
        Path ref = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(branch);
        try{
            return Files.exists(ref) ? Files.readAllLines(ref, UTF_8).get(0).trim()
                    : null;
        } catch (IOException e) {
           return null;
        }
    }

    private static Map<String, String> parseRemotes(Path configPath) {
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

    private static void collectObjectsRecursively(Set<String> seen, Path objectsRoot) throws IOException {
        Queue<String> q = new ArrayDeque<>(seen);
        while (!q.isEmpty()) {
            String sha = q.poll();
            Path p = objectsRoot.resolve(sha.substring(0,2)).resolve(sha.substring(2));
            byte[] full = ObjectUtils.zlibDecompress(Files.readAllBytes(p));
            int i = 0;
            while (i < full.length && full[i] != 0) i++;
            String payload = new String(full, i+1, full.length-i-1, UTF_8);

            for (String line : payload.split("\n")) {
                String[] parts = line.split(" ", 3);
                if (parts[0].matches("[0-9a-f]{40}") && seen.add(parts[0])) {
                    q.add(parts[0]);
                }
                else if (parts[0].equals("tree") && parts.length > 1 && seen.add(parts[1])) {
                    q.add(parts[1]);
                }
            }
        }
    }



}
