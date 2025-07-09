package com.myname.cemount.server;

import com.myname.cemount.core.Pair;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.myname.cemount.server.ObjectUtils.readLine;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final RepositoryManager repoManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String CEM_DIR    = ".cemount";
    private static final String REFS_DIR_HEAD       = "refs/heads";
    private static final String OBJECTS    = "objects";
    private static final String ECHO_DIR      = "ECHO";


    public ClientHandler(Socket socket, RepositoryManager repoManager) {
        this.socket = socket;
        this.repoManager = repoManager;
    }

    @Override
    public void run() {
        try {
            InputStream rawIn = socket.getInputStream();
            OutputStream        binOut = socket.getOutputStream();
            BufferedInputStream bin = new BufferedInputStream(rawIn);
            BufferedReader       in  = new BufferedReader( new InputStreamReader(bin, StandardCharsets.UTF_8));
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
                        handleFetch(in, out ,binOut, bareRepo, branch);
                        break;
                    case "PULL":
                        handelPull(in, out, binOut, bareRepo, branch);
                        return;
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

    private static void handelPull(BufferedReader textIn, BufferedWriter textOut, OutputStream binOut, Path bareRepo, String branch) throws IOException{
        int conut = Integer.parseInt(textIn.readLine().trim());
        for(int i = 0; i < conut; i++){
            String sha = textIn.readLine();
            byte[] raw = ObjectUtils.loadObject(bareRepo,sha);
            textOut.write(raw.length + "\n");
            textOut.flush();
            binOut.write(raw);
            binOut.flush();
        }
        textOut.write("END\n");
        textOut.flush();
    }

    private static void handleFetch(BufferedReader ctrlIn, BufferedWriter ctrlOut,OutputStream binOut, Path bareRepo, String branch) throws IOException {
        String remoteSha = ObjectUtils.getRef(bareRepo, branch);

        ctrlOut.write(remoteSha + "\n");
        ctrlOut.flush();

        String haveSha = ctrlIn.readLine().trim();

        if(haveSha.equals(remoteSha)){
            System.out.println("no update needed");
            return;
        }
        List<String> missing = new ArrayList<>();
        String parent = remoteSha;
        missing.add(parent);
        while (true){
            parent = ObjectUtils.getParent(bareRepo,parent);
            if(parent.equals(haveSha)) break;
            if(parent.equals("origin")) break;
            missing.add(parent);
        }

        ctrlOut.write(missing.size() + "\n");
        ctrlOut.flush();

        for(int i = 0; i < missing.size(); i++){
            ctrlOut.write(missing.get(i) + "\n");
            ctrlOut.flush();
            byte[] raw = ObjectUtils.loadCommit(bareRepo,missing.get(i));
            ctrlOut.write(raw.length + "\n");
            ctrlOut.flush();
            binOut.write(raw);
            binOut.flush();
        }
    }

    private void handlePush(Path bareRepo,
                            String branch,
                            BufferedWriter out,
                            BufferedInputStream bin) throws IOException {

        String clientSha = readLine(bin).trim();
        String refSha = ObjectUtils.getRef(bareRepo,branch).trim();
        if(clientSha.equals(refSha)){
            out.write("OK \n");
            out.flush();
            return;
        }else {
            out.write(  refSha + "\n");
            out.flush();
        }

        String line = readLine(bin);

        if (line != null && line.startsWith("COMMITS ")) {
            int count = Integer.parseInt(line.split(" ")[1]);
            for (int i = 0; i < count; i++) {
                //String sha = in.readLine().trim();
                String sha = readLine(bin).trim();
                Path newCommitPath = bareRepo.resolve(ECHO_DIR).resolve(sha.substring(0,2)).resolve(sha.substring(2));
                Path parentDir = newCommitPath.getParent();
                if(!Files.exists(parentDir)){
                    Files.createDirectories(parentDir);
                }
                Files.deleteIfExists(newCommitPath);
                Files.createFile(newCommitPath);
                //int len = Integer.parseInt(in.readLine().trim());
                int len = Integer.parseInt(readLine(bin).trim());
                byte[] raw = bin.readNBytes(len);
                Files.write(newCommitPath,raw, StandardOpenOption.WRITE);
                // fix the obj for the files
                System.out.println(bareRepo);
                List<Pair> addObj = ObjectUtils.getShaFromCommit(bareRepo, sha);
                for (int y  = 0; y < addObj.size();y++){
                    String currentSha = readLine(bin).trim();
                    Path objPath = bareRepo.resolve(OBJECTS).resolve(currentSha.substring(0,2)).resolve(currentSha.substring(2));
                    if(!Files.exists(objPath.getParent())){
                        Files.createDirectories(objPath.getParent());
                    }
                    Files.deleteIfExists(objPath);
                    Files.createFile(objPath);
                    int size = Integer.parseInt(readLine(bin).trim());
                    byte[] rawObj = bin.readNBytes(size);
                    Files.write(objPath,rawObj,StandardOpenOption.WRITE);
                }
            }
        }

        String update = (readLine(bin).trim());
        if (!update.startsWith("UPDATE_REF ")) {
            throw new IOException("Expected UPDATE_REF, got: " + update);
        }
        String[] up = update.split(" ", 3);
        Files.writeString(bareRepo.resolve("refs/heads").resolve(up[1]),
                up[2] + "\n",
                StandardCharsets.UTF_8);

        out.write("OK PUSH " + branch + "\n");
        out.flush();
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
