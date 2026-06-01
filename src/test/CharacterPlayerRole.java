package test;

import java.awt.event.KeyEvent;

/**
 * Strategy 패턴 - 졸라맨 이동 역할.
 * 부드러운 이동을 위해 키 누름/뗌 모두 서버에 전달.
 * 네트워크 전송: 양수 keyCode = 눌림, 음수 = 뗌.
 */
public class CharacterPlayerRole implements PlayerRole {

    private final CoopGamePanel panel;

    public CharacterPlayerRole(CoopGamePanel panel) {
        this.panel = panel;
    }

    @Override
    public void handleKeyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: panel.setCharLeft(true);   break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: panel.setCharRight(true);  break;
            case KeyEvent.VK_SPACE: case KeyEvent.VK_W: case KeyEvent.VK_UP:
                panel.requestCharJump();
                break;
        }
    }

    @Override
    public void handleKeyReleased(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_LEFT:  case KeyEvent.VK_A: panel.setCharLeft(false);  break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: panel.setCharRight(false); break;
        }
    }

    @Override
    public RoleType getRoleType() { return RoleType.CHARACTER_MOVER; }
}
