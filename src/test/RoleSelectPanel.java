package test;

import java.awt.*;
import javax.swing.*;

/** 역할 선택 화면 - 블록 이동 / 졸라맨 이동 선택 */
public class RoleSelectPanel extends JPanel {

    public interface RoleSelectListener {
        void onRoleSelected(RoleType role);
    }

    private final RoleSelectListener listener;
    private JButton blockBtn;
    private JButton charBtn;
    private JLabel  ipLabel;

    public RoleSelectPanel(RoleSelectListener listener) {
        this.listener = listener;
        setBackground(new Color(15, 15, 25));
        setLayout(new GridBagLayout());
        buildUI();
    }

    /** 서버 IP를 화면에 표시 (게임 만들기 측에서만 호출) */
    public void showServerIP(String ip) {
        ipLabel.setText("내 IP: " + ip + "  (포트: " + NetworkManager.PORT + ")");
        ipLabel.setVisible(true);
        revalidate();
        repaint();
    }

    /** IP 레이블 숨기기 (역할 화면 재사용 시) */
    public void hideServerIP() {
        ipLabel.setVisible(false);
    }

    private void buildUI() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 30, 12, 30);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.gridx  = 0;

        JLabel title = new JLabel("역할을 선택하세요", SwingConstants.CENTER);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        title.setForeground(new Color(255, 220, 0));
        gbc.gridy = 0; gbc.insets = new Insets(30, 30, 10, 30);
        add(title, gbc);

        // 서버 IP 레이블 (기본 숨김, showServerIP() 호출 시 표시)
        ipLabel = new JLabel("", SwingConstants.CENTER);
        ipLabel.setFont(new Font("Consolas", Font.BOLD, 13));
        ipLabel.setForeground(new Color(0, 220, 180));
        ipLabel.setVisible(false);
        gbc.gridy = 1; gbc.insets = new Insets(0, 30, 15, 30);
        add(ipLabel, gbc);

        // 블록 이동 버튼
        blockBtn = makeRoleButton(
                "블록 이동  (← → ↑ ↓)",
                "<html><center>테트로미노를 조종해<br>줄을 지우세요</center></html>",
                new Color(0, 140, 200));
        blockBtn.addActionListener(e -> listener.onRoleSelected(RoleType.BLOCK_MOVER));
        gbc.gridy = 2; gbc.insets = new Insets(8, 40, 8, 40);
        add(blockBtn, gbc);

        // 졸라맨 이동 버튼
        charBtn = makeRoleButton(
                "졸라맨 이동  (← → Space)",
                "<html><center>캐릭터를 조종해<br>깃발을 수집하세요</center></html>",
                new Color(0, 160, 80));
        charBtn.addActionListener(e -> listener.onRoleSelected(RoleType.CHARACTER_MOVER));
        gbc.gridy = 3;
        add(charBtn, gbc);

        JLabel hint = new JLabel("* 상대방이 선택한 역할은 자동으로 배정됩니다", SwingConstants.CENTER);
        hint.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        hint.setForeground(new Color(130, 130, 160));
        gbc.gridy = 4; gbc.insets = new Insets(20, 30, 10, 30);
        add(hint, gbc);
    }

    /** 역할 버튼 초기 상태로 복원 (재시작 시 호출) */
    public void resetButtons() {
        blockBtn.setEnabled(true);
        blockBtn.setText("블록 이동  (← → ↑ ↓)");
        charBtn.setEnabled(true);
        charBtn.setText("졸라맨 이동  (← → Space)");
        hideServerIP();
    }

    /** 특정 역할 버튼 비활성화 (상대방이 이미 선택한 경우) */
    public void disableRole(RoleType taken) {
        if (taken == RoleType.BLOCK_MOVER) {
            blockBtn.setEnabled(false);
            blockBtn.setText("블록 이동  (상대방 선택)");
        } else {
            charBtn.setEnabled(false);
            charBtn.setText("졸라맨 이동  (상대방 선택)");
        }
    }

    private JButton makeRoleButton(String title, String desc, Color bg) {
        JButton btn = new JButton("<html><b>" + title + "</b><br><font size='2'>" + desc + "</font></html>");
        btn.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(300, 80));
        return btn;
    }
}
