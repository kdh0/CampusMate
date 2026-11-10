package campusmate.service;

import campusmate.model.Course;
import campusmate.model.Task;
import campusmate.model.TaskType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 화면 입출력과 무관한 핵심 로직. Swing 화면에서도 이 클래스를 재사용한다. */
public final class PlannerService {
    // Map: ID로 과목 조회 / List: 일정 순회, 검색, 정렬
    private final Map<Integer, Course> courses = new LinkedHashMap<>();
    private final List<Task> tasks = new ArrayList<>();
    private final PriorityCalculator priorityCalculator = new PriorityCalculator();
    private int nextCourseId = 1;
    private int nextTaskId = 1;

    public PlannerService() { }

    public PlannerService(List<Course> savedCourses, List<Task> savedTasks,
                          int nextCourseId, int nextTaskId) {
        for (Course course : savedCourses) {
            if (courses.put(course.getId(), course) != null) {
                throw new IllegalArgumentException("중복된 과목 ID입니다.");
            }
            if (course.getId() >= nextCourseId) throw new IllegalArgumentException("과목 ID 정보 오류");
        }
        for (Task task : savedTasks) {
            requireCourse(task.getCourseId());
            if (tasks.stream().anyMatch(t -> t.getId() == task.getId())) {
                throw new IllegalArgumentException("중복된 일정 ID입니다.");
            }
            if (task.getId() >= nextTaskId) throw new IllegalArgumentException("일정 ID 정보 오류");
            tasks.add(task);
        }
        if (nextCourseId < 1 || nextTaskId < 1) throw new IllegalArgumentException("다음 ID 정보 오류");
        this.nextCourseId = nextCourseId;
        this.nextTaskId = nextTaskId;
    }

    public List<Course> getCourses() { return new ArrayList<>(courses.values()); }
    public List<Task> getTasks() { return Collections.unmodifiableList(tasks); }
    public int getNextCourseId() { return nextCourseId; }
    public int getNextTaskId() { return nextTaskId; }

    public Course requireCourse(int id) {
        Course course = courses.get(id);
        if (course == null) throw new IllegalArgumentException("해당 ID의 과목이 없습니다.");
        return course;
    }

    public Task requireTask(int id) {
        for (Task task : tasks) if (task.getId() == id) return task;
        throw new IllegalArgumentException("해당 ID의 일정이 없습니다.");
    }

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
        for (Task task : tasks) {
            if (task.getCourseId() == id) {
                throw new IllegalArgumentException("연결된 일정을 먼저 삭제하거나 다른 과목으로 옮겨 주세요.");
            }
        }
        courses.remove(id);
    }

    public Task addTask(int courseId, TaskType type, String title, String memo,
                        LocalDate dueDate, int importance) {
        requireCourse(courseId);
        Task task = new Task(nextTaskId, courseId, type, title, memo, dueDate, importance, false);
        tasks.add(task);
        nextTaskId++;
        return task;
    }

    public void updateTask(int id, int courseId, TaskType type, String title, String memo,
                           LocalDate dueDate, int importance) {
        requireCourse(courseId);
        requireTask(id).update(courseId, type, title, memo, dueDate, importance);
    }

    public void setCompleted(int id, boolean completed) { requireTask(id).setCompleted(completed); }
    public void deleteTask(int id) { tasks.remove(requireTask(id)); }

    /** null 조건은 전체, 여러 조건은 AND로 적용한다. */
    public List<Task> search(String keyword, Integer courseId, TaskType type, Boolean completed) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Task> result = new ArrayList<>();
        for (Task task : tasks) {
            String text = task.getTitle() + " " + task.getMemo() + " "
                    + requireCourse(task.getCourseId()).getName();
            if (!text.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (courseId != null && task.getCourseId() != courseId) continue;
            if (type != null && task.getType() != type) continue;
            if (completed != null && task.isCompleted() != completed) continue;
            result.add(task);
        }
        result.sort(Comparator.comparing(Task::getDueDate).thenComparingInt(Task::getId));
        return result;
    }

    public int priorityScore(Task task, LocalDate today) { return priorityCalculator.calculate(task, today); }

    public List<Task> recommendations(LocalDate today) {
        List<Task> result = search("", null, null, false);
        result.sort(Comparator.comparingInt((Task t) -> priorityScore(t, today)).reversed()
                .thenComparing(Task::getDueDate)
                .thenComparing(Comparator.comparingInt(Task::getImportance).reversed())
                .thenComparingInt(Task::getId));
        return result;
    }

    /** 미완료 일정 중 기한이 지났거나 3일 이내에 마감하는 일정. */
    public List<Task> upcoming(LocalDate today) {
        List<Task> result = new ArrayList<>();
        for (Task task : search("", null, null, false)) {
            if (task.daysUntil(today) <= 3) result.add(task);
        }
        return result;
    }

    public int completedCount(Integer courseId, TaskType type) {
        return search("", courseId, type, true).size();
    }

    public double completionRate(Integer courseId, TaskType type) {
        int total = search("", courseId, type, null).size();
        return total == 0 ? 0.0 : completedCount(courseId, type) * 100.0 / total;
    }
}
