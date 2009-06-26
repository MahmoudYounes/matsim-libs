/* *********************************************************************** *
 * project: org.matsim.*
 * MyRuns.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.marcel;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.opengis.kml._2.DocumentType;
import net.opengis.kml._2.FolderType;
import net.opengis.kml._2.KmlType;
import net.opengis.kml._2.LineStringType;
import net.opengis.kml._2.LineStyleType;
import net.opengis.kml._2.MultiGeometryType;
import net.opengis.kml._2.ObjectFactory;
import net.opengis.kml._2.PlacemarkType;
import net.opengis.kml._2.PolyStyleType;
import net.opengis.kml._2.StyleType;
import net.opengis.kml._2.TimeSpanType;

import org.matsim.api.basic.v01.Coord;
import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.core.api.Scenario;
import org.matsim.core.api.ScenarioLoader;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Network;
import org.matsim.core.api.network.Node;
import org.matsim.core.api.population.Leg;
import org.matsim.core.api.population.NetworkRoute;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.api.population.Population;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.events.Events;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.algorithms.NetworkCalcLanes;
import org.matsim.core.network.algorithms.NetworkFalsifier;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.PopulationReader;
import org.matsim.core.population.PopulationWriter;
import org.matsim.core.router.PlansCalcRoute;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeCost;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.CH1903LV03toWGS84;
import org.matsim.core.utils.misc.Time;
import org.matsim.population.algorithms.AbstractPersonAlgorithm;
import org.matsim.population.algorithms.ActLocationFalsifier;
import org.matsim.population.algorithms.PersonFilterSelectedPlan;
import org.matsim.population.algorithms.PersonRemoveCertainActs;
import org.matsim.population.algorithms.PersonRemoveLinkAndRoute;
import org.matsim.population.algorithms.PersonRemovePlansWithoutLegs;
import org.matsim.population.algorithms.PlanFilterActTypes;
import org.matsim.population.algorithms.PlansFilterByLegMode;
import org.matsim.population.algorithms.PlansFilterPersonHasPlans;
import org.matsim.population.algorithms.XY2Links;
import org.matsim.vis.kml.KMZWriter;

public class MyRuns {

	//////////////////////////////////////////////////////////////////////
	// filterSelectedPlans
	//////////////////////////////////////////////////////////////////////

	public static void filterSelectedPlans(final String[] args) {

		System.out.println("RUN: filterSelectedPlans");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();
		Scenario scenario = sl.getScenario();		

		final PopulationImpl plans = (PopulationImpl) scenario.getPopulation();
		plans.setIsStreaming(true);
		plans.addAlgorithm(new PersonFilterSelectedPlan());
		final PopulationWriter plansWriter = new PopulationWriter(plans);
		plans.addAlgorithm(plansWriter);
		PopulationReader plansReader = new MatsimPopulationReader(scenario);

		System.out.println("  reading and writing plans...");
		plansReader.readFile(scenario.getConfig().plans().getInputFile());
		plans.printPlansCount();
		plansWriter.write();
		System.out.println("  done.");

		System.out.println("RUN: filterSelectedPlans finished.");
		System.out.println();
	}

	//////////////////////////////////////////////////////////////////////
	// filterPlansInNetworkArea
	//////////////////////////////////////////////////////////////////////
	public static void filterPlansWithRouteInArea(final String[] args, final double x, final double y, final double radius) {
		System.out.println("RUN: filterPlansWithRouteInArea");

		final CoordImpl center = new CoordImpl(x, y);
		final Map<Id, Link> areaOfInterest = new HashMap<Id, Link>();

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();
		Network network = sl.getScenario().getNetwork();
		
		System.out.println("  extracting aoi... at " + (new Date()));
		for (Link link : network.getLinks().values()) {
			final Node from = link.getFromNode();
			final Node to = link.getToNode();
			if ((CoordUtils.calcDistance(from.getCoord(), center) <= radius) || (CoordUtils.calcDistance(to.getCoord(), center) <= radius)) {
				System.out.println("    link " + link.getId().toString());
				areaOfInterest.put(link.getId(),link);
			}
		}
		System.out.println("  done. ");
		System.out.println("  aoi contains: " + areaOfInterest.size() + " links.");

		System.out.println("RUN: filterPlansWithRouteInArea finished");
	}

	//////////////////////////////////////////////////////////////////////
	// filterCars
	//////////////////////////////////////////////////////////////////////

	public static void filterCars(final String[] args) {

		System.out.println("RUN: filterCars");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		Scenario scenario = sl.loadScenario();

		System.out.println("  processing plans...");
		new PlansFilterByLegMode(TransportMode.car, false).run(scenario.getPopulation());

		System.out.println("  writing plans...");
		final PopulationWriter plansWriter = new PopulationWriter(scenario.getPopulation());
		plansWriter.write();

		System.out.println("RUN: filterCars finished.");
		System.out.println();
	}


	//////////////////////////////////////////////////////////////////////
	// filterPt
	//////////////////////////////////////////////////////////////////////

	public static void filterPt(final String[] args) {

		System.out.println("RUN: filterPt");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		Scenario scenario = sl.loadScenario();
		final Population plans = scenario.getPopulation();

		System.out.println("  processing plans...");
		new PlansFilterByLegMode(TransportMode.pt, true).run(plans);

		System.out.println("  writing plans...");
		final PopulationWriter plansWriter = new PopulationWriter(plans);
		plansWriter.write();

		System.out.println("RUN: filterPt finished.");
		System.out.println();
	}


	//////////////////////////////////////////////////////////////////////
	// filterWork
	//////////////////////////////////////////////////////////////////////

	public static void filterWork(final String[] args) {

		System.out.println("RUN: filterWork");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		Scenario scenario = sl.loadScenario();

		new PersonRemoveCertainActs().run(scenario.getPopulation());
		new PersonRemovePlansWithoutLegs().run(scenario.getPopulation());
		new PlansFilterPersonHasPlans().run(scenario.getPopulation());

		final PopulationWriter plansWriter = new PopulationWriter(scenario.getPopulation());
		plansWriter.write();
		System.out.println("  done.");

		System.out.println("RUN: filterWork finished.");
		System.out.println();
	}

	public static void filterWorkEdu(final String[] args) {
		System.out.println("RUN: filterWorkEdu");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		Scenario scenario = sl.loadScenario();

		new PlanFilterActTypes(new String[] {"work1", "work2", "work3", "edu", "uni"}).run(scenario.getPopulation());
		new PlansFilterPersonHasPlans().run(scenario.getPopulation());

		final PopulationWriter plansWriter = new PopulationWriter(scenario.getPopulation());
		plansWriter.write();
		System.out.println("  done.");

		System.out.println("RUN: filterWorkEdu finished.");
		System.out.println();
	}

	//////////////////////////////////////////////////////////////////////
	// removeLinkAndRoute
	//////////////////////////////////////////////////////////////////////

	public static void removeLinkAndRoute(final String[] args) {

		System.out.println("RUN: removeLinkAndRoute");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();
		final Config config = sl.getScenario().getConfig();

		System.out.println("  setting up plans objects...");
		final PopulationImpl plans = (PopulationImpl) sl.getScenario().getPopulation();
		plans.setIsStreaming(true);
		final PopulationWriter plansWriter = new PopulationWriter(plans);
		final PopulationReader plansReader = new MatsimPopulationReader(sl.getScenario());
		System.out.println("  done.");

		System.out.println("  adding plans algorithm... ");
		plans.addAlgorithm(new PersonRemoveLinkAndRoute());
		System.out.println("  done.");

		System.out.println("  reading, processing, writing plans...");
		plans.addAlgorithm(plansWriter);
		plansReader.readFile(config.plans().getInputFile());
		plansWriter.write();
		System.out.println("  done.");

		System.out.println("RUN: removeLinkAndRoute finished.");
		System.out.println();
	}

	//////////////////////////////////////////////////////////////////////
	// calcNofLanes
	//////////////////////////////////////////////////////////////////////

	public static void calcNofLanes(final String[] args) {

		System.out.println("RUN: calcNofLanes");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();
		
		Network network = sl.getScenario().getNetwork();

		System.out.println("  calculating number of lanes... ");
		new NetworkCalcLanes().run(network);
		System.out.println("  done.");

		System.out.println("  writing the network...");
		final NetworkWriter network_writer = new NetworkWriter(network);
		network_writer.write();
		System.out.println("  done.");

		System.out.println("RUN: calcNofLanes finished.");
		System.out.println();
	}

	//////////////////////////////////////////////////////////////////////
	// subNetwork
	//////////////////////////////////////////////////////////////////////
	public static void subNetwork(final String[] args, final double x, final double y, final double minRadius, final double radiusStep, final double maxRadius) {
		System.out.println("RUN: subNetwork");

		final CoordImpl center = new CoordImpl(x, y);
		final Map<Id, Link> areaOfInterest = new HashMap<Id, Link>();

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();

		Network network = sl.getScenario().getNetwork();

//		System.out.println("  reading population... " + (new Date()));
//		final Plans population = new Plans(Plans.NO_STREAMING);
//		new MatsimPopulationReader(population, network).readFile(config.plans().getInputFile());

		System.out.println("  finding sub-networks... " + (new Date()));
		for (double radius = minRadius; radius <= maxRadius; radius += radiusStep) {
			for (Link link : network.getLinks().values()) {
				final Node from = link.getFromNode();
				final Node to = link.getToNode();
				if ((CoordUtils.calcDistance(from.getCoord(), center) <= radius) || (CoordUtils.calcDistance(to.getCoord(), center) <= radius)) {
					areaOfInterest.put(link.getId(),link);
				}
			}
			System.out.println("  aoi with radius=" + radius + " contains " + areaOfInterest.size() + " links.");
			areaOfInterest.clear();
		}
		System.out.println("  done. ");

//		final PlansWriter plansWriter = new PlansWriter(population);
//		final PersonIntersectAreaFilter filter = new PersonIntersectAreaFilter(plansWriter, areaOfInterest);
//		filter.setAlternativeAOI(center, radius);
//		population.addAlgorithm(filter);
//
//
//		plansWriter.writeEndPlans();
//		population.printPlansCount();
//		System.out.println("  done. " + (new Date()));
//		System.out.println("  filtered persons: " + filter.getCount());

		System.out.println("RUN: subNetwork finished");
	}

	public static void falsifyNetAndPlans(final String[] args) {
		System.out.println("RUN: falsifyNetAndPlans");

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();
		Scenario scenario = sl.getScenario();
		
		Network network = scenario.getNetwork();

		System.out.println("  falsifying the network...");
		(new NetworkFalsifier(50)).run(network);

		System.out.println("  writing the falsified network...");
		final NetworkWriter network_writer = new NetworkWriter(network);
		network_writer.write();
		System.out.println("  done.");

		System.out.println("  processing plans...");
		final PopulationImpl population = (PopulationImpl) scenario.getPopulation();
		population.setIsStreaming(true);
		final PopulationWriter plansWriter = new PopulationWriter(population);
		final PopulationReader plansReader = new MatsimPopulationReader(population, network);
		population.addAlgorithm(new ActLocationFalsifier(200));
		population.addAlgorithm(new XY2Links((NetworkLayer) network));
		final FreespeedTravelTimeCost timeCostFunction = new FreespeedTravelTimeCost();
		population.addAlgorithm(new PlansCalcRoute(network, timeCostFunction, timeCostFunction));
		population.addAlgorithm(plansWriter);
		plansReader.readFile(scenario.getConfig().plans().getInputFile());
		population.printPlansCount();
		plansWriter.write();
		System.out.println("  done.");

		System.out.println("RUN: falsifyNetAndPlans finished.");
		System.out.println();
	}
	
	//////////////////////////////////////////////////////////////////////
	// buildKML2
	//////////////////////////////////////////////////////////////////////

	private static MultiGeometryType getNetworkAsKml(final Network network, final CoordinateTransformation coordTransform) {
		return getNetworkAsKml(network, new TreeMap<Id, Integer>(), coordTransform);
	}

	private static MultiGeometryType getNetworkAsKml(final Network network, final TreeMap<Id, Integer> linkVolumes, final CoordinateTransformation coordTransform) {

		ObjectFactory kmlObjectFactory = new ObjectFactory();

		final MultiGeometryType networkGeom = kmlObjectFactory.createMultiGeometryType();

		for (Link link : network.getLinks().values()) {
			Integer volume = linkVolumes.get(link.getId());
			if (volume == null) volume = 0;
			final Coord fromCoord = coordTransform.transform(link.getFromNode().getCoord());
			final Coord toCoord = coordTransform.transform(link.getToNode().getCoord());
			final LineStringType lst = kmlObjectFactory.createLineStringType();
			lst.getCoordinates().add(Double.toString(fromCoord.getX()) + "," + Double.toString(fromCoord.getY()) + "," + volume);
			lst.getCoordinates().add(Double.toString(toCoord.getX()) + "," + Double.toString(toCoord.getY()) + "," + volume);
			networkGeom.getAbstractGeometryGroup().add(kmlObjectFactory.createLineString(lst));
		}

		return networkGeom;
	}

	public static void buildKML2(final String[] args, final boolean useVolumes) {

		System.out.println("RUN: buildKML2");

		final TreeMap<Integer, TreeMap<Id, Integer>> linkValues = new TreeMap<Integer, TreeMap<Id, Integer>>();

		ScenarioLoader sl = new ScenarioLoader(args[0]);
		sl.loadNetwork();
		Config config = sl.getScenario().getConfig();
		Network network = sl.getScenario().getNetwork();

		if (useVolumes) {
			System.out.println("  reading plans...");
			final PopulationImpl plans = (PopulationImpl) sl.getScenario().getPopulation();
			plans.setIsStreaming(true);
			final PopulationReader plansReader = new MatsimPopulationReader(sl.getScenario());
			plans.addAlgorithm(
					new AbstractPersonAlgorithm() {

						@Override
						public void run(final Person person) {
							Plan plan = person.getSelectedPlan();
							if (plan == null) {
								if (person.getPlans().size() == 0) return;
								plan = person.getPlans().get(0);
							}
							run(plan);
						}

						public void run(final Plan plan) {
							final List actslegs = plan.getPlanElements();
							for (int i = 1, max = actslegs.size(); i < max; i+=2) {
								final Leg leg = (Leg)actslegs.get(i);
								run((NetworkRoute) leg.getRoute(), leg.getDepartureTime());
							}
						}

						public void run(final NetworkRoute route, final double time) {
							if (route == null) return;

							final int hour = (int)time / 3600;
							if ((hour > 30) || (hour < 0)) return;

							for (final Link link : route.getLinks()) {
								final Id id = link.getId();
								TreeMap<Id, Integer> hourValues = linkValues.get(hour);
								if (hourValues == null) {
									hourValues = new TreeMap<Id, Integer>();
									linkValues.put(hour, hourValues);
								}
								Integer counter = hourValues.get(id);
								if (counter == null) counter = 0;
								counter++;
								hourValues.put(id, counter);
							}
						}
					}
			);
			plansReader.readFile(config.plans().getInputFile());
			plans.printPlansCount();
			System.out.println("  done.");
		}

		System.out.println("  writing the network...");

		final ObjectFactory kmlObjectFactory = new ObjectFactory();

		final KmlType kml = kmlObjectFactory.createKmlType();

		final DocumentType kmlDoc = kmlObjectFactory.createDocumentType();
		kmlDoc.setId("the root document");
		kml.setAbstractFeatureGroup(kmlObjectFactory.createDocument(kmlDoc));

		final StyleType style = kmlObjectFactory.createStyleType();
		style.setId("redWallStyle");
		LineStyleType lst = kmlObjectFactory.createLineStyleType();
		lst.setColor(new byte[]{(byte) 0xaf, (byte) 0x00, (byte) 0x00, (byte) 0xff});
		lst.setWidth(3.0);
		style.setLineStyle(lst);
		PolyStyleType pst = kmlObjectFactory.createPolyStyleType();
		pst.setColor(new byte[]{(byte) 0x7f, (byte) 0x00, (byte) 0x00, (byte) 0xff});
		style.setPolyStyle(pst);
		kmlDoc.getAbstractStyleSelectorGroup().add(kmlObjectFactory.createStyle(style));

		final FolderType networksFolder = kmlObjectFactory.createFolderType();
		networksFolder.setId("networks");
		kmlDoc.getAbstractFeatureGroup().add(kmlObjectFactory.createFolder(networksFolder));

		if (useVolumes) {
			for (int hour = 4; hour < 23; hour++) {
				System.out.println("adding network hour = " + hour);

				final PlacemarkType placemark = kmlObjectFactory.createPlacemarkType();
				placemark.setId("network " + hour);
				placemark.setName("Network at " + hour);
				placemark.setDescription("the road network at " + hour);
				placemark.setStyleUrl(style.getId());

				TimeSpanType timeSpan = kmlObjectFactory.createTimeSpanType();
				timeSpan.setBegin("1970-01-01T" + Time.writeTime(hour * 3600));
				timeSpan.setEnd("1970-01-01T" + Time.writeTime(hour * 3600 + 59 * 60 + 59));
				placemark.setAbstractTimePrimitiveGroup(kmlObjectFactory.createTimeSpan(timeSpan));

				TreeMap<Id, Integer> hourValues = linkValues.get(hour);
				if (hourValues == null) {
					hourValues = new TreeMap<Id, Integer>();
				}
				placemark.setAbstractGeometryGroup(
						kmlObjectFactory.createMultiGeometry(getNetworkAsKml(network, hourValues, new CH1903LV03toWGS84())));

				networksFolder.getAbstractFeatureGroup().add(kmlObjectFactory.createPlacemark(placemark));
			}
		} else {

			final PlacemarkType placemark = kmlObjectFactory.createPlacemarkType();
			placemark.setId("network");
			placemark.setName("Network");
			placemark.setDescription("the road network");
			placemark.setStyleUrl(style.getId());
			placemark.setAbstractGeometryGroup(kmlObjectFactory.createMultiGeometry(getNetworkAsKml(network, new CH1903LV03toWGS84())));

			networksFolder.getAbstractFeatureGroup().add(kmlObjectFactory.createPlacemark(placemark));
		}

		final KMZWriter kmzWriter = new KMZWriter("test.kml");
		kmzWriter.writeMainKml(kml);
		kmzWriter.close();

		System.out.println("  done.");

		System.out.println("RUN: buildKML2 finished.");
		System.out.println();
	}

	public static void readPlansDat(final String[] args) {
		try {
			final DataInputStream in = new DataInputStream(new FileInputStream(args[0]));
			final int nofAgents = in.readInt();
			System.out.println("# agents: " + nofAgents);
			for (int i = 0; i < nofAgents; i++) {
				final int agentId = in.readInt();
				System.out.println("  agent: " + agentId);
				final int nofLegs = in.readInt();
				System.out.println("    # legs: " + nofLegs);
				for (int l = 0; l < nofLegs; l++) {
					final double time = in.readDouble();
					System.out.print("      " + Time.writeTime(time));
					final int nofLinks = in.readInt();
					for (int n = 0; n < nofLinks; n++) {
						System.out.print(" " + in.readInt());
					}
					System.out.println();
				}
			}
			in.close();
		}
		catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public static void readEventsDat(final String[] args) {
		try {
			final DataInputStream in = new DataInputStream(new FileInputStream("output/equil1/ITERS/it.0/deq_events.deq"));
			while (in.available() > 0) {
				final double time = in.readDouble();
				final int agentId = in.readInt();
				final int linkId = in.readInt();
				final int eventType = in.readInt();
				System.out.println(time + "\t" + agentId + "\t" + linkId + "\t" + eventType);
			}
			in.close();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
	}

	public static void someTest(final String[] args) {

//		CoordinateTransformation transformation = new SoldnerBerlinToWGS84();
//		Coord alexanderplatz = new CoordImpl(25466670/1000.0, 21618520/1000.0);
//		Coord wgs84 = transformation.transform(alexanderplatz);
//		System.out.println(wgs84.getX() + " / " + wgs84.getY()); // expected: 13.41 / 52.52
		
//		Gbl.printSystemInfo();
//		NetworkLayer network = new NetworkLayer();
//		new MatsimNetworkReader(network).readFile("/Volumes/Data/ETH/cvs/ivt/studies/switzerland/networks/navteq/network.xml.gz");
		
//		Controler controler = new Controler(new String[] {"test/scenarios/berlin/config.xml"});
//		controler.run();
		
//		ArrayList<Integer> list = new ArrayList<Integer>();
//		list.add(new Integer(5));
//		System.out.println(list.size());
//		someMethod(list);
//		System.out.println(list.size());
		
		Double d = null;
		System.out.println("some value: " + d);
		
	}
	
//	public static void someMethod(List<Integer> list) {
//		System.out.println(list.size());
//		list = new ArrayList<Integer>();
//		System.out.println(list.size());
//	}

	//////////////////////////////////////////////////////////////////////
	// main
	//////////////////////////////////////////////////////////////////////

	public static void main(final String[] args) {


		System.out.println("start at " + (new Date()));

/* ***   P L A N S   *** */

