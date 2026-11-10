package campusmate;

import campusmate.service.PlannerService;
import campusmate.storage.FileRepository;
import campusmate.ui.ConsoleUI;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Path path = Paths.get(args.length > 0 ? args[0] : "data/campusmate.properties");
        FileRepository repository = new FileRepository(path);
        try {
            PlannerService service = repository.load();
            // 실제 터미널은 해당 콘솔의 인코딩, 파일/파이프 입력은 UTF-8 사용.
            Scanner input = System.console() == null
                    ? new Scanner(System.in, "UTF-8") : new Scanner(System.console().reader());
            new ConsoleUI(service, repository, input, System.out).run();
        } catch (IOException e) {
            System.err.println("데이터를 불러오지 못했습니다: " + e.getMessage());
            System.err.println("기존 파일을 덮어쓰지 않고 종료합니다. 파일을 백업한 뒤 확인해 주세요.");
            System.exit(1);
        }
    }
}
