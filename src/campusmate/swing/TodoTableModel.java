package campusmate.swing;

import campusmate.model.TodoItem;
import campusmate.model.TodoStatus;
import campusmate.service.PlannerService;
import java.awt.Color;
import java.awt.Component;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * JTable에 할 일 목록을 보여 주는 표 모델. ('하루 계획' 탭과 '검색' 탭에서 함께 사용)
 * JTable은 이 클래스에 "몇 행? 몇 열? 이 칸의 값은?"을 물어 화면을 그린다.
 */
public class TodoTableModel extends AbstractTableModel {
    private final PlannerService service;
    private final boolean showDate;              // 검색 결과처럼 여러 날짜가 섞일 때만 날짜 열을 보여 준다.
    private final Runnable onCompletionChanged;  // 체크박스를 눌렀을 때 실행할 일 (저장 + 화면 새로 고침)
    private final String[] columns;
    private List<TodoItem> todos = new ArrayList<>();

    public TodoTableModel(PlannerService service, boolean showDate, Runnable onCompletionChanged) {
        this.service = service;
        this.showDate = showDate;
        this.onCompletionChanged = onCompletionChanged;
        this.columns = showDate
                ? new String[] {"완료", "날짜", "할 일", "연결", "상태"}
                : new String[] {"완료", "할 일", "연결", "상태"};
    }

    public void setTodos(List<TodoItem> todos) {
        this.todos = new ArrayList<>(todos);
        fireTableDataChanged(); // 표에 "데이터가 바뀌었으니 다시 그려라"라고 알린다.
    }

    public TodoItem getTodoAt(int row) { return todos.get(row); }

    @Override public int getRowCount() { return todos.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int column) { return columns[column]; }

    /** 첫 번째 열을 Boolean으로 알려 주면 JTable이 자동으로 체크박스를 그려 준다. */
    @Override
    public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : Object.class; }

    /** 완료 체크박스만 직접 클릭해서 바꿀 수 있다. */
    @Override
    public boolean isCellEditable(int row, int column) { return column == 0; }

    @Override
    public Object getValueAt(int row, int column) {
        TodoItem todo = todos.get(row);
        // 날짜 열이 없을 때는 1번 이후 열 번호를 하나씩 당겨서 같은 switch를 쓴다.
        int col = showDate ? column : (column == 0 ? 0 : column + 1);
        switch (col) {
            case 0: return todo.isDone();
            case 1: return SwingUtil.SHORT_DATE.format(todo.getDate());
            case 2: return todo.getContent()
                    + (todo.getPostponeCount() > 0 ? "   (미룬 일 " + todo.getPostponeCount() + "회)" : "");
            case 3: return SwingUtil.linkText(service, todo);
            case 4: return todo.getDisplayStatus(LocalDate.now()).getLabel();
            default: return "";
        }
    }

    /** 체크박스를 클릭하면 호출된다. */
    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != 0) return;
        TodoItem todo = todos.get(row);
        if (todo.isDone() != (Boolean) value) service.toggleTodo(todo.getId());
        fireTableRowsUpdated(row, row);
        onCompletionChanged.run();
    }

    /** 이 모델을 쓰는 JTable을 만들고, 상태별 색상과 열 너비를 설정한다. */
    public static JTable createTable(TodoTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new StatusRenderer(model));

        int[] widths = model.showDate ? new int[] {45, 90, 330, 230, 60} : new int[] {45, 360, 260, 60};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        return table;
    }

    /** 행마다 상태에 따라 배경색을 바꿔 그린다: 완료(연두), 못함(빨강), 미룸(노랑). */
    private static class StatusRenderer extends DefaultTableCellRenderer {
        private final TodoTableModel model;

        StatusRenderer(TodoTableModel model) { this.model = model; }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            TodoStatus status = model.getTodoAt(row).getDisplayStatus(LocalDate.now());
            String name = model.getColumnName(column);
            setHorizontalAlignment(name.equals("상태") || name.equals("날짜") ? CENTER : LEFT);
            if (isSelected) return this; // 선택된 줄은 기본 선택 색 사용

            setForeground(status == TodoStatus.DONE ? Color.GRAY : Color.BLACK);
            if (status == TodoStatus.DONE) setBackground(SwingUtil.DONE_COLOR);
            else if (status == TodoStatus.MISSED) setBackground(SwingUtil.MISSED_COLOR);
            else if (status == TodoStatus.POSTPONED) setBackground(SwingUtil.POSTPONED_COLOR);
            else setBackground(Color.WHITE);
            return this;
        }
    }
}
