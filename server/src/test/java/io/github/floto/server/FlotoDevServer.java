package io.github.floto.server;

public class FlotoDevServer {

	public static void main(final String args[]) {
		String definition = "definitions/floto-dev/floto-dev.js";
		if(args.length >= 0) {
			definition = args[0];
		}
		FlotoServer
				.main(new String[] {
						"--dev",
						"--root",
						definition });
	}
}
