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
package sc.iview;

import com.sun.javafx.application.PlatformImpl;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import graphics.scenery.controls.behaviours.MovementCommand;
import net.imagej.Dataset;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RealPoint;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import org.lwjgl.system.MemoryUtil;
import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import org.scijava.ui.behaviour.BehaviourMap;
import sc.iview.process.MeshConverter;
import sc.iview.vec3.ClearGLDVec3;
import sc.iview.vec3.DVec3;
import sc.iview.vec3.DVec3s;

import cleargl.GLVector;
import coremem.enums.NativeTypeEnum;
import graphics.scenery.Box;
import graphics.scenery.Camera;
import graphics.scenery.DetachedHeadCamera;
import graphics.scenery.Line;
import graphics.scenery.Material;
import graphics.scenery.Mesh;
import graphics.scenery.Node;
import graphics.scenery.PointCloud;
import graphics.scenery.PointLight;
import graphics.scenery.SceneryBase;
import graphics.scenery.SceneryElement;
import graphics.scenery.Sphere;
import graphics.scenery.backends.Renderer;
import graphics.scenery.controls.InputHandler;
import graphics.scenery.controls.behaviours.ArcballCameraControl;
import graphics.scenery.controls.behaviours.FPSCameraControl;
import graphics.scenery.controls.behaviours.SelectCommand;
import graphics.scenery.controls.behaviours.SelectCommand.SelectResult;
import graphics.scenery.utils.SceneryPanel;
import graphics.scenery.volumes.Volume;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class SciView extends SceneryBase {

    @Parameter
    private LogService log;

    @Parameter
    private OpService ops;

    private Thread animationThread;
    private Node activeNode = null;

    private Boolean defaultArcBall = true;// arcball target broken

    Camera camera = null;

    private boolean initialized = false;// i know TODO

    private boolean useJavaFX = false;
    SceneryPanel imagePanel = null;

    public SciView( Context context ) {
        super( "SciView", 800, 600, true );
        context.inject( this );
    }

    public SciView( String applicationName, int windowWidth, int windowHeight ) {
        super( applicationName, windowWidth, windowHeight, false );
    }

    public InputHandler publicGetInputHandler() {
        return getInputHandler();
    }

    @Override
    public void init() {
        if( useJavaFX ) {
            CountDownLatch latch = new CountDownLatch( 1 );
            final SceneryPanel[] sceneryPanel = { null };

            PlatformImpl.startup( () -> {} );

            Platform.runLater( () -> {

                Stage stage = new Stage();
                stage.setTitle( getApplicationName() );

                StackPane stackPane = new StackPane();
                stackPane.setBackground( new Background( new BackgroundFill( Color.TRANSPARENT, CornerRadii.EMPTY,
                                                                             Insets.EMPTY ) ) );

                GridPane pane = new GridPane();
                Label label = new Label( getApplicationName() );

                sceneryPanel[0] = new SceneryPanel( getWindowWidth(), getWindowHeight() );

                GridPane.setHgrow( sceneryPanel[0], Priority.ALWAYS );
                GridPane.setVgrow( sceneryPanel[0], Priority.ALWAYS );

                GridPane.setFillHeight( sceneryPanel[0], true );
                GridPane.setFillWidth( sceneryPanel[0], true );

                GridPane.setHgrow( label, Priority.ALWAYS );
                GridPane.setHalignment( label, HPos.CENTER );
                GridPane.setValignment( label, VPos.BOTTOM );

                label.maxWidthProperty().bind( pane.widthProperty() );

                pane.setStyle( "-fx-background-color: rgb(20, 255, 20);" + "-fx-font-family: Consolas;" +
                               "-fx-font-weight: 400;" + "-fx-font-size: 1.2em;" + "-fx-text-fill: white;" +
                               "-fx-text-alignment: center;" );

                label.setStyle( "-fx-padding: 0.2em;" + "-fx-text-fill: black;" );

                label.setTextAlignment( TextAlignment.CENTER );

                pane.add( sceneryPanel[0], 1, 1 );
                pane.add( label, 1, 2 );
                stackPane.getChildren().addAll( pane );

                javafx.scene.Scene scene = new javafx.scene.Scene( stackPane );
                stage.setScene( scene );
                stage.setOnCloseRequest( event -> {
                    getRenderer().setShouldClose( true );

                    Platform.runLater( Platform::exit );
                } );
                stage.show();

                latch.countDown();
            } );

            try {
                latch.await();
            } catch( InterruptedException e1 ) {
                e1.printStackTrace();
            }

            setRenderer( Renderer.createRenderer( getHub(), getApplicationName(), getScene(), getWindowWidth(),
                                                  getWindowHeight(), sceneryPanel[0] ) );
        } else {
            setRenderer( Renderer.createRenderer( getHub(), getApplicationName(), getScene(), 512, 512 ) );
        }

        getHub().add( SceneryElement.Renderer, getRenderer() );

        PointLight[] lights = new PointLight[2];

        for( int i = 0; i < lights.length; i++ ) {
            lights[i] = new PointLight( 150.0f );
            lights[i].setPosition( new GLVector( 20.0f * i - 20.0f, 20.0f * i - 20.0f, 20.0f * i - 20.0f ) );
            lights[i].setEmissionColor( new GLVector( 1.0f, 1.0f, 1.0f ) );
            lights[i].setIntensity( 5000.2f * ( i + 1 ) );
            getScene().addChild( lights[i] );
        }

        Camera cam = new DetachedHeadCamera();
        //cam.setPosition( new GLVector(0.0f, 0.0f, 5.0f) );
        //cam.setPosition( new GLVector(20.0f, 10.0f, 35.0f) );
        cam.setPosition( new GLVector( 0.0f, 0.0f, 5.0f ) );
        cam.perspectiveCamera( 50.0f, getWindowWidth(), getWindowHeight(), 0.1f, 750.0f );
        cam.setTarget( new GLVector( 0, 0, 0 ) );
        cam.setTargeted( true );
        cam.setActive( true );
        getScene().addChild( cam );
        this.camera = cam;

        Box shell = new Box( new GLVector( 100.0f, 100.0f, 100.0f ), true );
        //Box shell = new Box(new GLVector(1200.0f, 2200.0f, 4500.0f), true);
        shell.getMaterial().setDiffuse( new GLVector( 0.2f, 0.2f, 0.2f ) );
        shell.getMaterial().setSpecular( GLVector.getNullVector( 3 ) );
        shell.getMaterial().setAmbient( GLVector.getNullVector( 3 ) );
        shell.getMaterial().setDoubleSided( true );
        shell.getMaterial().setCullingMode( Material.CullingMode.Front );
        // Could we generate a grid pattern with proper scale/units as a texture right now?
        getScene().addChild( shell );

        //initialized = true; // inputSetup is called second, so that needs to toggle initialized
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Camera getCamera() {
        return camera;
    }

    @Override
    public void inputSetup() {
        //setInputHandler((ClearGLInputHandler) viewer.getHub().get(SceneryElement.INPUT));

        getInputHandler().useDefaultBindings( "" );
        getInputHandler().addBehaviour( "object_selection_mode", new SelectCommand( "objectSelector", getRenderer(),
                                                                                    getScene(),
                                                                                    () -> getScene().findObserver(),
                                                                                    false, result -> this.selectNode(
                                                                                                                      result ) ) );

        enableArcBallControl();
        //enableFPSControl();



        setupCameraModeSwitching( "C" );

        initialized = true;
    }

    private Object selectNode( List<SelectResult> result ) {
        if( !result.isEmpty() ) {
            Collections.sort( result, new Comparator<SelectResult>() {
                @Override
                public int compare( SelectResult lhs, SelectResult rhs ) {
                    return lhs.getDistance() > rhs.getDistance() ? -1 : lhs.getDistance() < rhs.getDistance() ? 1 : 0;
                }
            } );
            activeNode = result.get( 0 ).getNode();
            //log.warn( "Selected " + activeNode );
            return activeNode;
        }
        return null;
    }

    public graphics.scenery.Node addBox() {
        return addBox( new GLVector( 0.0f, 0.0f, 0.0f ) );
    }

    public graphics.scenery.Node addBox( GLVector position ) {
        return addBox( position, new GLVector( 1.0f, 1.0f, 1.0f ) );
    }

    public graphics.scenery.Node addBox( GLVector position, GLVector size ) {
        return addBox( position, size, new GLVector( 0.9f, 0.9f, 0.9f ), false );
    }

    public graphics.scenery.Node addBox( GLVector position, GLVector size, GLVector color, boolean inside ) {
        // TODO: use a material from the current pallate by default
        Material boxmaterial = new Material();
        boxmaterial.setAmbient( new GLVector( 1.0f, 0.0f, 0.0f ) );
        boxmaterial.setDiffuse( color );
        boxmaterial.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
        boxmaterial.setDoubleSided( true );
        //boxmaterial.getTextures().put("diffuse", SceneViewer3D.class.getResource("textures/helix.png").getFile() );

        final Box box = new Box( size, inside );
        box.setMaterial( boxmaterial );
        box.setPosition( position );

        //System.err.println( "Num elements in scene: " + viewer.getSceneNodes().size() );

        activeNode = box;

        getScene().addChild( box );

        if( defaultArcBall ) enableArcBallControl();

        return box;

        //System.err.println( "Num elements in scene: " + viewer.getSceneNodes().size() );
    }

    public graphics.scenery.Node addSphere() {
        return addSphere( new GLVector( 0.0f, 0.0f, 0.0f ), 1 );
    }

    public graphics.scenery.Node addSphere( GLVector position, float radius ) {
        return addSphere( position, radius, new GLVector( 0.9f, 0.9f, 0.9f ) );
    }

    public graphics.scenery.Node addSphere( GLVector position, float radius, GLVector color ) {
        Material material = new Material();
        material.setAmbient( new GLVector( 1.0f, 0.0f, 0.0f ) );
        material.setDiffuse( color );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
        //boxmaterial.getTextures().put("diffuse", SceneViewer3D.class.getResource("textures/helix.png").getFile() );

        final Sphere sphere = new Sphere( radius, 20 );
        sphere.setMaterial( material );
        sphere.setPosition( position );

        activeNode = sphere;

        getScene().addChild( sphere );

        if( defaultArcBall ) enableArcBallControl();
        return sphere;
    }

    public graphics.scenery.Node addLine() {
        return addLine( new ClearGLDVec3( 0.0f, 0.0f, 0.0f ), new ClearGLDVec3( 0.0f, 0.0f, 0.0f ) );
    }

    public graphics.scenery.Node addLine( DVec3 start, DVec3 stop ) {
        return addLine( start, stop, new ClearGLDVec3( 0.9f, 0.9f, 0.9f ) );
    }

    public graphics.scenery.Node addLine( DVec3 start, DVec3 stop, DVec3 color ) {

        Material material = new Material();
        material.setAmbient( new GLVector( 1.0f, 1.0f, 1.0f ) );
        //material.setDiffuse( color ); // TODO line color
        material.setDiffuse( new GLVector( 1.0f, 1.0f, 1.0f ) );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );

        final Line line = new Line( 2 );

        // TODO remove line hack
        line.addPoint( new GLVector( 0, 0, 0 ) );
        line.addPoint( new GLVector( 0, 0, 0 ) );
        line.addPoint( DVec3s.convert( start ) );
        line.addPoint( DVec3s.convert( stop ) );

        line.setEdgeWidth( 0.1f );

        line.setMaterial( material );
        line.setPosition( DVec3s.convert( start ) );

        activeNode = line;

        getScene().addChild( line );

        if( defaultArcBall ) enableArcBallControl();
        return line;
    }

    public graphics.scenery.Node addLine( DVec3[] points, DVec3 color, double edgeWidth ) {

        Material material = new Material();
        material.setAmbient( new GLVector( 1.0f, 1.0f, 1.0f ) );
        //material.setDiffuse( color ); // TODO line color
        material.setDiffuse( new GLVector( 1.0f, 1.0f, 1.0f ) );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );

        final Line line = new Line( 2 );

        // TODO remove line hack
        for( DVec3 pt : points ) {
            line.addPoint( DVec3s.convert( pt ).minus( DVec3s.convert( points[0] ) ) );
        }

        line.setEdgeWidth( ( float ) edgeWidth );

        line.setMaterial( material );
        line.setPosition( DVec3s.convert( points[0] ) );

        activeNode = line;

        getScene().addChild( line );

        if( defaultArcBall ) enableArcBallControl();
        return line;
    }

    public graphics.scenery.Node addPointLight() {
        Material material = new Material();
        material.setAmbient( new GLVector( 1.0f, 0.0f, 0.0f ) );
        material.setDiffuse( new GLVector( 0.0f, 1.0f, 0.0f ) );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
        //boxmaterial.getTextures().put("diffuse", SceneViewer3D.class.getResource("textures/helix.png").getFile() );

        final PointLight light = new PointLight( 5.0f );
        light.setMaterial( material );
        light.setPosition( new GLVector( 0.0f, 0.0f, 0.0f ) );

        getScene().addChild( light );
        return light;
    }

    public void writeSCMesh( String filename, Mesh scMesh ) {
        File f = new File( filename );
        BufferedOutputStream out;
        try {
            out = new BufferedOutputStream( new FileOutputStream( f ) );
            out.write( "solid STL generated by FIJI\n".getBytes() );

            FloatBuffer normalsFB = scMesh.getNormals();
            FloatBuffer verticesFB = scMesh.getVertices();

            while( verticesFB.hasRemaining() && normalsFB.hasRemaining() ) {
                out.write( ( "facet normal " + normalsFB.get() + " " + normalsFB.get() + " " + normalsFB.get() +
                             "\n" ).getBytes() );
                out.write( "outer loop\n".getBytes() );
                for( int v = 0; v < 3; v++ ) {
                    out.write( ( "vertex\t" + verticesFB.get() + " " + verticesFB.get() + " " + verticesFB.get() +
                                 "\n" ).getBytes() );
                }
                out.write( "endloop\n".getBytes() );
                out.write( "endfacet\n".getBytes() );
            }
            out.write( "endsolid vcg\n".getBytes() );
            out.close();
        } catch( FileNotFoundException e ) {
            e.printStackTrace();
        } catch( IOException e ) {
            e.printStackTrace();
        }

    }

    public graphics.scenery.Node addSTL( String filename ) {

        Mesh scMesh = new Mesh();
        scMesh.readFromSTL( filename );

        addMesh( scMesh );
        return scMesh;
    }

    public graphics.scenery.Node addObj( String filename ) {
        Mesh scMesh = new Mesh();
        scMesh.readFromOBJ( filename, false );// Could check if there is a MTL to use to toggle flag

        addMesh( scMesh );

        return scMesh;
    }

    public static ArrayList<RealPoint> readXyz( String filename ) throws IOException {
        ArrayList<RealPoint> vertices = new ArrayList<>();

        try( BufferedReader br = new BufferedReader( new FileReader( filename ) ) ) {
            String line;
            while( ( line = br.readLine() ) != null ) {
                String[] parts = line.split( "\\s*(,|\\s)\\s*" );
                vertices.add( new RealPoint( Double.parseDouble( parts[0] ), Double.parseDouble( parts[1] ),
                                             Double.parseDouble( parts[2] ) ) );
            }
        }

        return vertices;
    }

    public float getDefaultPointSize() {
        return 0.025f;
    }

    public float[] makeNormalsFromVertices( ArrayList<RealPoint> verts ) {
        float[] normals = new float[verts.size()];// div3 * 3coords

        for( int k = 0; k < verts.size(); k += 3 ) {
            GLVector v1 = new GLVector( verts.get( k ).getFloatPosition( 0 ), //
                                        verts.get( k ).getFloatPosition( 1 ), //
                                        verts.get( k ).getFloatPosition( 2 ) );
            GLVector v2 = new GLVector( verts.get( k + 1 ).getFloatPosition( 0 ), verts.get( k + 1 ).getFloatPosition(
                                                                                                                       1 ),
                                        verts.get( k + 1 ).getFloatPosition( 2 ) );
            GLVector v3 = new GLVector( verts.get( k + 2 ).getFloatPosition( 0 ), verts.get( k + 2 ).getFloatPosition(
                                                                                                                       1 ),
                                        verts.get( k + 2 ).getFloatPosition( 2 ) );
            GLVector a = v2.minus( v1 );
            GLVector b = v3.minus( v1 );
            GLVector n = a.cross( b ).getNormalized();
            normals[k / 3] = n.get( 0 );
            normals[k / 3 + 1] = n.get( 1 );
            normals[k / 3 + 2] = n.get( 2 );
        }
        return normals;
    }

    public graphics.scenery.Node addXyz( String filename ) throws IOException {
        ArrayList<RealPoint> verts = readXyz( filename );

        float[] flatVerts = new float[verts.size() * 3];
        for( int k = 0; k < verts.size(); k++ ) {
            flatVerts[k * 3] = verts.get( k ).getFloatPosition( 0 );
            flatVerts[k * 3 + 1] = verts.get( k ).getFloatPosition( 1 );
            flatVerts[k * 3 + 2] = verts.get( k ).getFloatPosition( 2 );
        }

        PointCloud pointCloud = new PointCloud( getDefaultPointSize(), filename );
        Material material = new Material();
        FloatBuffer vBuffer = MemoryUtil.memAlloc( flatVerts.length * 4 ).order(
                                                                                 ByteOrder.nativeOrder() ).asFloatBuffer();
        FloatBuffer nBuffer = MemoryUtil.memAlloc( 0 ).order( ByteOrder.nativeOrder() ).asFloatBuffer();

        vBuffer.put( flatVerts );
        vBuffer.flip();

        pointCloud.setVertices( vBuffer );
        pointCloud.setNormals( nBuffer );
        pointCloud.setIndices( MemoryUtil.memAlloc( 0 ).order( ByteOrder.nativeOrder() ).asIntBuffer() );
        pointCloud.setupPointCloud();
        material.setAmbient( new GLVector( 1.0f, 1.0f, 1.0f ) );
        material.setDiffuse( new GLVector( 1.0f, 1.0f, 1.0f ) );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
        pointCloud.setMaterial( material );
        pointCloud.setPosition( new GLVector( 0f, 0f, 0f ) );
        getScene().addChild( pointCloud );
        return pointCloud;
    }

    public graphics.scenery.Node addNode( Node n ) {
        getScene().addChild( n );
        return n;
    }

    public graphics.scenery.Node addMesh( Mesh scMesh ) {
        Material material = new Material();
        material.setAmbient( new GLVector( 1.0f, 0.0f, 0.0f ) );
        material.setDiffuse( new GLVector( 0.0f, 1.0f, 0.0f ) );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
        material.setDoubleSided( false );

        scMesh.setMaterial( material );
        scMesh.setPosition( new GLVector( 1.0f, 1.0f, 1.0f ) );

        activeNode = scMesh;

        getScene().addChild( scMesh );

        if( defaultArcBall ) enableArcBallControl();

        return scMesh;
    }

    public graphics.scenery.Node addMesh( net.imagej.mesh.Mesh mesh ) {
        Mesh scMesh = MeshConverter.toScenery( mesh );

        log.warn( "Converting to a scenery mesh" );

        Material material = new Material();
        material.setAmbient( new GLVector( 1.0f, 0.0f, 0.0f ) );
        material.setDiffuse( new GLVector( 0.0f, 1.0f, 0.0f ) );
        material.setSpecular( new GLVector( 1.0f, 1.0f, 1.0f ) );
        material.setDoubleSided( true );

        scMesh.setMaterial( material );
        scMesh.setPosition( new GLVector( 1.0f, 1.0f, 1.0f ) );

        activeNode = scMesh;

        getScene().addChild( scMesh );

        if( defaultArcBall ) enableArcBallControl();

        displayNodeProperties( activeNode );

        return scMesh;
    }

    public void displayNodeProperties( Node n ) {
        log.warn( "Position: " + n.getPosition() + " bounding box: [ " + n.getBoundingBoxCoords()[0] + ", " +
                  n.getBoundingBoxCoords()[1] + ", " + n.getBoundingBoxCoords()[2] + ", " +
                  n.getBoundingBoxCoords()[3] + ", " + n.getBoundingBoxCoords()[4] + ", " +
                  n.getBoundingBoxCoords()[5] + " ]" );
    }

    public void removeMesh( Mesh scMesh ) {
        getScene().removeChild( scMesh );
    }

    public Node getActiveNode() {
        return activeNode;
    }

    public Thread getAnimationThread() {
        return animationThread;
    }

    public void setAnimationThread( Thread newAnimator ) {
        animationThread = newAnimator;
    }

    public void takeScreenshot() {
        getRenderer().screenshot();
    }

    public void enableArcBallControl() {
        GLVector target;
        if( getActiveNode() == null ) {
            target = new GLVector( 0, 0, 0 );
        } else {
            target = getActiveNode().getPosition();
        }

        Supplier<Camera> cameraSupplier = () -> getScene().findObserver();
        ArcballCameraControl targetArcball = new ArcballCameraControl( "mouse_control", cameraSupplier,
                                                                       getRenderer().getWindow().getWidth(),
                                                                       getRenderer().getWindow().getHeight(), target );
        targetArcball.setMaximumDistance( Float.MAX_VALUE );
        targetArcball.setMouseSpeedMultiplier( 0.25f );
        targetArcball.setScrollSpeedMultiplier( 0.05f );
        getInputHandler().addBehaviour( "mouse_control", targetArcball );
        getInputHandler().addBehaviour( "scroll_arcball", targetArcball );
        getInputHandler().addKeyBinding( "scroll_arcball", "scroll" );

        getInputHandler().addBehaviour("move_forward", new MovementCommand("move_forward", "forward", () -> getScene().findObserver(), 2.0f ) );
        getInputHandler().addBehaviour("move_back", new MovementCommand("move_back", "back", () -> getScene().findObserver(), 2.0f ) );
        getInputHandler().addBehaviour("move_left", new MovementCommand("move_left", "left", () -> getScene().findObserver(), 2.0f ) );
        getInputHandler().addBehaviour("move_right", new MovementCommand("move_right", "right", () -> getScene().findObserver(), 2.0f ) );
        getInputHandler().addBehaviour("move_up", new MovementCommand("move_up", "up", () -> getScene().findObserver(), 2.0f ) );
        getInputHandler().addBehaviour("move_down", new MovementCommand("move_down", "down", () -> getScene().findObserver(), 2.0f ) );
    }

    public void enableFPSControl() {
        Supplier<Camera> cameraSupplier = () -> getScene().findObserver();
        FPSCameraControl fpsControl = new FPSCameraControl( "mouse_control", cameraSupplier,
                                                            getRenderer().getWindow().getWidth(),
                                                            getRenderer().getWindow().getHeight() );

        getInputHandler().addBehaviour( "mouse_control", fpsControl );
        getInputHandler().removeBehaviour( "scroll_arcball" );
    }

    public Node[] getSceneNodes() {
        CopyOnWriteArrayList<Node> children = getScene().getChildren();

        return getScene().getChildren().toArray( new Node[children.size()] );
    }

    public void deleteSelectedMesh() {
        getScene().removeChild( getActiveNode() );
    }

    public void dispose() {
        getRenderer().setShouldClose( true );
    }

    public void moveCamera( float[] position ) {
        getCamera().setPosition( new GLVector( position[0], position[1], position[2] ) );
    }

    public void moveCamera( double[] position ) {
        getCamera().setPosition( new GLVector( ( float ) position[0], ( float ) position[1], ( float ) position[2] ) );
    }

    public String getName() {
        return getApplicationName();
    }

    public void addChild( Node node ) {
        getScene().addChild( node );
    }

    public graphics.scenery.Node addVolume( Dataset image, float[] voxelDimensions ) {

        log.warn( "Add Volume" );

        IterableInterval img = image.getImgPlus();

        long dimensions[] = new long[3];
        img.dimensions( dimensions );

        Class voxelType = img.firstElement().getClass();
        int bytesPerVoxel = ( ( RealType ) img.firstElement() ).getBitsPerPixel() / 8;
        float minVal = Float.MIN_VALUE, maxVal = Float.MAX_VALUE;
        NativeTypeEnum nType = null;

        if( voxelType == UnsignedByteType.class ) {
            minVal = 0;
            maxVal = 255;
            nType = NativeTypeEnum.UnsignedByte;
        } else if( voxelType == UnsignedShortType.class ) {
            minVal = 0;
            maxVal = 65535;
            nType = NativeTypeEnum.UnsignedShort;
        } else if( voxelType == FloatType.class ) {
            minVal = 0;
            maxVal = 1;
            nType = NativeTypeEnum.Float;
        } else {
            log.debug( "Type: " + voxelType +
                       " cannot be displayed as a volume. Convert to UnsignedByteType, UnsignedShortType, or FloatType." );
            return null;
        }

        // Make and populate a ByteBuffer with the content of the Dataset
        ByteBuffer byteBuffer = MemoryUtil.memAlloc( ( int ) ( bytesPerVoxel * dimensions[0] * dimensions[1] *
                                                               dimensions[2] ) );
        Cursor cursor = img.cursor();

        while( cursor.hasNext() ) {
            cursor.fwd();
            if( voxelType == UnsignedByteType.class ) {
                byteBuffer.put( ( byte ) ( ( ( UnsignedByteType ) cursor.get() ).get() & 0xff ) );
            } else if( voxelType == UnsignedShortType.class ) {
                byteBuffer.putShort( ( short ) Math.abs( ( ( UnsignedShortType ) cursor.get() ).getShort() ) );
            } else if( voxelType == FloatType.class ) {
                byteBuffer.putFloat( ( ( FloatType ) cursor.get() ).get() );
            }
        }
        byteBuffer.flip();

        Volume v = new Volume();
        v.setColormap( "jet" );
        v.readFromBuffer( image.getName(), byteBuffer, dimensions[0], dimensions[1], dimensions[2], voxelDimensions[0],
                          voxelDimensions[1], voxelDimensions[2], nType, bytesPerVoxel );

        getScene().addChild( v );

        v.setTrangemin( minVal );
        v.setTrangemax( maxVal );

        return v;
    }

}
