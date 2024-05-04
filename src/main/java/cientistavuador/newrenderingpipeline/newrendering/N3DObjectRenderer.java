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

    public static boolean PARALLAX_ENABLED = false;
    public static boolean REFLECTIONS_ENABLED = true;

    private static final ConcurrentLinkedQueue<N3DObject> renderQueue = new ConcurrentLinkedQueue<>();

    public static void queueRender(N3DObject obj) {
        renderQueue.add(obj);
    }

    private static class ToRender {
        
        public final Matrix4f transformation;
        public final Matrix4f model;
        public final float distanceSquared;
        public final NAnimator animator;
        public final NGeometry geometry;

        public ToRender(Matrix4f transformation, Matrix4f model, float distanceSquared, NAnimator animator, NGeometry geometry) {
            this.transformation = transformation;
            this.model = model;
            this.distanceSquared = distanceSquared;
            this.animator = animator;
            this.geometry = geometry;
        }
    }

    public static void render(Camera camera, List<NLight> lights, NCubemap skybox) {
        List<NProgram.NProgramLight> renderableLightsList = new ArrayList<>();
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
                    renderableLightsList.add(light);
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
                    renderableLightsList.add(light);
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
                    renderableLightsList.add(light);
                }
            }
        }
        renderableLightsList.sort((o1, o2) -> {
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
        for (int i = 0; i < renderableLights.length; i++) {
            NProgram.NProgramLight light = null;
            if (i < renderableLightsList.size()) {
                light = renderableLightsList.get(i);
            }
            renderableLights[i] = light;
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
                
                objectsToRender.add(obj);
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
                            .mul(n.getTotalTransformation());
                    
                    NGeometry[] geometries = n.getGeometries();
                    for (NGeometry geometry : geometries) {
                        if (animator != null) {
                            modelMatrix.transformAab(
                                    geometry.getMesh().getAnimatedAabbMin(), geometry.getMesh().getAnimatedAabbMax(),
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
                                transformation, modelMatrix, distanceSquared, obj.getAnimator(), geometry
                        ));
                    }
                }
            }
        }

        List<ToRender> opaqueList = new ArrayList<>();
        List<ToRender> testedList = new ArrayList<>();
        List<ToRender> blendList = new ArrayList<>();

        for (ToRender toRender : toRenderList) {
            NBlendingMode mode = toRender.geometry.getMaterial().getTextures().getBlendingMode();
            float materialAlpha = toRender.geometry.getMaterial().getDiffuseColor().w();

            if (materialAlpha <= 0f) {
                continue;
            }

            if (materialAlpha != 1f && NBlendingMode.OPAQUE.equals(mode)) {
                mode = NBlendingMode.ALPHA_BLENDING;
            }

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
                renderVariant(NProgram.VARIANT_ALPHA_BLENDING, camera, skybox, renderableLights, opaqueList);
            }
            if (!testedList.isEmpty()) {
                renderVariant(NProgram.VARIANT_ALPHA_TESTING, camera, skybox, renderableLights, testedList);
            }
            glEnable(GL_BLEND);
        }
        
        if (skybox != null) {
            renderSkybox(camera, skybox);
        }

        if (!blendList.isEmpty()) {
            renderVariant(NProgram.VARIANT_ALPHA_BLENDING, camera, skybox, renderableLights, blendList);
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
            List<ToRender> toRender
    ) {
        glUseProgram(variant.getProgram());

        BetterUniformSetter.uniformMatrix4fv(variant.locationOf(NProgram.UNIFORM_PROJECTION),
                camera.getProjection()
        );
        BetterUniformSetter.uniformMatrix4fv(variant.locationOf(NProgram.UNIFORM_VIEW),
                camera.getView()
        );

        glUniform1i(variant.locationOf(NProgram.UNIFORM_ANIMATION_ENABLED), 0);

        glUniform1i(variant.locationOf(NProgram.UNIFORM_PARALLAX_ENABLED),
                (PARALLAX_ENABLED ? 1 : 0)
        );
        
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTIONS_ENABLED),
                (REFLECTIONS_ENABLED ? 1 : 0)
        );
        
        int skyboxCubemap = skybox.cubemap();
        
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_CUBE_MAP, skyboxCubemap);
        
        glUniform1i(variant.locationOf(NProgram.UNIFORM_REFLECTION_CUBEMAP), 3);
        
        for (int i = 0; i < lights.length; i++) {
            NProgram.sendLight(variant, lights[i], i);
        }

        glUniform1i(variant.locationOf(NProgram.UNIFORM_R_G_B_A), 0);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_HT_RG_MT_NX), 1);
        glUniform1i(variant.locationOf(NProgram.UNIFORM_ER_EG_EB_NY), 2);

        render(variant, toRender);
        
        glUseProgram(0);
    }

    private static void render(BetterUniformSetter variant, List<ToRender> list) {
        Matrix4f transformedBone = new Matrix4f();

        NMaterial lastMaterial = null;
        NTextures lastTextures = null;
        Matrix4f lastTransformation = null;
        NMesh lastMesh = null;
        NAnimator lastAnimator = null;

        for (ToRender render : list) {
            NMaterial material = render.geometry.getMaterial();
            NTextures textures = material.getTextures();
            Matrix4f transformation = render.transformation;
            NMesh mesh = render.geometry.getMesh();
            NAnimator animator = render.animator;

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

            if (!transformation.equals(lastTransformation)) {
                BetterUniformSetter.uniformMatrix4fv(
                        variant.locationOf(NProgram.UNIFORM_MODEL),
                        transformation
                );
                BetterUniformSetter.uniformMatrix3fv(
                        variant.locationOf(NProgram.UNIFORM_NORMAL_MODEL),
                        new Matrix3f().set(new Matrix4f().set(transformation).invert().transpose())
                );
                lastTransformation = transformation;
            }

            if (!mesh.equals(lastMesh)) {
                glBindVertexArray(mesh.getVAO());
                lastMesh = mesh;
            }

            if (animator != lastAnimator) {
                if (animator == null) {
                    glUniform1i(variant.locationOf(NProgram.UNIFORM_ANIMATION_ENABLED), 0);
                } else {
                    glUniform1i(variant.locationOf(NProgram.UNIFORM_ANIMATION_ENABLED), 1);

                    for (int boneIndex = 0; boneIndex < mesh.getAmountOfBones(); boneIndex++) {
                        NMeshBone bone = mesh.getBone(boneIndex);

                        Matrix4fc boneMatrix = animator.getBoneMatrix(bone.getName());
                        Matrix4fc offset = bone.getOffset();

                        transformedBone.identity();

                        if (boneMatrix != null) {
                            transformedBone
                                    .set(render.model)
                                    .mul(boneMatrix)
                                    .mul(offset);
                        }

                        NProgram.sendBoneMatrix(variant, transformedBone, boneIndex);
                    }
                }
                lastAnimator = animator;
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
