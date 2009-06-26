/* *********************************************************************** *
 * project: org.matsim.*
 * AnalysisSelectedPlansActivityChains.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package playground.mfeil.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.ScenarioImpl;
import org.matsim.core.api.population.Activity;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.api.population.PlanElement;
import org.matsim.core.api.population.Population;
import org.matsim.core.facilities.MatsimFacilitiesReader;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.knowledges.Knowledges;


/**
 * Simple class to analyze the selected plans of a plans (output) file. Extracts the 
 * activity chains and their number of occurrences.
 *
 * @author mfeil
 */
public class AnalysisSelectedPlansActivityChains {

	protected final Population population;
	protected final String outputDir;
	protected ArrayList<List<PlanElement>> activityChains;
	protected ArrayList<ArrayList<Plan>> plans;
	protected final Map<String,Double> minimumTime;
	protected final Knowledges knowledges;
	protected static final Logger log = Logger.getLogger(AnalysisSelectedPlansActivityChains.class);
	


	public AnalysisSelectedPlansActivityChains(final Population population, Knowledges knowledges, final String outputDir) {
		this.population = population;
		this.outputDir = outputDir;
		this.knowledges = knowledges;
		initAnalysis();
		this.minimumTime = new TreeMap<String, Double>();
		this.minimumTime.put("home", 7200.0);
		this.minimumTime.put("work", 3600.0);
		this.minimumTime.put("shopping", 1800.0);
		this.minimumTime.put("leisure", 3600.0);
	}
	
	protected void initAnalysis(){
		
		this.activityChains = new ArrayList<List<PlanElement>>();
		this.plans = new ArrayList<ArrayList<Plan>>();
		
		Map<Id,Person> agents = this.population.getPersons();
		for (Person person:agents.values()){
			boolean alreadyIn = false;
			for (int i=0;i<this.activityChains.size();i++){
				if (this.checkForEquality(person.getSelectedPlan(), this.activityChains.get(i))){
					plans.get(i).add(person.getSelectedPlan());
					alreadyIn = true;
					break;
				}
			}
			if (!alreadyIn){
				this.activityChains.add(person.getSelectedPlan().getPlanElements());
				this.plans.add(new ArrayList<Plan>());
				this.plans.get(this.plans.size()-1).add(person.getSelectedPlan());
			}
		}
	}
	
