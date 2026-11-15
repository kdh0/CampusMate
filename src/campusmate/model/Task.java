package campusmate.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 할 일 하나. 과제·시험이면 date가 마감일이다. */
public class Task {
    private final int id;
    private String title;
    private String course;      // 과목 이름 ("" = 과목 없음)
    private Category category;
    private LocalDate date;
    private boolean done;

    public Task(int id, String title, String course, Category category, LocalDate date, boolean done) {
        this.id = id;
        setTitle(title);
        setCourse(course);
        setCategory(category);
        setDate(date);
        setDone(done);
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getCourse() { return course; }
    public Category getCategory() { return category; }
    public LocalDate getDate() { return date; }
    public boolean isDone() { return done; }

    public void setTitle(String title) {
        String value = clean(title);
        if (value.isEmpty()) throw new IllegalArgumentException("내용을 입력해 주세요.");
        this.title = value;
    }

    public void setCourse(String course) { this.course = clean(course); }

    public void setCategory(Category category) {
        if (category == null) throw new IllegalArgumentException("종류를 골라 주세요.");
        this.category = category;
    }

    public void setDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("날짜를 입력해 주세요.");
        this.date = date;
    }

    public void setDone(boolean done) { this.done = done; }

    /** 남은 일수. 지났으면 음수. */
    public long daysLeft(LocalDate today) { return ChronoUnit.DAYS.between(today, date); }

    /** "D-3", "D-day", "D+2" */
    public String getDday(LocalDate today) {
        long days = daysLeft(today);
        if (days == 0) return "D-day";
        return days > 0 ? "D-" + days : "D+" + (-days);
    }

    /** 날짜가 지났는데 끝내지 못했는가? */
    public boolean isOverdue(LocalDate today) { return !done && date.isBefore(today); }

    public String getStatus(LocalDate today) {
        if (done) return "완료";
        return isOverdue(today) ? "지남" : "예정";
    }

    /** 저장 파일이 탭/줄바꿈으로 칸을 나누므로, 입력값의 탭/줄바꿈은 공백으로 바꾼다. */
    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("[\\t\\r\\n]", " ").trim();
    }
}
