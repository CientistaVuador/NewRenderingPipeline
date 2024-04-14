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
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class NProgram {

    public static final float LIGHT_ATTENUATION = 0.75f;

    public static final int MAX_AMOUNT_OF_LIGHTS = 16;
    public static final int MAX_AMOUNT_OF_LIGHTMAPS = 16;

    public static final int NULL_LIGHT_TYPE = 0;
    public static final int DIRECTIONAL_LIGHT_TYPE = 1;
    public static final int POINT_LIGHT_TYPE = 2;
    public static final int SPOT_LIGHT_TYPE = 3;

    public static class NProgramLight {

        public final int type;
        public final Vector3fc position;
        public final Vector3fc direction;
        public final float innerCone;
        public final float outerCone;
        public final Vector3fc diffuse;
        public final Vector3fc specular;
        public final Vector3fc ambient;

        public NProgramLight(
                int type,
                Vector3fc position,
                Vector3fc direction,
                float innerCone, float outerCone,
                Vector3fc diffuse, Vector3fc specular, Vector3fc ambient
        ) {
            this.type = type;
            this.position = new Vector3f(position);
            this.direction = new Vector3f(direction);
            this.innerCone = innerCone;
            this.outerCone = outerCone;
            this.diffuse = new Vector3f(diffuse);
            this.specular = new Vector3f(specular);
            this.ambient = new Vector3f(ambient);
        }
    }

    public static final NProgramLight NULL_LIGHT = new NProgramLight(
            NULL_LIGHT_TYPE,
            new Vector3f(),
            new Vector3f(),
            0f,
            0f,
            new Vector3f(), new Vector3f(), new Vector3f()
    );

    public static class NProgramMaterial {

        public final Vector4fc diffuseColor;
        public final Vector3fc specularColor;
        public final float minExponent;
        public final float maxExponent;
        public final float parallaxHeightCoefficient;
        public final float parallaxMinLayers;
        public final float parallaxMaxLayers;

        public NProgramMaterial(
                Vector4fc diffuseColor,
                Vector3fc specularColor,
                float exponentMin, float exponentMax,
                float parallaxHeightCoefficient, float parallaxMinLayers, float parallaxMaxLayers
        ) {
            this.diffuseColor = new Vector4f(diffuseColor);
            this.specularColor = new Vector3f(specularColor);
            this.minExponent = exponentMin;
            this.maxExponent = exponentMax;
            this.parallaxHeightCoefficient = parallaxHeightCoefficient;
            this.parallaxMinLayers = parallaxMinLayers;
            this.parallaxMaxLayers = parallaxMaxLayers;
        }
    }

    public static final NProgramMaterial NULL_MATERIAL = new NProgramMaterial(
            new Vector4f(0.75f, 0.75f, 0.75f, 1.0f),
            new Vector3f(0.25f, 0.25f, 0.25f),
            1f, 512f,
            0.065f,
            8f, 32f
    );

    public static final BetterUniformSetter VARIANT_ALPHA_TESTING;
    public static final BetterUniformSetter VARIANT_ALPHA_BLENDING;
    public static final BetterUniformSetter VARIANT_LIGHTMAPPED_ALPHA_TESTING;
    public static final BetterUniformSetter VARIANT_LIGHTMAPPED_ALPHA_BLENDING;

    private static final String VERTEX_SHADER = 
            """
            layout (location = VAO_INDEX_POSITION_XYZ) in vec3 vertexPosition;
            layout (location = VAO_INDEX_TEXTURE_XY) in vec2 vertexTexture;
            layout (location = VAO_INDEX_NORMAL_XYZ) in vec3 vertexNormal;
            layout (location = VAO_INDEX_TANGENT_XYZ) in vec3 vertexTangent;
            layout (location = VAO_INDEX_AMBIENT_OCCLUSION_X) in float vertexAmbientOcclusion;
            layout (location = VAO_INDEX_BONE_IDS_XYZW) in ivec4 vertexBoneIds;
            layout (location = VAO_INDEX_BONE_WEIGHTS_XYZW) in vec4 vertexBoneWeights;
            
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            uniform mat3 normalModel;
            
            #if defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
            uniform samplerBuffer lightmapUvs;
            #endif
            
            out VertexData {
                vec3 worldPosition;
                vec2 worldTexture;
                vec3 worldNormal;
                float worldAmbientOcclusion;
                
                mat3 TBN;
                
                vec3 tangentPosition;
                vec3 tangentViewPosition;
                
                #if defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
                vec2 worldLightmapUv;
                #endif
            } outVertex;
            
            void main() {
                vec3 tangent = normalize(normalModel * vertexTangent);
                vec3 normal = normalize(normalModel * vertexNormal);
                vec4 worldPosition = model * vec4(vertexPosition, 1.0);
                
                outVertex.worldPosition = worldPosition.xyz;
                outVertex.worldTexture = vertexTexture;
                outVertex.worldNormal = normal;
                outVertex.worldAmbientOcclusion = vertexAmbientOcclusion;
                outVertex.TBN = mat3(tangent, cross(normal, tangent), normal);
                
                mat3 transposedTBN = transpose(outVertex.TBN);
                outVertex.tangentPosition = transposedTBN * outVertex.worldPosition;
                outVertex.tangentViewPosition = transposedTBN * vec3(0.0);
                
                #if defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
                outVertex.worldLightmapUv = texelFetch(lightmapUvs, gl_VertexID).xy;
                #endif
                
                gl_Position = projection * view * worldPosition;
            }
            """;

    private static final String FRAGMENT_SHADER = 
            """
            uniform sampler2D r_g_b_a_or_h;
            uniform sampler2D e_nx_r_ny;
            
            uniform bool parallaxSupported;
            uniform bool parallaxEnabled;
            
            #if defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
            uniform float lightmapIntensity[MAX_AMOUNT_OF_LIGHTMAPS];
            uniform sampler2DArray lightmaps;
            #endif
            
            struct Material {
                vec4 diffuseColor;
                vec3 specularColor;
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
                vec3 worldNormal;
                float worldAmbientOcclusion;
                
                mat3 TBN;
                
                vec3 tangentPosition;
                vec3 tangentViewPosition;
                
                #if defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
                vec2 worldLightmapUv;
                #endif
            } inVertex;
            
            layout (location = 0) out vec4 outputFragColor;
            
            vec2 parallaxMapping(
                vec2 uv,
                vec3 tangentPosition,
                vec3 tangentViewPosition,
                float minLayers,
                float maxLayers,
                float heightScale
            ) {
                vec3 tangentViewDirection = normalize(tangentViewPosition - tangentPosition);
                
                float numLayers = mix(maxLayers, minLayers, max(dot(vec3(0.0, 0.0, 1.0), tangentViewDirection), 0.0));
                
                float layerDepth = 1.0 / numLayers;
                float currentLayerDepth = 0.0;
                
                vec2 scaledViewDirection = tangentViewDirection.xy * heightScale;
                vec2 deltaUv = scaledViewDirection / numLayers;
                
                vec2 currentUv = uv;
                float currentDepth = 1.0 - texture(r_g_b_a_or_h, currentUv)[3];
                
                while (currentLayerDepth < currentDepth) {
                    currentUv -= deltaUv;
                    currentDepth = 1.0 - texture(r_g_b_a_or_h, currentUv)[3];
                    currentLayerDepth += layerDepth;
                }
                 
                vec2 previousUv = currentUv + deltaUv;
                
                float afterDepth = currentDepth - currentLayerDepth;
                float beforeDepth = (1.0 - texture(r_g_b_a_or_h, previousUv)[3]) - currentLayerDepth + layerDepth;
                
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
                vec3 viewDirection,
                vec3 worldPosition
            ) {
                int lightType = light.type;
                
                vec3 positionalLightDirection = normalize(light.position - worldPosition);
                vec3 infiniteLightDirection = normalize(-light.direction);
                
                vec3 oppositeLightDirection = (lightType == DIRECTIONAL_LIGHT_TYPE ? infiniteLightDirection : positionalLightDirection);
                vec3 reflectedLightDirection = reflect(-oppositeLightDirection, normal);
                vec3 halfwayDirection = normalize(oppositeLightDirection + viewDirection);
                
                float diffuseFactor = max(dot(normal, oppositeLightDirection), 0.0);
                float specularFactor = pow(max(dot(normal, halfwayDirection), 0.0), exponent) * normalizationFactor;
                
                vec3 diffuse = light.diffuse * diffuseFactor * diffuseColor;
                vec3 specular = light.specular * specularFactor * specularColor;
                vec3 ambient = light.ambient * diffuseColor;
                
                float distance = length(light.position - worldPosition);
                float attenuation = 1.0 / ((distance * distance) + LIGHT_ATTENUATION);
                
                float pointAttenuation = (lightType == POINT_LIGHT_TYPE || lightType == SPOT_LIGHT_TYPE ? attenuation : 1.0);
                
                diffuse *= pointAttenuation;
                specular *= pointAttenuation;
                ambient *= pointAttenuation;
                
                float theta = dot(oppositeLightDirection, normalize(-light.direction));
                float epsilon = light.innerCone - light.outerCone;
                float intensity = clamp((theta - light.outerCone) / epsilon, 0.0, 1.0);
                
                float spotIntensity = (lightType == SPOT_LIGHT_TYPE ? intensity : 1.0);
                
                diffuse *= spotIntensity;
                specular *= spotIntensity;
                
                return diffuse + specular + ambient;
            }
            
            void main() {
                vec4 finalColor = vec4(0.0, 0.0, 0.0, 1.0);
                
                vec2 textureUv = inVertex.worldTexture;
                
                float heightScale = material.parallaxHeightCoefficient;
                
                if (parallaxSupported && parallaxEnabled && heightScale > 0.0) {
                    textureUv = parallaxMapping(inVertex.worldTexture, inVertex.tangentPosition, inVertex.tangentViewPosition, material.parallaxMinLayers, material.parallaxMaxLayers, material.parallaxHeightCoefficient);
                }
                
                vec4 rgbaorh = texture(r_g_b_a_or_h, textureUv);
                vec4 enxrny = texture(e_nx_r_ny, textureUv);
                
                #if defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
                int amountOfLightmaps = textureSize(lightmaps, 0).z;
                for (int i = 0; i < amountOfLightmaps; i++) {
                    float intensity = 1.0;
                    if (i < MAX_AMOUNT_OF_LIGHTMAPS) {
                        intensity = lightmapIntensity[i];
                    }
                    finalColor.rgb += texture(lightmaps, vec3(inVertex.worldLightmapUv, float(i))).rgb * intensity * rgbaorh.rgb;
                }
                #endif
                
                float alpha = 1.0;
                if (!parallaxSupported) {
                    alpha = rgbaorh.a;
                }
                finalColor.a = alpha * material.diffuseColor.a;
                
                mat3 TBN = inVertex.TBN;
                
                vec3 normal = vec3(
                    (enxrny[1] * 2.0) - 1.0,
                    (enxrny[3] * 2.0) - 1.0,
                    0.0
                );
                normal = normalize(vec3(
                    normal.x,
                    normal.y,
                    sqrt(1.0 - (normal.x * normal.x) - (normal.y * normal.y))
                ));
                
                normal = normalize(TBN * normal);
                
                float exponent = pow(material.maxExponent - material.minExponent, enxrny[0]) + material.minExponent;
                float normalizationFactor = ((exponent + 2.0) * (exponent + 4.0)) / (8.0 * PI * (pow(2.0, -exponent * 0.5) + exponent));
                vec3 viewDirection = normalize(-inVertex.worldPosition);
                vec3 worldPosition = inVertex.worldPosition;
                vec3 diffuseColor = material.diffuseColor.rgb * rgbaorh.rgb;
                vec3 specularColor = material.specularColor;
                
                for (int i = 0; i < MAX_AMOUNT_OF_LIGHTS; i++) {
                    Light light = lights[i];
                    if (light.type == NULL_LIGHT_TYPE) {
                        break;
                    }
                    finalColor.rgb += calculateLight(light, diffuseColor, specularColor, exponent, normalizationFactor, normal, viewDirection, worldPosition);
                }
                
                finalColor.rgb = pow(finalColor.rgb, vec3(1.0/2.2));
                
                #if defined(VARIANT_ALPHA_TESTING) || defined(VARIANT_LIGHTMAPPED_ALPHA_TESTING)
                if (finalColor.a < 0.5) {
                    discard;
                }
                outputFragColor = vec4(finalColor.rgb, 1.0);
                #endif
                
                #if defined(VARIANT_ALPHA_BLENDING) || defined(VARIANT_LIGHTMAPPED_ALPHA_BLENDING)
                outputFragColor = finalColor;
                #endif
            }
            """;

    private static final ProgramCompiler.ShaderConstant[] CONSTANTS = {
        new ProgramCompiler.ShaderConstant("VAO_INDEX_POSITION_XYZ", NMesh.VAO_INDEX_POSITION_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_TEXTURE_XY", NMesh.VAO_INDEX_TEXTURE_XY),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_NORMAL_XYZ", NMesh.VAO_INDEX_NORMAL_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_TANGENT_XYZ", NMesh.VAO_INDEX_TANGENT_XYZ),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_AMBIENT_OCCLUSION_X", NMesh.VAO_INDEX_AMBIENT_OCCLUSION_X),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_BONE_IDS_XYZW", NMesh.VAO_INDEX_BONE_IDS_XYZW),
        new ProgramCompiler.ShaderConstant("VAO_INDEX_BONE_WEIGHTS_XYZW", NMesh.VAO_INDEX_BONE_WEIGHTS_XYZW),
        new ProgramCompiler.ShaderConstant("PI", Math.PI),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_LIGHTS", MAX_AMOUNT_OF_LIGHTS),
        new ProgramCompiler.ShaderConstant("MAX_AMOUNT_OF_LIGHTMAPS", MAX_AMOUNT_OF_LIGHTMAPS),
        new ProgramCompiler.ShaderConstant("NULL_LIGHT_TYPE", NULL_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("DIRECTIONAL_LIGHT_TYPE", DIRECTIONAL_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("POINT_LIGHT_TYPE", POINT_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("SPOT_LIGHT_TYPE", SPOT_LIGHT_TYPE),
        new ProgramCompiler.ShaderConstant("LIGHT_ATTENUATION", LIGHT_ATTENUATION)
    };

    static {
        Map<String, Integer> programs = ProgramCompiler.compile(
                VERTEX_SHADER, FRAGMENT_SHADER,
                new String[]{
                    "ALPHA_TESTING",
                    "ALPHA_BLENDING",
                    "LIGHTMAPPED_ALPHA_TESTING",
                    "LIGHTMAPPED_ALPHA_BLENDING"
                },
                CONSTANTS
        );

        VARIANT_ALPHA_TESTING = new BetterUniformSetter(programs.get("ALPHA_TESTING"));
        VARIANT_ALPHA_BLENDING = new BetterUniformSetter(programs.get("ALPHA_BLENDING"));
        VARIANT_LIGHTMAPPED_ALPHA_TESTING = new BetterUniformSetter(programs.get("LIGHTMAPPED_ALPHA_TESTING"));
        VARIANT_LIGHTMAPPED_ALPHA_BLENDING = new BetterUniformSetter(programs.get("LIGHTMAPPED_ALPHA_BLENDING"));
    }

    public static final String UNIFORM_PROJECTION = "projection";
    public static final String UNIFORM_VIEW = "view";
    public static final String UNIFORM_MODEL = "model";
    public static final String UNIFORM_NORMAL_MODEL = "normalModel";
    public static final String UNIFORM_R_G_B_A_OR_H = "r_g_b_a_or_h";
    public static final String UNIFORM_E_NX_R_NY = "e_nx_r_ny";
    public static final String UNIFORM_LIGHTMAPS_UVS = "lightmapUvs";
    public static final String UNIFORM_LIGHTMAPS = "lightmaps";
    public static final String UNIFORM_PARALLAX_SUPPORTED = "parallaxSupported";
    public static final String UNIFORM_PARALLAX_ENABLED = "parallaxEnabled";

    public static void sendMaterial(BetterUniformSetter uniforms, NProgramMaterial material) {
        if (material == null) {
            material = NULL_MATERIAL;
        }
        glUniform4f(uniforms.locationOf("material.diffuseColor"), material.diffuseColor.x(), material.diffuseColor.y(), material.diffuseColor.z(), material.diffuseColor.w());
        glUniform3f(uniforms.locationOf("material.specularColor"), material.specularColor.x(), material.specularColor.y(), material.specularColor.z());
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
            glUniform3f(uniforms.locationOf("lights[" + index + "].position"), light.position.x(), light.position.y(), light.position.z());
            glUniform3f(uniforms.locationOf("lights[" + index + "].direction"), light.direction.x(), light.direction.y(), light.direction.z());
            glUniform1f(uniforms.locationOf("lights[" + index + "].innerCone"), light.innerCone);
            glUniform1f(uniforms.locationOf("lights[" + index + "].outerCone"), light.outerCone);
            glUniform3f(uniforms.locationOf("lights[" + index + "].diffuse"), light.diffuse.x(), light.diffuse.y(), light.diffuse.z());
            glUniform3f(uniforms.locationOf("lights[" + index + "].specular"), light.specular.x(), light.specular.y(), light.specular.z());
            glUniform3f(uniforms.locationOf("lights[" + index + "].ambient"), light.ambient.x(), light.ambient.y(), light.ambient.z());
        }
    }

    public static void sendLightmapIntensity(BetterUniformSetter uniforms, float intensity, int index) {
        if (index < 0 || index > MAX_AMOUNT_OF_LIGHTMAPS) {
            throw new IllegalArgumentException("Out of bounds index: " + index);
        }
        glUniform1f(uniforms.locationOf("lightmapIntensity[" + index + "]"), intensity);
    }

    public static void init() {

    }

    private NProgram() {

    }

}
