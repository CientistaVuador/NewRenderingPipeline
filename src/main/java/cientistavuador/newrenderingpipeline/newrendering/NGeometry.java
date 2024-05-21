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

import static cientistavuador.newrenderingpipeline.newrendering.NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class NGeometry {
    
    private N3DModel model = null;
    private int globalId = -1;
    private N3DModelNode parent = null;
    private int localId = -1;
    
    private final String name;
    private final NMesh mesh;

    private final Vector3f animatedAabbMin = new Vector3f();
    private final Vector3f animatedAabbMax = new Vector3f();
    private final Vector3f animatedAabbCenter = new Vector3f();
    private boolean animatedAabbGenerated = false;

    private NMaterial material = NMaterial.NULL_MATERIAL;

    public NGeometry(
            String name,
            NMesh mesh,
            NMaterial material,
            Vector3fc animatedMin, Vector3fc animatedMax
    ) {
        this.name = name;
        this.mesh = mesh;
        if (material == null) {
            material = NMaterial.NULL_MATERIAL;
        }
        this.material = material;

        if (animatedMin == null || animatedMax == null) {
            this.animatedAabbMin.set(mesh.getAabbMin());
            this.animatedAabbMax.set(mesh.getAabbMax());
            this.animatedAabbCenter.set(mesh.getAabbCenter());
        } else {
            this.animatedAabbMin.set(animatedMin);
            this.animatedAabbMax.set(animatedMax);
            this.animatedAabbCenter.set(animatedMin).add(animatedMax).mul(0.5f);
            this.animatedAabbGenerated = true;
        }
    }

    public NGeometry(String name, NMesh mesh, NMaterial material) {
        this(name, mesh, material, null, null);
    }

    public NGeometry(String name, NMesh mesh) {
        this(name, mesh, null);
    }
    
    protected void configure(N3DModel model, int globalId, N3DModelNode parent, int localId) {
        if (this.model != null || this.globalId != -1 || this.parent != null || this.localId != -1) {
            throw new IllegalStateException("This geometry was already configured! Geometry not unique exception.");
        }
        this.model = model;
        this.globalId = globalId;
        this.parent = parent;
        this.localId = localId;
    }
    
    public N3DModel getModel() {
        return model;
    }
    
    public int getGlobalId() {
        return globalId;
    }
    
    public N3DModelNode getParent() {
        return parent;
    }

    public int getLocalId() {
        return localId;
    }
    
    public String getName() {
        return name;
    }

    public NMesh getMesh() {
        return mesh;
    }

    public NMaterial getMaterial() {
        return material;
    }

    public void setMaterial(NMaterial material) {
        if (material == null) {
            material = NMaterial.NULL_MATERIAL;
        }
        this.material = material;
    }

    public void generateAnimatedAabb(N3DModel originalModel) {
        if (originalModel.getNumberOfAnimations() == 0) {
            this.animatedAabbGenerated = true;
            return;
        }

        N3DModelNode node = getParent();
        if (node == null) {
            throw new IllegalArgumentException("This geometry has no parent node/not inside a 3d model");
        }
        
        float[] vertices = this.mesh.getVertices();

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;

        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        Matrix4f totalBoneMatrix = new Matrix4f();
        Vector3f transformed = new Vector3f();
        Vector3f totalTransformation = new Vector3f();

        int[] bonesIds = new int[MAX_AMOUNT_OF_BONE_WEIGHTS];
        float[] bonesWeights = new float[MAX_AMOUNT_OF_BONE_WEIGHTS];

        for (int animationIndex = 0; animationIndex < originalModel.getNumberOfAnimations(); animationIndex++) {
            NAnimation animation = originalModel.getAnimation(animationIndex);

            NAnimator animator = new NAnimator(originalModel, animation.getName());
            animator.setLooping(false);
            while (!animator.isFinished()) {
                animator.update(NAnimator.UPDATE_RATE);

                for (int i = 0; i < vertices.length; i += NMesh.VERTEX_SIZE) {
                    float x = vertices[i + NMesh.OFFSET_POSITION_XYZ + 0];
                    float y = vertices[i + NMesh.OFFSET_POSITION_XYZ + 1];
                    float z = vertices[i + NMesh.OFFSET_POSITION_XYZ + 2];

                    bonesIds[0] = Float.floatToRawIntBits(vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 0]);
                    bonesIds[1] = Float.floatToRawIntBits(vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 1]);
                    bonesIds[2] = Float.floatToRawIntBits(vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 2]);
                    bonesIds[3] = Float.floatToRawIntBits(vertices[i + NMesh.OFFSET_BONE_IDS_XYZW + 3]);

                    bonesWeights[0] = vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 0];
                    bonesWeights[1] = vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 1];
                    bonesWeights[2] = vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 2];
                    bonesWeights[3] = vertices[i + NMesh.OFFSET_BONE_WEIGHTS_XYZW + 3];

                    totalTransformation.zero();
                    for (int j = 0; j < bonesIds.length; j++) {
                        int boneId = bonesIds[j];
                        float boneWeight = bonesWeights[j];

                        if (boneId >= 0) {
                            String bone = this.mesh.getBone(boneId);

                            Matrix4fc boneMatrix = animator.getBoneMatrix(bone);

                            totalBoneMatrix
                                    .set(boneMatrix)
                                    .mul(originalModel.getNode(bone).getToNodeSpace())
                                    .mul(node.getToRootSpace());

                            transformed.set(x, y, z);
                            totalBoneMatrix.transformProject(transformed);
                            transformed.mul(boneWeight);

                            totalTransformation.add(transformed);
                        }
                    }

                    minX = Math.min(minX, totalTransformation.x());
                    minY = Math.min(minY, totalTransformation.y());
                    minZ = Math.min(minZ, totalTransformation.z());

                    maxX = Math.max(maxX, totalTransformation.x());
                    maxY = Math.max(maxY, totalTransformation.y());
                    maxZ = Math.max(maxZ, totalTransformation.z());
                }
            }
        }

        this.animatedAabbMin.set(minX, minY, minZ);
        this.animatedAabbMax.set(maxX, maxY, maxZ);
        this.animatedAabbCenter.set(
                (minX * 0.5f) + (maxX * 0.5f),
                (minY * 0.5f) + (maxY * 0.5f),
                (minZ * 0.5f) + (maxZ * 0.5f)
        );

        this.animatedAabbGenerated = true;
    }

    public Vector3fc getAnimatedAabbMin() {
        return animatedAabbMin;
    }

    public Vector3fc getAnimatedAabbMax() {
        return animatedAabbMax;
    }

    public Vector3fc getAnimatedAabbCenter() {
        return animatedAabbCenter;
    }

    public boolean isAnimatedAabbGenerated() {
        return animatedAabbGenerated;
    }
    
}
