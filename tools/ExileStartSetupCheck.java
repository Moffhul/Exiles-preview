import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.util.MutableValue;
import data.scripts.rulecmd.ExileAddStartingScript;

/** Check the exact Exile package independently of campaign/UI initialization. */
public class ExileStartSetupCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    public static void main(String[] args) {
        final Map<String, Float> commodities = new HashMap<String, Float>();
        final MutableValue credits = new MutableValue();
        final CargoAPI cargo = (CargoAPI) Proxy.newProxyInstance(CargoAPI.class.getClassLoader(),
                new Class[] {CargoAPI.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] values) {
                if (m.getName().equals("addItems")) {
                    String id = (String) values[1];
                    commodities.put(id, Float.valueOf(((Number) values[2]).floatValue()));
                    return null;
                }
                if (m.getName().equals("getCredits")) return credits;
                throw new IllegalStateException(m.getName());
            }
        });
        CharacterCreationData creation = (CharacterCreationData) Proxy.newProxyInstance(
                CharacterCreationData.class.getClassLoader(),
                new Class[] {CharacterCreationData.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] values) {
                if (m.getName().equals("getStartingCargo")) return cargo;
                throw new IllegalStateException(m.getName());
            }
        });
        ExileAddStartingScript.addStartingPackage(creation);
        check(commodities.size() == 4, "No vanilla bonus commodities");
        check(commodities.get("crew").floatValue() == 100f, "Crew");
        check(commodities.get("heavy_machinery").floatValue() == 10f, "Machinery");
        check(commodities.get("supplies").floatValue() == 45f, "Supplies");
        check(commodities.get("fuel").floatValue() == 30f, "Fuel");
        check(credits.get() == 20000f, "Credits");
        System.out.println("PASS: exact Exile start package");
    }
}
