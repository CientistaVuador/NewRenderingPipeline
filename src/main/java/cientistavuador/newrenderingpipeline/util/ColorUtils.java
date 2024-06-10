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

import java.util.List;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 *
 * @author Cien
 */
public class ColorUtils {

    public static void blend(List<Vector4fc> colors, Vector4f outColor) {
        outColor.zero();
        if (colors == null || colors.isEmpty()) {
            return;
        }
        outColor.set(colors.get(0));
        for (int i = 1; i < colors.size(); i++) {
            Vector4fc source = colors.get(i);

            float alpha = source.w() + outColor.w() * (1f - source.w());
            if (alpha < 0.00001f) {
                continue;
            }
            float invalpha = 1f / alpha;
            outColor.set(
                    (source.x() * source.w() + outColor.x() * outColor.w() * (1f - source.w())) * invalpha,
                    (source.y() * source.w() + outColor.y() * outColor.w() * (1f - source.w())) * invalpha,
                    (source.z() * source.w() + outColor.z() * outColor.w() * (1f - source.w())) * invalpha,
                    alpha
            );
        }
    }

    private ColorUtils() {

    }
}
