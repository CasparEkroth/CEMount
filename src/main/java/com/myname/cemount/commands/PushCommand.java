package com.myname.cemount.commands;

import com.myname.cemount.core.Pair;
import com.myname.cemount.server.ObjectUtils;

import java.io.*;
import java.net.Socket;
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
    private static final String ECHO_FILE  = "ECHO";


    public static void execute(String[] args) throws IOException {
        if(args.length != 1){
            System.err.println("usage: cem push <remote>");
            return;
        }
        String remoteName = args[0];
        Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
        Path cemDir = repoRoot.resolve(CEM_DIR);
        Path configPath = cemDir.resolve(CONFIG);
        Map<String, String> remote = ObjectUtils.parseRemotes(configPath);

        if(!remote.containsKey(remoteName)){
            System.err.printf("fatal: no such remote '%s'%n", remoteName);
            return;
        }
        String remoteUrl = remote.get(remoteName);

        String branch = ObjectUtils.getBranch(cemDir);
        String shaRef = ObjectUtils.getRef(cemDir,branch).trim();

        if (shaRef.isEmpty()) {
            System.err.printf("fatal: unknown local branch '%s'%n", branch);
            return;
        }

        if(remoteUrl.startsWith("tcp://")){
            pushOverTcp(remoteUrl, branch, shaRef, cemDir);
        }else{
            pushOverFileSystem(remoteUrl, branch, shaRef, cemDir);
        }
    }

    private static void pushOverFileSystem(String remoteUrl, String branch, String localSha, Path cemDir){

        /// ...
    }

    private static void pushOverTcp(String remoteUrl,
                                    String branch,
                                    String localSha,
                                    Path cemDir) throws IOException {

        String[] net = ObjectUtils.parseRemote(remoteUrl);
        String repoName = net[0];
        String serverPort = net[1];
        String serverIP = net[2];

        try (Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
        ) {
            OutputStream rawOut = socket.getOutputStream();
            out.write("PUSH " + repoName + " " + branch + "\n");
            out.flush();

            out.write(localSha + "\n");
            out.flush();

            String respons = in.readLine().trim();
            if(respons.equals("OK")){
                return;
            }
            List<String> commits = findCommitsToPush(localSha, respons, cemDir);
            if(commits.isEmpty()){
                // not needed :)
                System.out.println("Everything up-to-date.");
                return;
            }

            out.write("COMMITS " + commits.size() + "\n");
            for (String sha : commits) {
                out.write(sha + "\n");
                out.flush();
                byte[] raw = ObjectUtils.loadCommit(cemDir,sha);
                rawOut.write((raw.length + "\n").getBytes(UTF_8));
                rawOut.write(raw);
                rawOut.flush();
                // send the obj for the files
                List<Pair> addObj = ObjectUtils.getShaFromCommit(cemDir,sha);
                for (int i = 0; i < addObj.size(); i++){
                    String objSha = addObj.get(i).getSha();
                    out.write(objSha +"\n");
                    out.flush();
                    byte[] rawObj = ObjectUtils.loadObject(cemDir, objSha);
                    rawOut.write((rawObj.length + "\n").getBytes(UTF_8));
                    rawOut.write(rawObj);
                    rawOut.flush();

                }
            }

            out.write("UPDATE_REF " + branch + " " + localSha + "\n");
            out.flush();
            String response = in.readLine();
            System.out.println("[client] ◀ response: " + response);
            rawOut.close();
            if (!response.startsWith("OK")) {
                System.err.println("Push failed: " + response);
            } else {
                System.out.println("Push successful.");
            }
        }
    }

    private static List<String> findCommitsToPush(String localSha,String remoteSha, Path cemDir) throws IOException {
        List<String> CommitsToPush = new ArrayList<>();
        CommitsToPush.add(localSha);
        String parent = localSha;
        while (true){
            parent = ObjectUtils.getParent(cemDir,parent);
            if(parent.equals(remoteSha)){
                break;
            }
            CommitsToPush.add(parent);
            if(parent.equals("origin")) break;
        }
        CommitsToPush.remove("origin");
        Collections.reverse(CommitsToPush);
        return CommitsToPush;
    }

}
