package campusmate.service;

import campusmate.model.Course;
import campusmate.model.Deadline;
import campusmate.model.DeadlineType;
import campusmate.model.TodoItem;
import campusmate.model.TodoStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 화면 입출력과 무관한 핵심 로직. (과목 / 마감 일정 / 하루 할 일)
 * 콘솔 화면(ConsoleUI)과 이후 Swing 화면이 모두 이 클래스를 그대로 사용한다.
 */
public final class PlannerService {
    /** 이 일수 이내에 마감하는 일정을 '다가오는 마감'으로 보여 준다. */
    public static final int DEADLINE_ALERT_DAYS = 7;

    // Map: 과목 ID → 과목 (ID로 바로 찾기, 등록 순서 유지)
    private final Map<Integer, Course> courses = new LinkedHashMap<>();
    // List: 마감 일정 목록 (정렬해서 보여 줌)
    private final List<Deadline> deadlines = new ArrayList<>();
    // Map: 날짜 → 그날 할 일 목록. TreeMap이라 날짜순으로 자동 정렬된다.
    private final Map<LocalDate, List<TodoItem>> todosByDate = new TreeMap<>();

    private int nextCourseId = 1;
    private int nextDeadlineId = 1;
    private int nextTodoId = 1;

    public PlannerService() { }

    /** 파일에서 읽어 온 데이터로 복원한다. 값이 서로 맞지 않으면 예외를 던진다. */
    public PlannerService(List<Course> savedCourses, List<Deadline> savedDeadlines, List<TodoItem> savedTodos,
                          int nextCourseId, int nextDeadlineId, int nextTodoId) {
        if (nextCourseId < 1 || nextDeadlineId < 1 || nextTodoId < 1) {
            throw new IllegalArgumentException("다음 ID 정보 오류");
        }
        for (Course course : savedCourses) {
            if (course.getId() >= nextCourseId || courses.put(course.getId(), course) != null) {
                throw new IllegalArgumentException("과목 ID 정보 오류: " + course.getId());
            }
        }
        // Set: 이미 나온 ID를 기억해 중복을 검사한다. (add가 false면 중복)
        Set<Integer> seenIds = new HashSet<>();
        for (Deadline deadline : savedDeadlines) {
            requireCourse(deadline.getCourseId());
            if (deadline.getId() >= nextDeadlineId || !seenIds.add(deadline.getId())) {
                throw new IllegalArgumentException("마감 일정 ID 정보 오류: " + deadline.getId());
            }
            deadlines.add(deadline);
        }
        seenIds.clear();
        for (TodoItem todo : savedTodos) {
            if (todo.getCourseId() != 0) requireCourse(todo.getCourseId());
            if (todo.getDeadlineId() != 0) requireDeadline(todo.getDeadlineId());
            if (todo.getId() >= nextTodoId || !seenIds.add(todo.getId())) {
                throw new IllegalArgumentException("할 일 ID 정보 오류: " + todo.getId());
            }
            listOf(todo.getDate()).add(todo);
        }
        this.nextCourseId = nextCourseId;
        this.nextDeadlineId = nextDeadlineId;
        this.nextTodoId = nextTodoId;
    }

    public int getNextCourseId() { return nextCourseId; }
    public int getNextDeadlineId() { return nextDeadlineId; }
    public int getNextTodoId() { return nextTodoId; }

    // ======================= 과목 =======================

    public List<Course> getCourses() { return new ArrayList<>(courses.values()); }

    public Course requireCourse(int id) {
        Course course = courses.get(id);
        if (course == null) throw new IllegalArgumentException("해당 과목이 없습니다.");
        return course;
    }

    public String getCourseName(int id) { return requireCourse(id).getName(); }

    public Course addCourse(String name) {
        checkCourseName(name, 0);
        Course course = new Course(nextCourseId, name);
        courses.put(course.getId(), course);
        nextCourseId++;
        return course;
    }

    public void renameCourse(int id, String name) {
        Course course = requireCourse(id);
        checkCourseName(name, id);
        course.setName(name);
    }

    private void checkCourseName(String name, int exceptId) {
        for (Course course : courses.values()) {
            if (course.getId() != exceptId && name != null
                    && course.getName().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("같은 이름의 과목이 이미 있습니다.");
            }
        }
    }

