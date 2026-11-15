package campusmate.console;

import campusmate.model.Category;
import campusmate.model.Task;
import campusmate.service.TaskManager;
import campusmate.storage.FileStore;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** 1단계 콘솔 버전. 메뉴로 입력을 받고, 실제 처리는 TaskManager에 맡긴다. */
public class ConsoleApp {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN);

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
            manager = new TaskManager(store.load());
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
                    case "1" -> printList(manager.getAll());
                    case "2" -> showDate();
                    case "3" -> add();
                    case "4" -> edit();
                    case "5" -> delete();
                    case "6" -> toggle();
                    case "7" -> search();
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

    private void printMenu() {
        List<Task> today = manager.getByDate(LocalDate.now());
        System.out.println("\n----------------------------------------------");
        System.out.println("오늘 " + DATE.format(LocalDate.now()) + " · 할 일 "
                + TaskManager.countDone(today) + "/" + today.size() + " 완료");
        System.out.println("1. 전체 목록   2. 날짜별 보기   3. 추가       4. 수정");
        System.out.println("5. 삭제        6. 완료 체크     7. 조건 검색  0. 저장 후 종료");
    }

    /** 시작할 때: 마감 임박 알림 → 지난 할 일 오늘로 옮기기 → 오늘 할 일 */
    private void startupCheck() {
        LocalDate today = LocalDate.now();
        List<Task> soon = manager.getUpcomingDeadlines(today);
        if (!soon.isEmpty()) {
            System.out.println("\n[마감 임박! " + TaskManager.ALERT_DAYS + "일 이내]");
            printList(soon);
        }
        List<Task> overdue = manager.getOverdueTodos(today);
        if (!overdue.isEmpty()) {
            System.out.println("\n[지난 날 못 한 할 일 " + overdue.size() + "개]");
            printList(overdue);
            if (ask("모두 오늘로 옮길까요? (y/n): ").equalsIgnoreCase("y")) {
                for (Task task : overdue) manager.moveToToday(task.getId(), today);
                System.out.println("오늘 할 일로 옮겼습니다.");
            }
        }
        System.out.println("\n[오늘 할 일]");
        printList(manager.getByDate(today));
    }

    // ======================= 메뉴 기능 =======================

    private void showDate() {
        LocalDate date = askDate("볼 날짜 (Enter: 오늘, 예: +1, 11/20): ", LocalDate.now());
        printList(manager.getByDate(date));
    }

    private void add() {
        String title = ask("내용: ");
        String course = ask("과목 (없으면 Enter): ");
        Category category = askCategory(Category.TODO);
        LocalDate date = askDate("날짜/마감일 (Enter: 오늘, 예: +1, 11/20): ", LocalDate.now());
        Task task = manager.add(title, course, category, date);
        System.out.println("추가했습니다: " + describe(task));
    }

    private void edit() {
        Task task = manager.get(askNumber("수정할 번호(#): "));
        System.out.println("Enter를 누르면 기존 값을 그대로 둡니다.");
        String title = askOr("내용 [" + task.getTitle() + "]: ", task.getTitle());
        String course = askOr("과목 [" + task.getCourse() + "]: ", task.getCourse());
        Category category = askCategory(task.getCategory());
        LocalDate date = askDate("날짜 [" + task.getDate() + "]: ", task.getDate());
        manager.update(task.getId(), title, course, category, date);
        System.out.println("수정했습니다: " + describe(task));
    }

    private void delete() {
        Task task = manager.get(askNumber("삭제할 번호(#): "));
        if (ask("'" + task.getTitle() + "'을(를) 삭제할까요? (y/n): ").equalsIgnoreCase("y")) {
            manager.delete(task.getId());
            System.out.println("삭제했습니다.");
        }
    }

    private void toggle() {
        int id = askNumber("완료/취소할 번호(#): ");
        boolean done = manager.toggleDone(id);
        System.out.println(done ? "완료했습니다!" : "완료를 취소했습니다.");
    }

    private void search() {
        System.out.println("원하는 조건만 입력하세요. (Enter: 전체)");
        String keyword = ask("검색어 (내용·과목): ");
        String c = ask("종류 (1: 할 일, 2: 과제, 3: 시험): ");
        String d = ask("상태 (1: 미완료, 2: 완료): ");

        Category category = c.matches("[1-3]") ? Category.values()[Integer.parseInt(c) - 1] : null;
        Boolean done = null;
        if (d.equals("1")) done = false;
        if (d.equals("2")) done = true;

        List<Task> result = manager.search(keyword, category, done);
        System.out.println("[검색 결과 " + result.size() + "건]");
        printList(result);
    }

    private void save() {
        try {
            store.save(manager.getAll());
        } catch (IOException e) {
            System.out.println("저장 실패: " + e.getMessage());
        }
    }

    // ======================= 출력 =======================

    private void printList(List<Task> list) {
        if (list.isEmpty()) {
            System.out.println("  (없음)");
            return;
        }
        for (Task task : list) System.out.println("  " + describe(task));
        int done = TaskManager.countDone(list);
        System.out.println("  → 완료 " + done + "/" + list.size() + " (" + TaskManager.percent(done, list.size()) + "%)");
    }

    /** "#3 [ ] 11/20 (금) [과제] 자료구조 - 연결리스트 구현  (D-4)" */
    private String describe(Task task) {
        LocalDate today = LocalDate.now();
        String mark = task.isDone() ? "[V]" : task.isOverdue(today) ? "[X]" : "[ ]";
        String course = task.getCourse().isEmpty() ? "" : task.getCourse() + " - ";
        String dday = task.getCategory().isDeadline() && !task.isDone() ? "  (" + task.getDday(today) + ")" : "";
        return "#" + task.getId() + " " + mark + " " + DATE.format(task.getDate()) + " [" + task.getCategory() + "] "
                + course + task.getTitle() + dday;
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

    private String askOr(String prompt, String defaultValue) {
        String value = ask(prompt);
        return value.isEmpty() ? defaultValue : value;
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

    private LocalDate askDate(String prompt, LocalDate defaultValue) {
        while (true) {
            String value = ask(prompt);
            if (value.isEmpty()) return defaultValue;
            LocalDate date = TaskManager.parseDate(value, LocalDate.now());
            if (date != null) return date;
            System.out.println("날짜 형식: 오늘, +1, -1, 11/20, 2026-11-20");
        }
    }

    private Category askCategory(Category defaultValue) {
        while (true) {
            String value = ask("종류 (1: 할 일, 2: 과제, 3: 시험, Enter: " + defaultValue + "): ");
            if (value.isEmpty()) return defaultValue;
            if (value.matches("[1-3]")) return Category.values()[Integer.parseInt(value) - 1];
            System.out.println("1~3 중에서 골라 주세요.");
        }
    }
}
