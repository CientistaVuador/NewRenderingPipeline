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
import cientistavuador.newrenderingpipeline.util.RasterUtils;
import cientistavuador.newrenderingpipeline.util.postprocess.GaussianBlur;
import cientistavuador.newrenderingpipeline.util.postprocess.MarginAutomata;
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import cientistavuador.newrenderingpipeline.util.raycast.LocalRayResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.primitives.Rectanglei;

/**
 *
 * @author Cien
 */
public class Lightmapper {

    public static interface TextureInput {

        public void textureColor(float[] mesh, float u, float v, int triangle, boolean emissive, Vector4f outputColor);
    }

    public static final float EPSILON = 0.0001f;
    public static final int DEFAULT_GAUSSIAN_BLUR_KERNEL_SIZE = 51;

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

    private static class Float3Buffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final float[] data;

        public Float3Buffer(int size, int samples) {
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

        public float[] getData() {
            return data;
        }

    }

    private static class Float3ImageBuffer extends Float3Buffer {

        public Float3ImageBuffer(int size) {
            super(size, 1);
        }

        public void write(Vector3f vec, int x, int y) {
            this.write(vec, x, y, 0);
        }

        public void read(Vector3f vec, int x, int y) {
            this.read(vec, x, y, 0);
        }

    }

    private static class IntegerBuffer {

        private final int lineSize;
        private final int sampleSize;
        private final int vectorSize;
        private final int[] data;

