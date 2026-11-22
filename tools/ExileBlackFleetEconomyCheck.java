package tools;

import data.scripts.exiles.ExileBlackFleetProsperityState;
import data.scripts.marketconditions.ExileSacked;
import com.thoughtworks.xstream.XStream;

/** Runs with the game's Janino and XStream, without a campaign. */
public class ExileBlackFleetEconomyCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    public static void run(String directory) throws Exception {
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(
                new java.io.File(directory, "data/config/exiles/director_config.json")));
        StringBuffer json = new StringBuffer();
        try {
            String line;
            while ((line = reader.readLine()) != null) json.append(line).append('\n');
        } finally { reader.close(); }
        String contents = json.toString();
        if (contents.length() > 0 && contents.charAt(0) == '\uFEFF') contents = contents.substring(1);
        contents = contents.replaceAll("(?m)^\\s*#.*$", "").replaceAll("(?m)\\s+#.*$", "");
        org.json.JSONObject config = new org.json.JSONObject(contents).getJSONObject("exileblackfleet");
        check(config.getJSONObject("prosperity").getDouble("recoveryThresholdDays") == 30,
                "Director JSON parses and contains recovery knob");
        check(config.getJSONObject("sacked").getInt("shortageUnits") == 3, "Director contains shortage knob");
        ExileBlackFleetProsperityState s = new ExileBlackFleetProsperityState(90, 30);
        s.advance(90, 1, 120, 30, 120);
        check(s.reserve == 0 && !s.active, "Exhaustion switches off");
        s.credit(15, 120, 30, true, 120, 60);
        check(!s.active, "Partial recovery stays off");
        s.credit(15, 120, 30, true, 120, 60);
        check(s.active && s.reserve == 30, "Threshold switches on");
        s.loss(15, 30);
        check(s.active && s.reserve == 15, "Hysteresis prevents threshold flicker");
        check(s.credit(30, 120, 30, true, 120, 60) == 30, "Concurrent collectors share remaining allowance");
        check(s.credit(30, 120, 30, true, 120, 60) == 0, "Further collectors cannot exceed allowance");
        s.advance(119, 1, 120, 30, 120);
        check(s.allowance(120, 60) == 0, "Rolling receipts retained until exact boundary");
        XStream xml = new XStream(new com.thoughtworks.xstream.io.xml.DomDriver());
        xml.setClassLoader(ExileBlackFleetEconomyCheck.class.getClassLoader());
        s = (ExileBlackFleetProsperityState) xml.fromXML(xml.toXML(s));
        check(s.allowance(120, 60) == 0 && !s.active, "Save/load retains allowance and inactive state");
        data.scripts.exiles.ExileBlackFleetExtortionManager.ExtortionOp op = new data.scripts.exiles.ExileBlackFleetExtortionManager.ExtortionOp();
        op.collected = true; op.returning = true; op.proceedsResolved = true;
        op = (data.scripts.exiles.ExileBlackFleetExtortionManager.ExtortionOp) xml.fromXML(xml.toXML(op));
        check(op.collected && op.returning && op.proceedsResolved, "Collector outcome guard survives save/load");
        s.advance(1, 1, 120, 30, 120);
        check(s.allowance(120, 60) == 60 && s.receipts.isEmpty(), "Rolling boundary releases receipts");
        s.credit(90, 120, 30, false, 120, 60);
        check(s.reserve == 90 && s.active, "Sack restores prosperity");
        check(s.credit(90, 120, 30, false, 120, 60) == 30 && s.reserve == 120, "Reserve cap clips haul");
        check(s.credit(30, 120, 30, true, 120, 60) == 0 && s.allowance(120, 60) == 60, "Overflow does not consume allowance or bank proceeds");
        s.loss(300, 30);
        check(s.reserve == 0 && !s.active, "Loss cannot create negative reserve");
        s.advance(120, 1, 120, 30, 120);
        check(s.reserve == 0, "No passive recovery or queued overflow");
        check(ExileSacked.additionalShortage(3, 2) == 1, "Overlap totals three, not five");
        check(ExileSacked.additionalShortage(3, 0) == 3, "Corsair removal restores full sack shortage");
        check(ExileSacked.additionalShortage(0, 2) == 0, "Shortage expiry leaves Corsair untouched");
        check(ExileSacked.additionalShortage(1, 2) == 0, "Tuned weaker sack cannot reduce Corsair shortage");
        System.out.println("PASS: reserve, concurrent allowance, recovery hysteresis, cap, save/load, shortage overlap");
    }
}
