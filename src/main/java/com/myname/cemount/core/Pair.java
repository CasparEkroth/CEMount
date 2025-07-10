package com.myname.cemount.core;

public class Pair {
    private final String sha;
    private final String fileName;

    public Pair(String sha, String fileName) {
        this.sha = sha;
        this.fileName = fileName;
    }

    public String getSha(){
        return this.sha;
    }

    public String getFileName(){
        return this.fileName;
    }

}
