/*-
 * #%L
 * Scenery-backed 3D visualization package for ImageJ.
 * %%
 * Copyright (C) 2016 - 2018 SciView developers.
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
package sc.iview.commands.edit;

import static org.scijava.widget.ChoiceWidget.LIST_BOX_STYLE;
import static sc.iview.commands.MenuWeights.EDIT;
import static sc.iview.commands.MenuWeights.EDIT_PROPERTIES;

import com.jogamp.opengl.math.Quaternion;

import java.util.ArrayList;

import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.ColorRGB;
import org.scijava.widget.NumberWidget;

import sc.iview.SciView;

import cleargl.GLVector;
import graphics.scenery.Material;
import graphics.scenery.Node;
import graphics.scenery.PointLight;

/**
 * A command for interactively editing a node's properties.
 * <ul>
 * <li>TODO: If the list of sceneNode changes while this dialog is open, it may
 * not be notified and thus, may cause strange behaviours. Furthermore,
 * refreshing the list of choises does not work. :(</li>
 * <li>Todo: Change the order of the property items. Scene node must be on top,
 * as the user selects here which object to manipulate.</li>
 * <li>Todo: As soon as object selection in Scenery itself works, the node
 * pulldown may be removed entirely.</li>
 * </ul>
 *
 * @author Robert Haase, Scientific Computing Facility, MPI-CBG Dresden
 * @author Curtis Rueden
 */
@Plugin(type = Command.class, initializer = "initValues", menuRoot = "SciView", //
        menu = { @Menu(label = "Edit", weight = EDIT), //
                 @Menu(label = "Properties...", weight = EDIT_PROPERTIES) })
public class Properties extends InteractiveCommand {

    private static final String PI_NEG = "-3.14159265358979323846";
    private static final String PI_POS = "3.14159265358979323846";

    @Parameter
    private UIService uiSrv;

    @Parameter
    private SciView sciView;

    @Parameter(required = false, style = LIST_BOX_STYLE, callback = "refreshSceneNodeInDialog")
    private String sceneNode;

    @Parameter(callback = "updateNodeProperties")
    private boolean visible;

    @Parameter(required = false, callback = "updateNodeProperties")
    private ColorRGB colour;

    @Parameter(label = "Position X", style = NumberWidget.SCROLL_BAR_STYLE, //
            min = "-50.0", max = "50.0", callback = "updateNodeProperties")
    private float positionX = 1;

    @Parameter(label = "Position Y", style = NumberWidget.SCROLL_BAR_STYLE, //
            min = "-50.0", max = "50.0", callback = "updateNodeProperties")
    private float positionY = 1;

    @Parameter(label = "Position Z", style = NumberWidget.SCROLL_BAR_STYLE, //
            min = "-50.0", max = "50.0", callback = "updateNodeProperties")
    private float positionZ = 1;

    @Parameter(label = "Rotation Phi", style = NumberWidget.SCROLL_BAR_STYLE, //
            min = PI_NEG, max = PI_POS, stepSize = "0.01", callback = "updateNodeProperties")
    private float rotationPhi;

    @Parameter(label = "Rotation Theta", style = NumberWidget.SCROLL_BAR_STYLE, //
            min = PI_NEG, max = PI_POS, stepSize = "0.01", callback = "updateNodeProperties")
    private float rotationTheta;

    @Parameter(label = "Rotation Psi", style = NumberWidget.SCROLL_BAR_STYLE, //
            min = PI_NEG, max = PI_POS, stepSize = "0.01", callback = "updateNodeProperties")
    private float rotationPsi;

    boolean fieldsUpdating = true;

    ArrayList<String> sceneNodeChoices = new ArrayList<>();
    private Node currentSceneNode;

    /**
     * Nothing happens here, as cancelling the dialog is not possible.
     */
    @Override
    public void cancel() {

    }

    /**
     * Nothing is done here, as the refreshing of the objects properties works via
     * callback methods.
     */
    @Override
    public void run() {

    }

    public void setSceneNode( final Node node ) {
        currentSceneNode = node;
        updateCommandFields();
    }

    protected void initValues() {
        rebuildSceneObjectChoiceList();
        refreshSceneNodeInDialog();
        updateCommandFields();
    }

