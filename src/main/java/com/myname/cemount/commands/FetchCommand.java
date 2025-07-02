package com.myname.cemount.commands;

public class FetchCommand {
    private static final String CEM_DIR        = ".cemount";
    private static final String CONFIG         = "config";
    
    public static void execute(String [] args){
        //cem fetch <repoName> <remote>
        // look at the remote
        // look at the latest commit
        // send the latest commit
        if(args.length != 1){
            System.err.println("Usage: cem fetch");
            return;
        }

    }
}
