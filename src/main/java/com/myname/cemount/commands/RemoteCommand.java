package com.myname.cemount.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RemoteCommand {

    private static final String CEM_DIR        = ".cemount";
    private static final String CONFIG         = "config";

    public static void execute(String[] args){
        Path configPath = getConfigPath();
        try{
            ensureConfigExists(configPath);
        } catch (IOException e) {
            System.out.println("fatal: Unable to create config: " + e.getMessage());
            return;
        }

        switch (args[0]){
            case "add":
                if(args.length != 3){
                    System.err.println("usage: cem remote add <name> <url>");
                    return;
                }
                String name = args[1];
                String url = args[2];
                try {
                    addRemote(configPath,name,url);
                } catch (IOException e) {
                    System.err.println("fatal: Unable to add remote: " + e.getMessage());
                }
                break;
            case "remove":
                break;
            case "-v":
                break;
            case "":
                break;
            default: // no args

                break;
        }
    }
    /**
     * Returns the path to .cemount/config.
     */
    private static Path getConfigPath(){
        Path repoRoot = Paths.get("").toAbsolutePath().normalize();
        return repoRoot.resolve(CEM_DIR).resolve(CONFIG);
    }

    /**
     * If config file doesn’t exist, create it (and parent dirs if needed).
     */
    private static void ensureConfigExists(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            Files.createDirectories(configPath.getParent());
            // parent is <repo>/.cemount
            Files.createFile(configPath);
        }
    }
    private static void addRemote(Path configPath, String name, String url) throws IOException {

    }
    private static void removeRemote(Path configPath, String name) throws IOException {

    }
    private static void listRemotes(Path configPath, boolean verbose){

    }
}
