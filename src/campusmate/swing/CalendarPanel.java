package campusmate.swing;

import campusmate.model.Deadline;
import campusmate.model.TodoItem;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * '달력' 탭. 날짜마다 할 일 달성 상태를 색으로 보여 준다.
 * 연두 = 모두 완료, 연빨강 = 지난 날인데 못 한 일 있음, 파란 테두리 = 오늘.
 * 날짜 클릭: 오른쪽에 그날 내용 / 더블클릭: '하루 계획' 탭으로 이동
 */
public class CalendarPanel extends JPanel {
    private static final String[] WEEK_DAYS = {"일", "월", "화", "수", "목", "금", "토"};
    private static final Color SELECTED_BORDER = new Color(142, 68, 173);

    private final MainFrame frame;
    private final PlannerService service;

    private YearMonth month = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();

    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel grid = new JPanel(new GridLayout(6, 7, 3, 3)); // 6주 × 7요일
    // Map: 날짜 → 그 날짜의 칸. 날짜를 클릭했을 때 해당 칸의 테두리만 바로 바꾸기 위해 사용
    private final Map<LocalDate, JPanel> cells = new HashMap<>();
    private final JLabel dayTitle = new JLabel();
    private final DefaultListModel<String> dayListModel = new DefaultListModel<>();

    public CalendarPanel(MainFrame frame) {
        this.frame = frame;
        this.service = frame.getService();
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel weekHeader = new JPanel(new GridLayout(1, 7, 3, 3));
        for (int i = 0; i < WEEK_DAYS.length; i++) {
            JLabel label = new JLabel(WEEK_DAYS[i], SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            if (i == 0) label.setForeground(Color.RED);
            if (i == 6) label.setForeground(Color.BLUE);
            weekHeader.add(label);
        }
        JPanel calendar = new JPanel(new BorderLayout(0, 4));
        calendar.add(weekHeader, BorderLayout.NORTH);
        calendar.add(grid, BorderLayout.CENTER);

        add(createNavigation(), BorderLayout.NORTH);
        add(calendar, BorderLayout.CENTER);
        add(createDayPanel(), BorderLayout.EAST);
    }

    private JPanel createNavigation() {
        JButton prev = new JButton("◀ 이전 달");
        JButton next = new JButton("다음 달 ▶");
        JButton today = new JButton("이번 달");
        prev.addActionListener(e -> { month = month.minusMonths(1); refresh(); });
        next.addActionListener(e -> { month = month.plusMonths(1); refresh(); });
        today.addActionListener(e -> {
            month = YearMonth.now();
            selectedDate = LocalDate.now();
            refresh();
        });
        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 18f));
        monthLabel.setPreferredSize(new Dimension(160, 30));

