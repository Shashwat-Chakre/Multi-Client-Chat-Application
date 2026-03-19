package Server.src;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main chat server entry point.
 * Listens on PORT 5000 and dispatches each incoming connection
 * to a {@link ClientHandler} via a fixed thread pool.
 */
public class ChatServer {

    public static final int PORT = 5000;

    /** Thread-safe map of username → handler for all connected clients. */
    public static final ConcurrentHashMap<String, ClientHandler> clients =
            new ConcurrentHashMap<>();

    private static final ExecutorService pool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║      CHAT SERVER  — PORT " + PORT + "    ║");
        System.out.println("╚══════════════════════════════════╝");

        // Start admin console on a separate daemon thread
        Thread adminThread = new Thread(new AdminConsole());
        adminThread.setDaemon(true);
        adminThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Waiting for connections…\n");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[Server] New connection from " + socket.getInetAddress());
                pool.execute(new ClientHandler(socket));
            }

        } catch (Exception e) {
            System.out.println("[Server] Fatal error: " + e.getMessage());
        }
    }
}
