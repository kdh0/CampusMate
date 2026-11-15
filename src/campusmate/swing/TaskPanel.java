package campusmate.swing;

import campusmate.model.Category;
import campusmate.model.Task;
import campusmate.service.TaskManager;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/** '할 일 목록' 탭: 위쪽 검색 조건 + 가운데 표(JTable) + 아래쪽 추가/수정/삭제 버튼. */
public class TaskPanel extends JPanel {
    private static final String[] COLUMNS = {"완료", "날짜", "D-day", "종류", "과목", "내용", "상태"};

    private final MainFrame frame;
    private final TaskManager manager;

    private final JTextField keywordField = new JTextField(15);
    private final JComboBox<String> categoryBox = new JComboBox<>(new String[] {"전체 종류", "할 일", "과제", "시험"});
    private final JComboBox<String> statusBox = new JComboBox<>(new String[] {"전체 상태", "미완료", "완료"});
    private final TaskTableModel model = new TaskTableModel();
    private final JTable table = new JTable(model);
    private List<Task> shown = new ArrayList<>(); // 지금 표에 보이는 항목 (표의 줄 순서와 같음)

    public TaskPanel(MainFrame frame) {
        this.frame = frame;
        this.manager = frame.getManager();
        setLayout(new BorderLayout(0, 6));

        // ----- 검색 조건 -----
        keywordField.addKeyListener(new KeyAdapter() { // 키 입력: 글자를 칠 때마다 바로 검색
            @Override
            public void keyReleased(KeyEvent e) { refresh(); }
        });
        categoryBox.addActionListener(e -> refresh());
        statusBox.addActionListener(e -> refresh());
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchBar.add(new JLabel("검색어"));
        searchBar.add(keywordField);
        searchBar.add(categoryBox);
        searchBar.add(statusBox);

        // ----- 표 -----
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(5).setPreferredWidth(300);
        table.addMouseListener(new MouseAdapter() { // 더블클릭: 수정
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) != 0) editSelected();
            }
        });
        table.addKeyListener(new KeyAdapter() { // Delete 키: 삭제
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) deleteSelected();
            }
        });

        // ----- 버튼 -----
        JButton addButton = new JButton("추가");
        JButton editButton = new JButton("수정");
        JButton deleteButton = new JButton("삭제");
        addButton.addActionListener(e -> frame.openTaskForm(null, LocalDate.now()));
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonBar.add(addButton);
        buttonBar.add(editButton);
        buttonBar.add(deleteButton);
        buttonBar.add(new JLabel("   체크박스: 완료 · 더블클릭: 수정 · Delete: 삭제"));

        add(searchBar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttonBar, BorderLayout.SOUTH);
    }

    /** 검색 조건대로 표를 다시 채운다. */
    public void refresh() {
        int c = categoryBox.getSelectedIndex();
        Category category = c == 0 ? null : Category.values()[c - 1];
        Boolean done = null;
        if (statusBox.getSelectedIndex() == 1) done = false;
        if (statusBox.getSelectedIndex() == 2) done = true;

        shown = manager.search(keywordField.getText(), category, done);
        model.fireTableDataChanged(); // 표에 "데이터가 바뀌었으니 다시 그려라"라고 알린다.
    }

    private Task getSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "표에서 항목을 먼저 선택해 주세요.");
            return null;
        }
        return shown.get(row);
    }

    private void editSelected() {
        Task task = getSelected();
        if (task != null) frame.openTaskForm(task, task.getDate());
    }

    private void deleteSelected() {
        Task task = getSelected();
        if (task == null) return;
        int answer = JOptionPane.showConfirmDialog(frame, "'" + task.getTitle() + "'을(를) 삭제할까요?",
                "삭제 확인", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            manager.delete(task.getId());
            frame.dataChanged();
        }
    }

    /** JTable은 이 클래스에 "몇 줄? 몇 칸? 이 칸의 값은?"을 물어서 화면을 그린다. */
    private class TaskTableModel extends AbstractTableModel {
        @Override public int getRowCount() { return shown.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        /** 첫 칸을 Boolean이라고 알려 주면 JTable이 체크박스로 그려 준다. */
        @Override
        public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : String.class; }

        /** 완료 체크박스만 클릭해서 바꿀 수 있다. */
        @Override
        public boolean isCellEditable(int row, int column) { return column == 0; }

        @Override
        public Object getValueAt(int row, int column) {
            Task task = shown.get(row);
            LocalDate today = LocalDate.now();
            switch (column) {
                case 0: return task.isDone();
                case 1: return task.getDate().toString();
                case 2: return task.getCategory().isDeadline() ? task.getDday(today) : "";
                case 3: return task.getCategory().getLabel();
                case 4: return task.getCourse();
                case 5: return task.getTitle();
                default: return task.getStatus(today);
            }
        }

        /** 체크박스를 클릭하면 호출된다. */
        @Override
        public void setValueAt(Object value, int row, int column) {
            manager.toggleDone(shown.get(row).getId());
            SwingUtilities.invokeLater(frame::dataChanged); // 표의 클릭 처리가 끝난 뒤 저장/새로 고침
        }
    }
}
