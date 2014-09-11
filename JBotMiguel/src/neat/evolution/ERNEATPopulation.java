package neat.evolution;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import neat.ERNEATNetwork;
import neat.JBotEvolverRandomFactory;
import neat.continuous.ERNEATContinuousNetwork;
import neat.continuous.MutatePerturbNeuronDecay;
import neat.continuous.MutateResetNeuronDecay;
import neat.continuous.NEATContinuousCODEC;
import neat.continuous.NEATMutateAddContinuousNode;
import neat.continuous.NEATMutateNeuronDecays;
import neat.continuous.SelectContinuousProportion;
import neat.evaluation.NEATEvaluation;
import neat.evaluation.TaskStatistics;
import neat.mutations.NEATCrossoverJBot;
import neat.mutations.NEATMutateAddLinkJBot;
import neat.mutations.NEATMutateAddNodeJBot;

import org.encog.ml.MLMethod;
import org.encog.ml.ea.opp.CompoundOperator;
import org.encog.ml.ea.opp.selection.TruncationSelection;
import org.encog.neural.neat.NEATCODEC;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.training.NEATGenome;
import org.encog.neural.neat.training.opp.NEATMutateWeights;
import org.encog.neural.neat.training.opp.links.MutatePerturbLinkWeight;
import org.encog.neural.neat.training.opp.links.MutateResetLinkWeight;
import org.encog.neural.neat.training.opp.links.SelectProportion;
import org.encog.neural.neat.training.species.OriginalNEATSpeciation;

import simulation.Simulator;
import simulation.robot.Robot;
import simulation.util.Arguments;
import taskexecutor.TaskExecutor;
import controllers.Controller;
import evolutionaryrobotics.JBotEvolver;
import evolutionaryrobotics.evaluationfunctions.EvaluationFunction;
import evolutionaryrobotics.neuralnetworks.Chromosome;
import evolutionaryrobotics.neuralnetworks.NeuralNetwork;
import evolutionaryrobotics.neuralnetworks.NeuralNetworkController;
import evolutionaryrobotics.populations.Population;

public class ERNEATPopulation extends Population implements Serializable{

	protected int populationSize;
	protected int numberOfGenerations;
	protected int numberOfSamplesPerChromosome;
	protected int numberInputs, numberOutputs;
	protected NEATPopulation population;

	protected int generationsElapsed = 0;

	protected TrainEAJBot trainer;

	protected NEATEvaluation evaluator;

	protected Map<NEATNetwork, TaskStatistics> stats;

	protected double bestFitness, worstFitness, avgFitness;
	
	protected double addNodeRate = 0.02;
	protected double mutateLinkRate = 0.1;
	
	protected boolean continuousNetwork = false;

	public ERNEATPopulation(Arguments arguments) {
		super(arguments);
		populationSize = arguments.getArgumentAsIntOrSetDefault("size",50);
		numberOfGenerations = arguments.getArgumentAsIntOrSetDefault("generations",100);
		numberOfSamplesPerChromosome = arguments.getArgumentAsIntOrSetDefault("samples",10);
		
		addNodeRate = arguments.getArgumentAsDoubleOrSetDefault("addnoderate", addNodeRate);
		mutateLinkRate = arguments.getArgumentAsDoubleOrSetDefault("mutatelinkrate", mutateLinkRate);
	}

