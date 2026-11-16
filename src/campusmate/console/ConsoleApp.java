package campusmate.console;

import campusmate.model.Category;
import campusmate.model.Task;
import campusmate.service.TaskManager;
import campusmate.storage.FileStore;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** 1단계 콘솔 버전. 메뉴로 입력을 받고, 실제 처리는 TaskManager에 맡긴다. */
public class ConsoleApp {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("M/d");

    private final TaskManager manager;
    private final FileStore store;
    private final Scanner in;

    public ConsoleApp(TaskManager manager, FileStore store, Scanner in) {
        this.manager = manager;
        this.store = store;
        this.in = in;
    }

    public static void main(String[] args) {
        FileStore store = new FileStore(FileStore.DEFAULT_PATH);
        TaskManager manager;
        try {
            manager = new TaskManager(store.load(), store.loadCourses());
        } catch (IOException e) {
            System.out.println("데이터를 불러오지 못했습니다: " + e.getMessage());
            System.out.println("기존 파일을 덮어쓰지 않도록 종료합니다.");
            return;
        }
        // IDE 실행창은 UTF-8, 실제 터미널은 그 터미널의 인코딩으로 읽는다. (한글 깨짐 방지)
        Scanner in = System.console() == null
                ? new Scanner(System.in, "UTF-8") : new Scanner(System.console().reader());
        new ConsoleApp(manager, store, in).run();
    }

    public void run() {
        System.out.println("=== CampusMate | 대학생 과제·할 일 플래너 ===");
        System.out.println("저장 위치: " + store.getPath());
        startupCheck();
        while (true) {
            printMenu();
            String choice = ask("선택: ");
            try {
                switch (choice) {
                    case "1" -> toggle();
                    case "2" -> add();
                    case "3" -> editOrDelete();
                    case "4" -> showList();
                    case "5" -> search();
                    case "6" -> manageCourses();
                    case "0" -> {
                        save();
                        System.out.println("저장하고 종료합니다.");
                        return;
                    }
                    default -> System.out.println("메뉴에 있는 번호를 입력해 주세요.");
                }
            } catch (IllegalArgumentException e) {
                // 빈 내용, 없는 번호 같은 잘못된 입력은 안내만 하고 메뉴로 돌아간다.
                System.out.println("안내: " + e.getMessage());
            }
        }
    }

    /** 메뉴 위에 오늘 할 일을 항상 보여 준다. */
    private void printMenu() {
        List<Task> today = manager.getByDate(LocalDate.now());
        System.out.println("\n====== 오늘 " + DATE.format(LocalDate.now()) + " · "
                + TaskManager.countDone(today) + "/" + today.size() + " 완료 ======");
        printItems(today);
        System.out.println("1. 완료 체크 (#번호)   2. 추가        3. 수정/삭제 (#번호)");
        System.out.println("4. 목록 보기           5. 검색        6. 과목 관리        0. 저장 후 종료");
    }

    /** 시작할 때: (처음이면) 과목 등록 → 과목별 마감 일정 → 지난 일 오늘로 옮기기 */
    private void startupCheck() {
        LocalDate today = LocalDate.now();
        if (manager.getCourses().isEmpty()) registerCourses();
        printDeadlines(today);

        List<Task> overdue = manager.getOverdue(today);
        if (!overdue.isEmpty()) {
            System.out.println("\n[지난 날 못 한 일 " + overdue.size() + "개]");
            printItems(overdue);
            if (ask("모두 오늘로 옮길까요? (y/n): ").equalsIgnoreCase("y")) {
                for (Task task : overdue) manager.moveToToday(task.getId(), today);
                System.out.println("오늘로 옮겼습니다.");
            }
        }
    }

    private void registerCourses() {
        System.out.println("\n처음 오셨네요! 이번 학기에 듣는 과목을 등록해 주세요. (Enter: 끝)");
        while (true) {
            String name = ask("과목 이름: ");
            if (name.isEmpty()) return;
            try {
                manager.addCourse(name);
            } catch (IllegalArgumentException e) {
                System.out.println("안내: " + e.getMessage());
            }
        }
    }

    /** 끝나지 않은 과제·시험의 D-day를 과목별로 묶어 보여 준다. */
    private void printDeadlines(LocalDate today) {
        List<Task> upcoming = manager.getUpcomingDeadlines(today);
        System.out.println("\n[과목별 마감 일정]");
        if (upcoming.isEmpty()) {
            System.out.println("  (없음)");
            return;
        }
        List<String> groups = new ArrayList<>(manager.getCourses());
        groups.add(""); // 과목 없음
        for (String course : groups) {
            boolean header = false;
            for (Task task : upcoming) {
                if (!task.getCourse().equals(course)) continue;
                if (!header) {
                    System.out.println("  " + (course.isEmpty() ? "(과목 없음)" : course));
                    header = true;
                }
                System.out.println("    " + task.getDday(today) + "  [" + task.getCategory() + "] " + task.getTitle()
                        + " (" + DATE.format(task.getDeadline()) + ")");
            }
        }
    }

