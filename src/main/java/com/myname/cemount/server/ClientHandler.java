package com.myname.cemount.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;

public class ClientHandler implements Runnable {
    //need to handel push && fetch
    //also need to create command for fetch and push
    private final Socket socket;
    private final RepositoryManager repoMgr;
    public ClientHandler(Socket socket, RepositoryManager repoMgr) {
        this.socket = socket;
        this.repoMgr = repoMgr;
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
            String cem = parts[0];
            Path bareRepo = repoMgr.getBareRepo(parts[1]);

            if("PUSH".equals(cem)){
                String branch = parts[2];
                handlePush(bareRepo, branch, in, out);
            } else if ("FETCH".equals(cem)){
                String branch = parts[2];
                String clientHave = parts[3];
                handleFetch(bareRepo, branch, clientHave, in, out);
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
    private void handlePush(Path bareRepo, String branch, BufferedReader in, BufferedWriter out) throws IOException {

    }

    private void handleFetch(Path bareRepo, String branch, String clientHave, BufferedReader in, BufferedWriter out) throws IOException {

    }
}
