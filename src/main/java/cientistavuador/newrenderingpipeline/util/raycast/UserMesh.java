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

import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.Pair;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class UserMesh {

    public static final int USER_INDEX_OFFSET = 0;
    public static final int VERTEX_INDEX_OFFSET = USER_INDEX_OFFSET + 1;
    public static final int XYZ_OFFSET = VERTEX_INDEX_OFFSET + 1;

    public static final int VERTEX_SIZE = XYZ_OFFSET + 3;

    public static UserMesh create(
            float[][] verticesArray,
            int[][] indicesArray,
            Matrix4fc[] transformations,
            Object[] userData,
            int vertexSize,
            int xyzOffset
    ) {
        if (verticesArray.length != indicesArray.length
                || (transformations != null && verticesArray.length != transformations.length)
                || verticesArray.length != userData.length) {
            throw new IllegalArgumentException("Arrays differ in size!");
        }

        int totalVertices = 0;
        for (int[] indices : indicesArray) {
            totalVertices += indices.length;
        }

        float[] resultVertices = new float[totalVertices * UserMesh.VERTEX_SIZE];
        int resultVerticesIndex = 0;

        Vector3f transformed = new Vector3f();

        for (int userIndex = 0; userIndex < indicesArray.length; userIndex++) {
            float[] vertices = verticesArray[userIndex];

            float userIndexFloat = Float.intBitsToFloat(userIndex);
            for (int vertexIndex : indicesArray[userIndex]) {
                float vertexIndexFloat = Float.intBitsToFloat(vertexIndex);

                float x = vertices[(vertexIndex * vertexSize) + xyzOffset + 0];
                float y = vertices[(vertexIndex * vertexSize) + xyzOffset + 1];
                float z = vertices[(vertexIndex * vertexSize) + xyzOffset + 2];

                transformed.set(x, y, z);
                if (transformations != null) {
                    Matrix4fc transformation = transformations[userIndex];
                    if (transformation != null) {
                        transformation.transformProject(transformed);
                    }
                }
                x = transformed.x();
                y = transformed.y();
                z = transformed.z();

                resultVertices[resultVerticesIndex + UserMesh.USER_INDEX_OFFSET + 0] = userIndexFloat;
                resultVertices[resultVerticesIndex + UserMesh.VERTEX_INDEX_OFFSET + 0] = vertexIndexFloat;
                resultVertices[resultVerticesIndex + UserMesh.XYZ_OFFSET + 0] = x;
                resultVertices[resultVerticesIndex + UserMesh.XYZ_OFFSET + 1] = y;
                resultVertices[resultVerticesIndex + UserMesh.XYZ_OFFSET + 2] = z;
                resultVerticesIndex += UserMesh.VERTEX_SIZE;
            }
        }

        Pair<float[], int[]> pair = MeshUtils.generateIndices(resultVertices, UserMesh.VERTEX_SIZE);

        float[] finalVertices = pair.getA();
        int[] finalIndices = pair.getB();

        return new UserMesh(finalVertices, finalIndices, userData.clone());
    }

    private final float[] vertices;
    private final int[] indices;
    private final Object[] userData;

    private UserMesh(
            float[] vertices,
            int[] indices,
            Object[] userData
    ) {
        this.vertices = vertices;
        this.indices = indices;
        this.userData = userData;
    }

    public float[] getVertices() {
        return vertices;
    }

    public int[] getIndices() {
        return indices;
    }

    public Object[] getUserData() {
        return userData;
    }

}
