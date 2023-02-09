import flanagan.math.MaximisationFunction;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


// Class to demonstrate Maximisation method, Maximisation nelderMead
class MathEngineBLE {

    static double bestLikelihood;

    static OptimizerBLE[] swarmPositioningOptimizers;

    static void generateTheOptimizerThreads(){
        // System.out.println(MathEngineBLE.swarmPositioningOptimizers.length);
        // Generate as many Optimizers as the available Cores on the machine
        for (int thread = 0; thread< SimApp.threads; thread++){
            // Generate a new optimizer and add it to the thread mapper
            MathEngineBLE.swarmPositioningOptimizers[thread] = new OptimizerBLE();
        }
    }

    static void align_Swarm(){
        SimpleRegression regr = new SimpleRegression();

        // First compute the regression
        for (Node current_Node: SimApp.nodeID_to_nodeObject.values()) {
            regr.addData(current_Node.current_relative_x, current_Node.current_relative_y);
        }

        double y_intercept = regr.getIntercept();
        double rad_rotation = -FastMath.atan(regr.getSlope());

        // Prepare these variables to hold the new extent
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        // Rotate all Nodes according to the calculated slope
        for (Node current_Node: SimApp.nodeID_to_nodeObject.values()) {

            final double newX = (current_Node.current_relative_x)*Math.cos(rad_rotation) - (current_Node.current_relative_y-y_intercept)*Math.sin(rad_rotation);
            final double newY = (current_Node.current_relative_x)*Math.sin(rad_rotation) + (current_Node.current_relative_y-y_intercept)*Math.cos(rad_rotation);

            // Update the extent variables
            if (newX<minX){
                minX = newX;
            }
            if (newX>maxX){
                maxX = newX;
            }

            if (newY<minY){
                minY = newY;
            }
            if (newY>maxY){
                maxY = newY;
            }

            current_Node.current_relative_x = newX;
            current_Node.current_relative_y = newY;
        }

        // Calculate the vector shift required to place the Swarm's center of mass at the Origin
        final double swarmCenterX = -(minX + ((maxX - minX)/2));
        final double swarmCenterY = -(minY + ((maxY - minY)/2));

        // After having rotated all Nodes, we will displace them so that their mass center is at 0,0
        for (Node current_Node: SimApp.nodeID_to_nodeObject.values()) {
            current_Node.current_relative_x = current_Node.current_relative_x + swarmCenterX;
            current_Node.current_relative_y = current_Node.current_relative_y + swarmCenterY;
        }

    }

    static List<Integer> order_NodeIDs_byPositionX(){

        List<Integer> orderedByLastCycleOrientation_NodeIDs = new ArrayList<>();

        Integer[] nodeIDs = new Integer[SimApp.nodeID_to_nodeObject.size()];
        double[] posX = new double[SimApp.nodeID_to_nodeObject.size()];

        for (int i = 0; i< SimApp.nodeID_to_nodeObject.size(); i++){
            nodeIDs[i] = i;
            posX[i] = SimApp.nodeID_to_nodeObject.get(i+1).current_relative_x;
        }

        Arrays.sort(nodeIDs, Comparator.comparingDouble(o -> posX[o]));

        for (int i:nodeIDs){
            orderedByLastCycleOrientation_NodeIDs.add(i+1);
        }

        return orderedByLastCycleOrientation_NodeIDs;
    }

    static double[] findBestPositionForCurrentNode(Node currentNode, int cycleCounter, int stepCounter){

        Core.getEffectiveNeighbors(currentNode);

        MathEngineBLE.generateTheOptimizerThreads();

        // Start each thread worker or notify it to continue with the optimization (i.e. escape the waiting state)
        for (OptimizerBLE optimizer: swarmPositioningOptimizers){
            // Reset the maxLikelihood
            MathEngineBLE.bestLikelihood = Double.NEGATIVE_INFINITY;
            optimizer.start();
        }
        try {
            for (OptimizerBLE optimizer: swarmPositioningOptimizers){
                optimizer.join();
            }
        }
        catch (InterruptedException ignored) {
            // In this case, the user has requested to stop the optimization
        }

        double[] best_params = new double[0];
        // At this point, all thread workers have finished
        for (OptimizerBLE optimizer: swarmPositioningOptimizers){
            if (MathEngineBLE.bestLikelihood < optimizer.optimal_probability){
                MathEngineBLE.bestLikelihood = optimizer.optimal_probability;
                best_params = optimizer.best_params;
            }
        }

        //System.out.println(Arrays.toString(best_params));

        if (best_params.length == 0){
            SimApp.appendToTextArea("Optimization was not successful for current node.");
            return(null);
        }
        else{
            SimApp.appendToTextArea("Cycle:" + cycleCounter + " " +
                    "   Step:" + stepCounter + " " +
                    "   Node:" + currentNode.id + " " +
                    "   Pos: [x= " + SimApp.two_decimals_formatter.format(best_params[0]).replace(",", ".") +
                    ", y= " + SimApp.two_decimals_formatter.format(best_params[1]).replace(",", ".") + "]");

            // get values at optimum
            return(best_params);
        }
    }
}

// Class to evaluate the Circular Distance Likelihood function
// Here, the rss is considered as the parameter
class DistanceLikelihoodBLE implements MaximisationFunction{

    Node attachedNode;
    double measurement;
    double minPlotX = 0.0D;
    double maxPlotX = 0.0D;
    double minPlotY = 0.0D;
    double maxPlotY = 0.0D;

