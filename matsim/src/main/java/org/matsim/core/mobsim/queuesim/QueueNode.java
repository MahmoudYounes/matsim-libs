/* *********************************************************************** *
 * project: org.matsim.*
 * QueueNode.java
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

package org.matsim.core.mobsim.queuesim;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.events.AgentStuckEventImpl;
import org.matsim.ptproject.qsim.interfaces.Mobsim;
import org.matsim.ptproject.qsim.interfaces.QVehicle;
import org.matsim.vis.snapshots.writers.VisData;
import org.matsim.vis.snapshots.writers.VisNode;

/**
 * Represents a node in the QueueSimulation.
 */
class QueueNode implements VisNode {

	private static final Logger log = Logger.getLogger(QueueNode.class);

	private static final QueueLinkIdComparator qlinkIdComparator = new QueueLinkIdComparator();

	private final QueueLink[] inLinksArrayCache;
	private final QueueLink[] tempLinks;

	private boolean active = false;

	private final Node node;

	private QueueNetwork queueNetwork;

	/* package */ QueueNode(final Node n, final QueueNetwork queueNetwork) {
		this.node = n;
		this.queueNetwork = queueNetwork;

		int nofInLinks = this.node.getInLinks().size();
		this.inLinksArrayCache = new QueueLink[nofInLinks];
		this.tempLinks = new QueueLink[nofInLinks];
	}

	/**
	 * Loads the inLinks-array with the corresponding links.
	 * Cannot be called in constructor, as the queueNetwork does not yet know
	 * the queueLinks. Should be called by QueueNetwork, after creating all
	 * QueueNodes and QueueLinks.
	 */
	/*package*/ void init() {
		int i = 0;
		for (Link l : this.node.getInLinks().values()) {
			this.inLinksArrayCache[i] = this.queueNetwork.getQueueLinks().get(l.getId());
			i++;
		}
		/* As the order of nodes has an influence on the simulation results,
		 * the nodes are sorted to avoid indeterministic simulations. dg[april08]
		 */
		Arrays.sort(this.inLinksArrayCache, QueueNode.qlinkIdComparator);
	}

	@Override
	public Node getNode() { // accessed from elsewhere
		return this.node;
	}

	// ////////////////////////////////////////////////////////////////////
	// Queue related movement code
	// ////////////////////////////////////////////////////////////////////
	/**
	 * @param veh
	 * @param link
	 * @param now
	 * @return <code>true</code> if the vehicle was successfully moved over the node, <code>false</code>
	 * otherwise (e.g. in case where the next link is jammed)
	 */
	protected boolean moveVehicleOverNode(final QVehicle veh, final QueueLink link, final double now) {
		Id nextLinkId = veh.getDriver().chooseNextLinkId();
		Link currentLink = link.getLink();

		// veh has to move over node
		if (nextLinkId != null) {
			Link nextLink = this.queueNetwork.getNetwork().getLinks().get(nextLinkId);
			if (currentLink.getToNode() != nextLink.getFromNode()) {
				throw new RuntimeException("Cannot move vehicle " + veh.getId() +
						" from link " + currentLink.getId() + " to link " + nextLinkId);
			}

			QueueLink nextQueueLink = this.queueNetwork.getQueueLink(nextLinkId);

			if (nextQueueLink.hasSpace()) {
				link.popFirstFromBuffer();
				veh.getDriver().notifyMoveOverNode();
				nextQueueLink.addFromIntersection(veh);
				return true;
			}

			// check if veh is stuck!
			Mobsim qSim = this.queueNetwork.getQSim() ;
			Scenario sc = qSim.getScenario() ;
			Config config = sc.getConfig() ;

//			if ((now - link.bufferLastMovedTime) > AbstractSimulation.getStuckTime()) {
			if ((now - link.bufferLastMovedTime) > config.simulation().getStuckTime() ) {
				/* We just push the vehicle further after stucktime is over, regardless
				 * of if there is space on the next link or not.. optionally we let them
				 * die here, we have a config setting for that!
				 */
				if (config.simulation().isRemoveStuckVehicles()) {
					link.popFirstFromBuffer();
					this.queueNetwork.getQSim().getAgentCounter().decLiving();
					this.queueNetwork.getQSim().getAgentCounter().incLost();
					QueueSimulation.getEvents().processEvent(
							new AgentStuckEventImpl(now, veh.getDriver().getPerson().getId(), currentLink.getId(), veh.getDriver().getCurrentLeg().getMode()));
				} else {
					link.popFirstFromBuffer();
					veh.getDriver().notifyMoveOverNode();
					nextQueueLink.addFromIntersection(veh);
					return true;
				}
			}
			return false;
		}

		// --> nextLink == null
		link.popFirstFromBuffer();
		this.queueNetwork.getQSim().getAgentCounter().decLiving();
		this.queueNetwork.getQSim().getAgentCounter().incLost();
		log.error(
				"Agent has no or wrong route! agentId=" + veh.getDriver().getPerson().getId()
						+ " currentLink=" + currentLink.getId().toString()
						+ ". The agent is removed from the simulation.");
		return true;
	}

