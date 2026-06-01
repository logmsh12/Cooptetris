package test;

/** Observer 패턴 - 게임 이벤트 수신 인터페이스 */
public interface GameObserver {
    void onGameEvent(GameEvent event, Object payload);
}
