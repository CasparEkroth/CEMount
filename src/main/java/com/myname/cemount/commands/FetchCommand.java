package com.myname.cemount.commands;

import com.myname.cemount.server.ObjectUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class FetchCommand {
    private static final String CEM_DIR    = ".cemount";
    private static final String CONFIG     = "config";
    private static final String REFS_DIR   = "refs";
    private static final String HEADS_DIR  = "heads";
    private static final String OBJECTS    = "objects";
    private static final String HEAD_FILE  = "HEAD";

    
    public static void execute(String [] args) throws IOException {
        //cem fetch<remote> <repoName>
        // look at the remote
        // look at the latest commit
        // send the latest commit
        if(args.length != 2){
            System.err.println("Usage: cem fetch");
            return;
        }
        String remoteName = args[0];
        String repoName = args[1];
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

        String without = remoteUrl.substring("tcp://".length());
        int idx1   = without.indexOf(':');
        int slash  = without.lastIndexOf('/');
        String serverPort = without.substring(idx1 + 1,
                slash < idx1 ? without.length() : slash);
        String serverIP   = without.substring(0, idx1);

        try(Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        ){
            out.write("FETCH " + repoName + " " + branch + "\n");
            out.flush();

            String respons = in.readLine().trim();
            if(respons.equals(shaRef)){
                System.out.println("Already up to date.");
                out.write("OK \n");
                out.flush();
                return;
            }else {
                out.write(shaRef + "\n");
                out.flush();

                in.readLine();
            }
            // add logig for the fetch (on server also)

        }
    }
}
