package com.myname.cemount.commands;

import com.myname.cemount.server.ObjectUtils;

import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;

public class FetchCommand {
    private static final String CEM_DIR    = ".cemount";
    private static final String CONFIG     = "config";
    private static final String REFS_DIR   = "refs";
    private static final String HEADS_DIR  = "heads";
    private static final String OBJECTS    = "objects";
    private static final String HEAD_FILE  = "HEAD";
    private static final String FETCH_FILE = "FETCH_HEAD";
    private static final String ECHO_DIR       = "ECHO";


    public static void execute(String [] args) throws IOException {
        //cem fetch<remote>
        if(args.length != 1){
            System.err.println("Usage: cem fetch");
            return;
        }
        String remoteName = args[0];
        Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
        Path cemDir = repoRoot.resolve(CEM_DIR);
        Path configPath = cemDir.resolve(CONFIG);
        Map<String, String> remote = ObjectUtils.parseRemotes(configPath);

        if(!remote.containsKey(remoteName)){
            System.err.printf("fatal: no such remote '%s'%n", remoteName);
            return;
        }
        String remoteUrl = remote.get(remoteName);
        String[] net = ObjectUtils.parseRemote(remoteUrl);
        String repoName = net[0];
        String serverPort = net[1];
        String serverIP = net[2];

        String branch = ObjectUtils.getBranch(cemDir);
        String shaRef = ObjectUtils.getRef(cemDir,branch).trim();

        try(Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
            InputStream rawIn = socket.getInputStream();
            BufferedInputStream bin = new BufferedInputStream(rawIn);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        ){
            out.write("FETCH " + repoName + " " + branch + "\n");
            out.flush();

            String respons = ObjectUtils.readLine(bin).trim();

            if(respons.equals(shaRef)){
                System.out.println("Already up to date.");
                out.write(shaRef + "\n");
                out.flush();
                return;
            }
            out.write(shaRef + "\n");
            out.flush();

            String cntLine = ObjectUtils.readLine(bin).trim();
            int count = Integer.parseInt(cntLine);
            //System.out.println("nr of obj " + count);

            String[] newSha = new String[count];
            for(int i = 0 ; i < count; i++){
                newSha[i] = ObjectUtils.readLine(bin).trim();
                Path commitPath = cemDir.resolve(ECHO_DIR).resolve(newSha[i].substring(0,2)).resolve(newSha[i].substring(2));
                Path paretnDir = commitPath.getParent();
                if(!Files.exists(paretnDir)){
                    Files.createDirectories(paretnDir);
                }
                Files.deleteIfExists(commitPath);
                Files.createFile(commitPath);
                int len = Integer.parseInt(ObjectUtils.readLine(bin).trim());
                byte[] raw = bin.readNBytes(len);
                Files.write(commitPath,raw, StandardOpenOption.WRITE);
            }
            //System.out.println(Arrays.toString(newSha));
            Path fetchPath = cemDir.resolve(FETCH_FILE);
            ObjectUtils.addToFile(fetchPath,newSha);
        }
    }
}