	public void createRandomPopulation(JBotEvolver jBotEvolver) {
		
		Simulator sim = jBotEvolver.createSimulator();
		Robot r = Robot.getRobot(sim, jBotEvolver.getArguments().get("--robots"));
		NeuralNetworkController c =(NeuralNetworkController)Controller.getController(sim, r, jBotEvolver.getArguments().get("--controllers"));
		NeuralNetwork network = c.getNeuralNetwork();
		numberInputs = network.getNumberOfInputNeurons();
		numberOutputs = network.getNumberOfOutputNeurons();
		
		if(network instanceof ERNEATContinuousNetwork)
			continuousNetwork = true;
		
		randomNumberGenerator = new Random(Integer.parseInt(jBotEvolver.getArguments().get("--random-seed").getCompleteArgumentString()));
		setGenerationRandomSeed(randomNumberGenerator.nextInt());
		if(continuousNetwork)
			population = new NEATEncogPopulation(numberInputs, numberOutputs, populationSize,true);
		else
			population = new NEATEncogPopulation(numberInputs, numberOutputs, populationSize);
		population.setInitialConnectionDensity(1.0);// not required, but speeds training
		population.setRandomNumberFactory(new JBotEvolverRandomFactory(generationRandomSeed));
		population.reset();

		//encog's structure requires the fitness function and the EA within the population
		this.evaluator = (NEATEvaluation)EvaluationFunction.getEvaluationFunction(jBotEvolver.getArguments().get("--evalmanager"));

		evaluator.setupObjectives(jBotEvolver.getArguments());
		
		trainer = constructPopulationTrainer();
	}
	
	public TrainEAJBot constructPopulationTrainer() {
		final TrainEAJBot result = new TrainEAJBot(population, this.evaluator);
		result.setThreadCount(getPopulationSize());
		
		OriginalNEATSpeciation speciation = new OriginalNEATSpeciation();
		//coefficient 3
		speciation.setConstMatched(3.0);
		//C_t
		speciation.setCompatibilityThreshold(3.0);
		result.setSpeciation(speciation);
		
		result.setSelection(new TruncationSelection(result, 0.2));
		
		final CompoundOperator weightMutation = new CompoundOperator();
		
		if(continuousNetwork) {
			weightMutation.getComponents().add(0.9*0.5,new NEATMutateWeights(new SelectProportion(1),new MutatePerturbLinkWeight(0.1)));
			weightMutation.getComponents().add(0.1*0.5,new NEATMutateWeights(new SelectProportion(1),new MutateResetLinkWeight()));
			
			weightMutation.getComponents().add(0.9*0.5,new NEATMutateNeuronDecays(new SelectContinuousProportion(1),new MutatePerturbNeuronDecay(0.1)));
			weightMutation.getComponents().add(0.1*0.5,new NEATMutateNeuronDecays(new SelectContinuousProportion(1),new MutateResetNeuronDecay()));
//			weightMutation.getComponents().add(0.9,new NEATMutateWeights(new SelectProportion(1),new MutatePerturbLinkWeight(0.1)));
//			weightMutation.getComponents().add(0.1,new NEATMutateWeights(new SelectProportion(1),new MutateResetLinkWeight()));
		} else {
			weightMutation.getComponents().add(0.9,new NEATMutateWeights(new SelectProportion(1),new MutatePerturbLinkWeight(0.1)));
			weightMutation.getComponents().add(0.1,new NEATMutateWeights(new SelectProportion(1),new MutateResetLinkWeight()));
		}
		
		weightMutation.getComponents().finalizeStructure();
		result.setChampMutation(weightMutation);
		
		result.addOperation(0.8, weightMutation);
		result.addOperation(0.2, new NEATCrossoverJBot());
		
		
		if(continuousNetwork)
			result.addOperation(addNodeRate, new NEATMutateAddContinuousNode());
		else {
			result.addOperation(addNodeRate, new NEATMutateAddNodeJBot());
		}
		
		result.addOperation(mutateLinkRate, new NEATMutateAddLinkJBot());
		
		result.getOperators().finalizeStructure();

		if(continuousNetwork)
			result.setCODEC(new NEATContinuousCODEC());
		else
			result.setCODEC(new NEATCODEC());
		
		return result;
	}

	@Override
	public boolean evolutionDone() {
		return this.getNumberOfCurrentGeneration() >= this.getNumberOfGenerations();
	}

	@Override
	public int getNumberOfCurrentGeneration() {
		return generationsElapsed;
	}

	@Override
	public int getPopulationSize() {
		return this.populationSize;
	}

	public NEATGenome getBestGenome(){
		return (NEATGenome) this.population.getBestGenome();
	}

