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
package cientistavuador.newrenderingpipeline.util.bakedlighting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class LightmapQuads {

    private static final float EPSILON = 0.001f;

    private static Map<Vector3f, List<Integer>> mapVertices(float[] vertices, int vertexSize, int xyzOffset) {
        Map<Vector3f, List<Integer>> vertexMap = new HashMap<>();
        for (int i = 0; i < vertices.length; i += vertexSize) {
            Vector3f position = new Vector3f(
                    vertices[i + xyzOffset + 0],
                    vertices[i + xyzOffset + 1],
                    vertices[i + xyzOffset + 2]
            );

            List<Integer> verticesList = vertexMap.get(position);
            if (verticesList == null) {
                verticesList = new ArrayList<>();
                vertexMap.put(position, verticesList);
            }
            verticesList.add(i);
        }
        return vertexMap;
    }

    private static Map<Integer, Vector3f> mapTriangleNormals(float[] vertices, int vertexSize, int xyzOffset) {
        Map<Integer, Vector3f> triangleNormals = new HashMap<>();
        for (int i = 0; i < vertices.length; i += vertexSize * 3) {
            Vector3f a = new Vector3f(
                    vertices[i + (vertexSize * 0) + xyzOffset + 0],
                    vertices[i + (vertexSize * 0) + xyzOffset + 1],
                    vertices[i + (vertexSize * 0) + xyzOffset + 2]
            );
            Vector3f b = new Vector3f(
                    vertices[i + (vertexSize * 1) + xyzOffset + 0],
                    vertices[i + (vertexSize * 1) + xyzOffset + 1],
                    vertices[i + (vertexSize * 1) + xyzOffset + 2]
            );
            Vector3f c = new Vector3f(
                    vertices[i + (vertexSize * 2) + xyzOffset + 0],
                    vertices[i + (vertexSize * 2) + xyzOffset + 1],
                    vertices[i + (vertexSize * 2) + xyzOffset + 2]
            );

            a.sub(c);
            b.sub(c);
            a.cross(b, c).normalize();

            triangleNormals.put(i, c);
        }
        return triangleNormals;
    }

    private static void transform(float[] uvs, Matrix4fc matrix) {
        Vector3f transformedPosition = new Vector3f();
        for (int j = 0; j < uvs.length; j += 3) {
            transformedPosition.set(
                    uvs[j + 0],
                    uvs[j + 1],
                    uvs[j + 2]
            );
            matrix.transformProject(transformedPosition);
            uvs[j + 0] = transformedPosition.x();
            uvs[j + 1] = transformedPosition.y();
            uvs[j + 2] = transformedPosition.z();
        }
    }

    private static void minMax(float[] uvs, Vector2f outMin, Vector2f outMax) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < uvs.length; i += 3) {
            minX = Math.min(minX, uvs[i + 0]);
            minY = Math.min(minY, uvs[i + 1]);
            maxX = Math.max(maxX, uvs[i + 0]);
            maxY = Math.max(maxY, uvs[i + 1]);
        }
        outMin.set(minX, minY);
        outMax.set(maxX, maxY);
    }

    private static void rotate(float[] uvs, float rotation) {
        Vector3f position = new Vector3f();
        float angleRadians = (float) Math.toRadians(rotation);
        for (int i = 0; i < uvs.length; i += 3) {
            position.set(
                    uvs[i + 0],
                    uvs[i + 1],
                    uvs[i + 2]
            ).rotateZ(angleRadians);
            uvs[i + 0] = position.x();
            uvs[i + 1] = position.y();
            uvs[i + 2] = position.z();
        }
    }

    private static float findBestRotation(float[] uvs) {
        Vector2f min = new Vector2f();
        Vector2f max = new Vector2f();

        float bestRotation = 0f;
        minMax(uvs, min, max);

        float bestArea = (max.x() - min.x()) * (max.y() - min.y());
        float[] clone = uvs.clone();

        for (int i = 0; i < 89; i++) {
            rotate(clone, 1f);
            minMax(clone, min, max);

            float area = (max.x() - min.x()) * (max.y() - min.y());

            if (area < bestArea) {
                bestRotation = i + 1f;
                bestArea = area;
            }
        }

        return bestRotation;
    }

    public static LightmapQuad[] generate(
            float[] vertices, int vertexSize,
            int xyzOffset, int outLightmapUv, int outLightmapQuadId
    ) {
        Map<Vector3f, List<Integer>> vertexMap = mapVertices(vertices, vertexSize, xyzOffset);
        Map<Integer, Vector3f> triangleNormals = mapTriangleNormals(vertices, vertexSize, xyzOffset);

        List<List<Integer>> faces = new ArrayList<>();
        Set<Integer> processedTriangles = new HashSet<>();

        for (int i = 0; i < vertices.length; i += vertexSize * 3) {
            if (processedTriangles.contains(i)) {
                continue;
            }
            processedTriangles.add(i);

            Vector3f triangleNormal = triangleNormals.get(i);

            List<Integer> face = new ArrayList<>();
            face.add(i);

            class Edge {

                int a;
                int b;

                Edge(int a, int b) {
                    this.a = a;
                    this.b = b;
                }
            }

            Queue<Edge> edges = new ArrayDeque<>();

            edges.add(new Edge(i + (vertexSize * 0), i + (vertexSize * 1)));
            edges.add(new Edge(i + (vertexSize * 1), i + (vertexSize * 2)));
            edges.add(new Edge(i + (vertexSize * 2), i + (vertexSize * 0)));

            Vector3f edgePositionA = new Vector3f();
            Vector3f edgePositionB = new Vector3f();

            Edge e;
            while ((e = edges.poll()) != null) {
                edgePositionA.set(
                        vertices[e.a + xyzOffset + 0],
                        vertices[e.a + xyzOffset + 1],
                        vertices[e.a + xyzOffset + 2]
                );
                edgePositionB.set(
                        vertices[e.b + xyzOffset + 0],
                        vertices[e.b + xyzOffset + 1],
                        vertices[e.b + xyzOffset + 2]
                );

                List<Integer> verticesOnA = vertexMap.get(edgePositionA);
                for (Integer vertexOnA : verticesOnA) {
                    int triangle = (vertexOnA / (vertexSize * 3)) * (vertexSize * 3);
                    if (processedTriangles.contains(triangle)) {
                        continue;
                    }
                    Vector3f otherTriangleNormal = triangleNormals.get(triangle);
                    if (triangleNormal.dot(otherTriangleNormal) < (1f - EPSILON)) {
                        continue;
                    }

                    boolean foundEdge = false;
                    List<Integer> verticesOnB = vertexMap.get(edgePositionB);
                    for (Integer vertexOnB : verticesOnB) {
                        int otherTriangle = (vertexOnB / (vertexSize * 3)) * (vertexSize * 3);
                        if (otherTriangle != triangle) {
                            continue;
                        }
                        foundEdge = true;
                        break;
                    }

                    if (!foundEdge) {
                        continue;
                    }

                    edges.add(new Edge(triangle + (vertexSize * 0), triangle + (vertexSize * 1)));
                    edges.add(new Edge(triangle + (vertexSize * 1), triangle + (vertexSize * 2)));
                    edges.add(new Edge(triangle + (vertexSize * 2), triangle + (vertexSize * 0)));

                    face.add(triangle);
                    processedTriangles.add(triangle);
                }
            }

            faces.add(face);
        }

        List<LightmapQuad> quads = new ArrayList<>();

        for (int i = 0; i < faces.size(); i++) {
            List<Integer> face = faces.get(i);

            float[] uvs = new float[face.size() * 3 * 3];
            for (int j = 0; j < face.size(); j++) {
                int triangle = face.get(j);

                for (int k = 0; k < 3; k++) {
                    uvs[(j * 3 * 3) + (k * 3) + 0] = vertices[triangle + (vertexSize * k) + xyzOffset + 0];
                    uvs[(j * 3 * 3) + (k * 3) + 1] = vertices[triangle + (vertexSize * k) + xyzOffset + 1];
                    uvs[(j * 3 * 3) + (k * 3) + 2] = vertices[triangle + (vertexSize * k) + xyzOffset + 2];
                }
            }

            Vector3f faceNormal = triangleNormals.get(face.get(0));

            float upX = 0f;
            float upY = 1f;
            float upZ = 0f;

            if (Math.abs(faceNormal.dot(upX, upY, upZ)) >= (1f - EPSILON)) {
                upY = 0f;
                upX = 1f;
            }

            Vector3f transformedPosition = new Vector3f();
            
            Matrix4f lookAt = new Matrix4f()
                    .lookAt(
                            0f, 0f, 0f,
                            -faceNormal.x(), -faceNormal.y(), -faceNormal.z(),
                            upX, upY, upZ
                    );
            transform(uvs, lookAt);

            Vector2f min = new Vector2f();
            Vector2f max = new Vector2f();
            
            minMax(uvs, min, max);
            Matrix4f centralize = new Matrix4f()
                    .translate(
                            (max.x() + min.x()) * -0.5f,
                            (max.y() + min.y()) * -0.5f,
                            -transformedPosition.z()
                    );
            transform(uvs, centralize);

            float bestRotation = findBestRotation(uvs);
            Matrix4f rotate = new Matrix4f()
                    .rotateZ((float) Math.toRadians(bestRotation));
            transform(uvs, rotate);

            minMax(uvs, min, max);
            Matrix4f bottomLeftCentralize = new Matrix4f()
                    .translate(-min.x(), -min.y(), 0f);
            transform(uvs, bottomLeftCentralize);
            
            for (int j = 0; j < face.size(); j++) {
                int triangle = face.get(j);

                for (int k = 0; k < 3; k++) {
                    vertices[triangle + (vertexSize * k) + outLightmapUv + 0] = uvs[(j * 3 * 3) + (k * 3) + 0];
                    vertices[triangle + (vertexSize * k) + outLightmapUv + 1] = uvs[(j * 3 * 3) + (k * 3) + 1];
                    vertices[triangle + (vertexSize * k) + outLightmapQuadId + 0] = Float.intBitsToFloat(i);
                }
            }

            quads.add(new LightmapQuad(
                    i,
                    (max.x() - min.x()), (max.y() - min.y()),
                    new Matrix4f(bottomLeftCentralize)
                            .mul(rotate)
                            .mul(centralize)
                            .mul(lookAt)
            ));
        }

        return quads.toArray(LightmapQuad[]::new);
    }

    private LightmapQuads() {

    }

}
