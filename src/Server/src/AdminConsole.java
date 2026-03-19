package Server.src;

import java.util.Scanner;

/**
 * Runs on a background thread and listens for admin commands typed
 * into the server console.
 *
 * Commands:
 *   /clients              — list all online users
 *   /kick <username>      — disconnect a specific user
 *   /broadcast <message>  — send a server announcement to all clients
 *   /stats                — show connected user count
 *   /help                 — show command list
 */
public class AdminConsole implements Runnable {

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        printHelp();

        while (true) {
            String command = scanner.nextLine().trim();

            if (command.equals("/clients")) {
                handleClients();

            } else if (command.startsWith("/kick ")) {
                handleKick(command.substring(6).trim());

            } else if (command.startsWith("/broadcast ")) {
                handleBroadcast(command.substring(11).trim());

            } else if (command.equals("/stats")) {
                handleStats();

            } else if (command.equals("/help")) {
                printHelp();

            } else if (!command.isBlank()) {
                System.out.println("Unknown command. Type /help for options.");
            }
        }
    }

    // ── Command handlers ──────────────────────────────────────────────────────

    private void handleClients() {
        if (ChatServer.clients.isEmpty()) {
            System.out.println("[Admin] No users connected.");
        } else {
            System.out.println("[Admin] Online users (" + ChatServer.clients.size() + "):");
            ChatServer.clients.keySet().forEach(u -> System.out.println("  • " + u));
        }
    }

    private void handleKick(String user) {
        if (user.isEmpty()) {
            System.out.println("[Admin] Usage: /kick <username>");
            return;
        }
        ClientHandler target = ChatServer.clients.get(user);
        if (target != null) {
            target.sendMessage("⚠ You were kicked by admin.");
            target.disconnect();
            System.out.println("[Admin] " + user + " has been kicked.");
        } else {
            System.out.println("[Admin] User '" + user + "' not found.");
        }
    }

    private void handleBroadcast(String message) {
        if (message.isEmpty()) {
            System.out.println("[Admin] Usage: /broadcast <message>");
            return;
        }
        String announcement = "📢 [Server] " + message;
        for (ClientHandler client : ChatServer.clients.values()) {
            client.sendMessage(announcement);
        }
        ServerLogger.log("[BROADCAST] " + message);
        System.out.println("[Admin] Broadcast sent.");
    }

    private void handleStats() {
        System.out.println("[Admin] Connected users: " + ChatServer.clients.size());
    }

    private void printHelp() {
        System.out.println("─────────────────────────────────────");
        System.out.println("  Admin Console Commands:");
        System.out.println("  /clients              List online users");
        System.out.println("  /kick <user>          Kick a user");
        System.out.println("  /broadcast <msg>      Announce to all");
        System.out.println("  /stats                Show user count");
        System.out.println("  /help                 Show this menu");
        System.out.println("─────────────────────────────────────");
    }
}
