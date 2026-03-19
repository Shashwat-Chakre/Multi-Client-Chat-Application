import Server.src.ChatServer;
import Server.src.ClientHandler;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained integration tests for the ChatApplication server.
 *
 * Run with:
 *   javac -d build\test\classes -cp build\classes test\ChatServerTest.java
 *   java  -ea -cp "build\classes;build\test\classes" ChatServerTest
 *
 * Each test starts a fresh server socket on a free port, connects mock
 * clients, exercises the feature, then tears down cleanly.
 */
public class ChatServerTest {

    // ── Test registry ─────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        run("testServerAcceptsConnection",         passed, failed);
        run("testDuplicateUsernameRejected",       passed, failed);
        run("testBroadcastDelivery",               passed, failed);
        run("testPrivateMessageDelivery",          passed, failed);
        run("testMalformedPrivateMessageNoCrash",  passed, failed);
        run("testUserDisconnectCleanup",           passed, failed);
        run("testBlankUsernameRejected",           passed, failed);
        run("testUserListProtocolOnJoin",          passed, failed);

        System.out.println();
        System.out.println("══════════════════════════════════════");
        System.out.printf ("  Results: %d passed, %d failed%n",
                passed.size(), failed.size());
        System.out.println("══════════════════════════════════════");
        passed.forEach(t -> System.out.println("  ✔ " + t));
        failed.forEach(t -> System.out.println("  ✘ " + t));
        System.out.println();

