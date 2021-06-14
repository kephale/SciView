package sc.iview

import junit.framework.Assert.assertNotNull
import org.junit.Test
import sc.iview.node.Line3D

class Line3DTests {

    //tests if a bounding box becomes null when Line does not implement HasGeometry
    @Test
    fun testBoundingBox() {
        val line = Line3D()
        assertNotNull(line.generateBoundingBox())
    }
}
