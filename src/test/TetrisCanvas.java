package test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class TetrisCanvas extends JPanel implements Runnable, KeyListener {
    protected Thread worker;
    protected Color colors[];
    protected int w = 25;
    protected TetrisData data;
    protected int margin = 20;
    protected boolean stop, makeNew;
    protected Piece current;
    protected int interval = 2000;
    protected int level = 2;
    
    // 외부 핸들러나 상위 패널에서 종료를 감지하기 위한 상태 변수
    protected boolean isGameOver = false;

    public TetrisCanvas() {
        data = new TetrisData();
        addKeyListener(this);
        colors = new Color[8];
        colors[0] = new Color(80, 80, 80); // 배경색
        colors[1] = new Color(255, 0, 0);  // 빨간색
        colors[2] = new Color(0, 255, 0);  // 녹색
        colors[3] = new Color(0, 200, 255); // 노란색
        colors[4] = new Color(255, 255, 0); // 하늘색
        colors[5] = new Color(255, 150, 0); // 황토색
        colors[6] = new Color(210, 0, 240); // 보라색
        colors[7] = new Color(40, 0, 240);  // 파란색
    }

    public void start() {
        data.clear();
        worker = new Thread(this);
        makeNew = true;
        stop = false;
        isGameOver = false; // 시작 시 초기화
        worker.start();
        requestFocus();
        repaint();
    }

    public void stop() {
        stop = true;
        current = null;
    }

    /**
     * Swing 표준에 맞춰 paint 대신 paintComponent를 재정의합니다.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // 쌓인 조각들 그리기
        for (int i = 0; i < TetrisData.ROW; i++) {
            for (int k = 0; k < TetrisData.COL; k++) {
                if (data.getAt(i, k) == 0) {
                    g.setColor(colors[data.getAt(i, k)]);
                    g.draw3DRect(margin / 2 + w * k, margin / 2 + w * i, w, w, true);
                } else {
                    g.setColor(colors[data.getAt(i, k)]);
                    g.fill3DRect(margin / 2 + w * k, margin / 2 + w * i, w, w, true);
                }
            }
        }
        
        // 현재 내려오는 조각 그리기
        if (current != null) {
            for (int i = 0; i < 4; i++) {
                g.setColor(colors[current.getType()]);
                g.fill3DRect(margin / 2 + w * (current.getX() + current.c[i]),
                             margin / 2 + w * (current.getY() + current.r[i]),
                             w, w, true);
            }
        }

        // 게임오버 상태일 때 화면에 안내 문구 오버레이 (클라이언트 인지용 안내 추가)
        if (isGameOver) {
            g.setColor(new Color(0, 0, 0, 180)); // 반투명 검은색 배경
            g.fillRect(0, 0, getWidth(), getHeight());
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("맑은 고딕", Font.BOLD, 24));
            FontMetrics fm = g.getFontMetrics();
            String msg = "GAME OVER";
            int x = (getWidth() - fm.stringWidth(msg)) / 2;
            int y = getHeight() / 2;
            g.drawString(msg, x, y);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int tw = w * TetrisData.COL + margin;
        int th = w * TetrisData.ROW + margin;
        return new Dimension(tw, th);
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                if (makeNew) {
                    int random = (int) (Math.random() * Integer.MAX_VALUE) % 7;
                    switch (random) {
                        case 0: current = new Bar(data); break;
                        case 1: current = new Tee(data); break;
                        case 2: current = new El(data); break;
                        case 3: current = new J(data); break;
                        case 4: current = new O(data); break;
                        case 5: current = new S(data); break;
                        case 6: current = new Z(data); break;
                        default:
                            if (random % 2 == 0) current = new Tee(data);
                            else current = new El(data);
                            break;
                    }
                    makeNew = false;
                } else {
                    if (current.moveDown()) {
                        makeNew = true;
                        if (current.copy()) {
                            handleGameOver(); // 게임 종료 공통 로직으로 분리 호출
                        }
                        current = null;
                    }
                    data.removeLines();
                }
                repaint();
                Thread.sleep(interval / level);
            } catch (Exception e) {
                break;
            }
        }
    }

    /**
     * 키 이벤트 핸들러 내부 종료 검증 강화
     */
    @Override
    public void keyPressed(KeyEvent e) {
        if (current == null || stop || isGameOver) return; // 이미 종료되었다면 입력 무시
        
        switch (e.getKeyCode()) {
            case 37: current.moveLeft(); repaint(); break;   // 왼쪽 화살표
            case 39: current.moveRight(); repaint(); break;  // 오른쪽 화살표
            case 38: current.rotate(); repaint(); break;     // 위쪽 화살표
            case 40: // 아래쪽 화살표
                boolean temp = current.moveDown();
                if (temp) {
                    makeNew = true;
                    if (current.copy()) {
                        handleGameOver();
                    }
                }
                data.removeLines();
                repaint();
                break;
        }
    }

    /**
     * 게임 오버 상태를 처리하는 메서드 (쓰레드 안전성 확보)
     */
    protected void handleGameOver() {
        stop();
        this.isGameOver = true;
        
        // 최종 점수 계산
        final int score = data.getLine() * 175 * level;
        
        // Swing GUI 스레드(EDT)에서 안전하게 다이얼로그 및 리페인트를 처리하도록 유도
        SwingUtilities.invokeLater(() -> {
            repaint(); // 오버레이 문구 갱신 반영
            JOptionPane.showMessageDialog(TetrisCanvas.this, "게임끝\n점수 : " + score);
        });
    }

    // 네트워크나 외부 패널에서 원격으로 게임오버 이벤트를 주입받을 수 있도록 제공하는 Setter
    public void setGameOver(boolean gameOver) {
        this.isGameOver = gameOver;
        if (gameOver) {
            stop();
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}