package com.myname.cemount.commands;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

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
        if(args.length == 0){
            listRemotes(configPath,false);
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
                if(args.length != 2){
                    System.err.println("usage: cem remote remove <name>");
                    return;
                }
                String nameToRemove = args[1];
                try {
                    removeRemote(configPath,nameToRemove);
                }catch (IOException e){
                    System.err.println("fatal: Unable to remove remote: " + e.getMessage());
                }
                break;
            case "-v":
                    listRemotes(configPath,true);
                break;
            default:
                System.err.println("fatal: unknown subcommand for 'remote': " + args[0]);
                System.err.println("Available: add, remove, -v");
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
        List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        String targetHeader = "[remote \"" + name + "\"]";
        for (String line : lines){
            if(line.trim().equals(targetHeader)){
                System.err.println("fatal: remote '" + name + "' already exists");
                return;
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            writer.write("\n");
            writer.write(targetHeader);
            writer.newLine();
            writer.write("    url = " + url);
            writer.newLine();
            writer.write("    fetch = +refs/heads/*:refs/remotes/" + name + "/*");
            writer.newLine();
        }
        System.out.println("Remote '" + name + "' added with URL " + url);
    }

    private static void removeRemote(Path configPath, String name) throws IOException {
        List<String> original = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        List<String> rebuilt = new ArrayList<>();

        String targetHeader = "[remote \"" + name + "\"]";
        boolean inTargetSection = false;
        boolean found = false;

        for (String line : original){
            String trimmed = line.trim();
            if(trimmed.equals(targetHeader)){
                inTargetSection = true;
                found = true;
                continue;
            }
            if(inTargetSection){
                if(trimmed.startsWith("[") && trimmed.endsWith("]")){
                    inTargetSection = false;
                    rebuilt.add(line);
                }
            }else{
                rebuilt.add(line);
            }
        }
        if (!found){
            System.err.println("fatal: No such remote '" + name + "'");
            return;
        }
        try(BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)){
            for (String line : rebuilt){
                writer.write(line);
                writer.newLine();
            }
        }
        System.out.println("Remote " + name + " removed");
    }

    private static void listRemotes(Path configPath, boolean verbose){
        List<String> lines;
        try {
            lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("fatal: cannot read config: " + e.getMessage());
            return;
        }
        String currentName = null;
        String currentUrl = null;
        for(int i = 0; i < lines.size();i++){
            String line = lines.get(i).trim();
            if(line.startsWith("[remote \"") && line.endsWith("\"]")){
                currentName = line.substring(9,line.length() - 2);
                currentUrl = null;
                if(!verbose){
                    System.out.println(currentName);
                    currentName = null;
                }

            } else if (verbose &&currentName != null){
                if(line.startsWith("url") || line.startsWith("url =")){
                    String[] parts = line.split("=", 2);
                    if(parts.length == 2){
                        currentUrl = parts[1].trim();
                    }
                    System.out.printf("%-10s %s (fetch)\n", currentName, currentUrl);
                    System.out.printf("%-10s %s (push)\n",  currentName, currentUrl);
                    currentName = null;
                    currentUrl  = null;
                }

            }
        }
    }

}
