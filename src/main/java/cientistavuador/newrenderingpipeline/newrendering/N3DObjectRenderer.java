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
import cientistavuador.newrenderingpipeline.util.TransformUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class N3DObjectRenderer {

    public static boolean PARALLAX_ENABLED = true;

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

    public static void render(Camera camera, List<NLight> lights) {
        NProgram.NProgramLight[] renderableLights = new NProgram.NProgramLight[NProgram.MAX_AMOUNT_OF_LIGHTS];
        for (int i = 0; i < renderableLights.length; i++) {
            NProgram.NProgramLight light = NProgram.NULL_LIGHT;
            if (i < lights.size()) {
                NLight indexLight = lights.get(i);
                if (indexLight != null) {
                    if (indexLight instanceof NLight.NDirectionalLight directional) {
                        light = new NProgram.NProgramLight(
                                NProgram.DIRECTIONAL_LIGHT_TYPE,
                                null, directional.getDirection(),
                                0f, 0f,
                                directional.getDiffuse(), directional.getSpecular(), directional.getAmbient()
                        );
                    }
                    if (indexLight instanceof NLight.NPointLight point) {
                        Vector3d relativePos = new Vector3d(point.getPosition()).sub(camera.getPosition());
                        light = new NProgram.NProgramLight(
                                NProgram.POINT_LIGHT_TYPE,
                                new Vector3f().set(relativePos), null,
                                0f, 0f,
                                point.getDiffuse(), point.getSpecular(), point.getAmbient()
                        );
                    }
                    if (indexLight instanceof NLight.NSpotLight spot) {
                        Vector3d relativePos = new Vector3d(spot.getPosition()).sub(camera.getPosition());
                        light = new NProgram.NProgramLight(
                                NProgram.SPOT_LIGHT_TYPE,
                                new Vector3f().set(relativePos), spot.getDirection(),
                                spot.getInnerCone(), spot.getOuterCone(),
                                spot.getDiffuse(), spot.getSpecular(), spot.getAmbient()
                        );
                    }
                }
            }
            renderableLights[i] = light;
        }

        List<N3DObject> objectsToRender = new ArrayList<>();

        Matrix4f projectionView = new Matrix4f(camera.getProjection()).mul(camera.getView());

        Vector3f transformedMin = new Vector3f();
        Vector3f transformedMax = new Vector3f();

        {
            N3DObject obj;
            while ((obj = renderQueue.poll()) != null) {
                obj.transformAabb(transformedMin, transformedMax);
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
                Matrix4f modelMatrix = obj.getModel();
                N3DModel n3dmodel = obj.getN3DModel();
                NAnimator animator = obj.getAnimator();

                for (int nodeIndex = 0; nodeIndex < n3dmodel.getNumberOfNodes(); nodeIndex++) {
                    N3DModelNode n = n3dmodel.getNode(nodeIndex);
                    Matrix4f transformation = new Matrix4f(modelMatrix).mul(n.getTotalTransformation());

                    NGeometry[] geometries = n.getGeometries();
                    for (NGeometry geometry : geometries) {
                        TransformUtils.transformAabb(
                                geometry.getMesh().getAabbMin(), geometry.getMesh().getAabbMax(),
                                transformation,
                                transformedMin, transformedMax
                        );
                        if (!transformedMin.isFinite() || !transformedMax.isFinite()) {
                            continue;
                        }
                        if (animator == null && !projectionView.testAab(
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

            if (materialAlpha != 1f && (NBlendingMode.OPAQUE.equals(mode) || NBlendingMode.OPAQUE_WITH_HEIGHT_MAP.equals(mode))) {
                mode = NBlendingMode.ALPHA_BLENDING;
            }

            switch (mode) {
                case OPAQUE, OPAQUE_WITH_HEIGHT_MAP ->
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

        glDisable(GL_BLEND);
        if (!opaqueList.isEmpty()) {
            BetterUniformSetter opaqueVariant = NProgram.VARIANT_ALPHA_BLENDING;

            glUseProgram(opaqueVariant.getProgram());

            BetterUniformSetter.uniformMatrix4fv(
                    opaqueVariant.locationOf(NProgram.UNIFORM_PROJECTION),
                    camera.getProjection()
            );
            BetterUniformSetter.uniformMatrix4fv(
                    opaqueVariant.locationOf(NProgram.UNIFORM_VIEW),
                    camera.getView()
            );

            glUniform1i(opaqueVariant.locationOf(NProgram.UNIFORM_ANIMATION_ENABLED), 0);

            glUniform1i(
                    opaqueVariant.locationOf(NProgram.UNIFORM_PARALLAX_ENABLED),
                    (PARALLAX_ENABLED ? 1 : 0)
            );

            for (int i = 0; i < renderableLights.length; i++) {
                NProgram.sendLight(opaqueVariant, renderableLights[i], i);
            }

            glUniform1i(opaqueVariant.locationOf(NProgram.UNIFORM_R_G_B_A_OR_H), 0);
            glUniform1i(opaqueVariant.locationOf(NProgram.UNIFORM_IE_NX_R_NY), 1);

            render(opaqueVariant, opaqueList);

            glUseProgram(0);
        }

        if (!testedList.isEmpty()) {
            BetterUniformSetter testedVariant = NProgram.VARIANT_ALPHA_TESTING;

            glUseProgram(testedVariant.getProgram());

            BetterUniformSetter.uniformMatrix4fv(testedVariant.locationOf(NProgram.UNIFORM_PROJECTION),
                    camera.getProjection()
            );
            BetterUniformSetter.uniformMatrix4fv(testedVariant.locationOf(NProgram.UNIFORM_VIEW),
                    camera.getView()
            );

            glUniform1i(testedVariant.locationOf(NProgram.UNIFORM_ANIMATION_ENABLED), 0);

            glUniform1i(testedVariant.locationOf(NProgram.UNIFORM_PARALLAX_ENABLED),
                    (PARALLAX_ENABLED ? 1 : 0)
            );

            for (int i = 0; i < renderableLights.length; i++) {
                NProgram.sendLight(testedVariant, renderableLights[i], i);
            }

            glUniform1i(testedVariant.locationOf(NProgram.UNIFORM_R_G_B_A_OR_H), 0);
            glUniform1i(testedVariant.locationOf(NProgram.UNIFORM_IE_NX_R_NY), 1);

            render(testedVariant, testedList);

            glUseProgram(0);
        }
        glEnable(GL_BLEND);

        if (!blendList.isEmpty()) {
            BetterUniformSetter blendVariant = NProgram.VARIANT_ALPHA_BLENDING;

            glUseProgram(blendVariant.getProgram());

            BetterUniformSetter.uniformMatrix4fv(blendVariant.locationOf(NProgram.UNIFORM_PROJECTION),
                    camera.getProjection()
            );
            BetterUniformSetter.uniformMatrix4fv(blendVariant.locationOf(NProgram.UNIFORM_VIEW),
                    camera.getView()
            );

            glUniform1i(blendVariant.locationOf(NProgram.UNIFORM_ANIMATION_ENABLED), 0);

            glUniform1i(blendVariant.locationOf(NProgram.UNIFORM_PARALLAX_ENABLED),
                    (PARALLAX_ENABLED ? 1 : 0)
            );

            for (int i = 0; i < renderableLights.length; i++) {
                NProgram.sendLight(blendVariant, renderableLights[i], i);
            }

            glUniform1i(blendVariant.locationOf(NProgram.UNIFORM_R_G_B_A_OR_H), 0);
            glUniform1i(blendVariant.locationOf(NProgram.UNIFORM_IE_NX_R_NY), 1);

            render(blendVariant, blendList);

            glUseProgram(0);
        }

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
                NProgram.sendMaterial(variant, new NProgram.NProgramMaterial(
                        material.getDiffuseColor(), material.getSpecularColor(),
                        material.getMinExponent(), material.getMaxExponent(),
                        material.getParallaxHeightCoefficient(),
                        material.getParallaxMinLayers(), material.getParallaxMaxLayers()
                ));
                lastMaterial = material;
            }

            if (!textures.equals(lastTextures)) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, textures.r_g_b_a_or_h());

                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, textures.ie_nx_r_ny());

                glUniform1i(
                        variant.locationOf(NProgram.UNIFORM_PARALLAX_SUPPORTED),
                        NBlendingMode.OPAQUE_WITH_HEIGHT_MAP.equals(textures.getBlendingMode()) ? 1 : 0
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
                            transformedBone.set(render.model).mul(boneMatrix).mul(offset);
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
