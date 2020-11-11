/* Skeleton Copyright (C) 2015, 2020 Paul N. Hilfinger and the Regents of the
 * University of California.  All rights reserved. */
package loa;

import ucb.gui2.Pad;

import java.util.concurrent.ArrayBlockingQueue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import static loa.Piece.*;
import static loa.Square.sq;
import static loa.Board.*;

/** A widget that displays a Loa game.
 *  @author Yulin Li
 */
class BoardWidget extends Pad {

    /* Parameters controlling sizes, speeds, colors, and fonts. */

    /** Squares on each side of the board. */
    static final int SIZE = Square.BOARD_SIZE;

    /** Colors of empty squares, pieces, boundaries, and markings. */
    static final Color
            BLACK_COLOR = Color.black,
            WHITE_COLOR = Color.white,
            DARK_SQUARE_COLOR = new Color(164, 164, 164),
            LIGHT_SQUARE_COLOR = new Color(226, 226, 226),
            BORDER_COLOR = new Color(0.408f, 0.271f, 0.138f),
            GRID_LINE_COLOR = Color.black,
            PIECE_BOUNDARY_COLOR = Color.black,
            CALGOLD = new Color(253, 181, 21),
            CALBLUE = new Color(0, 50, 98);


    /** Width of border around board (pixels). */
    static final int BORDER_WIDTH = 6;
    /** Distance from edge of window to board (pixels). */
    static final int MARGIN = 16;
    /** Dimension of single square on the board (pixels). */
    static final int SQUARE_SIDE = 30;
    /** Dimension of component containing the board and margin (pixels). */
    static final int TOTAL_SIDE = 8;
    /** Dimension of component containing the board and margin (pixels). */
    static final int BOARD_SIDE =
            SQUARE_SIDE * SIZE + 2 * MARGIN + 2 * BORDER_WIDTH;
    /** Diameter of piece (pixels). */
    static final int PIECE_SIZE = (int) Math.round(0.8 * SQUARE_SIDE);
    /** Distance of edge of piece to edge of square it's on. */
    static final int PIECE_OFFSET =
            (int) Math.round(0.5 * (SQUARE_SIDE - PIECE_SIZE));

    /** Strokes to provide boundary around board and outline of piece. */
    static final BasicStroke
            BORDER_STROKE = new BasicStroke(BORDER_WIDTH, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND),
            PIECE_BOUNDARY_STROKE = new BasicStroke(1.0f);

    /** A graphical representation of a Loa board that sends commands
     *  derived from mouse clicks to COMMANDS.  */
    BoardWidget(ArrayBlockingQueue<String> commands) {
        _commands = commands;
        setMouseHandler("press", this::mouseAction);
        setMouseHandler("release", this::mouseAction);
        setPreferredSize(BOARD_SIDE, BOARD_SIDE);
        _acceptingMoves = false;
    }

    /** Draw the bare board G.  */
    private void drawGrid(Graphics2D g) {
        g.setColor(LIGHT_SQUARE_COLOR);
        g.fillRect(0, 0, BOARD_SIDE, BOARD_SIDE);

        g.setColor(DARK_SQUARE_COLOR);
        for (int y = 0; y < SIZE; y += 1) {
            for (int x = (y + 1) % 2; x < SIZE; x += 2) {
                g.fillRect(x * SQUARE_SIDE + MARGIN + BORDER_WIDTH,
                        y * SQUARE_SIDE + MARGIN + BORDER_WIDTH,
                        SQUARE_SIDE, SQUARE_SIDE);
            }
        }
        g.setColor(BORDER_COLOR);
        g.setStroke(BORDER_STROKE);
        g.drawRect(MARGIN + BORDER_WIDTH / 2, MARGIN + BORDER_WIDTH / 2,
                BOARD_SIDE - 2 * MARGIN - BORDER_WIDTH,
                BOARD_SIDE - 2 * MARGIN - BORDER_WIDTH);

    }

    /** Strokes for ordinary grid lines and those that are parts of
     *  boundaries. */
    static final BasicStroke
            GRIDLINE_STROKE = new BasicStroke(BOARD_SIDE);

