/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

package org.matsim.contrib.parking.parkingproxy.penaltyCalculator;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.collections.Tuple;

/**
 * Generates an initial distribution of cars based on the location of the persons at the start of the simulation.
 * For each agent either one or zero vehicles (with a certain weight to account for smaller scenario sample sizes)
 * are counted with a certain probability matching the provided statistics of cars per 1000 inhabitants.
 * 
 * @author tkohl / Senozon
 *
 */
public class InitialLoadGenerator {
	
	private final Collection<? extends Person> population;
	private final int scaleFactor;
	private final Random rnd;

	/**
	 * Initializes the class and the RNG.
	 * 
	 * @param population the scenario population
	 * @param scaleFactor the factor with which to multiply the number of persons to get the full population (e.g. 4 in a 25% scenario)
	 */
	public InitialLoadGenerator(Collection<? extends Person> population, int scaleFactor) {
		this.population = population;
		this.scaleFactor = scaleFactor;
		this.rnd = MatsimRandom.getLocalInstance();
	}
	
	/**
	 * Generates the list of initial car positions and their weight based on randomly selecting agents whose first
	 * activity's coordinate serves as the coordinate of the vehicle.
	 * 
	 * @param carsPer1000Persons a statistical value of how many cars there shall be per 1000 persons in the scenario
	 * @return a List of (car position, car weight)-pairs
	 */
	public Collection<Tuple<Coord, Integer>> calculateInitialCarPositions(int carsPer1000Persons) {
		Collection<Tuple<Coord, Integer>> initialPositions = new LinkedList<Tuple<Coord, Integer>>();
		for (Person p : population) {
			if (rnd.nextInt(1000) < carsPer1000Persons) {
				initialPositions.add(new Tuple<>(((Activity)p.getSelectedPlan().getPlanElements().get(0)).getCoord(), scaleFactor));
			}
		}
		return initialPositions;
	}
}
