package com.acs.finance;

public class Main {
	public static void main(String[] args) throws Exception {
		int port = 8080;
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException ignored) {}
		}
		HttpServerApp server = new HttpServerApp(port);
		server.start();
		System.out.println("Server running on http://localhost:" + port);
		System.out.println("Press Ctrl+C to stop.");
		// Block main thread to keep server alive
		Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
		new java.util.concurrent.CountDownLatch(1).await();
	}
}


