import org.jzy3d.chart.ContourChart;
import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;
import org.jzy3d.colors.colormaps.ColorMapRainbow;
import org.jzy3d.contour.DefaultContourColoringPolicy;
import org.jzy3d.contour.MapperContourPictureGenerator;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.maths.Range;
import org.jzy3d.plot3d.builder.Mapper;
import org.jzy3d.plot3d.builder.SurfaceBuilder;
import org.jzy3d.plot3d.builder.concrete.OrthonormalGrid;
import org.jzy3d.plot3d.primitives.Scatter;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.primitives.axis.ContourAxisBox;
import org.jzy3d.plot3d.rendering.canvas.Quality;
import org.jzy3d.plot3d.text.ITextRenderer;
import org.jzy3d.plot3d.text.drawable.DrawableTextWrapper;
import org.jzy3d.plot3d.text.renderers.TextRenderer;
import org.jzy3d.plot3d.transform.squarifier.XZSquarifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;


public class Core {
    static String ProductFinalFunctionObject;

    static boolean init() throws Exception {
        SimApp.appendToTextArea("Initializing SwarmEngine");

        if (SimApp.uwb_model){
            MathEngineUWB.generateTheOptimizerThreads();
        }
        else if (SimApp.ble_model){
            MathEngineBLE.generateTheOptimizerThreads();
        }

        if (!parseDB()){
            return false;
        }

        // computeInitialCrossDistances();
        // Use this for debugging and to set the nodes to true Pos
        // reset_CurrentNodePos_to_TruePos();
        sortNodesByBeliefsStrength();

        // This is the first Map_Extent update to set the Global Min/Max X/Y to be ready for even the first optimization
        MapField.updateMapExtent();

        return true;
    }

    static void getEffectiveNeighbors(Node currentNode) {

        // Find the closest remote Nodes to it
        SimApp.effective_remoteNodes = new ArrayList<>();

        // System.out.println("Getting Nodes with effective beliefs for node: " + currentNode.id);

        //Get the Measurements from all other Nodes
        for (HashMap.Entry<Integer, Double> entry : currentNode.measurement_from_node.entrySet()) {

            int remoteNode = entry.getKey();
            double measurement_value = entry.getValue();

            // Ignore all measurement values that are not described by our model
            // System.out.println("Checking if measurement " + measurement_value + " between remote Node " + remoteNode + " and current Node " + currentNode.id + " is at least " + SimApp.min_effective_measurement);

            if (SimApp.uwb_model){
                if ((measurement_value <= SimApp.min_effective_measurement) && (remoteNode != currentNode.id)) {
                    SimApp.effective_remoteNodes.add(remoteNode);
                    //System.out.println("Effective Node " + remoteNode + " added for " + currentNode + " due to measurement " + measurement_value);
                    SimApp.nodeID_to_nodeObject.get(remoteNode).cdl_uwb.updateMeasurementAndExtentReach(measurement_value);
                }
            }
            else if (SimApp.ble_model){
                if ((measurement_value >= -SimApp.min_effective_measurement) && (remoteNode != currentNode.id)) {
                    SimApp.effective_remoteNodes.add(remoteNode);
                    SimApp.nodeID_to_nodeObject.get(remoteNode).cdl_ble.updateMeasurementAndExtentReach(measurement_value);
                }
            }
        }
    }

    static private void sortNodesByBeliefsStrength(){
        // Create a Node-Popularity mapping (based on the average Beliefs-Strength from the k strongest measurements)
        HashMap<Integer, Double> node_popularity = new HashMap<>();

        for (Entry<Integer, Node> current_NodeEntry: SimApp.nodeID_to_nodeObject.entrySet()) {
            int current_NodeID = current_NodeEntry.getKey();
            Node current_Node = current_NodeEntry.getValue();

            List<Double> measurement_values_list = new ArrayList<>(){};

            measurement_values_list.addAll(current_Node.measurement_from_node.values());

            // Keep only the 5 strongest signals
            Collections.sort(measurement_values_list);

            if (measurement_values_list.size() > SimApp.kNearestNeighbours_for_BeliefsStrength){
                node_popularity.put(current_NodeID, getAverage(measurement_values_list.subList(0, SimApp.kNearestNeighbours_for_BeliefsStrength)));
            }
            else {
                node_popularity.put(current_NodeID, getAverage(measurement_values_list));
            }
        }

        SimApp.OrderedByBeliefsStrength_NodeIDs = new ArrayList<>();

//        System.out.println(node_popularity);

        node_popularity.entrySet().stream()
                .sorted(HashMap.Entry.comparingByValue())
                .forEachOrdered(x -> SimApp.OrderedByBeliefsStrength_NodeIDs.add(x.getKey()));

//        System.out.println(SimApp.OrderedByBeliefsStrength_NodeIDs);
    }

    static private double getAverage(List <Double> items) {
        double sum = 0;
        if(!items.isEmpty()) {
            for (double mark: items) {
                sum += mark;
            }
            return sum/items.size();
        }
        return sum;
    }

