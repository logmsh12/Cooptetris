package test;

import java.io.Serializable;

/** 서버 → 클라이언트 전송용 게임 상태 스냅샷 */
public class SharedGameState implements Serializable {

    private static final long serialVersionUID = 2L;

    // 보드
    public int[][] board;

    // 낙하 중인 블록
    public boolean hasPiece;
    public int     pieceType;
    public int     pieceX, pieceY;
    public int[]   pieceR, pieceC;

    // 다음 블록 (미리보기)
    public int   nextPieceType;
    public int[] nextPieceR, nextPieceC;

    // 캐릭터 (픽셀 좌표 - 부드러운 렌더링)
    public float charBX;
    public float charBY;

    // 깃발
    public boolean flagVisible;
    public int     flagRow, flagCol;

    // HUD
    public int  remainingSeconds;
    public int  maxFlagSeconds;   // 현재 깃발의 제한시간 (타이머 바 비율 계산용)
    public int  score;
    public int  linesCleared;

    // 상태
    public boolean gameOver;
}
