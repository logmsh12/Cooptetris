package test;

import java.io.*;
import java.net.*;

/** NetworkManager 구체 구현 - 서버 역할 (다형성) */
public class GameServer extends NetworkManager {

    private ServerSocket       serverSocket;
    private Socket             clientSocket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    @Override
    public void connect(String host, MessageHandler handler) throws Exception {
        this.handler = handler;
        serverSocket = new ServerSocket(PORT);

        Thread acceptThread = new Thread(() -> {
            try {
                clientSocket = serverSocket.accept();
                // OOS를 먼저 flush해야 OIS 생성 시 데드락 방지
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush();
                in  = new ObjectInputStream(clientSocket.getInputStream());
                connected = true;
                handler.onMessage(new GameMessage(GameMessage.Type.CONNECT, null));
                startListening();
            } catch (Exception e) {
                if (handler != null) handler.onDisconnected();
            }
        }, "ServerAccept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void startListening() {
        Thread listenThread = new Thread(() -> {
            try {
                while (connected) {
                    GameMessage msg = (GameMessage) in.readObject();
                    if (handler != null) handler.onMessage(msg);
                }
            } catch (Exception e) {
                if (connected && handler != null) handler.onDisconnected();
            }
        }, "ServerListen");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    @Override
    public synchronized void send(GameMessage msg) {
        if (out == null || !connected) return;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // 직렬화 캐시 비우기 (객체 상태 변경 동기화에 필수)
        } catch (Exception e) {
            connected = false;
            if (handler != null) handler.onDisconnected();
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        try { if (out          != null) out.close(); }          catch (Exception ignored) {}
        try { if (in           != null) in.close(); }           catch (Exception ignored) {}
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    /** 로컬 IP 주소 반환 (클라이언트에게 공유할 주소) */
    public String getLocalAddress() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }
}