import java.awt.*;

public class CustomCheckbox {

    Checkbox gui_Checkbox;
    String label;
    boolean state;
    boolean enabled;

    public CustomCheckbox(String label, boolean state, CheckboxGroup group) {
        this.gui_Checkbox = new Checkbox(label, state, group);
    }

    public CustomCheckbox(String label, boolean state) {
        this.label = label;
        this.state = state;
        this.enabled = true;
    }

    public void setBounds(int a, int b, int c,int d) {
        if (!Sim_App.headless_mode) {
            this.gui_Checkbox.setBounds(a,b,c,d);
        }
    }

    public void setEnabled(boolean b) {
        if (Sim_App.headless_mode) {
            this.enabled = b;
        }
        else {
            this.gui_Checkbox.setEnabled(b);
        }
    }

    public boolean isEnabled() {
        if (Sim_App.headless_mode) {
            return this.enabled;
        }
        else {
            return this.gui_Checkbox.isEnabled();
        }
    }

    public void setState(boolean b) {
        if (Sim_App.headless_mode) {
            this.state = b;
        }
        else {
            this.gui_Checkbox.setState(b);
        }
    }

    public boolean getState() {
        if (Sim_App.headless_mode) {
            return this.state;
        }
        else {
            return this.gui_Checkbox.getState();
        }
    }

    public Object getCheckbox() {
        return this.gui_Checkbox;
    }
}
