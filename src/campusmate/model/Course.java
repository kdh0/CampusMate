package campusmate.model;

/** 수강 과목. ID는 바뀌지 않고, 이름만 수정할 수 있다. */
public final class Course {
    private final int id;
    private String name;

    public Course(int id, String name) {
        if (id <= 0) throw new IllegalArgumentException("과목 ID는 양수여야 합니다.");
        this.id = id;
        setName(name);
    }

    public int getId() { return id; }
    public String getName() { return name; }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("과목 이름을 입력해 주세요.");
        }
        this.name = name.trim();
    }
}
