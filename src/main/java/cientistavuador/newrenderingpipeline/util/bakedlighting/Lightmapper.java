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

import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.joml.Vector3f;
import org.joml.primitives.Rectanglei;

/**
 *
 * @author Cien
 */
public class Lightmapper {

    public static final int OFFSET_POSITION_XYZ = 0;
    public static final int OFFSET_LIGHTMAP_XY = OFFSET_POSITION_XYZ + 3;
    public static final int OFFSET_TEXTURE_XY = OFFSET_LIGHTMAP_XY + 2;
    public static final int OFFSET_TRIANGLE_NORMAL_XYZ = OFFSET_TEXTURE_XY + 2;
    public static final int OFFSET_NORMAL_XYZ = OFFSET_TRIANGLE_NORMAL_XYZ + 3;
    public static final int OFFSET_USER_XY = OFFSET_NORMAL_XYZ + 3;

    public static final int VERTEX_SIZE = OFFSET_USER_XY + 2;

    private static float[] validate(float[] mesh) {
        if (mesh == null) {
            return new float[0];
        }

        if (mesh.length % VERTEX_SIZE != 0) {
            throw new IllegalArgumentException("Invalid vertex size!");
        }
        if ((mesh.length / VERTEX_SIZE) % 3 != 0) {
            throw new IllegalArgumentException("The mesh does not contains triangles");
        }

        float[] copy = mesh.clone();

        Vector3f normal = new Vector3f();

        for (int i = 0; i < mesh.length; i += VERTEX_SIZE * 3) {
            float v0x = copy[i + (VERTEX_SIZE * 0) + OFFSET_POSITION_XYZ + 0];
            float v0y = copy[i + (VERTEX_SIZE * 0) + OFFSET_POSITION_XYZ + 1];
            float v0z = copy[i + (VERTEX_SIZE * 0) + OFFSET_POSITION_XYZ + 2];

            float v1x = copy[i + (VERTEX_SIZE * 1) + OFFSET_POSITION_XYZ + 0];
            float v1y = copy[i + (VERTEX_SIZE * 1) + OFFSET_POSITION_XYZ + 1];
            float v1z = copy[i + (VERTEX_SIZE * 1) + OFFSET_POSITION_XYZ + 2];

            float v2x = copy[i + (VERTEX_SIZE * 2) + OFFSET_POSITION_XYZ + 0];
            float v2y = copy[i + (VERTEX_SIZE * 2) + OFFSET_POSITION_XYZ + 1];
            float v2z = copy[i + (VERTEX_SIZE * 2) + OFFSET_POSITION_XYZ + 2];

            MeshUtils.calculateTriangleNormal(v0x, v0y, v0z, v1x, v1y, v1z, v2x, v2y, v2z, normal);

            for (int j = 0; j < 3; j++) {
                copy[i + (VERTEX_SIZE * j) + OFFSET_TRIANGLE_NORMAL_XYZ + 0] = normal.x();
                copy[i + (VERTEX_SIZE * j) + OFFSET_TRIANGLE_NORMAL_XYZ + 1] = normal.y();
                copy[i + (VERTEX_SIZE * j) + OFFSET_TRIANGLE_NORMAL_XYZ + 2] = normal.z();
            }
        }

        for (int i = 0; i < mesh.length; i += VERTEX_SIZE) {
            float nx = copy[i + OFFSET_NORMAL_XYZ + 0];
            float ny = copy[i + OFFSET_NORMAL_XYZ + 1];
            float nz = copy[i + OFFSET_NORMAL_XYZ + 2];

            normal.set(nx, ny, nz).normalize();

            if (!normal.isFinite()) {
                normal.set(
                        copy[i + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                        copy[i + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                        copy[i + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
                );
            }

            copy[i + OFFSET_NORMAL_XYZ + 0] = normal.x();
            copy[i + OFFSET_NORMAL_XYZ + 1] = normal.y();
            copy[i + OFFSET_NORMAL_XYZ + 2] = normal.z();
        }

        return copy;
    }

    public static class Lightmap {

        private final String name;
        private final int size;
        private final float[] lightmap;

        public Lightmap(String name, int size, float[] lightmap) {
            this.name = name;
            this.size = size;
            this.lightmap = lightmap;
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            return size;
        }

        public float[] getLightmap() {
            return lightmap;
        }

    }

    private static class LightGroup {

        public String groupName = "";
        public final List<Scene.Light> lights = new ArrayList<>();
    }

    private static class Float3ImageBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public Float3ImageBuffer(int size, int samples) {
            this.sampleSize = 3;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new float[size * this.lineSize];
        }

        public void write(Vector3f vec, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.x();
            this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.y();
            this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = vec.z();
        }

        public void read(Vector3f vec, int x, int y, int sample) {
            vec.set(
                    this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[1 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)],
                    this.data[2 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)]
            );
        }
    }

