package com.myname.cemount.commands;

import java.io.*;
import java.net.Socket;
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
        List<String> commits = findCommitsToPush(cemDir, localSha);
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

    }

    private static void pushOverTcp(String remoteUrl, String branch, String localSha, List<String> commits, Path cemDir){
        String without = remoteUrl.substring("tcp://".length());
        int idx = without.indexOf(':');
        int slash = without.lastIndexOf('/');
        // need to add this to the remoteCommand
        String repoName  = without.substring(slash +1);
        if(slash < idx){
            slash = without.length();
            repoName = "default";
        }
        String serverPort = without.substring(idx + 1, slash);
        String serverIP = without.substring(0,idx);

        try(Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())))
        {
            out.write("PUSH " + repoName + " " + branch + "\n");
            out.flush();

            out.write("COMMITS " + commits.size() + "\n");
            for (String sha : commits){
                out.write("COMMIT " + sha + "\n");
            }
            out.flush();

            for (String sha : commits){
                Path object = cemDir.resolve(OBJECTS)
                        .resolve(sha.substring(0,2))
                        .resolve(sha.substring(2));
                byte[] compressed = Files.readAllBytes(object);
                out.write("OBJECT" + sha + " " + compressed.length + "\n");
                out.flush();
                socket.getOutputStream().write(compressed);
                socket.getOutputStream().flush();
            }
            out.write("UPDATE_REF " + branch + " " + localSha + "\n");
            out.flush();

            StringBuilder reply = new StringBuilder();
            int c;
            while ((c = in.read()) != -1 && c != '\n'){
                reply.append((char) c);
            }
            if(!reply.toString().startsWith("OK")){
                System.err.println("Push failed: " + reply);
            }else{
                System.out.println("Push successful.");
            }
        }catch(IOException e){
            System.err.println("cem push: failed to push: " + e.getMessage());
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
