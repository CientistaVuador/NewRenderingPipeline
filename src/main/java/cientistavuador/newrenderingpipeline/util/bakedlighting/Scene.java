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

import cientistavuador.newrenderingpipeline.geometry.Geometry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class Scene {

    public static abstract class Light {

        private final Vector3f diffuse = new Vector3f(1f, 1f, 1f);

        private float lightSize = 0.02f;
        private String groupName = "";

        protected Light() {

        }

        public Vector3fc getDiffuse() {
            return diffuse;
        }

        public void setDiffuse(float r, float g, float b) {
            this.diffuse.set(r, g, b);
        }

        public void setDiffuse(Vector3fc diffuse) {
            setDiffuse(diffuse.x(), diffuse.y(), diffuse.z());
        }

        public float getLightSize() {
            return lightSize;
        }

        public void setLightSize(float lightSize) {
            this.lightSize = lightSize;
        }

        public float getLuminance() {
            return getDiffuse().length();
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            if (groupName == null) {
                groupName = "";
            }
            this.groupName = groupName;
        }

        public void calculateDirect(
                Vector3fc position, Vector3fc normal,
                Vector3f outputDirection, Vector3f outputColor,
                float directAttenuation
        ) {
            outputDirection.set(0f, 1f, 0f);
            outputColor.set(0f, 0f, 0f);
        }

        public void randomLightDirection(
                Vector3fc position, Vector3f outDirection
        ) {
            outDirection.set(0f, 1f, 0f);
        }
    }

    public static class DirectionalLight extends Light {

        private final Vector3f ambient = new Vector3f(0.2f, 0.3f, 0.4f);

        private final Vector3f direction = new Vector3f(0.5f, -2f, -1f).normalize();
        private final Vector3f directionNegated = new Vector3f(this.direction).negate();

        public DirectionalLight() {

        }

        public Vector3fc getDirection() {
            return direction;
        }

        public Vector3fc getDirectionNegated() {
            return directionNegated;
        }

        public void setDirection(float x, float y, float z) {
            this.direction.set(x, y, z).normalize();
            this.directionNegated.set(this.direction).negate();
        }

        public void setDirection(Vector3fc direction) {
            setDiffuse(direction.x(), direction.y(), direction.z());
        }

        public Vector3fc getAmbient() {
            return ambient;
        }

        public void setAmbient(float r, float g, float b) {
            this.ambient.set(r, g, b);
        }

        public void setAmbient(Vector3fc ambient) {
            setAmbient(ambient.x(), ambient.y(), ambient.z());
        }

        @Override
        public void calculateDirect(
                Vector3fc position, Vector3fc normal,
                Vector3f outputDirection, Vector3f outputColor,
                float directAttenuation
        ) {
            float diffuseFactor = Math.max(normal.dot(outputDirection.set(this.directionNegated)), 0f);
            outputColor.set(this.getDiffuse()).mul(diffuseFactor);
        }

        @Override
        public void randomLightDirection(
                Vector3fc position, Vector3f outDirection
        ) {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            float x;
            float y;
            float z;
            float dist;

            do {
                x = (random.nextFloat() * 2f) - 1f;
                y = (random.nextFloat() * 2f) - 1f;
                z = (random.nextFloat() * 2f) - 1f;
                dist = (x * x) + (y * y) + (z * z);
            } while (dist > 1f);

            outDirection.set(
                    x,
                    y,
                    z
            )
                    .mul(getLightSize())
                    .add(getDirectionNegated())
                    .normalize();
        }
    }

    private static void pointSpotLightDirection(
            Vector3fc lightPosition, float lightSize,
            Vector3fc position, Vector3f outDirection
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float dirX = lightPosition.x() - position.x();
        float dirY = lightPosition.y() - position.y();
        float dirZ = lightPosition.z() - position.z();
        float invlength = (float) (1f / Math.sqrt((dirX * dirX) + (dirY * dirY) + (dirZ * dirZ)));
        dirX *= invlength;
        dirY *= invlength;
        dirZ *= invlength;

        float x;
        float y;
        float z;
        float dist;

        do {
            x = (random.nextFloat() * 2f) - 1f;
            y = (random.nextFloat() * 2f) - 1f;
            z = (random.nextFloat() * 2f) - 1f;
            dist = (x * x) + (y * y) + (z * z);
        } while (dist > 1f);

        outDirection.set(x, y, z).normalize();
        
        if (outDirection.dot(-dirX, -dirY, -dirZ) < 0f) {
            outDirection.negate();
        }

        outDirection
                .mul(lightSize)
                .add(lightPosition)
                .sub(position);
    }

    public static class PointLight extends Light {

        private final Vector3f position = new Vector3f(-1f, 2f, 6f);
        private float bakeCutoff = 1f / 255f;

        public PointLight() {

        }

        public Vector3fc getPosition() {
            return position;
        }

        public void setPosition(float x, float y, float z) {
            this.position.set(x, y, z);
        }

        public void setPosition(Vector3fc position) {
            setPosition(position.x(), position.y(), position.z());
        }

        public float getBakeCutoff() {
            return bakeCutoff;
        }

        public void setBakeCutoff(float bakeCutoff) {
            this.bakeCutoff = bakeCutoff;
        }

        @Override
        public void calculateDirect(
                Vector3fc position, Vector3fc normal,
                Vector3f outputDirection, Vector3f outputColor,
                float directAttenuation
        ) {
            outputDirection.set(this.position).sub(position).normalize();
            float diffuseFactor = Math.max(normal.dot(outputDirection), 0f);

            float distance = this.position.distance(position);
            float attenuation = 1f / ((distance * distance) + directAttenuation);

            diffuseFactor *= attenuation;

            outputColor.set(this.getDiffuse()).mul(diffuseFactor);
        }

        @Override
        public void randomLightDirection(
                Vector3fc position, Vector3f outDirection
        ) {
            pointSpotLightDirection(getPosition(), getLightSize(), position, outDirection);
        }
    }

    public static class SpotLight extends Light {

        private final Vector3f position = new Vector3f(-1f, 2f, 6f);
        private float bakeCutoff = 1f / 255f;

        private final Vector3f direction = new Vector3f(0, -1, 0);
        private final Vector3f directionNegated = new Vector3f(this.direction).negate();
        private float innerCutoff = calculateRadiansCosine(25f);
        private float outerCutoff = calculateRadiansCosine(65f);

        public SpotLight() {
            
        }

        public Vector3fc getPosition() {
            return position;
        }

        public void setPosition(float x, float y, float z) {
            this.position.set(x, y, z);
        }

        public void setPosition(Vector3fc position) {
            setPosition(position.x(), position.y(), position.z());
        }

        public float getBakeCutoff() {
            return bakeCutoff;
        }

        public void setBakeCutoff(float bakeCutoff) {
            this.bakeCutoff = bakeCutoff;
        }

        private float calculateRadiansCosine(float angle) {
            return (float) Math.cos(Math.toRadians(angle));
        }
        
        public void setCutoffAngle(float cutoffAngle) {
            this.innerCutoff = calculateRadiansCosine(cutoffAngle);
        }
        
        public void setOuterCutoffAngle(float outerCutoffAngle) {
            this.outerCutoff = calculateRadiansCosine(outerCutoffAngle);
        }
        
        public float getInnerCutoff() {
            return innerCutoff;
        }
        
        public float getOuterCutoff() {
            return outerCutoff;
        }
        
        public void setInnerCutoff(float innerCutoff) {
            this.innerCutoff = innerCutoff;
        }
        
        public void setOuterCutoff(float outerCutoff) {
            this.outerCutoff = outerCutoff;
        }

        public Vector3fc getDirection() {
            return direction;
        }

        public Vector3fc getDirectionNegated() {
            return directionNegated;
        }

        public void setDirection(float x, float y, float z) {
            this.direction.set(x, y, z).normalize();
            this.directionNegated.set(this.direction).negate();
        }

        public void setDirection(Vector3fc dir) {
            setDirection(dir.x(), dir.y(), dir.z());
        }

        @Override
        public void calculateDirect(
                Vector3fc position, Vector3fc normal,
                Vector3f outputDirection, Vector3f outputColor,
                float directAttenuation
        ) {
            outputDirection.set(this.position).sub(position).normalize();
            float diffuseFactor = Math.max(normal.dot(outputDirection), 0f);

            float distance = this.position.distance(position);
            float attenuation = 1f / ((distance * distance) + directAttenuation);

            diffuseFactor *= attenuation;

            float theta = outputDirection.dot(this.directionNegated);
            float epsilon = this.innerCutoff - this.outerCutoff;
            float intensity = (theta - this.outerCutoff) / epsilon;
            intensity = Math.min(Math.max(intensity, 0f), 1f);

            diffuseFactor *= intensity;

            outputColor.set(this.getDiffuse()).mul(diffuseFactor);
        }

        @Override
        public void randomLightDirection(
                Vector3fc position, Vector3f outDirection
        ) {
            pointSpotLightDirection(getPosition(), getLightSize(), position, outDirection);
        }
    }

    public static class EmissiveLight extends Light {

        private int emissiveRays = 128;
        private float emissiveBlurArea = 3f;

        public EmissiveLight() {
            setDiffuse(10f, 10f, 10f);
        }

        public int getEmissiveRays() {
            return emissiveRays;
        }

        public void setEmissiveRaysPerSample(int emissiveRays) {
            this.emissiveRays = emissiveRays;
        }

        public float getEmissiveBlurArea() {
            return emissiveBlurArea;
        }

        public void setEmissiveBlurArea(float emissiveBlurArea) {
            this.emissiveBlurArea = emissiveBlurArea;
        }

        @Override
        public void calculateDirect(Vector3fc position, Vector3fc normal, Vector3f outputDirection, Vector3f outputColor, float directAttenuation) {
            outputColor.set(getDiffuse());
            outputDirection.set(0f, 1f, 0f);
        }

        @Override
        public void randomLightDirection(Vector3fc position, Vector3f outDirection) {
            outDirection.set(0f, 1f, 0f);
        }
    }

    private float pixelToWorldRatio = 6f;
    private final List<Geometry> geometries = new ArrayList<>();

    private final List<Light> lights = new ArrayList<>();

    private SamplingMode samplingMode = SamplingMode.SAMPLE_9;

    private boolean directLightingEnabled = true;
    private float directLightingAttenuation = 0.75f;

    private boolean shadowsEnabled = true;
    private int shadowRaysPerSample = 64;
    private float shadowBlurArea = 1f;

    private boolean indirectLightingEnabled = true;
    private int indirectRaysPerSample = 32;
    private int indirectBounces = 4;
    private float indirectLightingBlurArea = 4f;
    private float indirectLightReflectionFactor = 1f;

    private float rayOffset = 0.003f;
    private boolean fillDisabledValuesWithLightColors = false;

    private boolean fastModeEnabled = false;

    public Scene() {

    }

    public float getPixelToWorldRatio() {
        return pixelToWorldRatio;
    }

    public void setPixelToWorldRatio(float pixelToWorldRatio) {
        this.pixelToWorldRatio = pixelToWorldRatio;
    }

    public List<Geometry> getGeometries() {
        return geometries;
    }

    public List<Light> getLights() {
        return lights;
    }

    public SamplingMode getSamplingMode() {
        return samplingMode;
    }

    public void setSamplingMode(SamplingMode samplingMode) {
        this.samplingMode = samplingMode;
    }

    public boolean isDirectLightingEnabled() {
        return directLightingEnabled;
    }

    public void setDirectLightingEnabled(boolean directLightingEnabled) {
        this.directLightingEnabled = directLightingEnabled;
    }

    public boolean isShadowsEnabled() {
        return shadowsEnabled;
    }

    public void setShadowsEnabled(boolean shadowsEnabled) {
        this.shadowsEnabled = shadowsEnabled;
    }

    public int getShadowRaysPerSample() {
        return shadowRaysPerSample;
    }

    public void setShadowRaysPerSample(int shadowRaysPerSample) {
        this.shadowRaysPerSample = shadowRaysPerSample;
    }

    public float getShadowBlurArea() {
        return shadowBlurArea;
    }

    public void setShadowBlurArea(float shadowBlurArea) {
        this.shadowBlurArea = shadowBlurArea;
    }

    public boolean isIndirectLightingEnabled() {
        return indirectLightingEnabled;
    }

    public void setIndirectLightingEnabled(boolean indirectLightingEnabled) {
        this.indirectLightingEnabled = indirectLightingEnabled;
    }

    public int getIndirectRaysPerSample() {
        return indirectRaysPerSample;
    }

    public void setIndirectRaysPerSample(int indirectRaysPerSample) {
        this.indirectRaysPerSample = indirectRaysPerSample;
    }

    public int getIndirectBounces() {
        return indirectBounces;
    }

    public void setIndirectBounces(int indirectBounces) {
        this.indirectBounces = indirectBounces;
    }

    public float getIndirectLightingBlurArea() {
        return indirectLightingBlurArea;
    }

    public void setIndirectLightingBlurArea(float indirectLightingBlurArea) {
        this.indirectLightingBlurArea = indirectLightingBlurArea;
    }

    public float getIndirectLightReflectionFactor() {
        return indirectLightReflectionFactor;
    }

    public void setIndirectLightReflectionFactor(float indirectLightReflectionFactor) {
        this.indirectLightReflectionFactor = indirectLightReflectionFactor;
    }

    public float getRayOffset() {
        return rayOffset;
    }

    public void setRayOffset(float rayOffset) {
        this.rayOffset = rayOffset;
    }

    public boolean isFastModeEnabled() {
        return fastModeEnabled;
    }

    public void setFastModeEnabled(boolean fastModeEnabled) {
        this.fastModeEnabled = fastModeEnabled;
    }

    public void setFillDisabledValuesWithLightColors(boolean fillDisabledValuesWithLightColors) {
        this.fillDisabledValuesWithLightColors = fillDisabledValuesWithLightColors;
    }

    public boolean fillEmptyValuesWithLightColors() {
        return fillDisabledValuesWithLightColors;
    }

    public float getDirectLightingAttenuation() {
        return directLightingAttenuation;
    }

    public void setDirectLightingAttenuation(float directLightingAttenuation) {
        this.directLightingAttenuation = directLightingAttenuation;
    }

}
