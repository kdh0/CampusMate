package campusmate.swing;

import campusmate.model.Task;
import campusmate.service.TaskManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** '통계' 탭: 왼쪽 과목별 완료율(JProgressBar) + 오른쪽 최근 7일 완료율 막대 차트. */
public class StatsPanel extends JPanel {
    private static final int DAYS = 7;

    private final TaskManager manager;
    private final JPanel bars = new JPanel(new GridLayout(0, 1, 0, 6));

    /** 차트: paintComponent를 고쳐 Graphics로 직접 그린다. repaint()를 부르면 다시 그려진다. */
    private final JPanel chart = new JPanel() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            drawChart(g, getWidth(), getHeight());
        }
    };

    public StatsPanel(MainFrame frame) {
        this.manager = frame.getManager();
        setLayout(new GridLayout(1, 2, 10, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("과목별 완료율"));
        left.add(bars, BorderLayout.NORTH); // 위에서부터 차곡차곡
        chart.setBackground(Color.WHITE);
        chart.setBorder(BorderFactory.createTitledBorder("최근 " + DAYS + "일 완료율"));

        add(left);
        add(chart);
    }

    public void refresh() {
        bars.removeAll();
        bars.add(progressRow("전체", manager.getAll()));
        for (String course : manager.getCourses()) {
            bars.add(progressRow(course, manager.getByCourse(course)));
        }
        bars.revalidate();
        bars.repaint();
        chart.repaint();
    }

    /** 한 줄: [과목 이름] [====진행률 바====] */
    private JPanel progressRow(String name, List<Task> tasks) {
        int done = TaskManager.countDone(tasks);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(TaskManager.percent(done, tasks.size()));
        bar.setStringPainted(true);
        bar.setString(done + "/" + tasks.size() + " (" + bar.getValue() + "%)");

        JLabel label = new JLabel(name);
        label.setPreferredSize(new Dimension(100, 22));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(label, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    /** 오늘 포함 최근 7일의 완료율 막대. 할 일이 없던 날은 막대를 그리지 않는다. */
    private void drawChart(Graphics g, int width, int height) {
        LocalDate today = LocalDate.now();
        int baseY = height - 35;                // 막대 바닥선 위치
        int maxHeight = baseY - 60;             // 100%일 때 막대 높이
        int slot = (width - 40) / DAYS;         // 막대 하나가 차지하는 폭
        int barWidth = slot / 2;

        g.setColor(Color.GRAY);
        g.drawLine(20, baseY, width - 20, baseY);
        for (int i = 0; i < DAYS; i++) {
            LocalDate date = today.minusDays(DAYS - 1 - i);
            List<Task> tasks = manager.getByDate(date);
            int x = 20 + i * slot + slot / 4;

            if (!tasks.isEmpty()) {
                int rate = TaskManager.percent(TaskManager.countDone(tasks), tasks.size());
                int barHeight = maxHeight * rate / 100;
                g.setColor(date.equals(today) ? new Color(231, 76, 60) : new Color(52, 152, 219)); // 오늘은 빨강
                g.fillRect(x, baseY - barHeight, barWidth, barHeight);
                g.setColor(Color.BLACK);
                g.drawString(rate + "%", x, baseY - barHeight - 5);
            }
            g.setColor(Color.BLACK);
            g.drawString(date.getMonthValue() + "/" + date.getDayOfMonth(), x, baseY + 18);
        }
    }
}
