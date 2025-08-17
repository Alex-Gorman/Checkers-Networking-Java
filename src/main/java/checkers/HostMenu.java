package checkers;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.net.*;
import java.io.*;
import java.util.Objects;

/**
 * HostMenu is the screen where a user configures and starts a host session.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Collect username and port from the host user.</li>
 *   <li>Start a {@link ServerSocket} and accept a client connection with a timeout.</li>
 *   <li>On success, construct {@link HostGame}, wire sockets, and swap the panel into the frame.</li>
 *   <li>Provide a "Main Menu" button to go back.</li>
 * </ul>
 *
 * <p><strong>UX details:</strong> Text fields use placeholder text cleared on focus.
 * Ports are validated against a narrow range (30,000–40,000) and default to 30,000.</p>
 */
public class HostMenu extends JPanel {

    /** Reference to main menu to allow navigation back. */
    MainMenu mainMenu;

    /** The application frame (for panel swapping). */
    JFrame frame;

    /** Button to return to the main menu. */
    JButton mainMenuButton;

    /** Host display name / username. */
    JTextField nameTextField = new JTextField("Enter Your Username");

    /** Port the server will listen on. */
    JTextField portNumberTextField = new JTextField("Enter Port Number");

    /** Host-side game panel (created on connect). */
    HostGame hostGame;

    /** Server socket (listening for a single client). */
    ServerSocket serverSocket;

    /**
     * Creates the host configuration screen.
     * @param frame top-level frame used for panel swapping and dialogs
     */
    public HostMenu(JFrame frame) {
        this.frame = frame;

        /* ----- Top controls ----- */

        // Button to go back to main menu
        mainMenuButton = new JButton("Main Menu");
        mainMenuButton.setPreferredSize(new Dimension(350, 100));
        mainMenuButton.setFont(new Font("", Font.PLAIN, 20));

        // Clear placeholder on focus for port field; restore if left empty
        portNumberTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (Objects.equals(portNumberTextField.getText(), "Enter Port Number")) {
                    portNumberTextField.setText("");
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (Objects.equals(portNumberTextField.getText(), "")) {
                    portNumberTextField.setText("Enter Port Number");
                }
            }
        });

        // Clear placeholder on focus for name field; restore if left empty
        nameTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (Objects.equals(nameTextField.getText(), "Enter Your Username")) {
                    nameTextField.setText("");
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (Objects.equals(nameTextField.getText(), "")) {
                    nameTextField.setText("Enter Your Username");
                }
            }
        });

        // Button to start hosting and accept a client
        JButton connectButton = new JButton("Connect");
        connectButton.setFont(new Font("", Font.PLAIN, 20));
        connectButton.setBackground(Color.GREEN);
        connectButton.setPreferredSize(new Dimension(350, 100));
        connectButton.addActionListener(e -> {
            try {
                Connect();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        /* ----- Layout ----- */
        setBackground(Theme.MENU_BG);

        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);
        GridBagConstraints gbc = new GridBagConstraints();

        // General spacing and horizontal stretch for fields
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0, col 0: Port field
        gbc.gridx = 0; gbc.gridy = 0;
        add(portNumberTextField, gbc);

        // Row 0, col 1: Name field
        gbc.gridx = 1; gbc.gridy = 0;
        add(nameTextField, gbc);

        // Row 2 (span 2 columns): Connect button
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        add(connectButton, gbc);

        // Row 3 (span 2 columns): Main menu button
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        add(mainMenuButton, gbc);
    }

    /**
     * Supplies the main menu reference so the "Main Menu" button can swap back.
     * @param mainPanel main menu panel instance
     */
    public void addMainPanel(MainMenu mainPanel){
        this.mainMenu = mainPanel;
        mainMenuButton.addActionListener(e -> {
            frame.getContentPane().removeAll();
            frame.getContentPane().add(mainPanel);
            frame.revalidate();
            frame.repaint();
        });
    }

    /**
     * Simple numeric check used for validating the port field.
     * @param strNum text to check
     * @return true if the string parses as a number
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
     * Starts the server socket on the chosen port, accepts a single client connection
     * with a short timeout, and on success constructs {@link HostGame}, wires sockets,
     * begins the messaging loop, and swaps the panel into the frame.
     *
     * <p>Defaults to port 30000. Only ports in [30000, 40000] are accepted from the field.</p>
     *
     * @throws IOException if server socket creation/close fails
     */
    private void Connect() throws IOException {

        // Build host-side game panel up-front (only shown after a client connects)
        hostGame = new HostGame(true);
        hostGame.setMainMenu(mainMenu);
        hostGame.setFrame(frame);

        System.out.println("Server started.");
        int portNumber = 30000;

        // If we had a previous server socket, close it first
        if (serverSocket != null) {
            serverSocket.close();
        }

        // Validate optional port input
        if (isNumeric(portNumberTextField.getText())
                && (Integer.parseInt(portNumberTextField.getText()) >= 30000)
                && (Integer.parseInt(portNumberTextField.getText()) <= 40000)) {
            portNumber = Integer.parseInt(portNumberTextField.getText());
        }

        // Create the server socket
        try {
            serverSocket = new ServerSocket(portNumber);
        } catch (Exception e) {
            System.out.println("Error: server socket can't work with port " + portNumber);
            return;
        }

        // Accept timeout (milliseconds). 1 s = 1000 ms → here we use 10 s.
        serverSocket.setSoTimeout(10000);

        try {
            // Block until a client connects or the accept() call times out
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client socket accepted in server");

            // Wire sockets into the HostGame/Model
            hostGame.addClientSocket(clientSocket);
            hostGame.addServerSocket(serverSocket);

            // Optional host username (if user changed the placeholder)
            if (!Objects.equals(nameTextField.getText(), "Enter Your Username")) {
                hostGame.setHostUsername(nameTextField.getText());
            }

            // Start background messaging and send initial host name
            hostGame.startMessaging();
            hostGame.sendInitMsg();

            // Swap HostGame into the frame
            frame.getContentPane().removeAll();
            frame.getContentPane().add(hostGame);
            frame.revalidate();
            frame.repaint();

        } catch (SocketTimeoutException e) {
            // Handle timeout (no client connected within 10s). Keeping silent per original code.
        }
    }
}

