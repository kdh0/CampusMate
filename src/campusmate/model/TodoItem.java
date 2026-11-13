package campusmate.model;

import java.time.LocalDate;

/** 하루 계획에 적는 할 일 한 개. 예) 11/13 "연결리스트 노드 클래스 작성" */
public final class TodoItem {
    private final int id;
    private LocalDate date;
    private String content;
    private int courseId;       // 0이면 과목 없음 (예: 운동, 영단어)
    private int deadlineId;     // 0이면 연결된 마감 일정 없음
    private TodoStatus status;
    private int postponeCount;  // 다른 날에서 미뤄져 온 횟수

    public TodoItem(int id, LocalDate date, String content, int courseId, int deadlineId,
                    TodoStatus status, int postponeCount) {
        if (id <= 0) throw new IllegalArgumentException("할 일 ID는 양수여야 합니다.");
        if (postponeCount < 0) throw new IllegalArgumentException("미룬 횟수는 음수일 수 없습니다.");
        this.id = id;
        update(date, content, courseId, deadlineId);
        setStatus(status);
        this.postponeCount = postponeCount;
    }

    /**
     * 날짜, 내용, 연결 정보를 바꾼다. 모든 값을 검사한 뒤 한꺼번에 바꾼다.
     * 날짜가 바뀌면 날짜별 목록도 옮겨야 하므로 PlannerService.updateTodo()를 통해 호출한다.
     */
    public void update(LocalDate date, String content, int courseId, int deadlineId) {
        if (date == null) throw new IllegalArgumentException("날짜가 필요합니다.");
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("할 일 내용을 입력해 주세요.");
        }
        if (courseId < 0 || deadlineId < 0) throw new IllegalArgumentException("연결 정보가 올바르지 않습니다.");
        this.date = date;
        this.content = content.trim();
        this.courseId = courseId;
        this.deadlineId = deadlineId;
    }

    public int getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getContent() { return content; }
    public int getCourseId() { return courseId; }
    public int getDeadlineId() { return deadlineId; }
    public TodoStatus getStatus() { return status; }
    public int getPostponeCount() { return postponeCount; }
    public boolean isDone() { return status == TodoStatus.DONE; }

    public void setStatus(TodoStatus status) {
        if (status == null) throw new IllegalArgumentException("상태가 필요합니다.");
        this.status = status;
    }

    /** 연결된 마감 일정이 삭제되면 연결만 끊는다. (할 일 자체는 남긴다) */
    public void unlinkDeadline() { this.deadlineId = 0; }

    /** 날짜가 지났는데 아직 완료/못함/미룸 처리가 안 된 할 일인가? */
    public boolean isOverdue(LocalDate today) {
        return status == TodoStatus.PLANNED && date.isBefore(today);
    }

    /** 화면에 보여 줄 상태. 날짜가 지났는데 아직 '예정'이면 '못함'으로 보여 준다. */
    public TodoStatus getDisplayStatus(LocalDate today) {
        return isOverdue(today) ? TodoStatus.MISSED : status;
    }
}
