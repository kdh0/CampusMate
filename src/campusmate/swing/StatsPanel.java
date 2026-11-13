package campusmate.swing;

import campusmate.model.Course;
import campusmate.model.Deadline;
import campusmate.model.TodoItem;
import campusmate.model.TodoStatus;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/** '기록·통계' 탭: 요약 카드 + 진행률(JProgressBar) + 최근 14일 달성률 차트. */
public class StatsPanel extends JPanel {
    private static final int CHART_DAYS = 14;

    private final PlannerService service;

    private final JLabel totalRateValue = new JLabel();
    private final JLabel weekRateValue = new JLabel();
    private final JLabel streakValue = new JLabel();
    private final JLabel missedValue = new JLabel();
    private final JPanel progressList = new JPanel(new GridLayout(0, 1, 0, 6));
    private final BarChartPanel chart = new BarChartPanel("최근 " + CHART_DAYS + "일 달성률 (%)");

    public StatsPanel(MainFrame frame) {
        this.service = frame.getService();
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 0));
        cards.add(createCard("지금까지 달성률", totalRateValue, new Color(52, 73, 94)));
        cards.add(createCard("이번 주 달성률", weekRateValue, new Color(41, 128, 185)));
        cards.add(createCard("연속 달성", streakValue, new Color(39, 174, 96)));
        cards.add(createCard("못 한 일 (미룸 포함)", missedValue, new Color(192, 57, 43)));

        JPanel progressWrapper = new JPanel(new BorderLayout());
        progressWrapper.add(progressList, BorderLayout.NORTH); // 위쪽부터 차곡차곡
        JScrollPane progressScroll = new JScrollPane(progressWrapper);
        progressScroll.setBorder(BorderFactory.createTitledBorder("진행률"));

        JPanel center = new JPanel(new GridLayout(1, 2, 10, 0));
        center.add(progressScroll);
        chart.setBorder(BorderFactory.createLineBorder(SwingUtil.BORDER_COLOR));
        center.add(chart);

        add(cards, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private JPanel createCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(color);
        card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 26f));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    public void refresh() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        totalRateValue.setText(rate(null, today) + "%");
        weekRateValue.setText(rate(monday, today) + "%");
        streakValue.setText(service.getStreak(today) + "일");
        missedValue.setText(service.searchTodos("", null, null, null, TodoStatus.MISSED, today).size() + "개");

        progressList.removeAll();
        progressList.add(sectionLabel("마감 일정별 (연결된 할 일 완료율)"));
        List<Deadline> open = service.getOpenDeadlines();
        if (open.isEmpty()) progressList.add(new JLabel("  진행 중인 마감 일정이 없습니다."));
        for (Deadline d : open) {
            progressList.add(progressRow("[" + d.getDday(today) + "] " + d.getTitle(),
                    service.countLinkedDone(d.getId()), service.countLinkedTodos(d.getId())));
        }
        progressList.add(sectionLabel("과목별 (할 일 완료율)"));
        if (service.getCourses().isEmpty()) progressList.add(new JLabel("  등록된 과목이 없습니다."));
        for (Course course : service.getCourses()) {
            int total = service.searchTodos("", course.getId(), null, null, null, today).size();
            int done = service.searchTodos("", course.getId(), null, null, TodoStatus.DONE, today).size();
            progressList.add(progressRow(course.getName(), done, total));
        }
        progressList.revalidate();
        progressList.repaint();

        refreshChart(today);
    }

    /** 기간 안의 달성률(%). */
    private int rate(LocalDate from, LocalDate to) {
        return SwingUtil.percent(service.countDone(from, to), service.countTodos(from, to));
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    /** 한 줄: [이름] [=====진행률 바=====] */
    private JPanel progressRow(String name, int done, int total) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(SwingUtil.percent(done, total));
        bar.setStringPainted(true);
        bar.setString(total == 0 ? "연결된 할 일 없음" : SwingUtil.percent(done, total) + "%  (" + done + "/" + total + ")");

        JLabel label = new JLabel(name);
        label.setPreferredSize(new Dimension(170, 22));
        label.setToolTipText(name);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(label, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    /** 오늘 포함 최근 14일. 계획이 없던 날은 막대를 그리지 않는다(-1). */
    private void refreshChart(LocalDate today) {
        String[] labels = new String[CHART_DAYS];
        int[] values = new int[CHART_DAYS];
        for (int i = 0; i < CHART_DAYS; i++) {
            LocalDate date = today.minusDays(CHART_DAYS - 1 - i);
            labels[i] = date.getDayOfMonth() + "\n" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
            List<TodoItem> todos = service.getTodos(date);
            int done = 0;
            for (TodoItem todo : todos) if (todo.isDone()) done++;
            values[i] = todos.isEmpty() ? -1 : SwingUtil.percent(done, todos.size());
        }
        chart.setData(labels, values, 100, "%", CHART_DAYS - 1); // 마지막 막대 = 오늘
    }
}
