package campusmate.storage;

import campusmate.model.Course;
import campusmate.model.Task;
import campusmate.model.TaskType;
import campusmate.service.PlannerService;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** 외부 라이브러리 없이 UTF-8 Properties 파일로 저장한다. */
public class FileRepository {
    private final Path path;

    public FileRepository(Path path) { this.path = path.toAbsolutePath(); }
    public Path getPath() { return path; }

    public PlannerService load() throws IOException {
        if (Files.notExists(path)) return new PlannerService();
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            data.load(reader);
            if (!"1".equals(required(data, "version"))) {
                throw new IllegalArgumentException("지원하지 않는 저장 버전입니다.");
            }
            List<Course> courses = new ArrayList<>();
            List<Task> tasks = new ArrayList<>();
            int courseCount = count(data, "course.count");
            int taskCount = count(data, "task.count");
            for (int i = 0; i < courseCount; i++) {
                String key = "course." + i + ".";
                courses.add(new Course(number(data, key + "id"), required(data, key + "name")));
            }
            for (int i = 0; i < taskCount; i++) {
                String key = "task." + i + ".";
                String completed = required(data, key + "completed");
                if (!completed.equals("true") && !completed.equals("false")) {
                    throw new IllegalArgumentException("완료 상태 값 오류");
                }
                tasks.add(new Task(number(data, key + "id"), number(data, key + "courseId"),
                        TaskType.valueOf(required(data, key + "type")), required(data, key + "title"),
                        required(data, key + "memo"), LocalDate.parse(required(data, key + "dueDate")),
                        number(data, key + "importance"), Boolean.parseBoolean(completed)));
            }
            return new PlannerService(courses, tasks, number(data, "nextCourseId"), number(data, "nextTaskId"));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new IOException("저장 파일 형식이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }

    public void save(PlannerService service) throws IOException {
        Properties data = new Properties();
        data.setProperty("version", "1");
        data.setProperty("nextCourseId", String.valueOf(service.getNextCourseId()));
        data.setProperty("nextTaskId", String.valueOf(service.getNextTaskId()));
        List<Course> courses = service.getCourses();
        List<Task> tasks = service.getTasks();
        data.setProperty("course.count", String.valueOf(courses.size()));
        data.setProperty("task.count", String.valueOf(tasks.size()));
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            String key = "course." + i + ".";
            data.setProperty(key + "id", String.valueOf(course.getId()));
            data.setProperty(key + "name", course.getName());
        }
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            String key = "task." + i + ".";
            data.setProperty(key + "id", String.valueOf(task.getId()));
            data.setProperty(key + "courseId", String.valueOf(task.getCourseId()));
            data.setProperty(key + "type", task.getType().name());
            data.setProperty(key + "title", task.getTitle());
            data.setProperty(key + "memo", task.getMemo());
            data.setProperty(key + "dueDate", task.getDueDate().toString());
            data.setProperty(key + "importance", String.valueOf(task.getImportance()));
            data.setProperty(key + "completed", String.valueOf(task.isCompleted()));
        }
        Files.createDirectories(path.getParent());
        // 쓰기를 마친 임시 파일로 교체하여 쓰기 도중 기존 파일이 잘리는 것을 줄인다.
        Path temp = Files.createTempFile(path.getParent(), "campusmate-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                data.store(writer, "CampusMate console v1");
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
}