    static private void constrainLongDistances(){
        // At this point we have considered all recorded pair-measurements.
        // However, we also need to consider all pairs for which no measurement exists. These relations shall be used
        // to constraint close distances
        for (Entry<Integer, Node> current_NodeEntry: SimApp.nodeID_to_nodeObject.entrySet()) {
            int current_NodeID = current_NodeEntry.getKey();
            Node current_Node = current_NodeEntry.getValue();

            for (Entry<Integer, Node> remote_NodeEntry: SimApp.nodeID_to_nodeObject.entrySet()) {
                int remote_NodeID = remote_NodeEntry.getKey();
                Node remote_Node = remote_NodeEntry.getValue();

                // Avoid considering the same Node
                if (current_NodeID != remote_NodeID){
                    //System.out.println(current_NodeID + " to " + remote_NodeID + ": " + );
                }
            }
        }
    }

    static private void computeInitialCrossDistances(){
        // At this point, we have already generated all involved Nodes

        // Update all the distances
        for (Entry<Integer, Node> current_NodeEntry: SimApp.nodeID_to_nodeObject.entrySet()) {
            int current_NodeID = current_NodeEntry.getKey();
            Node current_Node = current_NodeEntry.getValue();

            for (Entry<Integer, Node> remote_NodeEntry: SimApp.nodeID_to_nodeObject.entrySet()) {
                int remote_NodeID = remote_NodeEntry.getKey();
                Node remote_Node = remote_NodeEntry.getValue();

                // Avoid considering the same Node
                if(current_NodeID != remote_NodeID){
                    // Update for current Node,
                    // both the true and current (due to initial randomness) Distance towards the remote Node
                    double current_distance_to_remoteNode = current_Node.getDistanceToNode(remote_Node, false);
                    current_Node.current_distance_to_node.put(remote_NodeID, current_distance_to_remoteNode);

                    double true_distance_to_remoteNode = current_Node.getDistanceToNode(remote_Node, true);
                    current_Node.true_distance_to_node.put(remote_NodeID, true_distance_to_remoteNode);

                    //System.out.println(current_NodeID + ":[" + current_Node.true_relative_x + "," + current_Node.true_relative_y + "]- " + remote_NodeID + ":[" + remote_Node.true_relative_x + "," + remote_Node.true_relative_y + "]: DistanceToRemoteNode= " + current_distance_to_remoteNode);
                }
            }
        }
    }

    static private boolean parseDB() throws Exception {
        // Loading the database
        SimApp.appendToTextArea("Parsing Node DB");

        // Open the file
        FileInputStream fstream = new FileInputStream(SimApp.input_file_path);
        BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

        String strLine;
        int parsed_measurements = 0;

        // Data format in the DB file is important!
        String positions_header = "#POSITIONS GROUND TRUTH#";
        
        String model = null;
        
        if (SimApp.ble_model){
            model = "RSS";
        }
        else if (SimApp.uwb_model){
            model = "TIME";
        }

        String measurement_header = "#" + model + "_" + SimApp.evaluated_iteration + "#";

        String current_parsing_type = null;

        //Read File Line By Line
        while ((strLine = br.readLine()) != null)   {

            // Try to detect a flag
            if (strLine.equals(positions_header)){
                current_parsing_type = positions_header;
                continue;
            }
            else if (strLine.equals(measurement_header)){
                current_parsing_type = "#";
                continue;
            }
            else if (strLine.equals("")){
                current_parsing_type = null;
                continue;
            }

            if (current_parsing_type != null){
                // Being here means that we haven't escaped above with a continuation due to successful header_check matching
                // Handle accordingly the parsed data
                if (current_parsing_type.equals(positions_header)){
                    // Start with the position
                    String[] position_parts = strLine.split(":");
                    String[] coords = position_parts[1].split(";");

                    int parsed_nodeID = Integer.parseInt(position_parts[0].trim());
                    double posX = Double.parseDouble(coords[0].trim());
                    double posY = Double.parseDouble(coords[1].trim());

                    // Create the Node object and add it to the Node Map
                    Node new_node = new Node(parsed_nodeID, posX, posY);
                    SimApp.nodeID_to_nodeObject.put(parsed_nodeID, new_node);
                    SimApp.appendToTextArea(new_node.get_report());
                }

                else {
                    String[] measurement_value_parts = strLine.split(":");

                    String[] str_nodes = measurement_value_parts[0].split(";");
                    int nodeA = Integer.parseInt(str_nodes[0]);
                    int nodeB = Integer.parseInt(str_nodes[1]);

                    double measurement_value = Double.parseDouble(measurement_value_parts[1].split("&")[0]);

                    // Update for 1st Node, the measurement towards the 2nd Node
                    SimApp.nodeID_to_nodeObject.get(nodeA).measurement_from_node.put(nodeB, measurement_value);

                    parsed_measurements = parsed_measurements + 1;
                    // System.out.println("Node " + nodeA + " to Node " + nodeB + " measurement: " + measurement_value);
                }
            }
        }

        //Close the input stream
        fstream.close();

        SimApp.appendToTextArea("\nValid " + model + " measurements found in the database: " + parsed_measurements + "\n");

        return parsed_measurements != 0;
    }

