package Server.src;

import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles a single connected client: reads messages, routes private
 * messages, broadcasts to all, and cleans up on disconnect.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read username
            out.println("Enter username:");
            username = in.readLine();

            if (username == null || username.isBlank()) {
                out.println("Invalid username.");
                socket.close();
                return;
            }

            username = username.trim();

            if (ChatServer.clients.containsKey(username)) {
                out.println("Username already taken. Disconnecting.");
                socket.close();
                return;
            }

            ChatServer.clients.put(username, this);

            // Broadcast join + push updated user list
            broadcast("🟢 " + username + " joined the chat");
            broadcastUserList();
            ServerLogger.log(username + " joined");

            String message;
            while ((message = in.readLine()) != null) {

                if (message.equalsIgnoreCase("exit")) break;

                if (message.startsWith("@")) {
                    handlePrivateMessage(message);
                } else {
                    String stamped = "[" + LocalTime.now().format(TIME_FMT) + "] 💬 " + username + ": " + message;
                    broadcast(stamped);
                    ServerLogger.log(username + ": " + message);
                }
            }

        } catch (Exception e) {
            System.out.println("Connection lost: " + username);
        } finally {
            cleanup();
        }
    }

    // ── Private messaging ────────────────────────────────────────────────────

    private void handlePrivateMessage(String message) {
        String[] parts = message.split(" ", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            out.println("⚠ Usage: @username your message");
            return;
        }

        String targetUser = parts[0].substring(1);
        String msg        = parts[1];

        ClientHandler target = ChatServer.clients.get(targetUser);
        if (target != null) {
            String dm = "[" + LocalTime.now().format(TIME_FMT) + "] 🔒 (Private) " + username + ": " + msg;
            target.sendMessage(dm);
            out.println(dm);   // echo back to sender
            ServerLogger.log("(Private) " + username + " → " + targetUser + ": " + msg);
        } else {
            out.println("⚠ User '" + targetUser + "' not found.");
        }
    }

    // ── Public API used by AdminConsole ──────────────────────────────────────

    /** Send a message directly to this client. */
    public void sendMessage(String msg) {
        out.println(msg);
    }

    /** Forcibly disconnect this client. */
    public void disconnect() {
        try {
            if (username != null) {
                ChatServer.clients.remove(username);
                broadcast("❌ " + username + " was kicked by admin");
                broadcastUserList();
                ServerLogger.log(username + " was kicked");
            }
            socket.close();
        } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void broadcast(String message) {
        for (ClientHandler client : ChatServer.clients.values()) {
            client.out.println(message);
        }
    }

    /**
     * Sends the current online user list to every connected client.
     * Format: "USERS:alice,bob,charlie"
     */
    static void broadcastUserList() {
        String list = "USERS:" + String.join(",", ChatServer.clients.keySet());
        for (ClientHandler client : ChatServer.clients.values()) {
            client.out.println(list);
        }
    }

    private void cleanup() {
        try {
            if (username != null && ChatServer.clients.containsKey(username)) {
                ChatServer.clients.remove(username);
                broadcast("❌ " + username + " disconnected");
                broadcastUserList();
                ServerLogger.log(username + " disconnected");
            }
            socket.close();
        } catch (Exception ignored) {}
    }
}
