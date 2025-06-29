package com.myname.cemount.commands;


import com.myname.cemount.server.ClientHandler;
import com.myname.cemount.server.RepositoryManager;
import com.myname.cemount.server.ViewCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ServeCommand listens on a TCP port and accepts a single `cem push` from a client.
 * Usage: cem serve <port> <path-to-bare-repo>
 * Example:
 *   On server (old laptop):
 *     $ cem serve 7842 /home/alice/repos/
 *   Then the client’s PushCommand can connect to 192.168.1.50:7842 and send objects.
 */

public class ServerCommand {
    private static volatile boolean isRunning = true;
    private static final String CEM_DB_DIR = "CEMountDB";
    private static final int MAX_THREADS = 10;
    private static ServerSocket serverSocket;
    private static RepositoryManager repoMgr;

    public static final String BOLD = "\033[1m";
    public static final String RESET = "\033[0m";
    private static final String CEM_DIR        = ".cemount";
    private static final String REFS_DIR       = "refs";
    private static final String HEADS_DIR      = "heads";
    private static final String HEAD_FILE      = "HEAD";

    public static void execute(String[] args){
        if(args.length != 2){
            System.err.println("Usage: cem server <port> <file path for CEMount>");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("fatal: invalid port: " + args[0]);
            return;
        }
        Path dbDir;
        try{
            dbDir = createDataBaseDir(args[1]);
            repoMgr = new RepositoryManager(dbDir, "master");
        }catch (IOException e){
            System.err.println("fatal: could not create CEM database dir: " + e.getMessage());
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                while (isRunning) {
                    String line = in.readLine();
                    if(line != null){
                        String[] tokens = line.trim().split("\\s+");
                        if(tokens.length == 0) continue;
                        String cmd = tokens[0].toLowerCase();
                        switch (cmd){
                            case "":
                                break;
                            case "q":
                            case "shutdown":
                                stop();
                                break;
                            case "list":
                                listRepos(dbDir.toString());
                                break;
                            case "info":
                                if(tokens.length != 2){
                                    System.out.println("Error: the info command takes 1 arg");
                                    break;
                                }
                                repoInfo(tokens[1],dbDir);
                                break;
                            case "view":
                                if(tokens.length != 2){
                                    System.out.println("Error: the view command takes 1 arg");
                                    break;
                                }
                                ViewCommand.execute(dbDir.resolve(tokens[1]));
                                break;
                            case "-h":
                                showServerCommands();
                                break;
                            default:
                                System.out.println("Unknown command: " + cmd);
                                break;
                        }
                        System.out.print("CEMount " + port + ": ");
                    }
                }
            } catch (IOException e) {
                System.err.println("Console-watcher error: " + e.getMessage());
            }
        }, "CEM-Shutdown-Watcher").start();

        try{
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            System.out.println("CEM server listening on port " + port);
            System.out.print("CEMount " + port + ": ");
            while (isRunning){
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                pool.submit(new ClientHandler(clientSocket, repoMgr ));
                System.out.print(CEM_DB_DIR + " " + port + ": ");
            }
        } catch (IOException e) {
            if(isRunning){
                System.err.println("fatal: server error: " + e.getMessage());
            }
        } finally{
            pool.shutdown();
            System.out.println("CEM server shutting down.");
        }

    }

    private static Path createDataBaseDir(String path) throws IOException {
        Path serverPath = Paths.get(path).toAbsolutePath().normalize().resolve(ServerCommand.CEM_DB_DIR);
        Files.createDirectories(serverPath);
        return serverPath;
    }

    private static void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
           // ...
        }
    }
    private static void listRepos(String path){
        File directory = new File(path);
        String[] content = directory.list();
        if(content != null){
            System.out.println(BOLD + "REPOSITORY'S" + RESET);
            for(String name : content){
                System.out.println("\t" + name);
            }
        }else {
            System.out.println(CEM_DB_DIR + " is empty");
        }
    }

    private static void repoInfo(String repoName, Path dbDir){
        File repo = new File(dbDir.resolve(repoName).toString());
        String[] content = repo.list();
        if(content != null){

        }else {
            System.out.println(CEM_DB_DIR + " is empty");
        }
        // info abut the repo ex nr of commits
        // time for last commit

    }
    private static void showServerCommands(){
        System.out.println(BOLD + "COMMAND'S\n" + RESET);
        System.out.println(BOLD + "list" + RESET);
        System.out.println("\tshows all repos in the CEMountDB folder\n");
        System.out.println(BOLD + "info" + RESET);
        System.out.println("\tshows info of the repo aka time ect\n");
        System.out.println(BOLD + "view" + RESET);
        System.out.println("\tshows the contents of a file in a repo\n");
        System.out.println(BOLD + "shutdown" + RESET);
        System.out.println("\tshutdown kills the server and closes the port\n");
        System.out.println(BOLD + "-h" + RESET);
        System.out.println("\tshows all commands supported by the server\n");
    }
}
