package test;

/** TetrisData 확장 - 협동 게임에 필요한 보드 분석 기능 추가 (상속) */
public class CoopTetrisData extends TetrisData {

    /** 쌓인 블록 중 가장 높은(위) 블록의 행 번호를 반환. 비어있으면 ROW-1 반환 */
    public int getHighestBlockRow() {
        for (int row = 0; row < ROW; row++) {
            for (int col = 0; col < COL; col++) {
                if (getAt(row, col) != 0) return row;
            }
        }
        return ROW - 1;
    }

    public boolean isBoardEmpty() {
        return getHighestBlockRow() == ROW - 1;
    }
}
