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
import cientistavuador.newrenderingpipeline.camera.PerspectiveCamera;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 *
 * @author Cien
 */
public class NCubemapRenderer {

    public static final int SUPER_RESOLUTION_MULTIPLIER = 4;

    public static NCubemap render(
            String name, NCubemapInfo info, int size,
            List<NLight> lights, NCubemaps cubemaps
    ) {
        int fboSize = size * SUPER_RESOLUTION_MULTIPLIER;

        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);

        int rboColor = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboColor);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGB32F, fboSize, fboSize);

        int rboDepthStencil = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboDepthStencil);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, fboSize, fboSize);

        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rboColor);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rboDepthStencil);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalArgumentException("Fatal framebuffer error, could not render cubemap, framebuffer is not complete!");
        }

        N3DObject[] objects = N3DObjectRenderer.copyQueueObjects();

        float[] cameraRotations = {
            0f, 0f, 180f,
            0f, -180f, 180f,
            90f, -90f, 0f,
            -90f, -90f, 0f,
            0f, 90f, -180f,
            0f, -90f, -180f
        };
        PerspectiveCamera camera = new PerspectiveCamera();
        camera.setPosition(info.getCubemapPosition());
        camera.setDimensions(1f, 1f);
        camera.setFov(90f);

        FloatBuffer sides = memAllocFloat(fboSize * fboSize * 3 * NCubemap.SIDES);
        float[] cubemap = new float[size * size * 3 * NCubemap.SIDES];
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, fboSize, fboSize);
            for (int i = 0; i < NCubemap.SIDES; i++) {
                float pitch = cameraRotations[(i * 3) + 0];
                float yaw = cameraRotations[(i * 3) + 1];
                float roll = cameraRotations[(i * 3) + 2];
                camera.setRotation(pitch, yaw, roll);
                sides.position(fboSize * fboSize * 3 * i);

                if (i != 0) {
                    for (int j = 0; j < objects.length; j++) {
                        N3DObjectRenderer.queueRender(objects[j]);
                    }
                }

                glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
                N3DObjectRenderer.render(camera, lights, cubemaps);
                glReadPixels(0, 0, fboSize, fboSize, GL_RGB, GL_FLOAT, sides);
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, Main.WIDTH, Main.HEIGHT);

            glDeleteRenderbuffers(rboColor);
            glDeleteRenderbuffers(rboDepthStencil);
            glDeleteFramebuffers(fbo);

            for (int y = 0; y < size * NCubemap.SIDES; y++) {
                for (int x = 0; x < size; x++) {
                    float r = 0f;
                    float g = 0f;
                    float b = 0f;
                    for (int yOffset = 0; yOffset < SUPER_RESOLUTION_MULTIPLIER; yOffset++) {
                        for (int xOffset = 0; xOffset < SUPER_RESOLUTION_MULTIPLIER; xOffset++) {
                            int trueX = (x * SUPER_RESOLUTION_MULTIPLIER) + xOffset;
                            int trueY = (y * SUPER_RESOLUTION_MULTIPLIER) + yOffset;

                            r += sides.get(0 + (trueX * 3) + (trueY * fboSize * 3));
                            g += sides.get(1 + (trueX * 3) + (trueY * fboSize * 3));
                            b += sides.get(2 + (trueX * 3) + (trueY * fboSize * 3));
                        }
                    }
                    float inv = 1f / (SUPER_RESOLUTION_MULTIPLIER * SUPER_RESOLUTION_MULTIPLIER);
                    r *= inv;
                    g *= inv;
                    b *= inv;

                    cubemap[0 + (x * 3) + (y * size * 3)] = r;
                    cubemap[1 + (x * 3) + (y * size * 3)] = g;
                    cubemap[2 + (x * 3) + (y * size * 3)] = b;
                }
            }
        } finally {
            memFree(sides);
        }

        return new NCubemap(name, info, null, null, size, cubemap);
    }
    
    private NCubemapRenderer() {

    }
}