    static void resumeSwarmPositioning() {

        // Check if the requested cycles have been reached. Since counting started from 0, we use equality to check.
        if (SimApp.optimization_cycles == SimApp.cycleCounter){
            System.out.println("Cycles finished");
            SimApp.stop_optimization = true;

            // At this point we can store the results.log for this iteration
            SimApp.writeString2File(
                    Paths.get(SimApp.output_iteration_results_folder_path,"results.log").toString(),
                    SimApp.outputTerminal.getText()
            );

            return;
        }

        // Check if there is no remaining step from previous unfinished cycles
        if (SimApp.temp_OrderedRemoteNodeIDs.size()==0){

            // When in GUI mode, we can align the swarm based on a principal spatial variation for better visualization
//             MathEngineUWB.align_Swarm(); or MathEngineBLE.align_Swarm();

            // We start a new Cycle. At this point, sortNodesByBeliefsStrength() has already ordered the nodes.
            SimApp.temp_OrderedRemoteNodeIDs.addAll(SimApp.OrderedByBeliefsStrength_NodeIDs);

            // Update also the progress counters
            SimApp.cycleCounter = SimApp.cycleCounter + 1;
            SimApp.stepCounter = 0;
        }

        // Check if the user wants to get results per step
        if (SimApp.results_per_step){

            SimApp.stepCounter = SimApp.stepCounter + 1;
            boolean cycle_end = SimApp.stepCounter == SimApp.nodeID_to_nodeObject.size();
            boolean export_plot = SimApp.export_ProductLikelihood_WolframPlot && cycle_end;

            Node currentNode = SimApp.nodeID_to_nodeObject.get(SimApp.temp_OrderedRemoteNodeIDs.remove(0));
            //System.out.println("Removing: " + currentNode + " TempList: " + RemoteNodeIDbyPopularity_tracker.size() + " OriginalList: " + NodeIDbyPopularity_originalList.size());

            //currentNode = 6; // Set this manually for debugging purposes
            
            double[] new_current_position = new double[0];

            if (SimApp.uwb_model){
                new_current_position = MathEngineUWB.findBestPositionForCurrentNode(
                        currentNode, SimApp.cycleCounter, SimApp.stepCounter);
            }
            else if (SimApp.ble_model){
                new_current_position = MathEngineBLE.findBestPositionForCurrentNode(
                        currentNode, SimApp.cycleCounter, SimApp.stepCounter);
            }
            
            if (new_current_position != null){
                currentNode.updateCurrentNodePos(new_current_position[0], new_current_position[1]);
            }

            MapField.updateMapExtent();

            // If the auto resumer is activated, rendering a new chart on every step might be too difficult for the GUI
            // We use the same optimization function to publish the likelihood at the end of a cycle
            if (SimApp.auto_resumer_btn.getState()){
                publishResults(
                        SimApp.cycleCounter, SimApp.stepCounter, currentNode, cycle_end, export_plot);
            }
            else{
                // Auto resumer is not activated. We have enough rendering time available to draw the step
                publishResults(
                        SimApp.cycleCounter, SimApp.stepCounter, currentNode,true, export_plot);
            }

            // To use this for debugging whenever needed
//            if (resetAll_CurrentNodePos_to_TruePos){
//                reset_CurrentNodePos_to_TruePos();
//            }
        }
        // Being here means that the user wants to get results per cycle
        else {
            boolean last_step = false;
            int remaining_steps = SimApp.temp_OrderedRemoteNodeIDs.size();

            for (int step = 0; step < remaining_steps; step++){

                SimApp.stepCounter = SimApp.stepCounter + 1;

                int currentNodeID = SimApp.temp_OrderedRemoteNodeIDs.remove(0);
                Node currentNode = SimApp.nodeID_to_nodeObject.get(currentNodeID);

                // Check if after removing this node, we ended up at the last optimization step
                if (SimApp.temp_OrderedRemoteNodeIDs.size()==0){
                    last_step = true;
                }

                //System.out.println("Removing: " + currentNodeID + " TempList: " + RemoteNodeIDbyPopularity_tracker.size() + " OriginalList: " + NodeIDbyPopularity_originalList.size());

                double[] new_current_position = new double[0];

                if (SimApp.uwb_model){
                    new_current_position = MathEngineUWB.findBestPositionForCurrentNode(
                            SimApp.nodeID_to_nodeObject.get(currentNodeID), SimApp.cycleCounter, SimApp.stepCounter);
                }
                else if (SimApp.ble_model){
                    new_current_position = MathEngineBLE.findBestPositionForCurrentNode(
                            SimApp.nodeID_to_nodeObject.get(currentNodeID), SimApp.cycleCounter, SimApp.stepCounter);
                }
                
                if (new_current_position != null){
                    currentNode.updateCurrentNodePos(new_current_position[0], new_current_position[1]);
                }

                MapField.updateMapExtent();

                // Check whether we are currently at the last step
                if (last_step){
                    // We use the same optimization function to publish the likelihood at the end of a cycle
                    publishResults(
                            SimApp.cycleCounter, SimApp.stepCounter, currentNode,
                            true, SimApp.export_ProductLikelihood_WolframPlot);
                }
            }
        }

        SimApp.appendToTextArea("Map Extent: (" + MapField.global_minPlotX + ", " + MapField.global_maxPlotX + "), (" + MapField.global_minPlotY + ", " + MapField.global_maxPlotY + ")");
        SimApp.appendToTextArea("=========== Optimization Finished ===========");
    }

