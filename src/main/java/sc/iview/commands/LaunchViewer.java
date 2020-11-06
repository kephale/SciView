/*-
 * #%L
 * Scenery-backed 3D visualization package for ImageJ.
 * %%
 * Copyright (C) 2016 - 2020 SciView developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package sc.iview.commands;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;

import sc.iview.SciView;
import sc.iview.SciViewService;
import sc.iview.display.SciViewDisplay;
import sc.iview.commands.edit.RenderingDeviceChooser;

/**
 * Command to launch SciView
 *
 * @author Kyle Harrington
 *
 */
@Plugin(type = Command.class, menuPath = "Plugins>SciView")
public class LaunchViewer implements Command {

    @Parameter
    private DisplayService displayService;

    @Parameter
    private SciViewService sciViewService;

    @Parameter(required = false)
    private UIService uiService;

    @Parameter
    private PrefService prefsService;

    @Parameter(type = ItemIO.OUTPUT)
    private SciView sciView = null;

    @Override
    public void run() {
        //setup the System properties just in case the sciview shall be created anew
        RenderingDeviceChooser.setupSystemProperties(
                prefsService.get(RenderingDeviceChooser.class,"selectedRenderer") );

        final SciViewDisplay display = displayService.getActiveDisplay(SciViewDisplay.class);
        try {
            if (display == null) {
                sciView = sciViewService.getOrCreateActiveSciView();
            }
            else
                sciViewService.createSciView();
        } catch (Exception e) {
            e.printStackTrace();
        }

//        else if (uiService != null)
//            uiService.showDialog( "The SciView window is already open. For now, only one SciView window is supported.", "SciView" );
    }

}
