package test;

import java.awt.*;

/**
 * 졸라맨 캐릭터 - 픽셀 기반 물리 (중력, AABB 충돌 해소).
 * 크기: 1칸 가로(25px) × 2칸 세로(50px).
 * 최대 점프 높이: 정확히 2칸 (중력 900 px/s², 초속 -300 px/s).
 */
public class GameCharacter {

    // ── 치수 상수 (CoopGamePanel.CELL 과 일치해야 함) ─────────────
    public static final int CELL_SIZE = 25;
    public static final int W = CELL_SIZE;        // 1칸 가로
    public static final int H = 2 * CELL_SIZE;    // 2칸 세로

    // ── 물리 상수 ─────────────────────────────────────────────────
    private static final float GRAVITY    =  900f;  // px/s²
    private static final float JUMP_VY    = -300f;  // px/s  → 최대 높이 300²/(2·900)=50px=2칸
    private static final float MOVE_SPEED =  160f;  // px/s

    // ── 상태 ──────────────────────────────────────────────────────
    private float bx, by;   // 보드 좌상단 기준 픽셀 위치
    private float vx, vy;
    private boolean onGround;
    private boolean movingLeft, movingRight;
    private boolean jumpPending;

    public GameCharacter() { reset(); }

    public void reset() {
        bx = (TetrisData.COL * CELL_SIZE - W) / 2f;
        by = TetrisData.ROW * CELL_SIZE - H;
        vx = vy = 0f;
        onGround = true;
        movingLeft = movingRight = jumpPending = false;
    }

    // ── 매 틱 호출 ────────────────────────────────────────────────
    public void update(float dt, TetrisData data) {
        // 지면 확인 (이전 위치 기준)
        onGround = checkOnGround(data);

        // 수평 속도
        vx = movingLeft ? -MOVE_SPEED : (movingRight ? MOVE_SPEED : 0f);

        // 중력 / 점프
        if (onGround) {
            vy = 0f;
            if (jumpPending) {
                vy = JUMP_VY;
                onGround = false;
            }
        } else {
            vy += GRAVITY * dt;
        }
        jumpPending = false;

        // 이동
        bx += vx * dt;
        by += vy * dt;

        // 충돌 해소
        resolveCollision(data);
        onGround = checkOnGround(data);
    }

    // ── 충돌 해소 (보드 경계 + 쌓인 블록) ─────────────────────────
    private void resolveCollision(TetrisData data) {
        final float boardW = TetrisData.COL * CELL_SIZE;
        final float boardH = TetrisData.ROW * CELL_SIZE;

        // 수평 경계 클램프
        bx = Math.max(0f, Math.min(boardW - W, bx));

        int tRow = Math.max(0, (int)(by / CELL_SIZE));
        int bRow = Math.min(TetrisData.ROW - 1, (int)((by + H - 1f) / CELL_SIZE));

        // 수평 블록 충돌 (왼쪽)
        if (vx < 0) {
            int lCol = Math.max(0, (int)(bx / CELL_SIZE));
            for (int r = tRow; r <= bRow; r++) {
                if (data.getAt(r, lCol) != 0) { bx = (lCol + 1) * CELL_SIZE; break; }
            }
        }
        // 수평 블록 충돌 (오른쪽)
        if (vx > 0) {
            int rCol = Math.min(TetrisData.COL - 1, (int)((bx + W - 1f) / CELL_SIZE));
            for (int r = tRow; r <= bRow; r++) {
                if (data.getAt(r, rCol) != 0) { bx = rCol * CELL_SIZE - W; break; }
            }
        }

        int lCol = Math.max(0, (int)(bx / CELL_SIZE));
        int rCol = Math.min(TetrisData.COL - 1, (int)((bx + W - 1f) / CELL_SIZE));

        // 수직 낙하 충돌
        if (vy > 0) {
            if (by + H >= boardH) {
                by = boardH - H; vy = 0;
            } else {
                int belowRow = (int)((by + H) / CELL_SIZE);
                if (belowRow < TetrisData.ROW) {
                    for (int c = lCol; c <= rCol; c++) {
                        if (data.getAt(belowRow, c) != 0) {
                            by = belowRow * CELL_SIZE - H; vy = 0; break;
                        }
                    }
                }
            }
        }
        // 수직 상승 충돌 (천장)
        if (vy < 0) {
            if (by < 0) {
                by = 0; vy = 0;
            } else {
                int aboveRow = (int)(by / CELL_SIZE);
                if (aboveRow >= 0) {
                    for (int c = lCol; c <= rCol; c++) {
                        if (data.getAt(aboveRow, c) != 0) {
                            by = (aboveRow + 1) * CELL_SIZE; vy = 0; break;
                        }
                    }
                }
            }
        }
        // 바닥 최종 클램프
        if (by + H > boardH) by = boardH - H;
        if (by < 0)          by = 0;
    }

