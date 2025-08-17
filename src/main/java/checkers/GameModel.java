/* GameModel.java */
package checkers;

import javax.swing.*;
import java.awt.*;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Core game state and rules (the Model in MVC).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Owns board state (tiles, pieces, whose turn), scores, and chat log.</li>
 *   <li>Implements move/jump/king rules and state transitions for click-based input.</li>
 *   <li>Publishes updates to {@link GameModelSubscriber} views via {@link #notifySubscribers()}.</li>
 *   <li>Manages a simple network protocol for chat and moves via a socket output stream.</li>
 * </ul>
 *
 * <p><strong>Protocol tokens:</strong></p>
 * <ul>
 *   <li><code>*</code> prefix → chat</li>
 *   <li><code>@</code> prefix → init/handshake (usernames)</li>
 *   <li><code>"%"</code>       → quit/end</li>
 * </ul>
 *
 * <p><strong>Threading:</strong> Game logic may be triggered from background threads (network receive)
 * or the EDT (UI). Views should assume notifications can arrive from either and marshal to the
 * EDT as necessary.</p>
 */
public class GameModel {

    /** Discrete UI-input states for the click-driven move flow. */
    enum State { FIRST_PRESS, SECOND_PRESS, OTHER_PLAYER, CAN_JUMP, CAN_JUMP_AGAIN }

    /* ===== Core game state ===== */
    State currentState;

    int numberOfClicks; // currently not used by most flows
    ArrayList<GameModelSubscriber> subs;

    ArrayList<Piece> playerOnePieces; // "red" (from older comments)
    ArrayList<Piece> playerTwoPieces; // "black" (from older comments)

    /** 8x8 board tiles, each may hold one {@link Piece} or null. */
    GameTile[][] tiles = new GameTile[8][8];

    /** Highlight: legal destinations for the currently selected piece. */
    ArrayList<GameTile> tilesPlayerCanMoveTo = new ArrayList<>();

    /** Tiles of pieces that currently have jump opportunities. */
    ArrayList<GameTile> tilesOfPiecesThatCanJump = new ArrayList<>();

    /* Selected piece position (row/col) captured on first click. */
    int playerRow, playerCol;

    /** Buffer for outgoing move messages (constructed by {@link #createMoveString}). */
    String messageToSend = "";

    /* ===== Networking ===== */
    Socket socket;
    ServerSocket serverSocket;
    static DataOutputStream dout; // used to send protocol messages

    /* ===== Chat and names ===== */
    ArrayList<String> chatMessage = new ArrayList<>();
    String hostName   = "Player 1";
    String clientName = "Player 2";
    String chatPrefix = "*";
    String initPrefix = "@";

    /* ===== Score / UI frames ===== */
    int hostScore = 0;
    int clientScore = 0;

    MainMenu mainMenu;
    JFrame frame;

    /* ===== Session flags ===== */
    Boolean host;        // true if this model instance is running on the host
    Boolean gameStarted; // currently always true on construction

    /* Current "turn" label (username to display). */
    String turn;

    /**
     * Constructs a model for either host or client.
     * @param host {@code true} if running as the host, else client
     */
    public GameModel(Boolean host) {
        this.host = host;
        gameStarted = true;

        synchronized (this) {
            currentState = State.FIRST_PRESS;
        }
        numberOfClicks = 0;

        subs = new ArrayList<>();
        playerOnePieces = new ArrayList<>();
        playerTwoPieces = new ArrayList<>();
    }

    /* ===== Subscribers (Views) ===== */

    /** Registers a new subscriber to receive {@link #notifySubscribers()} signals. */
    public void addSubscriber(GameModelSubscriber newSub) {
        subs.add(newSub);
    }

    /** Notifies all views that observable state has changed. */
    private void notifySubscribers() {
        subs.forEach(GameModelSubscriber::modelUpdated);
    }

    /* ===== Board / pieces accessors ===== */

    /** @return the 8x8 tile array backing the board. */
    public GameTile[][] getTiles() { return tiles; }

    /** @return current legal destinations for the selected piece. */
    public ArrayList<GameTile> getTilesPlayerCanMoveTo() { return tilesPlayerCanMoveTo; }

    /** @return player one pieces (mutable list). */
    public ArrayList<Piece> getPlayerOnePieces() { return playerOnePieces; }

    /** @return player two pieces (mutable list). */
    public ArrayList<Piece> getPlayerTwoPieces() { return playerTwoPieces; }

    /** Adds a piece to player one and refreshes views. */
    public void addPiecePlayerOne(Piece p) {
        playerOnePieces.add(p);
        notifySubscribers();
    }

    /** Adds a piece to player two and refreshes views. */
    public void addPiecePlayerTwo(Piece p) {
        playerTwoPieces.add(p);
        notifySubscribers();
    }

    /* ===== Occupancy helpers ===== */

    /** @return true if any piece occupies (row,col). */
    public Boolean isOccupied(int row, int col) {
        return (tiles[row][col].piece != null);
    }

    /** @return true if a piece of the other player occupies (row,col). */
    public Boolean isOccupiedByOtherPlayer(int row, int col) {
        return (tiles[row][col].piece != null && tiles[row][col].piece.player == false);
    }

    /* ===== Legal move / jump computation ===== */

    /**
     * Recomputes basic (non-capturing) legal moves for the piece at (row,col).
     * Populates {@link #tilesPlayerCanMoveTo}. Kings can move diagonally both directions.
     */
    public void canMoveTo(int row, int col) {
        Piece p = tiles[row][col].piece;

        tilesPlayerCanMoveTo.removeAll(tilesPlayerCanMoveTo); // prefer tilesPlayerCanMoveTo.clear();

        if (!p.king) {
            // Normal piece: forward-only (toward row 0 for player-true pieces)
            if (row > 0 && col > 0 && !isOccupied(row-1, col-1)) tilesPlayerCanMoveTo.add(tiles[row-1][col-1]);
            if (row > 0 && col < 7 && !isOccupied(row-1, col+1)) tilesPlayerCanMoveTo.add(tiles[row-1][col+1]);
        } else {
            // King: can step diagonally in all directions (edge cases grouped by board edges)
            if (row == 0 && col == 7 && !isOccupied(row+1, col-1)) tilesPlayerCanMoveTo.add(tiles[row+1][col-1]);

            if (row == 0 && col < 7 && col > 0) {
                if (!isOccupied(row+1, col-1)) tilesPlayerCanMoveTo.add(tiles[row+1][col-1]);
                if (!isOccupied(row+1, col+1)) tilesPlayerCanMoveTo.add(tiles[row+1][col+1]);
            }

            if (row == 7 && col == 0 && !isOccupied(row-1, col+1)) tilesPlayerCanMoveTo.add(tiles[row-1][col+1]);

            if (row == 7 && col < 7 && col > 0) {
                if (!isOccupied(row-1, col-1)) tilesPlayerCanMoveTo.add(tiles[row-1][col-1]);
                if (!isOccupied(row-1, col+1)) tilesPlayerCanMoveTo.add(tiles[row-1][col+1]);
            }

            if (col == 7 && row < 7 && row > 0) {
                if (!isOccupied(row+1, col-1)) tilesPlayerCanMoveTo.add(tiles[row+1][col-1]);
                if (!isOccupied(row-1, col-1)) tilesPlayerCanMoveTo.add(tiles[row-1][col-1]);
            }

            if (col == 0 && row < 7 && row > 0) {
                if (!isOccupied(row+1, col+1)) tilesPlayerCanMoveTo.add(tiles[row+1][col+1]);
                if (!isOccupied(row-1, col+1)) tilesPlayerCanMoveTo.add(tiles[row-1][col+1]);
            }

            if (col < 7 && col > 0 && row < 7 && row > 0) {
                if (!isOccupied(row+1, col+1)) tilesPlayerCanMoveTo.add(tiles[row+1][col+1]);
                if (!isOccupied(row+1, col-1)) tilesPlayerCanMoveTo.add(tiles[row+1][col-1]);
                if (!isOccupied(row-1, col+1)) tilesPlayerCanMoveTo.add(tiles[row-1][col+1]);
                if (!isOccupied(row-1, col-1)) tilesPlayerCanMoveTo.add(tiles[row-1][col-1]);
            }
        }
    }

    /**
     * Appends/initializes the outbound move payload in reversed coordinates (7-indexed mirroring).
     * The message is built as "rowPrev,colPrev,rowCur,colCur[+ ...]".
     * @param moveToRow destination row
     * @param moveToCol destination col
     * @param append if true, appends with '+' for multi-jump; otherwise initializes
     * @return current message buffer (for chaining/diagnostics)
     */
    public String createMoveString(int moveToRow, int moveToCol, Boolean append) {
        if (!append) {
            messageToSend += String.valueOf(7-playerRow);
            messageToSend += ",";
            messageToSend += String.valueOf(7-playerCol);
            messageToSend += ",";
            messageToSend += String.valueOf(7-moveToRow);
            messageToSend += ",";
            messageToSend += String.valueOf(7-moveToCol);
            return messageToSend;
        } else {
            messageToSend += "+";
            messageToSend += String.valueOf(7-playerRow);
            messageToSend += ",";
            messageToSend += String.valueOf(7-playerCol);
            messageToSend += ",";
            messageToSend += String.valueOf(7-moveToRow);
            messageToSend += ",";
            messageToSend += String.valueOf(7-moveToCol);
            return messageToSend;
        }
    }

    /** Clears the move message buffer. */
    public void clearMessageToSendString() { messageToSend = ""; }

    /** @return the current move message buffer. */
    public String getMessageToSend() { return messageToSend; }

    /**
     * Computes capture opportunities for the current player and updates state to {@link State#CAN_JUMP}
     * if any jumps exist. Populates both {@link #tilesPlayerCanMoveTo} and
     * {@link #tilesOfPiecesThatCanJump}.
     * @return true if at least one jump is available
     */
    public Boolean canJump() {
        tilesPlayerCanMoveTo.removeAll(tilesPlayerCanMoveTo);         // prefer .clear()
        Boolean jumpPossible = false;
        tilesOfPiecesThatCanJump.removeAll(tilesOfPiecesThatCanJump);  // prefer .clear()

        // Check all player-one pieces for jumps over player-two pieces.
        for (Piece p: playerOnePieces) {
            int checkRow = p.row;
            int checkCol = p.col;

            // Edge cases that result in no-op (kept for readability)
            if (checkCol <= 1 && checkRow <= 1) ;
            if (checkCol >= 6 && checkRow <= 1) ;

            // Up-left jump
            if (checkCol >= 6 && checkRow >= 2) {
                if (isOccupiedByOtherPlayer(checkRow-1, checkCol-1) && !isOccupied(checkRow-2, checkCol-2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol-2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
            }

            // Up-right jump
            if (checkCol <= 1 && checkRow >= 2) {
                if (isOccupiedByOtherPlayer(checkRow-1, checkCol+1) && !isOccupied(checkRow-2, checkCol+2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol+2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
            }

            // Middle columns (both diagonal captures)
            if (checkCol >= 2 && checkCol <= 5 && checkRow >= 2) {
                if (isOccupiedByOtherPlayer(checkRow-1, checkCol+1) && !isOccupied(checkRow-2, checkCol+2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol+2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
                if (isOccupiedByOtherPlayer(checkRow-1, checkCol-1) && !isOccupied(checkRow-2, checkCol-2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol-2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
            }

            // Kings can also capture downward
            if (p.isKing()) {
                if (checkRow >= 6) ;
                if (checkRow <= 5 && checkCol <= 1) {
                    if (isOccupiedByOtherPlayer(checkRow+1, checkCol+1) && !isOccupied(checkRow+2, checkCol+2)) {
                        tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol+2]);
                        tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                        jumpPossible = true;
                    }
                }
                if (checkRow <= 5 && checkCol >= 6) {
                    if (isOccupiedByOtherPlayer(checkRow+1, checkCol-1) && !isOccupied(checkRow+2, checkCol-2)) {
                        tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol-2]);
                        tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                        jumpPossible = true;
                    }
                }
                if (checkRow <= 5 && checkCol <= 5 && checkCol >= 2) {
                    if (isOccupiedByOtherPlayer(checkRow+1, checkCol+1) && !isOccupied(checkRow+2, checkCol+2)) {
                        tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol+2]);
                        tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                        jumpPossible = true;
                    }
                    if (isOccupiedByOtherPlayer(checkRow+1, checkCol-1) && !isOccupied(checkRow+2, checkCol-2)) {
                        tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol-2]);
                        tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                        jumpPossible = true;
                    }
                }
            }
        }
        notifySubscribers();
        currentState = jumpPossible ? State.CAN_JUMP : State.FIRST_PRESS;
        return jumpPossible;
    }

    /**
     * Computes whether the provided piece {@code p} has another capture from its current spot
     * (multi-jump case). Populates {@link #tilesPlayerCanMoveTo} accordingly and sets state to
     * {@link State#CAN_JUMP_AGAIN} if possible.
     * @return true if another jump is available for the same piece
     */
    public Boolean canJumpAgain(Piece p) {
        tilesPlayerCanMoveTo.removeAll(tilesPlayerCanMoveTo);         // prefer .clear()
        Boolean jumpPossible = false;
        tilesOfPiecesThatCanJump.removeAll(tilesOfPiecesThatCanJump);  // prefer .clear()

        int checkRow = p.row;
        int checkCol = p.col;

        if (checkCol <= 1 && checkRow <= 1) ;
        if (checkCol >= 6 && checkRow <= 1) ;

        if (checkCol >= 6 && checkRow >= 2) {
            if (isOccupiedByOtherPlayer(checkRow-1, checkCol-1) && !isOccupied(checkRow-2, checkCol-2)) {
                tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol-2]);
                tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                jumpPossible = true;
            }
        }

        if (checkCol <= 1 && checkRow >= 2) {
            if (isOccupiedByOtherPlayer(checkRow-1, checkCol+1) && !isOccupied(checkRow-2, checkCol+2)) {
                tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol+2]);
                tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                jumpPossible = true;
            }
        }

        if (checkCol >= 2 && checkCol <= 5 && checkRow >= 2) {
            if (isOccupiedByOtherPlayer(checkRow-1, checkCol+1) && !isOccupied(checkRow-2, checkCol+2)) {
                tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol+2]);
                tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                jumpPossible = true;
            }
            if (isOccupiedByOtherPlayer(checkRow-1, checkCol-1) && !isOccupied(checkRow-2, checkCol-2)) {
                tilesPlayerCanMoveTo.add(tiles[checkRow-2][checkCol-2]);
                tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                jumpPossible = true;
            }
        }

        if (p.isKing()) {
            if (checkRow >= 6) ;
            if (checkRow <= 5 && checkCol <= 1) {
                if (isOccupiedByOtherPlayer(checkRow+1, checkCol+1) && !isOccupied(checkRow+2, checkCol+2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol+2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
            }
            if (checkRow <= 5 && checkCol >= 6) {
                if (isOccupiedByOtherPlayer(checkRow+1, checkCol-1) && !isOccupied(checkRow+2, checkCol-2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol-2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
            }
            if (checkRow <= 5 && checkCol <= 5 && checkCol >= 2) {
                if (isOccupiedByOtherPlayer(checkRow+1, checkCol+1) && !isOccupied(checkRow+2, checkCol+2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol+2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
                if (isOccupiedByOtherPlayer(checkRow+1, checkCol-1) && !isOccupied(checkRow+2, checkCol-2)) {
                    tilesPlayerCanMoveTo.add(tiles[checkRow+2][checkCol-2]);
                    tilesOfPiecesThatCanJump.add(tiles[checkRow][checkCol]);
                    jumpPossible = true;
                }
            }
        }

        notifySubscribers();
        if (jumpPossible) {
            currentState = State.CAN_JUMP_AGAIN;
        } else {
            tilesPlayerCanMoveTo.removeAll(tilesPlayerCanMoveTo);        // prefer .clear()
            tilesOfPiecesThatCanJump.removeAll(tilesOfPiecesThatCanJump); // prefer .clear()
        }
        return jumpPossible;
    }

    /** Clears the legal-destination highlight list. */
    public void resetTilesPlayerCanMoveTo() { tilesPlayerCanMoveTo.removeAll(tilesPlayerCanMoveTo); }

    /** Clears the "pieces that can jump" list. */
    public void resetTilesOfPiecesThatCanJump() { tilesOfPiecesThatCanJump.removeAll(tilesOfPiecesThatCanJump); }

    /** Removes a captured piece from player two at (row,col). */
    public void removePieceFromPlayer2(int row, int col) {
        for (Piece pToDelete: playerTwoPieces) {
            if (pToDelete.row == row && pToDelete.col == col) {
                playerTwoPieces.remove(pToDelete);
                break;
            }
        }
    }

    /** Removes a captured piece from player one at (row,col). */
    public void removePieceFromPlayer1(int row, int col) {
        for (Piece pToDelete: playerOnePieces) {
            if (pToDelete.row == row && pToDelete.col == col) {
                playerOnePieces.remove(pToDelete);
                break;
            }
        }
    }

    /**
     * Click handler core (Model side): processes FIRST/SECOND press logic, jump chains,
     * move validation, piece capture, and turn transitions. The corresponding view forwards
     * board coordinates into this method.
     */
    public void addTileClick(int row, int col) {

        switch (currentState) {
            case CAN_JUMP_AGAIN -> {
                Piece p = null;

                for (GameTile tile: tilesOfPiecesThatCanJump) {
                    if (tile.piece.row == playerRow && tile.piece.col == playerCol) {
                        p = tile.piece;
                        break;
                    }
                }
                if (p == null) return;

                boolean validMove = false;
                for (GameTile t: tilesPlayerCanMoveTo) {
                    if (t.col == col && t.row == row) {
                        validMove = true;
                        break;
                    }
                }
                if (!validMove) return;

                // Determine captured piece location (midpoint of the jump)
                int colToDelete = -1, rowToDelete = -1;
                if (row < playerRow) {
                    rowToDelete = row + 1;
                    colToDelete = (col > playerCol) ? col - 1 : col + 1;
                } else if (row > playerRow) {
                    rowToDelete = row - 1;
                    colToDelete = (col > playerCol) ? col - 1 : col + 1;
                }
                removePieceFromPlayer2(rowToDelete, colToDelete);

                // Update piece location
                p.row = row; p.col = col;

                // Append to multi-jump move string and king if needed
                createMoveString(row, col, true);
                if (p.row == 0) p.setKing();

                // Continue chain or end turn
                if (canJumpAgain(p)) {
                    playerRow = row; playerCol = col;
                } else {
                    playerRow = -1; playerCol = -1;
                    setPlayerStateToOtherPlayerTurn();
                }
                notifySubscribers();
            }

            case CAN_JUMP -> {
                // Select which jumping piece will be moved
                Piece p = null;
                for (GameTile tile: tilesOfPiecesThatCanJump) {
                    if (tile.piece.row == row && tile.piece.col == col) {
                        p = tile.piece;
                        break;
                    }
                }
                if (p == null) return;
                playerRow = p.row;
                playerCol = p.col;
                currentState = State.SECOND_PRESS;
            }

            case FIRST_PRESS -> {
                // Ignore clicks on opponent pieces / empty tiles
                if (isOccupiedByOtherPlayer(row, col)) return;
                if (!isOccupied(row, col)) return;

                // Compute legal steps and require at least one
                canMoveTo(row, col);
                if (tilesPlayerCanMoveTo.size() == 0) return;

                // Select it
                playerRow = row;
                playerCol = col;

                currentState = State.SECOND_PRESS;
                notifySubscribers();
            }

            case SECOND_PRESS -> {
                // Moving the previously selected piece
                Piece p = tiles[playerRow][playerCol].piece;

                boolean validMove = false;
                for (GameTile gameTile : tilesPlayerCanMoveTo) {
                    if (gameTile.row == row && gameTile.col == col) {
                        validMove = true;
                    }
                }

                if (!validMove) {
                    // Reset selection and either recompute jumps or go idle
                    playerRow = -1; playerCol = -1;
                    resetTilesPlayerCanMoveTo();

                    if (tilesOfPiecesThatCanJump.size() != 0) {
                        canJump();
                        return;
                    } else {
                        currentState = State.FIRST_PRESS;
                        notifySubscribers();
                    }
                }

                // Execute a valid move
                if (validMove) {
                    boolean justMadeAJump = false;
                    resetTilesOfPiecesThatCanJump();

                    // Initialize move message
                    createMoveString(row, col, false);

                    // Crown if reaching the opponent back rank
                    setIfKing(row, col, p);

                    // If the move spans >= 2 squares, it was a jump → delete the jumped piece
                    int colToDelete = -1, rowToDelete = -1;
                    if ((Math.abs(row-playerRow)) >= 2 || (Math.abs(col-playerCol)) >= 2) {
                        justMadeAJump = true;

                        if (row < playerRow) {
                            rowToDelete = row + 1;
                            colToDelete = (col > playerCol) ? col - 1 : col + 1;
                        } else if (row > playerRow) {
                            rowToDelete = row - 1;
                            colToDelete = (col > playerCol) ? col - 1 : col + 1;
                        }
                        removePieceFromPlayer2(rowToDelete, colToDelete);
                    }

                    // Update the piece
                    p.row = row; p.col = col;

                    // Continue multi-jump if possible; otherwise hand off turn
                    if (justMadeAJump && canJumpAgain(p)) {
                        playerRow = row; playerCol = col;
                        notifySubscribers();
                        return;
                    } else {
                        setPlayerStateToOtherPlayerTurn();
                    }
                }

                // Clear selection/highlights and refresh
                playerRow = -1; playerCol = -1;
                tilesPlayerCanMoveTo.removeAll(tilesPlayerCanMoveTo); // prefer .clear()
                notifySubscribers();
            }
        }
    }

    /** Crowns the piece {@code p} if it reaches the far end (row 0 for player-true pieces). */
    public void setIfKing(int row, int col, Piece p) {
        if (p.player && row == 0) p.setKing();
    }

    /** @return the current UI input state. */
    public State getCurrentState() { return currentState; }

    /**
     * Applies an incoming (single) move from the remote peer.
     * Message format: "rowPrev,colPrev,rowCur,colCur" in 0-based board coords.
     */
    public void takeIncomingMove(String incomingMoveMsg) {
        String[] numbers = incomingMoveMsg.split(",");
        int rowPrev = Integer.parseInt(numbers[0]);
        int colPrev = Integer.parseInt(numbers[1]);
        int rowCur  = Integer.parseInt(numbers[2]);
        int colCur  = Integer.parseInt(numbers[3]);

        // (Unused) count local pieces — kept from original code
        int count = 0; for (Piece p: playerOnePieces) { count++; }

        // Find the opponent piece to move, update board, handle capture & kinging
        for (Piece p: playerTwoPieces) {
            if (p.row == rowPrev && p.col == colPrev) {
                tiles[rowPrev][colPrev].piece = null;
                p.row = rowCur; p.col = colCur;
                tiles[rowCur][colCur].piece = p;

                // Capture detection (delta ≥ 2)
                int colToDelete = -1, rowToDelete = -1;
                if ((Math.abs(rowPrev - rowCur)) >= 2 || (Math.abs(colPrev - colCur)) >= 2) {
                    if (rowCur > rowPrev) {
                        rowToDelete = rowCur - 1;
                        colToDelete = (colCur > colPrev) ? colCur - 1 : colCur + 1;
                        removePieceFromPlayer1(rowToDelete, colToDelete);
                        notifySubscribers();
                    }
                    if (p.isKing()) {
                        if (rowPrev > rowCur) {
                            rowToDelete = rowCur + 1;
                            colToDelete = (colCur > colPrev) ? colCur - 1 : colCur + 1;
                            removePieceFromPlayer1(rowToDelete, colToDelete);
                            notifySubscribers();
                        }
                    }
                }

                // King if reaching back rank
                if (p.row == 7) { p.setKing(); }
                break;
            }
        }

        // If game ended, update scores and reinitialize board according to role
        if (checkPlayerOneLost()) {
            if (host) {
                updateScore(hostScore, clientScore + 1);
            } else {
                updateScore(hostScore + 1, clientScore);
            }

            if (host) initializeHostGameBoard();
            else      initializeClientGameBoard();

            currentState = State.OTHER_PLAYER;
            messageToSend = "";
        }

        notifySubscribers();
    }

    /**
     * Applies a chain of incoming moves separated by <code>'+'</code> (multi-jump).
     * Each segment is parsed by {@link #takeIncomingMove(String)}.
     */
    public void takeIncomingMultipleMove(String message) {
        String[] splitStrings = message.split("\\+");
        for (String s: splitStrings) {
            takeIncomingMove(s);
        }
    }

    /** Sets the local player to make a move next (used by remote hand-off). */
    public void setPlayerStateToTheirTurn() {
        currentState = State.FIRST_PRESS;
    }

    /** @return true if either side has no pieces. */
    public Boolean checkGameOver() { return (checkPlayerOneLost() || checkPlayerTwoLost()); }

    /** @return true if player one has zero pieces. */
    public Boolean checkPlayerOneLost() { return (playerOnePieces.size() == 0); }

    /** @return true if player two has zero pieces. */
    public Boolean checkPlayerTwoLost() { return (playerTwoPieces.size() == 0); }

    /** Resets piece lists and clears selection state. */
    public void resetGameVariables() {
        playerOnePieces.removeAll(playerOnePieces); // prefer .clear()
        playerTwoPieces.removeAll(playerTwoPieces); // prefer .clear()
        playerRow = -1; playerCol = -1;
    }

    /** Initializes a fresh board for the host perspective (host pieces at bottom). */
    public void initializeHostGameBoard() {
        resetGameVariables();
        currentState = State.FIRST_PRESS;

        Color black = new Color(21,21,21,255);
        Color white = new Color(225,220,185,255);

        // Player two pieces (top three rows) on dark squares
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    this.addPiecePlayerTwo(new Piece(row, col, false, white));
                }
            }
        }

        // Player one pieces (bottom three rows) on dark squares
        for (int row = 5; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    this.addPiecePlayerOne(new Piece(row, col, true, black));
                }
            }
        }
        notifySubscribers();
    }

    /** Initializes a fresh board for the client perspective (client turn starts as OTHER_PLAYER). */
    public void initializeClientGameBoard() {
        resetGameVariables();
        currentState = State.OTHER_PLAYER;

        Color black = new Color(21,21,21,255);
        Color white = new Color(225,220,185,255);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    this.addPiecePlayerTwo(new Piece(row, col, false, black));
                }
            }
        }

        for (int row = 5; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                if (row % 2 == 0 && col % 2 != 0 || row % 2 != 0 && col % 2 == 0) {
                    this.addPiecePlayerOne(new Piece(row, col, true, white));
                }
            }
        }
        notifySubscribers();
    }

    /**
     * Hands the turn to the other player (network send happens here).
     * If the game ended, updates scores and re-initializes the board based on role.
     * Sends the prepared move string via {@link #dout}, then clears it.
     */
    public void setPlayerStateToOtherPlayerTurn() {

        if (checkGameOver()) {
            if (checkPlayerTwoLost()) {
                if (host) updateScore(hostScore + 1, clientScore);
                else      updateScore(hostScore, clientScore + 1);
            } else if (checkPlayerOneLost()) {
                if (host) updateScore(hostScore, clientScore + 1);
                else      updateScore(hostScore + 1, clientScore);
            }

            if (host) initializeHostGameBoard();
            else      initializeClientGameBoard();

            currentState = State.FIRST_PRESS;
        } else {
            currentState = State.OTHER_PLAYER;
        }

        try {
            dout.writeUTF(getMessageToSend());
            clearMessageToSendString();
        } catch (Exception e) {
            // ignore send failure here; upstream UI will reset to main menu on socket errors
        }
    }

    /* ===== Networking glue (outgoing only; incoming handled by Client/Host threads) ===== */

    /** Injects a connected socket and prepares the output stream. */
    public void addSocket(Socket socket){
        this.socket = socket;
        try {
            dout = new DataOutputStream(socket.getOutputStream());
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /** Sends a chat message (and stores a local copy) with the proper name prefix. */
    public void sendChatMessage(String msg, boolean isHost){
        try {
            if (isHost){
                this.chatMessage.add(hostName + ": " + msg);
                dout.writeUTF(chatPrefix + hostName + ": " + msg);
            } else {
                this.chatMessage.add(clientName + ": " + msg);
                dout.writeUTF(chatPrefix + clientName + ": " + msg);
            }
        } catch (Exception e){
            // swallow; UI will reflect disconnection elsewhere
        }
        notifySubscribers();
    }

    /** Sends an init/handshake message (username) with the init prefix. */
    public void sendInitMessage(String msg){
        try {
            dout.writeUTF(initPrefix + msg);
        } catch (Exception e){
            e.printStackTrace();
        }
        notifySubscribers();
    }

    /** Receives a chat line from the network (already prefixed) and appends it to the log. */
    public void receiveChatMessage(String msg){
        msg = msg.substring(1); // strip '*'
        this.chatMessage.add(msg);
        notifySubscribers();
    }

    /** Receives a peer username from the network and assigns it according to our role. */
    public void receiveInitMessage(String msg, boolean isHost){
        msg = msg.substring(1); // strip '@'
        if (isHost) { setClientName(msg); }
        else        { setHostName(msg);   }
        notifySubscribers();
    }

    /** @return the chat log as a mutable list of lines. */
    public ArrayList<String> getChatMessage(){ return this.chatMessage; }

    /** Allows injecting a prepared {@link DataOutputStream} (e.g., from client thread). */
    public void setDataOutStream(DataOutputStream dout){ this.dout = dout; }

    /** Sets host display name. */
    public void setHostName(String hostName){ this.hostName = hostName; }

    /** Sets client display name. */
    public void setClientName(String clientName){ this.clientName = clientName; }

    /** Wires main menu panel for navigation. */
    public void setMainMenu(MainMenu mainMenu) { this.mainMenu = mainMenu; }

    /** Wires top-level frame for panel swaps. */
    public void setFrame(JFrame frame) { this.frame = frame; }

    /** Accepts server socket reference (used during cleanup). */
    public void setServerSocket(ServerSocket serverSocket) { this.serverSocket = serverSocket; }

    /**
     * Navigates back to the main menu and attempts to gracefully notify/close network channels.
     * Sends "%" to peer, closes server/client sockets (ignoring errors), then swaps panels.
     */
    public void toMainMenu(){
        try { dout.writeUTF("%"); }          catch (Exception ignored) {}
        try { serverSocket.close(); }        catch (Exception ignored) {}
        try { socket.close(); }              catch (Exception ignored) {}

        this.frame.getContentPane().removeAll();
        this.frame.getContentPane().add(this.mainMenu);
        this.frame.revalidate();
        this.frame.repaint();
    }

    /* ===== Score & turn helpers ===== */

    /** Updates scoreboard counters and refreshes subscribers. */
    public void updateScore(int newHostScore, int newClientScore){
        this.hostScore = newHostScore;
        this.clientScore = newClientScore;
        notifySubscribers();
    }

    /**
     * Sets the user label whose turn it is, then notifies views.
     */
    public void setTurn(String username) {
        this.turn = turn;
        notifySubscribers();
    }
}

