package Client.src;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Background thread that reads messages from the server and routes them
 * to the appropriate GUI method on the Event Dispatch Thread.
 */
public class ClientReceiver implements Runnable {

    private final BufferedReader in;
    private final ChatClientGUI gui;

    public ClientReceiver(BufferedReader in, ChatClientGUI gui) {
        this.in  = in;
        this.gui = gui;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String msg = message;

                // USERS_LIST protocol: server sends "USERS:alice,bob,charlie"
                if (msg.startsWith("USERS:")) {
                    String[] names = msg.substring(6).split(",");
                    List<String> users = new ArrayList<>();
                    for (String n : names) {
                        if (!n.isBlank()) users.add(n.trim());
                    }
                    gui.updateUserList(users);

                // JOIN event: extract username and add to sidebar
                } else if (msg.startsWith("🟢 ") && msg.contains(" joined the chat")) {
                    String user = msg.replace("🟢 ", "").replace(" joined the chat", "").trim();
                    gui.addUser(user);
                    SwingUtilities.invokeLater(() -> gui.appendSystemMessage(msg));

                // LEAVE / DISCONNECT event: remove from sidebar
                } else if ((msg.startsWith("❌ ") && (msg.contains(" disconnected") || msg.contains(" left the chat")))
                        || (msg.startsWith("🔨") && msg.contains("kicked"))) {
                    String[] parts = msg.split(" ");
                    if (parts.length >= 2) {
                        String user = parts[1];
                        gui.removeUser(user);
                    }
                    SwingUtilities.invokeLater(() -> gui.appendSystemMessage(msg));

                // System / server announcements
                } else if (msg.startsWith("⚠") || msg.startsWith("📢")) {
                    SwingUtilities.invokeLater(() -> gui.appendSystemMessage(msg));

                // Regular / private messages
                } else {
                    SwingUtilities.invokeLater(() -> gui.appendMessage(msg));
                }
            }
        } catch (Exception e) {
            gui.setConnected(false);
            gui.updateStatus("✘ Disconnected from server", new Color(255, 90, 90));
            gui.appendSystemMessage("⚠ Disconnected from server.");
        }
    }
}
