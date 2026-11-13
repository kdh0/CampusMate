package campusmate.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * 외부 라이브러리 없이 Graphics로 직접 그리는 간단한 막대 차트.
 * setData()로 값을 넣으면 repaint() → paintComponent()가 다시 호출되어 그려진다.
 */
public class BarChartPanel extends JPanel {
    private static final Color BAR_COLOR = new Color(52, 152, 219);
    private static final Color FULL_COLOR = new Color(39, 174, 96);
    private static final Color HIGHLIGHT_COLOR = new Color(231, 76, 60);

    private final String title;
    private String[] labels = new String[0];
    private int[] values = new int[0];        // -1이면 '데이터 없음' (막대를 그리지 않음)
    private int maxValue = 100;
    private String unit = "";
    private int highlightAt = -1;            // 강조할 막대 (예: 오늘)

    public BarChartPanel(String title) {
        this.title = title;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(420, 260));
    }

    /**
     * @param labels   막대 아래 글자 ("13\n금"처럼 두 줄 가능)
     * @param values   막대 값 (-1: 데이터 없음)
     * @param maxValue 세로축 최댓값 (예: 100%)
     */
    public void setData(String[] labels, int[] values, int maxValue, String unit, int highlightAt) {
        this.labels = labels;
        this.values = values;
        this.maxValue = Math.max(1, maxValue);
        this.unit = unit;
        this.highlightAt = highlightAt;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int left = 20, right = 15, top = 44, bottom = 42;

        g2.setColor(Color.DARK_GRAY);
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.drawString(title, left, 26);
        if (values.length == 0) return;

        int chartWidth = width - left - right;
        int chartHeight = height - top - bottom;
        int baseY = top + chartHeight;

        g2.setColor(Color.LIGHT_GRAY);
        g2.drawLine(left, baseY, width - right, baseY);

        g2.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g2.getFontMetrics();
        double slot = (double) chartWidth / values.length;
        int barWidth = (int) Math.max(4, slot * 0.6);

        for (int i = 0; i < values.length; i++) {
            int x = (int) (left + slot * i + (slot - barWidth) / 2);

            if (values[i] >= 0) {
                int barHeight = (int) ((double) values[i] / maxValue * (chartHeight - 16));
                int y = baseY - barHeight;
                Color color = values[i] >= maxValue ? FULL_COLOR : BAR_COLOR;
                if (i == highlightAt) color = HIGHLIGHT_COLOR;
                g2.setColor(color);
                g2.fillRect(x, y, barWidth, Math.max(barHeight, 1));

                g2.setColor(Color.DARK_GRAY);
                String text = values[i] + unit;
                g2.drawString(text, x + (barWidth - fm.stringWidth(text)) / 2, y - 3);
            }

            g2.setColor(i == highlightAt ? HIGHLIGHT_COLOR : Color.DARK_GRAY);
            String[] lines = labels[i].split("\n");
            for (int l = 0; l < lines.length; l++) {
                g2.drawString(lines[l], x + (barWidth - fm.stringWidth(lines[l])) / 2, baseY + 15 + l * 13);
            }
        }
    }
}
