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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author Cien
 */
public class CryptoUtils {

    public static String sha256(ByteBuffer buffer) {
        byte[] copiedData = new byte[buffer.remaining()];
        buffer.get(copiedData, buffer.position(), buffer.remaining());
        
        byte[] sha256Bytes;
        try {
            sha256Bytes = MessageDigest.getInstance("SHA256").digest(copiedData);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
        
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < sha256Bytes.length; i++) {
            String hex = Integer.toHexString(sha256Bytes[i] & 0xFF);
            if (hex.length() <= 1) {
                b.append('0');
            }
            b.append(hex);
        }
        
        return b.toString();
    }

    private CryptoUtils() {

    }
}
