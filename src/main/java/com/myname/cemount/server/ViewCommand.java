package com.myname.cemount.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.myname.cemount.server.ObjectUtils.zlibDecompress;

public class ViewCommand {
    private static final String CEMOUNT_DIR    = ".cemount";
    private static final String OBJECTS_SUBDIR = "objects";

    public static void execute(Path repoRoot) {
        Path objectsRoot = repoRoot.resolve(CEMOUNT_DIR).resolve(OBJECTS_SUBDIR);
        if (!Files.isDirectory(objectsRoot)) {
            System.err.println("No objects directory found at: " + objectsRoot);
            return;
        }

        try {
            Files.walk(objectsRoot)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        byte[] compressed = Files.readAllBytes(path);
                        byte[] fullBlob   = zlibDecompress(compressed);

                        // 1) split header/data
                        int idx = 0;
                        while (idx < fullBlob.length && fullBlob[idx] != 0) idx++;
                        String header = new String(fullBlob, 0, idx, StandardCharsets.UTF_8);
                        String[] hdr   = header.split(" ");
                        String type    = hdr[0];   // "blob", "commit", etc
                        byte[] payload = new byte[fullBlob.length - idx - 1];
                        System.arraycopy(fullBlob, idx + 1, payload, 0, payload.length);
                        // 2) handle each type

                        if("commit".equals(type)) {
                            System.out.printf("=== commit %s ===%n", path.getFileName());
                            String commitText = new String(payload, StandardCharsets.UTF_8);
                            System.out.println(commitText);
                            System.out.println("-- extracting referenced blobs --");
                            // look for lines "SHA filename"
                            List<String> lines = commitText.lines().collect(Collectors.toList());
                            for (String line : lines) {
                                String[] parts = line.trim().split(" ", 2);
                                if (parts.length == 2 && parts[0].matches("[0-9a-f]{40}")) {
                                    String sha = parts[0];
                                    // locate blob file under objectsRoot/aa/bb…
                                    Path blobPath = objectsRoot
                                            .resolve(sha.substring(0, 2))
                                            .resolve(sha.substring(2));
                                    if (Files.isRegularFile(blobPath)) {
                                        byte[] bcmp = Files.readAllBytes(blobPath);
                                        byte[] bfull = zlibDecompress(bcmp);
                                        // strip its header
                                        int j = 0;
                                        while (bfull[j] != 0) j++;
                                        String content = new String(
                                                bfull, j + 1, bfull.length - j - 1,
                                                StandardCharsets.UTF_8
                                        );
                                        System.out.printf(">>> %s contents:%n%s%n", parts[1], content);
                                    } else {
                                        System.err.println("Missing blob " + sha);
                                    }
                                }
                            }
                            System.out.println();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to walk objects dir: " + e.getMessage());
        }
    }

}