	protected final void activateNode() {
		this.active = true;
	}

	/*package*/ final boolean isActive() {
		return this.active;
	}

	/**
	 * Moves vehicles from the inlinks' buffer to the outlinks where possible.<br>
	 * The inLinks are randomly chosen, and for each link all vehicles in the
	 * buffer are moved to their desired outLink as long as there is space. If the
	 * front most vehicle in a buffer cannot move across the node because there is
	 * no free space on its destination link, the work on this inLink is finished
	 * and the next inLink's buffer is handled (this means, that at the node, all
	 * links have only like one lane, and there are no separate lanes for the
	 * different outLinks. Thus if the front most vehicle cannot drive further,
	 * all other vehicles behind must wait, too, even if their links would be
	 * free).
	 *
	 * @param now
	 *          The current time in seconds from midnight.
	 * @param random the random number generator to be used
	 */
	/*package*/ void moveNode(final double now, final Random random) {
	  int inLinksCounter = 0;
	  double inLinksCapSum = 0.0;
	  // Check all incoming links for buffered agents
	  for (QueueLink link : this.inLinksArrayCache) {
	    if (!link.bufferIsEmpty()) {
	      this.tempLinks[inLinksCounter] = link;
	      inLinksCounter++;
	      inLinksCapSum += link.getLink().getCapacity(now);
	    }
	  }

	  if (inLinksCounter == 0) {
	    this.active = false;
	    return; // Nothing to do
	  }

	  int auxCounter = 0;
	  // randomize based on capacity
	  while (auxCounter < inLinksCounter) {
	    double rndNum = random.nextDouble() * inLinksCapSum;
	    double selCap = 0.0;
	    for (int i = 0; i < inLinksCounter; i++) {
	      QueueLink link = this.tempLinks[i];
	      if (link == null)
	        continue;
	      selCap += link.getLink().getCapacity(now);
	      if (selCap >= rndNum) {
	        auxCounter++;
	        inLinksCapSum -= link.getLink().getCapacity(now);
	        this.tempLinks[i] = null;
	        //move the link
	        this.clearLaneBuffer(link, now);
	        break;
	      }
	    }
		}
	}

	private void clearLaneBuffer(final QueueLink link, final double now){
		while (!link.bufferIsEmpty()) {
			QVehicle veh = link.getFirstFromBuffer();
			if (!moveVehicleOverNode(veh, link, now)) {
				break;
			}
		}
	}

	protected static class QueueLinkIdComparator implements Comparator<QueueLink>, Serializable {
		private static final long serialVersionUID = 1L;
		@Override
		public int compare(final QueueLink o1, final QueueLink o2) {
			return o1.getLink().getId().compareTo(o2.getLink().getId());
		}
	}

	@Override
	public VisData getVisData() {
		return null;
	}

}
