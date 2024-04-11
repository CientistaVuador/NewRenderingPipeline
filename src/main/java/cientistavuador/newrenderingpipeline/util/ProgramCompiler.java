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
        
        glDeleteShader(vertexShader);
        if (geometryShader != 0) {
            glDeleteShader(geometryShader);
        }
        glDeleteShader(fragmentShader);

        if (Main.DEBUG_ENABLED && shaderName != null && GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(KHRDebug.GL_PROGRAM, program, "Program_" + shaderName);
        }

        return program;
    }

    private ProgramCompiler() {

    }
}
