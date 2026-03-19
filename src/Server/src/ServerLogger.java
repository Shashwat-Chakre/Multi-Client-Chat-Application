package Server.src;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe server-side logger. Appends timestamped entries to
 * {@code logs/chat_log.txt}, creating the directory if needed.
 */
public class ServerLogger {

    private static final String LOG_DIR  = "logs";
    private static final String LOG_FILE = LOG_DIR + "/chat_log.txt";

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Appends a message to the log file with a timestamp.
     * Synchronized to prevent interleaved writes from multiple client threads.
     */
    public static synchronized void log(String message) {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) dir.mkdirs();

            try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                writer.println("[" + LocalDateTime.now().format(FMT) + "] " + message);
            }

        } catch (Exception e) {
            System.err.println("[Logger] Failed to write log: " + e.getMessage());
        }
    }
}
