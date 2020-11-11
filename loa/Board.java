/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import java.util.regex.Pattern;

import static loa.Piece.*;
import static loa.Square.*;

/** Represents the state of a game of Lines of Action.
 *  @author Yulin Li
 */
class Board {

    /** Default number of moves for each side that results in a draw. */
    static final int DEFAULT_MOVE_LIMIT = 60;

    /** Pattern describing a valid square designator (cr). */
    static final Pattern ROW_COL = Pattern.compile("^[a-h][1-8]$");

    /** A position-score magnitude indicating a win (for white if positive,
     *  black if negative). */
    private static final int WINNING_VALUE = Integer.MAX_VALUE - 20;

    /** A position-score magnitude indicating a win (for white if positive,
     *  black if negative). */
    private static final int TWENTY = 20;

    /** A Board whose initial contents are taken from INITIALCONTENTS
     *  and in which the player playing TURN is to move. The resulting
     *  Board has
     *        get(col, row) == INITIALCONTENTS[row][col]
     *  Assumes that PLAYER is not null and INITIALCONTENTS is 8x8.
     *
     *  CAUTION: The natural written notation for arrays initializers puts
     *  the BOTTOM row of INITIALCONTENTS at the top.
     */
    Board(Piece[][] initialContents, Piece turn) {
        initialize(initialContents, turn);
    }

    /** A new board in the standard initial position. */
    Board() {
        this(INITIAL_PIECES, BP);
    }

    /** A Board whose initial contents and state are copied from
     *  BOARD. */
    Board(Board board) {
        this();
        copyFrom(board);
    }

