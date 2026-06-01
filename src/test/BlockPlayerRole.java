package test;

import java.awt.event.KeyEvent;

/** Strategy 패턴 - 블록 이동 역할. 스페이스바 = 즉시 낙하. */
public class BlockPlayerRole implements PlayerRole {

    private final CoopGamePanel panel;

    public BlockPlayerRole(CoopGamePanel panel) {
        this.panel = panel;
    }

    @Override
    public void handleKeyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:  panel.requestPieceLeft();    break;
            case KeyEvent.VK_RIGHT: panel.requestPieceRight();   break;
            case KeyEvent.VK_UP:    panel.requestPieceRotate();  break;
            case KeyEvent.VK_DOWN:  panel.requestPieceDown();    break;
            case KeyEvent.VK_SPACE: panel.requestHardDrop();     break;
        }
    }

    @Override
    public void handleKeyReleased(int keyCode) { /* 블록은 홀드 동작 없음 */ }

    @Override
    public RoleType getRoleType() { return RoleType.BLOCK_MOVER; }
}
