package campusmate.swing;

import campusmate.model.Course;
import campusmate.model.Deadline;
import campusmate.model.TodoItem;
import campusmate.service.PlannerService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 할 일을 어디에 연결할지 고르는 콤보박스의 항목 하나.
 * 예) "연결 없음", "[마감] 자료구조 · 연결리스트 과제 (D-7)", "[과목] 객체지향"
 */
final class LinkChoice {
    private final String label;
    private final int courseId;
    private final int deadlineId;

    private LinkChoice(String label, int courseId, int deadlineId) {
        this.label = label;
        this.courseId = courseId;
        this.deadlineId = deadlineId;
    }

    int getCourseId() { return courseId; }
    int getDeadlineId() { return deadlineId; }

    /** JComboBox는 항목의 toString()을 화면에 보여 준다. */
    @Override
    public String toString() { return label; }

    /**
     * 선택지 목록을 만든다: 연결 없음 → 진행 중인 마감 일정 → 과목.
     * @param keepDeadlineId 이미 완료된 마감 일정이라도 목록에 남길 ID (수정할 때 현재 연결 유지용)
     */
    static List<LinkChoice> build(PlannerService service, int keepDeadlineId) {
        LocalDate today = LocalDate.now();
        List<LinkChoice> result = new ArrayList<>();
        result.add(new LinkChoice("연결 없음", 0, 0));
        for (Deadline d : service.getDeadlines()) {
            if (d.isCompleted() && d.getId() != keepDeadlineId) continue;
            String state = d.isCompleted() ? "완료" : d.getDday(today);
            result.add(new LinkChoice("[마감] " + service.getCourseName(d.getCourseId()) + " · "
                    + d.getTitle() + " (" + state + ")", 0, d.getId()));
        }
        for (Course course : service.getCourses()) {
            result.add(new LinkChoice("[과목] " + course.getName(), course.getId(), 0));
        }
        return result;
    }

    /** 목록에서 같은 연결을 가리키는 항목의 위치. 없으면 0(연결 없음). */
    static int indexOf(List<LinkChoice> choices, int courseId, int deadlineId) {
        for (int i = 0; i < choices.size(); i++) {
            LinkChoice c = choices.get(i);
            if (deadlineId != 0 && c.deadlineId == deadlineId) return i;
            if (deadlineId == 0 && courseId != 0 && c.deadlineId == 0 && c.courseId == courseId) return i;
        }
        return 0;
    }

    static int indexOf(List<LinkChoice> choices, TodoItem todo) {
        return indexOf(choices, todo.getCourseId(), todo.getDeadlineId());
    }
}