//		createKutterPlans(args);
//		fmaToTrips(args);

//		convertPlans(args);
//		readPlans(args);
//		removeLinkAndRoute(args);

		/* ***   DEMAND MODELING   *** */

//		filterSelectedPlans(args);
//		filterPlansInArea(args, 4582000, 5939000, 4653000, 5850000);  // uckermark
//		filterPlansInArea(args, 4580000, 5807000, 4617000, 5835000);  // berlin
//		filterPlansWithRouteInArea(args, 683518.0, 246836.0, 30000.0); // Bellevue Zrh, 30km
//		filterPlansWithRouteInArea(args, 4595406.5, 5821171.5, 1000.0); // Berlin Mitte, 1km
//		filterPlansInNetworkArea(args);
//		filterPlansPassingLink(args, 4022);
//		filterCars(args);
//		filterPt(args);
//		filterWork(args);
//		filterWorkEdu(args);
//		createDebugPlans(args);
//		calcRoute(args);
//		calcRouteMTwithTimes(args); // multithreaded, use traveltimes from events
//		calcRoutePt(args);  // public transport

//		testPt(args);  // public transport
//		calcRealPlans(args);
//		calcScoreFromEvents_old(args);
//		calcScoreFromEvents_new(args);

/* ***   N E T W O R K S   *** */
//		convertNetwork(args);
//		cleanNetwork(args);
//		calcNofLanes(args);
//		falsifyNetwork(args);
//		subNetwork(args, 683518.0, 246836.0, 1000.0, 1000.0, 50000.0); // Belleue Zrh, 1-50km

