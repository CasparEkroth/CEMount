package com.myname.cemount.server;

import com.myname.cemount.commands.LogCommand;

import java.nio.file.Path;
import java.nio.file.Paths;

public class InfoCommand {
    private static final String CEM_DIR        = ".cemount";
    private static final String OBJECTS_SUBDIR = "objects";
    private static final String REFS_DIR       = "refs";
    private static final String HEADS_DIR      = "heads";
    private static final String HEAD_FILE      = "HEAD";

    public static void execute(Path repoRoot){
        Path cemDir    = repoRoot.resolve(CEM_DIR);
        Path objects   = cemDir.resolve(OBJECTS_SUBDIR);
        Path refsHeads = cemDir.resolve(REFS_DIR).resolve(HEADS_DIR);
        Path headFile  = cemDir.resolve(HEAD_FILE);
        // retrieve the time and sum more info if needed :)
        //LogCommand.execute(args);
    }
}
