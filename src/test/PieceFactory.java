package test;

/** Factory 패턴 - 테트로미노 생성 */
public class PieceFactory {

    public static Piece createRandom(TetrisData data) {
        return create((int)(Math.random() * 7), data);
    }

    public static Piece create(int type, TetrisData data) {
        switch (type) {
            case 0: return new Bar(data);
            case 1: return new Tee(data);
            case 2: return new El(data);
            case 3: return new J(data);
            case 4: return new O(data);
            case 5: return new S(data);
            case 6: return new Z(data);
            default: return new Bar(data);
        }
    }
}
