package com.acs.finance;

import com.acs.finance.util.Logger;

public class Main {
	public static void main(String[] args) throws Exception {
		int port = 8080;
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException ignored) {
				Logger.warning("Invalid port argument: %s, using default 8080", args[0]);
			}
		}
		
		Logger.info("Starting FinTrack server on port %d", port);
		HttpServerApp server = new HttpServerApp(port);
		
		try {
			server.start();
			Logger.info("Server running on http://localhost:%d", port);
			Logger.info("Press Ctrl+C to stop.");
			
			// Block main thread to keep server alive
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				Logger.info("Shutting down server...");
				server.stop();
				Logger.info("Server stopped");
			}));
			
			new java.util.concurrent.CountDownLatch(1).await();
		} catch (Exception e) {
			Logger.error("Failed to start server", e);
			throw e;
		}
	}
}


