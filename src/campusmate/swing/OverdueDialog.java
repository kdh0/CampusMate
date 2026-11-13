package campusmate.swing;

import campusmate.model.TodoItem;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * 시작할 때 뜨는 '못 한 일 처리' 창.
 * 체크한 항목은 오늘 할 일로 옮기고(원래 날짜에는 '미룸'으로 남음), 체크하지 않은 항목은 '못함'으로 기록한다.
 */
public class OverdueDialog extends JDialog {
    private final PlannerService service;
    private final List<TodoItem> overdue;
    private final List<JCheckBox> checkBoxes = new ArrayList<>(); // overdue와 같은 순서
    private boolean confirmed = false;

    public OverdueDialog(JFrame owner, PlannerService service, List<TodoItem> overdue) {
        super(owner, "지난 계획 확인", true);
        this.service = service;
        this.overdue = overdue;

        JLabel message = new JLabel("<html><b>지난 계획 중 끝내지 못한 할 일이 " + overdue.size() + "개 있습니다.</b><br>"
                + "오늘로 옮길 항목을 체크하세요. 체크하지 않은 항목은 '못함'으로 기록됩니다.</html>");

        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setBackground(Color.WHITE);
        for (TodoItem todo : overdue) {
            String link = SwingUtil.linkText(service, todo);
            JCheckBox box = new JCheckBox(SwingUtil.SHORT_DATE.format(todo.getDate()) + "   " + todo.getContent()
                    + (link.isEmpty() ? "" : "   <" + link + ">"), true); // 기본값: 모두 옮기기
            box.setOpaque(false);
            checkBoxes.add(box);
            list.add(box);
        }
        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setBackground(Color.WHITE);
        listWrapper.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listWrapper);
        scroll.setPreferredSize(new Dimension(460, Math.min(240, 40 + overdue.size() * 26)));

        JButton allButton = new JButton("모두 선택");
        JButton noneButton = new JButton("모두 해제");
        JButton okButton = new JButton("확인");
        allButton.addActionListener(e -> setAll(true));
        noneButton.addActionListener(e -> setAll(false));
        okButton.addActionListener(e -> apply());
        getRootPane().setDefaultButton(okButton);

        JPanel buttons = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(allButton);
        left.add(noneButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(okButton);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        root.add(message, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    /** 확인을 눌러 처리했으면 true. (X로 닫으면 아무것도 바꾸지 않고, 다음 실행 때 다시 묻는다) */
    public boolean isConfirmed() { return confirmed; }

    private void setAll(boolean selected) {
        for (JCheckBox box : checkBoxes) box.setSelected(selected);
    }

    private void apply() {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < overdue.size(); i++) {
            TodoItem todo = overdue.get(i);
            if (checkBoxes.get(i).isSelected()) service.postponeToToday(todo.getId(), today);
            else service.markMissed(todo.getId());
        }
        confirmed = true;
        dispose();
    }
}
