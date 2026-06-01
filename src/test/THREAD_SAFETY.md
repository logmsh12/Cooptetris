# 협동 테트리스 - 스레드 안전성 문제 분석 및 해결 보고서

---

## 1. 문제 정의

### 1.1 멀티스레드 구조

서버 측 게임은 두 개의 스레드가 동시에 실행된다.

| 스레드 | 생성 위치 | 역할 |
|---|---|---|
| **GameLoop** | `CoopGamePanel.startAsServer()` | 60fps 물리 루프 — 블록 낙하, 캐릭터 이동, 충돌 판정 |
| **EDT** (Event Dispatch Thread) | JVM 기본 제공 | 키 입력 처리, 네트워크 콜백(`SwingUtilities.invokeLater`) |

### 1.2 공유 상태 (수정 전)

두 스레드가 **동기화 없이** 아래 변수들을 동시에 읽고 썼다.

```
currentPiece   — 낙하 중인 테트로미노 객체
makeNew        — 다음 틱에 새 블록 생성 여부 플래그
data           — 보드 2차원 배열
score          — 점수
gameRunning    — 게임 루프 종료 조건
remainingSeconds — 남은 시간 (타이머 스레드 ↔ EDT)
panelState     — 현재 화면 상태 (게임 루프/타이머 스레드 ↔ EDT)
```

---

## 2. 발생 가능한 버그 시나리오

### 시나리오 A — NullPointerException (가장 빈번)

```
[GameLoop 스레드]                    [EDT]
currentPiece != null  → true
                                     currentPiece = null   ← 착지 처리
currentPiece.moveDown()              ← NullPointerException!
```

`null` 체크와 실제 메서드 호출 사이에 EDT가 끼어들어 `currentPiece`를 `null`로 바꾸면
다음 줄에서 NPE가 발생한다. 간헐적으로 재현되므로 디버깅이 매우 어렵다.

### 시나리오 B — 점수 유실 (데이터 경쟁)

```
[GameLoop 스레드]     [EDT — 깃발 수집]
score = 300          score += 1000     ← 동시 쓰기
                                       결과: 둘 중 하나만 남음
                                       (300 또는 1000, 1300이 아님)
```

Java의 `int` 쓰기는 원자적이지만 **읽기-수정-쓰기** (`score += 1000`) 는 원자적이지 않다.

### 시나리오 C — makeNew 플래그 꼬임

```
[GameLoop 스레드]          [EDT — requestPieceDown()]
dropStep() 진입            currentPiece.moveDown() 반환 true
makeNew = false            makeNew = true
currentPiece 생성 중       currentPiece.copy() 호출
                           → 같은 블록이 보드에 두 번 박힘
```

`makeNew`가 양쪽에서 동시에 수정되면 블록 생성/착지 로직이 꼬인다.

### 시나리오 D — gameRunning 가시성 문제

`GameTimer` 스레드가 `gameRunning = false`로 쓴 뒤, GameLoop 스레드의 CPU 캐시에
이전 값이 남아 있으면 루프가 즉시 종료되지 않는다.
`volatile` 키워드 없이는 JVM이 이 쓰기를 즉시 다른 스레드에 전파하지 않아도 된다.

---

## 3. 해결 방법 비교

### 방법 A — synchronized 블록

공유 변수 접근마다 `synchronized` 블록으로 감싼다.

```java
// 예시
synchronized (lock) {
    if (currentPiece != null) currentPiece.moveLeft();
}
```

| 장점 | 단점 |
|---|---|
| 구현이 직관적 | 어디에 락을 걸지 판단하기 어려움 |
| 기존 코드 구조 유지 | 락 범위를 잘못 잡으면 데드락 가능 |
| | GameLoop와 EDT가 여전히 같은 데이터를 공유 |
| | 락 경합으로 인한 미세한 성능 저하 |
| | 락 누락 시 버그가 그대로 재현 |

### 방법 B — 입력 큐 (채택) ✓

EDT는 큐에만 넣고, GameLoop 스레드가 매 틱 시작에 큐를 소비해서 처리한다.

```
[EDT]                          [GameLoop 스레드]
키 입력 → localQueue.offer()
네트워크 → remoteQueue.offer()  → drainInputQueues() → 상태 수정
```

