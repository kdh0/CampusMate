package campusmate.model;

/** 하루 할 일의 상태. 화면에는 mark(기호)와 label(이름)로 보여 준다. */
public enum TodoStatus {
    PLANNED("예정", "[ ]"),
    DONE("완료", "[V]"),
    MISSED("못함", "[X]"),      // 날짜가 지났고, 다른 날로 옮기지 않음
    POSTPONED("미룸", "[>]");   // 날짜가 지나서 다른 날로 옮김 (원래 날짜 기록에는 남는다)

    private final String label;
    private final String mark;

    TodoStatus(String label, String mark) {
        this.label = label;
        this.mark = mark;
    }

    public String getLabel() { return label; }
    public String getMark() { return mark; }
}
