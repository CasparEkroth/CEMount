package com.myname.cemount.commands;

import com.myname.cemount.server.ObjectUtils;
import com.myname.cemount.commands.FetchCommand;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
        List<String> missing = Files.readAllLines(fetchFile, StandardCharsets.UTF_8);
        missing.removeIf(s -> s.trim().isEmpty());

        try (Socket socket = new Socket(serverIP, Integer.parseInt(serverPort));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            out.write("PULL " + repoName + " " + branch + "\n");
            out.flush();

            InputStream bin = socket.getInputStream();
            out.write(missing.size() + "\n");
            out.flush();

            for (int i = 0; i < missing.size(); i++) {
                String sha = missing.get(i);
                out.write(sha + "\n");
                out.flush();

                String line = in.readLine();
                if (line == null || line.equals("END")) break;
                int len = Integer.parseInt(line.trim());

                byte[] data = bin.readNBytes(len);
                Path objPath = cemDir
                        .resolve(OBJECTS)
                        .resolve(sha.substring(0, 2))
                        .resolve(sha.substring(2));

                if (!Files.exists(objPath.getParent())) {
                    Files.createDirectories(objPath.getParent());
                }
                Files.write(objPath, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            bin.close();

            Path updatePath = cemDir.resolve(UPDATE_FILE);
            ObjectUtils.appendToFile(fetchFile, updatePath);
            Files.deleteIfExists(fetchFile);
            Files.createFile(fetchFile);

            // === determine the new HEAD commit SHA ===
            String newHeadSha = null;
            for (String sha : missing) {
                Path shaPath = cemDir
                        .resolve(OBJECTS)
                        .resolve(sha.substring(0, 2))
                        .resolve(sha.substring(2));
                byte[] compressed = Files.readAllBytes(shaPath);
                byte[] fullBlob = ObjectUtils.zlibDecompress(compressed);

                int idx = 0;
                while (idx < fullBlob.length && fullBlob[idx] != 0) idx++;
                String header = new String(fullBlob, 0, idx, StandardCharsets.UTF_8);
                String[] hdr = header.split(" ");
                if ("commit".equals(hdr[0])) {
                    newHeadSha = sha;
                    break;
                }
            }
            if (newHeadSha == null) {
                System.err.println("fatal: no commit object found in fetched objects");
                return;
            }

            // update the branch ref
            ObjectUtils.updateRef(cemDir, REFS_DIR_HEAD + "/" + branch, newHeadSha);

            // overwrite working-tree files to match the new commit


        } catch (IOException e) {
            System.err.println("pull failed: " + e.getMessage());
        }
    }
}
