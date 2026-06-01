package test;

import java.io.*;
import java.net.*;

/** NetworkManager 구체 구현 - 클라이언트 역할 (다형성) */
public class GameClient extends NetworkManager {

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

    @Override
    public void connect(String host, MessageHandler handler) throws Exception {
        this.handler = handler;
        socket = new Socket(host, PORT);
        
        // OOS 생성 후 즉시 flush하여 서버와의 스트림 연결 데드락 방지
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        connected = true;

        Thread listenThread = new Thread(() -> {
            try {
                while (connected) {
                    GameMessage msg = (GameMessage) in.readObject();
                    if (handler != null) {
                        handler.onMessage(msg);
                    }
                }
            } catch (Exception e) {
                // 스트림 종료나 예외 발생 시 안전하게 연결 끊기 처리
                if (connected) {
                    disconnect();
                    if (handler != null) handler.onDisconnected();
                }
            }
        }, "ClientListen");
        listenThread.setDaemon(true);
        listenThread.start();
    }

    @Override
    public synchronized void send(GameMessage msg) {
        if (out == null || !connected) return;
        try {
            out.writeObject(msg);
            out.flush();
            out.reset(); // Object 캐시를 비워 동일 객체의 상태 변경 내역이 서버에 정상 반영되도록 함
        } catch (Exception e) {
            // 전송 실패 시 연결 종료 및 핸들러 알림
            disconnect();
            if (handler != null) handler.onDisconnected();
        }
    }

    @Override
    public void disconnect() {
        if (!connected) return; // 중복 닫기 방지
        connected = false;
        
        try { if (out    != null) out.close(); }    catch (Exception ignored) {}
        try { if (in     != null) in.close(); }     catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}