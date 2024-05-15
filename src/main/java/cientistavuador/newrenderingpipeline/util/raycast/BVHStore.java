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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class BVHStore {
    
    public static final long MAGIC_NUMBER = 953602573048789926L;
    
    private static void recursiveWriteBVH(ObjectOutputStream out, BVH bvh) throws IOException {
        Vector3fc min = bvh.getMin();
        Vector3fc max = bvh.getMax();
        
        out.writeFloat(min.x());
        out.writeFloat(min.y());
        out.writeFloat(min.z());
        
        out.writeFloat(max.x());
        out.writeFloat(max.y());
        out.writeFloat(max.z());
        
        out.writeInt(bvh.getAmountOfTriangles());
        
        int[] triangles = bvh.getTriangles();
        
        if (triangles != null) {
            out.writeBoolean(true);
            out.writeInt(triangles.length);
            for (int triangle:triangles) {
                out.writeInt(triangle);
            }
        } else {
            out.writeBoolean(false);
        }
        
        BVH left = bvh.getLeft();
        BVH right = bvh.getRight();
        
        if (left != null) {
            out.writeBoolean(true);
            recursiveWriteBVH(out, left);
        } else {
            out.writeBoolean(false);
        }
        
        if (right != null) {
            out.writeBoolean(true);
            recursiveWriteBVH(out, right);
        } else {
            out.writeBoolean(false);
        }
    }
    
    public static void writeBVH(OutputStream output, BVH bvh) throws IOException {
        GZIPOutputStream zipOut = new GZIPOutputStream(output);
        ObjectOutputStream out = new ObjectOutputStream(zipOut);
        
        out.writeLong(MAGIC_NUMBER);
        recursiveWriteBVH(out, bvh);
        
        out.flush();
        zipOut.finish();
    }
    
    private static BVH recursiveReadBVH(ObjectInputStream in, float[] vertices, int[] indices, int vertexSize, int xyzOffset, Object userObject) throws IOException {
        float minX = in.readFloat();
        float minY = in.readFloat();
        float minZ = in.readFloat();
        
        float maxX = in.readFloat();
        float maxY = in.readFloat();
        float maxZ = in.readFloat();
        
        int amountOfTriangles = in.readInt();
        int[] triangles = null;
        
        if (in.readBoolean()) {
            triangles = new int[in.readInt()];
            for (int i = 0; i < triangles.length; i++) {
                triangles[i] = in.readInt();
            }
        }
        
        BVH left = null;
        if (in.readBoolean()) {
            left = recursiveReadBVH(in, vertices, indices, vertexSize, xyzOffset, userObject);
        }
        
        BVH right = null;
        if (in.readBoolean()) {
            right = recursiveReadBVH(in, vertices, indices, vertexSize, xyzOffset, userObject);
        }
        
        BVH bvh = new BVH(
                userObject,
                vertices, indices, vertexSize, xyzOffset,
                minX, minY, minZ,
                maxX, maxY, maxZ
        );
        
        bvh.left = left;
        bvh.right = right;
        bvh.amountOfTriangles = amountOfTriangles;
        bvh.triangles = triangles;
        
        if (left != null) {
            left.parent = bvh;
        }
        if (right != null) {
            right.parent = bvh;
        }
        
        return bvh;
    }
    
    public static BVH readBVH(InputStream input, float[] vertices, int[] indices, int vertexSize, int xyzOffset, Object userObject) throws IOException {
        GZIPInputStream zipIn = new GZIPInputStream(input);
        ObjectInputStream in = new ObjectInputStream(zipIn);
        
        long magic = in.readLong();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException("Invalid BVH Data.");
        }
        
        return recursiveReadBVH(in, vertices, indices, vertexSize, xyzOffset, userObject);
    }
    
    private BVHStore() {
        
    }
}
