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
import cientistavuador.newrenderingpipeline.util.E8Image;
import cientistavuador.newrenderingpipeline.util.ProgramCompiler;
import java.util.HashMap;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class NSkybox {
    
    public static final int VAO;
    public static final int AMOUNT_OF_INDICES;
    
    static {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        float[] vertices = {
            -1f, -1f, -1f, //bottom left front 0
            1f, -1f, -1f, //bottom right front 1
            1f, -1f, 1f, //bottom right back 2
            -1f, -1f, 1f, //bottom left back 3
            
            -1f, 1f, -1f, //top left front 4
            1f, 1f, -1f, //top right front 5
            1f, 1f, 1f, //top right back 6
            -1f, 1f, 1f //top left back 7
        };
        
        int[] indices = {
            0, 1, 5,
            0, 5, 4,
            
            1, 2, 6,
            1, 6, 5,
            
            2, 3, 7,
            2, 7, 6,
            
            3, 0, 4,
            3, 4, 7,
            
            4, 6, 7,
            4, 5, 6,
            
            0, 3, 2,
            0, 2, 1
        };
        
        AMOUNT_OF_INDICES = indices.length;
        
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        glBindVertexArray(0);
        
        VAO = vao;
    }
    
    public static final String VERTEX_SHADER = 
            """
            #version 330 core
            
            uniform mat4 projection;
            uniform mat4 view;
            
            layout (location = 0) in vec3 vertexPosition;
            
            out vec3 sampleDirection;
            
            void main() {
                sampleDirection = vertexPosition;
                vec4 transformedPosition = projection * view * vec4(vertexPosition, 1.0);
                gl_Position = transformedPosition.xyww;
            }
            """;
            
    public static final String FRAGMENT_SHADER =
            """
            #version 330 core
            
            uniform bool hdrOutput;
            uniform samplerCube skybox;
            
            in vec3 sampleDirection;
            
            layout (location = 0) out vec4 outputColor;
            
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
            
            vec4 RGBEToRGBA(vec4 rgbe) {
                return vec4(rgbe.rgb * pow(RGBE_BASE, (rgbe.a * RGBE_MAX_EXPONENT) - RGBE_BIAS), 1.0);
            }
            
            void main() {
                vec3 direction = normalize(sampleDirection);
                vec4 color = RGBEToRGBA(texture(skybox, direction));
                if (!hdrOutput) {
                    color.rgb = gammaCorrection(ACESFilm(color.rgb));
                }
                outputColor = vec4(color.rgb, 1.0);
            }
            """;
    
    public static final BetterUniformSetter SKYBOX_PROGRAM = new BetterUniformSetter(ProgramCompiler.compile(
            VERTEX_SHADER,
            FRAGMENT_SHADER,
            new HashMap<>() {{
                put("RGBE_BASE", Double.toString(E8Image.BASE));
                put("RGBE_MAX_EXPONENT", Integer.toString(E8Image.MAX_EXPONENT));
                put("RGBE_BIAS", Integer.toString(E8Image.BIAS));
            }}
    ));
    
    public static final String UNIFORM_PROJECTION = "projection";
    public static final String UNIFORM_VIEW = "view";
    public static final String UNIFORM_SKYBOX = "skybox";
    public static final String UNIFORM_HDR_OUTPUT = "hdrOutput";
    
    public static void init() {
        
    }
    
    private NSkybox() {
        
    }
    
}
