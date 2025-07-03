package com.myname.cemount.commands;

import com.myname.cemount.server.ObjectUtils;

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

    
    public static void execute(String [] args){
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
        Path configPath = repoRoot.resolve(CEM_DIR).resolve(CONFIG);
        Map<String, String> remote = ObjectUtils.parseRemotes(configPath);
        if(!remote.containsKey(remoteName)){
            System.err.printf("fatal: no such remote '%s'%n", remoteName);
            return;
        }
        String remoteUrl = remote.get(remoteName);
    }
}
