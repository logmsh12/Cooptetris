package test;

import java.awt.Point;

public abstract class Piece {
    protected static final int DOWN  = 0;
    protected static final int LEFT  = 1;
    protected static final int RIGHT = 2;

    protected int[] r;
    protected int[] c;
    protected TetrisData data;
    protected Point center;

    public Piece(TetrisData data) {
        r = new int[4];
        c = new int[4];
        this.data = data;
        center = new Point(5, 0);
    }

    public abstract int getType();
    public abstract int roteType();

    public int getX() { return center.x; }
    public int getY() { return center.y; }

    public int getMinY() {
        int min = r[0];
        for (int i = 1; i < 4; i++) if (r[i] < min) min = r[i];
        return min;
    }

    public boolean copy() {
        boolean topReached = (getMinY() + getY() <= 0);
        int x = getX(), y = getY();
        for (int i = 0; i < 4; i++) {
            int row = y + r[i];
            int col = x + c[i];
            if (row >= 0 && row < TetrisData.ROW && col >= 0 && col < TetrisData.COL)
                data.setAt(row, col, getType());
        }
        return topReached;
    }

    public boolean isOverlap(int dir) {
        int x = getX(), y = getY();
        switch (dir) {
            case DOWN:
                for (int i = 0; i < r.length; i++) {
                    int nextRow = y + r[i] + 1;
                    int col     = x + c[i];
                    if (nextRow >= TetrisData.ROW) return true;
                    if (data.getAt(nextRow, col) != 0) return true;
                }
                break;
            case LEFT:
                for (int i = 0; i < r.length; i++) {
                    int row     = y + r[i];
                    int prevCol = x + c[i] - 1;
                    if (prevCol < 0) return true;
                    if (data.getAt(row, prevCol) != 0) return true;
                }
                break;
            case RIGHT:
                for (int i = 0; i < r.length; i++) {
                    int row     = y + r[i];
                    int nextCol = x + c[i] + 1;
                    if (nextCol >= TetrisData.COL) return true;
                    if (data.getAt(row, nextCol) != 0) return true;
                }
                break;
        }
        return false;
    }

    public boolean moveDown() {
        if (isOverlap(DOWN)) return true;
        center.y++;
        return false;
    }

    public void moveLeft() {
        if (!isOverlap(LEFT)) center.x--;
    }

    public void moveRight() {
        if (!isOverlap(RIGHT)) center.x++;
    }

    public void rotate() {
        if (roteType() == 1) return;
        int[] newR = new int[4];
        int[] newC = new int[4];
        for (int i = 0; i < 4; i++) {
            newR[i] =  c[i];
            newC[i] = -r[i];
        }
        int x = getX(), y = getY();
        for (int i = 0; i < 4; i++) {
            int row = y + newR[i];
            int col = x + newC[i];
            if (row < 0 || row >= TetrisData.ROW || col < 0 || col >= TetrisData.COL) return;
            if (data.getAt(row, col) != 0) return;
        }
        r = newR;
        c = newC;
    }
}
