package test;

import java.awt.*;
import javax.swing.*;

/**
 * 협동 테트리스 메인 JFrame.
 * CardLayout으로 TitlePanel → RoleSelectPanel → CoopGamePanel 전환.
 * 서버/클라이언트 연결 및 네트워크 메시지 처리를 담당한다.
 *
 * 프로토콜 흐름:
 *   [서버] 게임 만들기 → 역할 선택 → 클라이언트 연결 대기
 *          → CONNECT 수신 → 역할 전달(ROLE_SELECT)
 *          → 클라이언트 역할 수신(ROLE_SELECT) → ROLE_CONFIRM → GAME_START
 *   [클라이언트] 게임 찾기 → 연결 → 역할 선택
 *               → 서버 ROLE_SELECT 수신(상대 역할 비활성화)
 *               → 내 역할 선택 → ROLE_CONFIRM 수신 → GAME_START 대기
 */
public class CoopTetrisFrame extends JFrame
        implements TitlePanel.TitleListener, RoleSelectPanel.RoleSelectListener {

    private static final String CARD_TITLE = "title";
    private static final String CARD_ROLE  = "role";
    private static final String CARD_GAME  = "game";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     cardPanel  = new JPanel(cardLayout);

    private TitlePanel      titlePanel;
    private RoleSelectPanel rolePanel;
    private CoopGamePanel   gamePanel;

    private NetworkManager  network;
    private boolean         isServer;
    private RoleType        myRole;
    private RoleType        opponentRole;
    private boolean         gameStarted;
    private String          lastServerHost; // 클라이언트 재접속용

    // ── 초기화 ────────────────────────────────────────────────────
    public CoopTetrisFrame() {
        setTitle("협동 테트리스");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        titlePanel = new TitlePanel(this);
        rolePanel  = new RoleSelectPanel(this);
        gamePanel  = new CoopGamePanel();

        cardPanel.add(titlePanel, CARD_TITLE);
        cardPanel.add(rolePanel,  CARD_ROLE);
        cardPanel.add(gamePanel,  CARD_GAME);
        setContentPane(cardPanel);

        gamePanel.setGameOverListener(new CoopGamePanel.GameOverListener() {
            @Override public void onReturnToTitle() { handleReturnToTitle(); }
            @Override public void onRestartGame()   { handleRestart(); }
        });

        cardLayout.show(cardPanel, CARD_TITLE);
        pack();
        setLocationRelativeTo(null);
    }

    // ── TitleListener ─────────────────────────────────────────────

    @Override
    public void onCreateGame() {
        isServer    = true;
        myRole      = null;
        opponentRole = null;
        gameStarted = false;

        GameServer server = new GameServer();
        network = server;

        try {
            server.connect(null, makeHandler());
        } catch (Exception e) {
            showError("서버 시작 실패: " + e.getMessage());
            return;
        }

        String ip = server.getLocalAddress();
        rolePanel.showServerIP(ip);
        showInfo("서버 시작됨!\n\n내 IP 주소:  " + ip
                + "\n포트:  " + NetworkManager.PORT
                + "\n\n상대방에게 이 주소를 알려주세요.\n이제 역할을 선택하세요.");
        switchTo(CARD_ROLE);
    }

    @Override
    public void onFindGame() {
        isServer    = false;
        myRole      = null;
        opponentRole = null;
        gameStarted = false;

        String host = JOptionPane.showInputDialog(this,
                "서버 IP 주소를 입력하세요:", "게임 찾기", JOptionPane.QUESTION_MESSAGE);
        if (host == null || host.isBlank()) return;

        lastServerHost = host.trim();
        doClientConnect(lastServerHost);
    }

    // ── RoleSelectListener ────────────────────────────────────────

    @Override
    public void onRoleSelected(RoleType selected) {
        // 상대가 이미 같은 역할 선택 → 반대 역할 자동 배정
        myRole = (opponentRole != null && opponentRole == selected)
                ? selected.opposite() : selected;

        network.send(new GameMessage(GameMessage.Type.ROLE_SELECT, myRole));

        String waitMsg = isServer
                ? "클라이언트 연결/역할 선택 대기 중...\n내 역할: " + myRole.displayName()
                : "게임 시작 대기 중...\n내 역할: " + myRole.displayName();
        gamePanel.setWaiting(waitMsg);
        switchTo(CARD_GAME);

        // 서버: 상대가 이미 역할 전달했으면 즉시 시작 가능
        if (isServer && opponentRole != null) {
            doStartGame();
        }
    }

    // ── 네트워크 메시지 처리 ─────────────────────────────────────

    private NetworkManager.MessageHandler makeHandler() {
        return new NetworkManager.MessageHandler() {
            @Override public void onMessage(GameMessage msg) {
                SwingUtilities.invokeLater(() -> processMessage(msg));
            }
            @Override public void onDisconnected() {
                SwingUtilities.invokeLater(() ->
                        showError("상대방과의 연결이 끊겼습니다."));
            }
        };
    }

    private void processMessage(GameMessage msg) {
        switch (msg.getType()) {

            // 서버: 클라이언트 접속 확인 → WELCOME 전송, 이미 역할 선택했으면 알림
            case CONNECT:
                if (isServer) {
                    network.send(new GameMessage(GameMessage.Type.WELCOME, null));
                    if (myRole != null)
                        network.send(new GameMessage(GameMessage.Type.ROLE_SELECT, myRole));
                }
                break;

            case WELCOME:
                // 클라이언트: 연결 수락 확인 (추가 처리 불필요)
                break;

            // 상대방이 선택한 역할 수신
            case ROLE_SELECT:
                opponentRole = (RoleType) msg.getPayload();

                if (myRole == null) {
                    // 아직 선택 전 → 해당 역할 버튼 비활성화
                    rolePanel.disableRole(opponentRole);
                } else {
                    // 이미 내 역할 선택 완료 → 충돌 해소
                    if (myRole == opponentRole) myRole = opponentRole.opposite();
                    if (isServer) doStartGame();
                }
                break;

            // 클라이언트: 서버가 배정한 역할 수령
            case ROLE_CONFIRM:
                myRole = (RoleType) msg.getPayload();
                break;

            // 클라이언트: 게임 시작 신호 수신
            case GAME_START:
                if (!isServer && !gameStarted) {
                    gameStarted = true;
                    gamePanel.startAsClient(network, myRole);
                }
                break;

            // 서버: 클라이언트 키 입력
            case PLAYER_INPUT:
                if (isServer && gameStarted)
                    gamePanel.handleRemoteInput((Integer) msg.getPayload());
                break;

            // 클라이언트: 서버 게임 상태 반영
            case GAME_STATE:
                if (!isServer)
                    gamePanel.applyReceivedState((SharedGameState) msg.getPayload());
                break;

            case GAME_OVER:
                break;

            case DISCONNECT:
                showError("상대방이 연결을 끊었습니다.");
                break;
        }
    }

    // ── 서버 전용: 게임 시작 ─────────────────────────────────────
    private void doStartGame() {
        if (gameStarted || myRole == null || opponentRole == null) return;
        gameStarted = true;

        // 충돌 시 서버 우선: 클라이언트에게 반대 역할 확정 전달
        RoleType clientRole = myRole.opposite();
        network.send(new GameMessage(GameMessage.Type.ROLE_CONFIRM, clientRole));
        network.send(new GameMessage(GameMessage.Type.GAME_START, null));

        gamePanel.startAsServer(network, myRole);
    }

    // ── 게임 오버 처리 ────────────────────────────────────────────

    private void handleReturnToTitle() {
        disconnectAndReset();
        switchTo(CARD_TITLE);
    }

    private void handleRestart() {
        disconnectAndReset();
        rebuildGamePanel();
        if (isServer) {
            onCreateGame();
        } else {
            if (lastServerHost != null) {
                doClientConnect(lastServerHost);
            } else {
                onFindGame();
            }
        }
    }

    private void doClientConnect(String host) {
        GameClient client = new GameClient();
        network = client;
        try {
            client.connect(host, makeHandler());
        } catch (Exception e) {
            showError("연결 실패: " + e.getMessage() + "\nIP 주소를 확인하세요.");
            return;
        }
        rolePanel.hideServerIP();
        switchTo(CARD_ROLE);
    }

    private void disconnectAndReset() {
        if (network != null) {
            network.disconnect();
            network = null;
        }
        gameStarted  = false;
        myRole       = null;
        opponentRole = null;
        rolePanel.resetButtons();
    }

    /** 게임 패널을 새로 생성해 카드에 교체 (재시작 시 상태 초기화) */
    private void rebuildGamePanel() {
        cardPanel.remove(gamePanel);
        gamePanel = new CoopGamePanel();
        gamePanel.setGameOverListener(new CoopGamePanel.GameOverListener() {
            @Override public void onReturnToTitle() { handleReturnToTitle(); }
            @Override public void onRestartGame()   { handleRestart(); }
        });
        cardPanel.add(gamePanel, CARD_GAME);
        cardPanel.revalidate();
    }

    // ── UI 헬퍼 ──────────────────────────────────────────────────
    private void switchTo(String card) {
        cardLayout.show(cardPanel, card);
        pack();
        setLocationRelativeTo(null);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, "안내", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "오류", JOptionPane.ERROR_MESSAGE);
    }
}
