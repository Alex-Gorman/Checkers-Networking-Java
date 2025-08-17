package checkers;

import java.awt.event.ActionEvent;
import java.util.Objects;

/**
 * Controller for the Checkers app (MVC).
 *
 * <p>Bridges user interactions from the views (tile clicks, chat send, quit)
 * to the {@link GameModel}. Methods here are typically invoked on the Swing
 * Event Dispatch Thread (EDT) by UI listeners.</p>
 *
 * <p><strong>Threading:</strong> expected to be called on the EDT. The model is
 * responsible for notifying views safely.</p>
 */
public class GameController {

    /** The shared game model this controller manipulates. */
    GameModel gameModel;

    /** Creates an empty controller; call {@link #setModel(GameModel)} before use. */
    public GameController() { }

    /**
     * Wires the model instance that this controller will operate on.
     *
     * @param newGameModel the model to control (must not be {@code null})
     */
    public void setModel(GameModel newGameModel) {
        gameModel = newGameModel;
    }

    /**
     * Handles a board tile click from the UI.
     *
     * @param e   the originating action event (may be ignored)
     * @param row board row index of the clicked tile
     * @param col board column index of the clicked tile
     */
    public void handleTileClick(ActionEvent e, int row, int col) {
        gameModel.addTileClick(row, col);
    }

    /**
     * Handles a chat "send" action.
     *
     * @param msg    the message text; empty strings are ignored
     * @param isHost {@code true} if the sender is the host; {@code false} if the client
     */
    public void handleSend(String msg, Boolean isHost) {
        if (!Objects.equals(msg, "")) {
            gameModel.sendChatMessage(msg, isHost);
        }
    }

    /**
     * Quits the current game: navigates back to the main menu and attempts to
     * close the underlying socket. Any I/O errors on close are intentionally ignored.
     */
    public void quitGame() {
        gameModel.toMainMenu();
        try {
            gameModel.socket.close();
        } catch (Exception e) {
            // Ignore close failures; we're already returning to main menu.
        }
    }
}

