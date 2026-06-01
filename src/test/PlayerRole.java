package test;

/** Strategy 패턴 - 플레이어 입력 전략 인터페이스 */
public interface PlayerRole {
    void handleKeyPressed(int keyCode);
    void handleKeyReleased(int keyCode);
    RoleType getRoleType();
}
