package checkers;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.util.Objects;

/**
 * ClientMenu is the UI that lets a player connect to a running host/server.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Collects username, host IP, and port from the user.</li>
 *   <li>Attempts a socket connection with a short timeout.</li>
 *   <li>On success, constructs {@link ClientGame}, wires it to the frame, and starts I/O.</li>
 *   <li>Provides a button to return to the main menu.</li>
 * </ul>
 *
 * <p><strong>UX details:</strong> Text fields use simple placeholder text cleared on focus.
 * If the host/IP field is empty or set to a non-routable value, we default to 127.0.0.1 for
 * same-computer testing.</p>
 */
public class ClientMenu extends JPanel {

    /* ===== Collaborators & frame ===== */
    private MainMenu mainPanel;
    private final JFrame frame;

    /* ===== Controls ===== */
    private final JButton mainMenuButton = new JButton("Main Menu");
    private final JTextField userName     = new JTextField("Enter Your Username");
    private final JTextField hostIpField  = new JTextField("Enter Host IP Address");
    private final JTextField hostPortField= new JTextField("Enter Port Number");

    private ClientGame clientGame;

    /**
     * Creates the client connect screen.
     * @param frame top-level frame; used for panel swapping and dialogs
     */
    public ClientMenu(JFrame frame) {
        this.frame = frame;

        /* ---- Buttons ---- */
        mainMenuButton.setPreferredSize(new Dimension(350, 100));
        mainMenuButton.setFont(new Font("", Font.PLAIN, 20));

        JButton connect = new JButton("Connect");
        connect.setFont(new Font("", Font.PLAIN, 20));
        connect.setBackground(Color.GREEN);
        connect.setPreferredSize(new Dimension(350, 100));
        connect.addActionListener(e -> Connect()); // run connection flow

        /* ---- Placeholder behavior for text fields ---- */
        hostPortField.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) {
                if (Objects.equals(hostPortField.getText(), "Enter Port Number")) {
                    hostPortField.setText("");
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (Objects.equals(hostPortField.getText(), "")) {
                    hostPortField.setText("Enter Port Number");
                }
            }
        });

        hostIpField.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) {
                if (Objects.equals(hostIpField.getText(), "Enter Host IP Address")) {
                    hostIpField.setText("");
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (Objects.equals(hostIpField.getText(), "")) {
                    hostIpField.setText("Enter Host IP Address");
                }
            }
        });

        userName.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) {
                if (Objects.equals(userName.getText(), "Enter Your Username")) {
                    userName.setText("");
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (Objects.equals(userName.getText(), "")) {
                    userName.setText("Enter Your Username");
                }
            }
        });

        /* ---- Layout ---- */
        setOpaque(true);
        setBackground(Theme.MENU_BG);

        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);
        GridBagConstraints gbc = new GridBagConstraints();

        // Spacing and defaults
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Host IP (col 0) + Port (col 1)
        gbc.gridx = 0; gbc.gridy = 0;
        add(hostIpField, gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        add(hostPortField, gbc);

        // Row 1: Username (span 2 columns)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        add(userName, gbc);

        // Row 2: Connect (span 2 columns)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        add(connect, gbc);

        // Row 3: Main Menu (span 2 columns)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        add(mainMenuButton, gbc);
    }

    /**
     * Registers the main menu so the "Main Menu" button can navigate back.
     * @param mainPanel main menu panel to swap into the frame
     */
    public void addMainPanel(MainMenu mainPanel) {
        this.mainPanel = mainPanel;
        mainMenuButton.addActionListener(e -> {
            frame.getContentPane().removeAll();
            frame.getContentPane().add(mainPanel);
            frame.revalidate();
            frame.repaint();
        });
    }

    /**
     * Simple numeric check used for port validation.
     * @param strNum text to check
     * @return true if parseable as a number
     */
    public static boolean isNumeric(String strNum) {
        if (strNum == null) return false;
        try {
            Double.parseDouble(strNum);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    /**
     * Attempts to connect to the host using values from the text fields.
     * On success, constructs {@link ClientGame}, starts the messaging thread,
     * sends the init message, and swaps the panel into the frame.
     *
     * <p>Notes:</p>
     * <ul>
     *   <li>Defaults to 127.0.0.1 if host field is empty/placeholder/0.0.0.0.</li>
     *   <li>Accepts ports 1–65535; otherwise falls back to 30000 with a dialog.</li>
     *   <li>Forces IPv4 to avoid v4/v6 mismatches on some macOS setups.</li>
     * </ul>
     */
    private void Connect() {
        // Defaults for same-computer testing
        String defaultHost = "127.0.0.1";
        int defaultPort = 30000;

        // Read + sanitize inputs
        String hostInput = hostIpField.getText() == null ? "" : hostIpField.getText().trim();
        String portInput = hostPortField.getText() == null ? "" : hostPortField.getText().trim();
        String nameInput = userName.getText() == null ? "" : userName.getText().trim();

        // Host selection: guard against placeholder / unroutable host
        String host = (hostInput.isEmpty()
                || hostInput.equals("Enter Host IP Address")
                || hostInput.equals("0.0.0.0"))
                ? defaultHost : hostInput;

        // Port selection with validation
        int port = defaultPort;
        try {
            int p = Integer.parseInt(portInput);
            if (p >= 1 && p <= 65535) {
                port = p;
            } else {
                JOptionPane.showMessageDialog(frame,
                        "Port must be between 1 and 65535.\nUsing default: " + defaultPort);
            }
        } catch (NumberFormatException ignored) {
            // keep default
        }

        // Helpful console log for diagnostics
        System.out.printf("CLIENT: Connecting to [%s]:%d%n", host, port);

        // Prefer IPv4 to avoid Aqua/macOS IPv6 binding mismatches
        System.setProperty("java.net.preferIPv4Stack", "true");

        try {
            // Build client panel; only show it after connection succeeds
            clientGame = new ClientGame();
            clientGame.setMainMenu(mainPanel);
            clientGame.setFrame(frame);

            // Connect with a timeout so it doesn't hang forever
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 3000); // 3s timeout

            // Success: hand off socket to the game/model
            System.out.println("CLIENT: Connected to " + socket.getRemoteSocketAddress());
            clientGame.addSocket(socket);

            // Optional username
            if (!nameInput.isEmpty() && !nameInput.equals("Enter Your Username")) {
                clientGame.setClientUsername(nameInput);
            }

            // Start messaging + send init
            clientGame.startMessaging();
            clientGame.sendInitMsg();

            // Swap into the frame
            frame.getContentPane().removeAll();
            frame.getContentPane().add(clientGame);
            frame.revalidate();
            frame.repaint();

        } catch (java.net.NoRouteToHostException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "No route to host.\n" +
                    "Tried: " + host + ":" + port + "\n\n" +
                    "Tips:\n" +
                    "• Same computer: use 127.0.0.1\n" +
                    "• Two Macs on same Wi-Fi: use the host’s IPv4 (e.g., from `ipconfig getifaddr en0`)\n" +
                    "• Avoid 0.0.0.0 or link-local IPv6 without %en0");

        } catch (java.net.ConnectException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "Connection refused.\nIs the host running and listening on " + port + "?\n" +
                    "Make sure ports match and the server started first.");

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "I/O error while connecting to " + host + ":" + port + "\n" + e);
        }
    }
}

