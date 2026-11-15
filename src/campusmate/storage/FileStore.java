package campusmate.storage;

import campusmate.model.Category;
import campusmate.model.Task;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 할 일 목록을 텍스트 파일에 한 줄에 하나씩 저장한다. 칸은 탭으로 나눈다.
 * 형식: 번호  날짜  종류  완료여부  과목  내용
 * 예)   3    2026-11-20  ASSIGNMENT  false  자료구조  연결리스트 과제
 */
public class FileStore {
    public static final String DEFAULT_PATH = "data/campusmate.txt";

    private final Path path;

    public FileStore(String path) { this.path = Paths.get(path).toAbsolutePath(); }

    public Path getPath() { return path; }

    /** 파일을 읽는다. 파일이 없으면(처음 실행) 빈 목록. */
    public List<Task> load() throws IOException {
        List<Task> tasks = new ArrayList<>();
        if (Files.notExists(path)) return tasks;

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] f = line.split("\t", -1);
            try {
                tasks.add(new Task(Integer.parseInt(f[0]), f[5], f[4], Category.valueOf(f[2]),
                        LocalDate.parse(f[1]), Boolean.parseBoolean(f[3])));
            } catch (RuntimeException e) { // 칸 부족, 숫자/날짜/종류 오류 등
                throw new IOException((i + 1) + "번째 줄 형식이 올바르지 않습니다: " + line);
            }
        }
        return tasks;
    }

    public void save(List<Task> tasks) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Task t : tasks) {
            lines.add(t.getId() + "\t" + t.getDate() + "\t" + t.getCategory().name() + "\t"
                    + t.isDone() + "\t" + t.getCourse() + "\t" + t.getTitle());
        }
        Files.createDirectories(path.getParent());
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
