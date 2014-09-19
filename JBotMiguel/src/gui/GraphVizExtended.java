package gui;

import evolutionaryrobotics.neuralnetworks.CTRNNMultilayer;
import evolutionaryrobotics.neuralnetworks.NeuralNetwork;
import gui.util.GraphViz;
import neat.ERNEATNetwork;
import neat.continuous.NEATContinuousNetwork;

import org.encog.neural.neat.NEATLink;
import org.encog.neural.neat.NEATNetwork;

public class GraphVizExtended extends GraphViz {
	
   public GraphVizExtended() {
	   super();
   }
   
   public GraphVizExtended(NeuralNetwork network) {
	   this();
	   this.network = network;
	   setupNetwork();
   }
   
   public GraphVizExtended(int input, int hidden, int output) {
	   super(input,hidden,output);
   }
   
   @Override
	public void changeNeuralNetwork(NeuralNetwork n) {
		super.changeNeuralNetwork(n);
		setupNetwork();
	}
   
   protected void setupNetwork() {
	   this.graph = new StringBuilder();
	   
	   addln(start_graph());
	   
	   if(network != null) {
		   this.input = network.getNumberOfInputNeurons();
		   this.output = network.getNumberOfOutputNeurons();
		   
		   if(network instanceof CTRNNMultilayer) {
			  super.setupNetwork();
		   }
		   
		   if(network instanceof ERNEATNetwork) {
			   createNEATNetwork();
			   connectNEATNetwork();
		   }
	   }
	   addln(end_graph());
   }
   
   protected void createNEATNetwork() {
	   String result = "node [shape=circle,fixedsize=true,width=0.9];";
	   
	   NEATNetwork net = ((ERNEATNetwork)network).getNEATNetwork();
	   
	   result+="size=\"18,18\"; ranksep=\"2.2 equally\"";
	   
	   result+="{rank=same;";
	   for(int i = 0 ; i < net.getInputCount()+1 ; i++) {
		   double state = ((int)(net.getPostActivation()[i]*100))/100.0;
		   result+=i+" [label=\""+i+"\n("+state+")\"] ";
		   System.out.println("graph.addNode("+i+");");
	   }
	   result+=";}";
	   
	   int hidden = net.getActivationFunctions().length-(net.getInputCount()+net.getOutputCount()+1);
	   if(hidden > 0) {
		   result+="{rank=same;";
		   for(int i = 0 ; i < hidden ; i++) {
			   String text = "";
			   
			   int id = (net.getOutputCount()+net.getOutputIndex()+i);
			   
			   if(net instanceof NEATContinuousNetwork) {
				   NEATContinuousNetwork cnet = (NEATContinuousNetwork)net;
				   if(cnet.getNeurons()[id].isDecayNeuron())
					   text=" c";
			   }
			   
			   double state = ((int)(net.getPostActivation()[id]*100))/100.0;
			   result+=id+" [label=\""+id+"\n("+state+text+")\"] ";
			   System.out.println("graph.addNode("+id+");");
		   }
		   result+=";}";
	   }
	   
	   result+="{rank=same;";
	   for(int i = 0 ; i < net.getOutputCount() ; i++) {
		   int id = i+net.getOutputIndex();
		   double state = ((int)(net.getPostActivation()[id]*100))/100.0;
		   result+=id+" [label=\""+id+"\n("+state+")\"] ";
		   System.out.println("graph.addNode("+id+");");
	   }
	   result+=";}";
	   
	   addln(result);
   }
   
   protected void connectNEATNetwork() {
	   
	   NEATLink[] links = ((ERNEATNetwork)network).getNEATNetwork().getLinks();
	   
	   for(NEATLink l : links) {
		   int from = l.getFromNeuron();
		   int to = l.getToNeuron();
		   double w = ((int)(l.getWeight()*100))/100.0;
		   System.out.println("graph.addLink("+from+","+to+");");
		   addln(from+" -> "+to+" [label=\" "+w+"\", "
		   		+ (w < 0 ? "arrowhead = empty, color = \"red\", " : "color = \"green\", ")
		   		+ "penwidth = "+Math.max(Math.abs((int)Math.round(w)),1)
		   		+ "];");
	   }
	   
   }
}