package campusmate.swing;

import campusmate.model.Course;
import campusmate.model.Deadline;
import campusmate.model.DeadlineType;
import campusmate.service.PlannerService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/** 마감 일정 추가/수정 창. deadline이 null이면 '추가', 아니면 '수정'. */
public class DeadlineDialog extends JDialog {
    private final PlannerService service;
    private final Deadline deadline;
    private final List<Course> courses;

    private final JComboBox<String> courseCombo = new JComboBox<>();
    private final JComboBox<String> typeCombo = new JComboBox<>();
    private final JTextField titleField = new JTextField(24);
    private final JTextField dateField = new JTextField(10);
    private boolean saved = false;

    public DeadlineDialog(JFrame owner, PlannerService service, Deadline deadline) {
        super(owner, deadline == null ? "마감 일정 추가" : "마감 일정 수정", true);
        this.service = service;
        this.deadline = deadline;
        this.courses = service.getCourses();

        for (Course course : courses) courseCombo.addItem(course.getName());
        for (DeadlineType type : DeadlineType.values()) typeCombo.addItem(type.getLabel());
        if (deadline == null) {
            dateField.setText(LocalDate.now().plusDays(7).toString());
        } else {
            courseCombo.setSelectedIndex(courses.indexOf(service.requireCourse(deadline.getCourseId())));
            typeCombo.setSelectedIndex(deadline.getType().ordinal());
            titleField.setText(deadline.getTitle());
            dateField.setText(deadline.getDueDate().toString());
        }
        dateField.setToolTipText(SwingUtil.DATE_HELP);

        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        datePanel.add(dateField);
        datePanel.add(quickDateButton("+7일", 7));
        datePanel.add(quickDateButton("+14일", 14));

        JPanel labels = new JPanel(new GridLayout(0, 1, 0, 8));
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 8));
        String[] names = {"과목", "종류", "제목", "마감일"};
        JComponent[] inputs = {courseCombo, typeCombo, titleField, datePanel};
        for (int i = 0; i < names.length; i++) {
            labels.add(new JLabel(names[i]));
            fields.add(inputs[i]);
        }
        JPanel form = new JPanel(new BorderLayout(12, 0));
        form.add(labels, BorderLayout.WEST);
        form.add(fields, BorderLayout.CENTER);

        JButton saveButton = new JButton(deadline == null ? "추가" : "저장");
        JButton cancelButton = new JButton("취소");
        saveButton.addActionListener(e -> onSave());
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(saveButton); // Enter = 저장
        getRootPane().registerKeyboardAction(e -> dispose(), // ESC = 취소
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(saveButton);
        buttons.add(cancelButton);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private JButton quickDateButton(String text, int plusDays) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(2, 6, 2, 6));
        button.addActionListener(e -> dateField.setText(LocalDate.now().plusDays(plusDays).toString()));
        return button;
    }

    private void onSave() {
        LocalDate dueDate = SwingUtil.parseDate(dateField.getText());
        if (dueDate == null) {
            showWarning("마감일을 확인해 주세요. (" + SwingUtil.DATE_HELP + ")");
            return;
        }
        int courseId = courses.get(courseCombo.getSelectedIndex()).getId();
        DeadlineType type = DeadlineType.values()[typeCombo.getSelectedIndex()];
        try {
            if (deadline == null) {
                service.addDeadline(courseId, type, titleField.getText(), dueDate);
            } else {
                service.updateDeadline(deadline.getId(), courseId, type, titleField.getText(), dueDate);
            }
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
            return;
        }
        saved = true;
        dispose();
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "입력 확인", JOptionPane.WARNING_MESSAGE);
    }
}
