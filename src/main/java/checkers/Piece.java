package checkers;

import java.awt.Color;

/**
 * Immutable-ish game piece model (mutable position and king state).
 *
 * <p>Fields:</p>
 * <ul>
 *   <li>{@code player} – which side this piece belongs to (convention in this codebase:
 *       {@code true} = Player 1, {@code false} = Player 2).</li>
 *   <li>{@code color}  – UI color used to render the piece.</li>
 *   <li>{@code king}   – whether the piece has been promoted to king.</li>
 *   <li>{@code row,col} – current board coordinates (0-based).</li>
 * </ul>
 *
 * <p>Note: This class intentionally exposes mutable fields via setters used by the model.</p>
 */
public class Piece {
    /** Owner side (true = player one, false = player two). */
    Boolean player;

    /** Render color for this piece. */
    Color color;

    /** Promotion flag: kings can move/capture in both directions. */
    Boolean king;

    /** Current board position (0..7). */
    int row;
    int col;

    /**
     * Creates a piece at a given position for a given player/color.
     * The piece starts as a non-king.
     *
     * @param row   initial row (0..7)
     * @param col   initial column (0..7)
     * @param bool  owner side ({@code true} = Player 1, {@code false} = Player 2)
     * @param color UI color for rendering
     */
    public Piece(int row, int col, Boolean bool, Color color) {
        this.player = bool;
        this.color = color;
        this.king = false;
        this.row = row;
        this.col = col;
    }

    /** Promotes this piece to a king. */
    public void setKing() {
        this.king = true;
    }

    /**
     * Updates the board coordinates for this piece.
     * @param row new row (0..7)
     * @param col new column (0..7)
     */
    public void updateRowCol(int row, int col) {
        this.row = row;
        this.col = col;
    }

    /**
     * @return {@code true} if this piece has been promoted to king.
     */
    public Boolean isKing() {
        return king;
    }
}
