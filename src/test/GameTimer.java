package test;

import java.util.*;

/**
 * Observer 패턴 Subject - 깃발 제한시간 관리.
 * 첫 깃발 30초, 수집할수록 점점 짧아짐.
 * 0초 도달 시 TIMER_EXPIRED 발행 → 게임 오버.
 */
public class GameTimer implements GameSubject, Runnable {

    public static final int DEFAULT_SECONDS = 30;

    private final List<GameObserver> observers = new ArrayList<>();
    private volatile int     remainingSeconds;
    private volatile boolean running;
    private Thread timerThread;

    @Override public void addObserver(GameObserver o)    { observers.add(o); }
    @Override public void removeObserver(GameObserver o) { observers.remove(o); }
    @Override public void notifyObservers(GameEvent event, Object payload) {
        for (GameObserver o : new ArrayList<>(observers)) o.onGameEvent(event, payload);
    }

    public void start() {
        remainingSeconds = DEFAULT_SECONDS;
        running = true;
        timerThread = new Thread(this, "GameTimer");
        timerThread.setDaemon(true);
        timerThread.start();
    }

    public void stop() {
        running = false;
        if (timerThread != null) timerThread.interrupt();
    }

    /** 깃발 수집 완료 시 호출 - 지정한 초로 제한시간 초기화 */
    public void reset(int seconds) {
        remainingSeconds = seconds;
        notifyObservers(GameEvent.TIMER_RESET, remainingSeconds);
    }

    @Override
    public void run() {
        while (running && remainingSeconds > 0) {
            try {
                Thread.sleep(1000);
                if (!running) break;
                remainingSeconds--;
                notifyObservers(GameEvent.TIMER_TICK, remainingSeconds);
                if (remainingSeconds <= 0) {
                    notifyObservers(GameEvent.TIMER_EXPIRED, 0);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public int getRemainingSeconds() { return remainingSeconds; }
}
