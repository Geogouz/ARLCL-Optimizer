import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Sim_App extends Frame {

    static boolean headless_mode;

    // This data structure will store each evaluation case
    static String[] eval_scenario;

    static boolean results_per_step;
    static boolean results_per_cycle;
    static boolean ble_model;
    static boolean uwb_model;
    static boolean export_ProductLikelihood_WolframPlot;

    // Values
    static int min_effective_measurement;
    static int threads;
    static int optimization_iterations;
    static double ftol;
    static int initial_step_size;
    static int max_optimization_time;
    static int kNearestNeighbours_for_BeliefsStrength;
    static int plotResolution;
    static int projectName;

    static int optimization_cycles;
    static int total_eval_iterations;
    static int current_eval_iteration;

    static String evaluated_scenario_name;
    static String input_file_path;
    static String outpath_results_folder_path;
    static String output_iterated_results_folder_path;


    // Create a map having <Node IDs, Node Objects> as <key, value> pairs
    static LinkedHashMap<Integer, Node> nodeID_to_nodeObject;
    static List<Integer> OrderedByBeliefsStrength_NodeIDs;
    static List<Integer> temp_OrderedRemoteNodeIDs;
    static List<Integer> OrderedByLastCycleOrientation_NodeIDs;

    static ArrayList<Integer> effective_remoteNodes;

    static double max_distance = 1000.;
    static boolean optimization_running;
    static boolean rendering_wolfram_data;

    static DecimalFormat two_decimals_formatter = new DecimalFormat("#.##");

    static boolean no_opt_executed_yet = true;
    static WnAdapter window_adapter;

    static String previous_valid_project_name = "";
    static String previous_valid_plot_resolution = "";

    static CustomTextArea outputTerminal;
    static CustomTextArea export_LikelihoodPlot_Frame;
    static CustomTextArea optimization_order_Label;
    static CustomTextArea rangingModel_Frame;
    static CustomTextArea resultsPer_Frame;
    static CustomTextArea optimizationParameters_Frame;

    static CustomTextArea projectName_LabelArea;
    static CustomTextArea loaded_db_name_LabelArea;
    static CustomTextArea kNearestNeighbours_for_BeliefsStrength_LabelArea;
    static CustomTextArea max_optimization_time_LabelArea;
    static CustomTextArea optimization_cycles_LabelArea;
    static CustomTextArea initial_step_size_LabelArea;
    static CustomTextArea min_effective_measurement_value_LabelArea;
    static CustomTextArea plotResolution_LabelArea;
    static CustomTextArea ftol_LabelArea;
    static CustomTextArea optimization_iterations_LabelArea;
    static CustomTextArea threads_LabelArea;

    static CustomTextField projectName_inputTextField;
    static CustomTextField kNearestNeighbours_for_BeliefsStrength_inputTextField;
    static CustomTextField max_optimization_time_inputTextField;
    static CustomTextField optimization_cycles_inputTextField;
    static CustomTextField initial_step_size_inputTextField;
    static CustomTextField min_effective_measurement_inputTextField;
    static CustomTextField plotResolution_inputTextField;
    static CustomTextField ftol_inputTextField;
    static CustomTextField optimization_iterations_inputTextField;
    static CustomTextField threads_inputTextField;

    static CustomButton openDB_Btn;
    static CustomButton resumeBtn;
    static CustomButton clearTerminalBtn;
    static CustomButton stopBtn;

    static CustomCheckbox autoResume;
    static CustomCheckbox results_per_step_btn;
    static CustomCheckbox results_per_cycle_btn;
    static CustomCheckbox spatial_direction_btn;
    static CustomCheckbox rss_density_check_btn;
    static CustomCheckbox ble_model_btn;
    static CustomCheckbox uwb_model_btn;

    static CustomCheckbox export_ProductLikelihood_WolframPlot_btn;

    static Thread t1;

    static SimpleDateFormat day_formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    static ScheduledExecutorService scheduler;
    static ScheduledFuture<?> scheduled_auto_resumer;

    static int cycleCounter;
    static int stepCounter;

    final static Random random = new Random();

    public static void run(String[] argv){
        Sim_App.headless_mode = argv.length != 0;

        if (headless_mode){
            System.out.println("Initiating headless optimization");

            // The argument should look like that

            System.out.println("Params: " + Arrays.toString(argv));
            // Get each argument
            HashMap<String, String> str_arguments = new HashMap<>();

            String input_rss_folder_path = null;
            String eval_scenarios_path = null;
            int evaluation_id = 0;

            // This section is for parsing the arguments when these come with the equality sign
            try {
                for (String argument : argv) {
                    String[] key_value_pair = argument.split("=");
                    str_arguments.put(key_value_pair[0], key_value_pair[1]);
                }

                // Set the seed
                Sim_App.random.setSeed(Long.parseLong(str_arguments.get("seed")));
                Sim_App.ftol = Double.parseDouble(str_arguments.get("ftol"));
                Sim_App.outpath_results_folder_path = str_arguments.get("out_path");
                input_rss_folder_path = str_arguments.get("rss_db_path");
                eval_scenarios_path = str_arguments.get("scenarios_path");
                evaluation_id = Integer.parseInt(str_arguments.get("eval_id"));
                Sim_App.total_eval_iterations = Integer.parseInt(str_arguments.get("eval_iter"));
                Sim_App.optimization_iterations = Integer.parseInt(str_arguments.get("opt_iter"));
                Sim_App.max_optimization_time = Integer.parseInt(str_arguments.get("max_opt_time"));
                Sim_App.threads = Integer.parseInt(str_arguments.get("threads"));
                Sim_App.optimization_cycles = Integer.parseInt(str_arguments.get("cycles"));
                Sim_App.kNearestNeighbours_for_BeliefsStrength = Integer.parseInt(str_arguments.get("k_Beliefs"));
                Sim_App.min_effective_measurement = Integer.parseInt(str_arguments.get("min_effect"));
                Sim_App.plotResolution = Integer.parseInt(str_arguments.get("plot_res"));
            }
            catch (Exception e) {
                e.printStackTrace();
                System.out.println("Zip exists already. Exiting");
                // At this point we can close the program
                System.exit(0);
            }

            // Initiate the iteration index
            Sim_App.current_eval_iteration = 0;

            // Parse the evaluation scenarios from the combos file
            eval_scenario = parse_eval_scenarios(evaluation_id, eval_scenarios_path);

            // This section is for setting our hardcoded minimal arguments
            String deployment_type = eval_scenario[0];
            String swarmIDs = eval_scenario[1];
            String sample_size = eval_scenario[2];

            Sim_App.evaluated_scenario_name = deployment_type + "_" + swarmIDs + "_" + sample_size;
            Sim_App.input_file_path = input_rss_folder_path + Sim_App.evaluated_scenario_name + ".smpl";

            String zip_name = Sim_App.outpath_results_folder_path + Sim_App.evaluated_scenario_name + ".zip";
            // Check to see if there is any .zip file so that we can cancel the process completely
            File zip_file = new File(zip_name);
            if (zip_file.exists()){
                // At this point we can close the program
                System.exit(0);
            }

            // Execute the following simulation setup as many times as the user has selected
            while (Sim_App.current_eval_iteration < Sim_App.total_eval_iterations +1){
                try {
                    System.out.println("Preparing data structures");
                    reset_data_structures();
                    Headless_Init();
                    Core.init();
                    System.out.println("Start Optimizing");
                    Sim_App.autoResume.setState(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    // At this point we can close the program
                    System.exit(0);
                }

                // Wait for almost "infinite" amount of time for the auto resume scheduler to finish.
                // Then we shall continue with the next iteration
                try {
                    scheduler.awaitTermination(10000000, SECONDS); // TODO: Hardcoded to be changed
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Increment one step and continue
                Sim_App.current_eval_iteration++;
            }

            System.out.println("Optimization finished");

            // We now zip the folder and wipe everything
            try {
                System.out.println("Storing results");
                store_results();

                File directoryToBeDeleted = new File(Sim_App.outpath_results_folder_path + Sim_App.evaluated_scenario_name);
                deleteDirectory(directoryToBeDeleted);

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Proper exit");
            // At this point we can close the program
            System.exit(0);
        }
        else {
            reset_data_structures();
            System.out.println("Executing ARLCL Sim in GUI mode");
            new Sim_App();
            new Core();
        }
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    private static void store_results() throws IOException {
        System.out.println(Sim_App.outpath_results_folder_path + Sim_App.evaluated_scenario_name);
        String sourceFile = Sim_App.outpath_results_folder_path + Sim_App.evaluated_scenario_name;
        FileOutputStream fos = new FileOutputStream(sourceFile+".zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        File fileToZip = new File(sourceFile);

        zipFile(fileToZip, fileToZip.getName(), zipOut);
        zipOut.close();
        fos.close();
    }

    private static String[] parse_eval_scenarios(long lines_to_skip, String eval_scenarios_path) {
        String[] temp_args = null;
        try {
            Path in_path = Paths.get(eval_scenarios_path);
            Stream<String> file_stream = Files.lines(in_path);

            String strLine = file_stream.skip(lines_to_skip).findFirst().get();
            System.out.println("Entry at line " + lines_to_skip + ": " + strLine);
            temp_args = new String[3];

            String[] split_array = strLine.split(" ");

            temp_args[0] = split_array[0];
            temp_args[1] = split_array[1].replace("(", "").replace(")", "");
            temp_args[2] = split_array[2];

        } catch (IOException e) {
            e.printStackTrace();
        }

        return temp_args;
    }

    private static void reset_data_structures() {
        Sim_App.cycleCounter = 0;
        Sim_App.stepCounter = 0;

        Sim_App.nodeID_to_nodeObject = new LinkedHashMap<>();
        Sim_App.OrderedByBeliefsStrength_NodeIDs = new ArrayList<>();
        Sim_App.temp_OrderedRemoteNodeIDs = new ArrayList<>();
        Sim_App.OrderedByLastCycleOrientation_NodeIDs = new ArrayList<>();

        Sim_App.optimization_running = false;
        Sim_App.rendering_wolfram_data = false;
    }

    public static void Headless_Init() {
        headless_exec();
    }

    public Sim_App() {
        setLayout(null);
        setTitle("Swarm Positioning");

        final int window_width = 1900;
        final int window_height = 1000;
        final int bar_height = 32;
        final int settings_panel_height = 360;

        final int tiny_gap = 5;
        final int normal_gap = 100;
        final int big_gap = 250;

        final int small_text_height = 20;
        final int medium_text_height = 34;
        final int value_text_width = 50;

        final int c1_x = 1370;
        final int c1_content_width = normal_gap;
        final int c2_x = c1_x + c1_content_width + tiny_gap;
        final int c2_content_width = normal_gap;
        final int c3_x = c2_x + c2_content_width + tiny_gap;
        final int c3_content_width = big_gap;
        final int c4_x = c3_x + c3_content_width;
        final int c4_content_width = value_text_width;

        final int r2_y = bar_height + window_height - settings_panel_height + small_text_height + tiny_gap;


        Sim_App.outputTerminal = new CustomTextArea("",2,40, TextArea.SCROLLBARS_VERTICAL_ONLY);

        Sim_App.outputTerminal.setEditable(false);
        Sim_App.outputTerminal.setBounds(c1_x, bar_height,window_width-c1_x-tiny_gap,window_height-settings_panel_height);
        add((TextArea) Sim_App.outputTerminal.getTextArea());

        Sim_App.clearTerminalBtn = new CustomButton("clear");
        Sim_App.clearTerminalBtn.addActionListener(new clearTerminalBtnAdapter());
        Sim_App.clearTerminalBtn.setBounds(c4_x,bar_height + window_height - settings_panel_height,40,18);
        add((Button) Sim_App.clearTerminalBtn.getButton());

        Sim_App.resumeBtn = new CustomButton("Resume");
        Sim_App.resumeBtn.addActionListener(new resumeBtnAdapter());
        Sim_App.resumeBtn.setBounds(c1_x,785,60,30);
        add((Button) Sim_App.resumeBtn.getButton());
        Sim_App.resumeBtn.setVisible(false);

        Sim_App.stopBtn = new CustomButton("Stop");
        Sim_App.stopBtn.addActionListener(new stopBtnAdapter());
        Sim_App.stopBtn.setBounds(c1_x,785,60,30);
        add((Button) Sim_App.stopBtn.getButton());
        Sim_App.stopBtn.setVisible(false);

        Sim_App.openDB_Btn = new CustomButton("DB load");
        Sim_App.openDB_Btn.addActionListener(new openRSSdbBtnAdapter());
        Sim_App.openDB_Btn.setBounds(0, window_height-medium_text_height, normal_gap,small_text_height+tiny_gap);
        add((Button) Sim_App.openDB_Btn.getButton());

        Sim_App.loaded_db_name_LabelArea = new CustomTextArea("",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.loaded_db_name_LabelArea.setFont(new Font("Arial", Font.ITALIC, 13));
        Sim_App.loaded_db_name_LabelArea.setBackground(Color.black);
        Sim_App.loaded_db_name_LabelArea.setEnabled(true);
        Sim_App.loaded_db_name_LabelArea.setFocusable(false);
        Sim_App.loaded_db_name_LabelArea.setEditable(false);
        Sim_App.loaded_db_name_LabelArea.setBounds(normal_gap, window_height-medium_text_height,window_width - normal_gap,small_text_height+tiny_gap);
        add((TextArea) Sim_App.loaded_db_name_LabelArea.getTextArea());



        // This Section is for the "Results per:"
        CheckboxGroup plot_export_group = new CheckboxGroup();
        Sim_App.results_per_step_btn = new CustomCheckbox("Step", false, plot_export_group);
        Sim_App.results_per_step_btn.setBounds(c1_x + tiny_gap, r2_y + small_text_height, 50, small_text_height);
        add((Checkbox) Sim_App.results_per_step_btn.getCheckbox());

        Sim_App.results_per_cycle_btn = new CustomCheckbox("Cycle", true, plot_export_group);
        Sim_App.results_per_cycle_btn.setBounds(c1_x + tiny_gap, r2_y + 2 * small_text_height, 50, small_text_height);
        add((Checkbox) Sim_App.results_per_cycle_btn.getCheckbox());

        Sim_App.resultsPer_Frame = new CustomTextArea("Results per:",2,1, TextArea.SCROLLBARS_NONE);
        Sim_App.resultsPer_Frame.setBackground(Color.lightGray);
        Sim_App.resultsPer_Frame.setFont(new Font("Arial", Font.BOLD, 13));
        Sim_App.resultsPer_Frame.setEnabled(true);
        Sim_App.resultsPer_Frame.setFocusable(false);
        Sim_App.resultsPer_Frame.setEditable(false);
        Sim_App.resultsPer_Frame.setBounds(c1_x, r2_y, c1_content_width,66);
        add((TextArea) Sim_App.resultsPer_Frame.getTextArea());



        // TODO: Experimentation option
        // This Section is for the Optimization's Order
//        final int optimization_order_Label_y = r2_y;
//        CheckboxGroup optimization_order_group = new CheckboxGroup();
//        Sim_App.spatial_direction_btn = new CustomCheckbox("Direction", false, optimization_order_group);
//        Sim_App.spatial_direction_btn.setBounds(1485, 846, 70, small_textfield_height);
//        add((Checkbox) Sim_App.spatial_direction_btn.getCheckbox());
//
//        Sim_App.rss_density_check_btn = new CustomCheckbox("Density", true, optimization_order_group);
//        Sim_App.rss_density_check_btn.setBounds(1485, 866, 70, small_textfield_height);
//        add((Checkbox) Sim_App.rss_density_check_btn.getCheckbox());
//
//        Sim_App.optimization_order_Label = new CustomTextArea("Opt. Order:",2,1, TextArea.SCROLLBARS_NONE);
//        Sim_App.optimization_order_Label.setBackground(Color.lightGray);
//        Sim_App.optimization_order_Label.setFont(new Font("Arial", Font.BOLD, 13));
//        Sim_App.optimization_order_Label.setEnabled(true);
//        Sim_App.optimization_order_Label.setFocusable(false);
//        Sim_App.optimization_order_Label.setEditable(false);
//        Sim_App.optimization_order_Label.setBounds(c2_x, optimization_order_Label_y, 90, 66);
//        add((TextArea) Sim_App.optimization_order_Label.getTextArea());


        // This Section is for the selection of the Ranging Model
        CheckboxGroup ranging_model_group = new CheckboxGroup();
        Sim_App.ble_model_btn = new CustomCheckbox("BLE [dBm]", true, ranging_model_group);
        Sim_App.ble_model_btn.setBounds(c2_x + tiny_gap, r2_y + small_text_height, 70, small_text_height);
        add((Checkbox) Sim_App.ble_model_btn.getCheckbox());

        Sim_App.uwb_model_btn = new CustomCheckbox("UWB [t]", false, ranging_model_group);
        Sim_App.uwb_model_btn.setBounds(c2_x + tiny_gap, r2_y + 2 * small_text_height, 70, small_text_height);
        add((Checkbox) Sim_App.uwb_model_btn.getCheckbox());

        Sim_App.rangingModel_Frame = new CustomTextArea("Rang. Model:",2,1, TextArea.SCROLLBARS_NONE);
        Sim_App.rangingModel_Frame.setBackground(Color.lightGray);
        Sim_App.rangingModel_Frame.setFont(new Font("Arial", Font.BOLD, 13));
        Sim_App.rangingModel_Frame.setEnabled(true);
        Sim_App.rangingModel_Frame.setFocusable(false);
        Sim_App.rangingModel_Frame.setEditable(false);
        Sim_App.rangingModel_Frame.setBounds(c2_x, r2_y, c1_content_width, 66);
        add((TextArea) Sim_App.rangingModel_Frame.getTextArea());


        Sim_App.autoResume = new CustomCheckbox("GO", false, null);
        Sim_App.autoResume.setBounds(c1_x, r2_y + 66 + tiny_gap, c1_content_width, small_text_height);
        add((Checkbox) Sim_App.autoResume.getCheckbox());


        // This Section is for the "Optimization's parameters:"
        final int min_effective_rss_LabelArea_y = r2_y + small_text_height;
        Sim_App.min_effective_measurement_value_LabelArea = new CustomTextArea("Min Effective Measurement\n(Units depend on the ranging technology):",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.min_effective_measurement_value_LabelArea.setBounds(c3_x, min_effective_rss_LabelArea_y, c3_content_width, medium_text_height);
        Sim_App.min_effective_measurement_value_LabelArea.setBackground(Color.lightGray);
        Sim_App.min_effective_measurement_value_LabelArea.setEnabled(true);
        Sim_App.min_effective_measurement_value_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.min_effective_measurement_value_LabelArea.getTextArea());

        int min_effective_measurement_inputTextArea_y = min_effective_rss_LabelArea_y + (medium_text_height-small_text_height)/2;
        Sim_App.min_effective_measurement_inputTextField = new CustomTextField("80");
        Sim_App.min_effective_measurement_inputTextField.setBounds(c4_x, min_effective_measurement_inputTextArea_y, c4_content_width, small_text_height);
        Sim_App.min_effective_measurement_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.min_effective_measurement_inputTextField));
        add((TextField) Sim_App.min_effective_measurement_inputTextField.getTextField());


        final int threads_LabelArea_y = min_effective_rss_LabelArea_y + medium_text_height;
        Sim_App.threads_LabelArea = new CustomTextArea("Optimization Threads [1,+]:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.threads_LabelArea.setBounds(c3_x, threads_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.threads_LabelArea.setBackground(Color.lightGray);
        Sim_App.threads_LabelArea.setEnabled(true);
        Sim_App.threads_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.threads_LabelArea.getTextArea());

        Sim_App.threads_inputTextField = new CustomTextField("6");
        Sim_App.threads_inputTextField.setBounds(c4_x, threads_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.threads_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.threads_inputTextField));
        add((TextField) Sim_App.threads_inputTextField.getTextField());


        final int optimization_iterations_LabelArea_y = threads_LabelArea_y + small_text_height;
        Sim_App.optimization_iterations_LabelArea = new CustomTextArea("Optimization Iterations [1,+]:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.optimization_iterations_LabelArea.setBounds(c3_x, optimization_iterations_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.optimization_iterations_LabelArea.setBackground(Color.lightGray);
        Sim_App.optimization_iterations_LabelArea.setEnabled(true);
        Sim_App.optimization_iterations_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.optimization_iterations_LabelArea.getTextArea());

        Sim_App.optimization_iterations_inputTextField = new CustomTextField("500");
        Sim_App.optimization_iterations_inputTextField.setBounds(c4_x, optimization_iterations_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.optimization_iterations_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.optimization_iterations_inputTextField));
        add((TextField) Sim_App.optimization_iterations_inputTextField.getTextField());


        final int ftol_LabelArea_y = optimization_iterations_LabelArea_y + small_text_height;
        Sim_App.ftol_LabelArea = new CustomTextArea("ftol: [1e-..]",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.ftol_LabelArea.setBounds(c3_x, ftol_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.ftol_LabelArea.setBackground(Color.lightGray);
        Sim_App.ftol_LabelArea.setEnabled(true);
        Sim_App.ftol_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.ftol_LabelArea.getTextArea());

        Sim_App.ftol_inputTextField = new CustomTextField("2");
        Sim_App.ftol_inputTextField.setBounds(c4_x, ftol_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.ftol_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.ftol_inputTextField));
        add((TextField) Sim_App.ftol_inputTextField.getTextField());


        final int initial_step_size_LabelArea_y = ftol_LabelArea_y + small_text_height;
        Sim_App.initial_step_size_LabelArea = new CustomTextArea("Initial Step Size: [1,+]",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.initial_step_size_LabelArea.setBounds(c3_x, initial_step_size_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.initial_step_size_LabelArea.setBackground(Color.lightGray);
        Sim_App.initial_step_size_LabelArea.setEnabled(true);
        Sim_App.initial_step_size_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.initial_step_size_LabelArea.getTextArea());

        Sim_App.initial_step_size_inputTextField = new CustomTextField("10");
        Sim_App.initial_step_size_inputTextField.setBounds(c4_x, initial_step_size_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.initial_step_size_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.initial_step_size_inputTextField));
        add((TextField) Sim_App.initial_step_size_inputTextField.getTextField());


        final int max_optimization_time_LabelArea_y = initial_step_size_LabelArea_y + small_text_height;
        Sim_App.max_optimization_time_LabelArea = new CustomTextArea("Max Step-Opt. runtime per Thread (ms) [1,+]:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.max_optimization_time_LabelArea.setBounds(c3_x, max_optimization_time_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.max_optimization_time_LabelArea.setBackground(Color.lightGray);
        Sim_App.max_optimization_time_LabelArea.setEnabled(true);
        Sim_App.max_optimization_time_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.max_optimization_time_LabelArea.getTextArea());

        Sim_App.max_optimization_time_inputTextField = new CustomTextField("6000");
        Sim_App.max_optimization_time_inputTextField.setBounds(c4_x, max_optimization_time_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.max_optimization_time_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.max_optimization_time_inputTextField));
        add((TextField) Sim_App.max_optimization_time_inputTextField.getTextField());


        final int optimization_cycles_LabelArea_y = max_optimization_time_LabelArea_y + small_text_height;
        Sim_App.optimization_cycles_LabelArea = new CustomTextArea("Optimization Cycles [1,+]:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.optimization_cycles_LabelArea.setBounds(c3_x, optimization_cycles_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.optimization_cycles_LabelArea.setBackground(Color.lightGray);
        Sim_App.optimization_cycles_LabelArea.setEnabled(true);
        Sim_App.optimization_cycles_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.optimization_cycles_LabelArea.getTextArea());

        Sim_App.optimization_cycles_inputTextField = new CustomTextField("50");
        Sim_App.optimization_cycles_inputTextField.setBounds(c4_x, optimization_cycles_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.optimization_cycles_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.optimization_cycles_inputTextField));
        add((TextField) Sim_App.optimization_cycles_inputTextField.getTextField());


        final int kNearestNeighbours_for_BeliefsStrength_LabelArea_y = optimization_cycles_LabelArea_y + small_text_height;
        Sim_App.kNearestNeighbours_for_BeliefsStrength_LabelArea = new CustomTextArea("kNN for Density Check [1,+]:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.kNearestNeighbours_for_BeliefsStrength_LabelArea.setBounds(c3_x, kNearestNeighbours_for_BeliefsStrength_LabelArea_y, c3_content_width, small_text_height);
        Sim_App.kNearestNeighbours_for_BeliefsStrength_LabelArea.setBackground(Color.lightGray);
        Sim_App.kNearestNeighbours_for_BeliefsStrength_LabelArea.setEnabled(true);
        Sim_App.kNearestNeighbours_for_BeliefsStrength_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.kNearestNeighbours_for_BeliefsStrength_LabelArea.getTextArea());

        Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField = new CustomTextField("6");
        Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField.setBounds(c4_x, kNearestNeighbours_for_BeliefsStrength_LabelArea_y, c4_content_width, small_text_height);
        Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField.addTextListener(
                new integer_greater_than_zero_Ensurer(Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField));
        add((TextField) Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField.getTextField());


        final int optimizationSettings_Label_height = (kNearestNeighbours_for_BeliefsStrength_LabelArea_y + small_text_height) - r2_y;
        Sim_App.optimizationParameters_Frame = new CustomTextArea("Optimization's Parameters",2,1, TextArea.SCROLLBARS_NONE);
        Sim_App.optimizationParameters_Frame.setFont(new Font("Arial", Font.BOLD, 13));
        Sim_App.optimizationParameters_Frame.setBackground(Color.lightGray);
        Sim_App.optimizationParameters_Frame.setEnabled(true);
        Sim_App.optimizationParameters_Frame.setFocusable(false);
        Sim_App.optimizationParameters_Frame.setEditable(false);
        Sim_App.optimizationParameters_Frame.setBounds(c3_x, r2_y, c3_content_width, optimizationSettings_Label_height);
        add((TextArea) Sim_App.optimizationParameters_Frame.getTextArea());



        // This Section is for the Plot Export Properties
        final int export_ProductLikelihood_Label_y = r2_y + optimizationSettings_Label_height + tiny_gap;
        Sim_App.plotResolution_LabelArea = new CustomTextArea("Detail [10,500]:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.plotResolution_LabelArea.setBounds(c2_x, export_ProductLikelihood_Label_y, c2_content_width, small_text_height);
        Sim_App.plotResolution_LabelArea.setBackground(Color.lightGray);
        Sim_App.plotResolution_LabelArea.setEnabled(true);
        Sim_App.plotResolution_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.plotResolution_LabelArea.getTextArea());

        final int plotResolution_inputTextArea_y = export_ProductLikelihood_Label_y + small_text_height;
        Sim_App.plotResolution_inputTextField = new CustomTextField("100");
        Sim_App.plotResolution_inputTextField.setBounds(c2_x, plotResolution_inputTextArea_y, c2_content_width, small_text_height);
        Sim_App.plotResolution_inputTextField.addTextListener(new plotResolution_inputTextArea_Ensurer());
        add((TextField) Sim_App.plotResolution_inputTextField.getTextField());

        final int export_ProductLikelihood_Label_height = (plotResolution_inputTextArea_y + small_text_height) - export_ProductLikelihood_Label_y;
        final int export_ProductLikelihood_WolframPlot_btn_y = plotResolution_inputTextArea_y - 2;
        Sim_App.export_ProductLikelihood_WolframPlot_btn = new CustomCheckbox("Export", true, null);
        Sim_App.export_ProductLikelihood_WolframPlot_btn.setBounds(c1_x + tiny_gap, export_ProductLikelihood_WolframPlot_btn_y, 95, small_text_height);
        add((Checkbox) Sim_App.export_ProductLikelihood_WolframPlot_btn.getCheckbox());

        Sim_App.export_LikelihoodPlot_Frame = new CustomTextArea("Likelihood Plot",2,1, TextArea.SCROLLBARS_NONE);
        Sim_App.export_LikelihoodPlot_Frame.setBackground(Color.lightGray);
        Sim_App.export_LikelihoodPlot_Frame.setFont(new Font("Arial", Font.BOLD, 13));
        Sim_App.export_LikelihoodPlot_Frame.setEnabled(true);
        Sim_App.export_LikelihoodPlot_Frame.setFocusable(false);
        Sim_App.export_LikelihoodPlot_Frame.setEditable(false);
        Sim_App.export_LikelihoodPlot_Frame.setBounds(c1_x, export_ProductLikelihood_Label_y, c2_x - c1_x, export_ProductLikelihood_Label_height);
        add((TextArea) Sim_App.export_LikelihoodPlot_Frame.getTextArea());



        // This Section is for the Project Name/db Setup
        //String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        //projectName_inputTextArea = new CustomTextField(timeStamp);
        Sim_App.projectName_LabelArea = new CustomTextArea("Export to Folder:",1,1, TextArea.SCROLLBARS_NONE);
        Sim_App.projectName_LabelArea.setBounds(c3_x, export_ProductLikelihood_Label_y, normal_gap, small_text_height);
        Sim_App.projectName_LabelArea.setBackground(Color.lightGray);
        Sim_App.projectName_LabelArea.setEnabled(true);
        Sim_App.projectName_LabelArea.setFocusable(false);
        add((TextArea) Sim_App.projectName_LabelArea.getTextArea());

        Sim_App.projectName_inputTextField = new CustomTextField(Sim_App.evaluated_scenario_name);
        Sim_App.projectName_inputTextField.setBounds(c3_x + normal_gap, export_ProductLikelihood_Label_y, c3_content_width - normal_gap, small_text_height);
        Sim_App.projectName_inputTextField.addTextListener(new projectName_inputTextArea_Ensurer());
        add((TextField) Sim_App.projectName_inputTextField.getTextField());

        setSize(window_width, window_height);
        setLocation(10,10);

        Sim_App.window_adapter = new WnAdapter();
        addWindowListener(Sim_App.window_adapter);

        setBackground(Color.lightGray);
        setResizable(false);

        setVisible(true);
        toFront();

        // Initiate the automated resumer
        //auto_optimization_resumer();
    }

    // This function is used during the headless execution
    public static void headless_exec() {
        Sim_App.outputTerminal = new CustomTextArea("");
        Sim_App.resumeBtn = new CustomButton();
        Sim_App.stopBtn = new CustomButton();
        Sim_App.stopBtn.addActionListener(new stopBtnAdapter());
        Sim_App.autoResume = new CustomCheckbox("GO", false);
        Sim_App.projectName_inputTextField = new CustomTextField(Sim_App.evaluated_scenario_name);

        // On the very first iteration, ensure that folder structure is as supposed to be
        if (Sim_App.current_eval_iteration == 0){
            // First ensure that the given outpath folder path to store the estimations is valid
            Sim_App.ensure_folder_existence(Sim_App.outpath_results_folder_path);

            // Then, ensure that the given project name exists as a folder
            Sim_App.ensure_folder_existence(Sim_App.outpath_results_folder_path + Sim_App.evaluated_scenario_name + "/");
        }

        // Construct the destination path for the results of current iteration
        Sim_App.output_iterated_results_folder_path = Sim_App.outpath_results_folder_path + Sim_App.evaluated_scenario_name + "/" + Sim_App.current_eval_iteration + "/";

        // Check whether current iteration has already been done before
        boolean proceed_with_optimization = Sim_App.handle_previous_results(output_iterated_results_folder_path);

        // Initiate the automated resumer
        auto_optimization_resumer();

        // Proceed or not with current iteration depending on the existence of previous results
        if (!proceed_with_optimization){
            // Stop the auto-resumer for the current optimization process
            Sim_App.scheduled_auto_resumer.cancel(true);
            Sim_App.scheduler.shutdown();
        }
    }

    // This methods takes care of the proper folder structure.
    // After any handling, it returns False only if we have already executed current evaluation iteration
    private static boolean handle_previous_results(String output_iterated_results_folder_path) {
        System.out.println("\nChecking for previous results at: " + output_iterated_results_folder_path);

        // Then, ensure that the given project name exists as a folder
        if (Sim_App.ensure_folder_existence(output_iterated_results_folder_path)){
            // Being here means that this iteration has not been executed before
            System.out.println("Destination folder: " + output_iterated_results_folder_path + " has been created");
            return true;
        }
        else{
            System.out.println("Destination folder: " + output_iterated_results_folder_path + " already exists");

            // Being here means that this iteration has been executed before.
            // Therefore, we need to check whether the previous results have been successfully delivered
            File result_file = new File(output_iterated_results_folder_path + "/results.log");
            boolean result_file_exists = result_file.exists();

            if (result_file_exists){
                // Being here means that a result file has been located. Therefore, we need to skip this iteration
                System.out.println("Previous results have finished. Proceeding to next iteration");
                return false;
            }
            else{
                // Being here means that a result file has not been.
                // Therefore, we wipe current folder and we start from the beginning
                System.out.println("Previous results have not finished.");

                File directoryToBeDeleted = new File(output_iterated_results_folder_path);

                boolean folder_deleted = deleteDirectory(directoryToBeDeleted);
                System.out.println("Deleting directory: " + output_iterated_results_folder_path + " = " + folder_deleted);

                boolean folder_created = directoryToBeDeleted.mkdirs();
                System.out.println("Creating directory: " + output_iterated_results_folder_path + " = " + folder_created);

                return true;
            }
        }
    }

    static void update_canvas_plot(String last_generated_image_path){

        // Get the last generated IMG result
        File fileInput = new File(FileSystems.getDefault().getPath("").toAbsolutePath() + "/Export/" + last_generated_image_path);

        BufferedImage last_generated_image;
        BufferedImage scaledImage = null;

        try {
            last_generated_image = ImageIO.read(fileInput);

            // Scale the image
            final int w = last_generated_image.getWidth();
            final int h = last_generated_image.getHeight();
            scaledImage = new BufferedImage((int) (w * 0.9),(int) (h * 0.9), BufferedImage.TYPE_INT_ARGB);
            final AffineTransform at = AffineTransform.getScaleInstance(0.9, 0.9);
            final AffineTransformOp ato = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
            scaledImage = ato.filter(last_generated_image, scaledImage);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // todo: mathCanvas.setImage(scaledImage);
    }

    static void append_to_text_area(String text){
        String current_text = Sim_App.outputTerminal.getText();
        Sim_App.outputTerminal.setText(current_text + text + "\n");
    }

    static void set_properties_as_available(boolean enabled){

        Sim_App.projectName_inputTextField.setEnabled(enabled);

        // Update the map-exporting toggler's state
        if (!Sim_App.headless_mode){
            Sim_App.results_per_step_btn.setEnabled(enabled);
            Sim_App.results_per_cycle_btn.setEnabled(enabled);

            Sim_App.spatial_direction_btn.setEnabled(enabled);
            Sim_App.rss_density_check_btn.setEnabled(enabled);
        }

        Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField.setEnabled(enabled);
        Sim_App.max_optimization_time_inputTextField.setEnabled(enabled);
        Sim_App.min_effective_measurement_inputTextField.setEnabled(enabled);

        Sim_App.plotResolution_inputTextField.setEnabled(enabled);

        if (!Sim_App.headless_mode){
            Sim_App.export_ProductLikelihood_WolframPlot_btn.setEnabled(enabled);
        }
    }

    // This listener ensures an Arithmetic text of less than 500 value
    static private class integer_greater_than_zero_Ensurer implements TextListener {

        CustomTextField attachedTextField;
        String previous_valid_value = "1";


        integer_greater_than_zero_Ensurer(CustomTextField attachedTextField){
            this.attachedTextField = attachedTextField;
        }

        public void textValueChanged(TextEvent evt) {

            String current_text = attachedTextField.getText();

            if (current_text.length() == 0){
                attachedTextField.setText("1");
            }

            // We do this check and corresponding update to be able to escape the infinite loop
            else if (!current_text.equals(previous_valid_value)){

                // Check whether we have exceeded the maximum allowed filename length
                String new_text = current_text.replaceAll("[^\\p{N}]+", "");

                try {
                    int plot_res = Integer.parseInt(new_text);

                    if (plot_res>0){
                        previous_valid_value = new_text;
                        attachedTextField.setText(new_text);
                    }
                    else{
                        attachedTextField.setText(previous_valid_value);
                    }
                } catch (Exception e) {
                    attachedTextField.setText(previous_valid_value);
                }

                attachedTextField.setCaretPosition(attachedTextField.getText().length());
            }
        }
    }

    // This listener ensures an Arithmetic text of less than 500 value
    static private class plotResolution_inputTextArea_Ensurer implements TextListener {
        public void textValueChanged(TextEvent evt) {

            String current_text = Sim_App.plotResolution_inputTextField.getText();

            if (current_text.length() == 0){
                Sim_App.plotResolution_inputTextField.setText("1");
            }

            // We do this check and corresponding update to be able to escape the infinite loop
            else if (!current_text.equals(Sim_App.previous_valid_plot_resolution)){

                // Check whether we have exceeded the maximum allowed filename length
                String new_text = current_text.replaceAll("[^\\p{N}]+", "");

                try {
                    int plot_res = Integer.parseInt(new_text);

                    if (plot_res<501){
                        Sim_App.previous_valid_plot_resolution = new_text;
                        Sim_App.plotResolution_inputTextField.setText(new_text);
                    }
                    else{
                        Sim_App.plotResolution_inputTextField.setText(Sim_App.previous_valid_plot_resolution);
                    }
                } catch (Exception e) {
                    Sim_App.plotResolution_inputTextField.setText(Sim_App.previous_valid_plot_resolution);
                }

                Sim_App.plotResolution_inputTextField.setCaretPosition(Sim_App.plotResolution_inputTextField.getText().length());
            }
        }
    }

    // This listener ensures an Alpharithmetic text of less than 16 Chars length
    static private class projectName_inputTextArea_Ensurer implements TextListener {
        public void textValueChanged(TextEvent evt) {

            String current_text = Sim_App.projectName_inputTextField.getText();

            Sim_App.resumeBtn.setEnabled(current_text.length() != 0);

            // We do this check and corresponding update to be able to escape the infinite loop
            if (!current_text.equals(Sim_App.previous_valid_project_name)){

                // Check whether we have exceeded the maximum allowed filename length
                if (current_text.length() > 15){
                    Sim_App.projectName_inputTextField.setText(Sim_App.previous_valid_project_name);
                }
                else{
                    String new_text = current_text.replaceAll("[^\\p{L}\\p{N} ]+", "");
                    Sim_App.previous_valid_project_name = new_text;
                    Sim_App.projectName_inputTextField.setText(new_text);
                }
                Sim_App.projectName_inputTextField.setCaretPosition(Sim_App.projectName_inputTextField.getText().length());
            }
        }
    }

    static private class openRSSdbBtnAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            final JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new GridLayout(0, 1));

            FileDialog fd = new FileDialog(frame, "Test", FileDialog.LOAD);
            fd.setVisible(true);

            evaluated_scenario_name = fd.getFile().replace(".smpl", "").replace(".rss", "");
            input_file_path = fd.getDirectory();

            Sim_App.loaded_db_name_LabelArea.setBackground(Color.white);
            Sim_App.loaded_db_name_LabelArea.setText(evaluated_scenario_name + " at " + input_file_path);

            System.out.println(evaluated_scenario_name + " database in " + input_file_path + " loaded."); // TODO: Add it on terminal

        }
    }

    static private class clearTerminalBtnAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Sim_App.outputTerminal.setText("");
        }
    }

    static private void resume(){

        // Execute everything in a new thread to avoid coming in conflict with the GUI's thread
        Sim_App.t1 = new Thread(() -> {
            Sim_App.resumeBtn.setEnabled(false);
            Sim_App.stopBtn.setEnabled(true);
            Sim_App.autoResume.setEnabled(false);
            Sim_App.stopBtn.setVisible(true);
            Sim_App.resumeBtn.setVisible(false);

            String results_per_selection;
            String optimization_order_selection;

            // Get the state of the results_per CustomCheckbox
            if (Sim_App.headless_mode || !Sim_App.results_per_step_btn.getState()){
                results_per_selection = "Cycle";
            }
            else{
                results_per_selection = "Step";
            }

            // Get the state of the optimization_order CustomCheckbox
            if (Sim_App.headless_mode || Sim_App.rss_density_check_btn.getState()){
                optimization_order_selection = "Beliefs-Strength";
            }
            else{
                optimization_order_selection = "Last Cycle's Principal Component Score";
            }

            // If we are performing an optimization based on Density, we need to mention the utilised parameter
            String kNN_for_beliefs_strength_check = "\n";
            if (Sim_App.headless_mode || Sim_App.rss_density_check_btn.getState()){
                kNN_for_beliefs_strength_check = "\nkNN to consider for the Beliefs-Strength check: " + Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField.getText() + " Neighbors\n";
            }

            // If we are exporting also Wolfram features, we need to mention which these are
            String likelihoods_export = "None]\n";
            if (Sim_App.headless_mode || Sim_App.export_ProductLikelihood_WolframPlot_btn.getState()){
                likelihoods_export = "Wolfram Plot]\n";
            }

            String summary_msg ="\n=========== Optimization Initiated ===========" +
                    "\nExport folder: " + Sim_App.output_iterated_results_folder_path +
                    "\nMax step-optimization runtime per thread: " + Sim_App.max_optimization_time_inputTextField.getText() + "ms" +
                    "\nMin effective measurement value: " + Sim_App.min_effective_measurement_inputTextField.getText() + "units" +
                    "\nftol: " + ftol +
                    "\nIterations: " + optimization_iterations +
                    "\nResults per: " + results_per_selection +
                    "\nOptimization order: " + optimization_order_selection +
                    kNN_for_beliefs_strength_check +
                    "Likelihoods export: [" + likelihoods_export;

            append_to_text_area(summary_msg);

            set_properties_as_available(false);

            Core.resume_SwarmPositioning();

            set_properties_as_available(true);
            Sim_App.stopBtn.setEnabled(false);
            Sim_App.resumeBtn.setEnabled(true);
            Sim_App.autoResume.setEnabled(true);
            Sim_App.resumeBtn.setVisible(true);
            Sim_App.stopBtn.setVisible(false);
        });
        Sim_App.t1.start();
    }

    static private class resumeBtnAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            // Set user's optimization parameters
            // Booleans
            Sim_App.results_per_step = Sim_App.results_per_step_btn.isEnabled();
            Sim_App.results_per_cycle = Sim_App.results_per_cycle_btn.isEnabled();
            Sim_App.ble_model = Sim_App.ble_model_btn.isEnabled();
            Sim_App.uwb_model = Sim_App.uwb_model_btn.isEnabled();
            Sim_App.export_ProductLikelihood_WolframPlot = Sim_App.export_ProductLikelihood_WolframPlot_btn.isEnabled();

            // Values
            Sim_App.min_effective_measurement = Integer.parseInt(Sim_App.min_effective_measurement_inputTextField.getText());
            Sim_App.threads = Integer.parseInt(Sim_App.threads_inputTextField.getText());
            Sim_App.optimization_iterations = Integer.parseInt(Sim_App.optimization_iterations_inputTextField.getText());
            Sim_App.ftol = Integer.parseInt(Sim_App.ftol_inputTextField.getText());
            Sim_App.initial_step_size = Integer.parseInt(Sim_App.initial_step_size_inputTextField.getText());
            Sim_App.max_optimization_time = Integer.parseInt(Sim_App.max_optimization_time_inputTextField.getText());
            Sim_App.kNearestNeighbours_for_BeliefsStrength = Integer.parseInt(Sim_App.kNearestNeighbours_for_BeliefsStrength_inputTextField.getText());
            Sim_App.plotResolution = Integer.parseInt(Sim_App.plotResolution_inputTextField.getText());
            //Sim_App.projectName = projectName_inputTextField.getText();

            resume();
        }
    }

    static void stop_optimization(){
        Sim_App.stopBtn.setEnabled(false);
        Sim_App.autoResume.setState(false);

        Date date = new Date();

        append_to_text_area("Force stop at: " + Sim_App.day_formatter.format(date));

        // Interrupt all the Optimizers
        for (Optimizer optimizer: MathEngine.swarmPositioningOptimizers){
            optimizer.interrupt();
        }

        // Interrupt also the Optimizer Handler
        Sim_App.t1.interrupt();

        // In case we are in-between a likelihood export process, cancel it
        if (Sim_App.rendering_wolfram_data){
            // Todo cancel any future possible rendering
        }

        while (true){
            // Make the loop break for a bit to not suffocate the thread while waiting
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {}
            // Wait until the optimization has been stopped
            if(!Sim_App.optimization_running){
                set_properties_as_available(true);
                break;
            }
        }

        if (MathEngine.NodePos_Results_filename != null){
            // Dump all results in a log file
            try (PrintWriter out = new PrintWriter(MathEngine.NodePos_Results_filename)) {
                String current_logs = Sim_App.outputTerminal.getText();
                if (Sim_App.headless_mode){
                    out.println(current_logs);
                }
                else {
                    out.println(current_logs.substring(0, current_logs.lastIndexOf("=========== Optimization Initiated ===========")));
                }
            } catch (FileNotFoundException exception) {
                exception.printStackTrace();
            }
        }
    }

    static private class stopBtnAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            stop_optimization();
        }
    }

    class WnAdapter extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
            dispose();
            System.exit(0);
        }
    }

    private static void auto_optimization_resumer() {
        scheduler = Executors.newScheduledThreadPool(1);

        final Runnable auto_resumer = () -> {
            //System.out.println("Sim_App.autoResume.getState():" + Sim_App.autoResume.getState() + " Sim_App.autoResume.isEnabled():" + Sim_App.autoResume.isEnabled());

            // Check if the user has just enabled the auto resumer
            if (Sim_App.autoResume.getState() && Sim_App.autoResume.isEnabled()){
                resume();
            }
        };

        scheduled_auto_resumer = scheduler.scheduleAtFixedRate(auto_resumer, 0, 100, MILLISECONDS);
    }

    static boolean ensure_folder_existence(String selected_path){
        File directory = new File(selected_path);
        return directory.mkdirs();
    }

    static boolean deleteDirectory(File directoryToBeEmptied) {

        File[] allContents = directoryToBeEmptied.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeEmptied.delete();
    }

    static void writeString2File(String filename, String data, boolean export_likelihood){

        String model_plot_str_addon = "";
        if (export_likelihood){
            model_plot_str_addon = "model, ";
        }

        try (PrintWriter out = new PrintWriter(filename)) {
            out.println(data.substring(0, data.lastIndexOf("\n")));
            // Make a check here to see if the list containing the effective_nodes has the same size as the entire Node db-1
            // This means that there is no non-Effective Node. Hence, we need to exclude Plot B
            if (Sim_App.effective_remoteNodes.size() == (Sim_App.nodeID_to_nodeObject.size()-1)){
                out.println("Show[" + model_plot_str_addon + "plotA, plotC]");
            }
            else{
                out.println("Show[" + model_plot_str_addon + "plotA, plotB, plotC]");
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}