    @Override
    public synchronized void paintComponent(Graphics2D g) {
        drawGrid(g);
        for (Square sq : Square.ALL_SQUARES) {
            drawPiece(g, sq);
        }
        g.setColor(GRID_LINE_COLOR);
        for (int k = 0; k < TOTAL_SIDE; k += 1) {
            g.drawLine(cx(k), cy(-1), cx(k), cy(TOTAL_SIDE - 1));
        }
        for (int k = 0; k < TOTAL_SIDE; k += 1) {
            g.drawLine(cx(0), cy(k), cx(TOTAL_SIDE), cy(k));
        }
    }

    /** Draw the contents of S on G. */
    private void drawPiece(Graphics2D g, Square s) {
        Piece p = _board.get(s);
        switch (p) {
            case EMP:
                return;
            case WP:
                g.setColor(WHITE_COLOR);
                break;
            case BP:
                g.setColor(BLACK_COLOR);
                break;
            default:
                assert false;
        }
        g.fillOval(cx(s) + PIECE_OFFSET, cy(s) + PIECE_OFFSET,
                PIECE_SIZE, PIECE_SIZE);
        g.setColor(PIECE_BOUNDARY_COLOR);
        g.setStroke(PIECE_BOUNDARY_STROKE);
        g.drawOval(cx(s) + PIECE_OFFSET, cy(s) + PIECE_OFFSET,
                PIECE_SIZE, PIECE_SIZE);
    }

    /** Handle a mouse-button push on S. */
    private void mousePressed(Square s) {
        if (_board.get(s) != null) {
            _moveFrom = s;
        } else {
            _moveFrom = null;
        }
        repaint();
    }

    /** Handle a mouse-button release on S. */
    private void mouseReleased(Square s) {
        if (_board.get(s) != null) {
            _moveTo = s;
        } else {
            _moveTo = null;
        }
        if (_moveFrom != null) {
            Move mv = Move.mv(_moveFrom, _moveTo);
            if (_board.isLegal(mv)) {
                _commands.offer(mv.toString());
            }
        }
        _moveFrom = null;
        _moveTo = null;
        repaint();
    }

    /** Handle mouse click event E. */
    private synchronized void mouseAction(String unused, MouseEvent e) {
        int xpos = e.getX(), ypos = e.getY();
        int x = (xpos - cx(0)) / SQUARE_SIDE,
                y = (cy(SIZE - 1) - ypos) / SQUARE_SIDE + SIZE - 1;
        if (_acceptingMoves
                && x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
            Square s = sq(x, y);
            switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED:
                    mousePressed(s);
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    mouseReleased(s);
                    break;
                default:
                    break;
            }
        }
    }

    /** Revise the displayed board according to BOARD. */
    synchronized void update(Board board) {
        _board.copyFrom(board);
        repaint();
    }

    /** Turn on move collection iff COLLECTING, and clear any current
     *  partial selection.  When move collection is off, ignore clicks on
     *  the board. */
    void setMoveCollection(boolean collecting) {
        _acceptingMoves = collecting;
        repaint();
    }

    /** Return x-pixel coordinate of the left corners of column X
     *  relative to the upper-left corner of the board. */
    private int cx(int x) {
        return x * SQUARE_SIDE + MARGIN + BORDER_WIDTH;
    }

    /** Return y-pixel coordinate of the upper corners of row Y
     *  relative to the upper-left corner of the board. */
    private int cy(int y) {
        return (SIZE - y - 1) * SQUARE_SIDE + MARGIN + BORDER_WIDTH;
    }

    /** Return x-pixel coordinate of the left corner of S
     *  relative to the upper-left corner of the board. */
    private int cx(Square s) {
        return cx(s.col());
    }

    /** Return y-pixel coordinate of the upper corner of S
     *  relative to the upper-left corner of the board. */
    private int cy(Square s) {
        return cy(s.row());
    }

    /** Queue on which to post move commands (from mouse clicks). */
    private ArrayBlockingQueue<String> _commands;
    /** Board being displayed. */
    private final Board _board = new Board();

    /** True iff accepting moves from user. */
    private boolean _acceptingMoves;

    /** The Square target we are moving from. */
    private Square _moveFrom;
    /** The Square target we aim at. */
    private Square _moveTo;

}
