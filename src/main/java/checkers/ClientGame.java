package checkers;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * ClientGame is the client-side game panel. It wires up MVC components (model, view, controller),
 * lays out the board, chat, and scoreboard, and runs a background thread to receive messages
 * from the server over a {@link Socket}.
 *
 * <p><strong>Networking protocol (incoming):</strong></p>
 * <ul>
 *   <li>Messages starting with <code>*</code> → chat message (forward to {@link GameModel#receiveChatMessage(String)}).</li>
 *   <li>Messages starting with <code>@</code> → init/handshake (forward to {@link GameModel#receiveInitMessage(String, boolean)}).</li>
 *   <li>Exact message <code>"%"</code> → server asked client to quit (close socket, return to main menu).</li>
 *   <li>Otherwise → move payload:
 *     <ul>
 *       <li>length ≤ 8 → single move ({@link GameModel#takeIncomingMove(String)})</li>
 *       <li>length &gt; 8 → multiple jump move ({@link GameModel#takeIncomingMultipleMove(String)})</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><strong>Threading:</strong> the socket-read loop runs on a background thread created by
 * {@link #startMessaging()}. Any Swing updates must ultimately happen on the EDT; the model
 * should notify views safely.</p>
 */
public class ClientGame extends JPanel {

    /** Connected socket to the host/server. */
    Socket socket;

    /** Shared game model (client-side). */
    GameModel gameModel;

    /** Constructs the client game panel and wires MVC + UI layout. */
    public ClientGame() {
        /* ----- MVC setup ----- */
        GameView gameView = new GameView(false);  // client-side view
        gameModel = new GameModel(false);         // client-side model
        GameController gameController = new GameController();

        gameView.setModel(gameModel);
        gameView.setController(gameController);
        gameController.setModel(gameModel);

        // Views subscribe to model updates
        gameModel.addSubscriber(gameView);

        // Initial board state: place pieces and set turn to "other player"
        gameView.initializeBoardChips();
        gameModel.setPlayerStateToOtherPlayerTurn();

        // Size the board area
        gameView.setPreferredSize(new Dimension(600, 600));

        // Chat panel (client-side)
        ChatView chatView = new ChatView(false);
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

        // Background tint for this container (semi-transparent cyan)
        this.setBackground(new Color(159, 235, 237));

        // Quit button → asks controller to perform quit logic (and inform server)
        JButton quitButton = new JButton("Quit Game");
        quitButton.setFont(new Font("SAN_SERIF", Font.PLAIN, 16));
        quitButton.setMaximumSize(new Dimension(80, 30));
        quitButton.addActionListener(e -> gameController.quitGame());

        // Panel to hold the quit button (transparent to show parent background)
        JPanel quitPanel = new JPanel();
        quitPanel.add(quitButton);
        quitPanel.setBackground(new Color(159, 235, 237, 0));

        /* ----- Layout ----- */

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

        // Chat (middle-right) — give it vertical weight so Send won't be clipped
        gbc.gridx = 2; gbc.gridy = 1;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        add(chatView, gbc);

        // Quit (bottom-right)
        gbc.gridx = 2; gbc.gridy = 2;
        gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE;
        add(quitPanel, gbc);

        
        // GridBagLayout layout = new GridBagLayout();
        // this.setLayout(layout);
        // GridBagConstraints gbc = new GridBagConstraints();
        // gbc.insets = new Insets(7, 7, 7, 7);

        // // Board (spans two columns, three rows)
        // gbc.gridx = 0;
        // gbc.gridy = 0;
        // gbc.gridheight = 3;
        // gbc.gridwidth = 2;
        // gbc.fill = GridBagConstraints.HORIZONTAL;
        // this.add(gameView, gbc);

        // // Chat (right column, middle row)
        // gbc.gridx = 2;
        // gbc.gridy = 1;
        // gbc.gridheight = 1;
        // gbc.gridwidth = 1;
        // this.add(chatView, gbc);

        // // Scoreboard (right column, top row)
        // gbc.gridx = 2;
        // gbc.gridy = 0;
        // this.add(scoreBoard, gbc);

        // // Quit (right column, bottom row)
        // gbc.gridx = 2;
        // gbc.gridy = 3;
        // this.add(quitPanel, gbc);
    }

    /** Sends an initial "hello/intro" message to the host (after socket is set). */
    public void sendInitMsg() {
        gameModel.sendInitMessage(gameModel.clientName);
    }

    /**
     * Injects the connected socket and forwards it to the model for I/O setup.
     * @param fd connected client socket
     */
    public void addSocket(Socket fd) {
        socket = fd;
        gameModel.addSocket(socket);
    }

    /**
     * Sets the client username used in outbound init and chat.
     * @param clientUsername display name
     */
    public void setClientUsername(String clientUsername) {
        gameModel.setClientName(clientUsername);
    }

    /**
     * Starts the background thread that continuously reads messages from the server.
     * The thread will exit when the socket closes or an I/O error occurs.
     */
    public void startMessaging() {
        Thread thread = new Thread(new MyRunnable(), "client-recv-loop");
        thread.start();
    }

    /**
     * Runnable that owns the blocking read loop from the server.
     * Parses messages according to the simple protocol documented on the class.
     */
    public class MyRunnable implements Runnable {
        @Override
        public void run() {
            try {
                // Set up streams once (DataInput for messages, DataOutput kept by model for sending)
                DataInputStream din = new DataInputStream(socket.getInputStream());
                gameModel.setDataOutStream(new DataOutputStream(socket.getOutputStream()));

                // Blocking read loop: each message is sent via DataOutputStream#writeUTF on the server
                while (true) {
                    String msg = din.readUTF();

                    // Route by prefix/special tokens
                    if (msg.charAt(0) == '*') {
                        gameModel.receiveChatMessage(msg);
                    } else if (msg.charAt(0) == '@') {
                        gameModel.receiveInitMessage(msg, false);
                    } else if (msg.equals("%")) {
                        // Server requested end-of-game: close socket and return to main menu
                        socket.close();
                        gameModel.toMainMenu();
                    } else {
                        // Move payload (single vs multiple jumps)
                        if (msg.length() <= 8) {
                            gameModel.takeIncomingMove(msg);
                        } else {
                            gameModel.takeIncomingMultipleMove(msg);
                        }
                        // After a move, check for any subsequent jump opportunities
                        gameModel.canJump();
                    }
                }

            } catch (IOException e) {
                // Any I/O issue (socket closed, host left, network error) → return to main menu
                gameModel.toMainMenu();
            }

            // Reached only if the loop breaks due to an exception / socket close
            System.out.println("Broke out of loop, buffer == -1");
        }
    }

    /** Allows returning to the main menu (delegated to model). */
    public void setMainMenu(MainMenu mainMenu) {
        gameModel.setMainMenu(mainMenu);
    }

    /** Passes frame reference to model for panel swapping / UI transitions. */
    public void setFrame(JFrame frame) {
        gameModel.setFrame(frame);
    }
}

