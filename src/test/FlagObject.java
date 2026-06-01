package test;

import java.awt.*;

/** 깃발 - 위치와 수집 여부만 관리. 닿으면 즉시 수집. */
public class FlagObject {

    private final int row;
    private final int col;
    private boolean collected;

    public FlagObject(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public void collect()           { collected = true; }
    public int     getRow()         { return row; }
    public int     getCol()         { return col; }
    public boolean isCollected()    { return collected; }

    public void draw(Graphics g, int cellSize, int ox, int oy) {
        int x  = ox + col * cellSize;
        int y  = oy + row * cellSize;
        int cx = x + cellSize / 2;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 기둥 (두 칸에 걸쳐 있을 수 있으므로 2*cellSize 높이)
        g2.setColor(new Color(220, 220, 220));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(cx, y + 2, cx, y + cellSize - 2);

        // 깃발 삼각형
        int[] px = { cx + 1, cx + cellSize / 2 - 2, cx + 1 };
        int[] py = { y + 3,  y + cellSize / 3,       y + (cellSize * 2) / 3 };
        g2.setColor(new Color(255, 210, 0));
        g2.fillPolygon(px, py, 3);
        g2.setColor(new Color(200, 150, 0));
        g2.drawPolygon(px, py, 3);

        // 반짝임 효과 (별)
        g2.setColor(new Color(255, 255, 200, 180));
        g2.fillOval(cx - 3, y + 2, 6, 6);
    }
}
