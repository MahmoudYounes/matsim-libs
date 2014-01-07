/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package playground.michalm.taxi.file;

import java.util.Stack;

import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.data.model.Depot;
import org.matsim.contrib.dvrp.data.model.impl.DepotImpl;
import org.matsim.contrib.dvrp.extensions.electric.*;
import org.matsim.contrib.transEnergySim.vehicles.energyConsumption.EnergyConsumptionModel;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.xml.sax.Attributes;

import playground.michalm.taxi.TaxiData;
import playground.michalm.taxi.model.VrpAgentElectricTaxi;


public class TaxiRankReader
    extends MatsimXmlParser
{
    private final static String RANK = "depot";
    private final static String TAXI = "vehicle";
    private final static String CHARGER = "charger";

    private final Scenario scenario;
    private final TaxiData data;
    private final EnergyConsumptionModel ecm;

    private Depot currentRank;


    public TaxiRankReader(Scenario scenario, TaxiData data, EnergyConsumptionModel ecm)
    {
        this.scenario = scenario;
        this.data = data;
        this.ecm = ecm;
    }


    public void readFile(String filename)
    {
        parse(filename);
    }


    @Override
    public void startTag(String name, Attributes atts, Stack<String> context)
    {
        if (RANK.equals(name)) {
            startRank(atts);
        }
        else if (TAXI.equals(name)) {
            startTaxi(atts);
        }
        else if (CHARGER.equals(name)) {
            startCharger(atts);
        }
    }


    @Override
    public void endTag(String name, String content, Stack<String> context)
    {}


    private void startRank(Attributes atts)
    {
        int id = data.getDepots().size();
        Id rankId = scenario.createId(id + "");

        String name = atts.getValue("name");
        if (name == null) {
            name = "R_" + id;
        }

        Id linkId = scenario.createId(atts.getValue("linkId"));
        Link link = scenario.getNetwork().getLinks().get(linkId);

        currentRank = new DepotImpl(rankId, name, link);
        data.addDepot(currentRank);
    }


    private void startTaxi(Attributes atts)
    {
        int id = data.getVehicles().size();
        Id taxiId = scenario.createId(id + "");

        String name = atts.getValue("name");
        if (name == null) {
            name = "T_" + id;
        }

        int t0 = getInt(atts, "t0", 0);
        int t1 = getInt(atts, "t1", 24 * 60 * 60);

        double chargeInJoules = getDouble(atts, "battery_charge_kWh", 20) * 1000 * 3600;
        double capacityInJoules = getDouble(atts, "battery_capacity_kWh", 20) * 1000 * 3600;

        ElectricVehicle ev = new VrpAgentElectricTaxi(taxiId, name, currentRank, t0, t1, ecm);
        ev.setBattery(new BatteryImpl(chargeInJoules, capacityInJoules));
        data.addVehicle(ev);
    }


    private void startCharger(Attributes atts)
    {
        int id = data.getChargers().size();
        Id chargerId = scenario.createId(id + "");

        String name = atts.getValue("name");
        if (name == null) {
            name = "Ch_" + id;
        }

        double powerInJoules = getDouble(atts, "power_kW", 20) * 1000;

        data.addCharger(new ChargerImpl(chargerId, name, powerInJoules, currentRank.getLink()));
    }


    private int getInt(Attributes atts, String qName, int defaultValue)
    {
        String val = atts.getValue(qName);

        if (val != null) {
            return Integer.parseInt(val);
        }
        else {
            return defaultValue;
        }
    }


    private double getDouble(Attributes atts, String qName, double defaultValue)
    {
        String val = atts.getValue(qName);

        if (val != null) {
            return Double.parseDouble(val);
        }
        else {
            return defaultValue;
        }
    }
}
