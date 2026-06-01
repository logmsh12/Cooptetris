package test;

import java.util.*;

/**
 * Observer 패턴 Subject - 깃발 생성·회수 관리.
 * 캐릭터가 깃발에 닿으면 즉시 수집 → FLAG_COLLECTED 발행 → 다음 깃발 자동 생성.
 *
 * 깃발 생성 범위: 최상단 블록보다 1~2칸 위(topLimit)부터 바닥까지 랜덤.
 * BFS로 캐릭터가 도달 불가능한 밀폐 공간은 제외한다.
 */
public class FlagManager implements GameSubject {

    private static final int START_SECONDS = 30;
    private static final int STEP_SECONDS  = 2;
    private static final int MIN_SECONDS   = 20;

    private final List<GameObserver> observers = new ArrayList<>();
    private FlagObject currentFlag;
    private boolean    firstFlag      = true;
    private int        flagsCollected = 0;

    @Override public void addObserver(GameObserver o)    { observers.add(o); }
    @Override public void removeObserver(GameObserver o) { observers.remove(o); }
    @Override public void notifyObservers(GameEvent event, Object payload) {
        for (GameObserver o : new ArrayList<>(observers)) o.onGameEvent(event, payload);
    }

    // ── 깃발 생성 ─────────────────────────────────────────────────
    public FlagObject spawnFlag(CoopTetrisData data, GameCharacter character) {
        return spawnFlag(data, character, -1, -1);
    }

    /**
     * 캐릭터 현재 위치(avoidRow, avoidCol)는 회피. -1 전달 시 회피 없음.
     * BFS로 계산한 도달 가능 셀에서만 생성한다.
     */
    public FlagObject spawnFlag(CoopTetrisData data, GameCharacter character,
                                int avoidRow, int avoidCol) {
        // 첫 깃발: 보드 세로 2/3 지점(아래쪽), 랜덤 열
        if (firstFlag) {
            firstFlag = false;
            int row = TetrisData.ROW * 2 / 3;
            int col = (int)(Math.random() * TetrisData.COL);
            currentFlag = new FlagObject(row, col);
            notifyObservers(GameEvent.FLAG_SPAWNED, currentFlag);
            return currentFlag;
        }

        // 캐릭터 그리드 위치(바닥 셀) 계산
        int startBottom = TetrisData.ROW - 1;
        int startCol    = TetrisData.COL / 2;
        if (character != null) {
            startBottom = (int)(character.getBY() / GameCharacter.CELL_SIZE) + 1;
            startCol    = (int)(character.getBX() / GameCharacter.CELL_SIZE);
        }
        Set<Long> reachable = computeReachableCells(data, startBottom, startCol);

        int highestRow = data.getHighestBlockRow();
        int offset     = 1 + (int)(Math.random() * 2);
        int topLimit   = Math.max(2, highestRow - offset);
        int range      = TetrisData.ROW - topLimit;

        int row = topLimit, col = TetrisData.COL / 2;
        boolean placed = false;

        // 랜덤 위치 시도 (최대 80회) - 도달 가능한 빈 셀만
        for (int attempt = 0; attempt < 80; attempt++) {
            int r = topLimit + (int)(Math.random() * range);
            int c = (int)(Math.random() * TetrisData.COL);
            if (data.getAt(r, c) == 0
                    && !(r == avoidRow && c == avoidCol)
                    && isFlagReachable(reachable, r, c)) {
                row = r; col = c; placed = true; break;
            }
        }

        // 랜덤 실패 시 전체 스캔 (극단적 상황 대비)
        if (!placed) {
            outer:
            for (int r = topLimit; r < TetrisData.ROW; r++) {
                for (int c = 0; c < TetrisData.COL; c++) {
                    if (data.getAt(r, c) == 0
                            && !(r == avoidRow && c == avoidCol)
                            && isFlagReachable(reachable, r, c)) {
                        row = r; col = c; break outer;
                    }
                }
            }
        }

        currentFlag = new FlagObject(row, col);
        notifyObservers(GameEvent.FLAG_SPAWNED, currentFlag);
        return currentFlag;
    }

    // ── 도달 가능성 BFS ───────────────────────────────────────────

    /**
     * 캐릭터(1×2칸)가 도달 가능한 bottomRow 집합을 BFS로 계산.
     * 이동: 좌/우, 낙하(↓1), 점프(↑1, ↑2).
     */
    private Set<Long> computeReachableCells(CoopTetrisData data, int startBottom, int startCol) {
        Set<Long> visited = new HashSet<>();
        if (!isValidCharPos(data, startBottom, startCol)) return visited;

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startBottom, startCol});
        visited.add(encode(startBottom, startCol));

        // 이동 벡터: 좌, 우, 아래(낙하), 위1(점프), 위2(점프)
        int[][] moves = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}, {-2, 0}};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int r = cur[0], c = cur[1];
            for (int[] m : moves) {
                int nr = r + m[0], nc = c + m[1];
                long key = encode(nr, nc);
                if (!visited.contains(key) && isValidCharPos(data, nr, nc)) {
                    visited.add(key);
                    queue.add(new int[]{nr, nc});
                }
            }
        }
        return visited;
    }

    /** 캐릭터 바닥 셀이 (bottomRow, col)일 때 유효한 위치인지 확인 */
    private boolean isValidCharPos(CoopTetrisData data, int bottomRow, int col) {
        if (col < 0 || col >= TetrisData.COL) return false;
        if (bottomRow < 1 || bottomRow >= TetrisData.ROW) return false;
        return data.getAt(bottomRow, col) == 0 && data.getAt(bottomRow - 1, col) == 0;
    }

    /**
     * 깃발(flagRow, flagCol)이 reachable 집합에서 닿을 수 있는지 확인.
     * 캐릭터가 2칸 키이므로, 바닥 셀이 flagRow 또는 flagRow+1이면 닿음.
     */
    private boolean isFlagReachable(Set<Long> reachable, int flagRow, int flagCol) {
        return reachable.contains(encode(flagRow, flagCol))
            || reachable.contains(encode(flagRow + 1, flagCol));
    }

    private long encode(int row, int col) { return (long) row * 100 + col; }

    // ── 업데이트 ──────────────────────────────────────────────────

    public void update(GameCharacter character, CoopTetrisData data) {
        if (currentFlag == null || currentFlag.isCollected()) return;

        if (character.overlapsFlag(currentFlag.getRow(), currentFlag.getCol())) {
            currentFlag.collect();
            flagsCollected++;
            notifyObservers(GameEvent.FLAG_COLLECTED, currentFlag);

            int charGridRow = (int)((character.getBY() + GameCharacter.H / 2f) / GameCharacter.CELL_SIZE);
            int charGridCol = (int)((character.getBX() + GameCharacter.W / 2f) / GameCharacter.CELL_SIZE);
            spawnFlag(data, character, charGridRow, charGridCol);
        }
    }

    /** 다음 깃발에 적용할 제한시간 (수집할수록 감소, 최소 MIN_SECONDS) */
    public int getNextTimerSeconds() {
        return Math.max(MIN_SECONDS, START_SECONDS - flagsCollected * STEP_SECONDS);
    }

    public FlagObject getCurrentFlag() { return currentFlag; }
}