        public IntegerBuffer(int size, int samples) {
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

    public static volatile int NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    public static final int IGNORE_TRIGGER_SIZE = 32;

    private static final int EMPTY = 0;
    private static final int FILLED = 0b00000001;
    private static final int IGNORE_SHADOW = 0b00000010;
    private static final int IGNORE_AMBIENT = 0b00000100;

    //lightmapper geometry/scene state
    private final TextureInput textureInput;
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

    //barycentric buffers
    private final Float3Buffer weights;
    private final IntegerBuffer triangles;
    private final IntegerBuffer sampleStates;

    //threads
    private int numberOfThreads;
    private ExecutorService service;

    //light group
    private LightGroup group;
    private int groupIndex;

    //lightmap
    private Float3ImageBuffer lightmap;
    private Float3ImageBuffer lightmapIndirect;

    //light buffers
    private Scene.Light light;
    private Float3ImageBuffer direct;
    private Float3ImageBuffer shadow;

    public Lightmapper(
            TextureInput textureIO,
            Scene scene,
            int lightmapSize,
            Rectanglei[] lightmapRectangles,
            float[] opaqueMesh,
            float[] alphaMesh
    ) {
        this.textureInput = textureIO;
        this.scene = scene;
        this.lightmapSize = lightmapSize;
        this.lightmapRectangles = lightmapRectangles;
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

        this.weights = new Float3Buffer(lightmapSize, this.scene.getSamplingMode().numSamples());
        this.triangles = new IntegerBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());
        this.sampleStates = new IntegerBuffer(lightmapSize, this.scene.getSamplingMode().numSamples());
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

    private float lerp(Vector3fc weights, int triangle, int offset) {
        float va  = this.mesh[triangle + (VERTEX_SIZE * 0) + offset];
        float vb = this.mesh[triangle + (VERTEX_SIZE * 1) + offset];
        float vc = this.mesh[triangle + (VERTEX_SIZE * 2) + offset];
        return (va  * weights.x()) + (vb * weights.y()) + (vc * weights.z());
    }

    private void rasterizeBarycentricBuffers() {
        Vector3f samplePosition = new Vector3f();

        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        Vector3f c = new Vector3f();

        Vector3f sampleWeights = new Vector3f();

        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();

        SamplingMode mode = this.scene.getSamplingMode();

        for (int triangle = 0; triangle < this.mesh.length; triangle += VERTEX_SIZE * 3) {
            normal.set(
                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
            );

            float v0x = this.mesh[triangle + (VERTEX_SIZE * 0) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v0y = this.mesh[triangle + (VERTEX_SIZE * 0) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            float v1x = this.mesh[triangle + (VERTEX_SIZE * 1) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v1y = this.mesh[triangle + (VERTEX_SIZE * 1) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

            float v2x = this.mesh[triangle + (VERTEX_SIZE * 2) + OFFSET_LIGHTMAP_XY + 0] * this.lightmapSize;
            float v2y = this.mesh[triangle + (VERTEX_SIZE * 2) + OFFSET_LIGHTMAP_XY + 1] * this.lightmapSize;

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

            boolean ignoreEnabled = true;

            Rectanglei rect = new Rectanglei(minX, minY, maxX, maxY);
            for (Rectanglei other : this.lightmapRectangles) {
                if (other.intersectsRectangle(rect)) {
                    int minSize = Math.min(other.lengthX(), other.lengthY());
                    if (minSize < IGNORE_TRIGGER_SIZE) {
                        ignoreEnabled = false;
                        break;
                    }
                }
            }

            raster:
            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    int sampleState = FILLED;

                    if (ignoreEnabled) {
                        if (!((x % 2 == 0 && y % 2 == 0) || (x % 2 != 0 && y % 2 != 0))) {
                            sampleState |= IGNORE_SHADOW;
                        }
                        if (!((y % 2 == 0) && ((((y / 2) % 2 == 0 && x % 2 == 0)) || (((y / 2) % 2 != 0 && x % 2 != 0))))) {
                            sampleState |= IGNORE_AMBIENT;
                        }
                    }

                    for (int s = 0; s < mode.numSamples(); s++) {
                        float sampleX = mode.sampleX(s);
                        float sampleY = mode.sampleY(s);

                        samplePosition.set(x + sampleX, y + sampleY, 0f);

                        RasterUtils.barycentricWeights(samplePosition, a, b, c, sampleWeights);

                        float wx = sampleWeights.x();
                        float wy = sampleWeights.y();
                        float wz = sampleWeights.z();

                        if (!Float.isFinite(wx) || !Float.isFinite(wy) || !Float.isFinite(wz)) {
                            break raster;
                        }

                        if (wx < 0f || wy < 0f || wz < 0f) {
                            continue;
                        }

                        position.set(
                                lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                        ).add(
                                normal.x() * this.scene.getRayOffset(),
                                normal.y() * this.scene.getRayOffset(),
                                normal.z() * this.scene.getRayOffset()
                        );

                        if (this.opaqueBVH.testSphere(
                                position.x(), position.y(), position.z(),
                                this.scene.getRayOffset() * 0.5f
                        )) {
                            continue;
                        }

                        this.weights.write(sampleWeights, x, y, s);
                        this.triangles.write(triangle, x, y, s);
                        this.sampleStates.write(sampleState, x, y, s);
                    }
                }
            }
        }
    }

    private void prepareLightmap(int index) {
        this.group = this.lightGroups[index];
        this.groupIndex = index;

        this.lightmap = new Float3ImageBuffer(this.lightmapSize);
        this.lightmapIndirect = new Float3ImageBuffer(this.lightmapSize);
    }

    private void prepareLight(Scene.Light light) {
        this.light = light;

        this.direct = new Float3ImageBuffer(this.lightmapSize);
        this.shadow = new Float3ImageBuffer(this.lightmapSize);
    }

    private void bakeDirect() {
        for (int i = 0; i < this.lightmapSize; i += this.numberOfThreads) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int j = 0; j < this.numberOfThreads; j++) {
                final int y = i + j;
                if (y >= this.lightmapSize) {
                    break;
                }
                tasks.add(this.service.submit(() -> {
                    Vector3f totalColor = new Vector3f();

                    Vector3f sampleWeights = new Vector3f();
                    Vector3f position = new Vector3f();
                    Vector3f normal = new Vector3f();

                    Vector3f outLightDirection = new Vector3f();
                    Vector3f outLightDirectColor = new Vector3f();

                    int numSamples = this.scene.getSamplingMode().numSamples();
                    for (int x = 0; x < this.lightmapSize; x++) {
                        totalColor.zero();
                        int samplesPassed = 0;
                        for (int s = 0; s < numSamples; s++) {
                            int sampleState = this.sampleStates.read(x, y, s);
                            if ((sampleState & FILLED) == 0) {
                                continue;
                            }

                            this.weights.read(sampleWeights, x, y, s);
                            int triangle = this.triangles.read(x, y, s);

                            position.set(
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                            );
                            normal.set(
                                    lerp(sampleWeights, triangle, OFFSET_NORMAL_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_NORMAL_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_NORMAL_XYZ + 2)
                            ).normalize();

                            this.light.calculateDirect(
                                    position, normal,
                                    outLightDirection, outLightDirectColor,
                                    this.scene.getDirectLightingAttenuation()
                            );

                            totalColor.add(outLightDirectColor);
                            samplesPassed++;
                        }
                        if (samplesPassed != 0) {
                            totalColor.div(samplesPassed);
                        }
                        this.direct.write(totalColor, x, y);
                    }
                }));
            }

            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }
            tasks.clear();
        }
    }

