package campusmate.model;

/** 항목 종류. 과제·시험은 '마감 일정'이라 D-day와 마감 임박 알림 대상이 된다. */
public enum Category {
    TODO("할 일"), ASSIGNMENT("과제"), EXAM("시험");

    private final String label;

    Category(String label) { this.label = label; }

    public String getLabel() { return label; }

    public boolean isDeadline() { return this != TODO; }

    /** JComboBox 등에 넣으면 이 글자가 보인다. */
    @Override
    public String toString() { return label; }
}
