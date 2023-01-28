import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import org.jzy3d.analysis.AWTAbstractAnalysis;
import org.jzy3d.analysis.AnalysisLauncher;
import org.jzy3d.chart.Chart;
import org.jzy3d.chart.factories.AWTChartFactory;
import org.jzy3d.chart.factories.AWTPainterFactory;
import org.jzy3d.chart.factories.IChartFactory;
import org.jzy3d.chart.factories.IPainterFactory;
import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;
import org.jzy3d.maths.Range;
import org.jzy3d.plot3d.builder.Mapper;
import org.jzy3d.plot3d.builder.SurfaceBuilder;
import org.jzy3d.plot3d.builder.concrete.OrthonormalGrid;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.rendering.canvas.Quality;

//public class SurfaceDemoAWT extends AWTAbstractAnalysis {
//    public static void main(String[] args) throws Exception {
//        SurfaceDemoAWT d = new SurfaceDemoAWT();
//        AnalysisLauncher.open(d);
//    }
//
//    @Override
//    public void init() {
//        // Define a function to plot
//        Mapper mapper = new Mapper() {
//            @Override
//            public double f(double x, double y) {
//                return x * Math.sin(x * y);
//            }
//        };
//
//        // Define range and precision for the function to plot
//        Range range = new Range(-3, 3);
//        int steps = 80;
//
//        // Create the object to represent the function over the given range.
//        final Shape surface = new SurfaceBuilder().orthonormal(new OrthonormalGrid(range, steps), mapper);
//        surface.setFaceDisplayed(true);
//        surface.setWireframeDisplayed(true);
//        surface.setWireframeColor(Color.BLACK);
//
//        // Create a chart
//        GLCapabilities c = new GLCapabilities(GLProfile.get(GLProfile.GL2));
//        IPainterFactory p = new AWTPainterFactory(c);
//        IChartFactory f = new AWTChartFactory(p);
//
//        chart = f.newChart();
//        chart.getScene().getGraph().add(surface);
//    }
//}


import org.jzy3d.analysis.AWTAbstractAnalysis;
import org.jzy3d.analysis.AnalysisLauncher;
import org.jzy3d.chart.factories.AWTChartFactory;
import org.jzy3d.chart.factories.IChartFactory;
import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;
import org.jzy3d.colors.colormaps.ColorMapRainbow;
import org.jzy3d.maths.Range;
import org.jzy3d.plot3d.builder.Func3D;
import org.jzy3d.plot3d.builder.SurfaceBuilder;
import org.jzy3d.plot3d.builder.concrete.OrthonormalGrid;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.rendering.canvas.Quality;
import com.jogamp.opengl.awt.GLCanvas;


public class SurfaceDemoAWT {
    public static void main(String[] args) {
        //init();
    }

    public static Chart generatePlot() {
        // Define a function to plot
        Func3D func = new Func3D((x, y) -> x * Math.sin(x * y));
        Range range = new Range(-3, 3);
        int steps = 80;

        // Create the object to represent the function over the given range.
        final Shape surface = new SurfaceBuilder().orthonormal(new OrthonormalGrid(range, steps), func);
        surface.setColorMapper(new ColorMapper(new ColorMapRainbow(), surface, new Color(1, 1, 1, .5f)));
        surface.setFaceDisplayed(true);
        surface.setWireframeDisplayed(true);
        surface.setWireframeColor(Color.BLACK);

        // Create a chart
        //GLCapabilities c = new GLCapabilities(GLProfile.get(GLProfile.GL3));
        //IPainterFactory p = new AWTPainterFactory(c);
        IChartFactory f = new AWTChartFactory();

        Chart chart = f.newChart(Quality.Advanced().setHiDPIEnabled(true));
        chart.view2d();
        chart.getScene().getGraph().add(surface);
        //chart.open();
        return chart;
    }
}



//import org.jzy3d.chart.Chart;
//import org.jzy3d.chart.factories.AWTChartFactory;
//import org.jzy3d.maths.Range;
//import org.jzy3d.plot3d.primitives.SampleGeom;
//import org.jzy3d.plot3d.primitives.Shape;
//import org.jzy3d.plot3d.rendering.canvas.Quality;

/**
 * Demonstrate a 2D surface chart
 *
 * @author Martin Pernollet
 *
 */
//public class SurfaceDemoAWT {
//    public static void main(String[] args) throws Exception {
//        Shape surface = SampleGeom.surface(new Range (-3, 1), new Range(-1, 3), 1);
//        surface.setWireframeDisplayed(false);
//
//        Chart chart = new AWTChartFactory().newChart(Quality.Advanced());
//        chart.add(surface);
//        chart.view2d();
//        chart.open();
//        chart.addMouse();
//    }
//}