    private void rebuildSceneObjectChoiceList() {
        fieldsUpdating = true;
        sceneNodeChoices = new ArrayList<>();
        int count = 0;
        // here, we want all nodes of the scene, not excluding PointLights and Cameras
        for( final Node node : sciView.getSceneNodes( n -> true ) ) {
            sceneNodeChoices.add( makeIdentifier( node, count ) );
            count++;
        }

        final MutableModuleItem<String> sceneNodeSelector = getInfo().getMutableInput( "sceneNode", String.class );
        sceneNodeSelector.setChoices( sceneNodeChoices );

        //todo: if currentSceneNode is set, put it here as current item
        sceneNodeSelector.setValue( this, sceneNodeChoices.get( sceneNodeChoices.size() - 1 ) );
        refreshSceneNodeInDialog();

        fieldsUpdating = false;
    }

    /**
     * find out, which node is currently selected in the dialog.
     */
    private void refreshSceneNodeInDialog() {
        final String identifier = sceneNode; //sceneNodeSelector.getValue(this);
        currentSceneNode = null;

        int count = 0;
        for( final Node node : sciView.getSceneNodes( n -> true ) ) {
            if( identifier.equals( makeIdentifier( node, count ) ) ) {
                currentSceneNode = node;
                //System.out.println("current node found");
                break;
            }
            count++;
        }

        // update property fields according to scene node properties
        updateCommandFields();

        if( sceneNodeChoices.size() != sciView.getSceneNodes( n -> true ).length ) {
            rebuildSceneObjectChoiceList();
        }
    }

    /** Updates command fields to match current scene node properties. */
    private void updateCommandFields() {
        if( currentSceneNode == null ) return;

        fieldsUpdating = true;

        // update colour
        if( currentSceneNode.getMaterial() != null && currentSceneNode.getMaterial().getDiffuse() != null ) {
            GLVector colourVector;
            if( currentSceneNode instanceof PointLight ) {
                colourVector = ( ( PointLight ) currentSceneNode ).getEmissionColor();
            } else {
                colourVector = currentSceneNode.getMaterial().getDiffuse();
            }
            colour = new ColorRGB( ( int ) ( colourVector.get( 0 ) * 255 ), //
                                   ( int ) ( colourVector.get( 1 ) * 255 ), //
                                   ( int ) ( colourVector.get( 2 ) * 255 ) );
        }

        // update visibility
        visible = currentSceneNode.getVisible();

        // update position
        final GLVector position = currentSceneNode.getPosition();
        positionX = position.get( 0 );
        positionY = position.get( 1 );
        positionZ = position.get( 2 );

        // update rotation
        final float[] eulerAngles = new float[3];
        currentSceneNode.getRotation().toEuler( eulerAngles );
        rotationPhi = eulerAngles[0];
        rotationTheta = eulerAngles[1];
        rotationPsi = eulerAngles[2];

        fieldsUpdating = false;
    }

    /** Updates current scene node properties to match command fields. */
    protected void updateNodeProperties() {
        if( currentSceneNode == null || fieldsUpdating ) return;

        // update visibility
        currentSceneNode.setVisible( visible );

        // update rotation
        currentSceneNode.setRotation( new Quaternion().setFromEuler( rotationPhi, //
                                                                     rotationTheta, //
                                                                     rotationPsi ) );

        // update colour
        final GLVector cVector = new GLVector( colour.getRed() / 255f, //
                                               colour.getGreen() / 255f, //
                                               colour.getBlue() / 255f );
        if( currentSceneNode instanceof PointLight ) {
            ( ( PointLight ) currentSceneNode ).setEmissionColor( cVector );
        } else {
            final Material material = currentSceneNode.getMaterial();
            if (material != null) material.setDiffuse( cVector );
        }

        // update position
        final GLVector position = currentSceneNode.getPosition();
        position.set( 0, ( positionX ) );
        position.set( 1, ( positionY ) );
        position.set( 2, ( positionZ ) );
        currentSceneNode.setPosition( position );
    }

    private String makeIdentifier( final Node node, final int count ) {
        return "" + node.getName() + "[" + count + "]";
    }

}
