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
package cientistavuador.newrenderingpipeline.util;

import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.resources.mesh.MeshData;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapUVs;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.GImpactCollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.bullet.util.DebugShapeFactory;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import vhacd4.Vhacd4;
import vhacd4.Vhacd4Hull;
import vhacd4.Vhacd4Parameters;

/**
 *
 * @author Cien
 */
public class MeshUtils {

    public static void generateTangent(float[] vertices, int vertexSize, int xyzOffset, int uvOffset, int outTangentXYZOffset) {
        if (vertices.length % vertexSize != 0) {
            throw new IllegalArgumentException("Wrong size.");
        }
        if (vertices.length % (3 * vertexSize) != 0) {
            throw new IllegalArgumentException("Not a triangulated mesh.");
        }
        for (int v = 0; v < vertices.length; v += (vertexSize * 3)) {
            int v0 = v;
            int v1 = v + vertexSize;
            int v2 = v + (vertexSize * 2);

            float v0x = vertices[v0 + xyzOffset + 0];
            float v0y = vertices[v0 + xyzOffset + 1];
            float v0z = vertices[v0 + xyzOffset + 2];
            float v0u = vertices[v0 + uvOffset + 0];
            float v0v = vertices[v0 + uvOffset + 1];

            float v1x = vertices[v1 + xyzOffset + 0];
            float v1y = vertices[v1 + xyzOffset + 1];
            float v1z = vertices[v1 + xyzOffset + 2];
            float v1u = vertices[v1 + uvOffset + 0];
            float v1v = vertices[v1 + uvOffset + 1];

            float v2x = vertices[v2 + xyzOffset + 0];
            float v2y = vertices[v2 + xyzOffset + 1];
            float v2z = vertices[v2 + xyzOffset + 2];
            float v2u = vertices[v2 + uvOffset + 0];
            float v2v = vertices[v2 + uvOffset + 1];

            float edge1x = v1x - v0x;
            float edge1y = v1y - v0y;
            float edge1z = v1z - v0z;

            float edge2x = v2x - v0x;
            float edge2y = v2y - v0y;
            float edge2z = v2z - v0z;

            float deltaUV1u = v1u - v0u;
            float deltaUV1v = v1v - v0v;

            float deltaUV2u = v2u - v0u;
            float deltaUV2v = v2v - v0v;

            float f = 1f / ((deltaUV1u * deltaUV2v) - (deltaUV2u * deltaUV1v));

            float tangentX = f * ((deltaUV2v * edge1x) - (deltaUV1v * edge2x));
            float tangentY = f * ((deltaUV2v * edge1y) - (deltaUV1v * edge2y));
            float tangentZ = f * ((deltaUV2v * edge1z) - (deltaUV1v * edge2z));

            float length = (float) (1.0 / Math.sqrt((tangentX * tangentX) + (tangentY * tangentY) + (tangentZ * tangentZ)));
            tangentX *= length;
            tangentY *= length;
            tangentZ *= length;

            vertices[v0 + outTangentXYZOffset + 0] = tangentX;
            vertices[v0 + outTangentXYZOffset + 1] = tangentY;
            vertices[v0 + outTangentXYZOffset + 2] = tangentZ;

            vertices[v1 + outTangentXYZOffset + 0] = tangentX;
            vertices[v1 + outTangentXYZOffset + 1] = tangentY;
            vertices[v1 + outTangentXYZOffset + 2] = tangentZ;

            vertices[v2 + outTangentXYZOffset + 0] = tangentX;
            vertices[v2 + outTangentXYZOffset + 1] = tangentY;
            vertices[v2 + outTangentXYZOffset + 2] = tangentZ;
        }
    }

    private static class Vertex {

        final float[] vertices;
        final int vertexSize;
        final int vertexIndex;
        final int vertexCount;

