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

import java.util.Objects;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class NMaterial {

    public static final Vector4fc DEFAULT_DIFFUSE_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Vector3fc DEFAULT_SPECULAR_COLOR = new Vector3f(1.0f, 1.0f, 1.0f);
    public static final Vector3fc DEFAULT_EMISSIVE_COLOR = new Vector3f(1.2f, 1.2f, 1.2f);
    public static final float DEFAULT_MIN_EXPONENT = 0.1f;
    public static final float DEFAULT_MAX_EXPONENT = 2048f;
    public static final float DEFAULT_PARALLAX_HEIGHT_COEFFICIENT = 0.065f;
    public static final float DEFAULT_PARALLAX_MIN_LAYERS = 8f;
    public static final float DEFAULT_PARALLAX_MAX_LAYERS = 32f;
    
    public static final NMaterial NULL_MATERIAL = new NMaterial("NULL_MATERIAL");
    
    static {
        NULL_MATERIAL.getDiffuseColor().set(1f, 1f, 1f, 1f);
        NULL_MATERIAL.getSpecularColor().set(0f, 0f, 0f);
        NULL_MATERIAL.setParallaxHeightCoefficient(0f);
    }
    
    private final String name;
    
    private NTextures textures = NTextures.NULL_TEXTURE;
    
    private final Vector4f diffuseColor = new Vector4f(DEFAULT_DIFFUSE_COLOR);
    private final Vector3f specularColor = new Vector3f(DEFAULT_SPECULAR_COLOR);
    private final Vector3f emissiveColor = new Vector3f(DEFAULT_EMISSIVE_COLOR);
    
    private float minExponent = DEFAULT_MIN_EXPONENT;
    private float maxExponent = DEFAULT_MAX_EXPONENT;
    private float parallaxHeightCoefficient = DEFAULT_PARALLAX_HEIGHT_COEFFICIENT;
    private float parallaxMinLayers = DEFAULT_PARALLAX_MIN_LAYERS;
    private float parallaxMaxLayers = DEFAULT_PARALLAX_MAX_LAYERS;
    
    public NMaterial(String name) {
        this(name, null);
    }
    
    public NMaterial(String name, NTextures textures) {
        this.name = name;
        if (textures == null) {
            textures = NTextures.NULL_TEXTURE;
        }
        this.textures = textures;
    }

    public String getName() {
        return name;
    }

    public NTextures getTextures() {
        return textures;
    }

    public void setTextures(NTextures textures) {
        if (textures == null) {
            textures = NTextures.NULL_TEXTURE;
        }
        this.textures = textures;
    }

    public Vector4f getDiffuseColor() {
        return diffuseColor;
    }

    public Vector3f getSpecularColor() {
        return specularColor;
    }

    public Vector3f getEmissiveColor() {
        return emissiveColor;
    }
    
    public float getMinExponent() {
        return minExponent;
    }

    public void setMinExponent(float minExponent) {
        this.minExponent = minExponent;
    }

    public float getMaxExponent() {
        return maxExponent;
    }
    
    public void setMaxExponent(float maxExponent) {
        this.maxExponent = maxExponent;
    }

    public float getParallaxHeightCoefficient() {
        return parallaxHeightCoefficient;
    }

    public void setParallaxHeightCoefficient(float parallaxHeightCoefficient) {
        this.parallaxHeightCoefficient = parallaxHeightCoefficient;
    }

    public float getParallaxMinLayers() {
        return parallaxMinLayers;
    }

    public void setParallaxMinLayers(float parallaxMinLayers) {
        this.parallaxMinLayers = parallaxMinLayers;
    }

    public float getParallaxMaxLayers() {
        return parallaxMaxLayers;
    }

    public void setParallaxMaxLayers(float parallaxMaxLayers) {
        this.parallaxMaxLayers = parallaxMaxLayers;
    }
    
    public boolean equalsPropertiesOnly(NMaterial other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (Float.floatToIntBits(this.minExponent) != Float.floatToIntBits(other.minExponent)) {
            return false;
        }
        if (Float.floatToIntBits(this.maxExponent) != Float.floatToIntBits(other.maxExponent)) {
            return false;
        }
        if (Float.floatToIntBits(this.parallaxHeightCoefficient) != Float.floatToIntBits(other.parallaxHeightCoefficient)) {
            return false;
        }
        if (Float.floatToIntBits(this.parallaxMinLayers) != Float.floatToIntBits(other.parallaxMinLayers)) {
            return false;
        }
        if (Float.floatToIntBits(this.parallaxMaxLayers) != Float.floatToIntBits(other.parallaxMaxLayers)) {
            return false;
        }
        if (!Objects.equals(this.diffuseColor, other.diffuseColor)) {
            return false;
        }
        if (!Objects.equals(this.specularColor, other.specularColor)) {
            return false;
        }
        return Objects.equals(this.emissiveColor, other.emissiveColor);
    }
    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.textures);
        hash = 37 * hash + Objects.hashCode(this.diffuseColor);
        hash = 37 * hash + Objects.hashCode(this.specularColor);
        hash = 37 * hash + Objects.hashCode(this.emissiveColor);
        hash = 37 * hash + Float.floatToIntBits(this.minExponent);
        hash = 37 * hash + Float.floatToIntBits(this.maxExponent);
        hash = 37 * hash + Float.floatToIntBits(this.parallaxHeightCoefficient);
        hash = 37 * hash + Float.floatToIntBits(this.parallaxMinLayers);
        hash = 37 * hash + Float.floatToIntBits(this.parallaxMaxLayers);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NMaterial other = (NMaterial) obj;
        if (!Objects.equals(this.textures, other.textures)) {
            return false;
        }
        return equalsPropertiesOnly(other);
    }
    
}