    /** 발 아래에 블록/바닥이 있는지 확인 */
    private boolean checkOnGround(TetrisData data) {
        if (by + H >= TetrisData.ROW * CELL_SIZE - 0.5f) return true;
        int belowRow = (int)((by + H + 0.5f) / CELL_SIZE);
        if (belowRow >= TetrisData.ROW) return true;
        int lCol = Math.max(0, (int)(bx / CELL_SIZE));
        int rCol = Math.min(TetrisData.COL - 1, (int)((bx + W - 1f) / CELL_SIZE));
        for (int c = lCol; c <= rCol; c++) {
            if (data.getAt(belowRow, c) != 0) return true;
        }
        return false;
    }

    // ── 입력 ──────────────────────────────────────────────────────
    public void setMovingLeft(boolean v)  { movingLeft  = v; }
    public void setMovingRight(boolean v) { movingRight = v; }
    public void requestJump()             { jumpPending = true; }

    // ── 충돌 판정 ─────────────────────────────────────────────────

    /** 깃발(1칸) AABB 겹침 (보드 픽셀 기준) */
    public boolean overlapsFlag(int flagRow, int flagCol) {
        float fx = flagCol * CELL_SIZE, fy = flagRow * CELL_SIZE;
        return bx < fx + CELL_SIZE && bx + W > fx
            && by < fy + CELL_SIZE && by + H > fy;
    }

    /** 낙하 블록 셀 AABB 겹침 (보드 픽셀 기준) */
    public boolean overlapsPieceBlock(int pieceRow, int pieceCol) {
        float px = pieceCol * CELL_SIZE, py = pieceRow * CELL_SIZE;
        return bx < px + CELL_SIZE && bx + W > px
            && by < py + CELL_SIZE && by + H > py;
    }

    // ── 접근자 / 설정자 ───────────────────────────────────────────
    public float   getBX()        { return bx; }
    public float   getBY()        { return by; }
    public boolean isOnGround()   { return onGround; }
    public void    setPosition(float bx, float by) { this.bx = bx; this.by = by; }

    // ── 렌더링 (졸라맨, 1×2칸) ────────────────────────────────────
    public void draw(Graphics g, int ox, int oy) {
        int x  = ox + (int)bx;
        int y  = oy + (int)by;
        int cx = x + W / 2;

        Graphics2D g2 = (Graphics2D) g;
        Stroke prev = g2.getStroke();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Color.WHITE);

        // 머리
        int headR = W / 5;
        int headCY = y + H / 5;
        g2.drawOval(cx - headR, headCY - headR, headR * 2, headR * 2);

        int bodyTop = headCY + headR + 1;
        int bodyMid = y + H / 2 + 2;
        int bodyBot = y + H - 3;

        g2.drawLine(cx, bodyTop, cx, bodyMid);          // 몸통
        int armY = bodyTop + (bodyMid - bodyTop) / 2;
        g2.drawLine(x + 2, armY, x + W - 2, armY);     // 팔
        g2.drawLine(cx, bodyMid, x + 3,     bodyBot);   // 왼 다리
        g2.drawLine(cx, bodyMid, x + W - 3, bodyBot);   // 오른 다리

        g2.setStroke(prev);
    }
}
