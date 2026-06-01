package test;

public class S extends Piece {
	public S(TetrisData data) {
		super(data);
		c[0] = 0;  r[0] = 0;   // 상단 좌측 (기준)
		c[1] = 1;  r[1] = 0;   // 상단 우측
		c[2] = -1; r[2] = 1;   // 하단 좌측
		c[3] = 0;  r[3] = 1;   // 하단 우측
		
	}
	public int getType() {
		return 6;
	}
	public int roteType() {
		return 2; // 2방향 회전
	}

}