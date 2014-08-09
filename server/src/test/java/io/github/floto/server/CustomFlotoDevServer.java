package io.github.floto.server;

import java.io.File;

public class CustomFlotoDevServer {

    public static void main(String args[]) {
        File rootFile = new File("definitions/custom-dev/custom-dev.js");
        if(!rootFile.exists()) {
            System.err.println("Root file not found, please copy definitions/custom-dev/custom-dev.js.sample to custom-dev.js");
            System.exit(1);
        }
        FlotoServer.main(new String[] {"--dev", "--root", rootFile.getPath()});
    }
}
