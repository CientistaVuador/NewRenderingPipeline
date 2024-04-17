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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 *
 * @author Cien
 */
public class StringUtils {

    public static String truncateStringTo255Bytes(String string) {
        return truncateStringBytes(string, 255);
    }

    private static int findNearestValidCodepoint(byte[] stringBytes, int maxBytes) {
        if ((stringBytes[maxBytes] & 0xFF) >> 6 != 0b10) {
            return maxBytes;
        }

        int length = maxBytes;
        for (; length > 0; length--) {
            int b = stringBytes[length - 1] & 0xFF;
            if (b >> 6 == 0b10) {
                continue;
            }
            if (b >> 7 == 0b1) {
                length--;
            }
            break;
        }
        
        return length;
    }

    public static String truncateStringBytes(String string, int maxBytes) {
        byte[] stringBytes = string.getBytes(StandardCharsets.UTF_8);
        if (stringBytes.length <= maxBytes) {
            return string;
        }
        return new String(
                Arrays.copyOf(
                        stringBytes,
                        findNearestValidCodepoint(stringBytes, maxBytes)
                ),
                StandardCharsets.UTF_8
        );
    }

    private StringUtils() {

    }
}
