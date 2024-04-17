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
package cientistavuador.newrenderingpipeline.util;

import cientistavuador.newrenderingpipeline.Main;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class ProgramCompiler {
    
    private static final boolean ONLY_OUTPUT_ERRORS = false;

    public static int compile(String vertexSource, String fragmentSource) {
        return compile(vertexSource, null, fragmentSource);
    }

    public static int compile(String vertexSource, String fragmentSource, Map<String, String> replacements) {
        return compile(vertexSource, null, fragmentSource, replacements);
    }

    public static int compile(String vertexSource, String geometrySource, String fragmentSource) {
        return compile(vertexSource, geometrySource, fragmentSource, null);
    }

    private static String replace(String s, Map<String, String> replacements) {
        for (Entry<String, String> e : replacements.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return s;
    }

    private static String createOutputMessage(String prefix, String message) {
        if (message.isBlank()) {
            return prefix + " Output -> (no output)";
        }
        return prefix + " Output -> {\n" + message + "\n}";
    }

    private static void checkErrors(int shader, String prefix) {
        boolean shaderFailed = glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE;
        String message = createOutputMessage(prefix, glGetShaderInfoLog(shader));
        if (!ONLY_OUTPUT_ERRORS && !shaderFailed) {
            System.out.println(message);
        } else if (shaderFailed) {
            throw new RuntimeException(message);
        }
    }

    public static int compile(String vertexSource, String geometrySource, String fragmentSource, Map<String, String> replacements) {
        String shaderName = null;
        if (!ONLY_OUTPUT_ERRORS) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < stackTrace.length; i++) {
                if (i == 0) {
                    continue;
                }
                StackTraceElement e = stackTrace[i];
                if (!e.getClassName().contains(ProgramCompiler.class.getName())) {
                    shaderName = e.toString();
                    System.out.println("Compiling shader in " + shaderName);
                    break;
                }
            }
        }

        if (replacements != null) {
            vertexSource = replace(vertexSource, replacements);
            fragmentSource = replace(fragmentSource, replacements);
            if (geometrySource != null) {
                geometrySource = replace(geometrySource, replacements);
            }
        }
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexSource);
        glCompileShader(vertexShader);

        checkErrors(vertexShader, "Vertex Shader");

        int geometryShader = 0;
        if (geometrySource != null) {
            geometryShader = glCreateShader(GL_GEOMETRY_SHADER);
            glShaderSource(geometryShader, geometrySource);
            glCompileShader(geometryShader);

            checkErrors(geometryShader, "Geometry Shader");
        }

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentSource);
        glCompileShader(fragmentShader);

        checkErrors(fragmentShader, "Fragment Shader");

        int program = glCreateProgram();

        glAttachShader(program, vertexShader);
        if (geometryShader != 0) {
            glAttachShader(program, geometryShader);
        }
        glAttachShader(program, fragmentShader);

        glLinkProgram(program);

        {
            boolean programLinkFailed = glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE;
            String message = createOutputMessage("Program Link", glGetProgramInfoLog(program));
            if (!ONLY_OUTPUT_ERRORS && !programLinkFailed) {
                System.out.println(message);
            } else if (programLinkFailed) {
                throw new RuntimeException(message);
            }
        }
        
        if (shaderName != null && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(KHRDebug.GL_SHADER, vertexShader,
                    StringUtils.truncateStringTo255Bytes("vertex_" + shaderName)
            );
        }
        
        if (geometryShader != 0 && shaderName != null && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(KHRDebug.GL_SHADER, geometryShader,
                    StringUtils.truncateStringTo255Bytes("geometry_" + shaderName)
            );
        }
        
        if (shaderName != null && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(KHRDebug.GL_SHADER, fragmentShader,
                    StringUtils.truncateStringTo255Bytes("fragment_" + shaderName)
            );
        }
        
        glDeleteShader(vertexShader);
        if (geometryShader != 0) {
            glDeleteShader(geometryShader);
        }
        glDeleteShader(fragmentShader);
        
        if (shaderName != null && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(KHRDebug.GL_PROGRAM, program, StringUtils.truncateStringTo255Bytes("program_" + shaderName));
        }
        
        return program;
    }
    
    public static class ShaderConstant {
        private final String name;
        private final Object constant;
        
        public ShaderConstant(String name, Object constant) {
            this.name = name;
            this.constant = constant;
        }

        public String getName() {
            return name;
        }

        public Object getConstant() {
            return constant;
        }
    }
    
    public static Map<String, Integer> compile(String vertex, String geometry, String fragment, String[] variations, ShaderConstant[] constants) {
        StringBuilder headerBuilder = new StringBuilder();
        
        headerBuilder.append("#version ").append(Main.OPENGL_MAJOR_VERSION).append(Main.OPENGL_MINOR_VERSION).append("0 core\n\n");
        
        headerBuilder.append("//Compiled at ").append(new Date().toString()).append("\n\n");
        
        int[] glslVersions = {
            3, 3,
            4, 0,
            4, 1,
            4, 2,
            4, 3,
            4, 4,
            4, 5,
            4, 6
        };
        
        headerBuilder.append("//The supported glsl versions by this gpu:\n");
        for (int i = 0; i < glslVersions.length; i += 2) {
            int major = glslVersions[i + 0];
            int minor = glslVersions[i + 1];
            if (Main.isSupported(major, minor)) {
                headerBuilder.append("#define SUPPORTED_").append(major).append(minor).append("0\n");
            }
        }
        
        headerBuilder.append("\n");
        
        headerBuilder.append("//The shader constants:\n");
        for (ShaderConstant constant:constants) {
            headerBuilder.append("#define ").append(constant.getName()).append(" ").append(constant.getConstant()).append("\n");
        }
        
        headerBuilder.append("\n");
        
        String header = headerBuilder.toString();
        
        Map<String, Integer> programs = new HashMap<>();
        
        for (String variant:variations) {
            String variantHeader = header+"//The variant of this program:\n#define VARIANT_"+variant+"\n\n";
            
            String modifiedVertex = variantHeader+vertex;
            String modifiedGeometry = null;
            if (geometry != null) {
                modifiedGeometry = variantHeader+geometry;
            }
            String modifiedFrag = variantHeader+fragment;
            
            programs.put(variant, compile(modifiedVertex, modifiedGeometry, modifiedFrag));
        }
        
        return programs;
    }
    
    public static Map<String, Integer> compile(String vertex, String fragment, String[] variations, ShaderConstant[] constants) {
        return compile(vertex, null, fragment, variations, constants);
    }
    
    private ProgramCompiler() {

    }
}
