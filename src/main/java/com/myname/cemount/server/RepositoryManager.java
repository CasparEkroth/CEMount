package com.myname.cemount.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads a config file with lines:
 *    repoName=/absolute/path/to/bare-repo.git
 * and turns it into a Map<String,Path>.
 */
public class RepositoryManager {
    private final Map<String, Path> repos = new HashMap<>();

    public RepositoryManager(Path configFile) throws IOException{
        try(BufferedReader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)){
            String line;
            while ((line = r.readLine()) != null){
                line = line.trim();
                if(line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=",2);
                if(parts.length != 2){
                    throw new IOException("Invalid config line: " + line);
                }
                String name = parts[0].trim();
                Path repoPath = Paths.get(parts[1].trim()).toAbsolutePath().normalize();
                if(!Files.isDirectory(repoPath.resolve(".cemount"))){
                    throw new IOException("Not a CEM repo: " + repoPath);
                }
                repos.put(name,repoPath);
            }
        }
    }
    public Path getBareRepo(String repoName){
        Path p = repos.get(repoName);
        if(p == null){
            throw new IllegalArgumentException("No such repository: " + repoName);
        }
        return p;
    }
    /** Return the set of all repo names. */
    public Set<String> listRepoNames() {
        return Collections.unmodifiableSet(repos.keySet());
    }
}
