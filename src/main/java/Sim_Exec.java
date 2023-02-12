public class Sim_Exec {
    static {
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

        System.setProperty("java.awt.headless", "false");
        System.out.println("Headless mode: " + java.awt.GraphicsEnvironment.isHeadless());
    }

    public static void main(String[] argv){
        SimApp.run(argv);
    }
}
