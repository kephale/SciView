package sc.iview;

import cleargl.GLVector;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import org.joml.Vector3f;

/**
 * Created by kharrington on 1/18/18.
 */
public class ClearGLDVec3 implements DVec3 {

    private GLVector glVector;

    public ClearGLDVec3 set( GLVector v ) {
        glVector = v;
        return this;
    }

    public  GLVector get() {
        return glVector;
    }

    public ClearGLDVec3( final float x, final float y, final float z ) {
        glVector = new GLVector(x,y,z);
    }

    public ClearGLDVec3(Vector3f source ) {
        glVector = new GLVector(source.get(0),source.get(1),source.get(2));
    }

    @Override
    public float getFloatPosition(int d) {
        return glVector.get(d);
    }

    @Override
    public double getDoublePosition(int d) {
        return glVector.get(d);
    }

    @Override
    public void move(RealLocalizable localizable) {
        glVector = glVector.plus( new GLVector(localizable.getFloatPosition(0),
                                                localizable.getFloatPosition(1),
                                                localizable.getFloatPosition(2)) );
    }

    @Override
    public void setPosition(float position, int d) {
        float x, y, z;
        x = glVector.get(0); y = glVector.get(1); z = glVector.get(2);
        if( d == 0 ) x = position;
        else if( d == 1 ) y = position;
        else if( d == 2 ) z = position;
        glVector = new GLVector(x, y, z);
    }
}