    private void randomDirection(
            Vector3fc normal,
            Vector3f worldUp, Vector3f tangent, Vector3f bitangent, Matrix3f tbn,
            Vector3f outDirection
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

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

        worldUp.set(0f, 1f, 0f);

        if (Math.abs(normal.dot(worldUp)) >= (1f - EPSILON)) {
            worldUp.set(1f, 0f, 0f);
        }

        normal.cross(worldUp, tangent).normalize();
        normal.cross(tangent, bitangent).normalize();

        tbn.set(tangent, bitangent, normal).transform(outDirection);
    }

    private Vector3f shadowBlend(Vector3fc position, Vector3fc direction, float length) {
        List<LocalRayResult> alphaResults = this.alphaBVH.testRaySorted(position, direction, true);
        
        if (alphaResults.isEmpty()) {
            return null;
        }
        
        Vector3f rayWeights = new Vector3f();
        
        List<Vector4f> colors = new ArrayList<>();
        for (LocalRayResult ray : alphaResults) {
            if (Float.isFinite(length) && ray.getLocalDistance() > length) {
                break;
            }
            
            ray.weights(rayWeights);
            
            float u = ray.lerp(rayWeights, OFFSET_TEXTURE_XY + 0);
            float v = ray.lerp(rayWeights, OFFSET_TEXTURE_XY + 1);
            
            Vector4f textureColor = new Vector4f();
            this.textureInput.textureColor(
                    this.alphaMesh,
                    u, v, ray.triangle() * 3 * VERTEX_SIZE,
                    false, textureColor
            );
            
            colors.add(textureColor);
        }
        
        if (colors.isEmpty()) {
            return null;
        }
        
        Vector3f finalShadowColor = new Vector3f(1f);
        for (int l = 0; l < colors.size(); l++) {
            Vector4f color = colors.get(l);

            if (l == 0) {
                finalShadowColor.set(
                        color.x() * color.w(),
                        color.y() * color.w(),
                        color.z() * color.w()
                ).add(
                        1f - color.w(),
                        1f - color.w(),
                        1f - color.w()
                );
                continue;
            }

            finalShadowColor
                    .mul(1f - color.w())
                    .add(
                            color.x() * color.w(),
                            color.y() * color.w(),
                            color.z() * color.w()
                    );
        }
        
        return finalShadowColor;
    }

