package data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class ExileXechas {

	public void generate(SectorAPI sector) {
		
		StarSystemAPI system = sector.createStarSystem("Xechas");
		LocationAPI hyper = Global.getSector().getHyperspace();

	system.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "music_pirates_market_friendly");
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background5.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI xechas_star = system.initStar("xechas",
				"star_orange",
				825f,
				450, // extent of corona outside star
				10f, // solar wind burn level
				1f, // flare probability
				3f); // CR loss multiplier, good values are in the range of 1-5
		
		system.setLightColor(new Color(255, 235, 205)); // light color in entire system, affects all entities
		system.getLocation().set(-7200, -29500);

		HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
		NebulaEditor editor = new NebulaEditor(hyperTerrain);
		editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 900f, 0, 360f);
		
		PlanetAPI xechas_b = system.addPlanet("thar", xechas_star, "Thar", "desert", 0, 190, 2550, 280);    /// (planetname, what it orbits, name in game, planet type, orbit angle, planet size, orbit radius, days for full orbit
		xechas_b.getSpec().setGlowTexture(Global.getSettings().getSpriteName("planets", "desert01"));
		xechas_b.getSpec().setGlowColor(new Color(255,210,255,255));
	//	xechas_b.getSpec().setUseReverseLightForGlow(true);
		xechas_b.getSpec().setAtmosphereThicknessMin(16);
		xechas_b.getSpec().setAtmosphereThickness(0.14f);
		xechas_b.getSpec().setAtmosphereColor( new Color(150,125,100,30));
		xechas_b.applySpecChanges();

		xechas_b.setCustomDescriptionId("planet_thar");
		xechas_b.setInteractionImage("illustrations", "mairaath");
		
		PlanetAPI xechas_c = system.addPlanet("daria", xechas_star, "Daria", "jungle", 30, 250, 5275, 320);
		xechas_c.setCustomDescriptionId("planet_daria");
	//	xechas_c.getSpec().setGlowTexture(Global.getSettings().getSpriteName("planets", "jungle"));
		xechas_c.getSpec().setGlowColor(new Color(255,150,0,255));
		xechas_c.getSpec().setUseReverseLightForGlow(true);
		xechas_c.getSpec().setCloudColor(new Color(255,245,205,220));
		xechas_c.getSpec().setAtmosphereThicknessMin(32f); // fix for aliasing issue on outward side of planet
		xechas_c.applySpecChanges();

		xechas_c.setCustomDescriptionId("planet_daria");

	//		system.addRingBand(xechas_c, "misc", "rings_special0", 256f, 0, new Color(255,230,100,255), 256f, 420, 60f, Terrain.RING, "Gelan's Shackle");
		
		// Xechas Jumppoint in the L-something of Gelan
		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("xechas_jump", "Xechas Jump-point");
		jumpPoint.setCircularOrbit( xechas_star, 300 + 60, 3000, 104);
		jumpPoint.setRelatedPlanet(xechas_c);
		system.addEntity(jumpPoint);
		
		
		PlanetAPI xechas_d = system.addPlanet("cama", xechas_star, "Cama", "barren", 0, 120, 7820, 460);
		xechas_d.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "barren02"));
		xechas_d.getSpec().setPlanetColor(new Color(255,255,225,255));
		xechas_d.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "aurorae"));
		xechas_d.getSpec().setGlowColor(new Color(255,250,200,80));
		xechas_d.getSpec().setUseReverseLightForGlow(true);
		
		xechas_d.applySpecChanges();
		
		Misc.initConditionMarket(xechas_d);
		xechas_d.getMarket().addCondition(Conditions.NO_ATMOSPHERE);
		xechas_d.getMarket().addCondition(Conditions.ORE_SPARSE);
		xechas_d.getMarket().addCondition(Conditions.RARE_ORE_SPARSE);
		
		xechas_d.setCustomDescriptionId("planet_cama");
		xechas_d.setInteractionImage("illustrations", "mairaath");
		
		
			// Mairaath gets 1 orbiting derelict station, one lost station, and ideally plenty of debris
		//	SectorEntityToken mairaathStation1 = system.addCustomEntity("mairaath_abandoned_station1",
		//		"Abandoned Astropolis", "station_side06", "neutral");
		//
		//	mairaathStation1.setCircularOrbitPointingDown( system.getEntityById("mairaath"), 45, 350, 50);
	//
		//	Misc.setAbandonedStationMarket("mairaath_abandoned_station1_market", mairaathStation1);
		//	
		//	mairaathStation1.setCustomDescriptionId("mairaath_station1");
		//	mairaathStation1.setInteractionImage("illustrations", "abandoned_station3");
		
			// Mairaath stellar shade - out of orbit
			//SectorEntityToken mairaath_shade = system.addCustomEntity("mairaath_shade", "Shade Chah", "stellar_shade", "neutral");
			//mairaath_shade.setCircularOrbitPointingDown( xechas_star, 300, 3550, 183 );		
			//mairaath_shade.setCustomDescriptionId("stellar_shade");
		
			// pirate sensor array counter-rotating Mairaath
			SectorEntityToken xechas_stable1 = system.addCustomEntity(null, null, "stable_location", Factions.NEUTRAL);
			xechas_stable1.setCircularOrbitPointingDown(xechas_star, 180, 4450, 183);
			
		//system.addAsteroidBelt(xechas_star, 90, 7300, 200, 140, 180, Terrain.ASTEROID_BELT, "The Danavas");
		system.addRingBand(xechas_star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 6570, 160f);

			// a gate opposite of the Lost Astropolis
			/*SectorEntityToken gate = system.addCustomEntity("xechas_gate", // unique id
					 "Xechas Gate", // name - if null, defaultName from custom_entities.json will be used
					 "inactive_gate", // type of object, defined in custom_entities.json
					 null); // faction
			
			gate.setCircularOrbit(system.getEntityById("xechas"), 45 + 180, 4520, 250);*/
		
		
		system.addRingBand(xechas_star, "misc", "rings_dust0", 256f, 1, Color.white, 256f, 6510, 100f);
		//system.addAsteroidBelt(xechas_star, 120, 5330, 300, 200, 300, Terrain.ASTEROID_BELT, "The Daitya");
		
		
		PlanetAPI xechas_e = system.addPlanet("reyes", xechas_star, "Reyes", "gas_giant", 70, 475, 9025, 620);
		xechas_e.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "aurorae"));
		xechas_e.getSpec().setGlowColor(new Color(255,50,240,245));
		xechas_e.getSpec().setUseReverseLightForGlow(true);
		xechas_e.getSpec().setPitch(15f);
		xechas_e.getSpec().setTilt(40);
		xechas_e.getSpec().setPlanetColor(new Color(255,240,235));
		Misc.initConditionMarket(xechas_e);
		xechas_e.getMarket().addCondition(Conditions.HIGH_GRAVITY);
		xechas_e.getMarket().addCondition(Conditions.VOLATILES_ABUNDANT);

		xechas_e.applySpecChanges();
		
			PlanetAPI xechas_e1 = system.addPlanet("disappointment", xechas_e, "Disappointment", "barren", 0, 70, 910, 30);
		//	xechas_e1.getSpec().setAtmosphereThicknessMin(16);
		//	xechas_e1.getSpec().setAtmosphereThickness(0.2f);
		//	xechas_e1.getSpec().setAtmosphereColor( new Color(160,175,200,10) );
			xechas_e1.getSpec().setPlanetColor(new Color(200,240,255));
			Misc.initConditionMarket(xechas_e1);
			xechas_e1.getMarket().addCondition(Conditions.NO_ATMOSPHERE);
			xechas_e1.getMarket().addCondition(Conditions.ORE_SPARSE);
			xechas_e1.getMarket().addCondition(Conditions.RARE_ORE_SPARSE);
			xechas_e1.applySpecChanges();
			
		// Xechas Relay
		SectorEntityToken xechas_relay = system.addCustomEntity("xechas_relay", "Xechas Relay", "comm_relay", "neutral");
		xechas_relay.setCircularOrbitPointingDown( xechas_star, 270 + 60, 6800, 460);
			
		// stable loc in counter-orbit to Diti
		SectorEntityToken xechas_stable2 = system.addCustomEntity(null, null, "stable_location", Factions.NEUTRAL);
		xechas_stable2.setCircularOrbitPointingDown(xechas_star, 90, 6800, 290);
		
		//system.addAsteroidBelt(xechas_star, 150, 7500, 400, 370, 430, Terrain.ASTEROID_BELT, "The Takshaka");
		//system.addRingBand(xechas_star, "misc", "rings_dust0", 256f, 0, Color.white, 256f, 7320, 400f);
		
		SectorEntityToken gate = system.addCustomEntity("xechas_gate", // unique id
			 "Xechas Gate", // name - if null, defaultName from custom_entities.json will be used
			 "inactive_gate", // type of object, defined in custom_entities.json
			 null); // faction
		gate.setCircularOrbit(system.getEntityById("xechas"), 0, 8400, 460);
		
		system.autogenerateHyperspaceJumpPoints(true, true);
	}
}
