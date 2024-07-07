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
package cientistavuador.newrenderingpipeline.newrendering;

import cientistavuador.newrenderingpipeline.util.bakedlighting.AmbientCube;
import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.util.ColorUtils;
import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.Pair;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCube;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapUVs;
import cientistavuador.newrenderingpipeline.util.bakedlighting.Lightmapper;
import cientistavuador.newrenderingpipeline.util.bakedlighting.Scene;
import cientistavuador.newrenderingpipeline.util.raycast.LocalRayResult;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.joml.primitives.Rectanglei;

/**
 *
 * @author Cien
 */
public class NMap {

    public static Scene.Light convertLight(NLight light) {
        if (light == null) {
            return null;
        }
        if (light instanceof NLight.NDirectionalLight directional) {
            Scene.DirectionalLight sceneDirectional = new Scene.DirectionalLight();
            sceneDirectional.setGroupName(directional.getGroupName());
            sceneDirectional.setLightSize(directional.getLightSize());
            sceneDirectional.setDiffuse(directional.getDiffuse());
            sceneDirectional.setAmbient(directional.getAmbient());
            sceneDirectional.setDirection(directional.getDirection());
            return sceneDirectional;
        }
        if (light instanceof NLight.NPointLight point) {
            Scene.PointLight scenePoint = new Scene.PointLight();
            scenePoint.setGroupName(point.getGroupName());
            scenePoint.setLightSize(point.getLightSize());
            scenePoint.setDiffuse(point.getDiffuse());
            scenePoint.setPosition(
                    (float) point.getPosition().x(),
                    (float) point.getPosition().y(),
                    (float) point.getPosition().z()
            );
            return scenePoint;
        }
        if (light instanceof NLight.NSpotLight spot) {
            Scene.SpotLight sceneSpot = new Scene.SpotLight();
            sceneSpot.setGroupName(spot.getGroupName());
            sceneSpot.setLightSize(spot.getLightSize());
            sceneSpot.setDiffuse(spot.getDiffuse());
            sceneSpot.setPosition(
                    (float) spot.getPosition().x(),
                    (float) spot.getPosition().y(),
                    (float) spot.getPosition().z()
            );
            sceneSpot.setDirection(spot.getDirection());
            sceneSpot.setInnerCutoff(spot.getInnerCone());
            sceneSpot.setOuterCutoff(spot.getOuterCone());
            return sceneSpot;
        }
        return null;
    }

    public static class BakeStatus {

        private final Future<Void> task;
        private Lightmapper lightmapper;

        public BakeStatus(Future<Void> task) {
            this.task = task;
        }

        protected Lightmapper getLightmapper() {
            return lightmapper;
        }

        protected void setLightmapper(Lightmapper lightmapper) {
            this.lightmapper = lightmapper;
        }

        public Future<Void> getTask() {
            return task;
        }

        public String getStatus() {
            if (this.lightmapper == null) {
                return "Waiting for Lightmapper Creation";
            }
            return this.lightmapper.getStatus();
        }

        public double getRaysPerSecond() {
            if (this.lightmapper == null) {
                return 0.0;
            }
            return this.lightmapper.getRaysPerSecond();
        }

        public double getProgress() {
            if (this.lightmapper == null) {
                return 0.0;
            }
            return this.lightmapper.getProgress();
        }
    }

    public static final int DEFAULT_LIGHTMAP_MARGIN = 5;

    public static String mapObjectPrefix(String mapName, int objectIndex, String objectName) {
        return "map_" + mapName + "_" + objectIndex + "_" + objectName;
    }

    private final String name;
    private final N3DObject[] objects;
    private final MeshCollisionShape meshCollision;

    private final int lightmapMargin;
    private final float lightmapPixelToWorldRatio;
    private final int lightmapSize;
    private final Rectanglei[] lightmapRectangles;

    private NLightmaps lightmaps = null;

