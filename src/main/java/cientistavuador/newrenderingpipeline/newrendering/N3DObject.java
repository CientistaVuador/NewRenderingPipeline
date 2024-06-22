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
import cientistavuador.newrenderingpipeline.camera.Camera;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.raycast.BVH;
import cientistavuador.newrenderingpipeline.util.raycast.LocalRayResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class N3DObject {

    private static class WrappedQueryObject {

        private int object = 0;
    }

    private final String name;
    private final N3DModel n3DModel;

    private final Vector3d position = new Vector3d(0.0, 0.0, 0.0);
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f scale = new Vector3f(1f, 1f, 1f);
    private final Matrix4f transformation = new Matrix4f();

    private boolean billboardEnabled = false;

    private boolean fresnelOutlineEnabled = false;
    private float fresnelOutlineExponent = 3f;
    private final Vector3f fresnelOutlineColor = new Vector3f(1f, 1f, 1f);

    private final WrappedQueryObject queryObject = new WrappedQueryObject();

    private NAnimator animator = null;
    private NLightmaps lightmaps = NLightmaps.NULL_LIGHTMAPS;
    private NMap map = null;

    private final AmbientCube ambientCubeA = new AmbientCube();
    private final AmbientCube ambientCubeB = new AmbientCube();

    private long currentAmbientCubeUpdate = System.currentTimeMillis();
    private long nextAmbientCubeUpdate = System.currentTimeMillis() + 10;

    private final AmbientCube ambientCube = new AmbientCube();

    public N3DObject(String name, N3DModel n3DModel) {
        this.name = name;
        this.n3DModel = n3DModel;

        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedQueryObject finalWrapped = this.queryObject;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int obj = finalWrapped.object;
                if (obj != 0) {
                    glDeleteQueries(obj);
                }
                finalWrapped.object = 0;
            });
        });
    }

    public String getName() {
        return name;
    }

    public N3DModel getN3DModel() {
        return n3DModel;
    }

    public Vector3d getPosition() {
        return position;
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public Vector3f getScale() {
        return scale;
    }

    public Matrix4f getTransformation() {
        return transformation;
    }

    public boolean isBillboardEnabled() {
        return billboardEnabled;
    }

    public void setBillboardEnabled(boolean billboardEnabled) {
        this.billboardEnabled = billboardEnabled;
    }

    public boolean isFresnelOutlineEnabled() {
        return fresnelOutlineEnabled;
    }

    public void setFresnelOutlineEnabled(boolean fresnelOutlineEnabled) {
        this.fresnelOutlineEnabled = fresnelOutlineEnabled;
    }

    public float getFresnelOutlineExponent() {
        return fresnelOutlineExponent;
    }

    public void setFresnelOutlineExponent(float fresnelOutlineExponent) {
        this.fresnelOutlineExponent = fresnelOutlineExponent;
    }

    public Vector3f getFresnelOutlineColor() {
        return fresnelOutlineColor;
    }

    public boolean equalsFresnelOutline(N3DObject other) {
        return other != null
                && this.fresnelOutlineEnabled == other.fresnelOutlineEnabled
                && Float.floatToRawIntBits(this.fresnelOutlineExponent) == Float.floatToRawIntBits(other.fresnelOutlineExponent)
                && this.fresnelOutlineColor.equals(other.fresnelOutlineColor);
    }

    public boolean hasQueryObject() {
        return this.queryObject.object != 0;
    }

    public void createQueryObject() {
        if (this.queryObject.object == 0) {
            this.queryObject.object = glGenQueries();
        }
    }

    public int getQueryObject() {
        return this.queryObject.object;
    }

    public void calculateModelMatrix(Matrix4f outputModelMatrix, Camera camera) {
        double camX = 0.0;
        double camY = 0.0;
        double camZ = 0.0;

        if (camera != null) {
            camX = camera.getPosition().x();
            camY = camera.getPosition().y();
            camZ = camera.getPosition().z();
        }

        outputModelMatrix
                .identity()
                .translate(
                        (float) (getPosition().x() - camX),
                        (float) (getPosition().y() - camY),
                        (float) (getPosition().z() - camZ)
                )
                .rotate(getRotation())
                .scale(getScale());

        if (camera != null && isBillboardEnabled()) {
            outputModelMatrix.mul(camera.getInverseView());
        }

        getTransformation().mul(outputModelMatrix, outputModelMatrix);
    }

    public void transformAabb(Matrix4fc modelMatrix, Vector3f outMin, Vector3f outMax) {
        modelMatrix.transformAab(
                this.n3DModel.getAabbMin(), this.n3DModel.getAabbMax(),
                outMin, outMax
        );
    }

    public void transformAnimatedAabb(Matrix4fc modelMatrix, Vector3f outMin, Vector3f outMax) {
        modelMatrix.transformAab(
                this.n3DModel.getAnimatedAabbMin(), this.n3DModel.getAnimatedAabbMax(),
                outMin, outMax
        );
    }

    public NAnimator getAnimator() {
        return animator;
    }

    public void setAnimator(NAnimator animator) {
        this.animator = animator;
    }

    public NLightmaps getLightmaps() {
        return lightmaps;
    }

    public void setLightmaps(NLightmaps lightmaps) {
        if (lightmaps == null) {
            lightmaps = NLightmaps.NULL_LIGHTMAPS;
        }
        this.lightmaps = lightmaps;
    }

    public NMap getMap() {
        return map;
    }

    public void setMap(NMap map) {
        this.map = map;
    }

    public AmbientCube getAmbientCube() {
        return ambientCube;
    }

    public void updateAmbientCube(double pX, double pY, double pZ) {
        if (this.map == null || this.lightmaps != NLightmaps.NULL_LIGHTMAPS) {
            this.ambientCube.zero();
            return;
        }
        
        if (System.currentTimeMillis() < this.nextAmbientCubeUpdate) {
            long start = this.currentAmbientCubeUpdate;
            long end = this.nextAmbientCubeUpdate;
            long current = System.currentTimeMillis();

            float factor = ((float) (current - start)) / (end - start);

            this.ambientCube.setLerp(this.ambientCubeA, this.ambientCubeB, factor);
            return;
        }
        
        for (int i = 0; i < AmbientCube.SIDES; i++) {
            this.ambientCubeA.setSide(i, this.ambientCubeB.getSide(i));
        }
        
        this.map.sampleStaticAmbientCube(pX, pY, pZ, this.ambientCubeB);
        
        int nextTime = 100 + ThreadLocalRandom.current().nextInt(100 + 1);
        long time = System.currentTimeMillis();

        this.currentAmbientCubeUpdate = time;
        this.nextAmbientCubeUpdate = time + nextTime;
    }
    
    public List<NRayResult> testRay(
            double pX, double pY, double pZ,
            float dX, float dY, float dZ
    ) {
        List<NRayResult> results = new ArrayList<>();

        Vector3d rayPosition = new Vector3d(pX, pY, pZ);
        Vector3f rayDirection = new Vector3f(dX, dY, dZ);

        Matrix4d toWorldSpace = new Matrix4d()
                .translate(getPosition())
                .rotate(getRotation())
                .scale(getScale().x(), getScale().y(), getScale().z());
        new Matrix4d(getTransformation())
                .mul(toWorldSpace, toWorldSpace);
        Matrix4d toObjectSpace = new Matrix4d(toWorldSpace).invert();

        Vector3d objectPosition = new Vector3d(rayPosition);
        Vector3d objectDirection = new Vector3d(rayDirection);

        toObjectSpace.transformPosition(objectPosition);
        toObjectSpace.transformDirection(objectDirection);

        Vector3f geometryPosition = new Vector3f();
        Vector3f geometryDirection = new Vector3f();

        Vector3f rootSpaceHitPosition = new Vector3f();
        Vector3d hitPosition = new Vector3d();

        for (int i = 0; i < this.n3DModel.getNumberOfGeometries(); i++) {
            NGeometry geometry = this.n3DModel.getGeometry(i);

            Matrix4fc toGeometrySpace = geometry.getParent().getToNodeSpace();
            Matrix4fc toRootSpace = geometry.getParent().getToRootSpace();

            toGeometrySpace.transformPosition(
                    (float) objectPosition.x(),
                    (float) objectPosition.y(),
                    (float) objectPosition.z(),
                    geometryPosition
            );
            toGeometrySpace.transformDirection(
                    (float) objectDirection.x(),
                    (float) objectDirection.y(),
                    (float) objectDirection.z(),
                    geometryDirection
            ).normalize();

            BVH meshBVH = geometry.getMesh().getBVH();

            List<LocalRayResult> localRays = meshBVH.testRay(geometryPosition, geometryDirection);

            for (LocalRayResult localRay : localRays) {
                rootSpaceHitPosition.set(localRay.getLocalHitPosition());
                toRootSpace.transformPosition(rootSpaceHitPosition);
                hitPosition.set(rootSpaceHitPosition);
                toWorldSpace.transformPosition(hitPosition);

                results.add(new NRayResult(
                        rayPosition, rayDirection, hitPosition,
                        this, geometry, localRay
                ));
            }
        }

        return results;
    }

}
