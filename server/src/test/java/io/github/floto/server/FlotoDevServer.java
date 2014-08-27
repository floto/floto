package io.github.floto.server;

public class FlotoDevServer {

	public static void main(final String args[]) {
		String definition = "definitions/floto-dev/floto-dev.js";
		String proxyUrl = "";
		if(args.length >= 1) {
			definition = args[0];
		}
		if(args.length >= 2) {
			proxyUrl = args[1];
		}
		FlotoServer
				.main(new String[] {
						"--dev",
						"--root",
						definition,
						"--proxy-url",
						proxyUrl });
	}
}
