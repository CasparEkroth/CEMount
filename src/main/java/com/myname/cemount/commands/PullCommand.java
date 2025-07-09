package com.myname.cemount.commands;

import com.myname.cemount.core.Pair;
import com.myname.cemount.server.ObjectUtils;
import com.myname.cemount.commands.FetchCommand;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PullCommand {
    private static final String CEM_DIR             = ".cemount";
    private static final String REFS_DIR_HEAD       = "refs/heads";
    private static final String OBJECTS             = "objects";
    private static final String FETCH_FILE          = "FETCH_HEAD";
    private static final String CONFIG              = "config";
    private static final String UPDATE_FILE         = "UPDATE";

    public static void execute(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: cem pull <remote>");
            return;
        }
        String remoteName = args[0];
        Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
        Path cemDir = repoRoot.resolve(CEM_DIR);
        Path configPath = cemDir.resolve(CONFIG);
        Map<String, String> remote = ObjectUtils.parseRemotes(configPath);

        if (!remote.containsKey(remoteName)) {
            System.err.printf("fatal: no such remote '%s'\n", remoteName);
            return;
        }

        FetchCommand.execute(new String[]{remoteName});

        String remoteUrl = remote.get(remoteName);
        String[] net = ObjectUtils.parseRemote(remoteUrl);
        String repoName = net[0];
        String serverPort = net[1];
        String serverIP = net[2];

        String branch = ObjectUtils.getBranch(cemDir);

        Path fetchFile = cemDir.resolve(FETCH_FILE);

        try (Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
             InputStream rawIn = socket.getInputStream();
             BufferedInputStream bin = new BufferedInputStream(rawIn);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {


            List<String> commitsSha = Files.readAllLines(fetchFile);
            List<Pair> obj = new ArrayList<>();
            String newHeadSha = new String();
            long date = 0;
            for(String sha : commitsSha){
                long current = ObjectUtils.getTimeStamp(cemDir, sha);
                if(current > date){
                    newHeadSha = sha;
                    date = current;
                }
                List<Pair> tmp = ObjectUtils.getShaFromCommit(cemDir,sha);
                obj.addAll(tmp);
            }

            out.write("PULL " + repoName + " " + branch + "\n");
            out.flush();

            for(Pair cObj : obj){
                String shaO = cObj.getSha();
                out.write(shaO + "\n");
                out.flush();
                Path objPath = cemDir.resolve(OBJECTS).resolve(shaO.substring(0,2)).resolve(shaO.substring(2));
                Path parentPath = objPath.getParent();
                if(!Files.exists(parentPath)){
                    Files.createDirectories(parentPath);
                }
                Files.deleteIfExists(objPath);
                Files.createFile(objPath);
                int len = Integer.parseInt(ObjectUtils.readLine(bin).trim());
                byte[] rawObj  = bin.readNBytes(len);
                Files.write(objPath,rawObj,StandardOpenOption.WRITE);
                // wright to file
                Path repo = cemDir.getParent();
                String rawName = cObj.getFileName();
                Path filePath = Paths.get(rawName);
                if (filePath.isAbsolute()) {
                    filePath = filePath.subpath(0, filePath.getNameCount());
                }
                Path currentFile  = repoRoot.resolve(filePath);
                ObjectUtils.createPath(currentFile);
                String content = ObjectUtils.readObjectText(cemDir, shaO);
                Files.writeString(currentFile,content,StandardCharsets.UTF_8);
            }
            out.write("OK \n");
            out.flush();
            ObjectUtils.updateRef(cemDir, REFS_DIR_HEAD + "/" + branch, newHeadSha);

        } catch (IOException e) {
            System.err.println("pull failed: " + e.getMessage());
        }
    }
}
