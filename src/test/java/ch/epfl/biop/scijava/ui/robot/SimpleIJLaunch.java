package ch.epfl.biop.scijava.ui.robot;

import org.scijava.Context;
import org.scijava.ui.UIService;

public class SimpleIJLaunch {


    public static void main(final String... args) {
        Context context = new Context();
        // Show the Swing UI so the SciJava input harvester renders dialogs the
        // Robot can drive. Local display required — see class javadoc.
        context.service(UIService.class).showUI();


    }
}
