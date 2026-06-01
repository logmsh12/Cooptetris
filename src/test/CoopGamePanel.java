package test;

import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.*;

/**
 * 협동 테트리스 메인 게임 패널.
 *
 * [서버] 60 fps 물리 루프 실행, 블록 낙하·깃발·타이머 관리, 상태 동기화.
 * [클라이언트] 수신된 SharedGameState 렌더링, 키 입력 서버 전달.
 *
 * 디자인 패턴:
 *  Observer  - FlagManager·GameTimer 이벤트 수신
 *  Strategy  - PlayerRole (BlockPlayerRole / CharacterPlayerRole)
 */
public class CoopGamePanel extends JPanel implements Runnable, KeyListener, GameObserver {

    // ── 레이아웃 ──────────────────────────────────────────────────
    static final int CELL   = GameCharacter.CELL_SIZE;  // 25
    private static final int OX     = 10;
    private static final int OY     = 10;
    private static final int HUD_X  = OX + TetrisData.COL * CELL + 14;
    private static final int HUD_W  = 130;

    // ── 색상 ──────────────────────────────────────────────────────
    private static final Color[] COLORS = {
        new Color(50,  50,  50),
        new Color(255, 60,  60),   // 1 Bar
        new Color(60,  210, 60),   // 2 Tee
        new Color(50,  200, 255),  // 3 El
        new Color(255, 230, 0),    // 4 J
        new Color(255, 150, 0),    // 5 O
        new Color(200, 0,   240),  // 6 S
        new Color(60,  60,  240)   // 7 Z
    };

    // ── 게임 상태 (서버 전용) ────────────────────────────────────
    private CoopTetrisData data;
    private Piece          currentPiece;
    private Piece          nextPiece;
    private boolean        makeNew = true;
    private volatile boolean        gameRunning;
    private Thread         gameThread;

    private GameCharacter character;
    private FlagManager   flagManager;
    private GameTimer     gameTimer;

    private int score;
    private volatile int remainingSeconds = GameTimer.DEFAULT_SECONDS;
    private int currentFlagSeconds = GameTimer.DEFAULT_SECONDS; // 현재 깃발의 최대 제한시간

    // 낙하 누적 타이머
    private int dropIntervalMs = 1000;
    private long dropAccum;
    private long stateAccum;

    // 미리보기용 더미 데이터 (렌더링 전용)
    private static final TetrisData PREVIEW_DATA = new TetrisData();

    // ── 역할·네트워크 ─────────────────────────────────────────────
    private boolean      isServer;
    private RoleType     myRole;
    private PlayerRole   playerRole;
    private NetworkManager network;
    private final ConcurrentLinkedQueue<Integer> localQueue  = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Integer> remoteQueue = new ConcurrentLinkedQueue<>();

    // ── 클라이언트용 수신 상태 ────────────────────────────────────
    private SharedGameState clientState;

    // ── 게임 오버 콜백 ───────────────────────────────────────────
    public interface GameOverListener {
        void onReturnToTitle();
        void onRestartGame();
    }
    private GameOverListener gameOverListener;
    public void setGameOverListener(GameOverListener l) { gameOverListener = l; }

    // ── 대기 / 플레이 / 종료 ─────────────────────────────────────
    private enum PanelState { WAITING, PLAYING, GAME_OVER }
    private volatile PanelState panelState  = PanelState.WAITING;
    private String     waitMessage = "대기 중...";

    // ─────────────────────────────────────────────────────────────
    public CoopGamePanel() {
        setBackground(new Color(18, 18, 28));
        setFocusable(true);
        addKeyListener(this);
    }

    // ── 공개 초기화 ───────────────────────────────────────────────