/* ***   M A T R I C E S   *** */
//		visumMatrixTest(args);
//		convertMatrices(args);


/* ***   A N A L Y S I S   *** */

		/* ***   VOLUMES   *** */
//		calc1hODMatrices(args);		// calcs 1h OD matrices from plans and events, using 1004 tvz from Berlin

		/* ***   PLANS   *** */
//		calcPlanStatistics(args);
//		analyzeLegTimes_events(args, 300); // # departure, # arrival, # stuck per time bin
//		calcTripLength(args); // from plans
//		calcTolledTripLength(args); // calc avg trip length on tolled links
//		generateRealPlans(args);
//		writeVisumRouten_plans(args);

		/* ***   EVENTS   *** */
//		readEvents(args);
//		calcLegTimes(args);  // leg durations
//		calcStuckVehStats(args);

/* ***   C O M P L E X   S T U F F   *** */

//		falsifyNetAndPlans(args);

		/* ***   P L A Y G R O U N D   *** */

//		buildKML2(args, false);
//		network2kml(args);
//		readPlansDat(args);
//		readEventsDat(args);
//		speedEventsDat(args);
//		readMatrices(args);
//		readWriteLinkStats(args);
//		randomWalk(10000);
//		readCounts(args);
//		writeKml();
//		runSimulation();
		someTest(args);

		Gbl.printSystemInfo();

		System.out.println("stop at " + (new Date()));
		System.exit(0); // currently only used for calcRouteMT();
	}
}
