package test;

/** 추상화 - 네트워크 통신 레이어 (상속을 위한 추상 클래스) */
public abstract class NetworkManager {

    public static final int PORT = 9999;

    protected volatile boolean connected;
    protected MessageHandler handler;

    public interface MessageHandler {
        void onMessage(GameMessage msg);
        void onDisconnected();
    }

    public abstract void connect(String host, MessageHandler handler) throws Exception;
    public abstract void send(GameMessage msg);
    public abstract void disconnect();

    public boolean isConnected() { return connected; }
}
