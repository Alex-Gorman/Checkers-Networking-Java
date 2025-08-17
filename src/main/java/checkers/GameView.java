package checkers;

import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * The board UI (View in MVC).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Owns the 8×8 grid of {@link GameTile}s and wires tile clicks to the controller.</li>
 *   <li>Observes the {@link GameModel} and repaints pieces/highlights on updates.</li>
 *   <li>Provides helpers to initialize the starting positions for host/client perspectives.</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This class extends AWT {@link Panel}. The rest of the UI uses Swing
 * (e.g., {@code JButton}, {@code JPanel}). Mixing AWT/Swing can cause minor L&F/opacity quirks.
 * If you ever refactor, consider switching this to {@code JPanel} and using Swing-only.</p>
 */
public class GameView extends Panel implements GameModelSubscriber {

    /** Controller that receives user interactions from this view. */
    GameController gameController;

    /** Model that holds the board state, pieces, and rules. */
    GameModel model;

    /** Whether this view is shown on the host side (affects initial layout). */
    Boolean host;

    /** Piece colors (theme). */
    Color black = new Color(21, 21, 21, 255);
    Color white = new Color(225, 220, 185, 255);

    /**
     * Creates the view and sets an 8×8 grid layout to hold {@link GameTile}s.
     * @param host {@code true} if rendered for the host perspective; {@code false} for client
     */
    public GameView(Boolean host) {
        this.host = host;
        setLayout(new GridLayout(8, 8));
    }

    /**
     * Builds all 64 {@link GameTile}s, adds them to this panel,
     * initializes piece positions for host or client, and forces an initial repaint.
     *
     * <p>Call this after wiring {@link #setModel(GameModel)}.</p>
     */
    public void initializeBoardChips() {
        // Create the tiles backing the board UI and give each tile a reference to this view.
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                model.getTiles()[row][col] = new GameTile(row, col, this);
                this.add(model.getTiles()[row][col]);
            }
        }

        if (host) initializeHostGameBoard();
        else      initializeClientGameBoard();

        // Render initial state
        modelUpdated();
    }

    /**
     * Wires the model; must be called before {@link #initializeBoardChips()}.
     * @param newGameModel the model to observe
     */
    public void setModel(GameModel newGameModel) {
        model = newGameModel;
    }

    /**
     * Forwards a tile click (with its board coordinates) to the controller.
     * @param e   action event originating from the tile button
     * @param row row index of the clicked tile
     * @param col column index of the clicked tile
     */
    public void takeButtonData(ActionEvent e, int row, int col) {
        gameController.handleTileClick(e, row, col);
    }

    /**
     * Wires the controller that handles user interactions.
     * @param newController controller for this view
     */
    public void setController(GameController newController) {
        this.gameController = newController;
    }

    /**
     * Reconciles the UI with the model:
     * <ol>
     *   <li>Clears icons/highlights from all tiles.</li>
     *   <li>Places player-one and player-two pieces at their current positions.</li>
     *   <li>Highlights legal destinations and jump-capable pieces.</li>
     * </ol>
     *
     * <p>If model notifications may arrive off the EDT (e.g., from networking),
     * you could wrap this body in {@code SwingUtilities.invokeLater(...)} to be extra safe.</p>
     */
    @Override
    public void modelUpdated() {

        // 1) Clear all pieces and restore original tile colors
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                model.getTiles()[row][col].clearPiece();
                model.getTiles()[row][col].setBackgroundToOriginalColor();
            }
        }

        // 2) Re-draw all pieces in their updated positions
        model.getPlayerOnePieces().forEach(player ->
                model.getTiles()[player.row][player.col].assignPiece(player));

        model.getPlayerTwoPieces().forEach(player ->
                model.getTiles()[player.row][player.col].assignPiece(player));

        // 3) Highlight legal destinations and jump options
        model.getTilesPlayerCanMoveTo().forEach(tile -> tile.setBackground(Color.ORANGE));
        model.tilesOfPiecesThatCanJump.forEach(tile -> tile.setBackground(Color.cyan));
    }

    /**
     * Places the starting pieces for the host viewpoint.
     * Player two pieces populate the top three rows (on dark squares),
     * player one pieces populate the bottom three rows.
     */
    public void initializeHostGameBoard() {
        // Player 2 (top)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    model.addPiecePlayerTwo(new Piece(row, col, false, white));
                }
            }
        }

        // Player 1 (bottom)
        for (int row = 5; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    model.addPiecePlayerOne(new Piece(row, col, true, black));
                }
            }
        }
    }

    /**
     * Places the starting pieces for the client viewpoint.
     * Same dark-square pattern, but piece colors can be flipped by perspective.
     */
    public void initializeClientGameBoard() {
        // Player 2 (top)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    model.addPiecePlayerTwo(new Piece(row, col, false, black));
                }
            }
        }

        // Player 1 (bottom)
        for (int row = 5; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    model.addPiecePlayerOne(new Piece(row, col, true, white));
                }
            }
        }
    }
}


