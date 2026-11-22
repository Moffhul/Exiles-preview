package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import java.util.Set;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/** Finite fines, billed through vanilla monthly accounting. One saved object, no frame script. */
public class ExileStartingDebt extends BaseIntelPlugin implements EconomyTickListener,
        TooltipMakerAPI.TooltipCreator {
    public static final String KEY = "$exiles_starting_debt";
    public static final String NODE = "exiles_starting_fine";
    public static final String PAY = "exiles_settle_fine";
    protected ExileStartingDebtLedger ledger;
    protected boolean collectionEnabled;
    protected boolean notified;
    // Only the latest report is retained; references are shared with vanilla, not copies.
    protected MonthlyReport reconciledReport;
    protected MonthlyReport pendingReport;

    public static void initialize() {
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean("$exiles_starting_fines_eligible")) {
            Global.getLogger(ExileStartingDebt.class).info("Exiles starting fines: skipped (start not marked eligible)");
            return;
        }
        if (Global.getSector().getMemoryWithoutUpdate().contains(KEY)) return;
        ExileStartingDebtConfig config = ExileStartingDebtConfig.load();
        ExileStartingDebt debt = new ExileStartingDebt();
        debt.ledger = new ExileStartingDebtLedger(config.months, config.payments,
                month(), config.gracePeriodMonths);
        debt.collectionEnabled = config.enabled;
        debt.reconciledReport = SharedData.getData().getPreviousReport();
        Global.getSector().getMemoryWithoutUpdate().set(KEY, debt);
        Global.getSector().getIntelManager().addIntel(debt);
        restore();
        Global.getLogger(ExileStartingDebt.class).info("Exiles starting fines: initialized; total="
                + debt.ledger.total + ", firstBillingMonth=" + debt.ledger.firstBillingMonth
                + ", enabled=" + debt.collectionEnabled);
    }

    /** No retrofit into old campaigns. Saved completion is authoritative, including when disabled. */
    public static void restore() {
        Object saved = Global.getSector().getMemoryWithoutUpdate().get(KEY);
        if (!(saved instanceof ExileStartingDebt)) return;
        ExileStartingDebt debt = (ExileStartingDebt) saved;
        debt.collectionEnabled = ExileStartingDebtConfig.loadEnabled();
        debt.reconcile();
        Global.getSector().getListenerManager().removeListener(debt);
        if (!debt.ledger.completed) Global.getSector().getListenerManager().addListener(debt, true);
    }

    protected static int month() {
        return Global.getSector().getClock().getCycle() * 12
                + Global.getSector().getClock().getMonth() - 1;
    }

    protected void reconcile() {
        MonthlyReport previous = SharedData.getData().getPreviousReport();
        if (previous != null && previous != reconciledReport) {
            // CoreScript rolls over and settles in a single callback. We deliberately do not
            // reconcile from reportEconomyMonthEnd, whose ordering relative to CoreScript varies.
            if (pendingReport == null || pendingReport == previous) {
                ledger.reconcile(previous.getDebt());
                pendingReport = null;
                reconciledReport = previous;
            }
        }
        finishIfPaid();
    }

    protected void finishIfPaid() {
        if (!ledger.completed || notified) return;
        notified = true;
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().getCampaignUI().addMessage("Nachiketa fines paid in full. Collection has ended.");
    }

    public void reportEconomyTick(int iterIndex) {
        reconcile();
        int last = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex != last) return;
        // ReachEconomyStepper emits the final tick AFTER the calendar changes, immediately
        // before settling the previous month's report. Bill the month being closed.
        int amount = ledger.bill(month() - 1, collectionEnabled);
        if (amount == 0) return;
        MonthlyReport report = SharedData.getData().getCurrentReport();
        pendingReport = report;
        FDNode node = report.getNode(report.getNode(MonthlyReport.FLEET), NODE);
        node.upkeep = amount;
        node.name = "Nachiketa fines";
        node.icon = getIcon();
        node.tooltipCreator = this;
    }

    // Settlement order among listeners is not guaranteed. Reconcile on next tick/load/UI instead.
    public void reportEconomyMonthEnd() { }

    public String getName() { return ledger != null && ledger.completed ? "Nachiketa fines: paid" : "Nachiketa fines"; }
    public String getIcon() { return Global.getSettings().getSpriteName("income_report", "generic_expense"); }

    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_STORY);
        // Vanilla never ships INTEL_STORY alone; without a second tag the entry is invisible
        // under every filter but "Story", which is not where players look for an open
        // obligation. ACCEPTED puts it beside commissions and accepted missions (2026-09-13).
        tags.add(Tags.INTEL_ACCEPTED);
        return tags;
    }

    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        reconcile();
        info.addPara("Fines imposed following your conviction in absentia for assisting in the destruction "
                + "of Nachiketa's fuel-production facility. Automated collection has resumed after your absence.", 0f);
        info.addPara("Original fine: %s. Remaining: %s.", 10f, Misc.getHighlightColor(),
                Misc.getDGSCredits(ledger.total), Misc.getDGSCredits(ledger.remaining()));
        if (ledger.completed) {
            info.addPara("Paid in full. No further installments will be collected. "
                    + "Payment does not overturn your conviction or alter faction relationships.", 10f);
            return;
        }
        if (!collectionEnabled) info.addPara("New installments are paused by configuration. "
                + "Previously billed amounts remain payable. No catch-up installments accrue.", 10f);
        int graceLeft = Math.max(0, ledger.firstBillingMonth - month());
        if (graceLeft > 0) info.addPara("First billing: end of month %s, cycle %s.", 10f,
                Misc.getHighlightColor(), "" + (ledger.firstBillingMonth % 12 + 1),
                "" + (ledger.firstBillingMonth / 12));
        info.addPara("Next installment: %s. Installments remaining: %s.", 10f, Misc.getHighlightColor(),
                Misc.getDGSCredits(ledger.nextPayment()), "" + ledger.remainingMonths());
        if (collectionEnabled && ledger.remainingMonths() > 0) {
            int nextMonth = Math.max(ledger.firstBillingMonth, Math.max(month(), ledger.lastBillingMonth + 1));
            int finalMonth = nextMonth + ledger.remainingMonths() - 1;
            info.addPara("Final scheduled installment: end of month %s, cycle %s, if collection remains enabled. "
                    + "Any unpaid billed balance still needs to be cleared.", 10f, Misc.getHighlightColor(),
                    "" + (finalMonth % 12 + 1), "" + (finalMonth / 12));
            int offset = ledger.installments;
            for (int i = 0; i < ledger.months.length; i++) {
                if (offset < ledger.months[i]) {
                    if (i + 1 < ledger.months.length) info.addPara("Next scheduled rate: %s after %s more installments.",
                            10f, Misc.getHighlightColor(), Misc.getDGSCredits(ledger.payments[i + 1]),
                            "" + (ledger.months[i] - offset));
                    break;
                }
                offset -= ledger.months[i];
            }
        }
        info.addPara("Repayment schedule (saved for this campaign):", 10f);
        for (int i = 0; i < ledger.months.length; i++) {
            info.addPara("%s months at %s per month", 3f, Misc.getHighlightColor(),
                    "" + ledger.months[i], Misc.getDGSCredits(ledger.payments[i]));
        }
        info.addPara("Unpaid billed fines: %s. Current installment awaiting monthly settlement: %s.",
                10f, Misc.getHighlightColor(), Misc.getDGSCredits(ledger.arrears), Misc.getDGSCredits(ledger.pending));
        info.addPara("Unpaid installments carry forward in the ordinary monthly report without added fine interest "
                + "or late fees. Other monthly expenses are treated as paid first. Settling here pays only "
                + "your remaining fines, including their share of carried debt.", 10f);
        info.addButton("Settle remaining fine", PAY, width, 24f, 10f).setEnabled(
                Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= ledger.remaining());
    }

    public boolean doesButtonHaveConfirmDialog(Object buttonId) { return PAY.equals(buttonId); }
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        reconcile();
        prompt.addPara("Pay %s now to settle all remaining Nachiketa fines? This permanently ends collection. "
                + "Other monthly expenses and debts are unchanged.", 0f, Misc.getHighlightColor(),
                Misc.getDGSCredits(ledger.remaining()));
    }

    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (!PAY.equals(buttonId)) return;
        reconcile();
        int amount = ledger.remaining();
        if (ledger.completed || Global.getSector().getPlayerFleet().getCargo().getCredits().get() < amount) return;
        // Remove only the fine's share of vanilla carried debt, never anyone else's expenses.
        MonthlyReport previous = SharedData.getData().getPreviousReport();
        if (previous != null && ledger.arrears > 0) {
            previous.setDebt(Math.max(0, previous.getDebt() - ledger.arrears));
        }
        if (pendingReport != null) {
            pendingReport.getNode(pendingReport.getNode(MonthlyReport.FLEET), NODE).upkeep = 0f;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(amount);
        pendingReport = null;
        ledger.settle();
        finishIfPaid();
        ui.updateUIForItem(this);
    }

    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        tooltip.addPara("Finite court-imposed fines. View Nachiketa fines in Intel for the saved repayment "
                + "schedule, remaining balance and early settlement. No fine interest or late fees.", 0f);
    }
    public float getTooltipWidth(Object tooltipParam) { return 450f; }
    public boolean isTooltipExpandable(Object tooltipParam) { return false; }
}