    private static class IntegerImageBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final int[] data;

        public IntegerImageBuffer(int size, int samples) {
            this.sampleSize = 1;
            this.vectorSize = this.sampleSize * samples;
            this.lineSize = size * this.vectorSize;
            this.data = new int[size * this.lineSize];
        }

        public void write(int data, int x, int y, int sample) {
            this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)] = data;
        }

        public int read(int x, int y, int sample) {
            return this.data[0 + (sample * this.sampleSize) + (x * this.vectorSize) + (y * this.lineSize)];
        }
    }

    public static volatile int NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors();
    public static final int IGNORE_TRIGGER = 32;
    
    private static final int EMPTY = 0;
    private static final int FILLED = 1;
    private static final int IGNORE_SHADOW = 2;
    private static final int IGNORE_AMBIENT = 3;
    private static final int IGNORE_SHADOW_AND_AMBIENT = 4;

    //lightmapper geometry/scene state
    private final Scene scene;
    private final int lightmapSize;
    private final Rectanglei[] lightmapRectangles;
    private final float[] opaqueMesh;
    private final float[] alphaMesh;
    private final BVH opaqueBVH;
    private final BVH alphaBVH;
    private final float[] mesh;
    private final LightGroup[] lightGroups;

    //lightmapper lightmaps
    private final Lightmap[] lightmaps;

    //mesh buffers
    private final Float3ImageBuffer weights;
    private final IntegerImageBuffer triangles;
    private final IntegerImageBuffer samples;

    //threads
    private ExecutorService service = null;

    public Lightmapper(
            Scene scene,
            int lightmapSize,
            Collection<Rectanglei> lightmapRectangles,
            float[] opaqueMesh,
            float[] alphaMesh
    ) {
        this.scene = scene;
        this.lightmapSize = lightmapSize;
        this.lightmapRectangles = lightmapRectangles.toArray(Rectanglei[]::new);
        this.opaqueMesh = validate(opaqueMesh);
        this.alphaMesh = validate(alphaMesh);

        int[] opaqueIndices = new int[this.opaqueMesh.length / VERTEX_SIZE];
        for (int i = 0; i < opaqueIndices.length; i++) {
            opaqueIndices[i] = i;
        }

        int[] alphaIndices = new int[this.alphaMesh.length / VERTEX_SIZE];
        for (int i = 0; i < alphaIndices.length; i++) {
            alphaIndices[i] = i;
        }

        this.opaqueBVH = BVH.create(this.opaqueMesh, opaqueIndices, VERTEX_SIZE, OFFSET_POSITION_XYZ);
        this.alphaBVH = BVH.create(this.alphaMesh, alphaIndices, VERTEX_SIZE, OFFSET_POSITION_XYZ);

        this.mesh = new float[this.opaqueMesh.length + this.alphaMesh.length];
        System.arraycopy(this.opaqueMesh, 0, this.mesh, 0, this.opaqueMesh.length);
        System.arraycopy(this.alphaMesh, 0, this.mesh, this.opaqueMesh.length, this.alphaMesh.length);

        List<LightGroup> groups = new ArrayList<>();
        for (Scene.Light light : scene.getLights()) {
            LightGroup group = null;
            for (LightGroup g : groups) {
                if (g.groupName.equals(light.getGroupName())) {
                    group = g;
                    break;
                }
            }
            if (group == null) {
                group = new LightGroup();
                group.groupName = light.getGroupName();
                groups.add(group);
            }
            group.lights.add(light);
        }
        this.lightGroups = groups.toArray(LightGroup[]::new);

        this.lightmaps = new Lightmap[this.lightGroups.length];

        this.weights = new Float3ImageBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());
        this.triangles = new IntegerImageBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());
        this.samples = new IntegerImageBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());
    }

    private int clamp(int v, int min, int max) {
        if (v > max) {
            return max;
        }
        if (v < min) {
            return min;
        }
        return v;
    }
    
    private class RectangleBVH {
        Rectanglei rectangle;
        RectangleBVH left;
        RectangleBVH right;
    }
    
    private RectangleBVH buildRectangleBVH() {
        RectangleBVH[] current = new RectangleBVH[this.lightmapRectangles.length];
        int currentLength = current.length;
        
        for (int i = 0; i < current.length; i++) {
            RectangleBVH bvh = new RectangleBVH();
            bvh.rectangle = this.lightmapRectangles[i];
            current[i] = bvh;
        }
        
        RectangleBVH[] next = new RectangleBVH[current.length];
        int nextIndex = 0;

        while (currentLength != 1) {
            for (int i = 0; i < currentLength; i++) {
                RectangleBVH bvh = current[i];
                
                if (bvh == null) {
                    continue;
                }

                int minX = bvh.rectangle.minX;
                int minY = bvh.rectangle.minY;
                int maxX = bvh.rectangle.maxX;
                int maxY = bvh.rectangle.maxY;

                float centerX = ((minX + 0.5f) * 0.5f) + ((maxX + 0.5f) * 0.5f);
                float centerY = ((minY + 0.5f) * 0.5f) + ((maxY + 0.5f) * 0.5f);

                RectangleBVH closest = null;
                float closestSquaredDistance = 0f;
                int closestIndex = 0;
                for (int j = 0; j < currentLength; j++) {
                    RectangleBVH other = current[j];
                    
                    if (i == j) {
                        continue;
                    }

                    if (other == null) {
                        continue;
                    }

                    float otherCenterX = ((other.rectangle.minX + 0.5f) * 0.5f) + ((other.rectangle.maxX + 0.5f) * 0.5f);
                    float otherCenterY = ((other.rectangle.minY + 0.5f) * 0.5f) + ((other.rectangle.maxY + 0.5f) * 0.5f);
                    
                    float dX = centerX - otherCenterX;
                    float dY = centerY - otherCenterY;

                    float squaredDist = (dX * dX) + (dY * dY);

                    if (squaredDist < closestSquaredDistance || closest == null) {
                        closest = other;
                        closestSquaredDistance = squaredDist;
                        closestIndex = j;
                    }
                }

                current[i] = null;

                if (closest == null) {
                    next[nextIndex++] = bvh;
                    continue;
                }

                current[closestIndex] = null;

                RectangleBVH merge = new RectangleBVH();
                merge.left = bvh;
                merge.right = closest;
                merge.rectangle = new Rectanglei(bvh.rectangle).union(closest.rectangle);
                next[nextIndex++] = merge;
            }

            RectangleBVH[] currentStore = current;

            current = next;
            next = currentStore;

            currentLength = nextIndex;
            nextIndex = 0;
        }

        return current[0];
    }
    
    private RectangleBVH rectangleOfPixel(RectangleBVH root, int x, int y) {
        if (root == null) {
            return null;
        }
        
        if (root.rectangle.containsPoint(x, y)) {
            RectangleBVH left = rectangleOfPixel(root.left, x, y);
            RectangleBVH right = rectangleOfPixel(root.right, x, y);
            
            if (left != null && right != null) {
                int minLeft = Math.min(left.left.rectangle.lengthX(), left.left.rectangle.lengthY());
                int minRight = Math.min(left.right.rectangle.lengthX(), left.right.rectangle.lengthY());
                if (minLeft < minRight) {
                    return left;
                } else {
                    return right;
                }
            }
            
            if (left != null) {
                return left;
            } else if (right != null) {
                return right;
            } else {
                return root;
            }
        }
        
        return null;
    }
    
    private void rasterize() {
        RectangleBVH rectangleBvh = buildRectangleBVH();
        
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();
        
        for (int i = 0; i < this.mesh.length; i += VERTEX_SIZE * 3) {
            float v0x = this.mesh[i + (VERTEX_SIZE * 0) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v0y = this.mesh[i + (VERTEX_SIZE * 0) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            float v1x = this.mesh[i + (VERTEX_SIZE * 1) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v1y = this.mesh[i + (VERTEX_SIZE * 1) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            float v2x = this.mesh[i + (VERTEX_SIZE * 2) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v2y = this.mesh[i + (VERTEX_SIZE * 2) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            a.set(v0x, v0y, 0f);
            b.set(v1x, v1y, 0f);
            c.set(v2x, v2y, 0f);
            
            int minX = (int) Math.floor(Math.min(v0x, Math.min(v1x, v2x))) - 1;
            int minY = (int) Math.floor(Math.min(v0y, Math.min(v1y, v2y))) - 1;
            int maxX = (int) Math.ceil(Math.max(v0x, Math.max(v1x, v2x))) + 1;
            int maxY = (int) Math.ceil(Math.max(v0y, Math.max(v1y, v2y))) + 1;

            minX = clamp(minX, 0, this.lightmapSize - 1);
            minY = clamp(minY, 0, this.lightmapSize - 1);
            maxX = clamp(maxX, 0, this.lightmapSize - 1);
            maxY = clamp(maxY, 0, this.lightmapSize - 1);
            
            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    RectangleBVH rect = rectangleOfPixel(rectangleBvh, x, y);
                    boolean ignoreEnabled = rect != null && Math.min(rect.rectangle.lengthX(), rect.rectangle.lengthY()) >= IGNORE_TRIGGER;
                    
                    
                }
            }
        }
    }

    public Lightmap[] bake() {
        this.service = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        try {

            return this.lightmaps;
        } finally {
            this.service.shutdownNow();
        }
    }

}
