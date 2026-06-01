package test;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;

/**
 * 협동 테트리스 게임 실행 진입점 (Main Class)
 * * [개선 사항]
 * 1. 운영체제 맞춤형 UI 스타일(Look and Feel) 자동 적용
 * 2. 예기치 못한 크래시 방지를 위한 전역 예외 처리(Uncaught Exception Handler) 추가
 * 3. 윈도우 중앙 정렬 보정 적용
 */
public class CoopMain {

    public static void main(String[] args) {
        // 1. 운영체제(OS) 스타일의 Look & Feel 적용 (다이얼로그 및 버튼 디자인 개선)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("시스템 UI 스타일 스타일 로드 실패: 기본 Swing 테마로 실행합니다.");
        }

        // 2. 프로그램 전체 스레드 예외 안전장치 배치 (디버깅 및 강제종료 방지)
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("⚠️ [" + thread.getName() + "] 스레드에서 예외 발생:");
            throwable.printStackTrace();
        });

        // 3. Swing GUI 스레드(EDT)에서 안전하게 프레임 구동
        SwingUtilities.invokeLater(() -> {
            CoopTetrisFrame frame = new CoopTetrisFrame();
            
            // 프레임 내부에서 설정하지 않았을 경우를 대비해 화면 정중앙에 배치하도록 보정
            frame.setLocationRelativeTo(null);
            
            // 창 띄우기
            frame.setVisible(true);
        });
    }
}