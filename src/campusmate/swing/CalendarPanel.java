package campusmate.swing;

import campusmate.model.Task;
import campusmate.service.TaskManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * '달력' 탭: 한 달 달력 + 고른 날짜의 할 일 목록(JList).
 * 칸 색: 연두 = 모두 완료, 연빨강 = 지난 날인데 못 한 일 있음 / 파란 테두리 = 오늘 / ★ = 과제·시험 마감
 */
public class CalendarPanel extends JPanel {
    private static final Color DONE_COLOR = new Color(210, 240, 200);
    private static final Color MISSED_COLOR = new Color(255, 215, 215);

    private final MainFrame frame;
    private final TaskManager manager;
    private YearMonth month = YearMonth.now();
    private LocalDate selected = LocalDate.now();

    private final JLabel monthLabel = new JLabel();
    private final JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2)); // 7칸(요일)씩 줄바꿈
    private final JLabel dayLabel = new JLabel();
    private final DefaultListModel<String> dayList = new DefaultListModel<>();

    public CalendarPanel(MainFrame frame) {
        this.frame = frame;
        this.manager = frame.getManager();
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        prev.addActionListener(e -> { month = month.minusMonths(1); refresh(); });
        next.addActionListener(e -> { month = month.plusMonths(1); refresh(); });
        JPanel top = new JPanel(); // 기본 FlowLayout (가운데 정렬)
        top.add(prev);
        top.add(monthLabel);
        top.add(next);

        JButton addButton = new JButton("이 날짜에 추가");
        addButton.addActionListener(e -> frame.openTaskForm(null, selected));
        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.setPreferredSize(new Dimension(260, 0));
        side.add(dayLabel, BorderLayout.NORTH);
        side.add(new JScrollPane(new JList<>(dayList)), BorderLayout.CENTER);
        side.add(addButton, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
    }

    public void refresh() {
        monthLabel.setText(month.getYear() + "년 " + month.getMonthValue() + "월");
        grid.removeAll();
        for (String day : new String[] {"일", "월", "화", "수", "목", "금", "토"}) {
            grid.add(new JLabel(day, SwingConstants.CENTER));
        }
        int blanks = month.atDay(1).getDayOfWeek().getValue() % 7; // 1일 앞의 빈칸 수 (일요일 시작)
        for (int i = 0; i < blanks; i++) grid.add(new JLabel());
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            grid.add(createCell(month.atDay(day)));
        }
        grid.revalidate();
        grid.repaint();
        showDay(selected);
    }

    /** 날짜 한 칸. 버튼이라 클릭하면 오른쪽에 그날 목록이 나온다. */
    private JButton createCell(LocalDate date) {
        LocalDate today = LocalDate.now();
        List<Task> tasks = manager.getByDate(date);
        int done = TaskManager.countDone(tasks);

        // JButton 글자에 HTML을 쓰면 여러 줄로 보여 줄 수 있다.
        String text = "<html><b>" + date.getDayOfMonth() + "</b>";
        if (!tasks.isEmpty()) text += "<br>" + done + "/" + tasks.size();
        for (Task task : tasks) {
            if (task.getCategory().isDeadline()) {
                text += "<br>★" + task.getCategory();
                break;
            }
        }
        JButton cell = new JButton(text + "</html>");
        cell.setVerticalAlignment(SwingConstants.TOP);

        if (!tasks.isEmpty() && done == tasks.size()) cell.setBackground(DONE_COLOR);
        else if (!tasks.isEmpty() && date.isBefore(today)) cell.setBackground(MISSED_COLOR);
        else cell.setBackground(Color.WHITE);
        if (date.equals(today)) cell.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));

        cell.addActionListener(e -> showDay(date));
        return cell;
    }

    private void showDay(LocalDate date) {
        selected = date;
        dayLabel.setText(date + " 할 일");
        dayList.clear();
        for (Task task : manager.getByDate(date)) {
            dayList.addElement((task.isDone() ? "[V] " : "[ ] ") + "[" + task.getCategory() + "] " + task.getTitle());
        }
        if (dayList.isEmpty()) dayList.addElement("계획 없음");
    }
}
