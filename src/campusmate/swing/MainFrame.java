package campusmate.swing;

import campusmate.model.Category;
import campusmate.model.Task;
import campusmate.service.TaskManager;
import campusmate.storage.FileStore;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * 2단계 Swing 버전의 메인 창. 위쪽에 오늘 진행률, 가운데에 탭 3개(할 일 목록 / 달력 / 통계).
 * 각 탭은 데이터를 바꾼 뒤 frame.dataChanged()만 부르면 저장과 화면 새로 고침이 된다.
 */
public class MainFrame extends JFrame {
    private final TaskManager manager;
    private final FileStore store;

    private final JProgressBar todayBar = new JProgressBar(0, 100);
    private final TaskPanel taskPanel;
    private final CalendarPanel calendarPanel;
    private final StatsPanel statsPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FileStore store = new FileStore(FileStore.DEFAULT_PATH);
            try {
                MainFrame frame = new MainFrame(new TaskManager(store.load(), store.loadCourses()), store);
                frame.setVisible(true);
                frame.startupCheck();
            } catch (IOException e) {
                // 파일을 덮어쓰지 않도록 창을 열지 않고 끝낸다.
                JOptionPane.showMessageDialog(null, "데이터를 불러오지 못했습니다.\n" + e.getMessage());
            }
        });
    }

    public MainFrame(TaskManager manager, FileStore store) {
        super("CampusMate - 대학생 과제·할 일 플래너");
        this.manager = manager;
        this.store = store;

        taskPanel = new TaskPanel(this);
        calendarPanel = new CalendarPanel(this);
        statsPanel = new StatsPanel(this);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("할 일 목록", taskPanel);
        tabs.addTab("달력", calendarPanel);
        tabs.addTab("통계", statsPanel);

        todayBar.setStringPainted(true);
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        header.add(new JLabel("오늘 " + LocalDate.now() + "  진행률"), BorderLayout.WEST);
        header.add(todayBar, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        // 창을 닫을 때 자동 저장 (WindowListener)
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { save(); }
        });

        setSize(1000, 650);
        setLocationRelativeTo(null); // 화면 가운데
        refreshAll();
    }

    public TaskManager getManager() { return manager; }

    /** 데이터를 바꾼 뒤 호출: 파일에 저장하고 모든 탭을 새로 그린다. */
    public void dataChanged() {
        save();
        refreshAll();
    }

    private void refreshAll() {
        taskPanel.refresh();
        calendarPanel.refresh();
        statsPanel.refresh();

        List<Task> today = manager.getByDate(LocalDate.now());
        int done = TaskManager.countDone(today);
        todayBar.setValue(TaskManager.percent(done, today.size()));
        todayBar.setString("오늘 할 일 " + done + "/" + today.size() + " 완료 (" + todayBar.getValue() + "%)");
    }

    private void save() {
        try {
            store.save(manager.getAll(), manager.getCourses());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "저장에 실패했습니다.\n" + e.getMessage());
        }
    }

    /** 시작할 때: 지난 일 오늘로 옮기기 → 남은 마감 일정 알림 */
    private void startupCheck() {
        LocalDate today = LocalDate.now();

        List<Task> overdue = manager.getOverdue(today);
        if (!overdue.isEmpty()) {
            StringBuilder msg = new StringBuilder("지난 날 못 한 일이 " + overdue.size() + "개 있습니다.\n\n");
            for (Task task : overdue) msg.append("· ").append(task.getDate()).append("  ").append(task.getTitle()).append("\n");
            msg.append("\n모두 오늘로 옮길까요?");
            int answer = JOptionPane.showConfirmDialog(this, msg.toString(), "지난 할 일", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                for (Task task : overdue) manager.moveToToday(task.getId(), today);
                dataChanged();
            }
        }

        List<Task> soon = manager.getUpcomingDeadlines(today);
        if (!soon.isEmpty()) {
            StringBuilder msg = new StringBuilder("남은 마감 일정입니다.\n\n");
            for (Task task : soon) {
                msg.append("[").append(task.getDday(today)).append("] ").append(task.getCategory()).append(" · ")
                        .append(task.getCourse()).append(" ").append(task.getTitle()).append("\n");
            }
            JOptionPane.showMessageDialog(this, msg.toString(), "마감 일정", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * 추가/수정 입력 창 (할 일 목록 탭, 달력 탭에서 함께 사용).
     * @param task 수정할 항목. null이면 새로 추가한다.
     * @param date 추가할 때 기본 날짜
     */
    public void openTaskForm(Task task, LocalDate date) {
        JTextField titleField = new JTextField(task == null ? "" : task.getTitle(), 20);
        // 과목: 목록(Set)에서 고르거나 새 이름을 직접 입력할 수 있다.
        JComboBox<String> courseBox = new JComboBox<>(manager.getCourses().toArray(new String[0]));
        courseBox.setEditable(true);
        courseBox.setSelectedItem(task == null ? "" : task.getCourse());
        JComboBox<Category> categoryBox = new JComboBox<>(Category.values());
        if (task != null) categoryBox.setSelectedItem(task.getCategory());
        JTextField dateField = new JTextField(TaskManager.formatDate(task == null ? date : task.getDate()));

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("내용"));
        form.add(titleField);
        form.add(new JLabel("과목"));
        form.add(courseBox);
        form.add(new JLabel("종류"));
        form.add(categoryBox);
        form.add(new JLabel("날짜/마감일 (예: 11/20)"));
        form.add(dateField);

        // 잘못 입력하면 안내 후 같은 창을 다시 띄운다. (입력한 내용은 그대로 남아 있음)
        while (true) {
            int answer = JOptionPane.showConfirmDialog(this, form, task == null ? "추가" : "수정",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            try {
                LocalDate newDate = TaskManager.parseDate(dateField.getText(), LocalDate.now());
                if (newDate == null) throw new IllegalArgumentException("날짜는 11/20처럼 월/일로 입력해 주세요.");
                String course = courseBox.getEditor().getItem().toString();
                Category category = (Category) categoryBox.getSelectedItem();
                // Swing은 아직 날짜 칸이 하나뿐이라 마감일은 따로 넣지 않는다. (과제는 할 날 = 마감일)
                if (task == null) manager.add(titleField.getText(), course, category, newDate, null);
                else manager.update(task.getId(), titleField.getText(), course, category, newDate, null);
                dataChanged();
                return;
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "입력 확인", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
}
