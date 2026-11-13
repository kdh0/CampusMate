package campusmate.storage;

import campusmate.model.Course;
import campusmate.model.Deadline;
import campusmate.model.DeadlineType;
import campusmate.model.TodoItem;
import campusmate.model.TodoStatus;
import campusmate.service.PlannerService;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 외부 라이브러리 없이 UTF-8 Properties(키=값) 텍스트 파일로 저장한다.
 * 예) todo.0.content=연결리스트 노드 클래스 작성
 */
public class FileRepository {
    public static final String DEFAULT_PATH = "data/campusmate.properties";
    private static final String VERSION = "2";

    private final Path path;

    public FileRepository(Path path) { this.path = path.toAbsolutePath(); }
    public Path getPath() { return path; }

    /** 파일을 읽어 PlannerService를 만든다. 파일이 없으면(첫 실행) 빈 데이터로 시작한다. */
    public PlannerService load() throws IOException {
        if (Files.notExists(path)) return new PlannerService();
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            data.load(reader);
            String version = required(data, "version");
            if (!VERSION.equals(version)) {
                throw new IllegalArgumentException("지원하지 않는 저장 버전(" + version
                        + ")입니다. 예전 버전 파일이면 이름을 바꾸거나 지운 뒤 실행해 주세요.");
            }

            List<Course> courses = new ArrayList<>();
            for (int i = 0; i < count(data, "course.count"); i++) {
                String key = "course." + i + ".";
                courses.add(new Course(number(data, key + "id"), required(data, key + "name")));
            }

            List<Deadline> deadlines = new ArrayList<>();
            for (int i = 0; i < count(data, "deadline.count"); i++) {
                String key = "deadline." + i + ".";
                deadlines.add(new Deadline(number(data, key + "id"), number(data, key + "courseId"),
                        DeadlineType.valueOf(required(data, key + "type")), required(data, key + "title"),
                        LocalDate.parse(required(data, key + "dueDate")), bool(data, key + "completed")));
            }

            List<TodoItem> todos = new ArrayList<>();
            for (int i = 0; i < count(data, "todo.count"); i++) {
                String key = "todo." + i + ".";
                todos.add(new TodoItem(number(data, key + "id"), LocalDate.parse(required(data, key + "date")),
                        required(data, key + "content"), number(data, key + "courseId"),
                        number(data, key + "deadlineId"), TodoStatus.valueOf(required(data, key + "status")),
                        number(data, key + "postponeCount")));
            }

            return new PlannerService(courses, deadlines, todos, number(data, "nextCourseId"),
                    number(data, "nextDeadlineId"), number(data, "nextTodoId"));
        } catch (IllegalArgumentException | DateTimeException e) {
            // 숫자/날짜/종류 변환 실패나 값 검사 실패를 '파일 형식 오류'로 묶어서 알린다.
            throw new IOException("저장 파일 형식이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }

    public void save(PlannerService service) throws IOException {
        Properties data = new Properties();
        data.setProperty("version", VERSION);
        data.setProperty("nextCourseId", String.valueOf(service.getNextCourseId()));
        data.setProperty("nextDeadlineId", String.valueOf(service.getNextDeadlineId()));
        data.setProperty("nextTodoId", String.valueOf(service.getNextTodoId()));

        List<Course> courses = service.getCourses();
        data.setProperty("course.count", String.valueOf(courses.size()));
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            String key = "course." + i + ".";
            data.setProperty(key + "id", String.valueOf(course.getId()));
            data.setProperty(key + "name", course.getName());
        }

        List<Deadline> deadlines = service.getDeadlines();
        data.setProperty("deadline.count", String.valueOf(deadlines.size()));
        for (int i = 0; i < deadlines.size(); i++) {
            Deadline deadline = deadlines.get(i);
            String key = "deadline." + i + ".";
            data.setProperty(key + "id", String.valueOf(deadline.getId()));
            data.setProperty(key + "courseId", String.valueOf(deadline.getCourseId()));
            data.setProperty(key + "type", deadline.getType().name());
            data.setProperty(key + "title", deadline.getTitle());
            data.setProperty(key + "dueDate", deadline.getDueDate().toString());
            data.setProperty(key + "completed", String.valueOf(deadline.isCompleted()));
        }

        List<TodoItem> todos = service.getAllTodos();
        data.setProperty("todo.count", String.valueOf(todos.size()));
        for (int i = 0; i < todos.size(); i++) {
            TodoItem todo = todos.get(i);
            String key = "todo." + i + ".";
            data.setProperty(key + "id", String.valueOf(todo.getId()));
            data.setProperty(key + "date", todo.getDate().toString());
            data.setProperty(key + "content", todo.getContent());
            data.setProperty(key + "courseId", String.valueOf(todo.getCourseId()));
            data.setProperty(key + "deadlineId", String.valueOf(todo.getDeadlineId()));
            data.setProperty(key + "status", todo.getStatus().name());
            data.setProperty(key + "postponeCount", String.valueOf(todo.getPostponeCount()));
        }

        Files.createDirectories(path.getParent());
        // 임시 파일에 다 쓴 뒤 원래 파일과 바꿔치기한다. (쓰는 도중 멈춰도 기존 파일이 깨지지 않게)
        Path temp = Files.createTempFile(path.getParent(), "campusmate-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                data.store(writer, "CampusMate data v" + VERSION);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String required(Properties data, String key) {
        String value = data.getProperty(key);
        if (value == null) throw new IllegalArgumentException("누락된 항목: " + key);
        return value;
    }

    private int number(Properties data, String key) { return Integer.parseInt(required(data, key)); }

    private int count(Properties data, String key) {
        int value = number(data, key);
        if (value < 0) throw new IllegalArgumentException("항목 개수는 음수일 수 없습니다.");
        return value;
    }

    private boolean bool(Properties data, String key) {
        String value = required(data, key);
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("true/false 값 오류: " + key);
        }
        return Boolean.parseBoolean(value);
    }
}