    public void deleteCourse(int id) {
        requireCourse(id);
        if (countDeadlinesOfCourse(id) > 0 || countTodosOfCourse(id) > 0) {
            throw new IllegalArgumentException("이 과목에 연결된 마감 일정이나 할 일이 있어 삭제할 수 없습니다.");
        }
        courses.remove(id);
    }

    public int countDeadlinesOfCourse(int courseId) {
        int count = 0;
        for (Deadline deadline : deadlines) if (deadline.getCourseId() == courseId) count++;
        return count;
    }

    public int countTodosOfCourse(int courseId) {
        int count = 0;
        for (TodoItem todo : getAllTodos()) if (todo.getCourseId() == courseId) count++;
        return count;
    }

    // ======================= 마감 일정 =======================

    /** 미완료가 먼저, 그 안에서는 마감일이 빠른 순. */
    public List<Deadline> getDeadlines() {
        List<Deadline> result = new ArrayList<>(deadlines);
        result.sort(Comparator.comparing(Deadline::isCompleted)
                .thenComparing(Deadline::getDueDate)
                .thenComparingInt(Deadline::getId));
        return result;
    }

    /** 아직 끝나지 않은 마감 일정 (마감일 순). */
    public List<Deadline> getOpenDeadlines() {
        List<Deadline> result = new ArrayList<>();
        for (Deadline deadline : getDeadlines()) if (!deadline.isCompleted()) result.add(deadline);
        return result;
    }

    /** 미완료 중 기한이 지났거나 DEADLINE_ALERT_DAYS일 이내에 마감하는 일정. */
    public List<Deadline> getUpcomingDeadlines(LocalDate today) {
        List<Deadline> result = new ArrayList<>();
        for (Deadline deadline : getOpenDeadlines()) {
            if (deadline.daysUntil(today) <= DEADLINE_ALERT_DAYS) result.add(deadline);
        }
        return result;
    }

    public Deadline requireDeadline(int id) {
        for (Deadline deadline : deadlines) if (deadline.getId() == id) return deadline;
        throw new IllegalArgumentException("해당 마감 일정이 없습니다.");
    }

    public Deadline addDeadline(int courseId, DeadlineType type, String title, LocalDate dueDate) {
        requireCourse(courseId);
        Deadline deadline = new Deadline(nextDeadlineId, courseId, type, title, dueDate, false);
        deadlines.add(deadline);
        nextDeadlineId++;
        return deadline;
    }

    public void updateDeadline(int id, int courseId, DeadlineType type, String title, LocalDate dueDate) {
        requireCourse(courseId);
        requireDeadline(id).update(courseId, type, title, dueDate);
        // 과목이 바뀌었으면 연결된 할 일의 과목도 함께 바꾼다.
        for (TodoItem todo : getAllTodos()) {
            if (todo.getDeadlineId() == id) {
                todo.update(todo.getDate(), todo.getContent(), courseId, id);
            }
        }
    }

    /** 완료 ↔ 미완료를 바꾸고, 바뀐 상태를 돌려준다. */
    public boolean toggleDeadline(int id) {
        Deadline deadline = requireDeadline(id);
        deadline.setCompleted(!deadline.isCompleted());
        return deadline.isCompleted();
    }

    /** 마감 일정을 지운다. 연결된 할 일은 지우지 않고 연결만 끊는다. */
    public void deleteDeadline(int id) {
        Deadline deadline = requireDeadline(id);
        for (TodoItem todo : getAllTodos()) {
            if (todo.getDeadlineId() == id) todo.unlinkDeadline();
        }
        deadlines.remove(deadline);
    }

    public int countLinkedTodos(int deadlineId) {
        int count = 0;
        for (TodoItem todo : getAllTodos()) if (todo.getDeadlineId() == deadlineId) count++;
        return count;
    }

    public int countLinkedDone(int deadlineId) {
        int count = 0;
        for (TodoItem todo : getAllTodos()) {
            if (todo.getDeadlineId() == deadlineId && todo.isDone()) count++;
        }
        return count;
    }

    // ======================= 하루 할 일 =======================

