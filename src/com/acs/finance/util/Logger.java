package com.acs.finance.util;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * Утилитный класс для логирования в приложении.
 * Использует java.util.logging (JUL) - встроенный в JDK.
 * Логи пишутся в консоль и в файл logs/app.log
 */
public class Logger {
	private static final java.util.logging.Logger logger;
	private static final String LOG_DIR = "logs";
	private static final String LOG_FILE = LOG_DIR + File.separator + "app.log";
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	static {
		logger = java.util.logging.Logger.getLogger("FinTrack");
		logger.setUseParentHandlers(false);
		logger.setLevel(Level.ALL);

		try {
			// Создаем директорию для логов
			File logDir = new File(LOG_DIR);
			if (!logDir.exists()) {
				logDir.mkdirs();
			}

			// Форматтер для логов
			SimpleFormatter formatter = new SimpleFormatter() {
				@Override
				public String format(LogRecord record) {
					String timestamp = LocalDateTime.now().format(DATE_FORMAT);
					String level = record.getLevel().getName();
					String className = record.getSourceClassName();
					if (className != null && className.contains(".")) {
						className = className.substring(className.lastIndexOf('.') + 1);
					}
					String method = record.getSourceMethodName();
					String message = record.getMessage();
					Throwable thrown = record.getThrown();

					StringBuilder sb = new StringBuilder();
					sb.append(timestamp).append(" [").append(level).append("] ");
					if (className != null) sb.append(className);
					if (method != null) sb.append(".").append(method);
					sb.append(": ").append(message);
					if (thrown != null) {
						sb.append("\n").append(getStackTrace(thrown));
					}
					sb.append("\n");
					return sb.toString();
				}

				private String getStackTrace(Throwable t) {
					java.io.StringWriter sw = new java.io.StringWriter();
					java.io.PrintWriter pw = new java.io.PrintWriter(sw);
					t.printStackTrace(pw);
					return sw.toString();
				}
			};

			// Консольный handler
			ConsoleHandler consoleHandler = new ConsoleHandler();
			consoleHandler.setLevel(Level.INFO);
			consoleHandler.setFormatter(formatter);
			logger.addHandler(consoleHandler);

			// Файловый handler с ротацией (10MB, 5 файлов)
			FileHandler fileHandler = new FileHandler(LOG_FILE, 10 * 1024 * 1024, 5, true);
			fileHandler.setLevel(Level.ALL);
			fileHandler.setFormatter(formatter);
			logger.addHandler(fileHandler);

		} catch (IOException e) {
			System.err.println("Failed to initialize logger: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static void info(String message) {
		logger.info(message);
	}

	public static void info(String message, Object... args) {
		logger.info(String.format(message, args));
	}

	public static void warning(String message) {
		logger.warning(message);
	}

	public static void warning(String message, Object... args) {
		logger.warning(String.format(message, args));
	}

	public static void error(String message) {
		logger.severe(message);
	}

	public static void error(String message, Throwable throwable) {
		logger.log(Level.SEVERE, message, throwable);
	}

	public static void error(String message, Object... args) {
		logger.severe(String.format(message, args));
	}

	public static void debug(String message) {
		logger.fine(message);
	}

	public static void debug(String message, Object... args) {
		logger.fine(String.format(message, args));
	}

	public static void trace(String message) {
		logger.finer(message);
	}

	public static void trace(String message, Object... args) {
		logger.finer(String.format(message, args));
	}
}

