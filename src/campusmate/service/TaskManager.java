package campusmate.service;

import campusmate.model.Category;
import campusmate.model.Task;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 핵심 로직: 추가/조회/수정/삭제, 조건 검색, 과목 관리, 알림, 통계.
 * 콘솔(ConsoleApp)과 Swing(MainFrame)이 이 클래스를 똑같이 사용한다.
 */
public class TaskManager {
    // Map: ID → 할 일. ID로 바로 찾을 수 있다.
    private final Map<Integer, Task> tasks = new HashMap<>();
    // Set: 등록된 과목 이름 (중복 없이 가나다순)
    private final Set<String> courses = new TreeSet<>();
    private int nextId = 1;

    /** 파일에서 읽은 목록으로 시작한다. (처음 실행이면 빈 목록) */
    public TaskManager(List<Task> saved, Set<String> savedCourses) {
        courses.addAll(savedCourses);
        for (Task task : saved) {
            tasks.put(task.getId(), task);
            nextId = Math.max(nextId, task.getId() + 1);
            // 예전 파일에는 과목 목록이 없으므로, 항목에 쓰인 과목도 등록해 둔다.
            if (!task.getCourse().isEmpty()) courses.add(task.getCourse());
        }
    }

    // ======================= CRUD =======================

    /**
     * @param date    할 날 (시험은 시험 날)
     * @param dueDate 과제 마감일 (null이면 할 날과 같음). 할 일·시험은 무시한다.
     */
    public Task add(String title, String course, Category category, LocalDate date, LocalDate dueDate) {
        dueDate = checkDates(category, date, dueDate);
        Task task = new Task(nextId, title, course, category, date, dueDate, false);
        tasks.put(task.getId(), task);
        nextId++;
        if (!task.getCourse().isEmpty()) courses.add(task.getCourse());
        return task;
    }

    public Task get(int id) {
        Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException(id + "번 항목이 없습니다.");
        return task;
    }

    /** 전체 목록 (List, 날짜순) */
    public List<Task> getAll() {
        List<Task> list = new ArrayList<>(tasks.values());
        list.sort(Comparator.comparing(Task::getDate).thenComparing(Task::getId));
        return list;
    }