	public void evolvePopulation(JBotEvolver jBotEvolver, TaskExecutor taskExecutor) {
		
		generationsElapsed++;
		//System.out.println("GENERATION " + generationsElapsed);
		randomNumberGenerator.setSeed(getGenerationRandomSeed());
		
		setGenerationRandomSeed(randomNumberGenerator.nextInt());
		trainer.setRandomNumberFactory(new JBotEvolverRandomFactory(getGenerationRandomSeed()));
		
		evaluator.setupEvolution(jBotEvolver, taskExecutor, this.numberOfSamplesPerChromosome,this.generationRandomSeed);
		population.setRandomNumberFactory(new JBotEvolverRandomFactory(generationRandomSeed));
		trainer.iteration();
		
	}

	public void updateStatistics() {
		this.stats = evaluator.getObjectivesStatistics();

		updateFitnessStats();
	}

	private void updateFitnessStats() {
		//the first objective is the one related to task performance
		String objectiveKey = this.evaluator.getObjectiveKey(1);
		this.worstFitness = Double.MAX_VALUE;
		this.bestFitness = Double.MIN_VALUE;
		this.avgFitness = 0.0;
		
		for(NEATNetwork key : this.stats.keySet()){
			TaskStatistics stats = this.stats.get(key);
			double objectiveValue = stats.get(objectiveKey);
			this.bestFitness = Math.max(objectiveValue, bestFitness);
			this.worstFitness = Math.min(objectiveValue, worstFitness);
			avgFitness += objectiveValue;
		}
		
		this.avgFitness /= this.stats.size();
	}

	@Override
	public double getLowestFitness() {
		return this.worstFitness;
	}

	@Override
	public double getAverageFitness() {
		return this.avgFitness;
	}

	@Override
	public double getHighestFitness() {
		return this.bestFitness;
	}

	public int getNumberOfSpecies() {
		return this.population.getSpecies().size();
	}


	public void resetStatistics() {
		this.stats.clear();
		this.evaluator.resetStatistics();
	}

	public Map<NEATNetwork, TaskStatistics> getStatistics(){
		return this.stats;
	}
	
	@Override
	public void setupIndividual(Robot r) {
		NEATGenome best = getBestGenome();
		NEATNetwork net = (NEATNetwork) population.getCODEC().decode(best);
		setIndividualMLController(r, net);
		
	}
	
	public void setMLControllers(ArrayList<Robot> robots, MLMethod method) {
		for(Robot r : robots) {
			setIndividualMLController(r, method);
		}
	}
	
	public void setIndividualMLController(Robot r, MLMethod method) {
		NeuralNetworkController controller = (NeuralNetworkController) r.getController();
		ERNEATNetwork wrapper = (ERNEATNetwork)controller.getNeuralNetwork();
		wrapper.setNEATNetwork((NEATNetwork)method);
		controller.setNeuralNetwork(wrapper);
		controller.reset();
	}

	/**************************************************************************
	 **************************************************************************
	 **************************************************************************
	 * not used.
	 **************************************************************************
	 **************************************************************************
	 **************************************************************************/

	@Override
	public int getNumberOfChromosomesEvaluated() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Chromosome getNextChromosomeToEvaluate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setEvaluationResult(Chromosome chromosome, double fitness) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setEvaluationResultForId(int pos, double fitness) {
		// TODO Auto-generated method stub

	}

	@Override
	public Chromosome getBestChromosome() {
		NEATNetwork net = (NEATNetwork)population.getCODEC().decode(population.getBestGenome());
		return new Chromosome(ERNEATNetwork.getWeights(net), 1);
	}

	@Override
	public Chromosome[] getTopChromosome(int number) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Chromosome getChromosome(int chromosomeId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void createNextGeneration() {
		// TODO Auto-generated method stub

	}

	@Override
	public void createRandomPopulation() {
		// TODO Auto-generated method stub

	}



	/**
	 * end of not methods not used.
	 */


}
