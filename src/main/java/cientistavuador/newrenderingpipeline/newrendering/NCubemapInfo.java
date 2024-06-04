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
public class NCubemapInfo {
    
    private final Vector3d cubemapPosition = new Vector3d(0.0, 0.0, 0.0);
    private final boolean parallaxCorrected;
    private final Vector3d parallaxPosition = new Vector3d(0.0, 0.0, 0.0);
    private final Quaternionf parallaxRotation = new Quaternionf(0f, 0f, 0f, 1f);
    private final Vector3f parallaxHalfExtents = new Vector3f(0f, 0f, 0f);
    
    public NCubemapInfo(
            Vector3dc cubemapPosition,
            boolean parallaxCorrected,
            Vector3dc parallaxPosition,
            Quaternionfc parallaxRotation,
            Vector3fc parallaxHalfExtents
    ) {
        if (cubemapPosition != null) {
            this.cubemapPosition.set(cubemapPosition);
        }
        this.parallaxCorrected = parallaxCorrected;
        if (parallaxPosition != null) {
            this.parallaxPosition.set(parallaxPosition);
        }
        if (parallaxRotation != null) {
            this.parallaxRotation.set(parallaxRotation);
        }
        if (parallaxHalfExtents != null) {
            this.parallaxHalfExtents.set(parallaxHalfExtents);
        }
    }
    
    public NCubemapInfo() {
        this(null, false, null, null, null);
    }
    
    public Vector3d getCubemapPosition() {
        return cubemapPosition;
    }

    public boolean isParallaxCorrected() {
        return parallaxCorrected;
    }

    public Vector3d getParallaxPosition() {
        return parallaxPosition;
    }

    public Quaternionf getParallaxRotation() {
        return parallaxRotation;
    }

    public Vector3f getParallaxHalfExtents() {
        return parallaxHalfExtents;
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
        float rx = (float) (this.parallaxPosition.x() - cx);
        float ry = (float) (this.parallaxPosition.y() - cy);
        float rz = (float) (this.parallaxPosition.z() - cz);
        outWorldToLocal
                .identity()
                .translate(rx, ry, rz)
                .rotate(this.parallaxRotation)
                .scale(this.parallaxHalfExtents)
                .invert()
                ;
        
        outCubemapPosition.set(
                this.cubemapPosition.x() - cx,
                this.cubemapPosition.y() - cy,
                this.cubemapPosition.z() - cz
        );
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.cubemapPosition);
        hash = 67 * hash + (this.parallaxCorrected ? 1 : 0);
        hash = 67 * hash + Objects.hashCode(this.parallaxPosition);
        hash = 67 * hash + Objects.hashCode(this.parallaxRotation);
        hash = 67 * hash + Objects.hashCode(this.parallaxHalfExtents);
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
        final NCubemapInfo other = (NCubemapInfo) obj;
        if (this.parallaxCorrected != other.parallaxCorrected) {
            return false;
        }
        if (!Objects.equals(this.cubemapPosition, other.cubemapPosition)) {
            return false;
        }
        if (!Objects.equals(this.parallaxPosition, other.parallaxPosition)) {
            return false;
        }
        if (!Objects.equals(this.parallaxRotation, other.parallaxRotation)) {
            return false;
        }
        return Objects.equals(this.parallaxHalfExtents, other.parallaxHalfExtents);
    }
    
    
    
}
