package campusmate.ui;

import campusmate.model.Course;
import campusmate.model.Task;
import campusmate.model.TaskType;
import campusmate.service.PlannerService;
import campusmate.storage.FileRepository;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** 사용자 입력과 화면 출력만 담당한다. 실제 처리는 PlannerService에 맡긴다. */
public class ConsoleUI {
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
        out.println("\n=== CampusMate | 스마트 대학생활 통합 플래너 ===");
        out.println("저장 위치: " + repository.getPath());
        dashboard();
        try {
            while (true) {
                out.println("\n1. 과목 관리       2. 전체 일정       3. 일정 추가");
                out.println("4. 일정 수정       5. 일정 삭제       6. 완료/미완료 변경");
                out.println("7. 조건 검색       8. 우선순위 추천   9. 학습 진행률");
                out.println("10. 대시보드       11. 수동 저장      0. 저장 후 종료");
                try {
                    switch (readInt("선택: ", 0, 11, null)) {
                        case 1: courseMenu(); break;
                        case 2: printTasks(service.search("", null, null, null), false); break;
                        case 3: addTask(); break;
                        case 4: editTask(); break;
                        case 5: deleteTask(); break;
                        case 6: changeCompletion(); break;
                        case 7: search(); break;
                        case 8: printTasks(service.recommendations(LocalDate.now()), true); break;
                        case 9: statistics(); break;
                        case 10: dashboard(); break;
                        case 11: save(); break;
                        case 0:
                            if (save()) {
                                out.println("CampusMate를 종료합니다.");
                                return;
                            }
                            out.println("저장 위치를 확인한 뒤 다시 종료해 주세요.");
                            break;
                        default: break;
                    }
                } catch (IllegalArgumentException e) {
                    out.println("안내: " + e.getMessage());
                }
            }
        } catch (NoSuchElementException e) {
            out.println("\n입력이 종료되어 현재 데이터를 저장합니다.");
            save();
        }
    }

    private void courseMenu() {
        printCourses();
        out.println("1. 등록  2. 이름 수정  3. 삭제  0. 돌아가기");
        int menu = readInt("선택: ", 0, 3, null);
        if (menu == 0) return;
        if (menu == 1) {
            Course course = service.addCourse(readText("과목 이름: ", null));
            out.println("과목 등록 완료 (ID: " + course.getId() + ")");
        } else {
            int id = readInt("과목 ID: ", 1, Integer.MAX_VALUE, null);
            Course course = service.requireCourse(id);
            if (menu == 2) {
                service.renameCourse(id, readText("새 이름 [Enter: 유지]: ", course.getName()));
            } else {
                if (!confirm("'" + course.getName() + "' 과목을 삭제할까요?")) return;
                service.deleteCourse(id);
            }
        }
        save();
    }

    private void printCourses() {
        out.println("\n[수강 과목]");
        if (service.getCourses().isEmpty()) out.println("등록된 과목이 없습니다.");
        for (Course course : service.getCourses()) {
            out.println(course.getId() + ". " + course.getName());
        }
    }

    private void addTask() {
        if (service.getCourses().isEmpty()) {
            out.println("먼저 1번 메뉴에서 과목을 등록해 주세요.");
            return;
        }
        printCourses();
        int courseId = readInt("과목 ID: ", 1, Integer.MAX_VALUE, null);
        service.requireCourse(courseId);
        TaskType type = readType(null);
        String title = readText("제목: ", null);
        String memo = readLine("메모 [생략 가능]: ");
        LocalDate dueDate = readDate("마감일 (예: 2026-11-20): ", null);
        int importance = readInt("중요도 (1: 낮음 ~ 5: 높음): ", 1, 5, null);
        Task task = service.addTask(courseId, type, title, memo, dueDate, importance);
        out.println("일정 등록 완료 (ID: " + task.getId() + ")");
        save();
    }

    private void editTask() {
        printTasks(service.search("", null, null, null), false);
        if (service.getTasks().isEmpty()) return;
        Task task = service.requireTask(readInt("수정할 일정 ID: ", 1, Integer.MAX_VALUE, null));
        out.println("Enter를 누르면 기존 값을 유지합니다.");
        printCourses();
        int courseId = readInt("과목 ID [" + task.getCourseId() + "]: ", 1, Integer.MAX_VALUE, task.getCourseId());
        service.requireCourse(courseId);
        TaskType type = readType(task.getType());
        String title = readText("제목 [" + task.getTitle() + "]: ", task.getTitle());
        String memo = readLine("메모 [" + task.getMemo() + ", '-' 입력 시 비우기]: ");
        memo = memo.isEmpty() ? task.getMemo() : memo.equals("-") ? "" : memo;
        LocalDate date = readDate("마감일 [" + task.getDueDate() + "]: ", task.getDueDate());
        int importance = readInt("중요도 [" + task.getImportance() + "]: ", 1, 5, task.getImportance());
        service.updateTask(task.getId(), courseId, type, title, memo, date, importance);
        out.println("수정했습니다.");
        save();
    }

    private void deleteTask() {
        printTasks(service.search("", null, null, null), false);
        if (service.getTasks().isEmpty()) return;
        Task task = service.requireTask(readInt("삭제할 일정 ID: ", 1, Integer.MAX_VALUE, null));
        if (confirm("'" + task.getTitle() + "' 일정을 삭제할까요?")) {
            service.deleteTask(task.getId());
            out.println("삭제했습니다.");
            save();
        }
    }

    private void changeCompletion() {
        printTasks(service.search("", null, null, null), false);
        if (service.getTasks().isEmpty()) return;
        Task task = service.requireTask(readInt("변경할 일정 ID: ", 1, Integer.MAX_VALUE, null));
        boolean completed = readInt("1. 완료  2. 미완료: ", 1, 2, null) == 1;
        service.setCompleted(task.getId(), completed);
        out.println(completed ? "완료 처리했습니다." : "미완료로 변경했습니다.");
        save();
    }

    private void search() {
        String keyword = readLine("검색어 (제목/메모/과목명, Enter: 전체): ");
        printCourses();
        int courseId = readInt("과목 ID (0 또는 Enter: 전체): ", 0, Integer.MAX_VALUE, 0);
        if (courseId != 0) service.requireCourse(courseId);
        int type = readInt("종류 (1: 과제, 2: 시험, 3: 공부 계획, 0 또는 Enter: 전체): ", 0, 3, 0);
        int status = readInt("상태 (1: 미완료, 2: 완료, 0 또는 Enter: 전체): ", 0, 2, 0);
        Boolean completed = status == 0 ? null : status == 2;
        printTasks(service.search(keyword, courseId == 0 ? null : courseId,
                type == 0 ? null : TaskType.values()[type - 1], completed), false);
    }

    private void dashboard() {
        LocalDate today = LocalDate.now();
        out.println("\n[오늘: " + today + "]");
        out.println("기한 지난 일정 / 오늘부터 3일 이내 마감 (미완료)");
        printTasks(service.upcoming(today), false);
        out.println("\n[지금 추천하는 일정 TOP 3]");
        List<Task> recommended = service.recommendations(today);
        printTasks(recommended.subList(0, Math.min(3, recommended.size())), true);
    }

    private void statistics() {
        out.println("\n[학습 진행률: 과제·시험·공부 계획 전체 기준]");
        printProgress("전체 일정", null, null);
        printProgress("전체 과제만", null, TaskType.ASSIGNMENT);
        for (Course course : service.getCourses()) {
            printProgress(course.getName() + " / 전체 일정", course.getId(), null);
            printProgress(course.getName() + " / 과제만", course.getId(), TaskType.ASSIGNMENT);
        }
    }

    private void printProgress(String label, Integer courseId, TaskType type) {
        int total = service.search("", courseId, type, null).size();
        int done = service.completedCount(courseId, type);
        double rate = service.completionRate(courseId, type);
        int filled = (int) (rate / 10);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? '#' : '-');
        bar.append(']');
        out.printf("%s %s %.1f%% | 완료 %d / 전체 %d / 남음 %d%n", label, bar, rate, done, total, total - done);
    }

    private void printTasks(List<Task> tasks, boolean showScore) {
        if (tasks.isEmpty()) {
            out.println("해당하는 일정이 없습니다.");
            return;
        }
        LocalDate today = LocalDate.now();
        for (Task task : tasks) {
            out.printf("#%d [%s] %s | %s | %s%n", task.getId(), task.isCompleted() ? "완료" : "미완료",
                    service.requireCourse(task.getCourseId()).getName(), task.getType().getLabel(), task.getTitle());
            out.printf("   마감 %s (%s) | 중요도 %d%s%n", task.getDueDate(), task.getDday(today),
                    task.getImportance(), showScore ? " | 추천 점수 " + service.priorityScore(task, today) : "");
            if (!task.getMemo().isEmpty()) out.println("   메모: " + task.getMemo());
        }
    }

    private boolean save() {
        try {
            repository.save(service);
            out.println("저장했습니다.");
            return true;
        } catch (IOException e) {
            out.println("저장 실패: " + e.getMessage());
            out.println("변경 사항이 아직 파일에 반영되지 않았습니다. 저장 경로와 쓰기 권한을 확인해 주세요.");
            return false;
        }
    }

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
            } catch (NumberFormatException ignored) { }
            out.println(min + "~" + max + " 범위의 숫자를 입력해 주세요.");
        }
    }

    private LocalDate readDate(String prompt, LocalDate fallback) {
        while (true) {
            String value = readLine(prompt);
            if (value.isEmpty() && fallback != null) return fallback;
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                out.println("실제로 존재하는 날짜를 yyyy-MM-dd 형식으로 입력해 주세요.");
            }
        }
    }

    private TaskType readType(TaskType fallback) {
        String current = fallback == null ? "" : " [현재: " + fallback.getLabel() + ", Enter: 유지]";
        int value = readInt("종류 (1: 과제, 2: 시험, 3: 공부 계획)" + current + ": ",
                1, 3, fallback == null ? null : fallback.ordinal() + 1);
        return TaskType.values()[value - 1];
    }

    private boolean confirm(String prompt) {
        return readLine(prompt + " [y 입력 시 실행]: ").equalsIgnoreCase("y");
    }
}