	protected void checkCorrectness(){
		for (int i=0;i<this.plans.size();i++){
			for (int j=0;j<this.plans.get(i).size();j++){
				for (int k=0;k<this.plans.get(i).get(j).getPlanElements().size()-2;k+=2){
					if (k==0){
						if ((((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getDuration()+((Activity)(this.plans.get(i).get(j).getPlanElements().get(this.plans.get(i).get(j).getPlanElements().size()-1))).getDuration())<this.minimumTime.get(((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getType())-1) { //ignoring rounding errors
							log.warn("Duration error in plan of person "+this.plans.get(i).get(j).getPerson().getId()+" at act position "+(k/2)+" with duration "+(((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getDuration()+((Activity)(this.plans.get(i).get(j).getPlanElements().get(this.plans.get(i).get(j).getPlanElements().size()-1))).getDuration()));
							break;
						}
					}
					else if (((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getDuration()<this.minimumTime.get(((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getType())-1) { //ignoring rounding errors
						log.warn("Duration error in plan of person "+this.plans.get(i).get(j).getPerson().getId()+" at act position "+(k/2)+" with duration "+(((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getDuration()));
						break;
					}
				}
				for (int k=0;k<this.knowledges.getKnowledgesByPersonId().get(this.plans.get(i).get(j).getPerson().getId()).getActivities(true).size();k++){
					boolean notIn = true;
					for (int l=0;l<this.plans.get(i).get(j).getPlanElements().size()-2;l+=2){
						if (((Activity)(this.plans.get(i).get(j).getPlanElements().get(l))).getType().equalsIgnoreCase(this.knowledges.getKnowledgesByPersonId().get(this.plans.get(i).get(j).getPerson().getId()).getActivities(true).get(k).getType()) &&
								((Activity)(this.plans.get(i).get(j).getPlanElements().get(l))).getFacilityId().toString().equalsIgnoreCase(this.knowledges.getKnowledgesByPersonId().get(this.plans.get(i).get(j).getPerson().getId()).getActivities(true).get(k).getFacility().getId().toString())){
							notIn = false;
							break;
						}
					}
					if (notIn) log.warn("Prim act error in plan of person "+this.plans.get(i).get(j).getPerson().getId()+" for prim act "+this.knowledges.getKnowledgesByPersonId().get(this.plans.get(i).get(j).getPerson().getId()).getActivities(true).get(k));
				}
				for (int k=0;k<this.plans.get(i).get(j).getPlanElements().size()-2;k+=2){
					if (((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getType().equalsIgnoreCase("home")){
						if (((Activity)(this.plans.get(i).get(j).getPlanElements().get(k))).getFacilityId()!=this.knowledges.getKnowledgesByPersonId().get(this.plans.get(i).get(j).getPerson().getId()).getActivities(true).get(0).getFacility().getId()){
							log.warn("Non-primary home act found in plan of person "+this.plans.get(i).get(j).getPerson().getId());
							break;
						}
					}
				}
			}
		}
	}
	
	protected void analyze(){
	
		PrintStream stream1;
		try {
			stream1 = new PrintStream (new File(this.outputDir + "/analysis.xls"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		
		/* Analysis of activity chains */
		double averageACLength=0;
		stream1.println("Number of occurrences\tActivity chain");
		for (int i=0; i<this.activityChains.size();i++){
			double weight = this.plans.get(i).size();
			stream1.print(weight+"\t");
			double length = this.activityChains.get(i).size();
			averageACLength+=weight*(java.lang.Math.ceil(length/2));
			for (int j=0; j<length;j=j+2){
				stream1.print(((Activity)(this.activityChains.get(i).get(j))).getType()+"\t");
			}
			stream1.println();
		}
		stream1.println((averageACLength/this.population.getPersons().size())+"\tAverage number of activities");
		stream1.println();
		
		/*
	 	Analysis of legs 
		double averageDistance=0;
		double averageTime=0;
		stream1.println("Person ID\tInitial travel distance\tFinal travel distance\tInitial travel time\tFinal travel time");
		for (int i=0; i<this.plans.size();i++){
			
			for (int j=0; j<this.plans.get(i).size();j++){
				stream1.print(this.plans.get(i).get(j).getPerson().getId()+"\t");
				double distance=0;
				double time=0;
				for (int k=1;k<this.plans.get(i).get(j).getPlanElements().size();k=k+2){
					distance+=((Leg)(this.plans.get(i).get(j).getPlanElements().get(k))).getRoute().getDistance();
					time +=((Leg)(this.plans.get(i).get(j).getPlanElements().get(k))).getTravelTime();
				}
				averageDistance+=distance;
				averageTime+=time;
				stream1.println(distance+"\t"+time);
			}
		}
		stream1.println();
		stream1.println((averageDistance/this.population.getPersons().size())+"\tAverage distance\t"
				+(averageTime/this.population.getPersons().size())+"\tAverage travel time");
		
		
		stream1.close();
		*/
	}
	
	protected boolean checkForEquality (Plan plan, List<PlanElement> activityChain){
		
		if (plan.getPlanElements().size()!=activityChain.size()){
		
			return false;
		}
		else{
			ArrayList<String> acts1 = new ArrayList<String> ();
			ArrayList<String> acts2 = new ArrayList<String> ();
			for (int i = 0;i<plan.getPlanElements().size();i=i+2){
				acts1.add(((Activity)(plan.getPlanElements().get(i))).getType().toString());				
			}
			for (int i = 0;i<activityChain.size();i=i+2){
				acts2.add(((Activity)(activityChain.get(i))).getType().toString());				
			}		
			return (acts1.equals(acts2));
		}
	}			
	

	public static void main(final String [] args) {
//		final String populationFilename = "./examples/equil/plans100.xml";
//		final String networkFilename = "./examples/equil/network.xml";
		final String populationFilename = "./plans/output_plans.xml.gz";
//		final String populationFilename = "./output/Test1/ITERS/it.0/0.plans.xml.gz";
		final String networkFilename = "./test/scenarios/chessboard/network.xml";
		final String facilitiesFilename = "./test/scenarios/chessboard/facilities.xml";

		final String outputDir = "./plans/";

		ScenarioImpl scenario = new ScenarioImpl();
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
		new MatsimFacilitiesReader(scenario.getActivityFacilities()).readFile(facilitiesFilename);
		new MatsimPopulationReader(scenario).readFile(populationFilename);

		AnalysisSelectedPlansActivityChains sp = new AnalysisSelectedPlansActivityChains(scenario.getPopulation(), scenario.getKnowledges(), outputDir);
		sp.analyze();
		sp.checkCorrectness();
		
		log.info("Analysis of plan finished.");
	}

}

