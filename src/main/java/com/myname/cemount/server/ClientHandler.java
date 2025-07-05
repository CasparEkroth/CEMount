package com.myname.cemount.server;

import java.io.*;
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
    private static final String CEM_DIR    = ".cemount";
    private static final String REFS_DIR_HEAD       = "refs/heads";
    private static final String OBJECTS    = "objects";
    public ClientHandler(Socket socket, RepositoryManager repoManager) {
        this.socket = socket;
        this.repoManager = repoManager;
    }

    @Override
    public void run() {
        try {
            InputStream rawIn = socket.getInputStream();
            BufferedInputStream bin = new BufferedInputStream(rawIn);
            //BufferedReader       in  = new BufferedReader( new InputStreamReader(bin, StandardCharsets.UTF_8));
            BufferedWriter       out = new BufferedWriter( new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));


            while (true) {
                String cmdLine = readLine(bin);
                if (cmdLine == null) break;
                String[] parts = cmdLine.split(" ", 3);
                String cmd = parts[0];
                String repoName = parts[1];
                String branch = parts[2];
                // XXXX <repo-name> <branch>
                Path bareRepo = repoManager.getOrCreate(repoName);
                switch (cmd) {
                    case "INIT":
                        handleInit(parts[1], parts[2], out);
                        break;
                    case "CLONE":
                        handleClone(parts[1], parts[2], out);
                        break;
                    case "PUSH":
                        handlePush(bareRepo, branch, out, bin);
                        return;
                    case "FETCH":
                        handleFetch(bareRepo, branch, out, bin);
                        break;
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

    private static void handleFetch(Path bareRepo, String branch, BufferedWriter out, BufferedInputStream bin) throws  IOException {
        Path cemDir = bareRepo;
        String latestSha = ObjectUtils.getRef(cemDir, branch);
        out.write(latestSha + "\n");
        out.flush();
        String respons = readLine(bin).trim();
        if(respons.equals("OK ")){
            return;
        }else {
            System.out.println(respons);
        }
        System.out.println();
    }

    private void handlePush(Path bareRepo,
                            String branch,
                            BufferedWriter out,
                            BufferedInputStream bin) throws IOException {

        String line = readLine(bin);
        if (line != null && line.startsWith("COMMITS ")) {
            int count = Integer.parseInt(line.split(" ")[1]);
            for (int i = 0; i < count; i++) {
                System.out.println("[server] ◀ skip: " + readLine(bin));
            }
        }

        String header;
        while ((header = readLine(bin)) != null && header.startsWith("OBJECT ")) {
            String[] parts = header.split(" ", 3);
            String sha = parts[1];
            int len = Integer.parseInt(parts[2]);

            byte[] rawCom = bin.readNBytes(len);
            if (rawCom.length < len) {
                throw new IOException("Unexpected EOF in object bytes");
            }

            Path objRoot = bareRepo.resolve(OBJECTS);
           String realSha = ObjectUtils.storeObject(objRoot, rawCom);
            System.out.printf("[server] stored %s at %s/%s\n",realSha, realSha.substring(0,2), realSha.substring(2));

            int nl = bin.read();
            if (nl != '\n') throw new IOException("Expected newline after object payload");
        }

        String update = (header != null && header.startsWith("UPDATE_REF "))
                ? header
                : readLine(bin);
        if (update == null || !update.startsWith("UPDATE_REF ")) {
            throw new IOException("Expected UPDATE_REF, got: " + update);
        }
        String[] up = update.split(" ", 3);
        Files.writeString(bareRepo.resolve("refs/heads").resolve(up[1]),
                up[2] + "\n",
                StandardCharsets.UTF_8);

        out.write("OK PUSH " + branch + "\n");
        out.flush();
    }

    private static String readLine(BufferedInputStream bin) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = bin.read()) != -1 && b != '\n') {
            line.write(b);
        }
        return line.toString(StandardCharsets.UTF_8.name());
    }

    private void handleInit(String repoName, String path,
                      BufferedWriter out) throws IOException {
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
