package simulation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import simulation.environment.Environment;
import simulation.physicalobjects.GeometricCalculator;
import simulation.physicalobjects.PhysicalObject;
import simulation.physicalobjects.PhysicalObjectType;
import simulation.robot.Robot;
import simulation.util.Arguments;
import simulation.util.SimRandom;

import comm.FileProvider;

public class Simulator implements Serializable {

	private static int maxNumberRobots = 100000;
	protected Double time = Double.valueOf(0);
	protected double timeDelta = 0.1;
	protected Environment environment;
	protected Random random;
	protected FileProvider fileProvider = FileProvider.getDefaultFileProvider();
	private int numberRobots = 0;
	private int numberPhysicalObjects = 0;
	private ArrayList<Updatable> callbacks = new ArrayList<Updatable>(); 
	private GeometricCalculator calculator;
	private boolean stopSimulation = false;
	
	private HashMap<String,Arguments> arguments = new HashMap<String,Arguments>(); 
	
	public Simulator(Random random, HashMap<String,Arguments> arguments) {
		this.random = random;
		this.arguments = arguments;
		calculator = new GeometricCalculator();
	}
	
	public Double getTime(){
		return time;
	}
	
	public GeometricCalculator getGeoCalculator(){
		return this.calculator;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public Environment getEnvironment() {
		return environment;
	}

	public Random getRandom() {
		return random;
	}

	public FileProvider getFileProvider() {
		return fileProvider;
	}

	public void setFileProvider(FileProvider fileProvider) {
		this.fileProvider = fileProvider;
	}

	public void addCallback(Updatable r) {
		callbacks.add(r);
	}
	
	public void removeCallback(Updatable r) {
		callbacks.remove(r);
	}

	public void performOneSimulationStep(Double time) {
		this.time = time;
		
		// Update the readings for all the sensors:
		updateAllRobotSensors(time);
		// Call the controllers:
		updateAllControllers(time);
		// Compute the actions of the robot's actuators on the environment and on itself
		updateAllRobotActuators(time);
		// Update non-robot objects in the environment
		updateEnvironment(time);
		// Update the positions of everything
		updatePositions(time);
		
		for (Updatable r : callbacks) {
			r.update(time);
		}
	}

	protected void updateAllControllers(Double time) {
		for (Robot r : environment.getRobots()) {
			r.getController().controlStep(time);
		}
	}

	protected void updateEnvironment(Double time) {
		environment.update(time);
	}

	protected void updateAllRobotSensors(double time) {
		ArrayList<PhysicalObject> teleported = environment.getTeleported();
		for (Robot r : environment.getRobots()) {
			r.updateSensors(time, teleported);
		}
		environment.clearTeleported();
	}

	protected void updateAllRobotActuators(double time) {
		//TODO is this really necessary??
		ArrayList<Robot> robots = (ArrayList<Robot>) environment.getRobots().clone();
		Collections.shuffle(robots,random);
		for (Robot r : robots) {
			r.updateActuators(time, timeDelta);
		}
	}

	protected void updatePositions(double time) {
		environment.updateCollisions(time);
	}

	public void simulate(int numIterations) {
		for (time = Double.valueOf(0); time < numIterations && !stopSimulation; time++) {
			performOneSimulationStep(time);
		}
	}

	public double getTimeDelta() {
		return timeDelta;
	}

	public int getAndIncrementNumberPhysicalObjects(PhysicalObjectType type) {
		return type == PhysicalObjectType.ROBOT ? this.numberRobots++ : maxNumberRobots + numberPhysicalObjects++;
	}
	
	public void stopSimulation() {
		stopSimulation = true;
	}
	
	public HashMap<String, Arguments> getArguments() {
		return arguments;
	}
}