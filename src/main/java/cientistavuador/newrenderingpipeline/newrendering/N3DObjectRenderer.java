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

import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.camera.Camera;
import cientistavuador.newrenderingpipeline.util.BetterUniformSetter;
import cientistavuador.newrenderingpipeline.util.GPUOcclusion;
import cientistavuador.newrenderingpipeline.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
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
    public static boolean HDR_OUTPUT = false;

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

    private static class ToRender {

        public final N3DObject obj;
        public final Matrix4f transformation;
        public final Matrix4f model;
        public final float distanceSquared;
        public final NGeometry geometry;

        public ToRender(
                N3DObject obj,
                Matrix4f transformation,
                Matrix4f model,
                float distanceSquared,
                NGeometry geometry
        ) {
            this.obj = obj;
            this.transformation = transformation;
            this.model = model;
            this.distanceSquared = distanceSquared;
            this.geometry = geometry;
        }
    }

    public static void render(Camera camera, List<NLight> lights, NCubemap skybox) {
        List<Pair<NProgram.NProgramLight, Boolean>> renderableLightsList = new ArrayList<>();
        for (NLight indexLight : lights) {
            if (indexLight != null) {
                if (indexLight instanceof NLight.NDirectionalLight d) {
                    NProgram.NProgramLight light = new NProgram.NProgramLight(
                            NProgram.DIRECTIONAL_LIGHT_TYPE,
                            0f, 0f, 0f,
                            d.getDirection().x(), d.getDirection().y(), d.getDirection().z(),
                            0f, 0f,
                            d.getDiffuse().x(), d.getDiffuse().y(), d.getDiffuse().z(),
                            d.getSpecular().x(), d.getSpecular().y(), d.getSpecular().z(),
                            d.getAmbient().x(), d.getAmbient().y(), d.getAmbient().z()
                    );
                    renderableLightsList.add(new Pair<>(light, indexLight.isDynamic()));
                }
                if (indexLight instanceof NLight.NPointLight p) {
                    float relativeX = (float) (p.getPosition().x() - camera.getPosition().x());
                    float relativeY = (float) (p.getPosition().y() - camera.getPosition().y());
                    float relativeZ = (float) (p.getPosition().z() - camera.getPosition().z());
                    NProgram.NProgramLight light = new NProgram.NProgramLight(
                            NProgram.POINT_LIGHT_TYPE,
                            relativeX, relativeY, relativeZ,
                            0f, 0f, 0f,
                            0f, 0f,
                            p.getDiffuse().x(), p.getDiffuse().y(), p.getDiffuse().z(),
                            p.getSpecular().x(), p.getSpecular().y(), p.getSpecular().z(),
                            p.getAmbient().x(), p.getAmbient().y(), p.getAmbient().z()
                    );
                    renderableLightsList.add(new Pair<>(light, indexLight.isDynamic()));
                }
                if (indexLight instanceof NLight.NSpotLight s) {
                    float relativeX = (float) (s.getPosition().x() - camera.getPosition().x());
                    float relativeY = (float) (s.getPosition().y() - camera.getPosition().y());
                    float relativeZ = (float) (s.getPosition().z() - camera.getPosition().z());
                    NProgram.NProgramLight light = new NProgram.NProgramLight(
                            NProgram.SPOT_LIGHT_TYPE,
                            relativeX, relativeY, relativeZ,
                            s.getDirection().x(), s.getDirection().y(), s.getDirection().z(),
                            s.getInnerCone(), s.getOuterCone(),
                            s.getDiffuse().x(), s.getDiffuse().y(), s.getDiffuse().z(),
                            s.getSpecular().x(), s.getSpecular().y(), s.getSpecular().z(),
                            s.getAmbient().x(), s.getAmbient().y(), s.getAmbient().z()
                    );
                    renderableLightsList.add(new Pair<>(light, indexLight.isDynamic()));
                }
            }
        }
        renderableLightsList.sort((o1Pair, o2Pair) -> {
            NProgram.NProgramLight o1 = o1Pair.getA();
            NProgram.NProgramLight o2 = o2Pair.getA();
            
            float o1Dist = (o1.x * o1.x) + (o1.y * o1.y) + (o1.z * o1.z);
            float o2Dist = (o2.x * o2.x) + (o2.y * o2.y) + (o2.z * o2.z);
            if (o1.type == NProgram.DIRECTIONAL_LIGHT_TYPE) {
                o1Dist = 0f;
            }
            if (o2.type == NProgram.DIRECTIONAL_LIGHT_TYPE) {
                o2Dist = 0f;
            }
            return Float.compare(o1Dist, o2Dist);
        });

        NProgram.NProgramLight[] renderableLights = new NProgram.NProgramLight[NProgram.MAX_AMOUNT_OF_LIGHTS];
        NProgram.NProgramLight[] renderableDynamicLights = new NProgram.NProgramLight[NProgram.MAX_AMOUNT_OF_LIGHTS];
        
        int renderableLightsIndex = 0;
        for (int i = 0; i < renderableLightsList.size(); i++) {
            NProgram.NProgramLight light = renderableLightsList.get(i).getA();
            renderableLights[renderableLightsIndex] = light;
            renderableLightsIndex++;
            if (renderableLightsIndex >= renderableLights.length) {
                break;
            }
        }
        
        int renderableDynamicLightsIndex = 0;
        for (int i = 0; i < renderableLightsList.size(); i++) {
            Pair<NProgram.NProgramLight, Boolean> pair = renderableLightsList.get(i);
            if (!pair.getB()) {
                continue;
            }
            NProgram.NProgramLight light = pair.getA();
            renderableLights[renderableDynamicLightsIndex] = light;
            renderableDynamicLightsIndex++;
            if (renderableDynamicLightsIndex >= renderableDynamicLights.length) {
                break;
            }
        }

        List<N3DObject> objectsToRender = new ArrayList<>();

        Matrix4f projectionView = new Matrix4f(camera.getProjection()).mul(camera.getView());

        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();

        {
            Matrix4f modelMatrix = new Matrix4f();

            N3DObject obj;
            while ((obj = renderQueue.poll()) != null) {
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
                        float x = transformedMin.x() * 0.5f + transformedMax.x() * 0.5f;
                        float y = transformedMin.y() * 0.5f + transformedMax.y() * 0.5f;
                        float z = transformedMin.z() * 0.5f + transformedMax.z() * 0.5f;
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
                    objectsToRender.add(obj);
                }
            }
        }

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

                        float centerX = (transformedMin.x() * 0.5f) + (transformedMax.x() * 0.5f);
                        float centerY = (transformedMin.y() * 0.5f) + (transformedMax.y() * 0.5f);
                        float centerZ = (transformedMin.z() * 0.5f) + (transformedMax.z() * 0.5f);

                        float distanceSquared = (centerX * centerX) + (centerY * centerY) + (centerZ * centerZ);

                        toRenderList.add(new ToRender(
                                obj,
                                transformation, modelMatrix,
                                distanceSquared,
                                geometry
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
                renderVariant(NProgram.VARIANT_ALPHA_BLENDING, camera, skybox, renderableLights, renderableDynamicLights, opaqueList);
            }
            if (!testedList.isEmpty()) {
                renderVariant(NProgram.VARIANT_ALPHA_TESTING, camera, skybox, renderableLights, renderableDynamicLights, testedList);
            }
            glEnable(GL_BLEND);
        }

        if (skybox != null) {
            renderSkybox(camera, skybox);
        }

        GPUOcclusion.executeQueries();

        if (!blendList.isEmpty()) {
            renderVariant(NProgram.VARIANT_ALPHA_BLENDING, camera, skybox, renderableLights, renderableDynamicLights, blendList);
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
    }

    private static void renderVariant(
            BetterUniformSetter variant,
            Camera camera,
            NCubemap skybox,
            NProgram.NProgramLight[] lights,
            NProgram.NProgramLight[] dynamicLights,
            List<ToRender> toRender
    ) {
        glUseProgram(variant.getProgram());

        BetterUniformSetter.uniformMatrix4fv(variant.locationOf(NProgram.UNIFORM_PROJECTION),
                camera.getProjection()
        );
        BetterUniformSetter.uniformMatrix4fv(variant.locationOf(NProgram.UNIFORM_VIEW),
                camera.getView()
        );
        
        glUniform1i(variant.locationOf(NProgram.UNIFORM_HDR_OUTPUT),
                (HDR_OUTPUT ? 1 : 0)
        );
        
        NProgram.sendBoneMatrix(variant, IDENTITY, -1);

        glUniform1i(variant.locationOf(NProgram.UNIFORM_PARALLAX_ENABLED),
                (PARALLAX_ENABLED ? 1 : 0)
        );

        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTIONS_ENABLED),
                (REFLECTIONS_ENABLED ? 1 : 0)
        );
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTIONS_SUPPORTED),
                (REFLECTIONS_ENABLED ? 1 : 0)
        );

        int skyboxCubemap = skybox.cubemap();

        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_CUBE_MAP, skyboxCubemap);

        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTION_CUBEMAP), 5);

        float width = 20f;
        float height = 5f;
        float depth = 20f;

        float relativeX = (float) ((0f + (width * 0.5f)) - camera.getPosition().x());
        float relativeY = (float) ((10f + (height * 0.5f)) - camera.getPosition().y());
        float relativeZ = (float) ((-15f + (depth * -0.5f)) - camera.getPosition().z());

        NProgram.sendParallaxCubemapInfo(variant,
                false,
                relativeX, relativeY, relativeZ + 5f,
                new Matrix4f()
                        .translate(relativeX, relativeY, relativeZ)
                        .scale(width * 0.5f, height * 0.5f, depth * 0.5f)
                        .invert()
        );
        
        glUniform1i(variant.locationOf(NProgram.UNIFORM_R_G_B_A), 1);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_HT_RG_MT_NX), 2);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_ER_EG_EB_NY), 3);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_LIGHTMAPS), 4);

        render(variant, toRender, lights, dynamicLights);

        glUseProgram(0);
    }

    private static void render(
            BetterUniformSetter variant,
            List<ToRender> list,
            NProgram.NProgramLight[] lights,
            NProgram.NProgramLight[] dynamicLights
    ) {
        Matrix4f transformedBone = new Matrix4f();
        
        NLightmaps lastLightmaps = null;
        NMaterial lastMaterial = null;
        NTextures lastTextures = null;
        Matrix4fc lastTransformation = null;
        NMesh lastMesh = null;
        NAnimator lastAnimator = null;
        N3DObject lastFresnel = null;
        
        Matrix3f normalMatrix = new Matrix3f();
        
        for (ToRender render : list) {
            N3DModel n3dmodel = render.obj.getN3DModel();
            
            NMaterial material = render.geometry.getMaterial();
            NTextures textures = material.getTextures();
            Matrix4f transformation = render.transformation;
            NMesh mesh = render.geometry.getMesh();
            NAnimator animator = render.obj.getAnimator();
            NLightmaps lightmaps = render.obj.getLightmaps();
            N3DObject fresnel = render.obj;
            
            if (animator != null) {
                transformation = render.model;
            }

            if (!material.equalsPropertiesOnly(lastMaterial)) {
                Vector4fc d = material.getDiffuseColor();
                Vector3fc s = material.getSpecularColor();
                Vector3fc e = material.getEmissiveColor();
                Vector3fc r = material.getReflectionColor();

                NProgram.sendMaterial(variant, new NProgram.NProgramMaterial(
                        d.x(), d.y(), d.z(), d.w(),
                        s.x(), s.y(), s.z(),
                        e.x(), e.y(), e.z(),
                        r.x(), r.y(), r.z(),
                        material.getMinExponent(), material.getMaxExponent(),
                        material.getParallaxHeightCoefficient(), material.getParallaxMinLayers(), material.getParallaxMaxLayers()
                ));
                lastMaterial = material;
            }
            
            if (lastLightmaps != lightmaps) {
                int maps = lightmaps.lightmaps();
                
                glActiveTexture(GL_TEXTURE4);
                glBindTexture(GL_TEXTURE_2D_ARRAY, maps);
                
                for (int i = 0; i < lightmaps.getNumberOfLightmaps(); i++) {
                    NProgram.sendLightmapIntensity(variant, lightmaps.getIntensity(i), i);
                }
                
                if (lightmaps == NLightmaps.NULL_LIGHTMAPS) {
                    for (int i = 0; i < lights.length; i++) {
                        NProgram.sendLight(variant, lights[i], i);
                    }
                } else {
                    for (int i = 0; i < dynamicLights.length; i++) {
                        NProgram.sendLight(variant, dynamicLights[i], i);
                    }
                }
                
                lastLightmaps = lightmaps;
            }
            
            if (!textures.equals(lastTextures)) {
                int r_g_b_a = textures.r_g_b_a();
                int ht_ie_rf_nx = textures.ht_rg_mt_nx();
                int er_eg_eb_ny = textures.er_eg_eb_ny();

                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, r_g_b_a);

                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, ht_ie_rf_nx);

                glActiveTexture(GL_TEXTURE3);
                glBindTexture(GL_TEXTURE_2D, er_eg_eb_ny);

                glUniform1i(
                        variant.locationOf(NProgram.UNIFORM_PARALLAX_SUPPORTED),
                        textures.isHeightMapSupported() ? 1 : 0
                );

                lastTextures = textures;
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
