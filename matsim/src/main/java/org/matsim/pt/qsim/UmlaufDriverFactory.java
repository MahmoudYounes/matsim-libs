/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package org.matsim.pt.qsim;

import org.matsim.pt.Umlauf;
import org.matsim.ptproject.qsim.interfaces.Mobsim;

/**
 * @author aneumann
 */
public class UmlaufDriverFactory implements AbstractTransitDriverFactory {

	@Override
	public AbstractTransitDriver createTransitDriver(Umlauf umlauf, TransitStopAgentTracker thisAgentTrackerVehicle, Mobsim qSim) {
		return new UmlaufDriver(umlauf, thisAgentTrackerVehicle, qSim);
	}

}