    /** 그날 목록. 아직 못 끝낸 과제는 마감일에도 나온다. */
    public List<Task> getByDate(LocalDate date) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (task.getDate().equals(date)) result.add(task);
            else if (task.getCategory() == Category.ASSIGNMENT && !task.isDone() && date.equals(task.getDeadline())) result.add(task);
        }
        return result;
    }

    public List<Task> getByCourse(String course) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (task.getCourse().equals(course)) result.add(task);
        }
        return result;
    }

    public void update(int id, String title, String course, Category category, LocalDate date, LocalDate dueDate) {
        Task task = get(id);
        dueDate = checkDates(category, date, dueDate);
        task.setTitle(title);   // 내용이 비어 있으면 여기서 예외 → 아무것도 바뀌지 않는다.
        task.setCourse(course);
        task.setCategory(category);
        task.setDate(date);
        task.setDueDate(dueDate);
        if (!task.getCourse().isEmpty()) courses.add(task.getCourse());
    }

    /** 날짜를 검사하고, 저장할 마감일을 돌려준다. (과제가 아니면 null) */
    private LocalDate checkDates(Category category, LocalDate date, LocalDate dueDate) {
        if (category != Category.ASSIGNMENT || dueDate == null) return null;
        if (date != null && date.isAfter(dueDate)) throw new IllegalArgumentException("할 날이 마감일보다 늦습니다.");
        return dueDate;
    }

    /** 완료 ↔ 미완료. 바뀐 상태를 돌려준다. */
    public boolean toggleDone(int id) {
        Task task = get(id);
        task.setDone(!task.isDone());
        return task.isDone();
    }

    public void delete(int id) {
        get(id); // 없는 번호면 예외
        tasks.remove(id);
    }

    // ======================= 조건 검색 =======================

    /**
     * 조건 검색 (콘솔). null 조건은 '상관없음'이고, 여러 조건은 모두 만족해야 한다(AND).
     * @param course   과목 ("" = 과목 없음)
     * @param category 종류
     * @param done     true = 완료, false = 미완료
     * @param when     -1 = 지난 일, 0 = 오늘, 1 = 앞으로 일 (목록에 나오는 날 기준)
     */
    public List<Task> search(String course, Category category, Boolean done, Integer when, LocalDate today) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (course != null && !task.getCourse().equals(course)) continue;
            if (category != null && task.getCategory() != category) continue;
            if (done != null && task.isDone() != done) continue;
            if (when != null && Integer.signum(task.getDate().compareTo(today)) != when) continue;
            result.add(task);
        }
        return result;
    }

    /** 검색어 검색 (Swing 할 일 목록 탭). 검색어는 내용 또는 과목 이름에서 찾는다. */
    public List<Task> searchKeyword(String keyword, Category category, Boolean done) {
        String word = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            String text = (task.getTitle() + " " + task.getCourse()).toLowerCase();
            if (!text.contains(word)) continue;
            if (category != null && task.getCategory() != category) continue;
            if (done != null && task.isDone() != done) continue;
            result.add(task);
        }
        return result;
    }

    // ======================= 과목 관리 =======================

    public Set<String> getCourses() { return new TreeSet<>(courses); }

    public void addCourse(String name) {
        courses.add(checkNewCourse(name));
    }

    /** 과목 이름을 바꾼다. 그 과목을 쓰는 항목도 함께 바뀐다. */
    public void renameCourse(String oldName, String newName) {
        String name = checkNewCourse(newName);
        if (!courses.remove(oldName)) throw new IllegalArgumentException("없는 과목입니다.");
        courses.add(name);
        for (Task task : tasks.values()) {
            if (task.getCourse().equals(oldName)) task.setCourse(name);
        }
    }

    /** 과목을 지운다. 그 과목의 항목도 함께 지운다. */
    public void deleteCourse(String name) {
        if (!courses.remove(name)) throw new IllegalArgumentException("없는 과목입니다.");
        tasks.values().removeIf(task -> task.getCourse().equals(name));
    }

    private String checkNewCourse(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("과목 이름을 입력해 주세요.");
        if (courses.contains(value)) throw new IllegalArgumentException("이미 있는 과목입니다.");
        return value;
    }

    // ======================= 알림 =======================

    /** 아직 끝나지 않은 과제·시험 중 마감이 안 지난 것 (마감일 순) */
    public List<Task> getUpcomingDeadlines(LocalDate today) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (task.getCategory().isDeadline() && !task.isDone() && task.daysLeft(today) >= 0) result.add(task);
        }
        result.sort(Comparator.comparing(Task::getDeadline));
        return result;
    }

    /** 할 날이 지났는데 끝내지 못한 할 일·과제. (마감이 지난 과제와 시험은 옮길 수 없으니 제외) */
    public List<Task> getOverdue(LocalDate today) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (task.getCategory() == Category.EXAM || !task.isOverdue(today)) continue;
            if (task.getCategory() == Category.ASSIGNMENT && task.daysLeft(today) < 0) continue;
            result.add(task);
        }
        return result;
    }

    /** 팀 특화 기능: 못 한 일을 오늘로 옮긴다. (과제의 마감일은 그대로) */
    public void moveToToday(int id, LocalDate today) {
        get(id).setDate(today);
    }

    // ======================= 통계 / 공통 도우미 =======================

    public static int countDone(List<Task> list) {
        int count = 0;
        for (Task task : list) {
            if (task.isDone()) count++;
        }
        return count;
    }

    public static int percent(int done, int total) {
        return total == 0 ? 0 : done * 100 / total;
    }

    /**
     * 날짜 입력을 해석한다. (콘솔·Swing 공통) "11/20"(월/일, 올해) 형식만 받는다. 아니면 null.
     */
    public static LocalDate parseDate(String text, LocalDate today) {
        String s = text.trim();
        if (!s.matches("\\d{1,2}/\\d{1,2}")) return null;
        String[] parts = s.split("/");
        try {
            return LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (DateTimeException e) { // 13/1, 11/31 같은 없는 날짜
            return null;
        }
    }

    /** parseDate가 읽을 수 있는 "11/20" 형식으로 바꾼다. */
    public static String formatDate(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }
}