        if (!failed.isEmpty()) {
            System.exit(1);
        }
    }

    private static void run(String name, List<String> passed, List<String> failed) {
        try {
            switch (name) {
                case "testServerAcceptsConnection"        -> testServerAcceptsConnection();
                case "testDuplicateUsernameRejected"      -> testDuplicateUsernameRejected();
                case "testBroadcastDelivery"              -> testBroadcastDelivery();
                case "testPrivateMessageDelivery"         -> testPrivateMessageDelivery();
                case "testMalformedPrivateMessageNoCrash" -> testMalformedPrivateMessageNoCrash();
                case "testUserDisconnectCleanup"          -> testUserDisconnectCleanup();
                case "testBlankUsernameRejected"          -> testBlankUsernameRejected();
                case "testUserListProtocolOnJoin"         -> testUserListProtocolOnJoin();
            }
            passed.add(name);
            System.out.println("  ✔ " + name);
        } catch (AssertionError | Exception e) {
            failed.add(name + " — " + e.getMessage());
            System.out.println("  ✘ " + name + " — " + e.getMessage());
        }
    }

    // ── Helper: start a mini server on a free port ────────────────────────────

    /** Starts a real ServerSocket, begins accepting in background threads. */
    private static ServerSocket startMiniServer() throws IOException {
        ServerSocket ss = new ServerSocket(0); // OS picks a free port
        Thread t = new Thread(() -> {
            try {
                while (!ss.isClosed()) {
                    try {
                        Socket client = ss.accept();
                        new Thread(new ClientHandler(client)).start();
                    } catch (IOException e) {
                        if (!ss.isClosed()) e.printStackTrace();
                    }
                }
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return ss;
    }

    /** Returns a connected client socket pair (socket + buffered streams). */
    private record Client(Socket socket, BufferedReader in, PrintWriter out) implements Closeable {
        @Override public void close() { try { socket.close(); } catch (IOException ignored) {} }
    }

    private static Client connectClient(int port) throws IOException {
        Socket s  = new Socket("localhost", port);
        s.setSoTimeout(3000);
        BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter    out = new PrintWriter(s.getOutputStream(), true);
        return new Client(s, in, out);
    }

    /** Drains up to maxLines from the reader, stops on timeout or EOF. */
    private static List<String> readLines(BufferedReader in, int maxLines) {
        List<String> lines = new ArrayList<>();
        try {
            for (int i = 0; i < maxLines; i++) {
                String line = in.readLine();
                if (line == null) break;
                lines.add(line);
            }
        } catch (IOException ignored) {} // SocketTimeoutException expected at end
        return lines;
    }

    /** Reads all available lines within a short timeout. */
    private static List<String> drain(BufferedReader in) {
        return readLines(in, 20);
    }

    private static boolean anyContains(List<String> lines, String fragment) {
        return lines.stream().anyMatch(l -> l.contains(fragment));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test Cases
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * TC-01: Server accepts a connection and prompts for a username.
     */
    static void testServerAcceptsConnection() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client c = connectClient(ss.getLocalPort())) {

            List<String> lines = drain(c.in);
            assert anyContains(lines, "username") : "Expected username prompt, got: " + lines;
        }
    }

    /**
     * TC-02: A second client with the same username is rejected.
     */
    static void testDuplicateUsernameRejected() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client c1 = connectClient(ss.getLocalPort());
             Client c2 = connectClient(ss.getLocalPort())) {

            drain(c1.in);         // consume prompt
            c1.out.println("alice");
            Thread.sleep(200);
            drain(c1.in);         // consume join events

            drain(c2.in);         // consume prompt
            c2.out.println("alice");
            Thread.sleep(200);

            List<String> c2Lines = drain(c2.in);
            assert anyContains(c2Lines, "taken") || anyContains(c2Lines, "already")
                    : "Expected rejection message, got: " + c2Lines;
            assert !ChatServer.clients.containsKey("alice") || ChatServer.clients.size() == 1
                    : "Should have at most one 'alice' entry";
        }
    }

    /**
     * TC-03: A broadcast message reaches all connected clients.
     */
    static void testBroadcastDelivery() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client alice = connectClient(ss.getLocalPort());
             Client bob   = connectClient(ss.getLocalPort())) {

            // Register both users
            drain(alice.in);
            alice.out.println("alice");
            Thread.sleep(150);
            drain(alice.in);

            drain(bob.in);
            bob.out.println("bob");
            Thread.sleep(150);
            drain(bob.in);

            // Alice broadcasts a message
            alice.out.println("Hello everyone!");
            Thread.sleep(200);

            List<String> bobReceived = drain(bob.in);
            assert anyContains(bobReceived, "Hello everyone!")
                    : "Bob should receive Alice's broadcast. Got: " + bobReceived;
        }
    }

    /**
     * TC-04: A private @user message is delivered only to the target.
     */
    static void testPrivateMessageDelivery() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client alice = connectClient(ss.getLocalPort());
             Client bob   = connectClient(ss.getLocalPort());
             Client carol = connectClient(ss.getLocalPort())) {

            drain(alice.in); alice.out.println("alice"); Thread.sleep(150); drain(alice.in);
            drain(bob.in);   bob.out.println("bob");     Thread.sleep(150); drain(bob.in);
            drain(carol.in); carol.out.println("carol");  Thread.sleep(150); drain(carol.in);

            // Alice sends private message to Bob
            alice.out.println("@bob Secret message");
            Thread.sleep(200);

            List<String> bobLines   = drain(bob.in);
            List<String> carolLines = drain(carol.in);

            assert anyContains(bobLines, "Secret message")
                    : "Bob should receive the private message. Got: " + bobLines;
            assert !anyContains(carolLines, "Secret message")
                    : "Carol should NOT receive the private message. Got: " + carolLines;
        }
    }

    /**
     * TC-05: A malformed @user with no message body does not crash the server
     *        and the client receives an error hint instead.
     */
    static void testMalformedPrivateMessageNoCrash() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client alice = connectClient(ss.getLocalPort())) {

            drain(alice.in);
            alice.out.println("alice");
            Thread.sleep(150);
            drain(alice.in);

            // Send a malformed private message (no body)
            alice.out.println("@bob");
            Thread.sleep(200);

            List<String> lines = drain(alice.in);
            // Server should still be alive (respond with usage hint)
            assert anyContains(lines, "Usage") || anyContains(lines, "usage") || anyContains(lines, "⚠")
                    : "Expected usage hint for malformed DM. Got: " + lines;

            // Verify server is still responsive
            assert ChatServer.clients.containsKey("alice")
                    : "Server should still have alice connected after malformed message";
        }
    }

    /**
     * TC-06: After a client disconnects, they are removed from the clients map.
     */
    static void testUserDisconnectCleanup() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer()) {
            Client alice = connectClient(ss.getLocalPort());

            drain(alice.in);
            alice.out.println("alice");
            Thread.sleep(200);
            drain(alice.in);

            assert ChatServer.clients.containsKey("alice")
                    : "Alice should be registered after connecting";

            alice.close(); // Forcibly close connection
            Thread.sleep(400);

            assert !ChatServer.clients.containsKey("alice")
                    : "Alice should be removed from clients map after disconnect";
        }
    }

    /**
     * TC-07: A blank username is rejected — client is disconnected.
     */
    static void testBlankUsernameRejected() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client c = connectClient(ss.getLocalPort())) {

            drain(c.in);          // consume prompt
            c.out.println("   "); // send blank/whitespace username
            Thread.sleep(200);

            List<String> lines = drain(c.in);
            // Server should either reject or forcibly close connection
            boolean rejected = anyContains(lines, "Invalid") || anyContains(lines, "invalid")
                    || anyContains(lines, "blank");
            boolean socketClosed = c.socket.isClosed() || lines.isEmpty();

            assert rejected || socketClosed
                    : "Blank username should be rejected. Got: " + lines;
        }
    }

    /**
     * TC-08: When a user joins, all clients receive a USERS: list update.
     */
    static void testUserListProtocolOnJoin() throws Exception {
        ChatServer.clients.clear();
        try (ServerSocket ss = startMiniServer();
             Client alice = connectClient(ss.getLocalPort());
             Client bob   = connectClient(ss.getLocalPort())) {

            drain(alice.in);
            alice.out.println("alice");
            Thread.sleep(200);
            drain(alice.in);

            drain(bob.in);
            bob.out.println("bob");
            Thread.sleep(200);

            List<String> aliceLines = drain(alice.in);

            // After bob joins, alice should receive a USERS: update
            assert anyContains(aliceLines, "USERS:") || anyContains(aliceLines, "bob")
                    : "Alice should receive USERS: list after Bob joins. Got: " + aliceLines;
        }
    }
}
