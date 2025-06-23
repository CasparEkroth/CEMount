package com.myname.cemount.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.InflaterInputStream;

public class ViewCommand {
    private static final String OBJECTS_SUBDIR = "objects";
    public static void viewRepo(Path repoRoot){
        Path objDir = repoRoot.resolve(OBJECTS_SUBDIR);
        try{
            Files.walk(objDir).filter(Files::isRegularFile).forEach(path -> {
                String sha1 = buildSha1(objDir, path);
                System.out.println("Object SHA-1: " + sha1);
                try{
                    byte[] raw = Files.readAllBytes(path);
                    byte[] decompressed = decompress(raw);
                    String header = new String(decompressed,0,Math.min(50,decompressed.length));
                    System.out.println("  Header/content preview: " + header);
                    // maybe more
                } catch (IOException e) {
                    System.err.println("Failed reading object: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("Failed to walk objects dir: " + e.getMessage());
        }
    }

    private static byte[] decompress(byte[] data) throws IOException{
        try(InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data));
            ByteArrayOutputStream out = new ByteArrayOutputStream()){
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) >= 0){
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String buildSha1(Path base, Path file){
        Path rel = base.relativize(file);
        return rel.getName(0) + rel.getName(1).toString();
    }
}
