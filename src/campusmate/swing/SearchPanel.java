package campusmate.swing;

import campusmate.model.Course;
import campusmate.model.TodoItem;
import campusmate.model.TodoStatus;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * '검색' 탭: 검색어 / 과목 / 기간 / 상태로 할 일을 찾는다. 조건은 모두 AND.
 * 검색어를 입력하면 바로 결과가 바뀌고, 과목 목록에는 검색어에 맞는 개수가 함께 표시된다.
 */
public class SearchPanel extends JPanel {
    private final PlannerService service;

    private final JTextField keywordField = new JTextField(12);
    private final JComboBox<String> courseCombo = new JComboBox<>();
    private final List<Integer> courseIds = new ArrayList<>(); // 콤보박스 위치 → 과목 ID (0번 = 전체)
    private final JTextField fromField = new JTextField(7);
    private final JTextField toField = new JTextField(7);
    private final JComboBox<String> statusCombo = new JComboBox<>(new String[] {"전체 상태", "예정", "완료", "못함"});
    private final JLabel resultLabel = new JLabel();
    private final TodoTableModel model;
    private final JTable table;
    // 콤보박스 내용을 코드로 다시 채우는 동안에는 검색 이벤트를 무시하기 위한 표시
    private boolean updatingCombo = false;

    public SearchPanel(MainFrame frame) {
        this.service = frame.getService();
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        model = new TodoTableModel(service, true, () -> SwingUtilities.invokeLater(frame::dataChanged));
        table = TodoTableModel.createTable(model);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.getSelectedRow();
                if (e.getClickCount() == 2 && row >= 0 && table.columnAtPoint(e.getPoint()) != 0) {
                    frame.openTodoDialog(model.getTodoAt(row));
                }
            }
        });

        add(createFilterBar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(resultLabel, BorderLayout.SOUTH);
    }

    private JPanel createFilterBar() {
        // 키 입력 이벤트: 글자를 칠 때마다 바로 검색
        keywordField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) { refresh(); }
        });
        courseCombo.addActionListener(e -> { if (!updatingCombo) refresh(); });
        statusCombo.addActionListener(e -> refresh());
        fromField.addActionListener(e -> refresh()); // 날짜 칸에서 Enter = 검색
        toField.addActionListener(e -> refresh());
        fromField.setToolTipText(SwingUtil.DATE_HELP);
        toField.setToolTipText(SwingUtil.DATE_HELP);

        JButton searchButton = new JButton("검색");
        searchButton.addActionListener(e -> refresh());
        JButton weekButton = new JButton("최근 7일");
        weekButton.addActionListener(e -> {
            fromField.setText("-6");
            toField.setText("오늘");
            refresh();
        });
        JButton resetButton = new JButton("초기화");
        resetButton.addActionListener(e -> {
            keywordField.setText("");
            fromField.setText("");
            toField.setText("");
            courseCombo.setSelectedIndex(0);
            statusCombo.setSelectedIndex(0);
            refresh();
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.add(new JLabel("검색어"));
        bar.add(keywordField);
        bar.add(courseCombo);
        bar.add(new JLabel("  기간"));
        bar.add(fromField);
        bar.add(new JLabel("~"));
        bar.add(toField);
        bar.add(statusCombo);
        bar.add(searchButton);
        bar.add(weekButton);
        bar.add(resetButton);
        return bar;
    }

    public void refresh() {
        LocalDate today = LocalDate.now();
        String keyword = keywordField.getText();
        reloadCourseCombo(keyword, today);

        // 날짜 칸: 비어 있으면 조건 없음, 잘못 입력하면 빨간 테두리로 알려 주고 그 조건은 무시한다.
        LocalDate from = readDateField(fromField);
        LocalDate to = readDateField(toField);

        Integer courseId = courseCombo.getSelectedIndex() <= 0 ? null : courseIds.get(courseCombo.getSelectedIndex());
        TodoStatus status = null;
        if (statusCombo.getSelectedIndex() == 1) status = TodoStatus.PLANNED;
        if (statusCombo.getSelectedIndex() == 2) status = TodoStatus.DONE;
        if (statusCombo.getSelectedIndex() == 3) status = TodoStatus.MISSED;

        List<TodoItem> result = service.searchTodos(keyword, courseId, from, to, status, today);
        model.setTodos(result);
        int done = 0;
        for (TodoItem todo : result) if (todo.isDone()) done++;
        resultLabel.setText("  검색 결과 " + result.size() + "건 · 완료 " + done + "건"
                + "   (더블클릭: 수정 · 체크박스: 완료 처리)");
    }

    /** 과목 콤보박스를 "자료구조 (3)"처럼 검색어에 맞는 개수와 함께 다시 채운다. 선택한 과목은 유지. */
    private void reloadCourseCombo(String keyword, LocalDate today) {
        Integer selectedId = courseCombo.getSelectedIndex() <= 0 ? null : courseIds.get(courseCombo.getSelectedIndex());

        // Map: 과목 ID → 검색어에 맞는 할 일 수
        Map<Integer, Integer> countByCourse = new HashMap<>();
        for (TodoItem todo : service.searchTodos(keyword, null, null, null, null, today)) {
            countByCourse.put(todo.getCourseId(), countByCourse.getOrDefault(todo.getCourseId(), 0) + 1);
        }

        updatingCombo = true;
        courseCombo.removeAllItems();
        courseIds.clear();
        courseCombo.addItem("전체 과목");
        courseIds.add(null);
        int selectIndex = 0;
        for (Course course : service.getCourses()) {
            courseCombo.addItem(course.getName() + " (" + countByCourse.getOrDefault(course.getId(), 0) + ")");
            courseIds.add(course.getId());
            if (selectedId != null && selectedId == course.getId()) selectIndex = courseIds.size() - 1;
        }
        courseCombo.setSelectedIndex(selectIndex);
        updatingCombo = false;
    }

    private LocalDate readDateField(JTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setBorder(new JTextField().getBorder());
            return null;
        }
        LocalDate date = SwingUtil.parseDate(text);
        field.setBorder(date == null ? BorderFactory.createLineBorder(Color.RED, 2) : new JTextField().getBorder());
        return date;
    }
}