        public Vertex(float[] vertices, int vertexSize, int vertexIndex, int vertexCount) {
            this.vertices = vertices;
            this.vertexSize = vertexSize;
            this.vertexIndex = vertexIndex;
            this.vertexCount = vertexCount;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = 0; i < this.vertexSize; i++) {
                hash = 27 * hash + Float.floatToRawIntBits(this.vertices[this.vertexIndex + i]);
            }
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
            final Vertex other = (Vertex) obj;
            int indexA = this.vertexIndex;
            int indexB = other.vertexIndex;
            for (int i = 0; i < this.vertexSize; i++) {
                if (Float.floatToRawIntBits(this.vertices[indexA + i]) != Float.floatToRawIntBits(this.vertices[indexB + i])) {
                    return false;
                }
            }
            return true;
        }
    }

    public static Pair<float[], int[]> generateIndices(float[] vertices, int vertexSize) {
        Map<Vertex, Vertex> verticesMap = new HashMap<>();

        float[] verticesIndexed = new float[64];
        int verticesIndexedIndex = 0;

        int[] indices = new int[64];
        int indicesIndex = 0;

        int vertexCount = 0;

        for (int v = 0; v < vertices.length; v += vertexSize) {
            Vertex current = new Vertex(vertices, vertexSize, v, vertexCount);
            Vertex other = verticesMap.get(current);

            if (other != null) {
                if (indicesIndex >= indices.length) {
                    indices = Arrays.copyOf(indices, indices.length * 2);
                }
                indices[indicesIndex] = other.vertexCount;
                indicesIndex++;
                continue;
            }

            verticesMap.put(current, current);

            if ((verticesIndexedIndex + vertexSize) > verticesIndexed.length) {
                verticesIndexed = Arrays.copyOf(verticesIndexed, verticesIndexed.length * 2);
            }
            System.arraycopy(vertices, v, verticesIndexed, verticesIndexedIndex, vertexSize);
            verticesIndexedIndex += vertexSize;

            if (indicesIndex >= indices.length) {
                indices = Arrays.copyOf(indices, indices.length * 2);
            }
            indices[indicesIndex] = vertexCount;
            indicesIndex++;

            vertexCount++;
        }

        return new Pair<>(
                Arrays.copyOf(verticesIndexed, verticesIndexedIndex),
                Arrays.copyOf(indices, indicesIndex)
        );
    }

    public static Pair<float[], int[]> unindex(float[] vertices, int[] indices, int vertexSize) {
        float[] unindexedVertices = new float[indices.length * vertexSize];
        int[] unindexedIndices = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            System.arraycopy(vertices, indices[i] * vertexSize, unindexedVertices, i * vertexSize, vertexSize);
            unindexedIndices[i] = i;
        }
        return new Pair<>(unindexedVertices, unindexedIndices);
    }

    public static LightmapUVs.GeneratorOutput generateLightmapUVs(float[] vertices, int vertexSize, int xyzOffset, float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ) {
        return LightmapUVs.generate(vertices, vertexSize, xyzOffset, pixelToWorldRatio, scaleX, scaleY, scaleZ);
    }

    public static void calculateTriangleNormal(
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            Vector3f outNormal
    ) {
        outNormal.set(bx, by, bz).sub(ax, ay, az).normalize();

        float baX = outNormal.x();
        float baY = outNormal.y();
        float baZ = outNormal.z();

        outNormal.set(cx, cy, cz).sub(ax, ay, az).normalize();

        float caX = outNormal.x();
        float caY = outNormal.y();
        float caZ = outNormal.z();

        outNormal.set(baX, baY, baZ).cross(caX, caY, caZ).normalize();
    }

    public static void calculateTriangleNormal(float[] vertices, int vertexSize, int xyzOffset, int i0, int i1, int i2, Vector3f outNormal) {
        float ax = vertices[(i0 * vertexSize) + xyzOffset + 0];
        float ay = vertices[(i0 * vertexSize) + xyzOffset + 1];
        float az = vertices[(i0 * vertexSize) + xyzOffset + 2];

        float bx = vertices[(i1 * vertexSize) + xyzOffset + 0];
        float by = vertices[(i1 * vertexSize) + xyzOffset + 1];
        float bz = vertices[(i1 * vertexSize) + xyzOffset + 2];

        float cx = vertices[(i2 * vertexSize) + xyzOffset + 0];
        float cy = vertices[(i2 * vertexSize) + xyzOffset + 1];
        float cz = vertices[(i2 * vertexSize) + xyzOffset + 2];

        calculateTriangleNormal(ax, ay, az, bx, by, bz, cx, cy, cz, outNormal);
    }

    private static float valuesDistanceSquared(float[] values) {
        float totalSum = 0f;
        for (int i = 0; i < values.length; i++) {
            totalSum += (values[i] * values[i]);
        }
        return totalSum;
    }
    
    public static int conservativeMergeByDistance(float[] vertices, int vertexSize, int offset, int size, float distance) {
        float distanceSquared = distance * distance;
        
        int altered = 0;
        boolean[] processed = new boolean[vertices.length / vertexSize];
        
        float[] current = new float[size];
        float[] other = new float[size];
        
        for (int v = 0; v < vertices.length; v += vertexSize) {
            if (processed[v / vertexSize]) {
                continue;
            }
            processed[v / vertexSize] = true;
            
            System.arraycopy(vertices, v + offset, current, 0, size);
            
            for (int vOther = (v + vertexSize); vOther < vertices.length; vOther += vertexSize) {
                if (processed[vOther / vertexSize]) {
                    continue;
                }
                
                System.arraycopy(vertices, vOther + offset, other, 0, size);
                
                for (int i = 0; i < other.length; i++) {
                    other[i] = current[i] - other[i];
                }
                
                float otherDistanceSquared = valuesDistanceSquared(other);
                
                if (otherDistanceSquared == 0f) {
                    processed[vOther / vertexSize] = true;
                    continue;
                }

                if (otherDistanceSquared <= distanceSquared) {
                    System.arraycopy(current, 0, vertices, vOther + offset, size);
                    processed[vOther / vertexSize] = true;
                    altered++;
                }
            }
        }

        return altered;
    }
    
    public static int conservativeMergeByDistanceXYZ(float[] vertices, int vertexSize, int xyzOffset, float distance) {
        return conservativeMergeByDistance(vertices, vertexSize, xyzOffset, 3, distance);
    }
    
    public static void vertexAO(float[] vertices, int vertexSize, int xyzOffset, int outAoOffset, float aoSize, int aoRays, float rayOffset) {
        VertexAO.vertexAO(vertices, vertexSize, xyzOffset, outAoOffset, aoSize, aoRays, rayOffset);
    }
    
    private static Pair<float[], int[]> transformAndReindex(float[][] vertices, int[][] indices, Matrix4fc[] models, int vertexSize, int xyzOffset) {
        float[] collisionVertices = new float[64];
        int collisionVerticesIndex = 0;
        
        Vector3f vertexPosition = new Vector3f();
        Matrix4fc identity = new Matrix4f();
        
        for (int mesh = 0; mesh < vertices.length; mesh++) {
            float[] meshVertices = vertices[mesh];
            int[] meshIndices = indices[mesh];
            Matrix4fc meshModel = models[mesh];
            if (meshModel == null) {
                meshModel = identity;
            }
            
            float[] unindexedVertices = unindex(meshVertices, meshIndices, vertexSize).getA();
            
            for (int v = 0; v < unindexedVertices.length; v += vertexSize) {
                vertexPosition.set(
                        unindexedVertices[v + xyzOffset + 0],
                        unindexedVertices[v + xyzOffset + 1],
                        unindexedVertices[v + xyzOffset + 2]
                );
                meshModel.transformProject(vertexPosition);
                if ((collisionVerticesIndex + 3) > collisionVertices.length) {
                    collisionVertices = Arrays.copyOf(collisionVertices, collisionVertices.length * 2 + 3);
                }
                collisionVertices[collisionVerticesIndex + 0] = vertexPosition.x() * Main.TO_PHYSICS_ENGINE_UNITS;
                collisionVertices[collisionVerticesIndex + 1] = vertexPosition.y() * Main.TO_PHYSICS_ENGINE_UNITS;
                collisionVertices[collisionVerticesIndex + 2] = vertexPosition.z() * Main.TO_PHYSICS_ENGINE_UNITS;
                collisionVerticesIndex += 3;
            }
        }
        
        collisionVertices = Arrays.copyOf(collisionVertices, collisionVerticesIndex);
        
        return generateIndices(collisionVertices, 3);
    }
    
    public static GImpactCollisionShape createGImpactCollisionShapeFromMeshes(float[][] vertices, int[][] indices, Matrix4fc[] models, int vertexSize, int xyzOffset) {
        Pair<float[], int[]> indexedCollisionVerticesPair = transformAndReindex(vertices, indices, models, vertexSize, xyzOffset);
        
        float[] indexedCollisionVertices = indexedCollisionVerticesPair.getA();
        int[] indexedCollisionIndices = indexedCollisionVerticesPair.getB();
        
        FloatBuffer verticesBuffer = BufferUtils
                .createFloatBuffer(indexedCollisionVertices.length)
                .put(indexedCollisionVertices)
                .flip()
                ;
        IntBuffer indicesBuffer = BufferUtils
                .createIntBuffer(indexedCollisionIndices.length)
                .put(indexedCollisionIndices)
                .flip();
        
        return new GImpactCollisionShape(new IndexedMesh(verticesBuffer, indicesBuffer));
    }
    
    public static MeshCollisionShape createStaticCollisionShapeFromMeshes(float[][] vertices, int[][] indices, Matrix4fc[] models, int vertexSize, int xyzOffset) {
        Pair<float[], int[]> indexedCollisionVerticesPair = transformAndReindex(vertices, indices, models, vertexSize, xyzOffset);
        
        float[] indexedCollisionVertices = indexedCollisionVerticesPair.getA();
        int[] indexedCollisionIndices = indexedCollisionVerticesPair.getB();
        
        FloatBuffer verticesBuffer = BufferUtils
                .createFloatBuffer(indexedCollisionVertices.length)
                .put(indexedCollisionVertices)
                .flip()
                ;
        IntBuffer indicesBuffer = BufferUtils
                .createIntBuffer(indexedCollisionIndices.length)
                .put(indexedCollisionIndices)
                .flip();
        
        return new MeshCollisionShape(true, new IndexedMesh(verticesBuffer, indicesBuffer));
    }
    
    public static CompoundCollisionShape createConvexCollisionShapeFromMeshes(float[][] vertices, int[][] indices, Matrix4fc[] models, int vertexSize, int xyzOffset, Vhacd4Parameters parameters) {
        Pair<float[], int[]> indexedCollisionVerticesPair = transformAndReindex(vertices, indices, models, vertexSize, xyzOffset);
        
        float[] indexedCollisionVertices = indexedCollisionVerticesPair.getA();
        int[] indexedCollisionIndices = indexedCollisionVerticesPair.getB();
        
        List<Vhacd4Hull> hulls = Vhacd4.compute(
                indexedCollisionVertices,
                indexedCollisionIndices,
                parameters
        );
        
        if (hulls.isEmpty()) {
            return null;
        }
        
        CompoundCollisionShape compound = new CompoundCollisionShape();
        for (Vhacd4Hull hull:hulls) {
            compound.addChildShape(new HullCollisionShape(hull));
        }
        
        return compound;
    }
    
    public static HullCollisionShape createHullCollisionShapeFromMeshes(float[][] vertices, int[][] indices, Matrix4fc[] models, int vertexSize, int xyzOffset) {
        Pair<float[], int[]> indexedCollisionVerticesPair = transformAndReindex(vertices, indices, models, vertexSize, xyzOffset);
        
        return new HullCollisionShape(indexedCollisionVerticesPair.getA());
    }
    
    public static MeshData createMeshFromCollisionShape(String name, CollisionShape shape) {
        FloatBuffer verts = DebugShapeFactory.getDebugTriangles(shape, DebugShapeFactory.highResolution);
        verts.flip();

        int amountOfVertices = verts.capacity() / 3;
        float[] vertices = new float[amountOfVertices * MeshData.SIZE];
        for (int v = 0; v < amountOfVertices; v++) {
            float x = verts.get() * Main.FROM_PHYSICS_ENGINE_UNITS;
            float y = verts.get() * Main.FROM_PHYSICS_ENGINE_UNITS;
            float z = verts.get() * Main.FROM_PHYSICS_ENGINE_UNITS;
            vertices[(v * MeshData.SIZE) + MeshData.XYZ_OFFSET + 0] = x;
            vertices[(v * MeshData.SIZE) + MeshData.XYZ_OFFSET + 1] = y;
            vertices[(v * MeshData.SIZE) + MeshData.XYZ_OFFSET + 2] = z;
        }

        Vector3f normal = new Vector3f();
        for (int v = 0; v < amountOfVertices; v += 3) {
            int i0 = v + 0;
            int i1 = v + 1;
            int i2 = v + 2;
            
            calculateTriangleNormal(
                    vertices, MeshData.SIZE, MeshData.XYZ_OFFSET,
                    i0, i1, i2,
                    normal
            );

            int v0 = i0 * MeshData.SIZE;
            int v1 = i1 * MeshData.SIZE;
            int v2 = i2 * MeshData.SIZE;

            vertices[v0 + MeshData.N_XYZ_OFFSET + 0] = normal.x();
            vertices[v0 + MeshData.N_XYZ_OFFSET + 1] = normal.y();
            vertices[v0 + MeshData.N_XYZ_OFFSET + 2] = normal.z();

            vertices[v1 + MeshData.N_XYZ_OFFSET + 0] = normal.x();
            vertices[v1 + MeshData.N_XYZ_OFFSET + 1] = normal.y();
            vertices[v1 + MeshData.N_XYZ_OFFSET + 2] = normal.z();

            vertices[v2 + MeshData.N_XYZ_OFFSET + 0] = normal.x();
            vertices[v2 + MeshData.N_XYZ_OFFSET + 1] = normal.y();
            vertices[v2 + MeshData.N_XYZ_OFFSET + 2] = normal.z();
        }
        
        MeshUtils.conservativeMergeByDistanceXYZ(
                vertices, MeshData.SIZE, MeshData.XYZ_OFFSET,
                0.0001f
        );
        
        Pair<float[], int[]> generated = MeshUtils.generateIndices(vertices, MeshData.SIZE);
        
        return new MeshData(name, generated.getA(), generated.getB());
    }
    
    public static SphereCollisionShape sphereCollisionFromVertices(float[] vertices, int vertexSize, int xyzOffset, float centerX, float centerY, float centerZ) {
        float maxRadius = 0f;
        
        for (int i = 0; i < vertices.length; i += vertexSize) {
            float x = vertices[i + xyzOffset + 0];
            float y = vertices[i + xyzOffset + 1];
            float z = vertices[i + xyzOffset + 2];
            
            x -= centerX;
            y -= centerY;
            z -= centerZ;
            
            maxRadius = (float) Math.max(maxRadius, Math.sqrt(
                    (x * x) + (y * y) + (z * z)
            ));
        }
        
        if (maxRadius <= 0f) {
            return null;
        }
        
        return new SphereCollisionShape(maxRadius * Main.TO_PHYSICS_ENGINE_UNITS);
    }
    
    public static CylinderCollisionShape cylinderCollisionFromVertices(float[] vertices, int vertexSize, int xyzOffset, float centerX, float centerY, float centerZ, int axis) {
        float maxRadius = 0f;
        float maxHeight = 0f;
        
        for (int i = 0; i < vertices.length; i += vertexSize) {
            float x = vertices[i + xyzOffset + 0];
            float y = vertices[i + xyzOffset + 1];
            float z = vertices[i + xyzOffset + 2];
            
            x -= centerX;
            y -= centerY;
            z -= centerZ;
            
            float radiusX, heightY, radiusZ;
            
            switch (axis) {
                case 0 -> {
                    radiusX = y;
                    heightY = x;
                    radiusZ = z;
                }
                case 1 -> {
                    radiusX = x;
                    heightY = y;
                    radiusZ = z;
                }
                case 2 -> {
                    radiusX = x;
                    heightY = z;
                    radiusZ = y;
                }
                default -> throw new UnsupportedOperationException("Unknown axis "+axis);
            }
            
            float radius = (float) Math.sqrt((radiusX * radiusX) + (radiusZ * radiusZ));
            float height = Math.abs(heightY);
            
            maxRadius = Math.max(maxRadius, radius);
            maxHeight = Math.max(maxHeight, height);
        }
        
        if (maxRadius <= 0f) {
            return null;
        }
        if (maxHeight <= 0f) {
            return null;
        }
        
        return new CylinderCollisionShape(
                maxRadius * Main.TO_PHYSICS_ENGINE_UNITS,
                maxHeight * 2f * Main.TO_PHYSICS_ENGINE_UNITS,
                axis
        );
    }
    
    public static BoxCollisionShape boxCollisionFromVertices(float[] vertices, int vertexSize, int xyzOffset, float centerX, float centerY, float centerZ) {
        float halfExtentX = 0f;
        float halfExtentY = 0f;
        float halfExtentZ = 0f;
        
        for (int i = 0; i < vertices.length; i += vertexSize) {
            float x = vertices[i + xyzOffset + 0];
            float y = vertices[i + xyzOffset + 1];
            float z = vertices[i + xyzOffset + 2];
            
            x -= centerX;
            y -= centerY;
            z -= centerZ;
            
            halfExtentX = Math.max(halfExtentX, Math.abs(x));
            halfExtentY = Math.max(halfExtentY, Math.abs(y));
            halfExtentZ = Math.max(halfExtentZ, Math.abs(z));
        }
        
        return new BoxCollisionShape(
                halfExtentX * Main.TO_PHYSICS_ENGINE_UNITS,
                halfExtentY * Main.TO_PHYSICS_ENGINE_UNITS,
                halfExtentZ * Main.TO_PHYSICS_ENGINE_UNITS
        );
    }
    
    public static CapsuleCollisionShape capsuleCollisionFromVertices(float[] vertices, int vertexSize, int xyzOffset, float centerX, float centerY, float centerZ, int axis) {
        float maxRadius = 0f;
        float maxHeight = 0f;
        
        for (int i = 0; i < vertices.length; i += vertexSize) {
            float x = vertices[i + xyzOffset + 0];
            float y = vertices[i + xyzOffset + 1];
            float z = vertices[i + xyzOffset + 2];
            
            x -= centerX;
            y -= centerY;
            z -= centerZ;
            
            float radiusX, heightY, radiusZ;
            
            switch (axis) {
                case 0 -> {
                    radiusX = y;
                    heightY = x;
                    radiusZ = z;
                }
                case 1 -> {
                    radiusX = x;
                    heightY = y;
                    radiusZ = z;
                }
                case 2 -> {
                    radiusX = x;
                    heightY = z;
                    radiusZ = y;
                }
                default -> throw new UnsupportedOperationException("Unknown axis "+axis);
            }
            
            float radius = (float) Math.sqrt((radiusX * radiusX) + (radiusZ * radiusZ));
            float height = Math.abs(heightY);
            
            maxRadius = Math.max(maxRadius, radius);
            maxHeight = Math.max(maxHeight, height);
        }
        
        float radius = maxRadius;
        float height = (maxHeight * 2f) - (maxRadius * 2f);
        
        if (radius <= 0f) {
            return null;
        }
        if (height <= 0f) {
            return null;
        }
        
        return new CapsuleCollisionShape(
                radius * Main.TO_PHYSICS_ENGINE_UNITS,
                height * Main.TO_PHYSICS_ENGINE_UNITS,
                axis
        );
    }
    
    public static void aabCenter(float[] vertices, int vertexSize, int xyzOffset, Vector3f centerOut) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        
        for (int i = 0; i < vertices.length; i += vertexSize) {
            float x = vertices[i + xyzOffset + 0];
            float y = vertices[i + xyzOffset + 1];
            float z = vertices[i + xyzOffset + 2];
            
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        
        float centerX = (minX * 0.5f) + (maxX * 0.5f);
        float centerY = (minY * 0.5f) + (maxY * 0.5f);
        float centerZ = (minZ * 0.5f) + (maxZ * 0.5f);
        
        centerOut.set(centerX, centerY, centerZ);
    }
    
    private MeshUtils() {

    }

}
