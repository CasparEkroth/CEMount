package com.myname.cemount.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PushCommand {
    private static final String CEM_DIR    = ".cemount";
    private static final String CONFIG     = "config";
    private static final String REFS_DIR   = "refs";
    private static final String HEADS_DIR  = "heads";
    private static final String OBJECTS    = "objects";
    private static final String HEAD_FILE  = "HEAD";

    public static void execute(String[] args){
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
        List<String> commits = findCommitsToPush(cemDir, localSha);
        if(commits.isEmpty()){
            System.out.println("Everything up-to-date.");
            return;
        }

    }

    private static List<String> findCommitsToPush(Path cemDir, String tipSha) {
        // Return list oldest->newest.

        return Collections.emptyList();
    }

    private static String resolveLocalRef(Path cemDir, String branch) {
        Path ref = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(branch);
        try{
            return Files.exists(ref) ? Files.readAllLines(ref, StandardCharsets.UTF_8).get(0).trim()
                    : null;
        } catch (IOException e) {
           return null;
        }
    }

    private static Map<String, String> parseRemotes(Path configPath){
        Map<String, String> map = new HashMap<>();
        String key = null;
        try {
            for (String raw : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
                String line = raw.trim();

                if(line.startsWith("[remote")){
                    key = line.substring(
                            line.indexOf('"') + 1,
                            line.indexOf('"',line.indexOf('"')));
                } else if (line.startsWith("url") && key != null){
                    String url = line.substring(line.indexOf("=")).trim();
                    map.put(key,url);
                    key = null;
                }
            }
        } catch (IOException e) {/* ... */}
        return map;
    }
}