    String ProductLikelihoodComponent_WolframOBJ = null;

    public DistanceLikelihoodBLE(Node attachedNode) {
        this.attachedNode = attachedNode;
    }

    // Set the Mathematica Likelihood Objects
    void updateProductLikelihoodComponentWolframOBJ() {
        // The original form for BLE RSS
        // this.ProductLikelihoodComponent_WolframOBJ = "(25.029205084016887*E^(0.07848246*" + measurement + " -0.005271437722209233*(53.03569084 + " + measurement + " + 12.7416977*Log[(" + this.attachedNode.current_relative_x + " -distanceX)^2 + (" + this.attachedNode.current_relative_y + " -distanceY)^2])^2))*\n";

        // The simplified form after applying the logarithmic rule for products
        this.ProductLikelihoodComponent_WolframOBJ = "(0.07848246*" + measurement +
                " -0.005271437722209233*(53.03569084 + " + measurement + " + 12.7416977*Log[(" + this.attachedNode.current_relative_x + " -distanceX)^2 + (" + this.attachedNode.current_relative_y + " -distanceY)^2])^2)+\n";
    }

    // Evaluation function
    public double function(double[] coords) {
        // The original form for BLE RSS
//        return Math.exp(0.07848246D * measurement - 0.005271437722209233D *
//                    Math.pow(53.03569084D + measurement + 12.7416977D *
//                        Math.log(Math.sqrt(Math.pow(this.attachedNode.current_relative_x - coords[0], 2.0D) + Math.pow(this.attachedNode.current_relative_y - coords[1], 2.0D)))
//                    , 2.0D)
//               );

        // The simplified form after applying the logarithmic rule for products
        return 0.07848246D * measurement - 0.005271437722209233D *
                    Math.pow(53.03569084D + measurement + 12.7416977D *
                        Math.log(Math.sqrt(Math.pow(this.attachedNode.current_relative_x - coords[0], 2.0D) + Math.pow(this.attachedNode.current_relative_y - coords[1], 2.0D)))
                    , 2.0D);
    }

    // Method to set the parameters of the DistanceLikelihood that belongs to the corresponding Node
    // This method is utilised only when the Node is among the "remote ones"
    void updateMeasurementAndExtentReach(double measurement){
        this.measurement = measurement;

        this.minPlotX = attachedNode.current_relative_x - SimApp.initial_Map_Extend;
        this.maxPlotX = attachedNode.current_relative_x + SimApp.initial_Map_Extend;
        this.minPlotY = attachedNode.current_relative_y - SimApp.initial_Map_Extend;
        this.maxPlotY = attachedNode.current_relative_y + SimApp.initial_Map_Extend;
    }
}

class OptimizerBLE extends Thread {

    // Class to evaluate the Position Likelihood function
    // Here, the measurement is considered as the parameter
    static class PositionLikelihoodBLE implements MaximisationFunction {
        // Evaluation function
        public double function(double[] coordinates) {

            double total_likelihood = 0.0D;

            for (int nodeID: SimApp.effective_remoteNodes){
                Node remoteNode = SimApp.nodeID_to_nodeObject.get(nodeID);
                double likelihood = remoteNode.cdl_ble.function(coordinates);

                total_likelihood = total_likelihood + likelihood;
//                System.out.println(total_likelihood);
                if (total_likelihood==Double.NEGATIVE_INFINITY){
                    break;
                }
            }

            return total_likelihood;
        }
    }
    flanagan.math.Maximisation NodePosMax = new flanagan.math.Maximisation();

    final PositionLikelihoodBLE position_likelihood;

    double optimal_probability = Double.NEGATIVE_INFINITY;

    double[] best_params = new double[0];

    OptimizerBLE() {
        NodePosMax.suppressNoConvergenceMessage();

        // Create instance of class holding function to be optimised
        position_likelihood = new PositionLikelihoodBLE();
    }

    public void run() {
        final long force_stop_time = System.currentTimeMillis() + SimApp.max_optimization_time_per_thread;

        for (int optimization_iteration = 0; optimization_iteration< SimApp.optimization_iterations_per_thread; optimization_iteration++){

            // Check whether we are out of time
            if (System.currentTimeMillis() > force_stop_time){
                SimApp.appendToTextArea("Max optimization time per thread reached.");
                break;
            }

            double randomX = MapField.global_minPlotX + SimApp.random.nextDouble() * (MapField.global_maxPlotX - MapField.global_minPlotX);
            double randomY = MapField.global_minPlotY + SimApp.random.nextDouble() * (MapField.global_maxPlotY - MapField.global_minPlotY);

            //System.out.println(randomX + ", " + randomY);

            // As initial estimates use Node's current position
            //double[] start = {currentNode.current_relative_x, currentNode.current_relative_y};
            double[] start = {randomX, randomY};

            // initial step sizes
            double[] step = {SimApp.step_size, SimApp.step_size};

            // convergence tolerance
            double ftol = SimApp.ftol;

            // Nelder and Mead optimization procedure
            NodePosMax.nelderMead(position_likelihood, start, step, ftol);

            if (optimal_probability < NodePosMax.getMaximum()) {
                optimal_probability = NodePosMax.getMaximum();
                best_params = NodePosMax.getParamValues();
                //System.out.println("Iteration: " + optimization_iteration + ": " + optimal_probability);
            }
        }
        //System.out.println("Final: " + highest_probability + " " + Arrays.toString(best_params) + "\n");
    }
}
