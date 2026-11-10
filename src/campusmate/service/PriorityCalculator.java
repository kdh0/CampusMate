package campusmate.service;

import campusmate.model.Task;
import java.time.LocalDate;

/** 초안 정책: 긴급도 점수 + 중요도(1~5) * 10. 팀에서 조정할 수 있다. */
public class PriorityCalculator {
    public int calculate(Task task, LocalDate today) {
        long days = task.daysUntil(today);
        int urgency;
        if (days < 0) urgency = 100;
        else if (days == 0) urgency = 90;
        else if (days == 1) urgency = 80;
        else if (days <= 3) urgency = 60;
        else if (days <= 7) urgency = 40;
        else if (days <= 14) urgency = 20;
        else urgency = 0;
        return urgency + task.getImportance() * 10;
    }
}
