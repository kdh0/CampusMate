package campusmate.console;

import campusmate.model.Course;
import campusmate.model.Deadline;
import campusmate.model.DeadlineType;
import campusmate.model.TodoItem;
import campusmate.model.TodoStatus;
import campusmate.service.PlannerService;
import campusmate.storage.FileRepository;
import java.io.IOException;
import java.io.PrintStream;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

/** 사용자 입력과 화면 출력만 담당한다. 실제 처리는 PlannerService에 맡긴다. */
public class ConsoleUI {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", Locale.KOREAN);
    private static final String DATE_HELP = "Enter: 오늘, +1: 내일, -1: 어제, 11/20 또는 2026-11-20";

    private final PlannerService service;
    private final FileRepository repository;
    private final Scanner input;
    private final PrintStream out;

    public ConsoleUI(PlannerService service, FileRepository repository, Scanner input, PrintStream out) {
        this.service = service;
        this.repository = repository;
        this.input = input;
        this.out = out;
    }

    public void run() {
        out.println("\n=== CampusMate | 대학생 하루 플래너 ===");
        out.println("저장 위치: " + repository.getPath());
        try {
            handleOverdue();
            showToday();
            while (true) {
                printMenu();
                try {
                    switch (readInt("선택: ", 0, 8, null)) {
                        case 1: checkToday(); break;
                        case 2: addTodos(); break;
                        case 3: editTodo(); break;
                        case 4: viewDate(); break;
                        case 5: history(); break;
                        case 6: search(); break;
                        case 7: deadlineMenu(); break;
                        case 8: courseMenu(); break;
                        case 0:
                            if (save()) {
                                out.println("CampusMate를 종료합니다. 내일도 화이팅!");
                                return;
                            }
                            out.println("저장 위치를 확인한 뒤 다시 종료해 주세요.");
                            break;
                        default: break;
                    }
                } catch (IllegalArgumentException e) {
                    // 서비스/모델에서 값 검사에 실패하면 메시지만 보여 주고 메뉴로 돌아간다.
                    out.println("안내: " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            out.println("\n입력이 종료되어 현재 데이터를 저장합니다.");
            save();
        }
    }

    private void printMenu() {
        LocalDate today = today();
        List<TodoItem> todos = service.getTodos(today);
        out.println("\n------------------------------------------------------------");
        out.println("[" + SHORT_DATE.format(today) + "] 오늘 할 일 " + countDone(todos) + "/" + todos.size()
                + " 완료 · 연속 달성 " + service.getStreak(today) + "일");
        out.println("1. 오늘 할 일 체크     2. 할 일 추가        3. 할 일 수정/삭제");
        out.println("4. 날짜별 계획 보기    5. 전체 기록         6. 조건 검색");
        out.println("7. 마감 일정 관리      8. 과목 관리         0. 저장 후 종료");
    }

    // ======================= 시작 화면 =======================

    /** 지난 날짜에 끝내지 못한 할 일이 있으면 오늘로 옮길지 묻는다. */
    private void handleOverdue() {
        LocalDate today = today();
        List<TodoItem> overdue = service.getOverdueTodos(today);
        if (overdue.isEmpty()) return;

        out.println("\n[지난 계획 중 끝내지 못한 할 일이 " + overdue.size() + "개 있습니다]");
        for (int i = 0; i < overdue.size(); i++) {
            TodoItem todo = overdue.get(i);
            out.println(" " + (i + 1) + ". " + SHORT_DATE.format(todo.getDate()) + "  " + describeTodo(todo));
        }
        int choice = readInt("1. 모두 오늘로 옮기기  2. 하나씩 고르기  3. 옮기지 않기(못함으로 기록): ", 1, 3, null);

        int moved = 0;
        for (TodoItem todo : overdue) {
            boolean move = choice == 1
                    || (choice == 2 && confirm("'" + todo.getContent() + "' 오늘로 옮길까요?"));
            if (move) {
                service.postponeToToday(todo.getId(), today);
                moved++;
            } else {
                service.markMissed(todo.getId());
            }
        }
        out.println(moved + "개를 오늘 할 일로 옮겼습니다."
                + (moved < overdue.size() ? " 나머지 " + (overdue.size() - moved) + "개는 '못함'으로 기록했습니다." : ""));
        save();
    }

    private void showToday() {
        LocalDate today = today();
        out.println("\n===== " + FULL_DATE.format(today) + " 오늘 할 일 =====");
        List<TodoItem> todos = service.getTodos(today);
        if (todos.isEmpty()) {
            out.println("아직 오늘 계획이 없습니다. 2번 메뉴에서 할 일을 적어 보세요.");
        } else {
            printTodoList(todos);
            printProgress(todos);
        }
        List<Deadline> upcoming = service.getUpcomingDeadlines(today);
        if (!upcoming.isEmpty()) {
            out.println("\n[다가오는 마감 (" + PlannerService.DEADLINE_ALERT_DAYS + "일 이내)]");
            for (Deadline deadline : upcoming) out.println("  " + describeDeadline(deadline));
        }
        if (service.getTodos(today.plusDays(1)).isEmpty()) {
            out.println("\n※ 내일 계획이 아직 없습니다. 미리 적어 두면 좋아요. (2번 메뉴, 날짜에 +1)");
        }
    }

    // ======================= 할 일 =======================

    /** 1. 오늘 할 일 목록에서 번호를 골라 완료 ↔ 미완료를 바꾼다. */
    private void checkToday() {
        LocalDate today = today();
        while (true) {
            List<TodoItem> todos = service.getTodos(today);
            if (todos.isEmpty()) {
                out.println("오늘 할 일이 없습니다. 2번 메뉴에서 추가해 주세요.");
                return;
            }
            out.println("\n[오늘 할 일]");
            printTodoList(todos);
            printProgress(todos);
            int number = readInt("완료/취소할 번호 (Enter: 돌아가기): ", 0, todos.size(), 0);
            if (number == 0) return;

            TodoItem todo = todos.get(number - 1);
            boolean done = service.toggleTodo(todo.getId());
            out.println("'" + todo.getContent() + "' → " + (done ? "완료!" : "완료 취소"));
            save();
            if (done && countDone(service.getTodos(today)) == todos.size()) {
                out.println("*** 오늘 할 일을 모두 끝냈습니다! 연속 달성 " + service.getStreak(today) + "일 ***");
            }
        }
    }

    /** 2. 날짜를 하나 고르고, 그 날짜에 할 일을 여러 개 이어서 적는다. */
    private void addTodos() {
        LocalDate date = readDate("날짜 (" + DATE_HELP + "): ", today());
        List<Deadline> openDeadlines = service.getOpenDeadlines();
        List<Course> courses = service.getCourses();

        out.println("\n" + FULL_DATE.format(date) + "에 할 일을 추가합니다. 내용 없이 Enter를 누르면 끝납니다.");
        printLinkChoices(openDeadlines, courses);

        int added = 0;
        while (true) {
            String content = readLine((added + 1) + "번째 할 일: ");
            if (content.isEmpty()) break;
            int deadlineId = askDeadline(openDeadlines);
            int courseId = deadlineId == 0 ? askCourse(courses) : 0;
            service.addTodo(date, content, courseId, deadlineId);
            added++;
            save();
        }
        if (added > 0) {
            out.println(added + "개를 추가했습니다.");
            printDay(date);
        }
    }

    /** 3. 날짜 → 번호를 골라 수정, 삭제, 완료 변경을 한다. (지난 날 기록도 고칠 수 있다) */
    private void editTodo() {
        LocalDate date = readDate("날짜 (" + DATE_HELP + "): ", today());
        List<TodoItem> todos = service.getTodos(date);
        if (todos.isEmpty()) {
            out.println(SHORT_DATE.format(date) + "에는 할 일이 없습니다.");
            return;
        }
        out.println("\n[" + FULL_DATE.format(date) + "]");
        printTodoList(todos);
        int number = readInt("번호 (Enter: 취소): ", 0, todos.size(), 0);
        if (number == 0) return;
        TodoItem todo = todos.get(number - 1);

        int action = readInt("1. 내용/날짜/연결 수정  2. 삭제  3. 완료/미완료 변경  (Enter: 취소): ", 0, 3, 0);
        if (action == 1) {
            out.println("Enter를 누르면 기존 값을 유지합니다.");
            String content = readText("내용 [" + todo.getContent() + "]: ", todo.getContent());
            LocalDate newDate = readDate("날짜 [" + todo.getDate() + "]: ", todo.getDate());
            int deadlineId = todo.getDeadlineId();
            int courseId = todo.getCourseId();
            out.println("현재 연결: " + (linkText(todo).isEmpty() ? "없음" : linkText(todo).trim()));
            if (confirm("과목/마감 일정 연결을 바꿀까요?")) {
                List<Deadline> openDeadlines = service.getOpenDeadlines();
                List<Course> courses = service.getCourses();
                printLinkChoices(openDeadlines, courses);
                deadlineId = askDeadline(openDeadlines);
                courseId = deadlineId == 0 ? askCourse(courses) : 0;
            }
            service.updateTodo(todo.getId(), newDate, content, courseId, deadlineId);
            out.println("수정했습니다.");
        } else if (action == 2) {
            if (!confirm("'" + todo.getContent() + "' 할 일을 삭제할까요?")) return;
            service.deleteTodo(todo.getId());
            out.println("삭제했습니다.");
        } else if (action == 3) {
            boolean done = service.toggleTodo(todo.getId());
            out.println("'" + todo.getContent() + "' → " + (done ? "완료" : "미완료"));
        } else {
            return;
        }
        save();
    }

    /** 4. 원하는 날짜의 계획을 본다. */
    private void viewDate() {
        printDay(readDate("볼 날짜 (" + DATE_HELP + "): ", today()));
    }

    private void printDay(LocalDate date) {
        List<TodoItem> todos = service.getTodos(date);
        out.println("\n[" + FULL_DATE.format(date) + "]");
        if (todos.isEmpty()) {
            out.println("계획한 할 일이 없습니다.");
        } else {
            printTodoList(todos);
            printProgress(todos);
        }
        for (Deadline deadline : service.getDeadlines()) {
            if (deadline.getDueDate().equals(date)) out.println("이 날 마감: " + describeDeadline(deadline));
        }
    }

    /** 5. 날짜별 기록. 끝내지 못한 일은 [X] 못함, 다른 날로 옮긴 일은 [>] 미룸으로 보인다. */
    private void history() {
        LocalDate today = today();
        List<LocalDate> dates = service.getPlanDates();
        if (dates.isEmpty()) {
            out.println("아직 기록이 없습니다. 2번 메뉴에서 할 일을 적어 보세요.");
            return;
        }
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        out.println("\n[전체 기록]");
        out.println("지금까지 달성률 " + rateText(null, today) + " | 이번 주 " + rateText(monday, today)
                + " | 연속 달성 " + service.getStreak(today) + "일");
        out.println("표시: [V] 완료  [X] 못함  [>] 다른 날로 미룸  [ ] 예정");

        Set<LocalDate> achieved = service.getAchievedDays();
        for (LocalDate date : dates) {
            List<TodoItem> todos = service.getTodos(date);
            int done = countDone(todos);
            String note = "";
            if (achieved.contains(date)) note = "  ★ 모두 달성";
            if (date.equals(today)) note += "  ← 오늘";
            out.println("\n-- " + SHORT_DATE.format(date) + "  " + done + "/" + todos.size()
                    + " (" + percent(done, todos.size()) + "%)" + note);
            for (TodoItem todo : todos) {
                out.println("   " + todo.getDisplayStatus(today).getMark() + " " + describeTodo(todo));
            }
        }
    }

    /** 6. 조건 검색: 검색어 → (해당되는 과목만) 과목 → 기간 → 상태. 원하는 조건만 입력한다. */
    private void search() {
        LocalDate today = today();
        out.println("\n[조건 검색] 원하는 조건만 입력하고, 상관없는 조건은 Enter로 넘어가세요.");
        String keyword = readLine("찾을 단어 (할 일 내용·과목명·마감 일정 제목에서 찾음 / Enter: 건너뛰기): ");

        List<TodoItem> matched = service.searchTodos(keyword, null, null, null, null, today);
        if (matched.isEmpty()) {
            out.println(keyword.isEmpty() ? "기록된 할 일이 없습니다." : "'" + keyword + "'에 해당하는 할 일이 없습니다.");
            return;
        }

        // 검색어에 맞는 할 일이 있는 과목만 선택지로 보여 준다. (Map: 과목 ID → 해당 할 일 수)
        Map<Integer, Integer> countByCourse = new HashMap<>();
        for (TodoItem todo : matched) {
            int id = todo.getCourseId();
            if (id != 0) countByCourse.put(id, countByCourse.getOrDefault(id, 0) + 1);
        }
        Integer courseId = null;
        if (!countByCourse.isEmpty()) {
            List<Course> choices = new ArrayList<>();
            out.println("\n[선택할 수 있는 과목]");
            for (Course course : service.getCourses()) {
                Integer count = countByCourse.get(course.getId());
                if (count == null) continue;
                choices.add(course);
                out.println(choices.size() + ". " + course.getName() + " (해당 할 일 " + count + "개)");
            }
            int number = readInt("과목 번호 (Enter: 전체): ", 0, choices.size(), 0);
            if (number > 0) courseId = choices.get(number - 1).getId();
        }

        LocalDate from = readOptionalDate("시작 날짜 (Enter: 처음부터, 예: -7 = 일주일 전): ");
        LocalDate to = readOptionalDate("끝 날짜 (Enter: 끝까지, 예: 오늘, 11/20): ");
        if (from != null && to != null && from.isAfter(to)) {
            out.println("시작 날짜가 끝 날짜보다 늦습니다. 다시 검색해 주세요.");
            return;
        }
        int state = readInt("상태 (1: 예정, 2: 완료, 3: 못함, Enter: 전체): ", 0, 3, 0);
        TodoStatus status = null;
        if (state == 1) status = TodoStatus.PLANNED;
        if (state == 2) status = TodoStatus.DONE;
        if (state == 3) status = TodoStatus.MISSED;

        List<TodoItem> result = service.searchTodos(keyword, courseId, from, to, status, today);
        out.println("\n[검색 결과 " + result.size() + "건 · 완료 " + countDone(result) + "건]");
        if (result.isEmpty()) out.println("조건에 맞는 할 일이 없습니다.");
        for (TodoItem todo : result) {
            out.println(SHORT_DATE.format(todo.getDate()) + "  " + todo.getDisplayStatus(today).getMark()
                    + " " + describeTodo(todo));
        }
    }

    // ======================= 마감 일정 =======================

    private void deadlineMenu() {
        printDeadlines();
        out.println("1. 추가  2. 수정  3. 완료/미완료 변경  4. 삭제  0. 돌아가기");
        int menu = readInt("선택: ", 0, 4, null);
        if (menu == 0) return;

        if (menu == 1) {
            List<Course> courses = service.getCourses();
            if (courses.isEmpty()) {
                out.println("먼저 8번 메뉴에서 과목을 등록해 주세요.");
                return;
            }
            printCourses(courses);
            int courseId = courses.get(readInt("과목 번호: ", 1, courses.size(), null) - 1).getId();
            DeadlineType type = readType(null);
            String title = readText("제목: ", null);
            LocalDate dueDate = readDate("마감일 (예: 11/20, +7 = 일주일 뒤): ", null);
            Deadline deadline = service.addDeadline(courseId, type, title, dueDate);
            out.println("마감 일정을 등록했습니다. (" + deadline.getDday(today()) + ")");
            out.println("팁: 2번 메뉴에서 할 일을 추가할 때 이 마감 일정을 연결하면 진행 상황을 볼 수 있어요.");
        } else {
            List<Deadline> deadlines = service.getDeadlines();
            if (deadlines.isEmpty()) return;
            Deadline deadline = deadlines.get(readInt("번호: ", 1, deadlines.size(), null) - 1);
            if (menu == 2) {
                out.println("Enter를 누르면 기존 값을 유지합니다.");
                List<Course> courses = service.getCourses();
                printCourses(courses);
                int current = courses.indexOf(service.requireCourse(deadline.getCourseId())) + 1;
                int courseId = courses.get(readInt("과목 번호 [" + current + "]: ", 1, courses.size(), current) - 1).getId();
                DeadlineType type = readType(deadline.getType());
                String title = readText("제목 [" + deadline.getTitle() + "]: ", deadline.getTitle());
                LocalDate dueDate = readDate("마감일 [" + deadline.getDueDate() + "]: ", deadline.getDueDate());
                service.updateDeadline(deadline.getId(), courseId, type, title, dueDate);
                out.println("수정했습니다.");
            } else if (menu == 3) {
                boolean done = service.toggleDeadline(deadline.getId());
                out.println("'" + deadline.getTitle() + "' → " + (done ? "완료" : "미완료"));
            } else {
                int linked = service.countLinkedTodos(deadline.getId());
                String warn = linked > 0 ? " (연결된 할 일 " + linked + "개는 남고 연결만 끊어집니다)" : "";
                if (!confirm("'" + deadline.getTitle() + "' 마감 일정을 삭제할까요?" + warn)) return;
                service.deleteDeadline(deadline.getId());
                out.println("삭제했습니다.");
            }
        }
        save();
    }

    private void printDeadlines() {
        out.println("\n[마감 일정]");
        List<Deadline> deadlines = service.getDeadlines();
        if (deadlines.isEmpty()) out.println("등록된 마감 일정이 없습니다.");
        for (int i = 0; i < deadlines.size(); i++) {
            out.println((i + 1) + ". " + describeDeadline(deadlines.get(i)));
        }
    }

    // ======================= 과목 =======================

    private void courseMenu() {
        List<Course> courses = service.getCourses();
        printCourses(courses);
        out.println("1. 등록  2. 이름 수정  3. 삭제  0. 돌아가기");
        int menu = readInt("선택: ", 0, 3, null);
        if (menu == 0) return;
        if (menu == 1) {
            Course course = service.addCourse(readText("과목 이름: ", null));
            out.println("'" + course.getName() + "' 과목을 등록했습니다.");
        } else {
            if (courses.isEmpty()) return;
            Course course = courses.get(readInt("과목 번호: ", 1, courses.size(), null) - 1);
            if (menu == 2) {
                service.renameCourse(course.getId(), readText("새 이름 [" + course.getName() + "]: ", course.getName()));
                out.println("과목 이름을 수정했습니다.");
            } else {
                if (!confirm("'" + course.getName() + "' 과목을 삭제할까요?")) return;
                service.deleteCourse(course.getId());
                out.println("과목을 삭제했습니다.");
            }
        }
        save();
    }

    private void printCourses(List<Course> courses) {
        out.println("\n[수강 과목]");
        if (courses.isEmpty()) out.println("등록된 과목이 없습니다.");
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            out.println((i + 1) + ". " + course.getName() + " (마감 일정 " + service.countDeadlinesOfCourse(course.getId())
                    + "개, 할 일 " + service.countTodosOfCourse(course.getId()) + "개)");
        }
    }

    // ======================= 할 일 ↔ 과목/마감 일정 연결 =======================

    private void printLinkChoices(List<Deadline> openDeadlines, List<Course> courses) {
        if (!openDeadlines.isEmpty()) {
            out.println("[연결할 수 있는 마감 일정]");
            for (int i = 0; i < openDeadlines.size(); i++) {
                Deadline d = openDeadlines.get(i);
                out.println("  " + (i + 1) + ") " + service.getCourseName(d.getCourseId()) + " · " + d.getTitle()
                        + " [" + d.getDday(today()) + "]");
            }
        }
        if (!courses.isEmpty()) {
            StringBuilder sb = new StringBuilder("[과목] ");
            for (int i = 0; i < courses.size(); i++) {
                sb.append(i + 1).append(") ").append(courses.get(i).getName()).append("  ");
            }
            out.println(sb.toString().trim());
        }
    }

    /** 마감 일정 번호를 묻는다. 고르지 않으면 0. */
    private int askDeadline(List<Deadline> openDeadlines) {
        if (openDeadlines.isEmpty()) return 0;
        int number = readInt("  └ 관련 마감 일정 번호 (Enter: 없음): ", 0, openDeadlines.size(), 0);
        return number == 0 ? 0 : openDeadlines.get(number - 1).getId();
    }

    /** 과목 번호를 묻는다. 고르지 않으면 0. */
    private int askCourse(List<Course> courses) {
        if (courses.isEmpty()) return 0;
        int number = readInt("  └ 과목 번호 (Enter: 없음): ", 0, courses.size(), 0);
        return number == 0 ? 0 : courses.get(number - 1).getId();
    }

    // ======================= 출력 도우미 =======================

    private void printTodoList(List<TodoItem> todos) {
        LocalDate today = today();
        for (int i = 0; i < todos.size(); i++) {
            TodoItem todo = todos.get(i);
            out.println(" " + (i + 1) + ". " + todo.getDisplayStatus(today).getMark() + " " + describeTodo(todo));
        }
    }

    private void printProgress(List<TodoItem> todos) {
        int done = countDone(todos);
        out.println("진행: " + bar(done, todos.size()) + " " + done + "/" + todos.size()
                + " (" + percent(done, todos.size()) + "%)");
    }

    /** "연결리스트 구현  <자료구조 · 연결리스트 과제>  (미룬 일 1회)" */
    private String describeTodo(TodoItem todo) {
        String text = todo.getContent() + linkText(todo);
        if (todo.getPostponeCount() > 0) text += "  (미룬 일 " + todo.getPostponeCount() + "회)";
        return text;
    }

    private String linkText(TodoItem todo) {
        if (todo.getDeadlineId() != 0) {
            Deadline deadline = service.requireDeadline(todo.getDeadlineId());
            return "  <" + service.getCourseName(deadline.getCourseId()) + " · " + deadline.getTitle() + ">";
        }
        if (todo.getCourseId() != 0) return "  <" + service.getCourseName(todo.getCourseId()) + ">";
        return "";
    }

    /** "[D-7] 과제 | 자료구조 | 연결리스트 구현 (11/20 (금) 마감) · 관련 할 일 2/5 완료" */
    private String describeDeadline(Deadline deadline) {
        String state = deadline.isCompleted() ? "[완료]" : "[" + deadline.getDday(today()) + "]";
        int total = service.countLinkedTodos(deadline.getId());
        String progress = total == 0 ? ""
                : " · 관련 할 일 " + service.countLinkedDone(deadline.getId()) + "/" + total + " 완료";
        return state + " " + deadline.getType().getLabel() + " | " + service.getCourseName(deadline.getCourseId())
                + " | " + deadline.getTitle() + " (" + SHORT_DATE.format(deadline.getDueDate()) + " 마감)" + progress;
    }

    private String rateText(LocalDate from, LocalDate to) {
        int total = service.countTodos(from, to);
        int done = service.countDone(from, to);
        return percent(done, total) + "% (" + done + "/" + total + ")";
    }

    private int countDone(List<TodoItem> todos) {
        int count = 0;
        for (TodoItem todo : todos) if (todo.isDone()) count++;
        return count;
    }

    private int percent(int done, int total) { return total == 0 ? 0 : done * 100 / total; }

    private String bar(int done, int total) {
        int filled = total == 0 ? 0 : done * 10 / total;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 10; i++) sb.append(i < filled ? '#' : '-');
        return sb.append(']').toString();
    }

    private LocalDate today() { return LocalDate.now(); }

    private boolean save() {
        try {
            repository.save(service);
            return true;
        } catch (IOException e) {
            out.println("저장 실패: " + e.getMessage());
            out.println("변경 사항이 아직 파일에 반영되지 않았습니다. 저장 경로와 쓰기 권한을 확인해 주세요.");
            return false;
        }
    }

    // ======================= 입력 도우미 (잘못된 입력은 다시 묻는다) =======================

    private String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        if (!input.hasNextLine()) throw new NoSuchElementException("입력 종료");
        return input.nextLine().trim();
    }

