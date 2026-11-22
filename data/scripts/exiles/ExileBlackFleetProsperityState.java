package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

/** Small serializable shared reserve. Rolling allowance counts actual credited reserve only. */
public class ExileBlackFleetProsperityState {
    public float reserve;
    public boolean active;
    public double day;
    public List receipts = new ArrayList();
    public static class Receipt {
        public double day;
        public float amount;
        public Receipt(double day, float amount) { this.day = day; this.amount = amount; }
    }
    public ExileBlackFleetProsperityState(float initial, float threshold) {
        reserve = initial;
        active = initial >= threshold;
    }
    public void advance(float days, float drain, float cap, float threshold, float window) {
        day += Math.max(0f, days);
        reserve = Math.max(0f, Math.min(cap, reserve - Math.max(0f, days) * drain));
        prune(window);
        update(threshold);
    }
    public void prune(float window) {
        while (!receipts.isEmpty() && ((Receipt) receipts.get(0)).day <= day - window) receipts.remove(0);
    }
    public float allowance(float window, float limit) {
        prune(window);
        float used = 0f;
        for (int i = 0; i < receipts.size(); i++) used += ((Receipt) receipts.get(i)).amount;
        return Math.max(0f, limit - used);
    }
    public float credit(float amount, float cap, float threshold, boolean extortion, float window, float limit) {
        float grant = Math.min(Math.max(0f, amount), Math.max(0f, cap - reserve));
        if (extortion) grant = Math.min(grant, allowance(window, limit));
        reserve += grant;
        if (extortion && grant > 0f) receipts.add(new Receipt(day, grant));
        update(threshold);
        return grant;
    }
    public void loss(float amount, float threshold) {
        reserve = Math.max(0f, reserve - amount);
        update(threshold);
    }
    protected void update(float threshold) {
        if (reserve <= 0f) active = false;
        else if (!active && reserve >= threshold) active = true;
    }
}
