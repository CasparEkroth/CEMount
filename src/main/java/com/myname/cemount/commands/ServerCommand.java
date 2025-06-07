package com.myname.cemount.commands;


/**
 * ServeCommand listens on a TCP port and accepts a single `cem push` from a client.
 * Usage: cem serve <port> <path-to-bare-repo>
 * Example:
 *   On server (old laptop):
 *     $ cem serve 7842 /home/alice/repos/project.git
 *   Then the client’s PushCommand can connect to 192.168.1.50:7842 and send objects.
 */

public class ServerCommand {
    // for setting upp a server
    // cem serve --port <n>
    private static final String CEM_DIR   = ".cemount";
    private static final String REFS_DIR  = "refs";
    private static final String HEADS_DIR = "heads";
    private static final String OBJECTS   = "objects";
    private static final String HEAD_FILE = "HEAD";
}
