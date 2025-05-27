package com.myname.cemount;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for the `cem` CLI tool.
 * Supports the `init` command to bootstrap a new .cemount repository.
 */
public class Cem {
    private static final String CEM_DIR = ".cemount";

    public static void main(String[] args) {
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
                new AddCommand(repo).run(Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                System.err.println("Unknown command: " + cmd);
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: cem <command>");
        System.out.println("Commands:");
        System.out.println("  init    Initialize a new CEMount repository");
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

            // Write initial HEAD pointing to master
            Path headFile = cemDir.resolve("HEAD");
            Files.writeString(headFile, "ref: refs/heads/master\n");

            System.out.println("Initialized empty CEMount repository in " + cemDir);
        } catch (IOException e) {
            System.err.println("Error initializing repository: " + e.getMessage());
        }
    }
}