    /** 해당 날짜의 할 일 목록 (적은 순서). 바깥에서 고쳐도 원본이 바뀌지 않도록 복사본을 준다. */
    public List<TodoItem> getTodos(LocalDate date) {
        List<TodoItem> list = todosByDate.get(date);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /** 할 일이 하나라도 있는 날짜들 (날짜순). */
    public List<LocalDate> getPlanDates() { return new ArrayList<>(todosByDate.keySet()); }

    /** 모든 할 일 (날짜순). */
    public List<TodoItem> getAllTodos() {
        List<TodoItem> result = new ArrayList<>();
        for (List<TodoItem> list : todosByDate.values()) result.addAll(list);
        return result;
    }

    public TodoItem requireTodo(int id) {
        for (TodoItem todo : getAllTodos()) if (todo.getId() == id) return todo;
        throw new IllegalArgumentException("해당 할 일이 없습니다.");
    }

    /**
     * 할 일을 추가한다.
     * @param courseId   과목 (0: 없음)
     * @param deadlineId 연결할 마감 일정 (0: 없음). 연결하면 과목은 그 마감 일정의 과목을 따른다.
     */
    public TodoItem addTodo(LocalDate date, String content, int courseId, int deadlineId) {
        int finalCourseId = resolveCourse(courseId, deadlineId);
        TodoItem todo = new TodoItem(nextTodoId, date, content, finalCourseId, deadlineId, TodoStatus.PLANNED, 0);
        listOf(date).add(todo);
        nextTodoId++;
        return todo;
    }

    public void updateTodo(int id, LocalDate date, String content, int courseId, int deadlineId) {
        TodoItem todo = requireTodo(id);
        LocalDate oldDate = todo.getDate();
        todo.update(date, content, resolveCourse(courseId, deadlineId), deadlineId);
        // 날짜가 바뀌었으면 날짜별 목록에서도 옮긴다.
        if (!oldDate.equals(date)) {
            removeFromDate(todo, oldDate);
            listOf(date).add(todo);
        }
    }

    /** 완료 ↔ 예정을 바꾸고, 완료되었으면 true. */
    public boolean toggleTodo(int id) {
        TodoItem todo = requireTodo(id);
        todo.setStatus(todo.isDone() ? TodoStatus.PLANNED : TodoStatus.DONE);
        return todo.isDone();
    }

    public void deleteTodo(int id) {
        TodoItem todo = requireTodo(id);
        removeFromDate(todo, todo.getDate());
    }

    // ----- 지난 날 못 한 일 처리 -----

    /** 날짜가 지났는데 아직 처리되지 않은 할 일들. */
    public List<TodoItem> getOverdueTodos(LocalDate today) {
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem todo : getAllTodos()) if (todo.isOverdue(today)) result.add(todo);
        return result;
    }

    /**
     * 못 한 일을 오늘로 옮긴다.
     * 원래 날짜의 기록은 '미룸'으로 남기고, 오늘 날짜에 같은 내용의 할 일을 새로 만든다.
     */
    public TodoItem postponeToToday(int id, LocalDate today) {
        TodoItem old = requireTodo(id);
        if (!old.isOverdue(today)) throw new IllegalArgumentException("지난 날짜의 미완료 할 일만 옮길 수 있습니다.");
        old.setStatus(TodoStatus.POSTPONED);
        TodoItem moved = new TodoItem(nextTodoId, today, old.getContent(), old.getCourseId(),
                old.getDeadlineId(), TodoStatus.PLANNED, old.getPostponeCount() + 1);
        listOf(today).add(moved);
        nextTodoId++;
        return moved;
    }

    /** 옮기지 않고 '못함'으로 기록한다. */
    public void markMissed(int id) { requireTodo(id).setStatus(TodoStatus.MISSED); }

    // ----- 조건 검색 -----

    /**
     * 할 일 조건 검색. null 조건은 '전체'를 뜻하고, 여러 조건은 AND로 적용한다.
     * 검색어는 할 일 내용, 과목명, 마감 일정 제목에서 찾는다. 결과는 날짜순.
     * @param status PLANNED(예정), DONE(완료), MISSED(못함 - 미룸 포함) 중 하나 또는 null
     */
    public List<TodoItem> searchTodos(String keyword, Integer courseId, LocalDate from, LocalDate to,
                                      TodoStatus status, LocalDate today) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<TodoItem> result = new ArrayList<>();
        for (TodoItem todo : getAllTodos()) {
            if (!searchText(todo).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (courseId != null && todo.getCourseId() != courseId) continue;
            if (from != null && todo.getDate().isBefore(from)) continue;
            if (to != null && todo.getDate().isAfter(to)) continue;
            if (status != null && !matchesStatus(todo, status, today)) continue;
            result.add(todo);
        }
        return result;
    }

