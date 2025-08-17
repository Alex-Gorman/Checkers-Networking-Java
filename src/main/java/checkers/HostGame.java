package checkers;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Host-side game panel. Wires MVC components, lays out the board/chat/score UI,
 * and runs a background thread to receive messages from a connected client.
 *
 * <p><strong>Networking protocol (incoming from client):</strong></p>
 * <ul>
 *   <li>Messages starting with <code>*</code> → chat line (forward to {@link GameModel#receiveChatMessage(String)}).</li>
 *   <li>Messages starting with <code>@</code> → init/handshake (username) ({@link GameModel#receiveInitMessage(String, boolean)}).</li>
 *   <li>Exact message <code>"%"</code> → client requested quit → close sockets and return to main menu.</li>
 *   <li>Otherwise → move payload:
 *     <ul>
 *       <li>length ≤ 8 → single move ({@link GameModel#takeIncomingMove(String)})</li>
 *       <li>length &gt; 8 → multi-jump chain ({@link GameModel#takeIncomingMultipleMove(String)})</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>Threading:</strong> A dedicated background thread reads from the socket.
 * The model notifies views; UI updates should ultimately occur on the EDT.</p>
 */
public class HostGame extends JPanel {

    /** Connected client socket (once a client joins). */
    Socket clientSocket;

    /** Server socket used by the host to accept a client (closed on quit). */
    ServerSocket serverSocket;

    /** Shared model for host-side session. */
    GameModel gameModel;

    /**
     * Constructs the host game UI and wires MVC.
     *
     * @param host {@code true} for host perspective (affects initial board setup)
     */
    public HostGame(Boolean host) {

        /* ----- MVC setup ----- */
        GameView gameView = new GameView(host);
        gameModel = new GameModel(true);
        GameController gameController = new GameController();

        gameView.setModel(gameModel);
        gameView.setController(gameController);
        gameController.setModel(gameModel);

        // Views subscribe to model updates
        gameModel.addSubscriber(gameView);

        // Build tiles and initial positions
        gameView.initializeBoardChips();
        gameView.setPreferredSize(new Dimension(600, 600));

        // Chat panel
        ChatView chatView = new ChatView(host);
        chatView.setModel(gameModel);
        chatView.setController(gameController);
        gameModel.addSubscriber(chatView);
        chatView.setPreferredSize(new Dimension(300, 500));

        // Scoreboard
        ScoreBoard scoreBoard = new ScoreBoard();
        scoreBoard.setModel(gameModel);
        scoreBoard.setController(gameController);
        gameModel.addSubscriber(scoreBoard);
        scoreBoard.setPreferredSize(new Dimension(300, 100));

        // Quit game button
        JButton quitButton = new JButton("Quit Game");
        quitButton.setFont(new Font("SAN_SERIF", Font.PLAIN, 16));
        quitButton.setMaximumSize(new Dimension(80, 30));
        quitButton.addActionListener(e -> gameController.quitGame());

        // Panel styling
        setBackground(Theme.WINDOW_BG); setOpaque(true);
        JPanel quitPanel = new JPanel();
        quitPanel.add(quitButton);
        quitPanel.setBackground(Theme.WINDOW_BG); quitPanel.setOpaque(true);

        /* ----- Layout ----- */
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7,7,7,7);

        // Board (left), big and stretchy
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.gridheight = 3;
        gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        add(gameView, gbc);

        // Score (top-right)
        gbc.gridx = 2; gbc.gridy = 0; gbc.gridwidth = 1; gbc.gridheight = 1;
        gbc.weightx = 0; gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        add(scoreBoard, gbc);

        // Chat (middle-right)
        gbc.gridx = 2; gbc.gridy = 1;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        add(chatView, gbc);

        // Quit (bottom-right)
        gbc.gridx = 2; gbc.gridy = 2;
        gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE;
        add(quitPanel, gbc);
    }

    /** Sends the host's username to the client using the init prefix. */
    public void sendInitMsg (){
        gameModel.sendInitMessage(gameModel.hostName);
    }

    /**
     * Injects the accepted client socket and forwards it to the model.
     * @param fd connected client socket
     */
    public void addClientSocket(Socket fd) {
        clientSocket = fd;
        gameModel.addSocket(clientSocket);
    }

    /**
     * Injects the server socket (so the model can close it during teardown).
     * @param fd server socket
     */
    public void addServerSocket(ServerSocket fd) {
        serverSocket = fd;
        gameModel.setServerSocket(serverSocket);
    }

    /** Sets the host display name used in chat/init messages. */
    public void setHostUsername(String hostUsername){
        gameModel.setHostName(hostUsername);
    }

    /**
     * Starts the background receive loop thread that listens for client messages.
     * The thread exits on socket close or I/O error.
     */
    public void startMessaging() {
        Thread thread = new Thread(new MyRunnable(), "host-recv-loop");
        thread.start();
    }

    /**
     * Runnable that reads messages from the connected client and routes them to the model.
     */
    public class MyRunnable implements Runnable {

        @Override
        public void run() {
            try {
                // Outer loop kept from original code (not strictly necessary); inner loop does the reads.
                while (true) {
                    DataInputStream din = new DataInputStream(clientSocket.getInputStream());
                    gameModel.setDataOutStream(new DataOutputStream(clientSocket.getOutputStream()));

                    // Blocking read loop
                    while (true) {
                        String msg = din.readUTF();

                        if (msg.charAt(0) == '*') {
                            gameModel.receiveChatMessage(msg);

                        } else if (msg.charAt(0) == '@') {
                            gameModel.receiveInitMessage(msg, true);

                        } else if (msg.equals("%")) {
                            // Client asked to terminate; close sockets and return to menu
                            clientSocket.close();
                            serverSocket.close();
                            gameModel.toMainMenu();

                        } else {
                            // Move payload: single vs multi-jump
                            if (msg.length() <= 8) gameModel.takeIncomingMove(msg);
                            else                   gameModel.takeIncomingMultipleMove(msg);

                            // After a move, check for any jump opportunities
                            gameModel.canJump();
                        }
                    }
                }
            } catch (IOException e) {
                // Any I/O issue → navigate back to main menu
                gameModel.toMainMenu();
            }
        }
    }

    /** Allows returning to the main menu (delegated to the model). */
    public void setMainMenu(MainMenu mainMenu){
        gameModel.setMainMenu(mainMenu);
    }

    /** Passes frame reference to model for panel swapping / UI transitions. */
    public void setFrame(JFrame frame){
        gameModel.setFrame(frame);
    }
}
