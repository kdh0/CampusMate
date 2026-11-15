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
 * 핵심 로직: 추가/조회/수정/삭제, 조건 검색, 알림, 통계.
 * 콘솔(ConsoleApp)과 Swing(MainFrame)이 이 클래스를 똑같이 사용한다.
 */
public class TaskManager {
    /** 이 일수 이내에 마감하는 과제·시험을 알려 준다. */
    public static final int ALERT_DAYS = 3;

    // Map: ID → 할 일. ID로 바로 찾을 수 있다.
    private final Map<Integer, Task> tasks = new HashMap<>();
    private int nextId = 1;

    /** 파일에서 읽은 목록으로 시작한다. (처음 실행이면 빈 목록) */
    public TaskManager(List<Task> saved) {
        for (Task task : saved) {
            tasks.put(task.getId(), task);
            nextId = Math.max(nextId, task.getId() + 1);
        }
    }

    // ======================= CRUD =======================

    public Task add(String title, String course, Category category, LocalDate date) {
        Task task = new Task(nextId, title, course, category, date, false);
        tasks.put(task.getId(), task);
        nextId++;
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

    public List<Task> getByDate(LocalDate date) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (task.getDate().equals(date)) result.add(task);
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

    public void update(int id, String title, String course, Category category, LocalDate date) {
        Task task = get(id);
        task.setTitle(title);   // 내용이 비어 있으면 여기서 예외 → 아무것도 바뀌지 않는다.
        task.setCourse(course);
        task.setCategory(category);
        task.setDate(date);
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
     * 조건 검색. null 조건은 '전체'이고, 여러 조건은 모두 만족해야 한다(AND).
     * @param keyword  내용 또는 과목 이름에 들어 있는 글자
     * @param category 종류 (null = 전체)
     * @param done     true = 완료, false = 미완료, null = 전체
     */
    public List<Task> search(String keyword, Category category, Boolean done) {
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

    /** Set: 등록된 과목 이름 (중복 없이 가나다순) */
    public Set<String> getCourses() {
        Set<String> courses = new TreeSet<>();
        for (Task task : tasks.values()) {
            if (!task.getCourse().isEmpty()) courses.add(task.getCourse());
        }
        return courses;
    }

    // ======================= 알림 =======================

    /** 오늘부터 ALERT_DAYS일 안에 마감하는, 아직 끝나지 않은 과제·시험 */
    public List<Task> getUpcomingDeadlines(LocalDate today) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            long days = task.daysLeft(today);
            if (task.getCategory().isDeadline() && !task.isDone() && days >= 0 && days <= ALERT_DAYS) {
                result.add(task);
            }
        }
        return result;
    }

    /** 날짜가 지났는데 끝내지 못한 '할 일' (과제·시험은 마감일이 고정이라 제외) */
    public List<Task> getOverdueTodos(LocalDate today) {
        List<Task> result = new ArrayList<>();
        for (Task task : getAll()) {
            if (task.getCategory() == Category.TODO && task.isOverdue(today)) result.add(task);
        }
        return result;
    }

    /** 팀 특화 기능: 못 한 할 일을 오늘로 옮긴다. */
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
     * 날짜 입력을 해석한다. (콘솔·Swing 공통) 해석할 수 없으면 null.
     * "오늘" / "+3"(3일 뒤) / "-1"(어제) / "11/20"(올해) / "2026-11-20"
     */
    public static LocalDate parseDate(String text, LocalDate today) {
        String s = text.trim();
        try {
            if (s.equals("오늘")) return today;
            if (s.startsWith("+") || s.startsWith("-")) return today.plusDays(Integer.parseInt(s));
            if (s.matches("\\d{1,2}/\\d{1,2}")) {
                String[] parts = s.split("/");
                return LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            return LocalDate.parse(s);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }
}
