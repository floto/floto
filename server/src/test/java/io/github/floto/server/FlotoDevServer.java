package io.github.floto.server;

public class FlotoDevServer {

    public static void main(String args[]) {
        FlotoServer.main(new String[] {"--dev", "--root", "definitions/floto-dev/floto-dev.js"});
    }
}
