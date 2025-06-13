package com.myname.cemount.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Path repoRoot;
    private static final String CEM_DIR        = ".cemount";
    private static final String OBJECTS_SUBDIR = "objects";
    private static final String REFS_DIR       = "refs";
    private static final String HEADS_DIR      = "heads";
    private static final String HEAD_FILE      = "HEAD";

    public ClientHandler(Socket socket, Path repoRoot) {
        this.socket = socket;
        this.repoRoot = repoRoot;
    }

    @Override
    public void run() {
        try(
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ){
            String header = in.readLine();
            if(header == null) return;
            String[] parts = header.split("\\s+");
            if(parts.length < 3){
                out.write("ERR Invalid command format\n");
                out.flush();
                return;
            }
            String cem = parts[0];
            String repoName = parts[1];
            String branch = parts[2];
            Path bareRepo = repoRoot.resolve(repoName);
            Path cemDir = bareRepo.resolve(CEM_DIR);


            RepositoryManager.initIfMissing(cemDir,branch,repoName);

            if("PUSH".equals(cem)){
                handlePush(bareRepo, branch, in, out);
            } else if ("FETCH".equals(cem)){
                String clientHave = parts[3];
                handleFetch(bareRepo, branch, clientHave, in, out);
            }else{
                out.write("ERR Unknown command\n");
            }
            out.flush();
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        }finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    private void handlePush(Path bareRepo, String branch, BufferedReader in, BufferedWriter out) throws IOException {
        Path cemDir = bareRepo.resolve(CEM_DIR);
        String line = in.readLine();
        if(!line.startsWith("COMMITS ")) throw new IOException("Expected COMMITS");
        int n = Integer.parseInt(line.substring(8));
        List<String> commits = new ArrayList<>();
        for(int i = 0; i < n; i++){
            String commitLine = in.readLine();
            if(!commitLine.startsWith("COMMIT ")) throw new IOException("Expected COMMIT");
            commits.add(commitLine.substring(8));
        }
        for(String sha : commits){
            String objHeader = in.readLine(); // "OBJECT <sha> <len>"
            if(!objHeader.startsWith("OBJECT ")) throw new IOException("Expected OBJECT");
            String[] parts = objHeader.split(" ");
            String objSha = parts[1];
            int len = Integer.parseInt(parts[2]);
            byte[] raw = new byte[len];
            InputStream rawIn = socket.getInputStream();
            for(int i = 0; i < len;){
                int r = rawIn.read(raw, i, len - i);
                if(r == -1)throw new IOException("Unexpected EOF in object bytes");
                i += r;
            }
            Path objPath = cemDir.resolve(OBJECTS_SUBDIR).resolve(objSha.substring(0,2)).resolve(objSha.substring(2));
            Files.createDirectories(objPath.getParent());
            Files.write(objPath,raw);
        }

        String update = in.readLine();
        if(!update.startsWith("UPDATE_REF ")) throw new IOException("Expected UPDATE_REF");
        String[] uParts = update.split(" ");
        String newSha = uParts[2];
        Path ref = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(branch);
        Files.writeString(ref, newSha + "\n", StandardCharsets.US_ASCII);

        Path headFile = cemDir.resolve(HEAD_FILE);
        if(!Files.exists(headFile)){
            Files.writeString(headFile, "ref: refs/heads/" + branch + "\n",StandardCharsets.US_ASCII);
        }
        out.write("OK\n");
    }

    private void handleFetch(Path bareRepo, String branch, String clientHave, BufferedReader in, BufferedWriter out) throws IOException {
        // 1) Read server’s current tip from bareRepo/.cemount/refs/heads/<branch>
        // 2) Compute all commits the server has that clientHave doesn’t
        // 3) out.write("COMMITS " + missing.size() + "\n");
        //    for each sha: out.write("COMMIT " + sha + "\n");
        // 4) For each missing commit, send its objects:
        //    out.write("OBJECT " + sha + " " + len + "\n"); then raw bytes
        // 5) out.write("NEW_REF " + branch + " " + serverTip + "\n");
    }
}
