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
import cientistavuador.newrenderingpipeline.util.BetterUniformSetter;
import cientistavuador.newrenderingpipeline.util.GPUOcclusion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class N3DObjectRenderer {

    public static boolean PARALLAX_ENABLED = true;
    public static boolean REFLECTIONS_ENABLED = true;
    public static boolean SPECULAR_ENABLED = true;
    public static boolean HDR_OUTPUT = false;
    public static boolean REFLECTIONS_DEBUG = false;

    public static final int OCCLUSION_QUERY_MINIMUM_VERTICES = 1024;
    public static final int OCCLUSION_QUERY_MINIMUM_SAMPLES = 8;

    public static final Matrix4fc IDENTITY = new Matrix4f();

    private static final ConcurrentLinkedQueue<N3DObject> renderQueue = new ConcurrentLinkedQueue<>();

    public static N3DObject[] copyQueueObjects() {
        return renderQueue.toArray(N3DObject[]::new);
    }

    public static void queueRender(N3DObject obj) {
        renderQueue.add(obj);
    }
    
    private static NProgram.NProgramLight convertToShaderLight(
            Camera camera, NLight light,
            float redFactor, float greenFactor, float blueFactor, float ambientFactor
    ) {
        if (light == null) {
            return NProgram.NULL_LIGHT;
        }

        int lightType = NProgram.NULL_LIGHT_TYPE;

        Vector3fc diffuse = light.getDiffuse();
        Vector3fc specular = light.getSpecular();
        Vector3fc ambient = light.getAmbient();

        Vector3dc position = null;
        Vector3fc direction = null;
        float innerCone = 0f;
        float outerCone = 0f;

        if (light instanceof NLight.NDirectionalLight e) {
            lightType = NProgram.DIRECTIONAL_LIGHT_TYPE;
            direction = e.getDirection();
        } else if (light instanceof NLight.NPointLight e) {
            lightType = NProgram.POINT_LIGHT_TYPE;
            position = e.getPosition();
        } else if (light instanceof NLight.NSpotLight e) {
            lightType = NProgram.SPOT_LIGHT_TYPE;
            direction = e.getDirection();
            position = e.getPosition();
            innerCone = e.getInnerCone();
            outerCone = e.getOuterCone();
        }

        float pX = 0f;
        float pY = 0f;
        float pZ = 0f;

        float dX = 0f;
        float dY = 0f;
        float dZ = 0f;

        if (position != null) {
            pX = (float) (position.x() - camera.getPosition().x());
            pY = (float) (position.y() - camera.getPosition().y());
            pZ = (float) (position.z() - camera.getPosition().z());
        }

        if (direction != null) {
            dX = direction.x();
            dY = direction.y();
            dZ = direction.z();
        }

        return new NProgram.NProgramLight(
                lightType,
                pX, pY, pZ,
                dX, dY, dZ,
                innerCone, outerCone,
                diffuse.x() * redFactor, diffuse.y() * greenFactor, diffuse.z() * blueFactor,
                specular.x() * redFactor, specular.y() * greenFactor, specular.z() * blueFactor,
                ambient.x() * redFactor * ambientFactor, ambient.y() * greenFactor * ambientFactor, ambient.z() * blueFactor * ambientFactor
        );
    }

    private static List<N3DObject> collectObjects() {
        List<N3DObject> objects = new ArrayList<>();
        N3DObject obj;
        while ((obj = renderQueue.poll()) != null) {
            objects.add(obj);
        }
        return objects;
    }

    private static List<N3DObject> filterOccluded(Camera camera, List<N3DObject> objects) {
        List<N3DObject> notOccludedObjects = new ArrayList<>();

        Matrix4f projectionView = new Matrix4f(camera.getProjection()).mul(camera.getView());

        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();

        Matrix4f modelMatrix = new Matrix4f();

        for (N3DObject obj : objects) {
            obj.calculateModelMatrix(modelMatrix, camera);

            if (obj.getAnimator() != null) {
                obj.transformAnimatedAabb(modelMatrix, transformedMin, transformedMax);
            } else {
                obj.transformAabb(modelMatrix, transformedMin, transformedMax);
            }

            if (!transformedMin.isFinite() || !transformedMax.isFinite()) {
                continue;
            }

            if (!projectionView.testAab(
                    transformedMin.x(), transformedMin.y(), transformedMin.z(),
                    transformedMax.x(), transformedMax.y(), transformedMax.z()
            )) {
                continue;
            }

            boolean occluded = false;
            if (obj.getN3DModel().getVerticesCount() >= OCCLUSION_QUERY_MINIMUM_VERTICES) {
                occlusionQuery:
                {
                    float x = (transformedMin.x() + transformedMax.x()) * 0.5f;
                    float y = (transformedMin.y() + transformedMax.y()) * 0.5f;
                    float z = (transformedMin.z() + transformedMax.z()) * 0.5f;
                    float width = transformedMax.x() - transformedMin.x();
                    float height = transformedMax.y() - transformedMin.y();
                    float depth = transformedMax.z() - transformedMin.z();
                    
                    if (GPUOcclusion.testCamera(
                            0f, 0f, 0f, camera.getNearPlane() * 1.05f,
                            x, y, z,
                            width, height, depth
                    )) {
                        break occlusionQuery;
                    }

                    if (obj.hasQueryObject()) {
                        int queryObject = obj.getQueryObject();
                        int samplesPassed = glGetQueryObjecti(queryObject, GL_QUERY_RESULT);
                        if (samplesPassed <= OCCLUSION_QUERY_MINIMUM_SAMPLES) {
                            occluded = true;
                        }
                    }
                    if (!obj.hasQueryObject()) {
                        obj.createQueryObject();
                    }
                    GPUOcclusion.occlusionQuery(
                            camera.getProjection(), camera.getView(),
                            x, y, z, width, height, depth,
                            obj.getQueryObject()
                    );
                }
            }
            
            if (!occluded) {
                double absCenterX = camera.getPosition().x() + ((transformedMin.x() + transformedMax.x()) * 0.5f);
                double absCenterY = camera.getPosition().y() + ((transformedMin.y() + transformedMax.y()) * 0.5f);
                double absCenterZ = camera.getPosition().z() + ((transformedMin.z() + transformedMax.z()) * 0.5f);
                
                obj.updateAmbientCube(absCenterX, absCenterY, absCenterZ);
                
                notOccludedObjects.add(obj);
            }
        }
        
        return notOccludedObjects;
    }
    
    private static class ToRender {

        public final N3DObject obj;
        public final Matrix4f transformation;
        public final Matrix4f model;
        public final float distanceSquared;
        public final NGeometry geometry;
        public final NCubemap[] cubemaps;
        public final NProgram.NProgramLight[] lights;

        public ToRender(
                N3DObject obj,
                Matrix4f transformation,
                Matrix4f model,
                float distanceSquared,
                NGeometry geometry,
                NCubemap[] cubemaps,
                NProgram.NProgramLight[] lights
        ) {
            this.obj = obj;
            this.transformation = transformation;
            this.model = model;
            this.distanceSquared = distanceSquared;
            this.geometry = geometry;
            this.cubemaps = cubemaps;
            this.lights = lights;
        }
    }

    public static void render(
            Camera camera,
            List<NLight> lights,
            NCubemaps cubemaps
    ) {
        if (cubemaps == null) {
            cubemaps = NCubemaps.NULL_CUBEMAPS;
        }

        List<N3DObject> objectsToRender = collectObjects();
        objectsToRender = filterOccluded(
                camera,
                objectsToRender
        );
        
        Matrix4f projectionView = new Matrix4f(camera.getProjection()).mul(camera.getView());
        
        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();
        
        Vector3d lightDirection = new Vector3d();
        Vector3f shadowColor = new Vector3f();
        
        List<ToRender> toRenderList = new ArrayList<>();

        {
            for (N3DObject obj : objectsToRender) {
                Matrix4f modelMatrix = new Matrix4f();
                N3DModel n3dmodel = obj.getN3DModel();
                NAnimator animator = obj.getAnimator();

                obj.calculateModelMatrix(modelMatrix, camera);

                for (int nodeIndex = 0; nodeIndex < n3dmodel.getNumberOfNodes(); nodeIndex++) {
                    N3DModelNode n = n3dmodel.getNode(nodeIndex);

                    Matrix4f transformation = new Matrix4f(modelMatrix)
                            .mul(n.getToRootSpace());

                    for (int i = 0; i < n.getNumberOfGeometries(); i++) {
                        NGeometry geometry = n.getGeometry(i);
                        if (animator != null) {
                            modelMatrix.transformAab(
                                    geometry.getAnimatedAabbMin(), geometry.getAnimatedAabbMax(),
                                    transformedMin, transformedMax
                            );
                        } else {
                            transformation.transformAab(
                                    geometry.getMesh().getAabbMin(), geometry.getMesh().getAabbMax(),
                                    transformedMin, transformedMax
                            );
                        }

                        if (!transformedMin.isFinite() || !transformedMax.isFinite()) {
                            continue;
                        }

                        if (!projectionView.testAab(
                                transformedMin.x(), transformedMin.y(), transformedMin.z(),
                                transformedMax.x(), transformedMax.y(), transformedMax.z()
                        )) {
                            continue;
                        }

                        List<NCubemap> toRenderCubemaps;
                        {
                            toRenderCubemaps = cubemaps
                                    .getCubemapsBVH()
                                    .testRelativeAab(camera.getPosition(), transformedMin, transformedMax);

                            List<NCubemap> toRenderCubemapsFiltered = new ArrayList<>();
                            for (NCubemap c : toRenderCubemaps) {
                                if (c.getIntensity() == 0f) {
                                    continue;
                                }

                                float cubemapMinX = (float) (c.getCubemapInfo().getMin().x() - camera.getPosition().x());
                                float cubemapMinY = (float) (c.getCubemapInfo().getMin().y() - camera.getPosition().y());
                                float cubemapMinZ = (float) (c.getCubemapInfo().getMin().z() - camera.getPosition().z());
                                float cubemapMaxX = (float) (c.getCubemapInfo().getMax().x() - camera.getPosition().x());
                                float cubemapMaxY = (float) (c.getCubemapInfo().getMax().y() - camera.getPosition().y());
                                float cubemapMaxZ = (float) (c.getCubemapInfo().getMax().z() - camera.getPosition().z());

                                if (projectionView.testAab(
                                        cubemapMinX, cubemapMinY, cubemapMinZ,
                                        cubemapMaxX, cubemapMaxY, cubemapMaxZ
                                )) {
                                    toRenderCubemapsFiltered.add(c);
                                }
                            }

                            toRenderCubemaps = toRenderCubemapsFiltered;
                        }

                        float centerX = (transformedMin.x() + transformedMax.x()) * 0.5f;
                        float centerY = (transformedMin.y() + transformedMax.y()) * 0.5f;
                        float centerZ = (transformedMin.z() + transformedMax.z()) * 0.5f;

                        double absCenterX = camera.getPosition().x() + centerX;
                        double absCenterY = camera.getPosition().y() + centerY;
                        double absCenterZ = camera.getPosition().z() + centerZ;

                        toRenderCubemaps.sort((o1, o2) -> {
                            double o1Dist = Math.min(
                                    o1.getCubemapInfo().getCubemapPosition().distanceSquared(camera.getPosition()),
                                    o1.getCubemapInfo().getCubemapPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                            );

                            double o2Dist = Math.min(
                                    o2.getCubemapInfo().getCubemapPosition().distanceSquared(camera.getPosition()),
                                    o2.getCubemapInfo().getCubemapPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                            );

                            return Double.compare(
                                    o1Dist,
                                    o2Dist
                            );
                        });

                        NCubemap[] finalToRenderCubemaps = Arrays.copyOf(toRenderCubemaps.toArray(NCubemap[]::new), NProgram.MAX_AMOUNT_OF_CUBEMAPS);
                        
                        List<NProgram.NProgramLight> toRenderLights = new ArrayList<>();
                        lights.sort((o1, o2) -> {
                            double disto1 = 0.0;
                            double disto2 = 0.0;
                            
                            if (o1 instanceof NLight.NSpotLight e) {
                                disto1 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            } else if (o1 instanceof NLight.NPointLight e) {
                                disto1 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            }
                            
                            if (o2 instanceof NLight.NSpotLight e) {
                                disto2 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            } else if (o2 instanceof NLight.NPointLight e) {
                                disto2 = Math.min(
                                        e.getPosition().distanceSquared(camera.getPosition()),
                                        e.getPosition().distanceSquared(absCenterX, absCenterY, absCenterZ)
                                );
                            }
                            
                            return Double.compare(disto1, disto2);
                        });
                        for (NLight light:lights) {
                            if (!light.isDynamic() && obj.getLightmaps() != NLightmaps.NULL_LIGHTMAPS) {
                                continue;
                            }
                            float ambientFactor = 1f;
                            if (!light.isDynamic() && obj.getMap() != null) {
                                ambientFactor = 0f;
                            }
                            float r = 1f;
                            float g = 1f;
                            float b = 1f;
                            if (obj.getMap() != null && obj.getLightmaps() == NLightmaps.NULL_LIGHTMAPS) {
                                Vector3d lightPosition = null;
                                double length = Double.POSITIVE_INFINITY;
                                
                                lightDirection.set(0f, 1f, 0f);
                                if (light instanceof NLight.NDirectionalLight d) {
                                    lightDirection.set(d.getDirection()).normalize().negate();
                                } else if (light instanceof NLight.NPointLight p) {
                                    lightPosition = p.getPosition();
                                } else if (light instanceof NLight.NSpotLight p) {
                                    lightPosition = p.getPosition();
                                }
                                
                                if (lightPosition != null) {
                                    lightDirection.set(lightPosition).sub(absCenterX, absCenterY, absCenterZ);
                                    length = lightDirection.length();
                                    lightDirection.div(length);
                                    length -= light.getLightSize();
                                    
                                    if (length < 0.0) {
                                        lightDirection.negate();
                                        length = -length;
                                    }
                                }
                                
                                obj.getMap().testShadow(
                                        absCenterX, absCenterY, absCenterZ,
                                        (float) lightDirection.x(), (float) lightDirection.y(), (float) lightDirection.z(),
                                        length,
                                        shadowColor
                                );
                                
                                r = shadowColor.x();
                                g = shadowColor.y();
                                b = shadowColor.z();
                            }
                            if (r != 0f || g != 0f || b != 0f || ambientFactor != 0f) {
                                toRenderLights.add(convertToShaderLight(camera, light, r, g, b, ambientFactor));
                            }
                        }
                        NProgram.NProgramLight[] finalLights = toRenderLights.toArray(toRenderLights.toArray(NProgram.NProgramLight[]::new));
                        finalLights = Arrays.copyOf(finalLights, NProgram.MAX_AMOUNT_OF_LIGHTS);

                        float distanceSquared = (centerX * centerX) + (centerY * centerY) + (centerZ * centerZ);
                        
                        toRenderList.add(new ToRender(
                                obj,
                                transformation, modelMatrix,
                                distanceSquared,
                                geometry,
                                finalToRenderCubemaps,
                                finalLights
                        ));
                    }
                }
            }
        }

        List<ToRender> opaqueList = new ArrayList<>();
        List<ToRender> testedList = new ArrayList<>();
        List<ToRender> blendList = new ArrayList<>();

        for (ToRender toRender : toRenderList) {
            if (toRender.geometry.getMaterial().isInvisible()) {
                continue;
            }

            NBlendingMode mode = toRender.geometry.getMaterial().getBlendingMode();

            switch (mode) {
                case OPAQUE ->
                    opaqueList.add(toRender);
                case ALPHA_TESTING ->
                    testedList.add(toRender);
                case ALPHA_BLENDING ->
                    blendList.add(toRender);
            }
        }

        Comparator<ToRender> distanceComparator = (o1, o2) -> {
            return Float.compare(o1.distanceSquared, o2.distanceSquared);
        };

        opaqueList.sort(distanceComparator);
        testedList.sort(distanceComparator);
        blendList.sort(distanceComparator.reversed());

        if (!opaqueList.isEmpty() || !testedList.isEmpty()) {
            glDisable(GL_BLEND);
            if (!opaqueList.isEmpty()) {
                renderVariant(NProgram.VARIANT_OPAQUE, camera, opaqueList);
            }
            if (!testedList.isEmpty()) {
                renderVariant(NProgram.VARIANT_ALPHA_TESTING, camera, testedList);
            }
            glEnable(GL_BLEND);
        }

        renderSkybox(camera, cubemaps.getSkybox());

        GPUOcclusion.executeQueries();

        if (!blendList.isEmpty()) {
            renderVariant(NProgram.VARIANT_ALPHA_BLENDING, camera, blendList);
        }
    }

    private static void renderSkybox(
            Camera camera,
            NCubemap skybox
    ) {
        BetterUniformSetter program = NSkybox.SKYBOX_PROGRAM;

        glUseProgram(program.getProgram());

        BetterUniformSetter.uniformMatrix4fv(program.locationOf(NSkybox.UNIFORM_PROJECTION),
                camera.getProjection()
        );
        BetterUniformSetter.uniformMatrix4fv(program.locationOf(NSkybox.UNIFORM_VIEW),
                camera.getView()
        );

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_CUBE_MAP, skybox.cubemap());
        glUniform1i(program.locationOf(NSkybox.UNIFORM_SKYBOX), 0);

        glUniform1i(program.locationOf(NSkybox.UNIFORM_HDR_OUTPUT),
                (N3DObjectRenderer.HDR_OUTPUT ? 1 : 0)
        );

        glBindVertexArray(NSkybox.VAO);
        glDrawElements(GL_TRIANGLES, NSkybox.AMOUNT_OF_INDICES, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        glUseProgram(0);
        
        Main.NUMBER_OF_DRAWCALLS++;
        Main.NUMBER_OF_VERTICES += NSkybox.AMOUNT_OF_INDICES;
    }

    private static void renderVariant(
            BetterUniformSetter variant,
            Camera camera,
            List<ToRender> toRender
    ) {
        NTextures.NULL_TEXTURE.r_g_b_a();
        NTextures.NULL_TEXTURE.ht_rg_mt_nx();
        NTextures.NULL_TEXTURE.er_eg_eb_ny();
        
        for (ToRender t : toRender) {
            NTextures textures = t.geometry.getMaterial().getTextures();

            textures.r_g_b_a();
            textures.ht_rg_mt_nx();
            textures.er_eg_eb_ny();

            for (NCubemap e : t.cubemaps) {
                if (e == null) {
                    continue;
                }
                e.cubemap();
            }
            
            t.obj.getLightmaps().lightmaps();
        }

        glUseProgram(variant.getProgram());

        BetterUniformSetter.uniformMatrix4fv(variant.locationOf(NProgram.UNIFORM_PROJECTION), camera.getProjection());
        BetterUniformSetter.uniformMatrix4fv(variant.locationOf(NProgram.UNIFORM_VIEW), camera.getView());

        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTIONS_DEBUG), (REFLECTIONS_DEBUG ? 1 : 0));
        glUniform1i(variant.locationOf(NProgram.UNIFORM_HDR_OUTPUT), (HDR_OUTPUT ? 1 : 0));

        NProgram.sendBoneMatrix(variant, IDENTITY, -1);

        glUniform1i(variant.locationOf(NProgram.UNIFORM_PARALLAX_ENABLED), (PARALLAX_ENABLED ? 1 : 0));
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTIONS_ENABLED), (REFLECTIONS_ENABLED ? 1 : 0));
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTIONS_SUPPORTED), (REFLECTIONS_ENABLED ? 1 : 0));

        glUniform1i(variant.locationOf(NProgram.UNIFORM_R_G_B_A), 0);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_HT_RG_MT_NX), 1);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_ER_EG_EB_NY), 2);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_LIGHTMAPS), 3);

        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTION_CUBEMAP_0), 4);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTION_CUBEMAP_1), 5);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTION_CUBEMAP_2), 6);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTION_CUBEMAP_3), 7);

        render(variant, camera, toRender);

        glUseProgram(0);
    }

    private static void render(
            BetterUniformSetter variant,
            Camera camera,
            List<ToRender> list
    ) {
        Matrix4f worldToLocal = new Matrix4f();
        Vector3f relativePosition = new Vector3f();

        Matrix4f transformedBone = new Matrix4f();
        
        NProgram.NProgramLight[] lastLights = null;
        AmbientCube lastAmbientCube = null;
        NLightmaps lastLightmaps = null;
        NMaterial lastMaterial = null;
        NTextures lastTextures = null;
        NCubemap[] lastCubemaps = null;
        Matrix4fc lastTransformation = null;
        NMesh lastMesh = null;
        NAnimator lastAnimator = null;
        N3DObject lastFresnel = null;

        Matrix3f normalMatrix = new Matrix3f();

        for (ToRender render : list) {
            N3DModel n3dmodel = render.obj.getN3DModel();

            NProgram.NProgramLight[] lights = render.lights;
            AmbientCube ambientCube = render.obj.getAmbientCube();
            NLightmaps lightmaps = render.obj.getLightmaps();
            NMaterial material = render.geometry.getMaterial();
            NTextures textures = material.getTextures();
            NCubemap[] cubemaps = render.cubemaps;
            Matrix4f transformation = render.transformation;
            NMesh mesh = render.geometry.getMesh();
            NAnimator animator = render.obj.getAnimator();
            N3DObject fresnel = render.obj;
            
            if (!Arrays.deepEquals(lastLights, lights)) {
                for (int i = 0; i < lights.length; i++) {
                    NProgram.sendLight(variant, lights[i], i);
                }
                lastLights = lights;
            }
            
            if (!ambientCube.equals(lastAmbientCube)) {
                NProgram.sendAmbientCube(variant, ambientCube);
                lastAmbientCube = ambientCube;
            }

            if (animator != null) {
                transformation = render.model;
            }

            if (!material.equalsPropertiesOnly(lastMaterial)) {
                Vector4fc d = material.getDiffuseColor();
                Vector3fc s = material.getSpecularColor();
                Vector3fc e = material.getEmissiveColor();
                Vector3fc r = material.getReflectionColor();

                float sr = s.x();
                float sg = s.y();
                float sb = s.z();

                if (!SPECULAR_ENABLED) {
                    sr = 0f;
                    sg = 0f;
                    sb = 0f;
                }
                
                NProgram.sendMaterial(variant, new NProgram.NProgramMaterial(
                        d.x(), d.y(), d.z(), d.w(),
                        sr, sg, sb,
                        e.x(), e.y(), e.z(),
                        r.x(), r.y(), r.z(),
                        material.getMinExponent(), material.getMaxExponent(),
                        material.getParallaxHeightCoefficient(), material.getParallaxMinLayers(), material.getParallaxMaxLayers()
                ));
                lastMaterial = material;
            }

            if (!textures.equals(lastTextures)) {
                int r_g_b_a = textures.r_g_b_a();
                int ht_ie_rf_nx = textures.ht_rg_mt_nx();
                int er_eg_eb_ny = textures.er_eg_eb_ny();

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, r_g_b_a);

                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, ht_ie_rf_nx);

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, er_eg_eb_ny);

                glUniform1i(
                        variant.locationOf(NProgram.UNIFORM_PARALLAX_SUPPORTED),
                        textures.isHeightMapSupported() ? 1 : 0
                );

                lastTextures = textures;
            }

            if (lastLightmaps != lightmaps) {
                int maps = lightmaps.lightmaps();

                glActiveTexture(GL_TEXTURE3);
                glBindTexture(GL_TEXTURE_2D_ARRAY, maps);

                for (int i = 0; i < lightmaps.getNumberOfLightmaps(); i++) {
                    NProgram.sendLightmapIntensity(variant, lightmaps.getIntensity(i), i);
                }
                
                lastLightmaps = lightmaps;
            }

            if (!Arrays.equals(lastCubemaps, cubemaps)) {
                for (int i = 0; i < cubemaps.length; i++) {
                    NCubemap cubemap = cubemaps[i];

                    int cubemapObj = NCubemap.NULL_CUBEMAP.cubemap();
                    if (cubemap != null) {
                        cubemapObj = cubemap.cubemap();
                    }

                    glActiveTexture(GL_TEXTURE4 + i);
                    glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapObj);

                    boolean enabled = false;
                    float intensity = 0f;

                    if (cubemap != null) {
                        enabled = true;
                        intensity = cubemap.getIntensity();

                        cubemap.getCubemapInfo().calculateRelative(camera.getPosition(), worldToLocal, relativePosition);
                    }

                    NProgram.sendParallaxCubemapInfo(
                            variant,
                            i,
                            enabled,
                            intensity,
                            relativePosition.x(), relativePosition.y(), relativePosition.z(),
                            worldToLocal
                    );
                }

                lastCubemaps = cubemaps;
            }

            if (!transformation.equals(lastTransformation)) {
                BetterUniformSetter.uniformMatrix4fv(
                        variant.locationOf(NProgram.UNIFORM_MODEL),
                        transformation
                );
                BetterUniformSetter.uniformMatrix3fv(
                        variant.locationOf(NProgram.UNIFORM_NORMAL_MODEL),
                        transformation.normal(normalMatrix)
                );
                lastTransformation = transformation;
            }

            if (animator != lastAnimator || (animator == null && mesh.getAmountOfBones() != 0) || !mesh.equals(lastMesh)) {
                for (int boneIndex = 0; boneIndex < mesh.getAmountOfBones(); boneIndex++) {
                    String bone = mesh.getBone(boneIndex);

                    if (animator != null) {
                        Matrix4fc boneMatrix = animator.getBoneMatrix(bone);
                        N3DModelNode boneNode = n3dmodel.getNode(bone);

                        transformedBone
                                .set(boneMatrix)
                                .mul(boneNode.getToNodeSpace())
                                .mul(render.geometry.getParent().getToRootSpace());

                        NProgram.sendBoneMatrix(variant, transformedBone, boneIndex);
                    } else {
                        NProgram.sendBoneMatrix(variant, IDENTITY, boneIndex);
                    }
                }

                lastAnimator = animator;
            }

            if (!mesh.equals(lastMesh)) {
                glBindVertexArray(mesh.getVAO());
                lastMesh = mesh;
            }

            if (!fresnel.equalsFresnelOutline(lastFresnel)) {
                NProgram.sendFresnelOutlineInfo(variant,
                        fresnel.isFresnelOutlineEnabled(),
                        fresnel.getFresnelOutlineExponent(),
                        fresnel.getFresnelOutlineColor().x(), fresnel.getFresnelOutlineColor().y(), fresnel.getFresnelOutlineColor().z()
                );
                lastFresnel = fresnel;
            }

            glDrawElements(GL_TRIANGLES, mesh.getIndices().length, GL_UNSIGNED_INT, 0);

            Main.NUMBER_OF_DRAWCALLS++;
            Main.NUMBER_OF_VERTICES += mesh.getIndices().length;
        }

        glBindVertexArray(0);
    }

    private N3DObjectRenderer() {

    }

}
