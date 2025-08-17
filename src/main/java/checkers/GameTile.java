package checkers;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

/**
 * A single square on the 8×8 checkers board.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Stores its grid position ({@code row}, {@code col}) and the {@link Piece} it holds (if any).</li>
 *   <li>Paints itself in alternating board colors and remembers its original background for resets.</li>
 *   <li>Forwards click events to {@link GameView} with its coordinates.</li>
 *   <li>Creates simple circular icons (with a gold center for kings) to visualize pieces.</li>
 * </ul>
 *
 * <p><strong>Threading:</strong> All Swing interactions should occur on the EDT.</p>
 */
public class GameTile extends JButton {

    /** Zero-based board coordinates for this tile. */
    int row, col;

    /** Piece currently on this tile, or {@code null} if empty. */
    Piece piece;

    /** Owning view to which this tile delegates click handling. */
    GameView gameView;

    /** Remembered background color so highlights can be cleared later. */
    Color originalColor;

    /**
     * Creates a tile at (row, col), sets the alternating board color,
     * and wires a click listener that passes control to {@link GameView}.
     *
     * @param row      zero-based row index (0..7)
     * @param col      zero-based column index (0..7)
     * @param gameView the parent view that handles tile clicks
     */
    public GameTile(int row, int col, GameView gameView) {

        /* Set the row and col index */
        this.row = row;
        this.col = col;

        /* Set the color of the tile (classic checkerboard dark/light). */
        // Dark squares occur when (row + col) is odd; light squares when it's even.
        if (!(row % 2 != 0 && col % 2 == 0 || row % 2 == 0 && col % 2 != 0)) {
            Color lightBrown = new Color(227,193,111,255);
            this.setBackground(lightBrown);
            originalColor = lightBrown;
        } else {
            Color darkBrown = new Color(184,139,74,255);
            this.setBackground(darkBrown);
            originalColor = darkBrown;
        }

        /* Enable clicking for gameplay. */
        this.setEnabled(true);

        /* Save reference to owning GameView so we can forward clicks. */
        this.gameView = gameView;

        /* Forward button presses with this tile's coordinates to the GameView. */
        this.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Let the view/controller handle game logic; the tile just reports where was clicked.
                gameView.takeButtonData(e, row, col);
            }
        });
    }

    /**
     * Places a piece on this tile and sets an appropriate icon (king vs. normal).
     * @param p piece to assign
     */
    public void assignPiece(Piece p) {
        piece = p;

        /* If not king, draw a single filled circle; kings get an inner gold disk. */
        if (!p.isKing()) {
            ImageIcon icon = new ImageIcon(createCircleImage(p.color));
            this.setIcon(icon);
        }
        else {
            ImageIcon icon = new ImageIcon(createCircleKingImage(p.color));
            this.setIcon(icon);
        }
    }

    /**
     * Updates the stored piece reference without repainting the icon.
     * Useful when promoting to king where caller will re-render separately.
     */
    public void assignPieceToBeKing(Piece p) {
        piece  = p;
    }

    /** Clears any piece from this tile and removes its icon. */
    public void clearPiece() {
        piece = null;
        this.setIcon(null);
    }

    /**
     * Renders a simple filled circle used for non-king pieces.
     * @param color disk color (player color)
     * @return an ARGB image with a filled circle
     */
    private static BufferedImage createCircleImage(Color color) {
        int circleSize = 55;
        BufferedImage image = new BufferedImage(circleSize, circleSize, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        g.setColor(color);
        g.fillOval(0, 0, circleSize, circleSize);
        g.dispose();
        return image;
    }

    /**
     * Renders a king piece: outer disk in {@code color} with a smaller gold disk inside.
     * @param color outer disk color (player color)
     * @return an ARGB image with a king-style double circle
     */
    private static BufferedImage createCircleKingImage(Color color) {
        int circleSize = 55;
        BufferedImage image = new BufferedImage(circleSize, circleSize, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();

        // Outer disk (player color)
        g.setColor(color);
        g.fillOval(0, 0, circleSize, circleSize);

        // Inner disk (gold accent to indicate "king")
        g.setColor(new Color(196,148,23, 255));
        int innerCircleSize = circleSize * 1/2;
        int innerCircleOffset = (circleSize - innerCircleSize) / 2;
        g.fillOval(innerCircleOffset, innerCircleOffset, innerCircleSize, innerCircleSize);

        g.dispose();
        return image;
    }

    /** Restores the original board color (useful after highlighting). */
    public void setBackgroundToOriginalColor() {
        this.setBackground(originalColor);
    }
}

