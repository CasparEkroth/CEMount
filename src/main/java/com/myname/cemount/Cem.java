package com.myname.cemount;

import com.myname.cemount.commands.*;
import com.myname.cemount.core.CommitCommand;
import com.myname.cemount.core.Pair;
import com.myname.cemount.server.ObjectUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the `cem` CLI tool.
 * Supports the `init` command to bootstrap a new .cemount repository.
 */
public class Cem {
    private static final String CEM_DIR = ".cemount";
    public static final String BOLD = "\033[1m";
    public static final String RESET = "\033[0m";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String cmd = args[0];
        switch (cmd) {
            case "init":
                runInit();
                break;
            case "add":
                AddCommand.execute(slice(args, 1));
                break;
            case "commit":
                CommitCommand.execute(slice(args, 1));
                break;
            case "log":
                LogCommand.execute(slice(args, 1));
                break;
            case "remote":
                RemoteCommand.execute(slice(args, 1));
                break;
            case "server":
                ServerCommand.execute(slice(args,1));
                break;
            case "-h":
                printHelpMsg();
                break;
            case "push":
                PushCommand.execute(slice(args,1));
                break;
            case "fetch":
                FetchCommand.execute(slice(args,1));
                break;
            case "pull":
                PullCommand.execute(slice(args,1));
                break;
            case "t":
                Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
                Path cemDir = repoRoot.resolve(CEM_DIR);
                String sha = Files.readString(cemDir.resolve("refs/heads/master")).trim();
                String text =  ObjectUtils.readCommitText(cemDir,sha);

                System.out.println(text);
                System.out.println("\n" + ObjectUtils.getTimeStamp(cemDir,sha));
                System.out.println();
                List<Pair> pairs = ObjectUtils.getShaFromCommit(cemDir, sha);
                for (Pair pair : pairs){
                    System.out.println(pair.getFileName() + " " + pair.getSha() + "  nice");
                }

                //text =  ObjectUtils.readObjectText(cemDir,"03ed7510cbaa462ed0f6f62c899999ad74336192");
                //System.out.println("sha: " + ObjectUtils.hexToString("03ed7510cbaa462ed0f6f62c899999ad74336192"));
                //System.out.println(text);
                break;
            default:
                System.err.println("Unknown command: " + cmd);
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: cem <command>");
        System.out.println("Commands:");
        System.out.println("  init     Initialize a new CEMount repository");
        System.out.println("  add      Add files to the CEMount index");
        System.out.println("  commit   Commit the current index");
        System.out.println("  log      Show commit history");
        System.out.println("  remote   Manage remote repositories (e.g. local paths or tcp://host:port)");
        System.out.println("  server   Stars a CEMount data base");
        System.out.println("  fetch    Fetches the latest version");
        System.out.println("  pull     Pulls the latest version from the remote");
        // more commands coming ;)
    }

    private static void printHelpMsg(){
        // need to add a user m
        // locke at the man git
        System.out.println(BOLD + "NAME" + RESET);
        System.out.println("\tcem - Caspar Ekroth Mount\n");
        System.out.println(BOLD + "DESCRIPTION" + RESET);
        System.out.println("\tCEMount is a cli tool used for working white a distrubutedfilesistym\n");
        System.out.println("For README go to: https://github.com/CasparEkroth/CEMount");
        System.out.println("Commands:");
        System.out.println("all of the commands starts using 'cem' followed by a command");
        System.out.println(BOLD + "init" + RESET);
        System.out.println("\tInitialize a new CEMount repository");
        System.out.println();
        System.out.println(BOLD + "add" + RESET);
        System.out.println("\tAdd files to the CEMount index");
        System.out.println();
        System.out.println(BOLD + "commit" + RESET);
        System.out.println("\tCommit the current index");
        System.out.println();
        System.out.println(BOLD + "log" + RESET);
        System.out.println("\tShow commit history");
        System.out.println();
        System.out.println(BOLD + "remote" + RESET);
        System.out.println("\tManage remote repositories (e.g. local paths or tcp://host:port)");
        System.out.println();
        System.out.println(BOLD + "server" + RESET);
        System.out.println("\tStars a CEMount data base");
        System.out.println(BOLD + "fetch" + RESET);
        System.out.println("\tupdates the FETCH_HEAD used for the pull command");

    }
    /**
     * Create the .cemount directory structure and initial HEAD file.
     */
    private static void runInit() {
        Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
        Path cemDir = repoRoot.resolve(CEM_DIR);

        try {
            if (Files.exists(cemDir)) {
                System.out.println("CEMount repository already initialized at " + cemDir);
                return;
            }

            // Create directories
            Files.createDirectories(cemDir.resolve("objects"));
            Files.createDirectories(cemDir.resolve("refs/heads"));

            Files.createFile(cemDir.resolve("FETCH_HEAD"));

            // Write initial HEAD pointing to master
            Path headFile = cemDir.resolve("HEAD");
            Files.writeString(headFile, "ref: refs/heads/master\n");

            Path configFile = cemDir.resolve("config");
            if (!Files.exists(configFile)) {
                Files.createFile(configFile);
            }

            System.out.println("Initialized empty CEMount repository in " + cemDir);
        } catch (IOException e) {
            System.err.println("Error initializing repository: " + e.getMessage());
        }
    }
    private static String[] slice(String[] arr, int start) {
        String[] result = new String[arr.length - start];
        System.arraycopy(arr, start, result, 0, result.length);
        return result;
    }
}
