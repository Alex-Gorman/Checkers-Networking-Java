package checkers;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.border.Border;

/**
 * ChatView is the chat panel shown alongside the game board.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Renders a scrollable chat log and a text input with a Send button.</li>
 *   <li>Forwards outbound messages to {@link GameController}.</li>
 *   <li>Subscribes to {@link GameModel} changes and refreshes the chat log
 *       when the model publishes new messages (via {@link #modelUpdated()}).</li>
 * </ul>
 *
 * <p><strong>Threading:</strong> All Swing interactions are expected to occur on the
 * Event Dispatch Thread (EDT). Invoke UI updates via {@link SwingUtilities#invokeLater(Runnable)}
 * if calling from non-EDT code.</p>
 */
public class ChatView extends JPanel implements GameModelSubscriber {

    /* ===== UI constants (avoid "magic numbers") ===== */
    private static final Dimension LOG_PREF_SIZE   = new Dimension(300, 400);
    private static final Dimension INPUT_PREF_SIZE = new Dimension(300, 50);
    private static final Font LOG_FONT   = new Font("SAN_SERIF", Font.PLAIN, 16);
    private static final Font INPUT_FONT = new Font("SAN_SERIF", Font.PLAIN, 16);
    private static final Font SEND_FONT  = new Font("SAN_SERIF", Font.BOLD, 14);

    // Reusable card-style border (gold line + inner padding)
    private static final Border CARD_BORDER = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 2),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
    );

    /** Background for chat area and input field (match board theme). */
    private static final Color CHAT_BG  = Theme.PARCHMENT_BG;
    /** Panel background */
    private static final Color PANEL_BG = Theme.WINDOW_BG;

    /* ===== MVC collaborators ===== */
    private GameController gameController;
    private GameModel model;

    /**
     * Whether this view sends messages as the host side.
     * Prefer primitive {@code boolean} over {@code Boolean} unless null is meaningful.
     */
    private final boolean host;

    /* ===== UI components ===== */
    private final JTextField text = new JTextField();
    private final JTextArea  textArea = new JTextArea(10, 30);
    private final JScrollPane scrollPane = new JScrollPane(textArea);

    /**
     * Creates the chat view.
     *
     * @param host {@code true} if this instance represents the host's chat endpoint; {@code false} for client.
     */
    public ChatView(boolean host) {
        this.host = host;

        // --- chat log ---
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(LOG_FONT);
        textArea.setBackground(CHAT_BG);
        textArea.setOpaque(true);

        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        scrollPane.setPreferredSize(LOG_PREF_SIZE);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setOpaque(true);
        scrollPane.setBackground(CHAT_BG);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(CHAT_BG);

        // Card-style borders
        scrollPane.setBorder(CARD_BORDER);
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        textArea.setMargin(new Insets(6, 6, 6, 6));

        // --- input + send ---
        text.setPreferredSize(INPUT_PREF_SIZE);
        text.setFont(INPUT_FONT);
        text.setBackground(CHAT_BG);
        text.setOpaque(true);
        text.setBorder(CARD_BORDER);

        JButton send = new JButton("Send");
        send.setFont(SEND_FONT);
        send.setPreferredSize(new Dimension(80, 30));
        send.addActionListener(e -> trySend());
        text.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) trySend();
            }
        });

        // --- layout ---
        setBackground(PANEL_BG);
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));  // <— replaces EmptyBorder import
        setLayout(new BorderLayout(8, 8));

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.add(text, BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(inputRow, BorderLayout.SOUTH);

        setFocusable(true);
        requestFocusInWindow();
    }

    /**
     * Wires the model; this view does not take ownership.
     * @param newGameModel game model to observe for chat updates
     */
    public void setModel(GameModel newGameModel) {
        this.model = newGameModel;
    }

    /**
     * Wires the controller used to send outbound messages.
     * @param newController controller that handles sending messages over the network
     */
    public void setController(GameController newController) {
        this.gameController = newController;
    }

    /**
     * Called by the model (via the subscriber interface) when chat data changes.
     * Rebuilds the text area from the model's chat messages.
     */
    @Override
    public void modelUpdated() {
        if (model == null) {
            return; // Defensive: nothing to render yet
        }
        // Build text once to avoid O(n^2) string concatenation on large logs.
        StringBuilder sb = new StringBuilder();
        model.getChatMessage().forEach(msg -> {
            sb.append(msg).append('\n');
        });
        textArea.setText(sb.toString());

        revalidate();
        repaint();
    }

    /**
     * Exposes the current input field contents (primarily for tests).
     * @return raw text in the input field
     */
    public String getText() {
        return text.getText();
    }

    /* ===== Helpers ===== */

    /**
     * Attempts to send the current input text through the controller.
     * Clears the input on success; shows stack traces on failure (could be replaced with a dialog).
     */
    private void trySend() {
        if (gameController == null) return;
        String payload = text.getText();
        if (payload == null) return;
        payload = payload.trim();
        if (payload.isEmpty()) return;

        try {
            gameController.handleSend(payload, host);
            text.setText("");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