    // ======================= 메뉴 기능 =======================

    private void toggle() {
        int id = askNumber("완료/취소할 번호(#): ");
        boolean done = manager.toggleDone(id);
        System.out.println(done ? "완료했습니다!" : "완료를 취소했습니다.");
    }

    private void add() {
        String course = askCourse("없음", "");
        int c = askChoice("종류 (1: 할 일, 2: 과제, 3: 시험, Enter: 할 일): ", 1, 3);
        Category category = c < 0 ? Category.TODO : Category.values()[c - 1];
        String title = ask("내용: ");

        LocalDate today = LocalDate.now();
        LocalDate date;
        LocalDate dueDate = null;
        if (category == Category.ASSIGNMENT) {
            dueDate = askDate("마감일 (예: 11/20, Enter: 오늘): ", today);
            date = askDate("할 날 (Enter: 마감일과 같게): ", dueDate);
        } else {
            String label = category == Category.EXAM ? "시험 날" : "할 날";
            date = askDate(label + " (예: 11/20, Enter: 오늘): ", today);
        }
        Task task = manager.add(title, course, category, date, dueDate);
        System.out.println("추가했습니다: " + describe(task));
    }

    private void editOrDelete() {
        Task task = manager.get(askNumber("번호(#): "));
        System.out.println("  " + describe(task));
        int choice = askChoice("1. 수정  2. 삭제 (Enter: 취소): ", 1, 2);
        if (choice == 1) edit(task);
        if (choice == 2) delete(task);
    }

    private void edit(Task task) {
        System.out.println("Enter를 누르면 기존 값을 그대로 둡니다.");
        String course = askCourse("[" + courseName(task.getCourse()) + "]", task.getCourse());
        int c = askChoice("종류 (1: 할 일, 2: 과제, 3: 시험, Enter: [" + task.getCategory() + "]): ", 1, 3);
        Category category = c < 0 ? task.getCategory() : Category.values()[c - 1];
        String title = ask("내용 [" + task.getTitle() + "]: ");
        if (title.isEmpty()) title = task.getTitle();

        // 과제·시험은 기존 마감일, 할 일은 기존 할 날을 기본값으로 쓴다.
        LocalDate oldDeadline = task.getDeadline() != null ? task.getDeadline() : task.getDate();
        LocalDate date;
        LocalDate dueDate = null;
        if (category == Category.ASSIGNMENT) {
            dueDate = askDate("마감일 [" + DATE.format(oldDeadline) + "]: ", oldDeadline);
            date = askDate("할 날 [" + DATE.format(task.getDate()) + "]: ", task.getDate());
        } else if (category == Category.EXAM) {
            date = askDate("시험 날 [" + DATE.format(oldDeadline) + "]: ", oldDeadline);
        } else {
            date = askDate("할 날 [" + DATE.format(task.getDate()) + "]: ", task.getDate());
        }
        manager.update(task.getId(), title, course, category, date, dueDate);
        System.out.println("수정했습니다: " + describe(task));
    }

    private void delete(Task task) {
        if (ask("'" + task.getTitle() + "'을(를) 삭제할까요? (y/n): ").equalsIgnoreCase("y")) {
            manager.delete(task.getId());
            System.out.println("삭제했습니다.");
        }
    }

    /** 날짜를 입력하면 그날 목록, Enter면 전체 목록 */
    private void showList() {
        LocalDate date = askDate("날짜 (예: 11/20, Enter: 전체): ", null);
        printList(date == null ? manager.getAll() : manager.getByDate(date));
    }

    private void search() {
        System.out.println("원하는 조건만 고르세요. (Enter: 상관없음)");
        String course = askCourse("상관없음", null);
        int c = askChoice("종류 (1: 할 일, 2: 과제, 3: 시험): ", 1, 3);
        int d = askChoice("상태 (1: 미완료, 2: 완료): ", 1, 2);
        int w = askChoice("기간 (1: 지난 일, 2: 오늘, 3: 앞으로 일): ", 1, 3);

        Category category = c < 0 ? null : Category.values()[c - 1];
        Boolean done = d < 0 ? null : d == 2;
        Integer when = w < 0 ? null : w - 2; // 1 → -1(지난 일), 2 → 0(오늘), 3 → 1(앞으로 일)

        List<Task> result = manager.search(course, category, done, when, LocalDate.now());
        System.out.println("[검색 결과 " + result.size() + "건]");
        printList(result);
    }

