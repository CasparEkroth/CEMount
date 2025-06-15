package com.myname.cemount.server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RepositoryManager repoManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ClientHandler(Socket socket, RepositoryManager repoManager) {
        this.socket = socket;
        this.repoManager = repoManager;
    }

    @Override
    public void run() {
        try {
            InputStream sockIn = socket.getInputStream();
            BufferedInputStream bin = new BufferedInputStream(sockIn);
            BufferedReader in = new BufferedReader(new InputStreamReader(bin, StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ");
                String cmd = parts[0];
                switch (cmd) {
                    case "INIT":
                        handleInit(parts[1], parts[2], in, out);
                        break;
                    case "CLONE":
                        handleClone(parts[1], parts[2], out);
                        break;
                    case "PUSH":
                        // PUSH <repo-name> <branch>
                        String repoName = parts[1];
                        String branch = parts[2];
                        Path bareRepo = repoManager.getOrCreate(repoName);
                        handlePush(bareRepo, branch, in, out, bin);
                        break;
                    // ... other commands
                    default:
                        out.write("ERROR Unknown command\n");
                        out.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handlePush(Path bareRepo, String branch,
                            BufferedReader in, BufferedWriter out,
                            BufferedInputStream bin) throws IOException {
        // Expect header: OBJECT <sha> <length>
        String header;
        while ((header = in.readLine()) != null && header.startsWith("OBJECT ")) {
            String[] hdr = header.split(" ");
            String sha = hdr[1];
            int len = Integer.parseInt(hdr[2]);

            // Read exactly 'len' bytes from the same buffered stream
            byte[] raw = new byte[len];
            int got = 0;
            while (got < len) {
                int r = bin.read(raw, got, len - got);
                if (r == -1) {
                    throw new IOException("Unexpected EOF in object bytes");
                }
                got += r;
            }

            // Write object file
            Path objDir = bareRepo.resolve("objects").resolve(sha.substring(0, 2));
            Files.createDirectories(objDir);
            Path objFile = objDir.resolve(sha.substring(2));
            Files.write(objFile, raw);
        }
        out.write("OK PUSH " + branch + "\n");
        out.flush();
    }

    private void handleInit(String repoName, String path,
                            BufferedReader in, BufferedWriter out) throws IOException {
        //Path repoPath = Paths.get(path).resolve(repoName).resolve(".cemount");
        repoManager.create(repoName);
        out.write("OK INIT " + repoName + "\n");
        out.flush();
    }

    private void handleClone(String repoName, String destination,
                             BufferedWriter out) throws IOException {
        Path bareRepo = repoManager.get(repoName);
        byte[] data = Files.readAllBytes(bareRepo.resolve("bundle.cem"));
        out.write("DATA " + data.length + "\n");
        out.flush();
        socket.getOutputStream().write(data);
        socket.getOutputStream().flush();
    }

    // ... other command handlers
}
