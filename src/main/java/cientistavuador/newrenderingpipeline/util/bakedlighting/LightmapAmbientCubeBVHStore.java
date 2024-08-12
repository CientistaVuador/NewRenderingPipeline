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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class LightmapAmbientCubeBVHStore {
    
    public static final long MAGIC_NUMBER = -8375053507618471537L;
    
    private static void writeAmbientCube(LightmapAmbientCube cube, ObjectOutputStream out) throws IOException {
        float x = cube.getPosition().x();
        float y = cube.getPosition().y();
        float z = cube.getPosition().z();
        float radius = cube.getRadius();
        
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(radius);
        
        out.writeInt(cube.getNumberOfAmbientCubes());
        for (int i = 0; i < cube.getNumberOfAmbientCubes(); i++) {
            AmbientCube ambient = cube.getAmbientCube(i);
            for (int j = 0; j < AmbientCube.SIDES; j++) {
                Vector3fc sideColor = ambient.getSide(j);
                
                float r = sideColor.x();
                float g = sideColor.y();
                float b = sideColor.z();
                
                out.writeFloat(r);
                out.writeFloat(g);
                out.writeFloat(b);
            }
        }
    }
    
    private static void writeAmbientCubes(List<LightmapAmbientCube> cubes, ObjectOutputStream out) throws IOException {
        out.writeInt(cubes.size());
        for (LightmapAmbientCube cube:cubes) {
            writeAmbientCube(cube, out);
        }
    }
    
    private static void writeBVHRecursively(LightmapAmbientCubeBVH bvh, ObjectOutputStream out) throws IOException {
        if (bvh == null) {
            return;
        }
        
        out.writeFloat(bvh.getMin().x());
        out.writeFloat(bvh.getMin().y());
        out.writeFloat(bvh.getMin().z());
        
        out.writeFloat(bvh.getMax().x());
        out.writeFloat(bvh.getMax().y());
        out.writeFloat(bvh.getMax().z());
        
        out.writeFloat(bvh.getAverageRadius());
        out.writeInt(bvh.getAmbientCubeIndex());
        
        out.writeBoolean(bvh.getLeft() != null);
        out.writeBoolean(bvh.getRight() != null);
        
        writeBVHRecursively(bvh.getLeft(), out);
        writeBVHRecursively(bvh.getRight(), out);
    }
    
    public static void writeBVH(LightmapAmbientCubeBVH bvh, OutputStream outputStream) throws IOException {
        GZIPOutputStream gzipOut = new GZIPOutputStream(outputStream);
        ObjectOutputStream out = new ObjectOutputStream(gzipOut);
        
        out.writeLong(MAGIC_NUMBER);
        writeAmbientCubes(bvh.getAmbientCubes(), out);
        writeBVHRecursively(bvh, out);
        
        out.flush();
        gzipOut.finish();
    }
    
    private static List<LightmapAmbientCube> readAmbientCubes(ObjectInputStream in) throws IOException {
        int amount = in.readInt();
        List<LightmapAmbientCube> cubes = new ArrayList<>(amount);
        
        for (int i = 0; i < amount; i++) {
            float x = in.readFloat();
            float y = in.readFloat();
            float z = in.readFloat();
            
            float radius = in.readFloat();
            
            int numberOfAmbientCubes = in.readInt();
            LightmapAmbientCube cube = new LightmapAmbientCube(x, y, z, radius, numberOfAmbientCubes);
            for (int j = 0; j < numberOfAmbientCubes; j++) {
                for (int k = 0; k < AmbientCube.SIDES; k++) {
                    float r = in.readFloat();
                    float g = in.readFloat();
                    float b = in.readFloat();
                    
                    cube.getAmbientCube(j).setSide(k, r, g, b);
                }
            }
            cubes.add(cube);
        }
        
        return cubes;
    }
    
    private static LightmapAmbientCubeBVH readBVHRecursively(List<LightmapAmbientCube> ambientCubes, ObjectInputStream in) throws IOException {
        float minX = in.readFloat();
        float minY = in.readFloat();
        float minZ = in.readFloat();
        
        float maxX = in.readFloat();
        float maxY = in.readFloat();
        float maxZ = in.readFloat();
        
        float averageRadius = in.readFloat();
        int ambientCubeIndex = in.readInt();
        
        boolean hasLeft = in.readBoolean();
        boolean hasRight = in.readBoolean();
        
        LightmapAmbientCubeBVH left = null;
        LightmapAmbientCubeBVH right = null;
        
        if (hasLeft) {
            left = readBVHRecursively(ambientCubes, in);
        }
        
        if (hasRight) {
            right = readBVHRecursively(ambientCubes, in);
        }
        
        return new LightmapAmbientCubeBVH(
                ambientCubes,
                averageRadius,
                new Vector3f(minX, minY, minZ), new Vector3f(maxX, maxY, maxZ),
                ambientCubeIndex,
                left, right
        );
    }
    
    public static LightmapAmbientCubeBVH readBVH(InputStream inputStream) throws IOException {
        GZIPInputStream gzipIn = new GZIPInputStream(inputStream);
        ObjectInputStream in = new ObjectInputStream(gzipIn);
        
        long magic = in.readLong();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Invalid ambient cube bvh magic number!");
        }
        
        List<LightmapAmbientCube> ambientCubes = readAmbientCubes(in);
        LightmapAmbientCubeBVH bvh = readBVHRecursively(ambientCubes, in);
        
        return bvh;
    }
    
    public static LightmapAmbientCubeBVH readBVH(byte[] data) {
        try {
            return readBVH(new ByteArrayInputStream(data));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    private LightmapAmbientCubeBVHStore() {
        
    }
}