    public void startAsServer(NetworkManager network, RoleType role) {
        this.isServer = true;
        this.network  = network;
        this.myRole   = role;

        data      = new CoopTetrisData();
        character = new GameCharacter();

        flagManager = new FlagManager();
        flagManager.addObserver(this);
        flagManager.spawnFlag(data, character);

        gameTimer = new GameTimer();
        gameTimer.addObserver(this);

        score       = 0;
        makeNew     = true;
        gameRunning = true;
        panelState  = PanelState.PLAYING;
        dropAccum   = 0;
        stateAccum  = 0;

        assignPlayerRole();
        gameTimer.start();

        gameThread = new Thread(this, "GameLoop");
        gameThread.setDaemon(true);
        gameThread.start();

        requestFocus();
    }

    public void startAsClient(NetworkManager network, RoleType role) {
        this.isServer = false;
        this.network  = network;
        this.myRole   = role;
        panelState    = PanelState.PLAYING;
        assignPlayerRole();
        requestFocus();
    }

    public void setWaiting(String message) {
        this.waitMessage = message;
        this.panelState  = PanelState.WAITING;
        repaint();
    }

    // ── 게임 루프 (서버, ~60 fps) ────────────────────────────────
    @Override
    public void run() {
        long prevNs = System.nanoTime();

        while (gameRunning) {
            long nowNs   = System.nanoTime();
            float dt     = Math.min((nowNs - prevNs) / 1_000_000_000f, 0.05f);
            long  elapsedMs = (nowNs - prevNs) / 1_000_000;
            prevNs = nowNs;

            // 0. 입력 처리 (EDT 큐 소비 - 게임 루프 쓰레드만 상태 수정)
            drainInputQueues();

            // 1. 캐릭터 물리
            character.update(dt, data);

            // 2. 낙하 블록 ↔ 캐릭터 충돌
            if (isPieceHittingCharacter()) {
                triggerGameOver("캐릭터가 블록에 맞았습니다!");
                return;
            }

            // 3. 깃발 수집
            flagManager.update(character, data);

            // 4. 블록 낙하 (dropIntervalMs 마다)
            dropAccum += elapsedMs;
            if (dropAccum >= dropIntervalMs) {
                dropAccum -= dropIntervalMs;
                dropStep();
                if (!gameRunning) return;
            }

            // 5. 네트워크 동기화 (50ms마다 = 20fps)
            stateAccum += elapsedMs;
            if (stateAccum >= 50) {
                stateAccum -= 50;
                sendGameState();
            }

            SwingUtilities.invokeLater(this::repaint);

            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }

    private void dropStep() {
        if (makeNew) {
            currentPiece = (nextPiece != null) ? nextPiece : PieceFactory.createRandom(data);
            nextPiece    = PieceFactory.createRandom(data);
            makeNew      = false;
        } else {
            if (currentPiece != null && currentPiece.moveDown()) {
                makeNew = true;
                if (currentPiece.copy()) { triggerGameOver("블록이 천장에 닿았습니다!"); return; }
                if (isCharacterCrushedByBoard()) { triggerGameOver("캐릭터가 블록에 깔렸습니다!"); return; }
                int linesBefore = data.getLine();
                data.removeLines();
                int cleared = data.getLine() - linesBefore;
                score += scoreForLines(cleared);
                currentPiece = null;
            }
        }
    }

    private int scoreForLines(int n) {
        switch (n) { case 1: return 100; case 2: return 300; case 3: return 500; case 4: return 800; default: return 0; }
    }

    // ── 낙하 블록 ↔ 캐릭터 충돌 ──────────────────────────────────

    /** 현재 낙하 중인 블록이 캐릭터와 겹치는지 확인 */
    private boolean isPieceHittingCharacter() {
        if (currentPiece == null) return false;
        for (int i = 0; i < 4; i++) {
            int pr = currentPiece.getY() + currentPiece.r[i];
            int pc = currentPiece.getX() + currentPiece.c[i];
            if (character.overlapsPieceBlock(pr, pc)) return true;
        }
        return false;
    }

    /**
     * 착지한 보드 블록이 캐릭터 내부에 겹치는지 확인.
     * 블록이 캐릭터 위치로 착지(copy)된 직후에 호출.
     * 캐릭터가 서 있는 블록(바로 아래)은 접촉만 하므로 false 반환.
     */
    private boolean isCharacterCrushedByBoard() {
        if (data == null || character == null) return false;
        float bxC = character.getBX(), byC = character.getBY();
        int tRow = Math.max(0, (int)(byC / CELL));
        int bRow = Math.min(TetrisData.ROW - 1, (int)((byC + GameCharacter.H - 1) / CELL));
        int lCol = Math.max(0, (int)(bxC / CELL));
        int rCol = Math.min(TetrisData.COL - 1, (int)((bxC + GameCharacter.W - 1) / CELL));
        for (int r = tRow; r <= bRow; r++) {
            for (int c = lCol; c <= rCol; c++) {
                if (data.getAt(r, c) != 0) {
                    float px = c * CELL, py = r * CELL;
                    if (bxC < px + CELL && bxC + GameCharacter.W > px
                     && byC < py + CELL && byC + GameCharacter.H > py)
                        return true;
                }
            }
        }
        return false;
    }

    private void triggerGameOver(String reason) {
        gameRunning = false;
        panelState  = PanelState.GAME_OVER;
        if (gameTimer != null) gameTimer.stop();
        if (network   != null) network.send(new GameMessage(GameMessage.Type.GAME_OVER, score));
        int lines = (data != null) ? data.getLine() : 0;
        int finalScore = score;
        SwingUtilities.invokeLater(() -> {
            repaint();
            showGameOverDialog(reason, finalScore, lines);
        });
    }

    private void showGameOverDialog(String reason, int finalScore, int finalLines) {
        Object[] options = {"다시하기", "메인화면으로"};
        int choice = JOptionPane.showOptionDialog(
            this,
            reason + "\n\n점수: " + finalScore + "  |  라인: " + finalLines,
            "게임 오버",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null, options, options[0]);
        if (gameOverListener != null) {
            if (choice == 0) gameOverListener.onRestartGame();
            else             gameOverListener.onReturnToTitle();
        }
    }

    // ── 네트워크 동기화 ───────────────────────────────────────────
    private void sendGameState() {
        if (network == null || !network.isConnected()) return;
        network.send(new GameMessage(GameMessage.Type.GAME_STATE, buildState()));
    }

    private SharedGameState buildState() {
        SharedGameState s = new SharedGameState();
        s.board = new int[TetrisData.ROW][TetrisData.COL];
        for (int r = 0; r < TetrisData.ROW; r++)
            for (int c = 0; c < TetrisData.COL; c++)
                s.board[r][c] = data.getAt(r, c);

        s.charBX = character.getBX();
        s.charBY = character.getBY();

        FlagObject flag = flagManager.getCurrentFlag();
        if (flag != null && !flag.isCollected()) {
            s.flagVisible = true;
            s.flagRow     = flag.getRow();
            s.flagCol     = flag.getCol();
        }

        s.remainingSeconds = remainingSeconds;
        s.maxFlagSeconds   = currentFlagSeconds;
        s.score            = score;
        s.linesCleared     = data.getLine();

        if (currentPiece != null) {
            s.hasPiece  = true;
            s.pieceType = currentPiece.getType();
            s.pieceX    = currentPiece.getX();
            s.pieceY    = currentPiece.getY();
            s.pieceR    = Arrays.copyOf(currentPiece.r, 4);
            s.pieceC    = Arrays.copyOf(currentPiece.c, 4);
        }
        if (nextPiece != null) {
            s.nextPieceType = nextPiece.getType();
            s.nextPieceR    = Arrays.copyOf(nextPiece.r, 4);
            s.nextPieceC    = Arrays.copyOf(nextPiece.c, 4);
        }
        s.gameOver = !gameRunning;
        return s;
    }

    public void applyReceivedState(SharedGameState s) {
        this.clientState      = s;
        this.remainingSeconds = s.remainingSeconds;
        this.score            = s.score;
        if (s.gameOver && panelState != PanelState.GAME_OVER) {
            panelState = PanelState.GAME_OVER;
            int fs = s.score, fl = s.linesCleared;
            SwingUtilities.invokeLater(() -> {
                repaint();
                showGameOverDialog("게임 오버!", fs, fl);
            });
        }
        SwingUtilities.invokeLater(this::repaint);
    }

    // ── 입력 요청 (PlayerRole → CoopGamePanel) ────────────────────

    // 블록 조작
    public void requestPieceLeft()   { if (isServer) localQueue.offer(KeyEvent.VK_LEFT);  else sendInput(KeyEvent.VK_LEFT,  true); }
    public void requestPieceRight()  { if (isServer) localQueue.offer(KeyEvent.VK_RIGHT); else sendInput(KeyEvent.VK_RIGHT, true); }
    public void requestPieceRotate() { if (isServer) localQueue.offer(KeyEvent.VK_UP);    else sendInput(KeyEvent.VK_UP,    true); }
    public void requestPieceDown()   { if (isServer) localQueue.offer(KeyEvent.VK_DOWN);  else sendInput(KeyEvent.VK_DOWN,  true); }
    public void requestHardDrop()    { if (isServer) localQueue.offer(KeyEvent.VK_SPACE); else sendInput(KeyEvent.VK_SPACE, true); }

    // 캐릭터 이동 (부드러운 이동 - 키 홀드)
    public void setCharLeft(boolean pressed) {
        if (isServer) localQueue.offer(pressed ? KeyEvent.VK_A : -KeyEvent.VK_A);
        else sendInput(KeyEvent.VK_A, pressed);
    }

    public void setCharRight(boolean pressed) {
        if (isServer) localQueue.offer(pressed ? KeyEvent.VK_D : -KeyEvent.VK_D);
        else sendInput(KeyEvent.VK_D, pressed);
    }

    public void requestCharJump() {
        if (isServer) localQueue.offer(KeyEvent.VK_SPACE);
        else sendInput(KeyEvent.VK_SPACE, true);
    }

    /** pressed=true → 양수 keyCode 전송 / false → 음수 전송 */
    private void sendInput(int keyCode, boolean pressed) {
        if (network != null && network.isConnected())
            network.send(new GameMessage(GameMessage.Type.PLAYER_INPUT,
                    pressed ? keyCode : -keyCode));
    }

    /** 서버가 클라이언트 입력 수신 - 큐에 넣고 게임 루프에서 처리 */
    public void handleRemoteInput(int signedKey) {
        remoteQueue.offer(signedKey);
    }

    /** 게임 루프 전용 - 로컬/원격 큐를 소비해 입력 적용 */
    private void drainInputQueues() {
        Integer key;
        while ((key = localQueue.poll())  != null) applyInput(myRole,            key);
        while ((key = remoteQueue.poll()) != null) applyInput(myRole.opposite(), key);
    }

    /**
     * 게임 루프 전용 - 역할에 따라 입력 적용.
     * signedKey: 양수=눌림, 음수=뗌
     */
    private void applyInput(RoleType role, int signedKey) {
        boolean pressed = signedKey > 0;
        int     key     = Math.abs(signedKey);

        if (role == RoleType.BLOCK_MOVER) {
            if (!pressed) return;
            switch (key) {
                case KeyEvent.VK_LEFT:  if (currentPiece != null) currentPiece.moveLeft();  break;
                case KeyEvent.VK_RIGHT: if (currentPiece != null) currentPiece.moveRight(); break;
                case KeyEvent.VK_UP:    if (currentPiece != null) currentPiece.rotate();    break;
                case KeyEvent.VK_DOWN:
                    if (currentPiece != null && currentPiece.moveDown()) {
                        makeNew = true;
                        if (currentPiece.copy()) { triggerGameOver("블록이 천장에 닿았습니다!"); return; }
                        if (isCharacterCrushedByBoard()) { triggerGameOver("캐릭터가 블록에 깔렸습니다!"); return; }
                        int lb = data.getLine();
                        data.removeLines();
                        score += scoreForLines(data.getLine() - lb);
                        currentPiece = null;
                    }
                    break;
                case KeyEvent.VK_SPACE:
                    if (currentPiece != null) {
                        while (!currentPiece.moveDown()) {
                            if (isPieceHittingCharacter()) { triggerGameOver("캐릭터가 블록에 맞았습니다!"); return; }
                        }
                        makeNew = true;
                        if (currentPiece.copy()) { triggerGameOver("블록이 천장에 닿았습니다!"); return; }
                        if (isCharacterCrushedByBoard()) { triggerGameOver("캐릭터가 블록에 깔렸습니다!"); return; }
                        int lb = data.getLine();
                        data.removeLines();
                        score += scoreForLines(data.getLine() - lb);
                        currentPiece = null;
                    }
                    break;
            }
        } else { // CHARACTER_MOVER
            if (pressed) {
                switch (key) {
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT:                        character.setMovingLeft(true);  break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT:                       character.setMovingRight(true); break;
                    case KeyEvent.VK_SPACE: case KeyEvent.VK_W: case KeyEvent.VK_UP: character.requestJump();        break;
                }
            } else {
                switch (key) {
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT:  character.setMovingLeft(false);  break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: character.setMovingRight(false); break;
                }
            }
        }
    }

    // ── Observer ─────────────────────────────────────────────────
    @Override
    public void onGameEvent(GameEvent event, Object payload) {
        switch (event) {
            case FLAG_COLLECTED:
                score += 1000;
                int nextSecs = flagManager.getNextTimerSeconds();
                currentFlagSeconds = nextSecs;
                gameTimer.reset(nextSecs);
                break;
            case TIMER_TICK:
            case TIMER_RESET:
                remainingSeconds = (Integer) payload;
                SwingUtilities.invokeLater(this::repaint);
                break;
            case TIMER_EXPIRED:
                if (isServer) triggerGameOver("시간 초과! 깃발을 수집하지 못했습니다.");
                break;
            default: break;
        }
    }

    // ── 렌더링 ────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (panelState == PanelState.WAITING) {
            drawWaiting(g2);
        } else if (isServer) {
            drawBoardBackground(g2);
            drawSettledBlocks(g2);
            drawGhostPiece(g2, currentPiece);
            drawCurrentPieceLocal(g2);
            character.draw(g2, OX, OY);
            drawFlagLocal(g2);
            drawHUD(g2, score, remainingSeconds, data.getLine(), currentFlagSeconds);
            drawNextPreview(g2, nextPiece);
            drawControls(g2);
        } else {
            drawBoardBackground(g2);
            if (clientState != null) {
                drawSettledBlocksClient(g2);
                drawGhostPieceClient(g2);
                drawCurrentPieceClient(g2);
                drawCharacterClient(g2);
                drawFlagClient(g2);
                drawHUD(g2, clientState.score, clientState.remainingSeconds, clientState.linesCleared,
                        clientState.maxFlagSeconds > 0 ? clientState.maxFlagSeconds : GameTimer.DEFAULT_SECONDS);
                drawNextPreviewClient(g2);
                drawControls(g2);
            }
        }
        g2.dispose();
    }

    // ── 보드 배경 ─────────────────────────────────────────────────
    private void drawBoardBackground(Graphics2D g) {
        g.setColor(new Color(28, 28, 42));
        g.fillRect(OX, OY, TetrisData.COL * CELL, TetrisData.ROW * CELL);
        g.setColor(new Color(50, 50, 72));
        for (int r = 0; r <= TetrisData.ROW; r++)
            g.drawLine(OX, OY + r * CELL, OX + TetrisData.COL * CELL, OY + r * CELL);
        for (int c = 0; c <= TetrisData.COL; c++)
            g.drawLine(OX + c * CELL, OY, OX + c * CELL, OY + TetrisData.ROW * CELL);
        g.setColor(new Color(100, 100, 150));
        g.setStroke(new BasicStroke(2));
        g.drawRect(OX, OY, TetrisData.COL * CELL, TetrisData.ROW * CELL);
        g.setStroke(new BasicStroke(1));
    }

    // ── 서버 렌더링 ───────────────────────────────────────────────
    private void drawSettledBlocks(Graphics2D g) {
        for (int r = 0; r < TetrisData.ROW; r++)
            for (int c = 0; c < TetrisData.COL; c++) {
                int v = data.getAt(r, c);
                if (v != 0) fillCell(g, c, r, COLORS[v]);
            }
    }

    private void drawCurrentPieceLocal(Graphics2D g) {
        if (currentPiece == null) return;
        for (int i = 0; i < 4; i++)
            fillCell(g, currentPiece.getX() + currentPiece.c[i],
                        currentPiece.getY() + currentPiece.r[i],
                        COLORS[currentPiece.getType()]);
    }

    private void drawFlagLocal(Graphics2D g) {
        FlagObject flag = (flagManager != null) ? flagManager.getCurrentFlag() : null;
        if (flag != null && !flag.isCollected()) flag.draw(g, CELL, OX, OY);
    }

    // ── 고스트(낙하 예상 위치) ───────────────────────────────────
    private void drawGhostPiece(Graphics2D g, Piece piece) {
        if (piece == null || data == null) return;
        int ghostY = piece.getY();
        int x = piece.getX();
        while (canMoveDown(x, ghostY, piece.r, piece.c)) ghostY++;
        if (ghostY == piece.getY()) return; // 이미 바닥
        Color base = COLORS[piece.getType()];
        Color ghost = new Color(base.getRed(), base.getGreen(), base.getBlue(), 65);
        for (int i = 0; i < 4; i++)
            fillCellAlpha(g, x + piece.c[i], ghostY + piece.r[i], ghost);
    }

    private boolean canMoveDown(int x, int y, int[] r, int[] c) {
        for (int i = 0; i < 4; i++) {
            int nr = y + r[i] + 1, nc = x + c[i];
            if (nr >= TetrisData.ROW) return false;
            if (data.getAt(nr, nc) != 0) return false;
        }
        return true;
    }

    // ── 클라이언트 렌더링 ─────────────────────────────────────────
    private void drawSettledBlocksClient(Graphics2D g) {
        for (int r = 0; r < TetrisData.ROW; r++)
            for (int c = 0; c < TetrisData.COL; c++) {
                int v = clientState.board[r][c];
                if (v != 0) fillCell(g, c, r, COLORS[v]);
            }
    }

    private void drawGhostPieceClient(Graphics2D g) {
        if (!clientState.hasPiece) return;
        // 클라이언트는 보드 상태로 고스트 계산
        int ghostY = clientState.pieceY;
        int x = clientState.pieceX;
        int[] r = clientState.pieceR, c = clientState.pieceC;
        while (canMoveDownClient(x, ghostY, r, c)) ghostY++;
        if (ghostY == clientState.pieceY) return;
        Color base = COLORS[clientState.pieceType];
        Color ghost = new Color(base.getRed(), base.getGreen(), base.getBlue(), 65);
        for (int i = 0; i < 4; i++)
            fillCellAlpha(g, x + c[i], ghostY + r[i], ghost);
    }

    private boolean canMoveDownClient(int x, int y, int[] r, int[] c) {
        for (int i = 0; i < 4; i++) {
            int nr = y + r[i] + 1, nc = x + c[i];
            if (nr >= TetrisData.ROW) return false;
            if (clientState.board[nr][nc] != 0) return false;
        }
        return true;
    }

    private void drawCurrentPieceClient(Graphics2D g) {
        if (!clientState.hasPiece) return;
        Color col = COLORS[clientState.pieceType];
        for (int i = 0; i < 4; i++)
            fillCell(g, clientState.pieceX + clientState.pieceC[i],
                        clientState.pieceY + clientState.pieceR[i], col);
    }

    private void drawCharacterClient(Graphics2D g) {
        int x  = OX + (int)clientState.charBX;
        int y  = OY + (int)clientState.charBY;
        int cx = x + GameCharacter.W / 2;
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        int headR  = GameCharacter.W / 5;
        int headCY = y + GameCharacter.H / 5;
        g.drawOval(cx - headR, headCY - headR, headR * 2, headR * 2);
        int bTop = headCY + headR + 1, bMid = y + GameCharacter.H / 2 + 2, bBot = y + GameCharacter.H - 3;
        g.drawLine(cx, bTop, cx, bMid);
        g.drawLine(x + 2, bTop + (bMid-bTop)/2, x + GameCharacter.W - 2, bTop + (bMid-bTop)/2);
        g.drawLine(cx, bMid, x + 3, bBot);
        g.drawLine(cx, bMid, x + GameCharacter.W - 3, bBot);
    }

    private void drawFlagClient(Graphics2D g) {
        if (clientState.flagVisible)
            new FlagObject(clientState.flagRow, clientState.flagCol).draw(g, CELL, OX, OY);
    }

    // ── 공통 셀 그리기 ────────────────────────────────────────────
    private void fillCell(Graphics2D g, int col, int row, Color base) {
        int x = OX + col * CELL, y = OY + row * CELL;
        g.setColor(base);
        g.fillRect(x + 1, y + 1, CELL - 2, CELL - 2);
        g.setColor(base.brighter());
        g.drawLine(x + 1, y + 1, x + CELL - 2, y + 1);
        g.drawLine(x + 1, y + 1, x + 1, y + CELL - 2);
        g.setColor(base.darker());
        g.drawLine(x + 1, y + CELL - 2, x + CELL - 2, y + CELL - 2);
        g.drawLine(x + CELL - 2, y + 1, x + CELL - 2, y + CELL - 2);
    }

    private void fillCellAlpha(Graphics2D g, int col, int row, Color c) {
        g.setColor(c);
        g.fillRect(OX + col * CELL + 1, OY + row * CELL + 1, CELL - 2, CELL - 2);
    }

    // ── HUD ───────────────────────────────────────────────────────
    private void drawHUD(Graphics2D g, int sc, int timeLeft, int lines, int maxSecs) {
        int sx = HUD_X;
        g.setColor(new Color(22, 22, 36));
        g.fillRect(HUD_X - 4, 0, getWidth() - HUD_X + 4, getHeight());

        // 제한시간
        label(g, "남은 시간", sx, 24);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 26));
        boolean urgent = timeLeft <= 5;
        g.setColor(urgent ? Color.RED : timeLeft <= 10 ? Color.ORANGE : new Color(0, 210, 255));
        g.drawString(timeLeft + " 초", sx, 50);

        float ratio = Math.max(0f, (float) timeLeft / Math.max(1, maxSecs));
        int   barW  = getWidth() - sx - 8;
        g.setColor(new Color(50, 50, 70));
        g.fillRoundRect(sx, 56, barW, 8, 4, 4);
        g.setColor(ratio < 0.33f ? Color.RED : ratio < 0.66f ? Color.ORANGE : new Color(0, 200, 80));
        g.fillRoundRect(sx, 56, (int)(barW * ratio), 8, 4, 4);

        // 점수
        label(g, "점수", sx, 80);
        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.setColor(new Color(255, 220, 0));
        g.drawString(String.valueOf(sc), sx, 100);

        // 라인
        label(g, "라인", sx, 118);
        g.setFont(new Font("Consolas", Font.PLAIN, 18));
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(lines), sx, 136);

        // 역할
        label(g, "내 역할", sx, 154);
        g.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        g.setColor(new Color(100, 255, 180));
        g.drawString(myRole != null ? myRole.displayName() : "-", sx, 170);

        // 안내
        g.setFont(new Font("맑은 고딕", Font.PLAIN, 10));
        g.setColor(new Color(255, 220, 80));
        g.drawString("★ 깃발에 닿으면 즉시 수집!", sx, 188);
        g.setColor(new Color(200, 100, 100));
        g.drawString("  블록에 맞으면 게임 오버!", sx, 200);
    }

    // ── 다음 블록 미리보기 ────────────────────────────────────────
    private void drawNextPreview(Graphics2D g, Piece next) {
        int sx = HUD_X, sy = 215;
        label(g, "NEXT", sx, sy);
        if (next == null) return;
        drawPreviewPiece(g, sx, sy + 4, next.getType(), next.r, next.c);
    }

    private void drawNextPreviewClient(Graphics2D g) {
        int sx = HUD_X, sy = 215;
        label(g, "NEXT", sx, sy);
        if (clientState.nextPieceType <= 0 || clientState.nextPieceR == null) return;
        drawPreviewPiece(g, sx, sy + 4, clientState.nextPieceType,
                clientState.nextPieceR, clientState.nextPieceC);
    }

    private void drawPreviewPiece(Graphics2D g, int sx, int sy, int type, int[] r, int[] c) {
        int pCell = 13;
        int boxW = 4 * pCell + 10, boxH = 4 * pCell + 10;
        g.setColor(new Color(32, 32, 50));
        g.fillRoundRect(sx, sy, boxW, boxH, 6, 6);
        g.setColor(new Color(70, 70, 100));
        g.drawRoundRect(sx, sy, boxW, boxH, 6, 6);
        Color base = COLORS[type];
        for (int i = 0; i < 4; i++) {
            int px = sx + 5 + (c[i] + 1) * pCell;
            int py = sy + 5 + (r[i] + 1) * pCell;
            g.setColor(base);
            g.fillRect(px + 1, py + 1, pCell - 2, pCell - 2);
            g.setColor(base.brighter());
            g.drawRect(px + 1, py + 1, pCell - 3, pCell - 3);
        }
    }

    // ── 조작법 안내 ───────────────────────────────────────────────
    private void drawControls(Graphics2D g) {
        int sx = HUD_X, y = 300;
        label(g, "[ 조작법 ]", sx, y); y += 14;
        g.setFont(new Font("맑은 고딕", Font.PLAIN, 10));
        g.setColor(new Color(180, 180, 200));
        if (myRole == RoleType.BLOCK_MOVER) {
            String[] lines = { "← →  블록 이동", "↑      회전", "↓      한 칸 내리기", "Space 즉시 낙하" };
            for (String l : lines) { g.drawString(l, sx, y); y += 13; }
        } else {
            String[] lines = { "← →  좌우 이동", "Space  점프 (최대 2칸)", "  (A D 도 사용 가능)" };
            for (String l : lines) { g.drawString(l, sx, y); y += 13; }
        }
    }

    private void label(Graphics2D g, String text, int x, int y) {
        g.setFont(new Font("맑은 고딕", Font.BOLD, 11));
        g.setColor(new Color(130, 150, 210));
        g.drawString(text, x, y);
    }

    // ── 대기 화면 ─────────────────────────────────────────────────
    private void drawWaiting(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        g.setColor(new Color(18, 18, 28));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0, 200, 255));
        g.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        FontMetrics fm = g.getFontMetrics();
        String[] lines = waitMessage.split("\n");
        int lineH  = fm.getHeight();
        int startY = (h - lineH * lines.length) / 2 + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, (w - fm.stringWidth(line)) / 2, startY);
            startY += lineH;
        }
    }

    // ── KeyListener ───────────────────────────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        if (panelState != PanelState.PLAYING || playerRole == null) return;
        playerRole.handleKeyPressed(e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (panelState != PanelState.PLAYING || playerRole == null) return;
        playerRole.handleKeyReleased(e.getKeyCode());
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ── 크기 / 초기화 ─────────────────────────────────────────────
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(OX + TetrisData.COL * CELL + HUD_W + 10,
                             OY + TetrisData.ROW * CELL + OY);
    }

    private void assignPlayerRole() {
        playerRole = (myRole == RoleType.BLOCK_MOVER)
                ? new BlockPlayerRole(this) : new CharacterPlayerRole(this);
    }

    public boolean isGameRunning() { return gameRunning; }
}
