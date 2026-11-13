package campusmate.swing;

import campusmate.model.Deadline;
import campusmate.model.TodoItem;
import campusmate.service.PlannerService;
import campusmate.storage.FileRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;

/**
 * 메인 창. 탭 6개를 담고, 데이터가 바뀌면 저장 후 모든 탭을 새로 그리는 '중앙 관리자' 역할을 한다.
 * 각 탭(패널)은 데이터를 바꾼 뒤 frame.dataChanged()만 부르면 된다.
 */
public class MainFrame extends JFrame {
    /** 시작할 때 이 일수 이내에 마감하는 일정이 있으면 알림 창을 띄운다. */
    private static final int ALERT_DAYS = 3;

    private final PlannerService service;
    private final FileRepository repository;

    private final JLabel summaryLabel = new JLabel();
    private final JProgressBar todayProgress = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();

    private final DayPanel dayPanel;
    private final CalendarPanel calendarPanel;
    private final StatsPanel statsPanel;
    private final SearchPanel searchPanel;
    private final DeadlinePanel deadlinePanel;
    private final CoursePanel coursePanel;

    public MainFrame(PlannerService service, FileRepository repository) {
        super("CampusMate - 대학생 하루 플래너");
        this.service = service;
        this.repository = repository;

        dayPanel = new DayPanel(this);
        calendarPanel = new CalendarPanel(this);
        statsPanel = new StatsPanel(this);
        searchPanel = new SearchPanel(this);
        deadlinePanel = new DeadlinePanel(this);
        coursePanel = new CoursePanel(this);

        tabs.addTab("하루 계획", dayPanel);
        tabs.addTab("달력", calendarPanel);
        tabs.addTab("기록·통계", statsPanel);
        tabs.addTab("검색", searchPanel);
        tabs.addTab("마감 일정", deadlinePanel);
        tabs.addTab("과목", coursePanel);

        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
        setJMenuBar(createMenuBar());

        // X 버튼을 누르면 바로 닫지 않고 저장부터 한다.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { exitProgram(); }
        });

        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null); // 화면 가운데
        statusLabel.setText("  저장 위치: " + repository.getPath());
        refreshAll();
    }

    public PlannerService getService() { return service; }

    // ======================= 화면 구성 =======================

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SwingUtil.HEADER_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        JLabel title = new JLabel("CampusMate");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        JLabel today = new JLabel("오늘 " + SwingUtil.LONG_DATE.format(LocalDate.now()));
        today.setForeground(new Color(200, 210, 220));

        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(title);
        left.add(today);

        summaryLabel.setForeground(Color.WHITE);
        todayProgress.setStringPainted(true);
        todayProgress.setPreferredSize(new Dimension(280, 22));
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 4));
        right.setOpaque(false);
        right.add(summaryLabel);
        right.add(todayProgress);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));
        statusLabel.setForeground(Color.DARK_GRAY);
        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    private JMenuBar createMenuBar() {
        JMenu fileMenu = new JMenu("파일");
        JMenuItem saveItem = new JMenuItem("저장");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)); // Ctrl+S
        saveItem.addActionListener(e -> save());
        JMenuItem exitItem = new JMenuItem("종료");
        exitItem.addActionListener(e -> exitProgram());
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu viewMenu = new JMenu("보기");
        JMenuItem todayItem = new JMenuItem("오늘 계획으로 이동");
        todayItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)); // Ctrl+T
        todayItem.addActionListener(e -> showDay(LocalDate.now()));
        JMenuItem overdueItem = new JMenuItem("못 한 일 처리하기");
        overdueItem.addActionListener(e -> handleOverdue(true));
        JMenuItem alertItem = new JMenuItem("마감 임박 알림 보기");
        alertItem.addActionListener(e -> showDeadlineAlert(true));
        viewMenu.add(todayItem);
        viewMenu.add(overdueItem);
        viewMenu.add(alertItem);

        JMenuBar bar = new JMenuBar();
        bar.add(fileMenu);
        bar.add(viewMenu);
        return bar;
    }

    // ======================= 다른 패널들이 호출하는 공통 기능 =======================

    /** 데이터를 바꾼 뒤 호출: 파일에 저장하고 모든 화면을 새로 고친다. */
    public void dataChanged() {
        save();
        refreshAll();
    }

    public void refreshAll() {
        dayPanel.refresh();
        calendarPanel.refresh();
        statsPanel.refresh();
        searchPanel.refresh();
        deadlinePanel.refresh();
        coursePanel.refresh();

        LocalDate today = LocalDate.now();
        List<TodoItem> todos = service.getTodos(today);
        int done = 0;
        for (TodoItem todo : todos) if (todo.isDone()) done++;
        todayProgress.setValue(SwingUtil.percent(done, todos.size()));
        todayProgress.setString(todos.isEmpty() ? "오늘 계획이 아직 없습니다"
                : "오늘 " + done + "/" + todos.size() + " 완료 (" + SwingUtil.percent(done, todos.size()) + "%)");
        summaryLabel.setText("연속 달성 " + service.getStreak(today) + "일 · 다가오는 마감 "
                + service.getUpcomingDeadlines(today).size() + "건");
    }

    /** '하루 계획' 탭으로 가서 해당 날짜를 보여 준다. (달력 등에서 사용) */
    public void showDay(LocalDate date) {
        dayPanel.showDate(date);
        tabs.setSelectedComponent(dayPanel);
    }

    public void showCourseTab() { tabs.setSelectedComponent(coursePanel); }

    public void openTodoDialog(TodoItem todo) {
        TodoDialog dialog = new TodoDialog(this, service, todo);
        dialog.setVisible(true); // 모달 창이므로 닫힐 때까지 여기서 기다린다.
        if (dialog.isSaved()) dataChanged();
    }

    public void deleteTodo(TodoItem todo) {
        int answer = JOptionPane.showConfirmDialog(this, "'" + todo.getContent() + "' 할 일을 삭제할까요?",
                "삭제 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            service.deleteTodo(todo.getId());
            dataChanged();
        }
    }

    public boolean save() {
        try {
            repository.save(service);
            statusLabel.setText("  저장 위치: " + repository.getPath() + "   |   마지막 저장 "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "저장에 실패했습니다.\n" + e.getMessage(),
                    "저장 오류", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ======================= 시작할 때 확인 =======================

    /** 프로그램을 켰을 때: 못 한 일 처리 → 마감 임박 알림 순서로 보여 준다. */
    public void runStartupChecks() {
        handleOverdue(false);
        showDeadlineAlert(false);
    }

    /** 지난 날짜에 끝내지 못한 할 일이 있으면, 오늘로 옮길 항목을 고르는 창을 띄운다. */
    private void handleOverdue(boolean fromMenu) {
        List<TodoItem> overdue = service.getOverdueTodos(LocalDate.now());
        if (overdue.isEmpty()) {
            if (fromMenu) JOptionPane.showMessageDialog(this, "처리할 지난 할 일이 없습니다.");
            return;
        }
        OverdueDialog dialog = new OverdueDialog(this, service, overdue);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) dataChanged();
    }

    /** 마감 임박(기한 지남 + ALERT_DAYS일 이내) 일정을 알림 창으로 보여 준다. */
    private void showDeadlineAlert(boolean fromMenu) {
        LocalDate today = LocalDate.now();
        List<Deadline> soon = new ArrayList<>();
        for (Deadline d : service.getUpcomingDeadlines(today)) {
            if (d.daysUntil(today) <= ALERT_DAYS) soon.add(d);
        }
        if (soon.isEmpty()) {
            if (fromMenu) JOptionPane.showMessageDialog(this, ALERT_DAYS + "일 이내에 마감하는 일정이 없습니다.");
            return;
        }
        StringBuilder sb = new StringBuilder("<html><b>" + ALERT_DAYS + "일 이내 마감 일정 " + soon.size() + "건</b><br><br>");
        for (Deadline d : soon) {
            String color = d.daysUntil(today) < 0 ? "#c0392b" : "#d35400";
            sb.append("<font color='").append(color).append("'>[").append(d.getDday(today)).append("]</font> ")
                    .append(SwingUtil.escape(service.getCourseName(d.getCourseId()))).append(" · ")
                    .append(SwingUtil.escape(d.getTitle())).append("<br>");
        }
        sb.append("</html>");
        JOptionPane.showMessageDialog(this, sb.toString(), "마감 임박 알림", JOptionPane.WARNING_MESSAGE);
    }

    private void exitProgram() {
        if (!save()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "저장하지 못했습니다. 그래도 종료할까요? (변경 내용이 사라질 수 있습니다)",
                    "종료 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
        }
        dispose();
        System.exit(0);
    }
}
