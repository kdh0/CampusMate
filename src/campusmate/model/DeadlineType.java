package campusmate.model;

/** 마감 일정의 종류. */
public enum DeadlineType {
    ASSIGNMENT("과제"), EXAM("시험"), ETC("기타");

    private final String label;

    DeadlineType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
