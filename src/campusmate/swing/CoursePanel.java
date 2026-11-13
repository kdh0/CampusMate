package campusmate.swing;

import campusmate.model.Course;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/** '과목' 탭: 과목 목록(JList) + 이름 입력칸 + 추가/이름 변경/삭제 버튼. */
public class CoursePanel extends JPanel {
    private final MainFrame frame;
    private final PlannerService service;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> courseList = new JList<>(listModel);
    private List<Course> courses = new ArrayList<>(); // courseList의 각 줄과 같은 순서
    private final JTextField nameField = new JTextField(20);

    public CoursePanel(MainFrame frame) {
        this.frame = frame;
        this.service = frame.getService();
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("수강 과목 (과목을 선택하면 아래 입력칸에 이름이 채워집니다)");
        title.setFont(title.getFont().deriveFont(Font.BOLD));

        courseList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        courseList.setFixedCellHeight(28);
        courseList.addListSelectionListener(e -> {
            Course course = getSelectedCourse();
            if (course != null) nameField.setText(course.getName());
        });

        JButton addButton = new JButton("추가");
        JButton renameButton = new JButton("이름 변경");
        JButton deleteButton = new JButton("삭제");
        addButton.addActionListener(e -> addCourse());
        nameField.addActionListener(e -> addCourse()); // 입력칸에서 Enter = 추가
        renameButton.addActionListener(e -> renameCourse());
        deleteButton.addActionListener(e -> deleteCourse());

        JPanel inputBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        inputBar.add(new JLabel("과목 이름"));
        inputBar.add(nameField);
        inputBar.add(addButton);
        inputBar.add(renameButton);
        inputBar.add(deleteButton);

        add(title, BorderLayout.NORTH);
        add(new JScrollPane(courseList), BorderLayout.CENTER);
        add(inputBar, BorderLayout.SOUTH);
    }

    public void refresh() {
        Course selected = getSelectedCourse();
        courses = service.getCourses();
        listModel.clear();
        for (Course course : courses) {
            listModel.addElement("  " + course.getName() + "    (마감 일정 " + service.countDeadlinesOfCourse(course.getId())
                    + "개, 할 일 " + service.countTodosOfCourse(course.getId()) + "개)");
            if (selected != null && selected.getId() == course.getId()) {
                courseList.setSelectedIndex(listModel.size() - 1);
            }
        }
    }

    private Course getSelectedCourse() {
        int index = courseList.getSelectedIndex();
        return index < 0 || index >= courses.size() ? null : courses.get(index);
    }

    private void addCourse() {
        try {
            service.addCourse(nameField.getText());
            nameField.setText("");
            frame.dataChanged();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        }
    }

    private void renameCourse() {
        Course course = getSelectedCourse();
        if (course == null) {
            showWarning("이름을 바꿀 과목을 목록에서 선택해 주세요.");
            return;
        }
        try {
            service.renameCourse(course.getId(), nameField.getText());
            frame.dataChanged();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        }
    }

    private void deleteCourse() {
        Course course = getSelectedCourse();
        if (course == null) {
            showWarning("삭제할 과목을 목록에서 선택해 주세요.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(frame, "'" + course.getName() + "' 과목을 삭제할까요?",
                "삭제 확인", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            service.deleteCourse(course.getId());
            nameField.setText("");
            frame.dataChanged();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        }
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(frame, message, "안내", JOptionPane.WARNING_MESSAGE);
    }
}