    static void publishResults(int cycle, int step, Node currentNode,
                               boolean draw_cycle_chart, boolean export_plot) {

//        System.out.println("Publishing: " + SimApp.clean_evaluated_scenario_name + " Cycle:" + cycle);

        if ((export_plot && SimApp.plotContours != 0) ||
                (SimApp.headless_mode && (SimApp.optimization_cycles == SimApp.cycleCounter))){
            String NodePos_CMD_filename = Paths.get(
                    SimApp.output_iteration_results_folder_path,
                    "Positions_c" + cycle + "_s" + step + "_n" + currentNode.id + ".txt"
            ).toString();

            ArrayList<String>[] mathematica_components_for_plot_sections = collectComponentsForWolframPlotSections(currentNode);

            // Create the Wolfram command
            String ProductLikelihood_WolframCMD = buildFunctionSectionOfWolframCommand(mathematica_components_for_plot_sections[1]);

            // Generate the final Wolfram command which we need to execute for producing the likelihood plot
            String wolfram_export_command = ProductLikelihood_WolframCMD + mathematica_components_for_plot_sections[0].get(0);

            // Export the String to a file
            SimApp.writeString2File(NodePos_CMD_filename, wolfram_export_command);
        }

        // We draw only the state at the end of a cycle. This limitation is due to the limited drawing speed.
        if (draw_cycle_chart && !SimApp.headless_mode){
            drawCycleChart(currentNode);
        }
    }

    private static void drawCycleChart(Node currentNode) {
        boolean odd_cycles = SimApp.cycleCounter % 2 != 0;

        removeOldChartComponentFromContainer(odd_cycles); // Remove the previous chart's component from the container
        setLikelihoodFunction(currentNode, odd_cycles);

        // Right before the rendering of the plot, we stop measuring the lapsed time
        SimApp.min_cycle_time =  Math.min(
                SimApp.min_cycle_time, ((System.nanoTime() - SimApp.startTime)/1000000)
        );

//        System.out.println("Lapsed time: " + SimApp.min_cycle_time);

        updateContainer(odd_cycles);
    }

    private static void removeOldChartComponentFromContainer(boolean odd_cycles) {
        if (odd_cycles){
            SimApp.chart_component_container_odd.remove(SimApp.chart_component_odd);
        }
        else{
            SimApp.chart_component_container_even.remove(SimApp.chart_component_even);
        }
    }

