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

import cientistavuador.newrenderingpipeline.util.BetterUniformSetter;
import cientistavuador.newrenderingpipeline.util.ProgramCompiler;
import java.util.Map;
import org.joml.Matrix4fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class NProgram {

    public static final float DIFFUSE_STRENGTH = 0.95f;
    public static final float SPECULAR_STRENGTH = 0.05f;

    public static final float LIGHT_ATTENUATION = 0.75f;
    
    public static final int MAX_AMOUNT_OF_LIGHTS = 24;
    public static final int MAX_AMOUNT_OF_LIGHTMAPS = 32;

    public static final int NULL_LIGHT_TYPE = 0;
    public static final int DIRECTIONAL_LIGHT_TYPE = 1;
    public static final int POINT_LIGHT_TYPE = 2;
    public static final int SPOT_LIGHT_TYPE = 3;
    
    public static final int MAX_AMOUNT_OF_CUBEMAPS = 4;

    public static final class NProgramLight {

        public final int type;
        public final float x;
        public final float y;
        public final float z;
        public final float dirX;
        public final float dirY;
        public final float dirZ;
        public final float innerCone;
        public final float outerCone;
        public final float diffuseR;
        public final float diffuseG;
        public final float diffuseB;
        public final float specularR;
        public final float specularG;
        public final float specularB;
        public final float ambientR;
        public final float ambientG;
        public final float ambientB;

        public NProgramLight(
                int type,
                float x, float y, float z,
                float dirX, float dirY, float dirZ,
                float innerCone, float outerCone,
                float diffuseR, float diffuseG, float diffuseB,
                float specularR, float specularG, float specularB,
                float ambientR, float ambientG, float ambientB
        ) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.innerCone = innerCone;
            this.outerCone = outerCone;
            this.diffuseR = diffuseR;
            this.diffuseG = diffuseG;
            this.diffuseB = diffuseB;
            this.specularR = specularR;
            this.specularG = specularG;
            this.specularB = specularB;
            this.ambientR = ambientR;
            this.ambientG = ambientG;
            this.ambientB = ambientB;
        }

    }

    public static final NProgramLight NULL_LIGHT = new NProgramLight(
            NULL_LIGHT_TYPE,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f
    );

    public static final class NProgramMaterial {

        public final float diffuseColorR;
        public final float diffuseColorG;
        public final float diffuseColorB;
        public final float diffuseColorA;
        public final float specularColorR;
        public final float specularColorG;
        public final float specularColorB;
        public final float emissiveColorR;
        public final float emissiveColorG;
        public final float emissiveColorB;
        public final float reflectionColorR;
        public final float reflectionColorG;
        public final float reflectionColorB;
        public final float minExponent;
        public final float maxExponent;
        public final float parallaxHeightCoefficient;
        public final float parallaxMinLayers;
        public final float parallaxMaxLayers;

        public NProgramMaterial(
                float diffuseColorR, float diffuseColorG, float diffuseColorB, float diffuseColorA,
                float specularColorR, float specularColorG, float specularColorB,
                float emissiveColorR, float emissiveColorG, float emissiveColorB,
                float reflectionColorR, float reflectionColorG, float reflectionColorB,
                float minExponent, float maxExponent,
                float parallaxHeightCoefficient, float parallaxMinLayers, float parallaxMaxLayers
        ) {
            this.diffuseColorR = diffuseColorR;
            this.diffuseColorG = diffuseColorG;
            this.diffuseColorB = diffuseColorB;
            this.diffuseColorA = diffuseColorA;
            this.specularColorR = specularColorR;
            this.specularColorG = specularColorG;
            this.specularColorB = specularColorB;
            this.emissiveColorR = emissiveColorR;
            this.emissiveColorG = emissiveColorG;
            this.emissiveColorB = emissiveColorB;
            this.reflectionColorR = reflectionColorR;
            this.reflectionColorG = reflectionColorG;
            this.reflectionColorB = reflectionColorB;
            this.minExponent = minExponent;
            this.maxExponent = maxExponent;
            this.parallaxHeightCoefficient = parallaxHeightCoefficient;
            this.parallaxMinLayers = parallaxMinLayers;
            this.parallaxMaxLayers = parallaxMaxLayers;
        }

    }

    public static final NProgramMaterial NULL_MATERIAL = new NProgramMaterial(
            NMaterial.DEFAULT_DIFFUSE_COLOR.x(), NMaterial.DEFAULT_DIFFUSE_COLOR.y(), NMaterial.DEFAULT_DIFFUSE_COLOR.z(), NMaterial.DEFAULT_DIFFUSE_COLOR.w(),
            NMaterial.DEFAULT_SPECULAR_COLOR.x(), NMaterial.DEFAULT_SPECULAR_COLOR.y(), NMaterial.DEFAULT_SPECULAR_COLOR.z(),
            NMaterial.DEFAULT_EMISSIVE_COLOR.x(), NMaterial.DEFAULT_EMISSIVE_COLOR.y(), NMaterial.DEFAULT_EMISSIVE_COLOR.z(),
            NMaterial.DEFAULT_REFLECTION_COLOR.x(), NMaterial.DEFAULT_REFLECTION_COLOR.y(), NMaterial.DEFAULT_REFLECTION_COLOR.z(),
            NMaterial.DEFAULT_MIN_EXPONENT, NMaterial.DEFAULT_MAX_EXPONENT,
            NMaterial.DEFAULT_PARALLAX_HEIGHT_COEFFICIENT,
            NMaterial.DEFAULT_PARALLAX_MIN_LAYERS, NMaterial.DEFAULT_PARALLAX_MAX_LAYERS
    );

    public static final BetterUniformSetter VARIANT_ALPHA_TESTING;
    public static final BetterUniformSetter VARIANT_ALPHA_BLENDING;

    private static final String VERTEX_SHADER
            = 
            """
            layout (location = VAO_INDEX_POSITION_XYZ) in vec3 vertexPosition;
            layout (location = VAO_INDEX_TEXTURE_XY) in vec2 vertexTexture;
            layout (location = VAO_INDEX_LIGHTMAP_TEXTURE_XY) in vec2 vertexLightmapTexture;
            layout (location = VAO_INDEX_NORMAL_XYZ) in vec3 vertexNormal;
            layout (location = VAO_INDEX_TANGENT_XYZ) in vec3 vertexTangent;
            layout (location = VAO_INDEX_BONE_IDS_XYZW) in ivec4 vertexBoneIds;
            layout (location = VAO_INDEX_BONE_WEIGHTS_XYZW) in vec4 vertexBoneWeights;
            
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            uniform mat3 normalModel;
            
            uniform mat4 boneMatrices[MAX_AMOUNT_OF_BONES + 1];
            
            out VertexData {
                vec3 worldPosition;
                vec2 worldTexture;
                vec2 worldLightmapTexture;
                vec3 worldNormal;
                
                mat3 TBN;
                vec3 tangentPosition;
            } outVertex;
            
            void main() {
                vec3 localTangent = vec3(0.0);
                vec3 localNormal = vec3(0.0);
                vec4 localPosition = vec4(0.0);
              
                for (int i = 0; i < MAX_AMOUNT_OF_BONE_WEIGHTS; i++) {
                    int boneId = vertexBoneIds[i] + 1;
                    float weight = vertexBoneWeights[i];
                    
                    mat4 boneModel = boneMatrices[boneId];
                    mat3 normalBoneModel = mat3(boneModel);
                    
                    localTangent += normalBoneModel * vertexTangent * weight;
                    localNormal += normalBoneModel * vertexNormal * weight;
                    localPosition += boneModel * vec4(vertexPosition, 1.0) * weight;
                }
                
                vec3 tangent = normalize(normalModel * localTangent);
                vec3 normal = normalize(normalModel * localNormal);
                vec4 worldPosition = model * localPosition;
                
                outVertex.worldPosition = worldPosition.xyz;
                outVertex.worldTexture = vertexTexture;
                outVertex.worldLightmapTexture = vertexLightmapTexture;
                outVertex.worldNormal = normal;
                
                outVertex.TBN = mat3(tangent, cross(normal, tangent), normal);
                outVertex.tangentPosition = transpose(outVertex.TBN) * outVertex.worldPosition;
                
                gl_Position = projection * view * worldPosition;
            }
            """;

    private static final String FRAGMENT_SHADER
            = 
            """
            #if defined(VARIANT_ALPHA_TESTING)
            #define IS_ALPHA_TESTING
            #endif
            
            #if defined(VARIANT_ALPHA_BLENDING)
            #define IS_ALPHA_BLENDING
            #endif
            
            uniform bool reflectionsDebug;
            uniform bool hdrOutput;
            
            //material textures
            uniform sampler2D r_g_b_a;
            uniform sampler2D ht_rg_mt_nx;
            uniform sampler2D er_eg_eb_ny;
            
            uniform bool parallaxSupported;
            uniform bool parallaxEnabled;
            
            //lightmaps texture
            uniform float lightmapIntensity[MAX_AMOUNT_OF_LIGHTMAPS];
            uniform sampler2DArray lightmaps;
            
            //reflection cubemaps
            uniform samplerCube reflectionCubemap_0;
            uniform samplerCube reflectionCubemap_1;
            uniform samplerCube reflectionCubemap_2;
            uniform samplerCube reflectionCubemap_3;
            
            uniform bool reflectionsSupported;
            uniform bool reflectionsEnabled;
            
            //POSITIVE_X
            //NEGATIVE_X
            //POSITIVE_Y
            //NEGATIVE_Y
            //POSITIVE_Z
            //NEGATIVE_Z
            uniform vec3 ambientCube[NUMBER_OF_CUBE_SIDES] = vec3[] (
                vec3(0.0),
                vec3(0.0),
                vec3(0.0),
                vec3(0.0),
                vec3(0.0),
                vec3(0.0)
            );
            
            struct FresnelOutline {
                bool enabled;
                float exponent;
                vec3 color;
            };
            
            uniform FresnelOutline fresnelOutline;
            
            struct ParallaxCubemap {
                bool enabled;
                float intensity;
                vec3 position;
                mat4 worldToLocal;
            };
            
            uniform ParallaxCubemap parallaxCubemaps[MAX_AMOUNT_OF_CUBEMAPS];
            
            struct Material {
                vec4 diffuseColor;
                vec3 specularColor;
                vec3 emissiveColor;
                vec3 reflectionColor;
                float minExponent;
                float maxExponent;
                float parallaxHeightCoefficient;
                float parallaxMinLayers;
                float parallaxMaxLayers;
            };
            
            uniform Material material;
            
            struct Light {
                int type;
                vec3 position;
                vec3 direction;
                float innerCone;
                float outerCone;
                vec3 diffuse;
                vec3 specular;
                vec3 ambient;
            };
            
            uniform Light lights[MAX_AMOUNT_OF_LIGHTS];
            
            in VertexData {
                vec3 worldPosition;
                vec2 worldTexture;
                vec2 worldLightmapTexture;
                vec3 worldNormal;
                
                mat3 TBN;
                vec3 tangentPosition;
            } inVertex;
            
            layout (location = 0) out vec4 outputFragColor;
            
            vec2 parallaxMapping(
                vec2 uv,
                vec3 tangentPosition,
                float minLayers,
                float maxLayers,
                float heightScale
            ) {
                vec3 tangentViewDirection = normalize(-tangentPosition);
                
                float numLayers = mix(maxLayers, minLayers, max(dot(vec3(0.0, 0.0, 1.0), tangentViewDirection), 0.0));
                
                float layerDepth = 1.0 / numLayers;
                float currentLayerDepth = 0.0;
                
                vec2 scaledViewDirection = tangentViewDirection.xy * heightScale;
                vec2 deltaUv = scaledViewDirection / numLayers;
                
                vec2 currentUv = uv;
                float currentDepth = 1.0 - texture(ht_rg_mt_nx, currentUv)[0];
                
                while (currentLayerDepth < currentDepth) {
                    currentUv -= deltaUv;
                    currentDepth = 1.0 - texture(ht_rg_mt_nx, currentUv)[0];
                    currentLayerDepth += layerDepth;
                }
                 
                vec2 previousUv = currentUv + deltaUv;
                
                float afterDepth = currentDepth - currentLayerDepth;
                float beforeDepth = (1.0 - texture(ht_rg_mt_nx, previousUv)[0]) - currentLayerDepth + layerDepth;
                
                float weight = afterDepth / (afterDepth - beforeDepth);
                vec2 finalUv = (previousUv * weight) + (currentUv * (1.0 - weight));
                
                return finalUv;
            }
            
            vec3 calculateLight(
                Light light,
                vec3 diffuseColor,
                vec3 specularColor,
                float exponent,
                float normalizationFactor,
                vec3 normal,
                vec3 fragDirection,
                vec3 worldPosition
            ) {
                int lightType = light.type;
                
                vec3 oppositeLightDirection;
                if (lightType == DIRECTIONAL_LIGHT_TYPE) {
                    oppositeLightDirection = normalize(-light.direction);
                } else {
                    oppositeLightDirection = normalize(light.position - worldPosition);
                }
                
                vec3 halfwayDirection = normalize(oppositeLightDirection + fragDirection);
                
                float diffuseFactor = max(dot(normal, oppositeLightDirection), 0.0);
                float specularFactor = pow(max(dot(normal, halfwayDirection), 0.0), exponent) * diffuseFactor;
                
                vec3 diffuse = light.diffuse * diffuseFactor * diffuseColor;
                vec3 specular = light.specular * specularFactor * specularColor * normalizationFactor;
                vec3 ambient = light.ambient * diffuseColor;
                
                if (lightType != DIRECTIONAL_LIGHT_TYPE) {
                    float distance = length(light.position - worldPosition);
                    float pointAttenuation = 1.0 / ((distance * distance) + LIGHT_ATTENUATION);
                    
                    diffuse *= pointAttenuation;
                    specular *= pointAttenuation;
                    ambient *= pointAttenuation;
                    
                    if (lightType == SPOT_LIGHT_TYPE) {
                        float theta = dot(oppositeLightDirection, normalize(-light.direction));
                        float epsilon = light.innerCone - light.outerCone;
                        float spotIntensity = clamp((theta - light.outerCone) / epsilon, 0.0, 1.0);
                        
                        diffuse *= spotIntensity;
                        specular *= spotIntensity;
                    }
                }
                
                return diffuse + specular + ambient;
            }
            
            vec3 ambientLight(vec3 normal) {
                vec3 normalSquared = normal * normal;
                ivec3 negative = ivec3(normal.x < 0.0, normal.y < 0.0, normal.z < 0.0);
                vec3 ambient = normalSquared.x * ambientCube[negative.x]
                                + normalSquared.y * ambientCube[negative.y + 2]
                                + normalSquared.z * ambientCube[negative.z + 4];
                return ambient;
            }
            
            vec4 sampleCubemap(int index, vec3 direction, float lod) {
                switch (index) {
                    case 0:
                        return textureLod(reflectionCubemap_0, direction, lod);
                    case 1:
                        return textureLod(reflectionCubemap_1, direction, lod);
                    case 2:
                        return textureLod(reflectionCubemap_2, direction, lod);
                    case 3:
                        return textureLod(reflectionCubemap_3, direction, lod);
                }
                return vec4(0.0, 0.0, 0.0, 1.0);
            }
            
            float sampleCubemapMiplevels(int index) {
                float mipLevels = 0.0;
                ivec2 cubemapSize = ivec2(0);
                switch (index) {
                    case 0:
                        #ifdef SUPPORTED_430
                        mipLevels = float(textureQueryLevels(reflectionCubemap_0));
                        #else
                        cubemapSize = textureSize(reflectionCubemap_0, 0);
                        mipLevels = 1.0 + floor(log2(max(float(cubemapSize.x), float(cubemapSize.y))));
                        #endif
                        return mipLevels;
                    case 1:
                        #ifdef SUPPORTED_430
                        mipLevels = float(textureQueryLevels(reflectionCubemap_1));
                        #else
                        cubemapSize = textureSize(reflectionCubemap_1, 0);
                        mipLevels = 1.0 + floor(log2(max(float(cubemapSize.x), float(cubemapSize.y))));
                        #endif
                        return mipLevels;
                    case 2:
                        #ifdef SUPPORTED_430
                        mipLevels = float(textureQueryLevels(reflectionCubemap_2));
                        #else
                        cubemapSize = textureSize(reflectionCubemap_2, 0);
                        mipLevels = 1.0 + floor(log2(max(float(cubemapSize.x), float(cubemapSize.y))));
                        #endif
                        return mipLevels;
                    case 3:
                        #ifdef SUPPORTED_430
                        mipLevels = float(textureQueryLevels(reflectionCubemap_3));
                        #else
                        cubemapSize = textureSize(reflectionCubemap_3, 0);
                        mipLevels = 1.0 + floor(log2(max(float(cubemapSize.x), float(cubemapSize.y))));
                        #endif
                        return mipLevels;
                }
                return 0.0;
            }
            
            vec3 computeReflection(
                vec3 fragPosition,
                vec3 fragDirection,
                vec3 normal,
                vec3 metallicColor,
                float roughness,
                float metallic
            ) {
                float materialRoughness = pow(roughness, 1.0/2.41);
                float fresnel = (1.0 - materialRoughness) * pow(1.0 - max(dot(fragDirection, normal), 0.0), 5.0);
                
                vec3 totalReflection = vec3(0.0);
                int count = 0;
                
                for (int i = 0; i < MAX_AMOUNT_OF_CUBEMAPS; i++) {
                    ParallaxCubemap parallaxCubemap = parallaxCubemaps[i];
                    
                    if (!parallaxCubemap.enabled) {
                        continue;
                    }
                    
                    vec3 localPosition = (parallaxCubemap.worldToLocal * vec4(fragPosition, 1.0)).xyz;
                    
                    vec3 absLocalPosition = abs(localPosition);
                    if (max(absLocalPosition.x, max(absLocalPosition.y, absLocalPosition.z)) > 1.0) {
                        continue;
                    }
                    
                    vec3 reflectedDirection = reflect(-fragDirection, normal);
                    vec3 localDirection = mat3(parallaxCubemap.worldToLocal) * reflectedDirection;
                    
                    vec3 firstPlane = (vec3(-1.0) - localPosition) / localDirection;
                    vec3 secondPlane = (vec3(1.0) - localPosition) / localDirection;
                    
                    vec3 furthestPlane = max(firstPlane, secondPlane);
                    
                    float distance = min(furthestPlane.x, min(furthestPlane.y, furthestPlane.z));
                    
                    float mipLevels = sampleCubemapMiplevels(i);
                    
                    float lodLevel = mipLevels * materialRoughness;
                    float lodFactor = lodLevel - floor(lodLevel);
                    
                    vec3 intersectionPosition = fragPosition + (reflectedDirection * distance);
                    reflectedDirection = normalize(intersectionPosition - parallaxCubemap.position);
                    
                    vec3 reflectedFloor = sampleCubemap(i, reflectedDirection, floor(lodLevel)).rgb;
                    vec3 reflectedCeil = sampleCubemap(i, reflectedDirection, ceil(lodLevel)).rgb;
                    vec3 reflectedColor = mix(reflectedFloor, reflectedCeil, lodFactor) * parallaxCubemap.intensity;
                    
                    vec3 metallicReflection = reflectedColor * metallicColor;
                    vec3 nonmetallicReflection = reflectedColor * fresnel;
                    
                    vec3 reflection = material.reflectionColor * mix(nonmetallicReflection, metallicReflection, metallic);
                    
                    totalReflection += reflection;
                    count++;
                }
                
                if (count != 0) {
                    totalReflection /= float(count);
                }
                
                return totalReflection;
            }
            
            vec3 ACESFilm(vec3 rgb) {
                float a = 2.51;
                float b = 0.03;
                float c = 2.43;
                float d = 0.59;
                float e = 0.14;
                return (rgb*(a*rgb+b))/(rgb*(c*rgb+d)+e);
            }
            
            vec3 gammaCorrection(vec3 rgb) {
                return pow(rgb, vec3(1.0/2.2));
            }
            
            void main() {
                vec4 finalColor = vec4(0.0, 0.0, 0.0, 1.0);
                
                vec2 textureUv = inVertex.worldTexture;
                float heightScale = material.parallaxHeightCoefficient;
                
                if (parallaxSupported && parallaxEnabled && heightScale > 0.0) {
                    textureUv = parallaxMapping(inVertex.worldTexture, inVertex.tangentPosition, material.parallaxMinLayers, material.parallaxMaxLayers, material.parallaxHeightCoefficient);
                }
                
                vec4 rgba = texture(r_g_b_a, textureUv);
                
                finalColor.a = rgba.a * material.diffuseColor.a;
                #ifdef IS_ALPHA_TESTING
                if (finalColor.a < 0.5) {
                    discard;
                }
                #endif
                
                vec4 hrmnx = texture(ht_rg_mt_nx, textureUv);
                vec4 eregebny = texture(er_eg_eb_ny, textureUv);
                
                vec3 vertexNormal = normalize(inVertex.worldNormal);
                mat3 TBN = inVertex.TBN;
                float nx = (hrmnx[3] * 2.0) - 1.0;
                float ny = (eregebny[3] * 2.0) - 1.0;
                vec3 normal = normalize(TBN * vec3(nx, ny, sqrt(1.0 - (nx * nx) - (ny * ny))));
                
                float roughness = hrmnx[1];
                float metallic = hrmnx[2];
                
                vec3 worldPosition = inVertex.worldPosition;
                vec3 fragDirection = normalize(-worldPosition);
                
                float exponent = (pow((material.maxExponent - material.minExponent) + 1.0, 1.0 - roughness) - 1.0) + material.minExponent;
                float normalizationFactor = ((exponent + 2.0) * (exponent + 4.0)) / (8.0 * PI * (pow(2.0, -exponent * 0.5) + exponent));
                
                vec3 metallicColor = material.diffuseColor.rgb * rgba.rgb;
                
                vec3 diffuseColor = metallicColor;
                vec3 specularColor = material.specularColor;
                
                float metallicStrength = (reflectionsEnabled ? metallic : 0.0);
                
                float diffuseStrength = mix(DIFFUSE_STRENGTH, 1.0 - DIFFUSE_STRENGTH, metallicStrength);
                float specularStrength = mix(SPECULAR_STRENGTH, 1.0 - SPECULAR_STRENGTH, metallicStrength);
                
                specularColor = mix(specularColor, diffuseColor, metallicStrength);
                
                diffuseColor *= diffuseStrength;
                specularColor *= specularStrength;
                
                float lightmapAo = pow(max(dot(vertexNormal, normal), 0.0), 1.2);
                int amountOfLightmaps = textureSize(lightmaps, 0).z;
                for (int i = 0; i < amountOfLightmaps; i++) {
                    float intensity = 1.0;
                    if (i < MAX_AMOUNT_OF_LIGHTMAPS) {
                        intensity = lightmapIntensity[i];
                    }
                    finalColor.rgb += texture(lightmaps, vec3(inVertex.worldLightmapTexture, float(i))).rgb * intensity * diffuseColor * lightmapAo;
                }
                
                for (int i = 0; i < MAX_AMOUNT_OF_LIGHTS; i++) {
                    Light light = lights[i];
                    if (light.type == NULL_LIGHT_TYPE) {
                        break;
                    }
                    finalColor.rgb += calculateLight(
                        light,
                        diffuseColor, specularColor,
                        exponent, normalizationFactor,
                        normal, fragDirection,
                        worldPosition
                    );
                }
                
                finalColor.rgb += ambientLight(normal) * diffuseColor;
                finalColor.rgb += eregebny.rgb * material.emissiveColor;
                
                if (reflectionsSupported && reflectionsEnabled) {
                    finalColor.rgb += computeReflection(worldPosition, fragDirection, normal, metallicColor, roughness, metallic);
                }
                
                if (reflectionsDebug) {
                    finalColor.rgb = computeReflection(worldPosition, fragDirection, vertexNormal, vec3(1.0), 0.0, 1.0);
                }
                
                if (!hdrOutput) {
                    finalColor.rgb = gammaCorrection(ACESFilm(finalColor.rgb));
                }
                
                if (fresnelOutline.enabled) {
                    float fresnel = pow(1.0 - max(dot(fragDirection, vertexNormal), 0.0), fresnelOutline.exponent);
                    finalColor.rgb = mix(finalColor.rgb, fresnelOutline.color, fresnel);
                }
                
                #ifdef IS_ALPHA_TESTING
                outputFragColor = vec4(finalColor.rgb, 1.0);
                #endif
                
                #ifdef IS_ALPHA_BLENDING
                outputFragColor = finalColor;
                #endif
            }
            """;

    private static final ProgramCompiler.ShaderConstant[] CONSTANTS = {
        new ProgramCompiler.ShaderConstant("VAO_INDEX_POSITION_XYZ", NMesh.VAO_INDEX_POSITION_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_TEXTURE_XY", NMesh.VAO_INDEX_TEXTURE_XY),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_LIGHTMAP_TEXTURE_XY", NMesh.VAO_INDEX_LIGHTMAP_TEXTURE_XY),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_NORMAL_XYZ", NMesh.VAO_INDEX_NORMAL_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_TANGENT_XYZ", NMesh.VAO_INDEX_TANGENT_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_BONE_IDS_XYZW", NMesh.VAO_INDEX_BONE_IDS_XYZW),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_BONE_WEIGHTS_XYZW", NMesh.VAO_INDEX_BONE_WEIGHTS_XYZW),
        new ProgramCompiler.ShaderConstant("PI", Math.PI),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_LIGHTS", MAX_AMOUNT_OF_LIGHTS),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_LIGHTMAPS", MAX_AMOUNT_OF_LIGHTMAPS),
        new ProgramCompiler.ShaderConstant("NULL_LIGHT_TYPE", NULL_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("DIRECTIONAL_LIGHT_TYPE", DIRECTIONAL_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("POINT_LIGHT_TYPE", POINT_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("SPOT_LIGHT_TYPE", SPOT_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("LIGHT_ATTENUATION", LIGHT_ATTENUATION),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_BONES", NMesh.MAX_AMOUNT_OF_BONES),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_BONE_WEIGHTS", NMesh.MAX_AMOUNT_OF_BONE_WEIGHTS),
        new ProgramCompiler.ShaderConstant("DIFFUSE_STRENGTH", DIFFUSE_STRENGTH),
        new ProgramCompiler.ShaderConstant("SPECULAR_STRENGTH", SPECULAR_STRENGTH),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_CUBEMAPS", MAX_AMOUNT_OF_CUBEMAPS),
        new ProgramCompiler.ShaderConstant("NUMBER_OF_CUBE_SIDES", NCubemap.SIDES)
    };

    static {
        Map<String, Integer> programs = ProgramCompiler.compile(
                VERTEX_SHADER, FRAGMENT_SHADER,
                new String[]{
                    "ALPHA_TESTING",
                    "ALPHA_BLENDING",
                },
                CONSTANTS
        );

        VARIANT_ALPHA_TESTING = new BetterUniformSetter(programs.get("ALPHA_TESTING"));
        VARIANT_ALPHA_BLENDING = new BetterUniformSetter(programs.get("ALPHA_BLENDING"));
    }

    public static final String UNIFORM_PROJECTION = "projection";
    public static final String UNIFORM_VIEW = "view";
    public static final String UNIFORM_MODEL = "model";
    public static final String UNIFORM_NORMAL_MODEL = "normalModel";
    public static final String UNIFORM_R_G_B_A = "r_g_b_a";
    public static final String UNIFORM_HT_RG_MT_NX = "ht_rg_mt_nx";
    public static final String UNIFORM_ER_EG_EB_NY = "er_eg_eb_ny";
    public static final String UNIFORM_LIGHTMAPS = "lightmaps";
    public static final String UNIFORM_PARALLAX_SUPPORTED = "parallaxSupported";
    public static final String UNIFORM_PARALLAX_ENABLED = "parallaxEnabled";
    public static final String UNIFORM_REFLECTION_CUBEMAP_0 = "reflectionCubemap_0";
    public static final String UNIFORM_REFLECTION_CUBEMAP_1 = "reflectionCubemap_1";
    public static final String UNIFORM_REFLECTION_CUBEMAP_2 = "reflectionCubemap_2";
    public static final String UNIFORM_REFLECTION_CUBEMAP_3 = "reflectionCubemap_3";
    public static final String UNIFORM_REFLECTIONS_SUPPORTED = "reflectionsSupported";
    public static final String UNIFORM_REFLECTIONS_ENABLED = "reflectionsEnabled";
    public static final String UNIFORM_HDR_OUTPUT = "hdrOutput";
    public static final String UNIFORM_REFLECTIONS_DEBUG = "reflectionsDebug";

    public static void sendMaterial(BetterUniformSetter uniforms, NProgramMaterial material) {
        if (material == null) {
            material = NULL_MATERIAL;
        }
        glUniform4f(uniforms.locationOf("material.diffuseColor"), material.diffuseColorR, material.diffuseColorG, material.diffuseColorB, material.diffuseColorA);
        glUniform3f(uniforms.locationOf("material.specularColor"), material.specularColorR, material.specularColorG, material.specularColorB);
        glUniform3f(uniforms.locationOf("material.emissiveColor"), material.emissiveColorR, material.emissiveColorG, material.emissiveColorB);
        glUniform3f(uniforms.locationOf("material.reflectionColor"), material.reflectionColorR, material.reflectionColorG, material.reflectionColorB);
        glUniform1f(uniforms.locationOf("material.minExponent"), material.minExponent);
        glUniform1f(uniforms.locationOf("material.maxExponent"), material.maxExponent);
        glUniform1f(uniforms.locationOf("material.parallaxHeightCoefficient"), material.parallaxHeightCoefficient);
        glUniform1f(uniforms.locationOf("material.parallaxMinLayers"), material.parallaxMinLayers);
        glUniform1f(uniforms.locationOf("material.parallaxMinLayers"), material.parallaxMaxLayers);
    }

    public static void sendLight(BetterUniformSetter uniforms, NProgramLight light, int index) {
        if (index < 0 || index > MAX_AMOUNT_OF_LIGHTS) {
            throw new IllegalArgumentException("Out of bounds index: " + index);
        }
        if (light == null) {
            light = NULL_LIGHT;
        }
        glUniform1i(uniforms.locationOf("lights[" + index + "].type"), light.type);
        if (light != NULL_LIGHT) {
            glUniform3f(uniforms.locationOf("lights[" + index + "].position"), light.x, light.y, light.z);
            glUniform3f(uniforms.locationOf("lights[" + index + "].direction"), light.dirX, light.dirY, light.dirZ);
            glUniform1f(uniforms.locationOf("lights[" + index + "].innerCone"), light.innerCone);
            glUniform1f(uniforms.locationOf("lights[" + index + "].outerCone"), light.outerCone);
            glUniform3f(uniforms.locationOf("lights[" + index + "].diffuse"), light.diffuseR, light.diffuseG, light.diffuseB);
            glUniform3f(uniforms.locationOf("lights[" + index + "].specular"), light.specularR, light.specularG, light.specularB);
            glUniform3f(uniforms.locationOf("lights[" + index + "].ambient"), light.ambientR, light.ambientG, light.ambientB);
        }
    }

    public static void sendLightmapIntensity(BetterUniformSetter uniforms, float intensity, int index) {
        if (index < 0 || index > MAX_AMOUNT_OF_LIGHTMAPS) {
            throw new IllegalArgumentException("Out of bounds index: " + index);
        }
        glUniform1f(uniforms.locationOf("lightmapIntensity[" + index + "]"), intensity);
    }

    public static void sendBoneMatrix(BetterUniformSetter uniforms, Matrix4fc matrix, int boneId) {
        BetterUniformSetter.uniformMatrix4fv(uniforms.locationOf("boneMatrices[" + (boneId + 1) + "]"), matrix);
    }

    public static void sendParallaxCubemapInfo(BetterUniformSetter uniforms, int index, boolean enabled, float intensity, float x, float y, float z, Matrix4fc worldToLocal) {
        glUniform1i(uniforms.locationOf("parallaxCubemaps["+index+"].enabled"), (enabled ? 1 : 0));
        if (enabled) {
            glUniform1f(uniforms.locationOf("parallaxCubemaps["+index+"].intensity"), intensity);
            glUniform3f(uniforms.locationOf("parallaxCubemaps["+index+"].position"), x, y, z);
            BetterUniformSetter.uniformMatrix4fv(uniforms.locationOf("parallaxCubemaps["+index+"].worldToLocal"), worldToLocal);
        }
    }

    public static void sendFresnelOutlineInfo(BetterUniformSetter uniforms, boolean enabled, float exponent, float r, float g, float b) {
        glUniform1i(uniforms.locationOf("fresnelOutline.enabled"), (enabled ? 1 : 0));
        if (enabled) {
            glUniform1f(uniforms.locationOf("fresnelOutline.exponent"), exponent);
            glUniform3f(uniforms.locationOf("fresnelOutline.color"), r, g, b);
        }
    }

    public static void init() {

    }

    private NProgram() {

    }

}
