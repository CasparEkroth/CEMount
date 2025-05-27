package com.myname.cemount.core;

import java.time.Instant;
import java.util.Map;
import java.util.List;

public class Commit {
    private final String sha;
    private final Instant timestamp;
    private final String message;
    private final Map<String, String> tree; // path → object SHA

    public Commit(String sha, Instant timestamp, String message, Map<String,String> tree) {
        this.sha = sha;
        this.timestamp = timestamp;
        this.message = message;
        this.tree = tree;
    }

    public String getSha() { return sha; }
    public Instant getTimestamp() { return timestamp; }
    public String getMessage() { return message; }
    public Map<String,String> getTree() { return tree; }
}
