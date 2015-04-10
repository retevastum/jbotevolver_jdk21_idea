package neat;

import java.util.ArrayList;
import java.util.Random;

import result.Result;
import neat.continuous.NEATContinuousNetwork;
import neat.evaluation.SingleObjective;

import org.encog.ml.MLMethod;
import org.encog.neural.neat.NEATNetwork;

import simulation.Simulator;
import simulation.robot.Robot;
import simulation.util.Arguments;
import taskexecutor.tasks.JBotEvolverTask;
import evolutionaryrobotics.JBotEvolver;
import evolutionaryrobotics.evaluationfunctions.EvaluationFunction;
import evolutionaryrobotics.neuralnetworks.NeuralNetworkController;

public class NEATGenerationalTask extends JBotEvolverTask {

	private int sampleNumber;
	protected Random random;
	private JBotEvolver jBotEvolver;
	protected MLMethod method;
	protected double fitness = 0;
	protected long threadId;
	protected int nSamples = 1;
	protected int objectiveId = 1;

	public NEATGenerationalTask(JBotEvolver jBotEvolver, int sampleNumber, MLMethod method, long seed, long threadId, int nSamples) {
		super(jBotEvolver);
		this.sampleNumber = sampleNumber;
		this.method = method;
		this.jBotEvolver = jBotEvolver;
		this.random = new Random(seed);
		this.threadId = threadId;
		this.nSamples = nSamples;
	}
	
	@Override
	public void run() {
		
		for(int i = 0 ; i < nSamples ; i++) {
			
			jBotEvolver.getArguments().get("--environment").setArgument("fitnesssample", i);
			
			Simulator simulator = jBotEvolver.createSimulator(new Random(random.nextLong()));
			simulator.setFileProvider(getFileProvider());
	
			ArrayList<Robot> robots = jBotEvolver.createRobots(simulator);
			//extended jbotevolver specific method
			setMLControllers(robots, method);
			simulator.addRobots(robots);
	
			EvaluationFunction eval;
			
			eval = EvaluationFunction.getEvaluationFunction(jBotEvolver.getArguments().get("--evaluation"));
			
			simulator.addCallback(eval);
			simulator.simulate();
			
			this.fitness+= eval.getFitness();
		}
		
		if(nSamples > 1)
			fitness/=nSamples;
	}
	
	private void setMLControllers(ArrayList<Robot> robots, MLMethod method) {
		for(Robot r : robots) {
			NeuralNetworkController controller = (NeuralNetworkController) r.getController();
			WrapperNetwork wrapper = (WrapperNetwork)controller.getNeuralNetwork();
			wrapper.setNetwork(method);
			controller.setNeuralNetwork(wrapper);
			controller.reset();
		}
	}

	@Override
	public Result getResult() {
		SimpleObjectiveResult fr = new SimpleObjectiveResult(objectiveId, this.sampleNumber, this.fitness, this.threadId);
		return fr;
	}
}