    private String searchText(TodoItem todo) {
        String text = todo.getContent();
        if (todo.getCourseId() != 0) text += " " + getCourseName(todo.getCourseId());
        if (todo.getDeadlineId() != 0) text += " " + requireDeadline(todo.getDeadlineId()).getTitle();
        return text;
    }

    private boolean matchesStatus(TodoItem todo, TodoStatus filter, LocalDate today) {
        TodoStatus shown = todo.getDisplayStatus(today);
        // 사용자 입장에서 '미룸'도 그날 못 한 일이므로 '못함' 검색에 포함한다.
        if (filter == TodoStatus.MISSED) return shown == TodoStatus.MISSED || shown == TodoStatus.POSTPONED;
        return shown == filter;
    }

    // ----- 기록 / 통계 -----

    /** 기간 안의 할 일 개수. from/to가 null이면 처음부터/끝까지. */
    public int countTodos(LocalDate from, LocalDate to) {
        return searchTodos("", null, from, to, null, LocalDate.now()).size();
    }

    /** 기간 안에서 완료한 할 일 개수. */
    public int countDone(LocalDate from, LocalDate to) {
        int count = 0;
        for (TodoItem todo : searchTodos("", null, from, to, null, LocalDate.now())) {
            if (todo.isDone()) count++;
        }
        return count;
    }

    /** 계획한 할 일을 모두 끝낸 날짜 모음. (Set: 어떤 날짜가 들어 있는지 contains로 빠르게 확인) */
    public Set<LocalDate> getAchievedDays() {
        Set<LocalDate> result = new HashSet<>();
        for (Map.Entry<LocalDate, List<TodoItem>> entry : todosByDate.entrySet()) {
            boolean allDone = true;
            for (TodoItem todo : entry.getValue()) {
                if (!todo.isDone()) allDone = false;
            }
            if (allDone) result.add(entry.getKey());
        }
        return result;
    }

    /**
     * 연속 달성 일수: 최근 날짜부터 거꾸로, 할 일을 모두 끝낸 날이 몇 번 이어졌는지 센다.
     * - 계획이 없는 날은 건너뛴다.
     * - 오늘은 아직 진행 중이므로, 다 끝냈을 때만 센다. (못 끝냈다고 끊기지 않음)
     */
    public int getStreak(LocalDate today) {
        Set<LocalDate> achieved = getAchievedDays();
        List<LocalDate> dates = getPlanDates();
        Collections.reverse(dates); // 최근 날짜부터
        int streak = 0;
        for (LocalDate date : dates) {
            if (date.isAfter(today)) continue;
            if (achieved.contains(date)) {
                streak++;
            } else if (!date.equals(today)) {
                break;
            }
        }
        return streak;
    }

    // ----- 내부 도우미 -----

    /** 해당 날짜의 목록을 꺼낸다. 없으면 새로 만들어 Map에 넣는다. */
    private List<TodoItem> listOf(LocalDate date) {
        List<TodoItem> list = todosByDate.get(date);
        if (list == null) {
            list = new ArrayList<>();
            todosByDate.put(date, list);
        }
        return list;
    }

    private void removeFromDate(TodoItem todo, LocalDate date) {
        List<TodoItem> list = todosByDate.get(date);
        if (list == null) return;
        list.remove(todo);
        if (list.isEmpty()) todosByDate.remove(date); // 빈 날짜는 기록에서 뺀다.
    }

    /** 마감 일정에 연결하면 그 마감 일정의 과목을, 아니면 입력한 과목(0: 없음)을 쓴다. */
    private int resolveCourse(int courseId, int deadlineId) {
        if (deadlineId != 0) return requireDeadline(deadlineId).getCourseId();
        if (courseId != 0) requireCourse(courseId);
        return courseId;
    }
}
