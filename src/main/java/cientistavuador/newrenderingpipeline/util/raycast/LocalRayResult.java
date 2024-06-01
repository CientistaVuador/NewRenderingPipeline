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
package cientistavuador.newrenderingpipeline.util.raycast;

import cientistavuador.newrenderingpipeline.util.RasterUtils;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class LocalRayResult {
    
    private final BVH originBVH;
    private final Vector3f localOrigin = new Vector3f();
    private final Vector3f localDirection = new Vector3f();
    private final Vector3f localHitPosition = new Vector3f();
    private final Vector3f localTriangleNormal = new Vector3f();
    private final float localDistance;
    private final int triangle;
    private final boolean frontFace;
    
    public LocalRayResult(BVH originBVH, Vector3fc localOrigin, Vector3fc localDirection, Vector3fc localHitPosition, Vector3fc localNormal, int triangle, boolean frontFace) {
        this.originBVH = originBVH;
        this.localOrigin.set(localOrigin);
        this.localDirection.set(localDirection);
        this.localHitPosition.set(localHitPosition);
        this.localTriangleNormal.set(localNormal);
        this.triangle = triangle;
        this.frontFace = frontFace;
        this.localDistance = this.localOrigin.distance(this.localHitPosition);
    }
    
    public BVH getOriginBVH() {
        return originBVH;
    }

    public Vector3fc getLocalOrigin() {
        return localOrigin;
    }

    public Vector3fc getLocalDirection() {
        return localDirection;
    }

    public Vector3fc getLocalHitPosition() {
        return localHitPosition;
    }

    public Vector3f getLocalTriangleNormal() {
        return localTriangleNormal;
    }
    
    public float getLocalDistance() {
        return localDistance;
    }
    
    public int triangle() {
        return this.triangle;
    }
    
    public boolean frontFace() {
        return frontFace;
    }
    
    public float lerp(Vector3fc weights, int componentOffset) {
        int[] indices = this.originBVH.getIndices();
        
        int v0 = indices[(this.triangle() * 3) + 0] * this.originBVH.getVertexSize();
        int v1 = indices[(this.triangle() * 3) + 1] * this.originBVH.getVertexSize();
        int v2 = indices[(this.triangle() * 3) + 2] * this.originBVH.getVertexSize();
        
        float[] vertices = this.originBVH.getVertices();
        
        float a = vertices[v0 + componentOffset];
        float b = vertices[v1 + componentOffset];
        float c = vertices[v2 + componentOffset];
        
        return (a * weights.x()) + (b * weights.y()) + (c * weights.z());
    }
    
    public void weights(Vector3f weights) {
        int[] indices = this.originBVH.getIndices();
        float[] vertices = this.originBVH.getVertices();
        
        int v0 = (indices[(this.triangle() * 3) + 0] * this.originBVH.getVertexSize()) + this.originBVH.getXYZOffset();
        int v1 = (indices[(this.triangle() * 3) + 1] * this.originBVH.getVertexSize()) + this.originBVH.getXYZOffset();
        int v2 = (indices[(this.triangle() * 3) + 2] * this.originBVH.getVertexSize()) + this.originBVH.getXYZOffset();
        
        float v0x = vertices[v0 + 0];
        float v0y = vertices[v0 + 1];
        float v0z = vertices[v0 + 2];
        
        float v1x = vertices[v1 + 0];
        float v1y = vertices[v1 + 1];
        float v1z = vertices[v1 + 2];
        
        float v2x = vertices[v2 + 0];
        float v2y = vertices[v2 + 1];
        float v2z = vertices[v2 + 2];
        
        Vector3fc localHitpoint = getLocalHitPosition();
        
        RasterUtils.barycentricWeights(
                localHitpoint.x(), localHitpoint.y(), localHitpoint.z(),
                v0x, v0y, v0z,
                v1x, v1y, v1z,
                v2x, v2y, v2z,
                weights
        );
    }

}
