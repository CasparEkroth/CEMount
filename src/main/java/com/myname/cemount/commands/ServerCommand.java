package com.myname.cemount.commands;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ServeCommand listens on a TCP port and accepts a single `cem push` from a client.
 * Usage: cem serve <port> <path-to-bare-repo>
 * Example:
 *   On server (old laptop):
 *     $ cem serve 7842 /home/alice/repos/project.git
 *   Then the client’s PushCommand can connect to 192.168.1.50:7842 and send objects.
 */

public class ServerCommand {
    // for setting upp a server
    // cem serve --port <n>
    private static final String CEM_DB_DIR = "CEMountDB";
    private static final String CEM_DIR   = ".cemount";
    private static final String REFS_DIR  = "refs";
    private static final String HEADS_DIR = "heads";
    private static final String OBJECTS   = "objects";
    private static final String HEAD_FILE = "HEAD";

    public static void execute(String[] args){
        if(args.length != 2){
            System.err.println("Usage: cem server <port> <file path for CEMount>");
            return;
        }
        String port = args[0];
        String path = args[1];
        try{
            Path dbDir = createDataBaseDir(path);
        }catch (IOException e){
            System.out.println("d" + e.getMessage());
        }

    }

    private static Path createDataBaseDir(String path) throws IOException {
        Path serverPath = Paths.get(path).toAbsolutePath().normalize().resolve(ServerCommand.CEM_DB_DIR);
        Files.createDirectories(serverPath);
        return serverPath;
    }
}
