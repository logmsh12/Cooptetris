package test;

/** Observer 패턴 - 이벤트 발행 인터페이스 */
public interface GameSubject {
    void addObserver(GameObserver observer);
    void removeObserver(GameObserver observer);
    void notifyObservers(GameEvent event, Object payload);
}
