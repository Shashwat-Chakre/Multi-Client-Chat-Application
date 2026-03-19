package Client.src;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatClientGUI {

    // ── Colour palette ───────────────────────────────────────────────────────
    private static final Color BG_DARK        = new Color(18, 18, 30);
    private static final Color BG_PANEL       = new Color(28, 28, 45);
    private static final Color BG_SIDEBAR     = new Color(22, 22, 38);
    private static final Color BG_INPUT       = new Color(35, 35, 55);
    private static final Color ACCENT         = new Color(108, 92, 231);
    private static final Color ACCENT_HOVER   = new Color(130, 115, 255);
    private static final Color TEXT_PRIMARY   = new Color(235, 235, 245);
    private static final Color TEXT_SECONDARY = new Color(155, 155, 180);
    private static final Color ONLINE_DOT     = new Color(68, 220, 140);
    private static final Color MSG_SELF_BG    = new Color(108, 92, 231, 60);
    private static final Color MSG_OTHER_BG   = new Color(45, 45, 68);
    private static final Color PRIVATE_BG     = new Color(220, 120, 60, 55);
    private static final Color SYSTEM_FG      = new Color(100, 180, 255);
    private static final Color SEPARATOR      = new Color(50, 50, 75);

    // ── UI components ────────────────────────────────────────────────────────
    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField messageField;
    private JButton sendButton;
    private DefaultListModel<String> userModel;
    private JLabel statusLabel;
    private JLabel headerUsernameLabel;

    // ── Network ──────────────────────────────────────────────────────────────
    private PrintWriter out;
    private String username;
    private volatile boolean connected = false;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    public ChatClientGUI() {
        buildFrame();
        connectServer();
    }

    // ── Frame construction ───────────────────────────────────────────────────

    private void buildFrame() {
        frame = new JFrame("ChatApp — Professional");
        frame.setSize(950, 650);
        frame.setMinimumSize(new Dimension(750, 500));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BG_DARK);
        frame.setUndecorated(false);

        frame.add(buildHeader(),  BorderLayout.NORTH);
        frame.add(buildCenter(),  BorderLayout.CENTER);
        frame.add(buildInputBar(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, SEPARATOR),
                new EmptyBorder(12, 20, 12, 20)));

        // Left: logo + title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel logo = new JLabel("💬");
        logo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));

        JLabel title = new JLabel("ChatApp");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_PRIMARY);

        JLabel subtitle = new JLabel("  Multi-User");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 200));

        left.add(logo);
        left.add(title);
        left.add(subtitle);

        // Right: username chip
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        headerUsernameLabel = new JLabel("Not connected");
        headerUsernameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        headerUsernameLabel.setForeground(TEXT_SECONDARY);

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Segoe UI", Font.BOLD, 10));
        dot.setForeground(new Color(100, 100, 130));

        right.add(dot);
        right.add(headerUsernameLabel);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ── Center (sidebar + chat) ───────────────────────────────────────────────

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildSidebar(),
                buildChatArea());
        split.setDividerLocation(200);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(SEPARATOR);
        return split;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(200, 0));

        // Title
        JLabel sideLabel = new JLabel("  ONLINE");
        sideLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        sideLabel.setForeground(TEXT_SECONDARY);
        sideLabel.setBorder(new EmptyBorder(14, 12, 8, 12));
        sidebar.add(sideLabel, BorderLayout.NORTH);

        // User list
        userModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setForeground(TEXT_PRIMARY);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setSelectionBackground(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 60));
        userList.setSelectionForeground(TEXT_PRIMARY);
        userList.setBorder(new EmptyBorder(4, 8, 4, 8));

        // Custom cell renderer — green dot + name
        userList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            cell.setOpaque(isSelected);
            cell.setBackground(isSelected
                    ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 60)
                    : BG_SIDEBAR);

            JLabel dot = new JLabel("●");
            dot.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            dot.setForeground(ONLINE_DOT);

            JLabel name = new JLabel(value);
            name.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            name.setForeground(TEXT_PRIMARY);

            cell.add(dot);
            cell.add(name);
            return cell;
        });

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(null);
        scroll.setBackground(BG_SIDEBAR);
        scroll.getViewport().setBackground(BG_SIDEBAR);
        scroll.getVerticalScrollBar().setUnitIncrement(8);
        sidebar.add(scroll, BorderLayout.CENTER);

        // Hint label
        JLabel hint = new JLabel("  Type @user msg to DM");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        hint.setForeground(new Color(100, 100, 130));
        hint.setBorder(new EmptyBorder(8, 12, 12, 12));
        sidebar.add(hint, BorderLayout.SOUTH);

        return sidebar;
    }

    private JScrollPane buildChatArea() {
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(BG_DARK);
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(BG_DARK);
        chatScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.getVerticalScrollBar().setUnitIncrement(12);
        chatScroll.setBackground(BG_DARK);

        return chatScroll;
    }

    // ── Input bar ────────────────────────────────────────────────────────────

    private JPanel buildInputBar() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_PANEL);
        wrapper.setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, SEPARATOR),
                new EmptyBorder(10, 16, 10, 16)));

        // Message field (pill-shaped)
        messageField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_INPUT);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
                super.paintComponent(g);
                g2.dispose();
            }
        };
        messageField.setOpaque(false);
        messageField.setBackground(new Color(0, 0, 0, 0));
        messageField.setForeground(TEXT_PRIMARY);
        messageField.setCaretColor(TEXT_PRIMARY);
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setBorder(new EmptyBorder(8, 16, 8, 16));
        messageField.addActionListener(e -> sendMessage());

        // Placeholder text
        messageField.setText("Type a message…");
        messageField.setForeground(TEXT_SECONDARY);
        messageField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (messageField.getText().equals("Type a message…")) {
                    messageField.setText("");
                    messageField.setForeground(TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (messageField.getText().isBlank()) {
                    messageField.setText("Type a message…");
                    messageField.setForeground(TEXT_SECONDARY);
                }
            }
        });

        // Send button
        sendButton = new JButton("Send ➤") {
            private boolean hovered = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hovered ? ACCENT_HOVER : ACCENT);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
                super.paintComponent(g);
                g2.dispose();
            }
        };
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.setOpaque(false);
        sendButton.setContentAreaFilled(false);
        sendButton.setBorderPainted(false);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.setPreferredSize(new Dimension(100, 38));
        sendButton.addActionListener(e -> sendMessage());

        // Status bar
        statusLabel = new JLabel("⚡ Connecting…");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLabel.setForeground(new Color(150, 150, 180));

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);
        inputRow.add(messageField, BorderLayout.CENTER);
        inputRow.add(sendButton,   BorderLayout.EAST);

        wrapper.add(inputRow,   BorderLayout.CENTER);
        wrapper.add(statusLabel, BorderLayout.SOUTH);

        return wrapper;
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private void connectServer() {
        username = promptUsername();
        if (username == null) {
            System.exit(0);
        }

        try {
            Socket socket = new Socket("localhost", 5000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(username);
            connected = true;

            // Update header
            headerUsernameLabel.setText(username);
            headerUsernameLabel.setForeground(ONLINE_DOT);
            updateStatus("✔ Connected as " + username, ONLINE_DOT);
            frame.setTitle("ChatApp — " + username);

            // Start receiver thread
            new Thread(new ClientReceiver(in, this)).start();

        } catch (Exception e) {
            updateStatus("✘ Cannot connect to server", new Color(255, 90, 90));
            showError("Cannot connect to the server.\nMake sure the server is running on localhost:5000.");
        }
    }

    private String promptUsername() {
        while (true) {
            String input = JOptionPane.showInputDialog(
                    frame,
                    "Enter your username:",
                    "ChatApp — Login",
                    JOptionPane.PLAIN_MESSAGE);

            if (input == null) return null;          // cancelled
            if (!input.trim().isEmpty()) return input.trim();

            JOptionPane.showMessageDialog(frame,
                    "Username cannot be empty.",
                    "Invalid Username",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ── Send message ──────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || text.equals("Type a message…")) return;
        if (!connected || out == null) {
            showError("You are not connected to the server.");
            return;
        }
        out.println(text);
        appendSelfMessage(text);
        messageField.setText("");
        messageField.setForeground(TEXT_PRIMARY);
    }

    // ── Chat display ──────────────────────────────────────────────────────────

    /**
     * Appends a message bubble received from the server.
     * Must be called on the EDT (already enforced by ClientReceiver).
     */
    public void appendMessage(String raw) {
        String time = LocalTime.now().format(TIME_FMT);

        // Determine style
        Color bg;
        Color fg = TEXT_PRIMARY;
        String display = raw;

        if (raw.startsWith("🔒 (Private)")) {
            bg = PRIVATE_BG;
        } else if (raw.contains(username + ":") || raw.startsWith("💬 " + username + ":")) {
            // Server echo of own message — skip (we already show it via appendSelfMessage)
            return;
        } else if (raw.startsWith("🟢") || raw.startsWith("❌") || raw.startsWith("🔨") || raw.startsWith("⚠")) {
            bg = new Color(0, 0, 0, 0);
            fg = SYSTEM_FG;
        } else {
            bg = MSG_OTHER_BG;
        }

        addBubble(display, time, bg, fg, false);
    }

    /** Appends own outgoing message bubble immediately (locally, no server echo). */
    private void appendSelfMessage(String text) {
        String time = LocalTime.now().format(TIME_FMT);
        addBubble(text, time, MSG_SELF_BG, TEXT_PRIMARY, true);
    }

    private void addBubble(String text, String time, Color bg, Color fg, boolean alignRight) {
        JPanel row = new JPanel(new FlowLayout(alignRight ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 2));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel bubble = new RoundedPanel(bg, 14);
        bubble.setLayout(new BorderLayout(4, 0));
        bubble.setBorder(new EmptyBorder(6, 12, 6, 12));
        bubble.setMaximumSize(new Dimension(580, Integer.MAX_VALUE));

        JTextArea msgText = new JTextArea(text);
        msgText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        msgText.setForeground(fg);
        msgText.setOpaque(false);
        msgText.setEditable(false);
        msgText.setLineWrap(true);
        msgText.setWrapStyleWord(true);
        msgText.setFocusable(false);
        msgText.setBackground(new Color(0, 0, 0, 0));

        JLabel timeLabel = new JLabel(time);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(TEXT_SECONDARY);

        bubble.add(msgText,   BorderLayout.CENTER);
        bubble.add(timeLabel, BorderLayout.EAST);

        row.add(bubble);
        chatPanel.add(row);
        chatPanel.add(Box.createVerticalStrut(4));
        chatPanel.revalidate();

        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar sb = chatScroll.getVerticalScrollBar();
            sb.setValue(sb.getMaximum());
        });
    }

    /** Adds a full-width system / server message (join/leave/error). */
    public void appendSystemMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            label.setForeground(SYSTEM_FG);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            label.setBorder(new EmptyBorder(4, 0, 4, 0));
            chatPanel.add(label);
            chatPanel.add(Box.createVerticalStrut(2));
            chatPanel.revalidate();
        });
    }

    // ── Online users list ─────────────────────────────────────────────────────

    /** Called by ClientReceiver to refresh the sidebar user list. */
    public void updateUserList(java.util.List<String> users) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String u : users) userModel.addElement(u);
        });
    }

    /** Adds a single user to the sidebar if not already present. */
    public void addUser(String user) {
        SwingUtilities.invokeLater(() -> {
            if (!userModel.contains(user)) userModel.addElement(user);
        });
    }

    /** Removes a user from the sidebar. */
    public void removeUser(String user) {
        SwingUtilities.invokeLater(() -> userModel.removeElement(user));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void updateStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    public void setConnected(boolean value) {
        connected = value;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(frame, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ── Rounded panel helper ──────────────────────────────────────────────────

    private static class RoundedPanel extends JPanel {
        private final Color bg;
        private final int radius;

        RoundedPanel(Color bg, int radius) {
            this.bg = bg;
            this.radius = radius;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            super.paintComponent(g);
            g2.dispose();
        }
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
