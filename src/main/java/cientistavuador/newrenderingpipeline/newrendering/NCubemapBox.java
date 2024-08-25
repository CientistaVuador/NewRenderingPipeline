/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.newrenderingpipeline.newrendering;

import java.util.Objects;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class NCubemapBox {
    
    public static final NCubemapBox NULL_CUBEMAP_BOX = new NCubemapBox();
    
    private final Vector3d cubemapPosition = new Vector3d();
    private final Vector3d position = new Vector3d();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f halfExtents = new Vector3f();
    
    private final Matrix4d localToWorld = new Matrix4d();
    private final Vector3d min = new Vector3d();
    private final Vector3d max = new Vector3d();
    
    public NCubemapBox(
            double cubemapX, double cubemapY, double cubemapZ,
            double x, double y, double z,
            float quaternionX, float quaternionY, float quaternionZ, float quaternionW,
            float halfExtentX, float halfExtentY, float halfExtentZ
    ) {
        this.cubemapPosition.set(cubemapX, cubemapY, cubemapZ);
        this.position.set(x, y, z);
        this.rotation.set(quaternionX, quaternionY, quaternionZ, quaternionW);
        this.halfExtents.set(halfExtentX, halfExtentY, halfExtentZ);
        
        this.localToWorld
                .identity()
                .translate(this.position)
                .rotate(this.rotation)
                .scale(this.halfExtents.x(), this.halfExtents.y(), this.halfExtents.z())
                ;
        
        this.min.set(-1.0);
        this.max.set(1.0);
        this.localToWorld.transformAab(this.min, this.max, this.min, this.max);
    }
    
    public NCubemapBox(
            double pX, double pY, double pZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        this(
                pX, pY, pZ,
                (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5,
                0f, 0f, 0f, 1f,
                (float) ((maxX - minX) * 0.5f), (float) ((maxY - minY) * 0.5f), (float) ((maxZ - minZ) * 0.5f)
        );
    }
    
    public NCubemapBox(
            Vector3dc cubemapPosition,
            Vector3dc position,
            Quaternionfc rotation,
            Vector3fc halfExtents
    ) {
        this(
                cubemapPosition.x(), cubemapPosition.y(), cubemapPosition.z(),
                position.x(), position.y(), position.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                halfExtents.x(), halfExtents.y(), halfExtents.z()
        );
    }
    
    public NCubemapBox() {
        this(
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 1,
                0, 0, 0
        );
    }
    
    public Vector3d getCubemapPosition() {
        return cubemapPosition;
    }
    
    public Vector3d getPosition() {
        return position;
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public Vector3f getHalfExtents() {
        return halfExtents;
    }

    public Matrix4dc getLocalToWorld() {
        return localToWorld;
    }

    public Vector3dc getMin() {
        return min;
    }

    public Vector3dc getMax() {
        return max;
    }
    
    public void calculateRelative(Vector3dc cameraPosition, Matrix4f outWorldToLocal, Vector3f outCubemapPosition) {
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        if (cameraPosition != null) {
            cx = cameraPosition.x();
            cy = cameraPosition.y();
            cz = cameraPosition.z();
        }
        float rx = (float) (this.position.x() - cx);
        float ry = (float) (this.position.y() - cy);
        float rz = (float) (this.position.z() - cz);
        outWorldToLocal
                .identity()
                .translate(rx, ry, rz)
                .rotate(this.rotation)
                .scale(this.halfExtents)
                .invert()
                ;

        if (outCubemapPosition != null) {
            outCubemapPosition.set(
                    this.cubemapPosition.x() - cx,
                    this.cubemapPosition.y() - cy,
                    this.cubemapPosition.z() - cz
            );
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.cubemapPosition);
        hash = 67 * hash + Objects.hashCode(this.position);
        hash = 67 * hash + Objects.hashCode(this.rotation);
        hash = 67 * hash + Objects.hashCode(this.halfExtents);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NCubemapBox other = (NCubemapBox) obj;
        if (!Objects.equals(this.cubemapPosition, other.cubemapPosition)) {
            return false;
        }
        if (!Objects.equals(this.position, other.position)) {
            return false;
        }
        if (!Objects.equals(this.rotation, other.rotation)) {
            return false;
        }
        return Objects.equals(this.halfExtents, other.halfExtents);
    }

}