        JLabel legend = new JLabel("<html>&nbsp;&nbsp;<font color='#8bc34a'>■</font> 모두 완료&nbsp;&nbsp;"
                + "<font color='#ef9a9a'>■</font> 못 한 일 있음&nbsp;&nbsp;<font color='#3498db'>□</font> 오늘"
                + "&nbsp;&nbsp;<font color='#8e44ad'>▶</font> 마감</html>");

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        nav.add(prev);
        nav.add(monthLabel);
        nav.add(next);
        nav.add(today);
        nav.add(legend);
        return nav;
    }

    private JPanel createDayPanel() {
        dayTitle.setFont(dayTitle.getFont().deriveFont(Font.BOLD, 14f));
        JList<String> dayList = new JList<>(dayListModel);
        JButton openButton = new JButton("이 날 계획 열기 / 할 일 추가");
        openButton.addActionListener(e -> frame.showDay(selectedDate));

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setPreferredSize(new Dimension(280, 0));
        panel.add(dayTitle, BorderLayout.NORTH);
        panel.add(new JScrollPane(dayList), BorderLayout.CENTER);
        panel.add(openButton, BorderLayout.SOUTH);
        return panel;
    }

    public void refresh() {
        monthLabel.setText(month.getYear() + "년 " + month.getMonthValue() + "월");
        grid.removeAll();
        cells.clear();

        // 1일이 무슨 요일인지에 따라 앞쪽을 빈칸으로 채운다. (일요일=0 기준)
        int blanks = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < blanks; i++) grid.add(new JLabel());
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            JPanel cell = createDayCell(date);
            cells.put(date, cell);
            grid.add(cell);
        }
        // 항상 6주(42칸)를 채워 달마다 칸 크기가 바뀌지 않게 한다.
        for (int i = blanks + month.lengthOfMonth(); i < 42; i++) grid.add(new JLabel());

        grid.revalidate();
        grid.repaint();
        refreshDayList();
    }

    /** 날짜 한 칸: 날짜 숫자 + "할 일 2/3" + 그날 마감 일정 제목. */
    private JPanel createDayCell(LocalDate date) {
        LocalDate today = LocalDate.now();
        List<TodoItem> todos = service.getTodos(date);
        int done = 0;
        for (TodoItem todo : todos) if (todo.isDone()) done++;

        JPanel cell = new JPanel(new BorderLayout());
        if (!todos.isEmpty() && done == todos.size()) cell.setBackground(SwingUtil.DONE_COLOR);
        else if (!todos.isEmpty() && date.isBefore(today)) cell.setBackground(SwingUtil.MISSED_COLOR);
        else cell.setBackground(Color.WHITE);
        cell.setBorder(cellBorder(date));

        JLabel dayLabel = new JLabel(" " + date.getDayOfMonth());
        int dow = date.getDayOfWeek().getValue();
        if (dow == 7) dayLabel.setForeground(Color.RED);
        if (dow == 6) dayLabel.setForeground(Color.BLUE);
        cell.add(dayLabel, BorderLayout.NORTH);

        // JLabel은 HTML을 지원하므로 여러 줄/색상 표시에 활용한다.
        StringBuilder html = new StringBuilder("<html>");
        if (!todos.isEmpty()) html.append("할 일 ").append(done).append("/").append(todos.size()).append("<br>");
        for (Deadline d : service.getDeadlines()) {
            if (!d.getDueDate().equals(date)) continue;
            String color = d.isCompleted() ? "#999999" : "#8e44ad";
            html.append("<font color='").append(color).append("'>▶ ")
                    .append(SwingUtil.escape(d.getTitle())).append("</font><br>");
        }
        html.append("</html>");
        JLabel info = new JLabel(html.toString());
        info.setFont(info.getFont().deriveFont(11f));
        info.setVerticalAlignment(SwingConstants.TOP);
        cell.add(info, BorderLayout.CENTER);

        // 클릭: 날짜 선택 / 더블클릭: 그 날짜의 '하루 계획' 탭으로
        cell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectDate(date);
                if (e.getClickCount() == 2) frame.showDay(date);
            }
        });
        return cell;
    }

    private javax.swing.border.Border cellBorder(LocalDate date) {
        if (date.equals(selectedDate)) return BorderFactory.createLineBorder(SELECTED_BORDER, 3);
        if (date.equals(LocalDate.now())) return BorderFactory.createLineBorder(SwingUtil.TODAY_COLOR, 2);
        return BorderFactory.createLineBorder(SwingUtil.BORDER_COLOR);
    }

    /** 선택 표시를 옮기고 오른쪽 목록을 바꾼다. (달력 전체를 다시 만들지 않음) */
    private void selectDate(LocalDate date) {
        LocalDate old = selectedDate;
        selectedDate = date;
        if (cells.containsKey(old)) cells.get(old).setBorder(cellBorder(old));
        if (cells.containsKey(date)) cells.get(date).setBorder(cellBorder(date));
        refreshDayList();
    }

    private void refreshDayList() {
        LocalDate today = LocalDate.now();
        dayTitle.setText(SwingUtil.LONG_DATE.format(selectedDate));
        dayListModel.clear();
        for (TodoItem todo : service.getTodos(selectedDate)) {
            dayListModel.addElement(todo.getDisplayStatus(today).getMark() + " " + todo.getContent());
        }
        for (Deadline d : service.getDeadlines()) {
            if (d.getDueDate().equals(selectedDate)) {
                dayListModel.addElement("▶ 마감: " + service.getCourseName(d.getCourseId()) + " · " + d.getTitle()
                        + (d.isCompleted() ? " (완료)" : ""));
            }
        }
        if (dayListModel.isEmpty()) dayListModel.addElement("계획 없음 — 아래 버튼으로 추가해 보세요");
    }
}
