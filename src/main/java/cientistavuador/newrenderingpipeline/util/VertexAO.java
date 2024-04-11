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

import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class VertexAO {
    
    private static final float EPSILON = 0.0001f;

    public static void vertexAO(float[] vertices, int vertexSize, int xyzOffset, int outAoOffset, float aoSize, int aoRays, float rayOffset) {
        new VertexAO(vertices, vertexSize, xyzOffset, outAoOffset, aoSize, aoRays, rayOffset).process();
    }

    private class Vertex {

        public int vertex;

        @Override
        public int hashCode() {
            int hash = 7;
            for (int i = VertexAO.this.xyzOffset; i < VertexAO.this.xyzOffset + 3; i++) {
                hash = 79 * hash + Float.floatToRawIntBits(VertexAO.this.vertices[this.vertex + i]);
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
            for (int i = VertexAO.this.xyzOffset; i < VertexAO.this.xyzOffset + 3; i++) {
                float t = VertexAO.this.vertices[this.vertex + i];
                float o = VertexAO.this.vertices[other.vertex + i];
                if (t != o) {
                    return false;
                }
            }
            return true;
        }
    }

    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final float[] vertices;
    private final int vertexSize;
    private final int xyzOffset;
    private final int outAoOffset;
    private final float aoSize;
    private final int aoRays;
    private final float rayOffset;

    private BVH bvh = null;
    private final Map<Vertex, List<Vertex>> mappedVertices = new HashMap<>();

    private VertexAO(float[] vertices, int vertexSize, int xyzOffset, int outAoOffset, float aoSize, int aoRays, float rayOffset) {
        this.vertices = vertices;
        this.vertexSize = vertexSize;
        this.xyzOffset = xyzOffset;
        this.outAoOffset = outAoOffset;
        this.aoSize = aoSize;
        this.aoRays = aoRays;
        this.rayOffset = rayOffset;
    }

    private void createBVH() {
        int[] indices = new int[this.vertices.length / vertexSize];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        this.bvh = BVH.create(
                this.vertices,
                indices,
                this.vertexSize,
                this.xyzOffset
        );
    }

    private void mapVertices() {
        for (int v = 0; v < this.vertices.length; v += this.vertexSize) {
            VertexAO.Vertex e = new VertexAO.Vertex();
            e.vertex = v;
            List<VertexAO.Vertex> verts = mappedVertices.get(e);
            if (verts == null) {
                verts = new ArrayList<>();
                mappedVertices.put(e, verts);
            }
            verts.add(e);
        }
    }

    private void computeAO() {
        List<Map.Entry<Vertex, List<Vertex>>> entryList = new ArrayList<>();
        entryList.addAll(this.mappedVertices.entrySet());

        int numberOfProcessors = Runtime.getRuntime().availableProcessors();
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < entryList.size(); i += numberOfProcessors) {
            for (int j = 0; j < numberOfProcessors; j++) {
                if ((i + j) < entryList.size()) {
                    final List<Vertex> finalVertexList = entryList.get(i + j).getValue();
                    tasks.add(this.service.submit(() -> {
                        computeAO(finalVertexList);
                    }));
                }
            }

            for (Future<?> fut : tasks) {
                try {
                    fut.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }

            tasks.clear();
        }
    }

    private void calculateNormal(Vertex vertex, Vector3f outNormal) {
        int triangle = (vertex.vertex / (this.vertexSize * 3));

        int i0 = ((triangle * 3) + 0);
        int i1 = ((triangle * 3) + 1);
        int i2 = ((triangle * 3) + 2);

        MeshUtils.calculateTriangleNormal(
                this.vertices,
                this.vertexSize,
                this.xyzOffset,
                i0, i1, i2,
                outNormal
        );
    }

    private void randomTangentDirection(Vector3f outDirection, Random random) {
        float x;
        float y;
        float z;
        float dist;

        do {
            x = (random.nextFloat() * 2f) - 1f;
            y = (random.nextFloat() * 2f) - 1f;
            z = random.nextFloat();
            dist = (x * x) + (y * y) + (z * z);
        } while (dist > 1f);

        outDirection.set(
                x,
                y,
                z
        )
                .normalize();
    }

    private void computeAO(List<Vertex> vertices) {
        float nX = 0f;
        float nY = 0f;
        float nZ = 0f;
        Vector3f normal = new Vector3f();

        for (Vertex v : vertices) {
            calculateNormal(v, normal);
            if (normal.isFinite()) {
                nX += normal.x();
                nY += normal.y();
                nZ += normal.z();
            }
        }

        normal.set(nX, nY, nZ).normalize();
        if (!normal.isFinite()) {
            return;
        }

        float upX = 0f;
        float upY = 1f;
        float upZ = 0f;

        if (Math.abs(normal.dot(upX, upY, upZ)) >= (1f - EPSILON)) {
            upY = 0f;
            upX = 1f;
        }

        Vector3f tangent = normal.cross(upX, upY, upZ, new Vector3f()).normalize();
        Vector3f bitangent = normal.cross(tangent, new Vector3f()).normalize();

        Matrix3f TBN = new Matrix3f(tangent, bitangent, normal);

        int vertex = vertices.get(0).vertex;
        Vector3f offsetPosition = new Vector3f(normal).mul(this.rayOffset).add(
                this.vertices[vertex + this.xyzOffset + 0],
                this.vertices[vertex + this.xyzOffset + 1],
                this.vertices[vertex + this.xyzOffset + 2]
        );

        Random random = new Random();
        Vector3f tangentDirection = new Vector3f();
        float result = 0f;
        for (int i = 0; i < this.aoRays; i++) {
            randomTangentDirection(tangentDirection, random);
            TBN.transform(tangentDirection).normalize();

            if (this.bvh.fastTestRay(offsetPosition, tangentDirection, this.aoSize)) {
                result++;
            }
        }
        result /= this.aoRays;

        for (Vertex v : vertices) {
            this.vertices[v.vertex + this.outAoOffset] = result;
        }
    }
    
    private void process() {
        try {
            createBVH();
            mapVertices();
            computeAO();
        } finally {
            this.service.shutdownNow();
        }
    }
}