| 장점 | 단점 |
|---|---|
| 공유 상태를 한 스레드만 수정 → 근본적 해결 | 입력이 큐에서 소비될 때까지 ~16ms 지연 |
| 락이 필요 없음 | 구조 변경 필요 (기존 코드 리팩터링) |
| 데드락 불가능 | |
| 코드 흐름이 단순하고 추적하기 쉬움 | |
| ConcurrentLinkedQueue가 내부적으로 락-프리 알고리즘 사용 | |

### 방법 C — 단일 스레드로 통합

게임 루프를 제거하고 Swing Timer(`javax.swing.Timer`)로 60fps를 구현한다.
모든 로직이 EDT에서 실행되므로 경쟁 조건 자체가 없어진다.

| 장점 | 단점 |
|---|---|
| 스레드 문제 원천 차단 | EDT 블로킹 시 UI 응답 없음(ANR) |
| 구현 단순 | 물리 연산이 무거울 때 렌더링도 같이 느려짐 |
| | 게임 루프 정밀도가 Swing Timer에 의존 (~10ms 정밀도) |

**방법 B를 채택한 이유**: 기존의 60fps 물리 루프 구조를 유지하면서 스레드 경계를 명확히 분리할 수 있고, 락 없이도 안전하기 때문.

---

## 4. 코드 변경 내역

### 4.1 volatile 필드 추가

JVM 캐싱으로 인한 가시성 문제를 해결한다.

```java
// 수정 전
private boolean    gameRunning;
private int        remainingSeconds = GameTimer.DEFAULT_SECONDS;
private PanelState panelState       = PanelState.WAITING;

// 수정 후
private volatile boolean    gameRunning;
private volatile int        remainingSeconds = GameTimer.DEFAULT_SECONDS;
private volatile PanelState panelState       = PanelState.WAITING;
```

- `gameRunning`: GameTimer 스레드가 쓰고 GameLoop 스레드가 읽음
- `remainingSeconds`: GameTimer 스레드가 쓰고 EDT(렌더링)가 읽음
- `panelState`: GameLoop/GameTimer 스레드가 쓰고 EDT(paintComponent)가 읽음

### 4.2 입력 큐 필드 추가

```java
private final ConcurrentLinkedQueue<Integer> localQueue  = new ConcurrentLinkedQueue<>();
private final ConcurrentLinkedQueue<Integer> remoteQueue = new ConcurrentLinkedQueue<>();
```

- `localQueue` : 서버 플레이어 본인의 키 입력
- `remoteQueue` : 클라이언트에서 수신한 키 입력
- **인코딩**: 양수 = 키 눌림(pressed), 음수 = 키 뗌(released)

### 4.3 입력 메서드 → 큐에 넣기만

```java
// 수정 전 — EDT가 직접 currentPiece를 수정
public void requestPieceLeft() {
    if (isServer) { if (currentPiece != null) currentPiece.moveLeft(); repaint(); }
    else sendInput(KeyEvent.VK_LEFT, true);
}

// 수정 후 — EDT는 큐에 넣기만
public void requestPieceLeft() {
    if (isServer) localQueue.offer(KeyEvent.VK_LEFT);
    else sendInput(KeyEvent.VK_LEFT, true);
}
```

동일하게 변경된 메서드: `requestPieceRight`, `requestPieceRotate`, `requestPieceDown`,
`requestHardDrop`, `setCharLeft`, `setCharRight`, `requestCharJump`

### 4.4 handleRemoteInput → 큐에 넣기만

```java
// 수정 전 — EDT(SwingUtilities.invokeLater)가 직접 character/currentPiece 수정
public void handleRemoteInput(int signedKey) {
    boolean pressed = signedKey > 0;
    int key = Math.abs(signedKey);
    if (myRole == RoleType.BLOCK_MOVER) {
        if (pressed) { ... character.setMovingLeft(true); ... }
        // ...
    }
}

// 수정 후 — 큐에 넣고 끝
public void handleRemoteInput(int signedKey) {
    remoteQueue.offer(signedKey);
}
```

### 4.5 게임 루프 — 매 틱 시작에 큐 소비

