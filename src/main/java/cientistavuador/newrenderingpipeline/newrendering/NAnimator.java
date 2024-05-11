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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class NAnimator {
    
    public static final Matrix4fc IDENTITY_MATRIX = new Matrix4f();
    
    public static final float UPDATE_RATE = 1f / 60f;

    private final N3DModel model;
    private final NAnimation animation;

    private final NBoneAnimation[] boneAnimations;
    private final Map<String, Integer> boneMap = new HashMap<>();

    private final String[] unusedBones;
    private final Matrix4f[] unusedBonesMatrices;
    private final Map<String, Integer> unusedBoneMap = new HashMap<>();
    
    private final Matrix4f[] localBoneMatrices;
    private final Matrix4f[] boneMatrices;

    private final int[] currentPositionKeys;
    private final int[] currentRotationKeys;
    private final int[] currentScalingKeys;

    private final Vector3f currentPosition = new Vector3f();
    private final Quaternionf currentRotation = new Quaternionf();
    private final Vector3f currentScaling = new Vector3f();

    private final Vector3f nextPosition = new Vector3f();
    private final Quaternionf nextRotation = new Quaternionf();
    private final Vector3f nextScaling = new Vector3f();

    private double tickCounter = 0f;
    private float animationCounter = 0f;
    private float animationSpeed = 1f;
    
    private boolean looping = true;

    public NAnimator(N3DModel model, String animation) {
        this.model = model;
        this.animation = model.getAnimation(animation);

        this.boneAnimations = new NBoneAnimation[this.animation.getNumberOfBoneAnimations()];
        for (int i = 0; i < this.boneAnimations.length; i++) {
            this.boneAnimations[i] = this.animation.getBoneAnimation(i);
            this.boneMap.put(this.boneAnimations[i].getBoneName(), i);
        }
        
        List<String> unusedList = new ArrayList<>();
        for (int totalBonesIndex = 0; totalBonesIndex < model.getNumberOfBones(); totalBonesIndex++) {
            String boneName = model.getBone(totalBonesIndex);
            if (!this.boneMap.containsKey(boneName)) {
                unusedList.add(boneName);
            }
        }
        
        this.unusedBones = unusedList.toArray(String[]::new);
        this.unusedBonesMatrices = new Matrix4f[this.unusedBones.length];
        for (int i = 0; i < this.unusedBones.length; i++) {
            this.unusedBoneMap.put(this.unusedBones[i], i);
            this.unusedBonesMatrices[i] = new Matrix4f();
        }
        
        this.localBoneMatrices = new Matrix4f[this.boneAnimations.length];
        this.boneMatrices = new Matrix4f[this.boneAnimations.length];
        for (int i = 0; i < this.boneAnimations.length; i++) {
            this.localBoneMatrices[i] = new Matrix4f();
            this.boneMatrices[i] = new Matrix4f();
        }
        
        this.currentPositionKeys = new int[this.boneAnimations.length];
        this.currentRotationKeys = new int[this.boneAnimations.length];
        this.currentScalingKeys = new int[this.boneAnimations.length];

        update();
    }

    private void resetKeys() {
        Arrays.fill(this.currentPositionKeys, 0);
        Arrays.fill(this.currentRotationKeys, 0);
        Arrays.fill(this.currentScalingKeys, 0);
    }

    public N3DModel getModel() {
        return model;
    }

    public NAnimation getAnimation() {
        return animation;
    }

    public Matrix4fc getBoneMatrix(String name) {
        Integer index = this.boneMap.get(name);
        if (index == null) {
            Integer unusedIndex = this.unusedBoneMap.get(name);
            if (unusedIndex == null) {
                return null;
            }
            return this.unusedBonesMatrices[unusedIndex];
        }
        return this.boneMatrices[index];
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public void setAnimationSpeed(float animationSpeed) {
        this.animationSpeed = animationSpeed;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isLooping() {
        return looping;
    }

    public void reset() {
        resetKeys();
        this.tickCounter = 0f;
        this.animationCounter = 0f;
    }

    public float getAnimationCounter() {
        return animationCounter;
    }
    
    public boolean isFinished() {
        return this.animationCounter >= this.animation.getDuration();
    }
    
    public void update(double tpf) {
        if (isFinished()) {
            if (this.looping) {
                reset();
            } else {
                return;
            }
        }

        this.tickCounter += tpf;

        while (this.tickCounter >= UPDATE_RATE) {
            update();
            this.animationCounter += (UPDATE_RATE * this.animationSpeed);
            this.tickCounter -= UPDATE_RATE;
        }
    }

    private void update() {
        updateLocalMatrices();
        updateMatrices();
    }

    private float timeLerpValue(float start, float end) {
        float lerp = (this.animationCounter - start) / (end - start);
        if (!Float.isFinite(lerp)) {
            return 0f;
        }
        return lerp;
    }

    private void updateLocalMatrices() {
        for (int i = 0; i < this.boneAnimations.length; i++) {
            NBoneAnimation boneAnimation = this.boneAnimations[i];
            Matrix4f localMatrix = this.localBoneMatrices[i].identity();

            int currentPositionKey = this.currentPositionKeys[i];
            int currentRotationKey = this.currentRotationKeys[i];
            int currentScalingKey = this.currentScalingKeys[i];
            
            float currentPositionTime = boneAnimation.getPositionTime(currentPositionKey);
            float currentRotationTime = boneAnimation.getRotationTime(currentRotationKey);
            float currentScalingTime = boneAnimation.getScalingTime(currentScalingKey);
            
            boolean hasNextPositionKey = (currentPositionKey + 1) < boneAnimation.getNumberOfPositions();
            boolean hasNextRotationKey = (currentRotationKey + 1) < boneAnimation.getNumberOfRotations();
            boolean hasNextScalingKey = (currentScalingKey + 1) < boneAnimation.getNumberOfScalings();

            float nextPositionTime = currentPositionTime;
            float nextRotationTime = currentRotationTime;
            float nextScalingTime = currentScalingTime;

            if (hasNextPositionKey) {
                nextPositionTime = boneAnimation.getPositionTime(currentPositionKey + 1);

                while (this.animationCounter > nextPositionTime) {
                    currentPositionKey++;
                    currentPositionTime = boneAnimation.getPositionTime(currentPositionKey);
                    nextPositionTime = currentPositionTime;
                    if ((currentPositionKey + 1) >= boneAnimation.getNumberOfPositions()) {
                        hasNextPositionKey = false;
                        break;
                    }
                    nextPositionTime = boneAnimation.getPositionTime(currentPositionKey + 1);
                }
            }

            if (hasNextRotationKey) {
                nextRotationTime = boneAnimation.getRotationTime(currentRotationKey + 1);

                while (this.animationCounter > nextRotationTime) {
                    currentRotationKey++;
                    currentRotationTime = boneAnimation.getRotationTime(currentRotationKey);
                    nextRotationTime = currentRotationTime;
                    if ((currentRotationKey + 1) >= boneAnimation.getNumberOfRotations()) {
                        hasNextRotationKey = false;
                        break;
                    }
                    nextRotationTime = boneAnimation.getRotationTime(currentRotationKey + 1);
                }
            }

            if (hasNextScalingKey) {
                nextScalingTime = boneAnimation.getScalingTime(currentScalingKey + 1);

                while (this.animationCounter > nextScalingTime) {
                    currentScalingKey++;
                    currentScalingTime = boneAnimation.getScalingTime(currentScalingKey);
                    nextScalingTime = currentScalingTime;
                    if ((currentScalingKey + 1) >= boneAnimation.getNumberOfScalings()) {
                        hasNextScalingKey = false;
                        break;
                    }
                    nextScalingTime = boneAnimation.getScalingTime(currentScalingKey + 1);
                }
            }

            float positionLerp = timeLerpValue(currentPositionTime, nextPositionTime);
            float rotationLerp = timeLerpValue(currentRotationTime, nextRotationTime);
            float scalingLerp = timeLerpValue(currentScalingTime, nextScalingTime);

            if (positionLerp > 1f) {
                currentPositionKey++;
                positionLerp = 0f;
                hasNextPositionKey = false;
            }

            if (rotationLerp > 1f) {
                currentRotationKey++;
                rotationLerp = 0f;
                hasNextRotationKey = false;
            }

            if (scalingLerp > 1f) {
                currentScalingKey++;
                scalingLerp = 0f;
                hasNextScalingKey = false;
            }

            boneAnimation.getPosition(currentPositionKey, this.currentPosition);
            boneAnimation.getRotation(currentRotationKey, this.currentRotation);
            boneAnimation.getScaling(currentScalingKey, this.currentScaling);

            this.nextPosition.set(this.currentPosition);
            this.nextRotation.set(this.currentRotation);
            this.nextScaling.set(this.currentScaling);

            if (hasNextPositionKey) {
                boneAnimation.getPosition(currentPositionKey + 1, this.nextPosition);
                this.currentPosition.lerp(this.nextPosition, positionLerp);
            }

            if (hasNextRotationKey) {
                boneAnimation.getRotation(currentRotationKey + 1, this.nextRotation);
                this.currentRotation.slerp(this.nextRotation, rotationLerp);
            }

            if (hasNextScalingKey) {
                boneAnimation.getScaling(currentScalingKey + 1, this.nextScaling);
                this.currentScaling.lerp(this.nextScaling, scalingLerp);
            }

            localMatrix
                    .translate(this.currentPosition)
                    .rotate(this.currentRotation)
                    .scale(this.currentScaling);
            
            this.currentPositionKeys[i] = currentPositionKey;
            this.currentRotationKeys[i] = currentRotationKey;
            this.currentScalingKeys[i] = currentScalingKey;
        }
    }

    private void recursiveTransform(N3DModelNode node, Matrix4f matrix) {
        if (node == null) {
            return;
        }

        Matrix4fc localMatrix;

        Integer boneIndex = this.boneMap.get(node.getName());
        if (boneIndex != null) {
            localMatrix = this.localBoneMatrices[boneIndex];
        } else {
            localMatrix = node.getTransformation();
        }
        
        localMatrix.mul(matrix, matrix);
        
        recursiveTransform(node.getParent(), matrix);
    }

    private void updateMatrices() {
        for (int i = 0; i < this.boneAnimations.length; i++) {
            String boneName = this.boneAnimations[i].getBoneName();
            Matrix4f matrix = this.boneMatrices[i].identity();

            recursiveTransform(this.model.getNode(boneName), matrix);
        }
        for (int i = 0; i < this.unusedBonesMatrices.length; i++) {
            String boneName = this.unusedBones[i];
            Matrix4f matrix = this.unusedBonesMatrices[i].identity();
            
            recursiveTransform(this.model.getNode(boneName), matrix);
        }
    }

}