    /** Set my state to CONTENTS with SIDE to move. */
    void initialize(Piece[][] contents, Piece side) {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                Square thisSquare = Square.sq(col, row);
                _board[thisSquare.index()] = contents[row][col];
            }
        }
        _turn = side;
        _moveLimit = DEFAULT_MOVE_LIMIT;
        _contents = contents;
    }

    /** Set me to the initial configuration. */
    void clear() {
        initialize(INITIAL_PIECES, BP);
    }

    /** Set my state to a copy of BOARD. */
    void copyFrom(Board board) {
        if (board == this) {
            return;
        }
        int counter = 0;
        for (Piece pc : board._board) {
            _board[counter] = pc;
            counter = counter + 1;
        }
        _moveLimit = board._moveLimit;
        _turn = board._turn;
        _winner = board._winner;
        _moves.addAll(board._moves);
        _winnerKnown = board._winnerKnown;
        _subsetsInitialized = board._subsetsInitialized;
        _blackRegionSizes.addAll(board.getRegionSizes(BP));
        _whiteRegionSizes.addAll(board.getRegionSizes(WP));
    }

    /** Return the contents of the square at SQ. */
    Piece get(Square sq) {
        return _board[sq.index()];
    }

    /** Set the square at SQ to V and set the side that is to move next
     *  to NEXT, if NEXT is not null. */
    void set(Square sq, Piece v, Piece next) {
        _board[sq.index()] = v;
        if (next != null) {
            _turn = next;
        }
    }

    /** Set the square at SQ to V, without modifying the side that
     *  moves next. */
    void set(Square sq, Piece v) {
        set(sq, v, null);
    }

    /** Set limit on number of moves by each side that results in a tie to
     *  LIMIT, where 2 * LIMIT > movesMade(). */
    void setMoveLimit(int limit) {
        if (2 * limit <= movesMade()) {
            throw new IllegalArgumentException("move limit too small");
        }
        _moveLimit = 2 * limit;
    }

    /** Assuming isLegal(MOVE), make MOVE. This function assumes that
     *  MOVE.isCapture() will return false.  If it saves the move for
     *  later retraction, makeMove itself uses MOVE.captureMove() to produce
     *  the capturing move. */
    void makeMove(Move move) {
        assert isLegal(move);
        Square before = move.getFrom();
        Square after = move.getTo();
        if (pieceAtSq(after) != EMP) {
            _moves.add(move.captureMove());
        } else {
            _moves.add(move);
        }
        set(after, _board[before.index()], _turn.opposite());
        set(before, EMP);
        winner();
        _subsetsInitialized = false;
        computeRegions();
    }

    /** Retract (unmake) one move, returning to the state immediately before
     *  that move.  Requires that movesMade () > 0. */
    void retract() {
        assert movesMade() > 0;
        Move lastMove = _moves.remove(_moves.size() - 1);
        Square from = lastMove.getFrom();
        Square to = lastMove.getTo();
        set(from, _board[to.index()]);
        if (lastMove.isCapture()) {
            set(to, _board[to.index()].opposite(), _turn.opposite());
        } else {
            set(to, EMP);
        }
    }

    /** Return the Piece representing who is next to move. */
    Piece turn() {
        return _turn;
    }

    /** Return true iff FROM - TO is a legal move for the player currently on
     *  move. */
    boolean isLegal(Square from, Square to) {
        if (from == null || to == null || from == to) {
            return false;
        } else if ((!from.isValidMove(to))) {
            return false;
        } else if (blocked(from, to)) {
            return false;
        } else if (!rightNumStep(from, to)) {
            return false;
        }
        return true;
    }

    /**
     * return if right number of steps.
     * @param from the Sq from
     * @param to the Sq to
     */
    boolean rightNumStep(Square from, Square to) {
        int dir = from.direction(to);
        int sqCount = 1;
        for (int i = 1; i < BOARD_SIZE; i++) {
            Square destSq = from.moveDest(dir, i);
            if (destSq != null && pieceAtSq(destSq) != EMP) {
                sqCount++;
            }
        }
        int oppoDir;
        if (dir >= 4) {
            oppoDir = dir - 4;
        } else {
            oppoDir = dir + 4;
        }
        for (int i = 1; i < BOARD_SIZE; i++) {
            Square destSq = from.moveDest(oppoDir, i);
            if (destSq != null && pieceAtSq(destSq) != EMP) {
                sqCount++;
            }
        }
        return from.distance(to) == sqCount;
    }

    /** Return true iff MOVE is legal for the player currently on move.
     *  The isCapture() property is ignored. */
    boolean isLegal(Move move) {
        return isLegal(move.getFrom(), move.getTo());
    }

    /** Return a sequence of all legal moves from this position. */
    List<Move> legalMoves() {
        ArrayList<Move> result = new ArrayList<Move>();
        for (Square from : ALL_SQUARES) {
            if (_board[from.index()] == turn()) {
                for (Square to : ALL_SQUARES) {
                    if (isLegal(from, to)) {
                        Move newMove = Move.mv(from, to);
                        result.add(newMove);
                    }
                }
            }
        }
        return result;
    }

    /** Return true iff the game is over (either player has all his
     *  pieces continguous or there is a tie). */
    boolean gameOver() {
        return winner() != null;
    }

    /** Return true iff SIDE's pieces are continguous. */
    boolean piecesContiguous(Piece side) {
        return getRegionSizes(side).size() == 1;
    }

    /** Return the winning side, if any.  If the game is not over, result is
     *  null.  If the game has ended in a tie, returns EMP. */
    Piece winner() {
        _winner = null;
        if (piecesContiguous(BP)
                && piecesContiguous(WP)) {
            _winnerKnown = true;
            _winner = _turn.opposite();
            return _winner;
        } else if (piecesContiguous(BP)) {
            _winnerKnown = true;
            _winner = BP;
            return _winner;
        } else if (piecesContiguous(WP)) {
            _winnerKnown = true;
            _winner = WP;
            return _winner;
        } else if (_moves.size() >= _moveLimit) {
            _winnerKnown = true;
            _winner = EMP;
            return _winner;
        } else {
            return _winner;
        }
    }

    /** Return the total number of moves that have been made (and not
     *  retracted).  Each valid call to makeMove with a normal move increases
     *  this number by 1. */
    int movesMade() {
        return _moves.size();
    }

    @Override
    public boolean equals(Object obj) {
        Board b = (Board) obj;
        return Arrays.deepEquals(_board, b._board) && _turn == b._turn;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(_board) * 2 + _turn.hashCode();
    }

    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("===%n");
        for (int r = BOARD_SIZE - 1; r >= 0; r -= 1) {
            out.format("    ");
            for (int c = 0; c < BOARD_SIZE; c += 1) {
                out.format("%s ", get(sq(c, r)).abbrev());
            }
            out.format("%n");
        }
        out.format("Next move: %s%n===", turn().fullName());
        return out.toString();
    }

    /** Return true if a move from FROM to TO is blocked by an opposing
     *  piece or by a friendly piece on the target square. */
    private boolean blocked(Square from, Square to) {
        if (_board[from.index()] == _board[to.index()]) {
            return true;
        }
        int dir = from.direction(to);
        int dis = from.distance(to);
        for (int i = 1; i < dis - 1; i++) {
            Square destSq = from.moveDest(dir, i);
            if (destSq != null && pieceAtSq(destSq) != EMP) {
                if (_board[destSq.index()] != _board[from.index()]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Return the size of the as-yet unvisited cluster of squares
     *  containing P at and adjacent to SQ.  VISITED indicates squares that
     *  have already been processed or are in different clusters.  Update
     *  VISITED to reflect squares counted. */
    private int numContig(Square sq, boolean[][] visited, Piece p) {
        if (sq == null) {
            return 0;
        } else if (pieceAtSq(sq) != p) {
            return 0;
        } else if (visited[sq.col()][sq.row()]) {
            return 0;
        }
        visited[sq.col()][sq.row()] = true;
        int result = 1;
        for (Square ajc : sq.adjacent()) {
            result = result + numContig(ajc, visited, p);
        }
        return result;
    }
    /** Helper func for piece at Square.
     * @param sq the piece at square
     * @return the piece*/
    private Piece pieceAtSq(Square sq) {
        return _board[sq.index()];
    }

    /** Set the values of _whiteRegionSizes and _blackRegionSizes. */
    private void computeRegions() {
        if (_subsetsInitialized) {
            return;
        }
        _whiteRegionSizes.clear();
        _blackRegionSizes.clear();
        boolean[][] totalVisited = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                totalVisited[i][j] = false;
            }
        }
        for (Square sq : ALL_SQUARES) {
            if (pieceAtSq(sq) == BP) {
                int currSize = numContig(sq, totalVisited, BP);
                totalVisited[sq.col()][sq.row()] = true;
                if (currSize != 0) {
                    _blackRegionSizes.add(currSize);
                }
            }
            if (pieceAtSq(sq) == WP) {
                int currSize = numContig(sq, totalVisited, WP);
                totalVisited[sq.col()][sq.row()] = true;
                if (currSize != 0) {
                    _whiteRegionSizes.add(currSize);

                }
            }
        }
        Collections.sort(_whiteRegionSizes, Collections.reverseOrder());
        Collections.sort(_blackRegionSizes, Collections.reverseOrder());
        _subsetsInitialized = true;
    }

    /** Return the sizes of all the regions in the current union-find
     *  structure for side S. */
    List<Integer> getRegionSizes(Piece s) {
        computeRegions();
        if (s == WP) {
            return _whiteRegionSizes;
        } else {
            return _blackRegionSizes;
        }
    }

    /** Return the herustic score of the position. */
    public int eval() {
        if (this.winner() == WP) {
            return WINNING_VALUE;
        }
        if (this.winner() == BP) {
            return -WINNING_VALUE;
        }
        int score = 0;
        int numWhiteGroup = getRegionSizes(WP).size();
        int numBlackGroup = getRegionSizes(BP).size();
        score = score + (numBlackGroup - numWhiteGroup) * TWENTY;
        for (int i = 3; i < 5; i++) {
            for (int j = 3; j < 5; j++) {
                if (pieceAtSq(sq(i, j)) == WP) {
                    score = score + 10;
                }
                if (pieceAtSq(sq(i, j)) == BP) {
                    score = score - 10;
                }
            }
        }
        int maxW = 0;
        int maxB = 0;
        for (int w : getRegionSizes(WP)) {
            maxW = Math.max(w, maxW);
        }
        for (int b : getRegionSizes(BP)) {
            maxB = Math.max(b, maxB);
        }
        score = score + (maxW - maxB) * 10;
        int random = (int) Math.random() * 10;
        return score + random;
    }


    /** The standard initial configuration for Lines of Action (bottom row
     *  first). */
    static final Piece[][] INITIAL_PIECES = {
            { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { WP,  EMP, EMP, EMP, EMP, EMP, EMP, WP  },
            { EMP, BP,  BP,  BP,  BP,  BP,  BP,  EMP }
    };

    /** Current contents of the board.  Square S is at _board[S.index()]. */
    private final Piece[] _board = new Piece[BOARD_SIZE  * BOARD_SIZE];

    /** List of all unretracted moves on this board, in order. */
    private final ArrayList<Move> _moves = new ArrayList<>();
    /** Current side on move. */
    private Piece _turn;
    /** Limit on number of moves before tie is declared.  */
    private int _moveLimit;
    /** True iff the value of _winner is known to be valid. */
    private boolean _winnerKnown;
    /** Cached value of the winner (BP, WP, EMP (for tie), or null (game still
     *  in progress).  Use only if _winnerKnown. */
    private Piece _winner;
    /** True iff subsets computation is up-to-date. */
    private boolean _subsetsInitialized;

    /** Keep track of the contents. */
    private Piece[][] _contents;

    /** List of the sizes of continguous clusters of pieces, by color. */
    private final ArrayList<Integer>
            _whiteRegionSizes = new ArrayList<>(),
            _blackRegionSizes = new ArrayList<>();
}