    public NMap(String name, Collection<N3DObject> objects, int lightmapMargin, float lightmapPixelToWorldRatio) {
        this.name = name;
        this.lightmapMargin = lightmapMargin;
        this.lightmapPixelToWorldRatio = lightmapPixelToWorldRatio;

        float[] transformedVertices = new float[NMesh.VERTEX_SIZE * 64];
        int transformedVerticesIndex = 0;

        class GeometryOffset {

            NGeometry geometry;
            int offset;
            int length;

            GeometryOffset(NGeometry geometry, int offset, int length) {
                this.geometry = geometry;
                this.offset = offset;
                this.length = length;
            }
        }

        class ObjectGeometries {

            N3DObject object;
            List<GeometryOffset> offsets;
        }

        List<ObjectGeometries> objectsGeometries = new ArrayList<>();

        Matrix4f modelMatrix = new Matrix4f();

        Matrix4f transformation = new Matrix4f();
        Matrix3f transformationNormal = new Matrix3f();

        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        Vector3f tangent = new Vector3f();

        for (N3DObject obj : objects) {
            ObjectGeometries objectGeometries = new ObjectGeometries();
            objectGeometries.object = obj;
            objectGeometries.offsets = new ArrayList<>();

            obj.calculateModelMatrix(modelMatrix, null);

            N3DModel model = obj.getN3DModel();
            for (int i = 0; i < model.getNumberOfGeometries(); i++) {
                NGeometry geometry = model.getGeometry(i);
                NMesh mesh = geometry.getMesh();

                transformation
                        .set(modelMatrix)
                        .mul(geometry.getParent().getToRootSpace());
                transformation.normal(transformationNormal);

                float[] unindexed = MeshUtils.unindex(mesh.getVertices(), mesh.getIndices(), NMesh.VERTEX_SIZE).getA();

                for (int v = 0; v < unindexed.length; v += NMesh.VERTEX_SIZE) {
                    position.set(
                            unindexed[v + NMesh.OFFSET_POSITION_XYZ + 0],
                            unindexed[v + NMesh.OFFSET_POSITION_XYZ + 1],
                            unindexed[v + NMesh.OFFSET_POSITION_XYZ + 2]
                    );
                    normal.set(
                            unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 0],
                            unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 1],
                            unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 2]
                    );
                    tangent.set(
                            unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 0],
                            unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 1],
                            unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 2]
                    );

                    transformation.transformProject(position);
                    transformationNormal.transform(normal);
                    transformationNormal.transform(tangent);

                    unindexed[v + NMesh.OFFSET_POSITION_XYZ + 0] = position.x();
                    unindexed[v + NMesh.OFFSET_POSITION_XYZ + 1] = position.y();
                    unindexed[v + NMesh.OFFSET_POSITION_XYZ + 2] = position.z();
                    unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 0] = normal.x();
                    unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 1] = normal.y();
                    unindexed[v + NMesh.OFFSET_NORMAL_XYZ + 2] = normal.z();
                    unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 0] = tangent.x();
                    unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 1] = tangent.y();
                    unindexed[v + NMesh.OFFSET_TANGENT_XYZ + 2] = tangent.z();
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 0] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 1] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 2] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_IDS_XYZW + 3] = Float.intBitsToFloat(-1);
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 0] = 1f;
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 1] = 0f;
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 2] = 0f;
                    unindexed[v + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 3] = 0f;
                }

                if ((transformedVertices.length - transformedVerticesIndex) < unindexed.length) {
                    transformedVertices = Arrays.copyOf(transformedVertices, (transformedVertices.length * 2) + unindexed.length);
                }

                System.arraycopy(
                        unindexed, 0,
                        transformedVertices, transformedVerticesIndex, unindexed.length
                );
                objectGeometries.offsets.add(new GeometryOffset(geometry, transformedVerticesIndex, unindexed.length));
                transformedVerticesIndex += unindexed.length;
            }

            objectsGeometries.add(objectGeometries);
        }
        transformedVertices = Arrays.copyOf(transformedVertices, transformedVerticesIndex);
        
        LightmapUVs.GeneratorOutput output = MeshUtils.generateLightmapUVs(
                transformedVertices, NMesh.VERTEX_SIZE, NMesh.OFFSET_POSITION_XYZ,
                this.lightmapMargin, this.lightmapPixelToWorldRatio, 1f, 1f, 1f
        );

        this.lightmapSize = output.getLightmapSize();

        LightmapUVs.LightmapperQuad[] quads = output.getQuads();
        this.lightmapRectangles = new Rectanglei[quads.length];
        for (int i = 0; i < this.lightmapRectangles.length; i++) {
            LightmapUVs.LightmapperQuad quad = quads[i];
            this.lightmapRectangles[i] = new Rectanglei(
                    quad.getX(), quad.getY(),
                    quad.getX() + quad.getWidth(), quad.getY() + quad.getHeight()
            );
        }

        float[] uvs = output.getUVs();
        for (int i = 0; i < transformedVertices.length; i += NMesh.VERTEX_SIZE) {
            transformedVertices[i + NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 0] = uvs[((i / NMesh.VERTEX_SIZE) * 2) + 0];
            transformedVertices[i + NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 1] = uvs[((i / NMesh.VERTEX_SIZE) * 2) + 1];
        }
        
        List<N3DObject> resultObjects = new ArrayList<>();

        int objectCounter = 0;
        for (ObjectGeometries obj : objectsGeometries) {
            List<NGeometry> newGeometries = new ArrayList<>();
            int geometryCounter = 0;
            for (GeometryOffset geo : obj.offsets) {
                float[] unindexedVertices = Arrays.copyOfRange(transformedVertices, geo.offset, geo.offset + geo.length);

                Pair<float[], int[]> pair = MeshUtils.generateIndices(unindexedVertices, NMesh.VERTEX_SIZE);

                float[] vertices = pair.getA();
                int[] indices = pair.getB();

                NMesh mesh = new NMesh(geo.geometry.getMesh().getName(), vertices, indices);
                mesh.generateBVH();
                newGeometries.add(new NGeometry(
                        mapObjectPrefix(this.name, geometryCounter, geo.geometry.getName()),
                        mesh,
                        geo.geometry.getMaterial()
                ));
                geometryCounter++;
            }
            N3DObject object = new N3DObject(
                    mapObjectPrefix(this.name, objectCounter, obj.object.getName()),
                    new N3DModel(
                            mapObjectPrefix(this.name, objectCounter, obj.object.getN3DModel().getName()),
                            new N3DModelNode(
                                    "root", null,
                                    newGeometries.toArray(NGeometry[]::new), null
                            )
                    )
            );
            resultObjects.add(object);
            objectCounter++;
        }

        this.objects = resultObjects.toArray(N3DObject[]::new);

        for (int i = 0; i < this.objects.length; i++) {
            this.objects[i].setMap(this);
        }

        float[] collisionTriangles = new float[(transformedVertices.length / NMesh.VERTEX_SIZE) * 3];

        for (int i = 0; i < transformedVertices.length; i += NMesh.VERTEX_SIZE) {
            float x = transformedVertices[i + NMesh.OFFSET_POSITION_XYZ + 0];
            float y = transformedVertices[i + NMesh.OFFSET_POSITION_XYZ + 1];
            float z = transformedVertices[i + NMesh.OFFSET_POSITION_XYZ + 2];

            collisionTriangles[0 + (i / NMesh.VERTEX_SIZE) * 3] = x * Main.TO_PHYSICS_ENGINE_UNITS;
            collisionTriangles[1 + (i / NMesh.VERTEX_SIZE) * 3] = y * Main.TO_PHYSICS_ENGINE_UNITS;
            collisionTriangles[2 + (i / NMesh.VERTEX_SIZE) * 3] = z * Main.TO_PHYSICS_ENGINE_UNITS;
        }

        Pair<float[], int[]> indexedCollision = MeshUtils.generateIndices(collisionTriangles, 3);

        this.meshCollision = new MeshCollisionShape(true, new IndexedMesh(
                BufferUtils.createFloatBuffer(indexedCollision.getA()),
                BufferUtils.createIntBuffer(indexedCollision.getB())
        ));

    }

    public String getName() {
        return name;
    }

    public int getNumberOfObjects() {
        return this.objects.length;
    }

    public N3DObject getObject(int index) {
        return this.objects[index];
    }

    public MeshCollisionShape getMeshCollision() {
        return meshCollision;
    }

    public int getLightmapMargin() {
        return lightmapMargin;
    }

    public float getLightmapPixelToWorldRatio() {
        return lightmapPixelToWorldRatio;
    }

    public int getLightmapSize() {
        return lightmapSize;
    }

    public int getNumberOfLightmapRectangles() {
        return this.lightmapRectangles.length;
    }

    public Rectanglei getLightmapRectangle(int index) {
        return this.lightmapRectangles[index];
    }

    public NLightmaps getLightmaps() {
        return lightmaps;
    }

    public void setLightmaps(NLightmaps lightmaps) {
        this.lightmaps = lightmaps;
        for (N3DObject obj : this.objects) {
            obj.setLightmaps(lightmaps);
        }
    }

    public List<NRayResult> testRay(
            double pX, double pY, double pZ,
            float dX, float dY, float dZ
    ) {
        List<NRayResult> results = new ArrayList<>();

        for (N3DObject obj : this.objects) {
            results.addAll(obj.testRay(pX, pY, pZ, dX, dY, dZ));
        }

        results.sort((o1, o2) -> Double.compare(o1.getDistance(), o2.getDistance()));

        return results;
    }

    public void testShadow(
            double pX, double pY, double pZ,
            float dX, float dY, float dZ,
            double length,
            Vector3f outShadow
    ) {
        outShadow.set(1f);

        List<NRayResult> alphaResults = testRay(pX, pY, pZ, dX, dY, dZ);

        if (alphaResults.isEmpty()) {
            return;
        }

        Vector3f rayWeights = new Vector3f();

        List<Vector4fc> colors = new ArrayList<>();
        for (NRayResult globalRay : alphaResults) {
            if (Double.isFinite(length) && globalRay.getDistance() > length) {
                break;
            }

            LocalRayResult ray = globalRay.getLocalRay();

            ray.weights(rayWeights);

            float lu = ray.lerp(rayWeights, NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 0);
            float lv = ray.lerp(rayWeights, NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 1);

            Vector4f color = new Vector4f(0f, 0f, 0f, 1f);
            if (this.lightmaps != null) {
                this.lightmaps.sampleColorMap(lu, lv, color);
            }
            colors.add(color);
        }

        if (colors.isEmpty()) {
            return;
        }

        Vector4f dest = new Vector4f(0f, 0f, 0f, 0f);
        ColorUtils.blend(colors, dest);

        outShadow.set(dest.x(), dest.y(), dest.z())
                .mul(dest.w())
                .add(1f - dest.w(), 1f - dest.w(), 1f - dest.w())
                .mul(1f - dest.w());
    }

    public void sampleStaticAmbientCube(
            double pX, double pY, double pZ,
            AmbientCube ambientCube
    ) {
        final float epsilon = 0.001f;

        ambientCube.zero();

        if (this.lightmaps == null) {
            return;
        }

        List<LightmapAmbientCube> lightmapAmbientCubes = this.lightmaps.searchStaticAmbientCubes(
                (float) pX, (float) pY, (float) pZ
        );

        if (lightmapAmbientCubes.isEmpty()) {
            return;
        }

        {
            Vector3f direction = new Vector3f();

            List<LightmapAmbientCube> filtered = new ArrayList<>();
            for (LightmapAmbientCube e : lightmapAmbientCubes) {
                direction.set(
                        (float) (e.getPosition().x() - pX),
                        (float) (e.getPosition().y() - pY),
                        (float) (e.getPosition().z() - pZ)
                );
                float distance = direction.length();
                if (distance < epsilon) {
                    filtered.add(e);
                    continue;
                }
                direction.div(distance);

                List<NRayResult> results = testRay(
                        pX, pY, pZ,
                        direction.x(), direction.y(), direction.z()
                );

                if (!results.isEmpty()) {
                    NRayResult closest = results.get(0);
                    if (closest.getDistance() > distance) {
                        filtered.add(e);
                    }
                } else {
                    filtered.add(e);
                }
            }

            lightmapAmbientCubes = filtered;
        }

        if (lightmapAmbientCubes.isEmpty()) {
            return;
        }

        float[] weights = new float[lightmapAmbientCubes.size()];

        if (weights.length > 1) {
            float totalDistance = 0f;

            for (int i = 0; i < weights.length; i++) {
                weights[i] = lightmapAmbientCubes
                        .get(i)
                        .getPosition()
                        .distance((float) pX, (float) pY, (float) pZ);
                totalDistance += weights[i];
            }

            if (totalDistance != 0f) {
                float totalWeight = 0f;
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = 1f - (weights[i] / totalDistance);
                    totalWeight += weights[i];
                }
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = weights[i] / totalWeight;
                }
            } else {
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = 1f;
                }
            }
        } else {
            for (int i = 0; i < weights.length; i++) {
                weights[i] = 1f;
            }
        }

        for (int side = 0; side < AmbientCube.SIDES; side++) {
            float r = 0f;
            float g = 0f;
            float b = 0f;

            for (int i = 0; i < lightmapAmbientCubes.size(); i++) {
                LightmapAmbientCube cube = lightmapAmbientCubes.get(i);
                float weight = weights[i];

                for (int j = 0; j < cube.getNumberOfAmbientCubes(); j++) {
                    float intensity = this.lightmaps.getIntensity(j);
                    Vector3fc sideColor = cube.getAmbientCube(j).getSide(side);

                    r += sideColor.x() * weight * intensity;
                    g += sideColor.y() * weight * intensity;
                    b += sideColor.z() * weight * intensity;
                }
            }

            ambientCube.setSide(side, r, g, b);
        }

    }

    public void sampleDynamicAmbientCube(
            Vector3fc ambientColor,
            double pX, double pY, double pZ,
            AmbientCube ambientCube
    ) {
        if (this.lightmaps == null) {
            for (int i = 0; i < AmbientCube.SIDES; i++) {
                ambientCube.setSide(i, ambientColor);
            }
            return;
        }

        Vector3f direction = new Vector3f();
        Vector3f weights = new Vector3f();

        Vector4f textureColor = new Vector4f();
        Vector3f lightmapColor = new Vector3f();
        Vector3f emissiveColor = new Vector3f();

        final int numberOfRays = 2048;
        for (int i = 0; i < AmbientCube.SIDES; i++) {
            float r = 0f;
            float g = 0f;
            float b = 0f;
            for (int j = 0; j < numberOfRays; j++) {
                AmbientCube.randomSideDirection(i, direction);

                List<NRayResult> rayResults = testRay(
                        pX, pY, pZ,
                        direction.x(), direction.y(), direction.z()
                );
                if (!rayResults.isEmpty()) {
                    NRayResult closest = rayResults.get(0);

                    LocalRayResult localRay = closest.getLocalRay();
                    localRay.weights(weights);

                    float lu = localRay.lerp(weights, NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 0);
                    float lv = localRay.lerp(weights, NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 1);

                    this.lightmaps.sampleCPULightmaps(lu, lv, lightmapColor);
                    this.lightmaps.sampleCPULightmapsEmissive(lu, lv, emissiveColor);
                    this.lightmaps.sampleColorMap(lu, lv, textureColor);

                    lightmapColor
                            .mul(textureColor.x(), textureColor.y(), textureColor.z())
                            .add(emissiveColor);
                } else {
                    lightmapColor.set(ambientColor);
                }

                r += lightmapColor.x();
                g += lightmapColor.y();
                b += lightmapColor.z();
            }
            float invrays = 1f / numberOfRays;
            r *= invrays;
            g *= invrays;
            b *= invrays;
            ambientCube.setSide(i, r, g, b);
        }
    }

    public BakeStatus bake(Scene scene) {
        CompletableFuture<Void> task = new CompletableFuture<>();
        BakeStatus status = new BakeStatus(task);
        Thread t = new Thread(() -> {
            try {
                this.bake(status, scene);
                task.complete(null);
                status.setLightmapper(null);
            } catch (Throwable ex) {
                task.completeExceptionally(ex);
            }
        }, "Lightmapper-map-" + this.name);
        t.start();

        return status;
    }

    private void bake(BakeStatus status, Scene scene) {
        float[] opaqueMesh = new float[Lightmapper.VERTEX_SIZE * 64];
        int opaqueMeshIndex = 0;

        float[] alphaMesh = new float[Lightmapper.VERTEX_SIZE * 64];
        int alphaMeshIndex = 0;

        for (int objectIndex = 0; objectIndex < this.objects.length; objectIndex++) {
            N3DObject obj = this.objects[objectIndex];

            N3DModel model = obj.getN3DModel();
            for (int i = 0; i < model.getNumberOfGeometries(); i++) {
                NGeometry geometry = model.getGeometry(i);

                if (geometry.getMaterial().isInvisible()) {
                    continue;
                }

                NBlendingMode blending = geometry.getMaterial().getBlendingMode();
                NMesh mesh = geometry.getMesh();

                float[] vertices = mesh.getVertices();
                int[] indices = mesh.getIndices();

                for (int j = 0; j < indices.length; j++) {
                    int v = indices[j] * NMesh.VERTEX_SIZE;

                    float x = vertices[v + NMesh.OFFSET_POSITION_XYZ + 0];
                    float y = vertices[v + NMesh.OFFSET_POSITION_XYZ + 1];
                    float z = vertices[v + NMesh.OFFSET_POSITION_XYZ + 2];

                    float nx = vertices[v + NMesh.OFFSET_NORMAL_XYZ + 0];
                    float ny = vertices[v + NMesh.OFFSET_NORMAL_XYZ + 1];
                    float nz = vertices[v + NMesh.OFFSET_NORMAL_XYZ + 2];

                    float tx = vertices[v + NMesh.OFFSET_TEXTURE_XY + 0];
                    float ty = vertices[v + NMesh.OFFSET_TEXTURE_XY + 1];

                    float lx = vertices[v + NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 0];
                    float ly = vertices[v + NMesh.OFFSET_LIGHTMAP_TEXTURE_XY + 1];

                    float[] meshArray;
                    int currentIndex;

                    if (NBlendingMode.OPAQUE.equals(blending)) {
                        if ((opaqueMeshIndex + Lightmapper.VERTEX_SIZE) > opaqueMesh.length) {
                            opaqueMesh = Arrays.copyOf(opaqueMesh, (opaqueMesh.length * 2) + Lightmapper.VERTEX_SIZE);
                        }
                        meshArray = opaqueMesh;
                        currentIndex = opaqueMeshIndex;
                    } else {
                        if ((alphaMeshIndex + Lightmapper.VERTEX_SIZE) > alphaMesh.length) {
                            alphaMesh = Arrays.copyOf(alphaMesh, (alphaMesh.length * 2) + Lightmapper.VERTEX_SIZE);
                        }
                        meshArray = alphaMesh;
                        currentIndex = alphaMeshIndex;
                    }

                    meshArray[currentIndex + Lightmapper.OFFSET_POSITION_XYZ + 0] = x;
                    meshArray[currentIndex + Lightmapper.OFFSET_POSITION_XYZ + 1] = y;
                    meshArray[currentIndex + Lightmapper.OFFSET_POSITION_XYZ + 2] = z;

                    meshArray[currentIndex + Lightmapper.OFFSET_NORMAL_XYZ + 0] = nx;
                    meshArray[currentIndex + Lightmapper.OFFSET_NORMAL_XYZ + 1] = ny;
                    meshArray[currentIndex + Lightmapper.OFFSET_NORMAL_XYZ + 2] = nz;

                    meshArray[currentIndex + Lightmapper.OFFSET_TEXTURE_XY + 0] = tx;
                    meshArray[currentIndex + Lightmapper.OFFSET_TEXTURE_XY + 1] = ty;

                    meshArray[currentIndex + Lightmapper.OFFSET_LIGHTMAP_XY + 0] = lx;
                    meshArray[currentIndex + Lightmapper.OFFSET_LIGHTMAP_XY + 1] = ly;

                    meshArray[currentIndex + Lightmapper.OFFSET_USER_XY + 0] = Float.intBitsToFloat(objectIndex);
                    meshArray[currentIndex + Lightmapper.OFFSET_USER_XY + 1] = Float.intBitsToFloat(geometry.getGlobalId());

                    if (NBlendingMode.OPAQUE.equals(blending)) {
                        opaqueMeshIndex += Lightmapper.VERTEX_SIZE;
                    } else {
                        alphaMeshIndex += Lightmapper.VERTEX_SIZE;
                    }
                }
            }
        }

        opaqueMesh = Arrays.copyOf(opaqueMesh, opaqueMeshIndex);
        alphaMesh = Arrays.copyOf(alphaMesh, alphaMeshIndex);

        Lightmapper.TextureInput texio = (
                float[] mesh,
                float u, float v,
                int triangle,
                boolean emissive,
                Vector4f outputColor) -> {
            int objectIndex = Float.floatToRawIntBits(mesh[triangle + Lightmapper.OFFSET_USER_XY + 0]);
            int geometryIndex = Float.floatToRawIntBits(mesh[triangle + Lightmapper.OFFSET_USER_XY + 1]);

            NMaterial material = this.objects[objectIndex].getN3DModel().getGeometry(geometryIndex).getMaterial();
            NTextures textures = material.getTextures();

            int pixelX = Math.abs(((int) Math.floor(u * textures.getWidth()))) % textures.getWidth();
            int pixelY = Math.abs(((int) Math.floor(v * textures.getHeight()))) % textures.getHeight();

            if (u < 0f) {
                pixelX = (textures.getWidth() - 1) - pixelX;
            }
            if (v < 0f) {
                pixelY = (textures.getHeight() - 1) - pixelY;
            }

            int pixelIndex = (pixelX * 4) + (pixelY * textures.getWidth() * 4);

            byte[] textureData;
            if (emissive) {
                textureData = textures.getEmissiveRedGreenBlueNormalY();
            } else {
                textureData = textures.getRedGreenBlueAlpha();
            }

            float r = ((textureData[pixelIndex + 0] & 0xFF) / 255f);
            float g = ((textureData[pixelIndex + 1] & 0xFF) / 255f);
            float b = ((textureData[pixelIndex + 2] & 0xFF) / 255f);
            float a = 1f;

            r = (float) Math.pow(r, 2.2);
            g = (float) Math.pow(g, 2.2);
            b = (float) Math.pow(b, 2.2);

            if (emissive) {
                Vector3f materialEmissive = material.getEmissiveColor();

                r *= materialEmissive.x();
                g *= materialEmissive.y();
                b *= materialEmissive.z();
            } else {
                a = ((textureData[pixelIndex + 3] & 0xFF) / 255f);
            }

            outputColor.set(r, g, b, a);
        };

        Lightmapper lightmapper = new Lightmapper(
                texio,
                scene,
                this.lightmapMargin, this.lightmapSize, this.lightmapRectangles,
                opaqueMesh, alphaMesh
        );
        status.setLightmapper(lightmapper);

        Lightmapper.LightmapperOutput output = lightmapper.bake();

        NLightmaps finalLightmaps = new NLightmaps(
                this.name, output.getNames(), this.lightmapMargin,
                this.lightmapSize, this.lightmapSize,
                output.getLightmaps(), output.getLightmapsEmissive(), output.getColor(),
                null, null,
                0, 0, null,
                output.getAmbientCubes(),
                null
        );
        
        this.lightmaps = finalLightmaps;
        
        Main.MAIN_TASKS.add(() -> {
            for (N3DObject obj : this.objects) {
                obj.setLightmaps(finalLightmaps);
            }
        });
    }

}