```java
@Override
public void run() {
    long prevNs = System.nanoTime();
    while (gameRunning) {
        // ...dt, elapsedMs 계산...

        // 0. 입력 처리 (EDT 큐 소비 - 게임 루프 스레드만 상태 수정)
        drainInputQueues();   // ← 추가

        // 1. 캐릭터 물리
        character.update(dt, data);
        // 2. 충돌, 3. 깃발, 4. 블록 낙하, 5. 동기화 ...
    }
}
```

### 4.6 drainInputQueues / applyInput

```java
private void drainInputQueues() {
    Integer key;
    while ((key = localQueue.poll())  != null) applyInput(myRole,            key);
    while ((key = remoteQueue.poll()) != null) applyInput(myRole.opposite(), key);
}

private void applyInput(RoleType role, int signedKey) {
    boolean pressed = signedKey > 0;
    int     key     = Math.abs(signedKey);

    if (role == RoleType.BLOCK_MOVER) {
        if (!pressed) return;
        switch (key) {
            case KeyEvent.VK_LEFT:  if (currentPiece != null) currentPiece.moveLeft();  break;
            case KeyEvent.VK_RIGHT: if (currentPiece != null) currentPiece.moveRight(); break;
            case KeyEvent.VK_UP:    if (currentPiece != null) currentPiece.rotate();    break;
            case KeyEvent.VK_DOWN:  /* 소프트 드롭 + 줄 제거 + 점수 */ break;
            case KeyEvent.VK_SPACE: /* 하드 드롭 + 줄 제거 + 점수 */ break;
        }
    } else { // CHARACTER_MOVER
        if (pressed) { /* setMovingLeft/Right, requestJump */ }
        else          { /* setMovingLeft/Right false */ }
    }
}
```

기존 `handleRemoteInput`에 흩어져 있던 블록/캐릭터 처리 로직이 `applyInput` 하나로 통합되었다.

---

## 5. 검증

### 5.1 컴파일 검증

```
javac -d build -cp . *.java
→ 에러/경고 없음

jar cfm CoopTetris.jar manifest.txt -C build .
→ 성공
```

### 5.2 실행 검증

`java -jar CoopTetris.jar` 실행 후 확인 항목:

| 확인 항목 | 결과 |
|---|---|
| 타이틀 화면 정상 표시 | ✓ |
| 게임 만들기 → 역할 선택 화면 전환 | ✓ |
| 게임 찾기 → IP 입력 → 역할 선택 화면 전환 | ✓ |
| 같은 PC 127.0.0.1로 2창 연결 후 게임 시작 | ✓ |
| 블록 이동/회전/소프트드롭/하드드롭 정상 작동 | ✓ |
| 캐릭터 좌우이동/점프 정상 작동 | ✓ |
| 깃발 수집 → 점수 +1000, 타이머 리셋 | ✓ |
| 게임 오버 다이얼로그 → 재시작/타이틀 전환 | ✓ |

### 5.3 구조적 안전성 확인

수정 후 공유 상태 접근 패턴:

```
currentPiece   → 쓰기: GameLoop만 (applyInput, dropStep)
makeNew        → 쓰기: GameLoop만
data           → 쓰기: GameLoop만 (removeLines는 synchronized 유지)
score          → 쓰기: GameLoop만 (applyInput, dropStep, FLAG_COLLECTED는
                                   FlagManager.update()를 통해 GameLoop에서 호출)
gameRunning    → 쓰기: GameLoop + GameTimer (volatile로 가시성 보장)
remainingSeconds → 쓰기: GameTimer (volatile), 읽기: EDT (렌더링)
panelState     → 쓰기: GameLoop + GameTimer (volatile), 읽기: EDT
```

`currentPiece`, `makeNew`, `data`, `score`를 수정하는 경로가 GameLoop 스레드 하나로 좁혀졌다.

---

## 6. 한계 및 잔여 사항

- **입력 지연**: 키 입력이 큐를 거쳐 처리되므로 이론상 최대 16ms(1틱) 지연이 추가된다.
  게임 플레이에서는 체감하기 어려운 수준이다.
- **GameTimer → triggerGameOver 경로**: `GameTimer` 스레드가 `triggerGameOver`를
  직접 호출하는 경로가 남아 있으나, `volatile gameRunning`과 `SwingUtilities.invokeLater`
  조합으로 실질적인 안전성은 확보되어 있다.
