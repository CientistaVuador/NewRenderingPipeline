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

import cientistavuador.newrenderingpipeline.util.Aab;
import cientistavuador.newrenderingpipeline.util.MeshUtils;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Intersectionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class BVH implements Aab {

    private static final float AABB_OFFSET = 0.001f;
    
    public static BVH create(float[] vertices, int[] indices, int vertexSize, int xyzOffset) {
        return create(null, vertices, indices, vertexSize, xyzOffset);
    }

    public static BVH create(Object userObject, float[] vertices, int[] indices, int vertexSize, int xyzOffset) {
        if (vertices.length == 0) {
            return new BVH(userObject, vertices, indices, vertexSize, xyzOffset, new Vector3f(), new Vector3f());
        }
        
        final int numberOfTriangles = indices.length / 3;

        BVH[] currentArray = new BVH[numberOfTriangles];

        for (int i = 0; i < numberOfTriangles; i++) {
            int v0 = (indices[(i * 3) + 0] * vertexSize) + xyzOffset;
            int v1 = (indices[(i * 3) + 1] * vertexSize) + xyzOffset;
            int v2 = (indices[(i * 3) + 2] * vertexSize) + xyzOffset;

            float v0x = vertices[v0 + 0];
            float v0y = vertices[v0 + 1];
            float v0z = vertices[v0 + 2];

            float v1x = vertices[v1 + 0];
            float v1y = vertices[v1 + 1];
            float v1z = vertices[v1 + 2];

            float v2x = vertices[v2 + 0];
            float v2y = vertices[v2 + 1];
            float v2z = vertices[v2 + 2];

            float minX = Math.min(v0x, Math.min(v1x, v2x)) - AABB_OFFSET;
            float minY = Math.min(v0y, Math.min(v1y, v2y)) - AABB_OFFSET;
            float minZ = Math.min(v0z, Math.min(v1z, v2z)) - AABB_OFFSET;

            float maxX = Math.max(v0x, Math.max(v1x, v2x)) + AABB_OFFSET;
            float maxY = Math.max(v0y, Math.max(v1y, v2y)) + AABB_OFFSET;
            float maxZ = Math.max(v0z, Math.max(v1z, v2z)) + AABB_OFFSET;
            
            BVH e = new BVH(
                    userObject,
                    vertices,
                    indices,
                    vertexSize,
                    xyzOffset,
                    minX, minY, minZ,
                    maxX, maxY, maxZ
            );
            e.amountOfTriangles = 1;
            e.triangles = new int[]{i};
            currentArray[i] = e;
        }
        
        BVH[] nextArray = new BVH[numberOfTriangles];
        int currentLength = nextArray.length;
        int nextIndex = 0;

        while (currentLength != 1) {
            for (int i = 0; i < currentLength; i++) {
                BVH current = currentArray[i];

                if (current == null) {
                    continue;
                }

                float minX = current.getMin().x();
                float minY = current.getMin().y();
                float minZ = current.getMin().z();

                float maxX = current.getMax().x();
                float maxY = current.getMax().y();
                float maxZ = current.getMax().z();

                float centerX = (minX * 0.5f) + (maxX * 0.5f);
                float centerY = (minY * 0.5f) + (maxY * 0.5f);
                float centerZ = (minZ * 0.5f) + (maxZ * 0.5f);

                BVH closest = null;
                float closestDistanceSquared = Float.POSITIVE_INFINITY;
                int closestIndex = -1;

                float closestMinX = 0f;
                float closestMinY = 0f;
                float closestMinZ = 0f;

                float closestMaxX = 0f;
                float closestMaxY = 0f;
                float closestMaxZ = 0f;

                for (int j = (i + 1); j < currentLength; j++) {
                    BVH other = currentArray[j];

                    if (other == null) {
                        continue;
                    }

                    float otherMinX = other.getMin().x();
                    float otherMinY = other.getMin().y();
                    float otherMinZ = other.getMin().z();

                    float otherMaxX = other.getMax().x();
                    float otherMaxY = other.getMax().y();
                    float otherMaxZ = other.getMax().z();

                    float otherCenterX = (otherMinX * 0.5f) + (otherMaxX * 0.5f);
                    float otherCenterY = (otherMinY * 0.5f) + (otherMaxY * 0.5f);
                    float otherCenterZ = (otherMinZ * 0.5f) + (otherMaxZ * 0.5f);

                    float dX = centerX - otherCenterX;
                    float dY = centerY - otherCenterY;
                    float dZ = centerZ - otherCenterZ;

                    float distanceSquared = (dX * dX) + (dY * dY) + (dZ * dZ);

                    if (distanceSquared < closestDistanceSquared) {
                        closest = other;
                        closestDistanceSquared = distanceSquared;
                        closestIndex = j;

                        closestMinX = otherMinX;
                        closestMinY = otherMinY;
                        closestMinZ = otherMinZ;

                        closestMaxX = otherMaxX;
                        closestMaxY = otherMaxY;
                        closestMaxZ = otherMaxZ;
                    }
                }

                currentArray[i] = null;

                if (closest == null) {
                    nextArray[nextIndex++] = current;
                    continue;
                }
                currentArray[closestIndex] = null;

                float newMinX = Math.min(Math.min(minX, closestMinX), Math.min(maxX, closestMaxX));
                float newMinY = Math.min(Math.min(minY, closestMinY), Math.min(maxY, closestMaxY));
                float newMinZ = Math.min(Math.min(minZ, closestMinZ), Math.min(maxZ, closestMaxZ));

                float newMaxX = Math.max(Math.max(minX, closestMinX), Math.max(maxX, closestMaxX));
                float newMaxY = Math.max(Math.max(minY, closestMinY), Math.max(maxY, closestMaxY));
                float newMaxZ = Math.max(Math.max(minZ, closestMinZ), Math.max(maxZ, closestMaxZ));

                BVH merge = new BVH(
                        userObject,
                        vertices,
                        indices,
                        vertexSize,
                        xyzOffset,
                        newMinX, newMinY, newMinZ,
                        newMaxX, newMaxY, newMaxZ
                );
                merge.amountOfTriangles = current.amountOfTriangles + closest.amountOfTriangles;
                merge.left = current;
                merge.right = closest;
                current.parent = merge;
                closest.parent = merge;

                nextArray[nextIndex++] = merge;
            }

            currentLength = nextIndex;
            nextIndex = 0;

            BVH[] currentStore = currentArray;
            currentArray = nextArray;
            nextArray = currentStore;
        }

        return currentArray[0];
    }

    private final Object userObject;

    private final float[] vertices;
    private final int[] indices;
    private final int vertexSize;
    private final int xyzOffset;

    private final Vector3f min = new Vector3f();
    private final Vector3f max = new Vector3f();

    protected BVH parent;
    protected BVH left;
    protected BVH right;
    protected int amountOfTriangles = 0;
    protected int[] triangles = null;

    protected BVH(Object userObject, float[] vertices, int[] indices, int vertexSize, int xyzOffset, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.userObject = userObject;
        this.vertices = vertices;
        this.indices = indices;
        this.vertexSize = vertexSize;
        this.xyzOffset = xyzOffset;
        this.min.set(minX, minY, minZ);
        this.max.set(maxX, maxY, maxZ);
    }

    private BVH(Object userObject, float[] vertices, int[] indices, int vertexSize, int xyzOffset, Vector3fc min, Vector3fc max) {
        this(userObject, vertices, indices, vertexSize, xyzOffset, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    public Object getUserObject() {
        return userObject;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public int getVertexSize() {
        return vertexSize;
    }

    public int getXYZOffset() {
        return xyzOffset;
    }

    public BVH getParent() {
        return parent;
    }

    public BVH getLeft() {
        return left;
    }

    public BVH getRight() {
        return right;
    }

    public int getAmountOfTriangles() {
        return amountOfTriangles;
    }

    public int[] getTriangles() {
        return triangles;
    }

    public Vector3fc getMin() {
        return min;
    }

    public Vector3fc getMax() {
        return max;
    }

    @Override
    public void getMin(Vector3f min) {
        min.set(this.min);
    }

    @Override
    public void getMax(Vector3f max) {
        max.set(this.max);
    }

    private boolean fastTestRay(
            Vector3f a, Vector3f b, Vector3f c,
            BVH e,
            Vector3fc localOrigin, Vector3fc localDirection,
            float maxLength, Vector3f rayMin, Vector3f rayMax
    ) {
        if (rayMin != null && rayMax != null) {
            if (!IntersectionUtils.testAabAab(rayMin, rayMax, e.getMin(), e.getMax())) {
                return false;
            }
        }
        if (IntersectionUtils.testRayAab(localOrigin, localDirection, e.getMin(), e.getMax())) {
            if (e.getLeft() == null && e.getRight() == null) {
                int[] nodeTriangles = e.getTriangles();
                for (int i = 0; i < nodeTriangles.length; i++) {
                    int triangle = nodeTriangles[i];

                    int v0xyz = (this.indices[(triangle * 3) + 0] * this.vertexSize) + this.xyzOffset;
                    int v1xyz = (this.indices[(triangle * 3) + 1] * this.vertexSize) + this.xyzOffset;
                    int v2xyz = (this.indices[(triangle * 3) + 2] * this.vertexSize) + this.xyzOffset;

                    a.set(
                            this.vertices[v0xyz + 0],
                            this.vertices[v0xyz + 1],
                            this.vertices[v0xyz + 2]
                    );
                    b.set(
                            this.vertices[v1xyz + 0],
                            this.vertices[v1xyz + 1],
                            this.vertices[v1xyz + 2]
                    );
                    c.set(
                            this.vertices[v2xyz + 0],
                            this.vertices[v2xyz + 1],
                            this.vertices[v2xyz + 2]
                    );

                    float hit = IntersectionUtils.intersectRayTriangle(localOrigin, localDirection, a, b, c);
                    if (hit >= 0f && (!Float.isFinite(maxLength) || hit <= maxLength)) {
                        return true;
                    }
                }
            }

            if (e.getLeft() != null) {
                if (fastTestRay(a, b, c, e.getLeft(), localOrigin, localDirection, maxLength, rayMin, rayMax)) {
                    return true;
                }
            }
            if (e.getRight() != null) {
                if (fastTestRay(a, b, c, e.getRight(), localOrigin, localDirection, maxLength, rayMin, rayMax)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean fastTestRay(Vector3fc localOrigin, Vector3fc localDirection, float maxLength) {
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Vector3f rayMin = null;
        Vector3f rayMax = null;

        if (Float.isFinite(maxLength)) {
            rayMin = new Vector3f(localOrigin);
            rayMax = new Vector3f(localDirection).mul(maxLength).add(localOrigin);

            float minX = Math.min(rayMin.x(), rayMax.x());
            float minY = Math.min(rayMin.y(), rayMax.y());
            float minZ = Math.min(rayMin.z(), rayMax.z());

            float maxX = Math.max(rayMin.x(), rayMax.x());
            float maxY = Math.max(rayMin.y(), rayMax.y());
            float maxZ = Math.max(rayMin.z(), rayMax.z());

            rayMin.set(minX, minY, minZ);
            rayMax.set(maxX, maxY, maxZ);
        }
        
        return fastTestRay(a, b, c, this, localOrigin, localDirection, maxLength, rayMin, rayMax);
    }

    private void testRay(
            Vector3fc localOrigin, Vector3fc localDirection,
            List<LocalRayResult> resultsOutput, BVH bvh, BitSet tested,
            Vector3f normal, Vector3f hitposition, Vector3f a, Vector3f b, Vector3f c
    ) {
        if (IntersectionUtils.testRayAab(localOrigin, localDirection, bvh.getMin(), bvh.getMax())) {
            if (bvh.getLeft() == null && bvh.getRight() == null) {
                int[] nodeTriangles = bvh.getTriangles();
                for (int i = 0; i < nodeTriangles.length; i++) {
                    int triangle = nodeTriangles[i];

                    if (tested.get(triangle)) {
                        continue;
                    }
                    tested.set(triangle);

                    int i0 = this.indices[(triangle * 3) + 0];
                    int i1 = this.indices[(triangle * 3) + 1];
                    int i2 = this.indices[(triangle * 3) + 2];

                    int v0xyz = (i0 * this.vertexSize) + this.xyzOffset;
                    int v1xyz = (i1 * this.vertexSize) + this.xyzOffset;
                    int v2xyz = (i2 * this.vertexSize) + this.xyzOffset;

                    a.set(
                            this.vertices[v0xyz + 0],
                            this.vertices[v0xyz + 1],
                            this.vertices[v0xyz + 2]
                    );
                    b.set(
                            this.vertices[v1xyz + 0],
                            this.vertices[v1xyz + 1],
                            this.vertices[v1xyz + 2]
                    );
                    c.set(
                            this.vertices[v2xyz + 0],
                            this.vertices[v2xyz + 1],
                            this.vertices[v2xyz + 2]
                    );

                    float hit = IntersectionUtils.intersectRayTriangle(localOrigin, localDirection, a, b, c);
                    if (hit >= 0f) {
                        MeshUtils.calculateTriangleNormal(
                                this.vertices,
                                this.vertexSize,
                                this.xyzOffset,
                                i0,
                                i1,
                                i2,
                                normal
                        );
                        boolean frontFace = normal.dot(localDirection) < 0f;

                        hitposition.set(localDirection).mul(hit).add(localOrigin);

                        resultsOutput.add(new LocalRayResult(this, localOrigin, localDirection, hitposition, normal, triangle, frontFace));
                    }
                }
            }

            if (bvh.getLeft() != null) {
                testRay(localOrigin, localDirection, resultsOutput, bvh.getLeft(), tested, normal, hitposition, a, b, c);
            }
            if (bvh.getRight() != null) {
                testRay(localOrigin, localDirection, resultsOutput, bvh.getRight(), tested, normal, hitposition, a, b, c);
            }
        }
    }

    public List<LocalRayResult> testRay(Vector3fc localOrigin, Vector3fc localDirection) {
        List<LocalRayResult> resultsOutput = new ArrayList<>();

        Vector3f normal = new Vector3f();
        Vector3f hitposition = new Vector3f();

        BitSet tested = new BitSet(this.indices.length / 3);

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        testRay(localOrigin, localDirection, resultsOutput, this, tested, normal, hitposition, a, b, c);

        return resultsOutput;
    }

    public List<LocalRayResult> testRaySorted(Vector3fc localOrigin, Vector3fc localDirection, boolean frontFaceOnly) {
        List<LocalRayResult> results = testRay(localOrigin, localDirection);
        if (frontFaceOnly) {
            List<LocalRayResult> filtered = new ArrayList<>();
            for (LocalRayResult e : results) {
                if (e.frontFace()) {
                    filtered.add(e);
                }
            }
            results = filtered;
        }
        results.sort((o1, o2) -> Float.compare(o1.getLocalDistance(), o2.getLocalDistance()));
        return results;
    }

    private boolean fastTestSphere(BVH bvh, float x, float y, float z, float radius) {
        if (bvh == null) {
            return false;
        }

        if (!Intersectionf.testAabSphere(
                bvh.getMin().x(), bvh.getMin().y(), bvh.getMin().z(),
                bvh.getMax().x(), bvh.getMax().y(), bvh.getMax().z(),
                x, y, z, radius * radius
        )) {
            return false;
        }

        if (bvh.getLeft() == null && bvh.getRight() == null) {
            Vector3f resultVector = new Vector3f();
            for (int triangle : bvh.getTriangles()) {
                int i0 = this.indices[(triangle * 3) + 0];
                int i1 = this.indices[(triangle * 3) + 1];
                int i2 = this.indices[(triangle * 3) + 2];

                int v0xyz = (i0 * this.vertexSize) + this.xyzOffset;
                int v1xyz = (i1 * this.vertexSize) + this.xyzOffset;
                int v2xyz = (i2 * this.vertexSize) + this.xyzOffset;

                float v0x = this.vertices[v0xyz + 0];
                float v0y = this.vertices[v0xyz + 1];
                float v0z = this.vertices[v0xyz + 2];

                float v1x = this.vertices[v1xyz + 0];
                float v1y = this.vertices[v1xyz + 1];
                float v1z = this.vertices[v1xyz + 2];

                float v2x = this.vertices[v2xyz + 0];
                float v2y = this.vertices[v2xyz + 1];
                float v2z = this.vertices[v2xyz + 2];

                int result = Intersectionf.intersectSphereTriangle(
                        x, y, z, radius,
                        v0x, v0y, v0z,
                        v1x, v1y, v1z,
                        v2x, v2y, v2z,
                        resultVector
                );

                if (result != 0) {
                    return true;
                }
            }
            return false;
        }

        return fastTestSphere(bvh.getLeft(), x, y, z, radius) || fastTestSphere(bvh.getRight(), x, y, z, radius);
    }

    public boolean fastTestSphere(float x, float y, float z, float radius) {
        return fastTestSphere(this, x, y, z, radius);
    }
    
    private void testSphere(Set<Integer> triangles, BVH bvh, float x, float y, float z, float radius) {
        if (bvh == null) {
            return;
        }
        
        if (!Intersectionf.testAabSphere(
                bvh.getMin().x(), bvh.getMin().y(), bvh.getMin().z(),
                bvh.getMax().x(), bvh.getMax().y(), bvh.getMax().z(),
                x, y, z, radius * radius
        )) {
            return;
        }

        if (bvh.getLeft() == null && bvh.getRight() == null) {
            Vector3f resultVector = new Vector3f();
            for (int triangle : bvh.getTriangles()) {
                int i0 = this.indices[(triangle * 3) + 0];
                int i1 = this.indices[(triangle * 3) + 1];
                int i2 = this.indices[(triangle * 3) + 2];

                int v0xyz = (i0 * this.vertexSize) + this.xyzOffset;
                int v1xyz = (i1 * this.vertexSize) + this.xyzOffset;
                int v2xyz = (i2 * this.vertexSize) + this.xyzOffset;

                float v0x = this.vertices[v0xyz + 0];
                float v0y = this.vertices[v0xyz + 1];
                float v0z = this.vertices[v0xyz + 2];

                float v1x = this.vertices[v1xyz + 0];
                float v1y = this.vertices[v1xyz + 1];
                float v1z = this.vertices[v1xyz + 2];

                float v2x = this.vertices[v2xyz + 0];
                float v2y = this.vertices[v2xyz + 1];
                float v2z = this.vertices[v2xyz + 2];

                int result = Intersectionf.intersectSphereTriangle(
                        x, y, z, radius,
                        v0x, v0y, v0z,
                        v1x, v1y, v1z,
                        v2x, v2y, v2z,
                        resultVector
                );

                if (result != 0 && !triangles.contains(triangle)) {
                    triangles.add(triangle);
                }
            }
        }

        testSphere(triangles, bvh.getLeft(), x, y, z, radius);
        testSphere(triangles, bvh.getRight(), x, y, z, radius);
    }
    
    public Set<Integer> testSphere(float x, float y, float z, float radius) {
        Set<Integer> set = new HashSet<>();
        testSphere(set, this, x, y, z, radius);
        return set;
    }

}
