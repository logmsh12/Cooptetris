package test;

public enum RoleType {
    BLOCK_MOVER,
    CHARACTER_MOVER;

    public RoleType opposite() {
        return this == BLOCK_MOVER ? CHARACTER_MOVER : BLOCK_MOVER;
    }

    public String displayName() {
        return this == BLOCK_MOVER ? "블록 이동" : "졸라맨 이동";
    }
}
