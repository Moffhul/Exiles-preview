package data.scripts.exiles;

/** Saved finite obligation. All amounts are whole credits; no campaign API dependencies. */
public class ExileStartingDebtLedger {
    public int[] months;
    public int[] payments;
    public int total;
    public int unbilled;
    public int arrears;
    public int pending;
    public int installments;
    public int firstBillingMonth;
    public int lastBillingMonth = -1;
    public boolean completed;

    public ExileStartingDebtLedger(int[] months, int[] payments, int startMonth, int grace) {
        this.months = (int[]) months.clone();
        this.payments = (int[]) payments.clone();
        for (int i = 0; i < months.length; i++) total += months[i] * payments[i];
        unbilled = total;
        firstBillingMonth = startMonth + grace + 1;
    }

    public int nextPayment() {
        int offset = installments;
        for (int i = 0; i < months.length; i++) {
            if (offset < months[i]) return Math.min(unbilled, payments[i]);
            offset -= months[i];
        }
        return 0;
    }

    public int remainingMonths() {
        int result = -installments;
        for (int i = 0; i < months.length; i++) result += months[i];
        return Math.max(0, result);
    }

    public int bill(int month, boolean enabled) {
        if (completed || !enabled || month < firstBillingMonth || month <= lastBillingMonth
                || pending != 0 || unbilled == 0) return 0;
        int amount = nextPayment();
        if (amount <= 0) return 0;
        unbilled -= amount;
        pending = amount;
        installments++;
        lastBillingMonth = month;
        return amount;
    }

    /** Attribute payments to other expenses first, then fines. Never create extra engine debt. */
    public void reconcile(int reportDebt) {
        arrears = Math.min(arrears + pending, Math.max(0, reportDebt));
        pending = 0;
        completed = remaining() == 0;
    }

    public int remaining() { return unbilled + arrears + pending; }

    public void settle() {
        unbilled = 0;
        arrears = 0;
        pending = 0;
        completed = true;
    }
}
