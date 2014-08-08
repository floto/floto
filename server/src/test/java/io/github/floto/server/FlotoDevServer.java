package io.github.floto.server;

import java.io.File;

public class FlotoDevServer {

    public static void main(String args[]) {
        File rootFile = new File("dev.js");
        if(!rootFile.exists()) {
            System.err.println("Root file not found, please copy dev.js.sample to dev.js");
            System.exit(1);
        }
        FlotoServer.main(new String[] {"--dev", "--root", rootFile.getPath()});
    }
}
