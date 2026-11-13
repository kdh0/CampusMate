package campusmate.swing;

import campusmate.service.PlannerService;
import campusmate.storage.FileRepository;
import java.io.IOException;
import java.nio.file.Paths;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** 2단계 Swing(GUI) 버전 실행 진입점. 콘솔 버전과 같은 저장 파일을 사용한다. */
public class SwingMain {
    public static void main(String[] args) {
        FileRepository repository = new FileRepository(
                Paths.get(args.length > 0 ? args[0] : FileRepository.DEFAULT_PATH));

        // 화면 생성/변경은 Swing 전용 스레드(EDT)에서 하는 것이 규칙이다.
        SwingUtilities.invokeLater(() -> {
            try {
                // 운영체제 기본 모양(Windows 등)을 사용한다. 실패해도 기본 모양으로 계속 진행.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }

            PlannerService service;
            try {
                service = repository.load();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "데이터를 불러오지 못했습니다.\n" + e.getMessage()
                                + "\n\n기존 파일을 덮어쓰지 않고 종료합니다.",
                        "CampusMate", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            MainFrame frame = new MainFrame(service, repository);
            frame.setVisible(true);
            frame.runStartupChecks(); // 못 한 일 처리 → 마감 임박 알림
        });
    }
}
