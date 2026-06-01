package test;

import java.io.Serializable;

/** 네트워크 메시지 프로토콜 (직렬화 가능) */
public class GameMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        CONNECT,        // 클라이언트 → 서버: 연결 요청
        WELCOME,        // 서버 → 클라이언트: 연결 수락
        ROLE_SELECT,    // 양방향: 역할 선택 (payload = RoleType)
        ROLE_CONFIRM,   // 서버 → 클라이언트: 역할 확정 (payload = RoleType 클라이언트용)
        GAME_START,     // 서버 → 클라이언트: 게임 시작
        PLAYER_INPUT,   // 클라이언트 → 서버: 키 입력 (payload = Integer keyCode)
        GAME_STATE,     // 서버 → 클라이언트: 게임 상태 동기화
        GAME_OVER,      // 서버 → 클라이언트: 게임 종료 (payload = Integer score)
        DISCONNECT,     // 양방향: 연결 종료
        
        // ── [추가] 다시하기 투표 메시지 타입 (payload = Boolean 찬성여부) ──
        REMATCH_VOTE    
    }

    private final Type   type;
    private final Object payload;

    public GameMessage(Type type, Object payload) {
        this.type    = type;
        this.payload = payload;
    }

    public Type   getType()    { return type; }
    public Object getPayload() { return payload; }
}