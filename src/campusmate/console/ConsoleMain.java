package campusmate.console;

import campusmate.service.PlannerService;
import campusmate.storage.FileRepository;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Scanner;

/** 1단계 콘솔 버전 실행 진입점. */
public class ConsoleMain {
    public static void main(String[] args) {
        FileRepository repository = new FileRepository(
                Paths.get(args.length > 0 ? args[0] : FileRepository.DEFAULT_PATH));
        PlannerService service;
        try {
            service = repository.load();
        } catch (IOException e) {
            System.err.println("데이터를 불러오지 못했습니다: " + e.getMessage());
            System.err.println("기존 파일을 덮어쓰지 않고 종료합니다. 파일을 백업한 뒤 확인해 주세요.");
            System.exit(1);
            return;
        }
        // 실제 터미널은 해당 콘솔의 인코딩, IDE 실행창/파일 입력은 UTF-8 사용 (한글 깨짐 방지).
        Scanner input = System.console() == null
                ? new Scanner(System.in, "UTF-8") : new Scanner(System.console().reader());
        new ConsoleUI(service, repository, input, System.out).run();
    }
}