    private void bakeShadow() {
        for (int i = 0; i < this.lightmapSize; i += this.numberOfThreads) {
            List<Future<?>> tasks = new ArrayList<>();

            for (int j = 0; j < this.numberOfThreads; j++) {
                final int y = i + j;
                if (y >= this.lightmapSize) {
                    break;
                }
                tasks.add(this.service.submit(() -> {
                    Vector3f totalShadow = new Vector3f();

                    Vector3f sampleWeights = new Vector3f();
                    Vector3f position = new Vector3f();
                    Vector3f normal = new Vector3f();

                    Vector3f outLightDirection = new Vector3f();

                    Vector3f worldUp = new Vector3f();

                    Vector3f tangent = new Vector3f();
                    Vector3f bitangent = new Vector3f();
                    Matrix3f tbn = new Matrix3f();

                    Vector3f rayWeights = new Vector3f();

                    Vector4f emissiveColor = new Vector4f();

                    int numSamples = this.scene.getSamplingMode().numSamples();
                    for (int x = 0; x < this.lightmapSize; x++) {
                        totalShadow.zero();
                        int samplesPassed = 0;
                        for (int s = 0; s < numSamples; s++) {
                            int sampleState = this.sampleStates.read(x, y, s);
                            if ((sampleState & FILLED) == 0 || (sampleState & IGNORE_SHADOW) != 0) {
                                continue;
                            }

                            this.weights.read(sampleWeights, x, y, s);
                            int triangle = this.triangles.read(x, y, s);

                            position.set(
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 0),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 1),
                                    lerp(sampleWeights, triangle, OFFSET_POSITION_XYZ + 2)
                            );
                            normal.set(
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 0],
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 1],
                                    this.mesh[triangle + OFFSET_TRIANGLE_NORMAL_XYZ + 2]
                            );

                            position.add(
                                    normal.x() * this.scene.getRayOffset(),
                                    normal.y() * this.scene.getRayOffset(),
                                    normal.z() * this.scene.getRayOffset()
                            );

                            if (this.light instanceof Scene.EmissiveLight emissiveLight) {
                                for (int k = 0; k < emissiveLight.getEmissiveRays(); k++) {
                                    randomDirection(normal, worldUp, tangent, bitangent, tbn, outLightDirection);

                                    List<LocalRayResult> results = this.opaqueBVH.testRaySorted(position, outLightDirection, true);
                                    if (!results.isEmpty()) {
                                        LocalRayResult closest = results.get(0);
                                        closest.weights(rayWeights);

                                        float u = closest.lerp(rayWeights, OFFSET_TEXTURE_XY + 0);
                                        float v = closest.lerp(rayWeights, OFFSET_TEXTURE_XY + 1);

                                        this.textureInput.textureColor(
                                                this.opaqueMesh,
                                                u, v, closest.triangle() * 3 * VERTEX_SIZE,
                                                true, emissiveColor
                                        );
                                        
                                        if (emissiveColor.x() != 0f || emissiveColor.y() != 0f || emissiveColor.z() != 0f) {
                                            Vector3f blend = shadowBlend(position, outLightDirection, closest.getLocalDistance());
                                            if (blend != null) {
                                                emissiveColor.mul(
                                                        blend.x(),
                                                        blend.y(),
                                                        blend.z(),
                                                        1f
                                                );
                                            }
                                            totalShadow.add(emissiveColor.x(), emissiveColor.y(), emissiveColor.z());
                                        }
                                    }
                                }
                                samplesPassed += emissiveLight.getEmissiveRays();
                            } else {
                                for (int k = 0; k < this.scene.getShadowRaysPerSample(); k++) {
                                    this.light.randomLightDirection(position, outLightDirection);
                                    float length = outLightDirection.length();
                                    outLightDirection.div(length);

                                    if (this.light instanceof Scene.DirectionalLight) {
                                        length = Float.POSITIVE_INFINITY;
                                    }

                                    if (!this.opaqueBVH.fastTestRay(position, outLightDirection, length)) {
                                        Vector3f blend = shadowBlend(position, outLightDirection, length);
                                        if (blend != null) {
                                            totalShadow.add(blend);
                                        } else {
                                            totalShadow.add(1f, 1f, 1f);
                                        }
                                    }
                                }
                                samplesPassed += this.scene.getShadowRaysPerSample();
                            }
                        }
                        if (samplesPassed != 0) {
                            totalShadow.div(samplesPassed);
                        }
                        this.shadow.write(totalShadow, x, y);
                    }
                }));
            }

            for (Future<?> t : tasks) {
                try {
                    t.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }
            tasks.clear();
        }
    }

    private MarginAutomata.MarginAutomataIO createAutomataIO(
            final Rectanglei rectangle,
            final Float3ImageBuffer buffer,
            final int ignoreFlag
    ) {
        MarginAutomata.MarginAutomataIO marginIO = new MarginAutomata.MarginAutomataIO() {
            @Override
            public int width() {
                return rectangle.lengthX();
            }

            @Override
            public int height() {
                return rectangle.lengthY();
            }

            @Override
            public boolean empty(int x, int y) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                boolean empty = true;
                int numSamples = Lightmapper.this.scene.getSamplingMode().numSamples();
                for (int s = 0; s < numSamples; s++) {
                    int state = Lightmapper.this.sampleStates.read(absX, absY, s);
                    if ((state & Lightmapper.FILLED) != 0 && (state & ignoreFlag) == 0) {
                        empty = false;
                        break;
                    }
                }

                return empty;
            }

            final Vector3f colorVector = new Vector3f();

            @Override
            public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                buffer.read(this.colorVector, absX, absY);
                color.r = this.colorVector.x();
                color.g = this.colorVector.y();
                color.b = this.colorVector.z();
            }

            @Override
            public void write(int x, int y, MarginAutomata.MarginAutomataColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                this.colorVector.set(color.r, color.g, color.b);
                buffer.write(this.colorVector, absX, absY);
            }
        };

        return marginIO;
    }

    private void generateDirectMargins() {
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.direct, Lightmapper.EMPTY
            );
            MarginAutomata.generateMargin(io, -1);
        }
    }

    private void generateShadowMargins() {
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            MarginAutomata.MarginAutomataIO io = createAutomataIO(
                    this.lightmapRectangles[i], this.shadow, Lightmapper.IGNORE_SHADOW
            );
            MarginAutomata.generateMargin(io, -1);
        }
    }

    private GaussianBlur.GaussianIO createGaussianIO(
            final Rectanglei rectangle,
            final Float3ImageBuffer buffer
    ) {
        GaussianBlur.GaussianIO gaussianIO = new GaussianBlur.GaussianIO() {
            @Override
            public int width() {
                return rectangle.lengthX();
            }

            @Override
            public int height() {
                return rectangle.lengthY();
            }

            final Vector3f colorVector = new Vector3f();

            @Override
            public void read(int x, int y, GaussianBlur.GaussianColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                buffer.read(this.colorVector, absX, absY);
                color.r = this.colorVector.x();
                color.g = this.colorVector.y();
                color.b = this.colorVector.z();
            }

            @Override
            public void write(int x, int y, GaussianBlur.GaussianColor color) {
                int absX = x + rectangle.minX;
                int absY = y + rectangle.minY;

                this.colorVector.set(color.r, color.g, color.b);
                buffer.write(this.colorVector, absX, absY);
            }
        };

        return gaussianIO;
    }

    private void denoiseShadow() {
        float blurArea = this.scene.getShadowBlurArea();
        if (this.light instanceof Scene.EmissiveLight emissiveLight) {
            blurArea = emissiveLight.getEmissiveBlurArea();
        }
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            GaussianBlur.GaussianIO io = createGaussianIO(this.lightmapRectangles[i], this.shadow);
            GaussianBlur.blur(io, DEFAULT_GAUSSIAN_BLUR_KERNEL_SIZE, blurArea);
        }
    }

    private void outputLight() {
        Vector3f directLight = new Vector3f();
        Vector3f shadowLight = new Vector3f();

        Vector3f resultLight = new Vector3f();
        Vector3f currentLight = new Vector3f();

        for (int y = 0; y < this.lightmapSize; y++) {
            for (int x = 0; x < this.lightmapSize; x++) {
                this.direct.read(directLight, x, y);
                this.shadow.read(shadowLight, x, y);
                this.lightmap.read(currentLight, x, y);

                resultLight.set(directLight).mul(shadowLight).add(currentLight);

                this.lightmap.write(resultLight, x, y);
            }
        }

        this.direct = null;
        this.shadow = null;
    }

    private void bakeIndirect() {

    }

    private void generateIndirectMargins() {

    }

    private void denoiseIndirect() {

    }

    private void outputIndirect() {
        Vector3f directLight = new Vector3f();
        Vector3f indirectLight = new Vector3f();

        for (int y = 0; y < this.lightmapSize; y++) {
            for (int x = 0; x < this.lightmapSize; x++) {
                this.lightmap.read(directLight, x, y);
                this.lightmapIndirect.read(indirectLight, x, y);

                directLight.add(indirectLight);

                this.lightmap.write(directLight, x, y);
            }
        }

        this.lightmapIndirect = null;
    }

    private void outputLightmap() {
        Lightmap m = new Lightmap(this.group.groupName, this.lightmapSize, this.lightmap.getData());
        this.lightmaps[this.groupIndex] = m;

        this.lightmap = null;
    }

    public Lightmap[] bake() {
        this.numberOfThreads = NUMBER_OF_THREADS;
        this.service = Executors.newFixedThreadPool(this.numberOfThreads);
        try {
            rasterizeBarycentricBuffers();
            for (int i = 0; i < this.lightGroups.length; i++) {
                prepareLightmap(i);

                for (Scene.Light l : this.group.lights) {
                    prepareLight(l);

                    bakeDirect();
                    bakeShadow();

                    generateDirectMargins();
                    generateShadowMargins();

                    denoiseShadow();

                    outputLight();
                }

                bakeIndirect();
                generateIndirectMargins();
                denoiseIndirect();
                outputIndirect();

                outputLightmap();
            }
            return this.lightmaps;
        } finally {
            this.service.shutdownNow();
        }
    }

}
