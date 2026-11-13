package campusmate.swing;

import campusmate.model.Deadline;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** '마감 일정' 탭: 과제·시험 목록(D-day, 연결된 할 일 진행률) + 추가/수정/삭제. */
public class DeadlinePanel extends JPanel {
    private final MainFrame frame;
    private final PlannerService service;
    private final DeadlineTableModel model = new DeadlineTableModel();
    private final JTable table = new JTable(model);

    public DeadlinePanel(MainFrame frame) {
        this.frame = frame;
        this.service = frame.getService();
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new DeadlineRenderer());
        int[] widths = {45, 110, 60, 120, 300, 100, 150};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(0).setMaxWidth(50);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.columnAtPoint(e.getPoint()) != 0) editSelected();
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) deleteSelected();
            }
        });

        JButton addButton = new JButton("+ 마감 일정 추가");
        JButton editButton = new JButton("수정");
        JButton deleteButton = new JButton("삭제");
        addButton.addActionListener(e -> openDialog(null));
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        JLabel help = new JLabel("  할 일을 추가할 때 마감 일정에 연결하면 '관련 할 일' 진행률이 올라갑니다.");
        help.setForeground(Color.GRAY);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(help);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    public void refresh() {
        model.setDeadlines(service.getDeadlines());
    }

    private Deadline getSelected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getDeadlineAt(row);
    }

    private void openDialog(Deadline deadline) {
        if (service.getCourses().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "먼저 '과목' 탭에서 과목을 등록해 주세요.");
            frame.showCourseTab();
            return;
        }
        DeadlineDialog dialog = new DeadlineDialog(frame, service, deadline);
        dialog.setVisible(true);
        if (dialog.isSaved()) frame.dataChanged();
    }

    private void editSelected() {
        Deadline deadline = getSelected();
        if (deadline == null) {
            JOptionPane.showMessageDialog(frame, "수정할 마감 일정을 표에서 선택해 주세요.");
            return;
        }
        openDialog(deadline);
    }

    private void deleteSelected() {
        Deadline deadline = getSelected();
        if (deadline == null) {
            JOptionPane.showMessageDialog(frame, "삭제할 마감 일정을 표에서 선택해 주세요.");
            return;
        }
        int linked = service.countLinkedTodos(deadline.getId());
        String message = "'" + deadline.getTitle() + "' 마감 일정을 삭제할까요?"
                + (linked > 0 ? "\n연결된 할 일 " + linked + "개는 남고 연결만 끊어집니다." : "");
        if (JOptionPane.showConfirmDialog(frame, message, "삭제 확인", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION) {
            service.deleteDeadline(deadline.getId());
            frame.dataChanged();
        }
    }

    /** 마감 일정 표 모델. 첫 열(완료)은 체크박스. */
    private class DeadlineTableModel extends AbstractTableModel {
        private final String[] columns = {"완료", "D-day", "종류", "과목", "제목", "마감일", "관련 할 일"};
        private List<Deadline> deadlines = new ArrayList<>();

        void setDeadlines(List<Deadline> deadlines) {
            this.deadlines = new ArrayList<>(deadlines);
            fireTableDataChanged();
        }

        Deadline getDeadlineAt(int row) { return deadlines.get(row); }

        @Override public int getRowCount() { return deadlines.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : Object.class; }
        @Override public boolean isCellEditable(int row, int column) { return column == 0; }

        @Override
        public Object getValueAt(int row, int column) {
            Deadline d = deadlines.get(row);
            switch (column) {
                case 0: return d.isCompleted();
                case 1: return d.isCompleted() ? "-" : d.getDday(LocalDate.now());
                case 2: return d.getType().getLabel();
                case 3: return service.getCourseName(d.getCourseId());
                case 4: return d.getTitle();
                case 5: return SwingUtil.SHORT_DATE.format(d.getDueDate());
                case 6:
                    int total = service.countLinkedTodos(d.getId());
                    int done = service.countLinkedDone(d.getId());
                    return total == 0 ? "-" : done + "/" + total + " (" + SwingUtil.percent(done, total) + "%)";
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0) return;
            Deadline d = deadlines.get(row);
            if (d.isCompleted() != (Boolean) value) service.toggleDeadline(d.getId());
            SwingUtilities.invokeLater(frame::dataChanged);
        }
    }

    /** 완료(회색), 기한 지남(빨강), 3일 이내(노랑)로 행 배경색을 바꾼다. */
    private class DeadlineRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(column == 4 ? LEFT : CENTER);
            if (isSelected) return this;
            Deadline d = model.getDeadlineAt(row);
            long days = d.daysUntil(LocalDate.now());
            setForeground(d.isCompleted() ? Color.GRAY : Color.BLACK);
            if (d.isCompleted()) setBackground(new Color(240, 240, 240));
            else if (days < 0) setBackground(SwingUtil.MISSED_COLOR);
            else if (days <= 3) setBackground(SwingUtil.POSTPONED_COLOR);
            else setBackground(Color.WHITE);
            return this;
        }
    }
}
