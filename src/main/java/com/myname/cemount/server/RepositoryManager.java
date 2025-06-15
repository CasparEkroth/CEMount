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
    private final Path rootDir;
    private final String defaultBranch;
    private static final String CEM_DIR = ".cemount";
    /**
     * @param configFile  existing config (can be null if you want only dynamic create)
     * @param rootDir     where to put newly created repos, e.g. "/Users/me/CEMDB"
     * @param defaultBranch  e.g. "main" or "master"
     */
    public RepositoryManager(Path configFile, Path rootDir, String defaultBranch ) throws IOException{
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.defaultBranch = defaultBranch;

        Files.createDirectories(this.rootDir);

        if (configFile != null) {
            try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length != 2) {
                        throw new IOException("Invalid config line: " + line);
                    }
                    String name = parts[0].trim();
                    Path path = Paths.get(parts[1].trim()).toAbsolutePath().normalize();
                    if (!Files.isDirectory(path.resolve(".cemount"))) {
                        throw new IOException("Not a CEM repo: " + path);
                    }
                    repos.put(name, path);
                }
            }
        }
    }
    /**
     * Convenience constructor when no config file is needed.
     * Allows: new RepositoryManager(rootDir, defaultBranch);
     */
    public RepositoryManager(Path rootDir, String defaultBranch) throws IOException {
        this(null, rootDir, defaultBranch);
    }

    public synchronized Path get(String repoName){
        return getBareRepo(repoName);
    }

    public Path getBareRepo(String repoName){
        Path p = repos.get(repoName);
        if(p == null){
            throw new IllegalArgumentException("No such repository: " + repoName);
        }
        return p;
    }

    public synchronized void create(String repoName) throws IOException {
        Path cemDir = rootDir.resolve(repoName).resolve(CEM_DIR);
        initIfMissing(cemDir, defaultBranch, repoName);
        repos.put(repoName, cemDir);
    }

    /** Return the set of all repo names. */
    public Set<String> listRepoNames() {
        return Collections.unmodifiableSet(repos.keySet());
    }

    public synchronized Path getOrCreate(String repoName) throws IOException {
        if(repos.containsKey(repoName)){
            return repos.get(repoName);
        }
        create(repoName);
        return repos.get(repoName);
    }

    public static void initIfMissing(Path cemDir, String defaultBranch, String repoName) throws IOException {
        if (Files.exists(cemDir)) return;

        System.out.println("Creating repo '" + repoName + "' at " + cemDir);

        Path objects = cemDir.resolve("objects");
        Path refs = cemDir.resolve("refs");
        Path heads = refs.resolve("heads");
        Path headFile = cemDir.resolve("HEAD");

        //System.out.println("Creating dirs: " + objects);
        Files.createDirectories(objects);
        //System.out.println("Creating dirs: " + heads);
        Files.createDirectories(heads);
        //System.out.println("Writing HEAD to: " + headFile);
        Files.writeString(headFile,
                "ref: refs/heads/" + defaultBranch + "\n",
                StandardCharsets.US_ASCII);

        System.out.println("Finished creating repo: " + repoName);
    }

}
