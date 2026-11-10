package campusmate.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 과제, 시험, 공부 계획의 공통 정보를 담는다. */
public final class Task {
    private final int id;
    private int courseId;
    private TaskType type;
    private String title;
    private String memo;
    private LocalDate dueDate;
    private int importance;
    private boolean completed;

    public Task(int id, int courseId, TaskType type, String title, String memo,
                LocalDate dueDate, int importance, boolean completed) {
        if (id <= 0) throw new IllegalArgumentException("일정 ID는 양수여야 합니다.");
        this.id = id;
        update(courseId, type, title, memo, dueDate, importance);
        this.completed = completed;
    }

    // 먼저 모든 값을 검사하여 잘못된 입력 때 일부 필드만 바뀌는 것을 막는다.
    public void update(int courseId, TaskType type, String title, String memo,
                       LocalDate dueDate, int importance) {
        if (courseId <= 0) throw new IllegalArgumentException("과목 ID는 양수여야 합니다.");
        if (type == null || dueDate == null) {
            throw new IllegalArgumentException("종류와 마감일이 필요합니다.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해 주세요.");
        }
        if (importance < 1 || importance > 5) {
            throw new IllegalArgumentException("중요도는 1~5 사이입니다.");
        }
        this.courseId = courseId;
        this.type = type;
        this.title = title.trim();
        this.memo = memo == null ? "" : memo.trim();
        this.dueDate = dueDate;
        this.importance = importance;
    }

    public int getId() { return id; }
    public int getCourseId() { return courseId; }
    public TaskType getType() { return type; }
    public String getTitle() { return title; }
    public String getMemo() { return memo; }
    public LocalDate getDueDate() { return dueDate; }
    public int getImportance() { return importance; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public long daysUntil(LocalDate today) {
        return ChronoUnit.DAYS.between(today, dueDate);
    }

    public String getDday(LocalDate today) {
        long days = daysUntil(today);
        if (days == 0) return "D-day";
        return days > 0 ? "D-" + days : "D+" + (-days) + " (기한 지남)";
    }
}