    private static void updateContainer(boolean odd_cycles) {
        // The target container is expected to be behind its substitute container
        // Remove any previous components from this container
        if (odd_cycles){
            SimApp.chart_component_container_odd.add(SimApp.chart_component_odd);

            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            SimApp.app.setComponentZOrder(SimApp.chart_component_container_odd,0);
                        }
                    },
                    SimApp.min_cycle_time/2
            );
        }
        else{

            SimApp.chart_component_container_even.add(SimApp.chart_component_even);

            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            SimApp.app.setComponentZOrder(SimApp.chart_component_container_even,0);
                        }
                    },
                    SimApp.min_cycle_time/2
            );
        }
    }

    public static void setLikelihoodFunction(Node current_node, boolean odd_cycles) {

        //        System.out.println("Building the canvas");

        boolean contours_on = SimApp.plotContours != 0;

        if (odd_cycles){
            // This means that we need to clear any previous odd charts
            try {SimApp.chart_odd.dispose();}
            catch (Exception ignored) {}

            // Show the extent only if resolution is set higher than 10
            if (contours_on){
                BufferedImage contour_filled_image = getContourImage(MapField.getXRange(), MapField.getYRange());

                SimApp.chart_odd = new ContourChart();
                SimApp.chart_odd.view2d();

                ContourAxisBox chart_axis = (ContourAxisBox) SimApp.chart_odd.getView().getAxis();
                chart_axis.setContourImg(contour_filled_image, MapField.getXRange(), MapField.getYRange());

                Scatter extent_scatter = getCanvasExtentScatter();
                SimApp.chart_odd.getView().getScene().add(extent_scatter);
            }
            else{
                // Create a chart with contour axe box, and attach the contour picture
                SimApp.chart_odd = new ContourChart(Quality.Advanced().setHiDPIEnabled(true));
                SimApp.chart_odd.getCanvas().getView().setSquarifier(new XZSquarifier());
                SimApp.chart_odd.getCanvas().getView().setSquared(true);
                SimApp.chart_odd.addMouse();
            }

            Scatter nodes_scatter = getNodesScatter(current_node, contours_on);
            List<DrawableTextWrapper> node_labels = getNodeLabels(contours_on);

            SimApp.chart_odd.getView().getScene().add(nodes_scatter);
            SimApp.chart_odd.getView().getScene().add(node_labels);

            SimApp.chart_component_odd = (Component) SimApp.chart_odd.getCanvas();
            SimApp.chart_component_odd.setBounds(SimApp.chart_components_bounds);
        }
        else{
            try {SimApp.chart_even.dispose();}
            catch (Exception ignored) {}

            // Show the extent only if resolution is set higher than 10
            if (contours_on){
                BufferedImage contour_filled_image = getContourImage(MapField.getXRange(), MapField.getYRange());

                SimApp.chart_even = new ContourChart();
                SimApp.chart_even.view2d();

                ContourAxisBox chart_axis = (ContourAxisBox) SimApp.chart_even.getView().getAxis();
                chart_axis.setContourImg(contour_filled_image, MapField.getXRange(), MapField.getYRange());

                Scatter extent_scatter = getCanvasExtentScatter();
                SimApp.chart_even.getView().getScene().add(extent_scatter);
            }
            else{
                // Create a chart with contour axe box, and attach the contour picture
                SimApp.chart_even = new ContourChart(Quality.Advanced().setHiDPIEnabled(true));
                SimApp.chart_even.getCanvas().getView().setSquarifier(new XZSquarifier());
                SimApp.chart_even.getCanvas().getView().setSquared(true);
                SimApp.chart_even.addMouse();
            }

            Scatter nodes_scatter = getNodesScatter(current_node, contours_on);
            List<DrawableTextWrapper> node_labels = getNodeLabels(contours_on);

            SimApp.chart_even.getView().getScene().add(nodes_scatter);
            SimApp.chart_even.getView().getScene().add(node_labels);

            SimApp.chart_component_even = (Component) SimApp.chart_even.getCanvas();
            SimApp.chart_component_even.setBounds(SimApp.chart_components_bounds);
        }
    }

    // Keep in mind that this function (especially getFilledContourImage()) is quite heavy and prone to memory leaks.
    // To be used only for visualization in GUI mode since it takes most performance out from the localization.
    public static BufferedImage getContourImage(Range x_range, Range y_range) {
        // Function for creating a contours plot
        Mapper mapper = new Mapper(){
            public double f(double x, double y) {
                if (SimApp.uwb_model){
                    return MathEngineUWB.swarmPositioningOptimizers[0].position_likelihood.function(new double[] {x, y});
                }
                else if (SimApp.ble_model){
                    return MathEngineBLE.swarmPositioningOptimizers[0].position_likelihood.function(new double[] {x, y});
                }
                return 0;
            }
        };

        // Create the object to represent the function over the given range.
        final Shape surface = new SurfaceBuilder().orthonormal(
                new OrthonormalGrid(x_range, 10, y_range, 10), mapper // For spanning across the entire plot
        );

        ColorMapper myColorMapper = null;
        if (SimApp.uwb_model){
            myColorMapper = new ColorMapper(
                    new ColorMapRainbow(),
                    surface.getBounds().getZmin(),
                    MathEngineUWB.bestLikelihood,
                    new org.jzy3d.colors.Color(1,1,1,.2f)
            );
        }
        else if (SimApp.ble_model){
            myColorMapper = new ColorMapper(
                    new ColorMapRainbow(),
                    surface.getBounds().getZmin(),
                    MathEngineBLE.bestLikelihood,
                    new org.jzy3d.colors.Color(1,1,1,.2f)
            );
        }

        surface.setDisplayed(false);

        // Compute an image of the contour
        MapperContourPictureGenerator contour = new MapperContourPictureGenerator(mapper, x_range, y_range);
        return contour.getFilledContourImage(
                new DefaultContourColoringPolicy(myColorMapper),
                SimApp.chart_plot_size,
                SimApp.chart_plot_size,
                SimApp.plotContours
        );
    }

    public static Scatter getNodesScatter(Node current_node, boolean contours_on){

        // Prepare the structures to hold the drawing properties
        int size = SimApp.nodeID_to_nodeObject.size();

        // Calculate the z position for the scatter-plot elements so that they are drawn above the function
        double other_nodes_z = 0;
        double current_node_z = 0;
        int scatter_width;

        if (contours_on){
            if (SimApp.uwb_model){
                other_nodes_z = MathEngineUWB.bestLikelihood + 800;
                current_node_z = MathEngineUWB.bestLikelihood;
            }
            else if (SimApp.ble_model){
                other_nodes_z = MathEngineBLE.bestLikelihood + 800;
                current_node_z = MathEngineBLE.bestLikelihood;
            }
            scatter_width = 25;
        }
        else {
            other_nodes_z = 0;
            scatter_width = 10;
            current_node_z = other_nodes_z;
        }

        Coord3d[] points = new Coord3d[size];
        org.jzy3d.colors.Color[] colors = new org.jzy3d.colors.Color[size];

        int index_calculator = 0;
        for (Node node: SimApp.nodeID_to_nodeObject.values()){
            // Make sure we are not considering the same
            if (node.id != current_node.id){
                // Check if this remote Node is among the effective ones
                if (SimApp.effective_remoteNodes.contains(node.id)){
                    // Add the properties of its effective Neighbors
                    points[index_calculator] = new Coord3d(node.current_relative_x, node.current_relative_y, other_nodes_z);
                    colors[index_calculator] = new org.jzy3d.colors.Color(239, 253, 0);
                }
                else{
                    // Add the properties of the rest
                    points[index_calculator] = new Coord3d(node.current_relative_x, node.current_relative_y, other_nodes_z);
                    colors[index_calculator] = new org.jzy3d.colors.Color(53, 255, 227);
                }
            }
            else{
                // Add the properties of current Node
                points[index_calculator] = new Coord3d(current_node.current_relative_x, current_node.current_relative_y, current_node_z); // Using here the bestLikelihood; is a workaround for making the scatter visible
                colors[index_calculator] = new org.jzy3d.colors.Color(255, 0, 0);
            }

            index_calculator = index_calculator + 1;
        }

        return new Scatter(points, colors, scatter_width);
    }

    public static Scatter getCanvasExtentScatter(){

        double[] box_extent = MapField.getBoxExtent();

//        System.out.println(Arrays.toString(box_extent));

        // Calculate the z position for the scatter-plot elements so that they are drawn above the function
        double scatter_z = 0;
        
        if (SimApp.uwb_model){
            scatter_z = MathEngineUWB.bestLikelihood;
        }
        else if (SimApp.ble_model){
            scatter_z = MathEngineBLE.bestLikelihood;
        }

        // We use 4 to be able to add 4 labels for setting manually a fixed aspect ratio for the plot
        // Prepare the points for defining the entire canvas
        Coord3d[] canvas_corner_points = new Coord3d[4];
        org.jzy3d.colors.Color[] canvas_corner_colors = new org.jzy3d.colors.Color[4];

        canvas_corner_points[0] = new Coord3d(box_extent[0], box_extent[2], scatter_z);
        canvas_corner_colors[0] = new org.jzy3d.colors.Color(0, 0, 0);

        canvas_corner_points[1] = new Coord3d(box_extent[1], box_extent[2], scatter_z);
        canvas_corner_colors[1] = new org.jzy3d.colors.Color(0, 0, 0);

        canvas_corner_points[2] = new Coord3d(box_extent[1], box_extent[3], scatter_z);
        canvas_corner_colors[2] = new org.jzy3d.colors.Color(0, 0, 0);

        canvas_corner_points[3] = new Coord3d(box_extent[0], box_extent[3], scatter_z);
        canvas_corner_colors[3] = new org.jzy3d.colors.Color(0, 0, 0);

        return new Scatter(canvas_corner_points, canvas_corner_colors, 1);
    }

    public static List<DrawableTextWrapper> getNodeLabels(boolean contours_on){

        // Calculate the z position for the label elements so that they are drawn above the function and scatter
        double labels_z = 0;
        if (contours_on){
            if (SimApp.uwb_model){
                labels_z = MathEngineUWB.bestLikelihood + 1000;
            }
            else if (SimApp.ble_model){
                labels_z = MathEngineBLE.bestLikelihood + 1000;
            }
        }
        else {
            labels_z = 0.04;
        }

        // Prepare the structures to hold the label properties
        DrawableTextWrapper[] node_ides_to_render = new DrawableTextWrapper[SimApp.nodeID_to_nodeObject.size()];

        //DefaultTextStyle text_style = new DefaultTextStyle();
        ITextRenderer node_ids_renderer = new TextRenderer();

        int index_calculator = 0;
        for (Node node: SimApp.nodeID_to_nodeObject.values()){
            // Add the label of Node
            node_ides_to_render[index_calculator] = new DrawableTextWrapper(
                    String.valueOf(node.id),
                    new Coord3d(node.current_relative_x, node.current_relative_y, labels_z),
                    Color.BLACK, node_ids_renderer
            );
            index_calculator = index_calculator + 1;
        }

        return Arrays.asList(node_ides_to_render);
    }

    private static String buildFunctionSectionOfWolframCommand(ArrayList<String> productLikelihoodComponents){

//        System.out.println("Building function section of wolfram command");

        StringBuilder temp_distance_likelihood_function = new StringBuilder();

        for (String f: productLikelihoodComponents){
            temp_distance_likelihood_function.append(f);
        }

        // Remove the last unwanted chars from the Strings
        // System.out.println(temp_distance_likelihood_function);

        if (SimApp.uwb_model){
            temp_distance_likelihood_function.setLength(temp_distance_likelihood_function.length() - 2);
        }
        else if (SimApp.ble_model){
            temp_distance_likelihood_function.setLength(temp_distance_likelihood_function.length() - 3);
        }
        ProductFinalFunctionObject = temp_distance_likelihood_function.toString();

        String mathematica_likelihdood_function = "r[distanceX_, distanceY_] := (" + ProductFinalFunctionObject + ")";

        String plot_cmd = null;
        if (SimApp.uwb_model){
            plot_cmd = mathematica_likelihdood_function
                    + ")/ " + String.valueOf(MathEngineUWB.bestLikelihood).replaceFirst("E-", "*^-") // Use extra ) for simplified UWB model
                    + ";\n\n";
        }
        else if (SimApp.ble_model){
            plot_cmd = mathematica_likelihdood_function
                    + ")/ " + String.valueOf(MathEngineBLE.bestLikelihood).replaceFirst("E-", "*^-") // Use extra ) for simplified UWB model
                    + ";\n\n";
        }


        if (SimApp.headless_mode && (SimApp.optimization_cycles == SimApp.cycleCounter)){
            plot_cmd = plot_cmd + "model = ContourPlot[r[distanceX, distanceY], " +
                    "{distanceX, " + MapField.global_minPlotX + ", " + MapField.global_maxPlotX + "}, " +
                    "{distanceY, " + MapField.global_minPlotY + ", " + MapField.global_maxPlotY + "}, " +
                    "PlotPoints -> {100, 100}, " +
                    "MaxRecursion -> 1, " +
                    "PlotRange -> Full, " +
                    "ClippingStyle -> None, " +
                    "FrameLabel -> {Style[\"X (cm)\", 20, Bold], Style[\"Y (cm)\", 20, Bold]}," +
                    "FrameTicksStyle -> Directive[Black, 15]," +
                    "GridLines -> Automatic," +
                    "GridLinesStyle -> Directive[AbsoluteThickness[0.5], Black]," +
                    "Contours -> {Automatic, 30}];\n\n";
        }
        else {
            plot_cmd = plot_cmd + "model = ContourPlot[r[distanceX, distanceY], " +
                    "{distanceX, " + MapField.global_minPlotX + ", " + MapField.global_maxPlotX + "}, " +
                    "{distanceY, " + MapField.global_minPlotY + ", " + MapField.global_maxPlotY + "}, " +
                    "PlotPoints -> {100, 100}, " +
                    "MaxRecursion -> 1, " +
                    "PlotRange -> Full, " +
                    "ClippingStyle -> None, " +
                    "FrameLabel -> {Style[\"X (cm)\", 20, Bold], Style[\"Y (cm)\", 20, Bold]}," +
                    "FrameTicksStyle -> Directive[Black, 15]," +
                    "GridLines -> Automatic," +
                    "GridLinesStyle -> Directive[AbsoluteThickness[0.5], Black]," +
                    "Contours -> {Automatic, " + SimApp.plotContours + "}];\n\n";
        }

        return plot_cmd;
    }

    private static ArrayList[] collectComponentsForWolframPlotSections(Node current_node){

//        System.out.println("Collecting individual mathematica plot components for node " + current_node.id);

        // Hold all likelihood components and labels in the following variables
        ArrayList productLikelihoodComponents = null;

        // Check if the user has selected to export also the ProductLikelihood
        if (SimApp.headless_mode || SimApp.export_ProductLikelihood_WolframPlot_function_btn.getState()){
            // Create the Wolfram CMD
            productLikelihoodComponents = getWolframFunctionComponentsOfNeighborBeliefsForNode(current_node);
        }

        ArrayList[] mathematica_components = new ArrayList[2];

        mathematica_components[0] = buildListPlotSectionsOfWolframCommand(current_node);
        mathematica_components[1] = productLikelihoodComponents;

        return mathematica_components;
    }

    private static ArrayList getWolframFunctionComponentsOfNeighborBeliefsForNode(Node current_node){

        ArrayList productLikelihoodComponents = new ArrayList<>();

        // System.out.println("Neighbors with effective beliefs: " + SimApp.effective_remoteNodes);

        for (Node remote_node: SimApp.nodeID_to_nodeObject.values()){
            // Make sure we are not considering the same
            if (remote_node.id != current_node.id){
                // Check if this remote Node is among the effective ones
                if (SimApp.effective_remoteNodes.contains(remote_node.id)){
                    if (SimApp.uwb_model){
                        remote_node.cdl_uwb.updateProductLikelihoodComponentWolframOBJ();
                        // System.out.println(remote_node.cdl.ProductLikelihoodComponent_WolframOBJ);
                        productLikelihoodComponents.add(remote_node.cdl_uwb.ProductLikelihoodComponent_WolframOBJ);
                    }
                    else if (SimApp.ble_model){
                        remote_node.cdl_ble.updateProductLikelihoodComponentWolframOBJ();
                        // System.out.println(remote_node.cdl.ProductLikelihoodComponent_WolframOBJ);
                        productLikelihoodComponents.add(remote_node.cdl_ble.ProductLikelihoodComponent_WolframOBJ);
                    }
                }
            }
        }
        return productLikelihoodComponents;
    }

    private static ArrayList buildListPlotSectionsOfWolframCommand(Node current_node){

        StringBuilder temp_EffectiveNode_Pos_component = new StringBuilder();
        temp_EffectiveNode_Pos_component.append("plotA = ListPlot[\n  {");

        StringBuilder temp_EffectiveNode_Labels_component = new StringBuilder();
        temp_EffectiveNode_Labels_component.append("} -> {");

        StringBuilder temp_nonEffectiveNode_Pos_component = new StringBuilder();
        temp_nonEffectiveNode_Pos_component.append("\n\nplotB = ListPlot[\n  {");

        StringBuilder temp_nonEffectiveNode_Labels_component = new StringBuilder();
        temp_nonEffectiveNode_Labels_component.append("} -> {");

        String temp_currentNode_component = null;

        for (Node remote_node: SimApp.nodeID_to_nodeObject.values()){
            // Make sure we are not considering the same
            if (remote_node.id != current_node.id){
                // Check if this remote Node is among the effective ones
                if (SimApp.effective_remoteNodes.contains(remote_node.id)){
                    temp_EffectiveNode_Pos_component.append("{" + remote_node.current_relative_x + ", " + remote_node.current_relative_y + "}, ");
                    temp_EffectiveNode_Labels_component.append(remote_node.id + ", ");
                }
                else{
                    // Being here means that this remote Node is not among the effective ones.
                    // Yet, we want to plot it
                    temp_nonEffectiveNode_Pos_component.append("{" + remote_node.current_relative_x + ", " + remote_node.current_relative_y + "}, ");
                    temp_nonEffectiveNode_Labels_component.append(remote_node.id + ", ");
                }
            }
            // Also handle specifically the styling of current Node
            else {
                temp_currentNode_component = "\n\nplotC = ListPlot[\n" +
                        "   {{" + current_node.current_relative_x + ", " + current_node.current_relative_y + "}} -> {\"" + current_node.id + "\"},\n" +
                        "   AspectRatio -> Automatic,\n" +
                        "   PlotStyle -> Opacity[1, Green],\n" +
                        "   Background -> Transparent,\n" +
                        "   PlotMarkers -> {{Graphics@{Green, Disk[]} , 20}},\n" +
                        "   PlotRange -> {{" +
                        MapField.global_minPlotX + ", " +
                        MapField.global_maxPlotX + "}, {" +
                        MapField.global_minPlotY + ", " +
                        MapField.global_maxPlotY + "}},\n" +
                        "   LabelingFunction -> Center,\n" +
                        "   LabelStyle -> {FontFamily -> \"Helvetica\", FontSize -> 13, Bold}\n" +
                        "   ];\n";
            }
        }

        // Remove the last character from the commands
        temp_EffectiveNode_Pos_component.setLength(temp_EffectiveNode_Pos_component.length() - 2);
        temp_EffectiveNode_Labels_component.setLength(temp_EffectiveNode_Labels_component.length() - 2);
        temp_EffectiveNode_Pos_component.append(temp_EffectiveNode_Labels_component);

        temp_EffectiveNode_Pos_component.append("},\n" +
                "   AspectRatio -> Automatic,\n" +
                "   PlotStyle -> Opacity[1, Yellow],\n" +
                "   Background -> Transparent,\n" +
                "   PlotMarkers -> {{Graphics@{Yellow, Disk[]} , 20}},\n" +
                "   PlotRange -> {{" +
                MapField.global_minPlotX + ", " +
                MapField.global_maxPlotX + "}, {" +
                MapField.global_minPlotY + ", " +
                MapField.global_maxPlotY + "}},\n" +
                "   LabelingFunction -> Center,\n" +
                "   LabelStyle -> {FontFamily -> \"Helvetica\", FontSize -> 13, Bold}\n" +
                "  ];");

        temp_nonEffectiveNode_Pos_component.setLength(temp_nonEffectiveNode_Pos_component.length() - 2);
        temp_nonEffectiveNode_Labels_component.setLength(temp_nonEffectiveNode_Labels_component.length() - 2);
        temp_nonEffectiveNode_Pos_component.append(temp_nonEffectiveNode_Labels_component);

        temp_nonEffectiveNode_Pos_component.append("},\n" +
                "   AspectRatio -> Automatic,\n" +
                "   PlotStyle -> Opacity[1, Orange],\n" +
                "   Background -> Transparent,\n" +
                "   PlotMarkers -> {{Graphics@{Orange, Disk[]} , 20}},\n" +
                "   PlotRange -> {{" +
                MapField.global_minPlotX + ", " +
                MapField.global_maxPlotX + "}, {" +
                MapField.global_minPlotY + ", " +
                MapField.global_maxPlotY + "}},\n" +
                "   LabelingFunction -> Center,\n" +
                "   LabelStyle -> {FontFamily -> \"Helvetica\", FontSize -> 13, Bold}\n" +
                "  ];");

        // Create an ArrayList that will always have 1 member only.
        // Namely, the Wolfram Command to generate the Canvas Plot with the Positions
        ArrayList positionsCanvasPlot_WolframCMD_ArrayListContainer = new ArrayList<>();

        // Make a check here to see if the list containing the effective_nodes has the same size as the entire Node db-1
        // This means that there is no non-Effective Node. Hence, we need to exclude Plot B
        if (SimApp.effective_remoteNodes.size() == (SimApp.nodeID_to_nodeObject.size()-1)){
            positionsCanvasPlot_WolframCMD_ArrayListContainer.add(temp_EffectiveNode_Pos_component.append(temp_currentNode_component).toString());
        }
        else{
            positionsCanvasPlot_WolframCMD_ArrayListContainer.add(temp_EffectiveNode_Pos_component.append(temp_nonEffectiveNode_Pos_component).append(temp_currentNode_component).toString());
        }

        return positionsCanvasPlot_WolframCMD_ArrayListContainer;
    }

    // Use the following to reset everytime the Nodes to their True Position (for debugging)
    static private void reset_CurrentNodePos_to_TruePos(){
        // Loop throughout all Nodes
        for (Node node: SimApp.nodeID_to_nodeObject.values()){
            node.updateCurrentNodePos(node.true_relative_x, node.true_relative_y);
        }
    }
}