package campusmate.swing;

import campusmate.model.TodoItem;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/** 할 일 수정 창. 모달(modal)이라 닫기 전에는 메인 창을 누를 수 없다. */
public class TodoDialog extends JDialog {
    private final PlannerService service;
    private final TodoItem todo;

    private final JTextField contentField = new JTextField(28);
    private final JTextField dateField = new JTextField(10);
    private final JComboBox<LinkChoice> linkCombo = new JComboBox<>();
    private final JCheckBox doneCheck = new JCheckBox("완료");
    private final List<LinkChoice> linkChoices;
    private boolean saved = false;

    public TodoDialog(JFrame owner, PlannerService service, TodoItem todo) {
        super(owner, "할 일 수정", true);
        this.service = service;
        this.todo = todo;

        contentField.setText(todo.getContent());
        dateField.setText(todo.getDate().toString());
        linkChoices = LinkChoice.build(service, todo.getDeadlineId());
        for (LinkChoice choice : linkChoices) linkCombo.addItem(choice);
        linkCombo.setSelectedIndex(LinkChoice.indexOf(linkChoices, todo));
        doneCheck.setSelected(todo.isDone());

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        root.add(createForm(), BorderLayout.CENTER);
        root.add(createButtons(), BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private JPanel createForm() {
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        datePanel.add(dateField);
        datePanel.add(quickDateButton("오늘", 0));
        datePanel.add(quickDateButton("내일", 1));
        dateField.setToolTipText(SwingUtil.DATE_HELP);

        // 왼쪽: 이름표 / 오른쪽: 입력칸 (GridLayout으로 줄 맞춤)
        JPanel labels = new JPanel(new GridLayout(0, 1, 0, 8));
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 8));
        String[] names = {"할 일", "날짜", "연결", "상태"};
        JComponent[] inputs = {contentField, datePanel, linkCombo, doneCheck};
        for (int i = 0; i < names.length; i++) {
            labels.add(new JLabel(names[i]));
            fields.add(inputs[i]);
        }
        JPanel form = new JPanel(new BorderLayout(12, 0));
        form.add(labels, BorderLayout.WEST);
        form.add(fields, BorderLayout.CENTER);
        return form;
    }

    private JButton quickDateButton(String text, int plusDays) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(2, 6, 2, 6));
        button.addActionListener(e -> dateField.setText(LocalDate.now().plusDays(plusDays).toString()));
        return button;
    }

    private JPanel createButtons() {
        JButton saveButton = new JButton("저장");
        JButton cancelButton = new JButton("취소");
        saveButton.addActionListener(e -> onSave());
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(saveButton); // Enter 키 = 저장
        getRootPane().registerKeyboardAction(e -> dispose(), // ESC 키 = 취소
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(saveButton);
        panel.add(cancelButton);
        return panel;
    }

    private void onSave() {
        LocalDate date = SwingUtil.parseDate(dateField.getText());
        if (date == null) {
            showWarning("날짜를 확인해 주세요. (" + SwingUtil.DATE_HELP + ")");
            dateField.requestFocus();
            return;
        }
        LinkChoice link = (LinkChoice) linkCombo.getSelectedItem();
        try {
            // 값 검사는 콘솔 버전과 똑같이 서비스/모델 클래스가 담당한다.
            service.updateTodo(todo.getId(), date, contentField.getText(), link.getCourseId(), link.getDeadlineId());
            if (todo.isDone() != doneCheck.isSelected()) service.toggleTodo(todo.getId());
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
