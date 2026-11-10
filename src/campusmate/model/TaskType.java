package campusmate.model;

public enum TaskType {
    ASSIGNMENT("과제"), EXAM("시험"), STUDY("공부 계획");

    private final String label;

    TaskType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
