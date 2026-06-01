package test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** 게임 시작 화면 - 게임 만들기 / 게임 찾기 선택 */
public class TitlePanel extends JPanel {

    public interface TitleListener {
        void onCreateGame();
        void onFindGame();
    }

    private final TitleListener listener;

    public TitlePanel(TitleListener listener) {
        this.listener = listener;
        setBackground(new Color(15, 15, 25));
        setLayout(new GridBagLayout());
        buildUI();
    }

    private void buildUI() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 20, 10, 20);
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.gridx  = 0;

        // 제목
        JLabel title = new JLabel("협동 테트리스", SwingConstants.CENTER);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 36));
        title.setForeground(new Color(0, 220, 255));
        gbc.gridy = 0; gbc.insets = new Insets(30, 20, 20, 20);
        add(title, gbc);

        JLabel sub = new JLabel("Cooperative Tetris", SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.ITALIC, 14));
        sub.setForeground(new Color(100, 150, 200));
        gbc.gridy = 1; gbc.insets = new Insets(0, 20, 30, 20);
        add(sub, gbc);

        // 설명
        JLabel desc = new JLabel("<html><center>한 명은 블록을 조작하고,<br>한 명은 캐릭터로 깃발을 수집해 시간을 늘리세요!</center></html>",
                SwingConstants.CENTER);
        desc.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        desc.setForeground(new Color(180, 180, 200));
        gbc.gridy = 2; gbc.insets = new Insets(0, 20, 30, 20);
        add(desc, gbc);

        // 게임 만들기 버튼
        JButton createBtn = makeButton("게임 만들기  (서버 시작)", new Color(0, 160, 90));
        createBtn.addActionListener(e -> listener.onCreateGame());
        gbc.gridy = 3; gbc.insets = new Insets(8, 40, 8, 40);
        add(createBtn, gbc);

        // 게임 찾기 버튼
        JButton findBtn = makeButton("게임 찾기  (서버에 접속)", new Color(0, 100, 200));
        findBtn.addActionListener(e -> listener.onFindGame());
        gbc.gridy = 4;
        add(findBtn, gbc);

        // 종료
        JButton exitBtn = makeButton("종료", new Color(140, 30, 30));
        exitBtn.addActionListener(e -> System.exit(0));
        gbc.gridy = 5; gbc.insets = new Insets(20, 80, 30, 80);
        add(exitBtn, gbc);
    }

    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(280, 48));
        return btn;
    }
}
