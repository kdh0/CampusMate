package campusmate.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 과제·시험처럼 마감일이 정해진 큰 일정. 하루 할 일(TodoItem)을 여기에 연결할 수 있다. */
public final class Deadline {
    private final int id;
    private int courseId;
    private DeadlineType type;
    private String title;
    private LocalDate dueDate;
    private boolean completed;

    public Deadline(int id, int courseId, DeadlineType type, String title,
                    LocalDate dueDate, boolean completed) {
        if (id <= 0) throw new IllegalArgumentException("마감 일정 ID는 양수여야 합니다.");
        this.id = id;
        update(courseId, type, title, dueDate);
        this.completed = completed;
    }

    // 먼저 모든 값을 검사하여 잘못된 입력 때 일부 필드만 바뀌는 것을 막는다.
    public void update(int courseId, DeadlineType type, String title, LocalDate dueDate) {
        if (courseId <= 0) throw new IllegalArgumentException("과목을 선택해 주세요.");
        if (type == null || dueDate == null) {
            throw new IllegalArgumentException("종류와 마감일이 필요합니다.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해 주세요.");
        }
        this.courseId = courseId;
        this.type = type;
        this.title = title.trim();
        this.dueDate = dueDate;
    }

    public int getId() { return id; }
    public int getCourseId() { return courseId; }
    public DeadlineType getType() { return type; }
    public String getTitle() { return title; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public long daysUntil(LocalDate today) {
        return ChronoUnit.DAYS.between(today, dueDate);
    }

    public String getDday(LocalDate today) {
        long days = daysUntil(today);
        if (days == 0) return "D-day";
        return days > 0 ? "D-" + days : "D+" + (-days) + " 기한 지남";
    }
}
