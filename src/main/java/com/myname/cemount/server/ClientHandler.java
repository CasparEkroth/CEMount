package com.myname.cemount.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Path repoRoot;
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
            Path bareRepo = repoRoot.resolve(parts[1]);

            RepositoryManager repoMgr = new RepositoryManager(bareRepo);

            if("PUSH".equals(cem)){
                String branch = parts[2];
                handlePush(repoMgr, bareRepo, branch, in, out);
            } else if ("FETCH".equals(cem)){
                String branch = parts[2];
                String clientHave = parts[3];
                handleFetch(repoMgr, bareRepo, branch, clientHave, in, out);
            }else{
                out.write("ERR Unknown command\n");
            }
            out.flush();
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
        }finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    private void handlePush(RepositoryManager repoMgr, Path bareRepo, String branch, BufferedReader in, BufferedWriter out) throws IOException {
        // 1) Read COMMITS <N> + N lines of COMMIT <sha>
        // 2) Read OBJECT <sha> <len>\n<bytes> blocks, write into bareRepo/.cemount/objects/…
        // 3) Read UPDATE_REF <branch> <newSha>, update bareRepo/.cemount/refs/heads/<branch>
        // 4) Optionally update bareRepo/.cemount/HEAD if branch=="master"
        // 5) out.write("OK\n");
    }

    private void handleFetch(RepositoryManager repoMgr, Path bareRepo, String branch, String clientHave, BufferedReader in, BufferedWriter out) throws IOException {
        // 1) Read server’s current tip from bareRepo/.cemount/refs/heads/<branch>
        // 2) Compute all commits the server has that clientHave doesn’t
        // 3) out.write("COMMITS " + missing.size() + "\n");
        //    for each sha: out.write("COMMIT " + sha + "\n");
        // 4) For each missing commit, send its objects:
        //    out.write("OBJECT " + sha + " " + len + "\n"); then raw bytes
        // 5) out.write("NEW_REF " + branch + " " + serverTip + "\n");
    }
}