    private void manageCourses() {
        List<String> courses = new ArrayList<>(manager.getCourses());
        System.out.println("[과목] " + (courses.isEmpty() ? "(없음)" : numbered(courses)));
        int choice = askChoice("1. 추가  2. 이름 수정  3. 삭제 (Enter: 돌아가기): ", 1, 3);
        if (choice == 1) {
            manager.addCourse(ask("새 과목 이름: "));
            System.out.println("추가했습니다.");
        } else if (choice == 2 || choice == 3) {
            if (courses.isEmpty()) throw new IllegalArgumentException("등록된 과목이 없습니다.");
            int n = askChoice("과목 번호 (Enter: 취소): ", 1, courses.size());
            if (n < 0) return;
            String name = courses.get(n - 1);
            if (choice == 2) {
                manager.renameCourse(name, ask("새 이름 [" + name + "]: "));
                System.out.println("이름을 바꿨습니다.");
            } else {
                int count = manager.getByCourse(name).size();
                if (count > 0) System.out.println("주의: '" + name + "' 과목의 항목 " + count + "개도 함께 삭제됩니다.");
                if (ask("'" + name + "' 과목을 삭제할까요? (y/n): ").equalsIgnoreCase("y")) {
                    manager.deleteCourse(name);
                    System.out.println("삭제했습니다.");
                }
            }
        }
    }

    private void save() {
        try {
            store.save(manager.getAll(), manager.getCourses());
        } catch (IOException e) {
            System.out.println("저장 실패: " + e.getMessage());
        }
    }

    // ======================= 출력 =======================

    /** 목록 + 완료율 */
    private void printList(List<Task> list) {
        printItems(list);
        if (list.isEmpty()) return;
        int done = TaskManager.countDone(list);
        System.out.println("  → 완료 " + done + "/" + list.size() + " (" + TaskManager.percent(done, list.size()) + "%)");
    }

    private void printItems(List<Task> list) {
        if (list.isEmpty()) System.out.println("  (없음)");
        for (Task task : list) System.out.println("  " + describe(task));
    }

    /** "#3 [ ] 11/18 (수) [과제] 자료구조 - 연결리스트 구현  (마감 11/20 · D-2)" */
    private String describe(Task task) {
        LocalDate today = LocalDate.now();
        String mark = task.isDone() ? "[V]" : task.isOverdue(today) ? "[X]" : "[ ]";
        String course = task.getCourse().isEmpty() ? "" : task.getCourse() + " - ";
        return "#" + task.getId() + " " + mark + " " + DATE.format(task.getDate()) + " [" + task.getCategory() + "] "
                + course + task.getTitle() + ddayText(task, today);
    }

    /** 끝나지 않은 과제·시험의 D-day 표시 */
    private String ddayText(Task task, LocalDate today) {
        if (!task.getCategory().isDeadline() || task.isDone()) return "";
        boolean isToday = task.daysLeft(today) == 0;
        if (task.getCategory() == Category.EXAM) return isToday ? "  (오늘 시험!)" : "  (" + task.getDday(today) + ")";
        return isToday ? "  (오늘 마감!)" : "  (마감 " + SHORT.format(task.getDeadline()) + " · " + task.getDday(today) + ")";
    }

    /** "1: 자료구조  2: 운영체제" */
    private String numbered(List<String> items) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < items.size(); i++) text.append(i + 1).append(": ").append(items.get(i)).append("  ");
        return text.toString().trim();
    }

    private String courseName(String course) {
        return course.isEmpty() ? "없음" : course;
    }

    // ======================= 입력 (잘못 입력하면 다시 묻는다) =======================

    private String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) { // 입력이 끊기면(Ctrl+D 등) 저장하고 끝낸다.
            save();
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private int askNumber(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(ask(prompt));
            } catch (NumberFormatException e) {
                System.out.println("숫자를 입력해 주세요.");
            }
        }
    }

    /** min~max 중 하나를 고른다. Enter면 -1 */
    private int askChoice(String prompt, int min, int max) {
        while (true) {
            String value = ask(prompt);
            if (value.isEmpty()) return -1;
            try {
                int n = Integer.parseInt(value);
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) {
                // 아래 안내 후 다시 묻는다.
            }
            System.out.println(min + "~" + max + " 중에서 골라 주세요.");
        }
    }

    /** 과목을 번호로 고른다. 0 = 과목 없음, Enter = defaultValue */
    private String askCourse(String enterLabel, String defaultValue) {
        List<String> courses = new ArrayList<>(manager.getCourses());
        String list = courses.isEmpty() ? "" : numbered(courses) + "  ";
        int n = askChoice("과목 (" + list + "0: 없음, Enter: " + enterLabel + "): ", 0, courses.size());
        if (n < 0) return defaultValue;
        return n == 0 ? "" : courses.get(n - 1);
    }

    private LocalDate askDate(String prompt, LocalDate defaultValue) {
        while (true) {
            String value = ask(prompt);
            if (value.isEmpty()) return defaultValue;
            LocalDate date = TaskManager.parseDate(value, LocalDate.now());
            if (date != null) return date;
            System.out.println("날짜는 11/20처럼 월/일로 입력해 주세요.");
        }
    }
}
