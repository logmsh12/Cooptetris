package test;

public class TetrisData {
	public static final int ROW = 20;
	public static final int COL = 10;
	
	private int data[][];
	private int line;
	
	public TetrisData() {
		data = new int[ROW][COL];
	}
	public int getAt(int x, int y) {
		if(x <0 || x >= ROW || y < 0 || y >= COL)
			return 0;
		return data[x][y];
					
	}
	public void setAt(int x, int y, int v) {
		data[x][y] = v;
	}
	public int getLine() {
		return line;
	}
	public synchronized void removeLines() {
		for (int i = ROW - 1; i >= 0; i--) {
			boolean full = true;
			for (int k = 0; k < COL; k++) {
				if (data[i][k] == 0) { full = false; break; }
			}
			if (full) {
				line++;
				for (int x = i; x > 0; x--) {
					for (int y = 0; y < COL; y++) {
						data[x][y] = data[x-1][y];
					}
				}
				for (int y = 0; y < COL; y++) data[0][y] = 0;
				i++; // 줄이 내려왔으므로 같은 행 재검사
			}
		}
	}
	public void clear() {
		for(int i=0; i < ROW; i++) {
			for(int k = 0; k < COL; k++) {
				data[i][k] = 0;
			}
		}
	}
	public void dump() {
		for(int i =0; i < ROW; i++) {
			for(int k =0; k <COL; k++) {
				System.out.print(data[i][k] + " ");
			}
			System.out.println();
		}
	}

}
