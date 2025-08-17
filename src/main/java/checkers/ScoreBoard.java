package checkers;

import javax.swing.*;
import java.awt.*;

/**
 * Simple scoreboard view showing host and client names with their current scores.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Display two lines: "<hostName>: <hostScore>" and "<clientName>: <clientScore>".</li>
 *   <li>Observe {@link GameModel} and update labels when scores or names change.</li>
 * </ul>
 *
 * <p><strong>Threading:</strong> {@link #modelUpdated()} may be called from a non-EDT thread.
 * If you observe flicker or warnings, wrap label updates in
 * {@code SwingUtilities.invokeLater(() -> { ... })}.</p>
 */
public class ScoreBoard extends JPanel implements GameModelSubscriber {

    /** Controller reference (not used here, but kept for symmetry with other views). */
    GameController gameController;

    /** The model that holds names and scores. */
    GameModel model;

    /** Label lines for host (player1) and client (player2). */
    JLabel player1;
    JLabel player2;

    /** Builds the scoreboard UI (labels, layout, basic styling). */
    public ScoreBoard() {
        player1 = new JLabel("Player1: ");
        player2 = new JLabel("Player2: ");

        // Typography
        player1.setFont(new Font("SAN_SERIF", Font.BOLD, 20));
        player2.setFont(new Font("SAN_SERIF", Font.BOLD, 20));

        // Layout: two rows, left-aligned
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 5, 7);
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1;

        gbc.gridy = 0;
        add(player1, gbc);

        gbc.gridy = 1;
        add(player2, gbc);

        // Border & background theme (matches board palette)
        setBorder(BorderFactory.createLineBorder(Color.BLACK));
        setBackground(new Color(243, 221, 188));
    }

    /**
     * Wires the model and immediately renders initial values.
     * @param newGameModel the shared game model
     */
    public void setModel(GameModel newGameModel) {
        model = newGameModel;
        player1.setText(" " + model.hostName + ": " + model.hostScore);
        player2.setText(" " + model.clientName + ": " + model.clientScore);
    }

    /**
     * Wires the controller (unused here but kept for API consistency with other views).
     * @param newController controller reference
     */
    public void setController(GameController newController) {
        this.gameController = newController;
    }

    /**
     * Refreshes labels from the model whenever scores or names change.
     * Called via {@link GameModelSubscriber} notifications.
     */
    @Override
    public void modelUpdated() {
        player1.setText(" " + model.hostName + ": " + model.hostScore);
        player2.setText(" " + model.clientName + ": " + model.clientScore);
        revalidate();
        repaint();
    }
}
