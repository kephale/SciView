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

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import net.miginfocom.swing.MigLayout;

import org.scijava.Context;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.ModuleException;
import org.scijava.module.ModuleItem;
import org.scijava.module.ModuleService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.PluginService;
import org.scijava.plugin.SciJavaPlugin;
import org.scijava.service.Service;
import org.scijava.ui.swing.widget.SwingInputHarvester;
import org.scijava.ui.swing.widget.SwingInputPanel;
import org.scijava.util.DebugUtils;
import org.scijava.widget.UIComponent;

import sc.iview.SciView;

import graphics.scenery.Node;

/**
 * Interactive UI for visualizing and editing the scene graph.
 *
 * @author Curtis Rueden
 */
public class SceneEditor implements UIComponent<JPanel> {

    private final SciView sciView;

    @Parameter
    private PluginService pluginService;

    @Parameter
    private ModuleService moduleService;

    @Parameter
    private CommandService commandService;

    @Parameter
    private LogService log;

    private JPanel panel;
    private DefaultTreeModel treeModel;
    private JTree tree;
    private JPanel props;

    public SceneEditor( final SciView sciView ) {
        this.sciView = sciView;
        sciView.getScijavaContext().inject( this );
    }

    /** Creates and displays a window containing the scene editor. */
    public void show() {
        final JFrame frame = new JFrame( "Scene Editor" );
        frame.setContentPane( getComponent() );
        // FIXME: Why doesn't the frame disappear when closed?
        frame.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
        frame.setSize( 600, 400 );
        frame.setVisible( true );
    }

    @Override
    public JPanel getComponent() {
        if( panel == null ) initPanel();
        return panel;
    }

    @Override
    public Class<JPanel> getComponentType() {
        return JPanel.class;
    }

    /** Initializes {@link #panel}. */
    private synchronized void initPanel() {
        if( panel != null ) return;
        final JPanel p = new JPanel();
        p.setLayout( new BorderLayout() );

        createTree();

        props = new JPanel();
        props.setLayout( new MigLayout( "inset 0", "[grow,fill]", "[grow,fill]" ) );
        updateProperties( null );

        final JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, //
                                                     new JScrollPane( tree ), //
                                                     new JScrollPane( props ) );
        splitPane.setDividerLocation( 200 );
        p.add( splitPane, BorderLayout.CENTER );

        panel = p;
    }

    private void createTree() {
        final SceneryTreeNode root = new SceneryTreeNode();
        for( final Node sceneNode : sciView.getSceneNodes( n -> true ) ) {
            addNode( root, sceneNode );
        }

        treeModel = new DefaultTreeModel( root );
        tree = new JTree( treeModel );

        tree.getSelectionModel().setSelectionMode( TreeSelectionModel.SINGLE_TREE_SELECTION );
        tree.addTreeSelectionListener( e -> {
            final Object treeNode = e.getNewLeadSelectionPath().getLastPathComponent();
            final Node sceneNode = sceneNode( treeNode );
            sciView.setActiveNode( sceneNode );
            updateProperties( sceneNode );
        } );
    }

    private void addNode( final SceneryTreeNode parent, final Node child ) {
        final SceneryTreeNode treeNode = new SceneryTreeNode( child );
        parent.add( treeNode );
        for( final Node n : child.getChildren() ) {
            addNode( treeNode, n );
        }
    }

    /** Generates a properties panel for the given node. */
    private void updateProperties( final Node sceneNode ) {
        if( sceneNode == null ) {
            updatePropertiesPanel( null );
            return;
        }

        // Prepare the Properties command module instance.
        final CommandInfo info = commandService.getCommand( Properties.class );
        final Module module = moduleService.createModule( info );
        resolveInjectedInputs( module );
        module.setInput( "sciView", sciView );
        module.setInput( "sceneNode", sceneNode );
        module.resolveInput( "sciView" );
        module.resolveInput( "sceneNode" );
        final Properties p = (Properties) module.getDelegateObject();
        p.setSceneNode( sceneNode );

        // Prepare the SwingInputHarvester.
        final PluginInfo<SciJavaPlugin> pluginInfo = pluginService.getPlugin( SwingInputHarvester.class );
        final SciJavaPlugin pluginInstance = pluginService.createInstance( pluginInfo );
        final SwingInputHarvester harvester = ( SwingInputHarvester ) pluginInstance;
        final SwingInputPanel inputPanel = harvester.createInputPanel();

        // Build the panel.
        try {
            harvester.buildPanel( inputPanel, module );
            updatePropertiesPanel( inputPanel.getComponent() );
        } catch( final ModuleException exc ) {
            log.error( exc );
            final String stackTrace = DebugUtils.getStackTrace( exc );
            final JTextArea textArea = new JTextArea();
            textArea.setText( "<html><pre>" + stackTrace + "</pre>" );
            updatePropertiesPanel( textArea );
        }
    }

    private void updatePropertiesPanel( final Component c ) {
        props.removeAll();
        props.add( c == null ? new JLabel( "<no node selected>" ) : c );
        props.validate();
        props.repaint();
    }

    /** Refreshes the tree to match the state of its model. */
    private void refreshTree() {
        treeModel.reload();
        // TODO: retain previously expanded nodes only
        for( int i = 0; i < tree.getRowCount(); i++ ) {
            tree.expandRow( i );
        }
    }

    /** Retrieves the scenery node of a given tree node. */
    private Node sceneNode( final Object treeNode ) {
        if( !( treeNode instanceof SceneryTreeNode ) ) return null;
        final SceneryTreeNode stn = ( SceneryTreeNode ) treeNode;
        final Object userObject = stn.getUserObject();
        if( !( userObject instanceof Node ) ) return null;
        return ( Node ) userObject;
    }

    /** HACK: Resolve injected {@link Context} and {@link Service} inputs. */
    private void resolveInjectedInputs( final Module module ) {
        for( final ModuleItem<?> input : module.getInfo().inputs() ) {
            final Class<?> type = input.getType();
            if( Context.class.isAssignableFrom( type ) || Service.class.isAssignableFrom( type ) ) {
                module.resolveInput( input.getName() );
            }
        }
    }

    private static class SceneryTreeNode extends DefaultMutableTreeNode {
        private final Node node;

        public SceneryTreeNode() {
            this( null );
        }

        public SceneryTreeNode( final Node node ) {
            super( node );
            this.node = node;
        }

        @Override
        public String toString() {
            return node == null ? "<SCENE>" : node.getName();
        }
    }
}
