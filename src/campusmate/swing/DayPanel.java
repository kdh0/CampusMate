package campusmate.swing;

import campusmate.model.Deadline;
import campusmate.model.TodoItem;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * '하루 계획' 탭. 날짜를 넘겨 가며 그날 할 일을 보고, 체크하고, 바로 추가한다.
 * ┌ [◀] 2026년 11월 13일 (금) [▶] [오늘]            [진행률 바] ┐
 * │ 할 일 표 (완료 체크박스)                    │ 다가오는 마감 │
 * └ 할 일 [입력칸] [연결 ▾] [추가]   [수정] [삭제]               ┘
 */
public class DayPanel extends JPanel {
    private final MainFrame frame;
    private final PlannerService service;
    private LocalDate date = LocalDate.now();

    private final JLabel dateLabel = new JLabel("", SwingConstants.CENTER);
    private final JProgressBar dayProgress = new JProgressBar(0, 100);
    private final TodoTableModel model;
    private final JTable table;
    private final JTextField contentField = new JTextField(24);
    private final JComboBox<LinkChoice> linkCombo = new JComboBox<>();
    private List<LinkChoice> linkChoices;
    private final DefaultListModel<String> deadlineListModel = new DefaultListModel<>();

    public DayPanel(MainFrame frame) {
        this.frame = frame;
        this.service = frame.getService();
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 체크박스로 완료 상태가 바뀌면 저장/새로 고침. (표 편집이 끝난 뒤 실행되도록 invokeLater)
        model = new TodoTableModel(service, false, () -> SwingUtilities.invokeLater(frame::dataChanged));
        table = TodoTableModel.createTable(model);
        setupTableEvents();

        add(createDateBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createDeadlineSide(), BorderLayout.EAST);
        add(createInputBar(), BorderLayout.SOUTH);
    }

    public void showDate(LocalDate date) {
        this.date = date;
        refresh();
    }

    private JPanel createDateBar() {
        JButton prev = new JButton("◀");
        JButton next = new JButton("▶");
        JButton today = new JButton("오늘");
        prev.addActionListener(e -> showDate(date.minusDays(1)));
        next.addActionListener(e -> showDate(date.plusDays(1)));
        today.addActionListener(e -> showDate(LocalDate.now()));
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.BOLD, 17f));
        dateLabel.setPreferredSize(new Dimension(260, 30));

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        nav.add(prev);
        nav.add(dateLabel);
        nav.add(next);
        nav.add(today);

        dayProgress.setStringPainted(true);
        dayProgress.setPreferredSize(new Dimension(240, 26));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(new JLabel("이 날 달성률"));
        right.add(dayProgress);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(nav, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel createDeadlineSide() {
        JList<String> list = new JList<>(deadlineListModel);
        list.setFocusable(false);
        JLabel title = new JLabel("다가오는 마감 (" + PlannerService.DEADLINE_ALERT_DAYS + "일 이내)");
        title.setFont(title.getFont().deriveFont(Font.BOLD));

        JPanel side = new JPanel(new BorderLayout(0, 4));
        side.setPreferredSize(new Dimension(250, 0));
        side.add(title, BorderLayout.NORTH);
        side.add(new JScrollPane(list), BorderLayout.CENTER);
        return side;
    }

    private JPanel createInputBar() {
        JButton addButton = new JButton("추가");
        JButton editButton = new JButton("수정");
        JButton deleteButton = new JButton("삭제");
        addButton.addActionListener(e -> addTodo());
        contentField.addActionListener(e -> addTodo()); // 입력칸에서 Enter = 추가
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        linkCombo.setPreferredSize(new Dimension(260, 26));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(new JLabel("할 일"));
        left.add(contentField);
        left.add(linkCombo);
        left.add(addButton);

        JLabel help = new JLabel("Enter: 추가 · 더블클릭: 수정 · Delete: 삭제");
        help.setForeground(Color.GRAY);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(help);
        right.add(editButton);
        right.add(deleteButton);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void setupTableEvents() {
        // 마우스 더블클릭 → 수정 창 (체크박스 칸은 제외)
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) != 0) editSelected();
            }
        });
        // Delete 키 → 삭제, Enter 키 → 수정
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    deleteSelected();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume(); // 기본 동작(다음 줄로 이동) 막기
                    editSelected();
                }
            }
        });
    }

    public void refresh() {
        LocalDate today = LocalDate.now();
        String suffix = date.equals(today) ? "  · 오늘"
                : date.equals(today.plusDays(1)) ? "  · 내일"
                : date.equals(today.minusDays(1)) ? "  · 어제" : "";
        dateLabel.setText(SwingUtil.LONG_DATE.format(date) + suffix);

        List<TodoItem> todos = service.getTodos(date);
        model.setTodos(todos);
        int done = 0;
        for (TodoItem todo : todos) if (todo.isDone()) done++;
        dayProgress.setValue(SwingUtil.percent(done, todos.size()));
        dayProgress.setString(todos.isEmpty() ? "계획 없음"
                : done + " / " + todos.size() + "  (" + SwingUtil.percent(done, todos.size()) + "%)");

        // 연결 선택지는 마감 일정/과목이 바뀌었을 수 있으므로 다시 만든다. (고른 항목은 유지)
        LinkChoice selected = (LinkChoice) linkCombo.getSelectedItem();
        linkChoices = LinkChoice.build(service, 0);
        linkCombo.removeAllItems();
        for (LinkChoice choice : linkChoices) linkCombo.addItem(choice);
        if (selected != null) {
            linkCombo.setSelectedIndex(LinkChoice.indexOf(linkChoices, selected.getCourseId(), selected.getDeadlineId()));
        }

        deadlineListModel.clear();
        for (Deadline d : service.getUpcomingDeadlines(today)) {
            int total = service.countLinkedTodos(d.getId());
            String progress = total == 0 ? "" : "  (" + service.countLinkedDone(d.getId()) + "/" + total + ")";
            deadlineListModel.addElement("[" + d.getDday(today) + "] " + d.getTitle() + progress);
        }
        if (deadlineListModel.isEmpty()) deadlineListModel.addElement("없음");
    }

    private void addTodo() {
        String content = contentField.getText().trim();
        if (content.isEmpty()) {
            contentField.requestFocus();
            return;
        }
        LinkChoice link = (LinkChoice) linkCombo.getSelectedItem();
        try {
            service.addTodo(date, content, link == null ? 0 : link.getCourseId(), link == null ? 0 : link.getDeadlineId());
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "입력 확인", JOptionPane.WARNING_MESSAGE);
            return;
        }
        contentField.setText("");
        frame.dataChanged();
        contentField.requestFocus(); // 바로 다음 할 일을 이어서 입력할 수 있게
    }

    private TodoItem getSelectedTodo() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getTodoAt(row);
    }

    private void editSelected() {
        TodoItem todo = getSelectedTodo();
        if (todo == null) {
            JOptionPane.showMessageDialog(frame, "수정할 할 일을 표에서 선택해 주세요.");
            return;
        }
        frame.openTodoDialog(todo);
    }

    private void deleteSelected() {
        TodoItem todo = getSelectedTodo();
        if (todo == null) {
            JOptionPane.showMessageDialog(frame, "삭제할 할 일을 표에서 선택해 주세요.");
            return;
        }
        frame.deleteTodo(todo);
    }
}
