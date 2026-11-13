package campusmate.swing;

import campusmate.model.Deadline;
import campusmate.model.TodoItem;
import campusmate.service.PlannerService;
import java.awt.Color;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Swing 화면 여러 곳에서 함께 쓰는 색상, 날짜 형식, 도우미 메서드 모음. */
final class SwingUtil {
    static final Color HEADER_COLOR = new Color(44, 62, 80);
    static final Color DONE_COLOR = new Color(223, 240, 216);      // 완료 / 모두 달성 (연두)
    static final Color MISSED_COLOR = new Color(255, 225, 225);    // 못함 / 기한 지남 (연빨강)
    static final Color POSTPONED_COLOR = new Color(255, 243, 205); // 미룸 / 마감 임박 (연노랑)
    static final Color TODAY_COLOR = new Color(52, 152, 219);      // 오늘 표시 (파랑)
    static final Color BORDER_COLOR = new Color(220, 220, 220);

    static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN);
    static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);
    static final String DATE_HELP = "예: 오늘, 내일, +3, -1, 11/20, 2026-11-20";

    private SwingUtil() { } // 객체를 만들 필요 없는 도우미 클래스

    /** 여러 형식의 날짜를 해석한다. (콘솔 버전과 같은 규칙) 해석할 수 없으면 null. */
    static LocalDate parseDate(String text) {
        String value = text == null ? "" : text.trim();
        LocalDate today = LocalDate.now();
        try {
            if (value.equals("오늘")) return today;
            if (value.equals("내일")) return today.plusDays(1);
            if (value.equals("어제")) return today.minusDays(1);
            if (value.startsWith("+") || value.startsWith("-")) return today.plusDays(Integer.parseInt(value));
            if (value.matches("\\d{1,2}/\\d{1,2}")) {
                String[] parts = value.split("/");
                return LocalDate.of(today.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            return LocalDate.parse(value);
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    /** "자료구조 · 연결리스트 과제" / "자료구조" / "" */
    static String linkText(PlannerService service, TodoItem todo) {
        if (todo.getDeadlineId() != 0) {
            Deadline deadline = service.requireDeadline(todo.getDeadlineId());
            return service.getCourseName(deadline.getCourseId()) + " · " + deadline.getTitle();
        }
        if (todo.getCourseId() != 0) return service.getCourseName(todo.getCourseId());
        return "";
    }

    static int percent(int done, int total) { return total == 0 ? 0 : done * 100 / total; }

    /** HTML 라벨에 사용자가 입력한 글자를 넣을 때 '<' 같은 문자가 태그로 해석되지 않게 바꾼다. */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