    private String readText(String prompt, String fallback) {
        while (true) {
            String value = readLine(prompt);
            if (!value.isEmpty()) return value;
            if (fallback != null) return fallback;
            out.println("내용을 입력해 주세요.");
        }
    }

    private int readInt(String prompt, int min, int max, Integer fallback) {
        while (true) {
            String value = readLine(prompt);
            if (value.isEmpty() && fallback != null) return fallback;
            try {
                int number = Integer.parseInt(value);
                if (number >= min && number <= max) return number;
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 아래 안내 후 다시 입력받는다.
            }
            out.println(min + "~" + max + " 범위의 숫자를 입력해 주세요.");
        }
    }

    /** 날짜 입력. Enter를 누르면 fallback(없으면 다시 묻기). */
    private LocalDate readDate(String prompt, LocalDate fallback) {
        while (true) {
            String value = readLine(prompt);
            if (value.isEmpty() && fallback != null) return fallback;
            LocalDate date = parseDate(value);
            if (date != null) return date;
            out.println("날짜 형식: 오늘, 내일, 어제, +3, -1, 11/20, 2026-11-20");
        }
    }

    /** 생략할 수 있는 날짜 입력. Enter를 누르면 null. */
    private LocalDate readOptionalDate(String prompt) {
        while (true) {
            String value = readLine(prompt);
            if (value.isEmpty()) return null;
            LocalDate date = parseDate(value);
            if (date != null) return date;
            out.println("날짜 형식: 오늘, 내일, 어제, +3, -1, 11/20, 2026-11-20");
        }
    }

    /** 여러 형식의 날짜를 해석한다. 해석할 수 없으면 null. */
    private LocalDate parseDate(String text) {
        LocalDate today = today();
        try {
            if (text.equals("오늘")) return today;
            if (text.equals("내일")) return today.plusDays(1);
            if (text.equals("어제")) return today.minusDays(1);
            if (text.startsWith("+") || text.startsWith("-")) {
                return today.plusDays(Integer.parseInt(text));      // +3 → 3일 뒤, -1 → 어제
            }
            if (text.matches("\\d{1,2}/\\d{1,2}")) {                 // 11/20 → 올해 11월 20일
                String[] parts = text.split("/");
                return LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            return LocalDate.parse(text);                            // 2026-11-20
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private DeadlineType readType(DeadlineType fallback) {
        String current = fallback == null ? "" : " [현재: " + fallback.getLabel() + "]";
        int value = readInt("종류 (1: 과제, 2: 시험, 3: 기타)" + current + ": ",
                1, 3, fallback == null ? null : fallback.ordinal() + 1);
        return DeadlineType.values()[value - 1];
    }

    private boolean confirm(String prompt) {
        return readLine(prompt + " (y/n): ").equalsIgnoreCase("y");
    }
}
