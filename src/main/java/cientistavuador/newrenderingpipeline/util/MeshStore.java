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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Cien
 */
public class MeshStore {

    public static final long MAGIC = 3549241685215793215L;

    public static class MeshStoreOutput {

        private final float[] vertices;
        private final int vertexSize;
        private final int[] indices;

        public MeshStoreOutput(float[] vertices, int vertexSize, int[] indices) {
            this.vertices = vertices;
            this.vertexSize = vertexSize;
            this.indices = indices;
        }

        public float[] vertices() {
            return vertices;
        }

        public int vertexSize() {
            return vertexSize;
        }

        public int[] indices() {
            return indices;
        }

    }

    public static void encode(float[] vertices, int vertexSize, int[] indices, OutputStream output) throws IOException {
        new MeshStore(vertices, vertexSize, indices, output).encode();
    }

    public static MeshStoreOutput decode(InputStream input) throws IOException {
        return new MeshStore(input).decode();
    }

    private final GZIPInputStream input;
    private final GZIPOutputStream output;

    private final DataInputStream dataInput;
    private final DataOutputStream dataOutput;

    private float[] vertices = null;
    private int vertexSize = 0;
    private int[] indices = null;

    private MeshStore(InputStream input) throws IOException {
        this.input = new GZIPInputStream(input, 8192);
        this.output = null;
        this.dataInput = new DataInputStream(this.input);
        this.dataOutput = null;
    }

    private MeshStore(float[] vertices, int vertexSize, int[] indices, OutputStream output) throws IOException {
        this.input = null;
        this.output = new GZIPOutputStream(output, 8192);
        this.dataInput = null;
        this.dataOutput = new DataOutputStream(this.output);

        this.vertices = vertices;
        this.vertexSize = vertexSize;
        this.indices = indices;
    }

    private void writeHeader() throws IOException {
        this.dataOutput.writeLong(MAGIC);
        this.dataOutput.writeInt(this.vertices.length);
        this.dataOutput.writeInt(this.vertexSize);
        this.dataOutput.writeInt(this.indices.length);
    }

    private void writeVertices() throws IOException {
        int amountOfVertices = this.vertices.length / this.vertexSize;
        for (int component = 0; component < this.vertexSize; component++) {
            for (int vertex = 0; vertex < amountOfVertices; vertex++) {
                this.dataOutput.writeFloat(this.vertices[(vertex * this.vertexSize) + component]);
            }
        }
    }
    
    private void writeIndices() throws IOException {
        int[] deltaIndices = this.indices.clone();
        for (int i = 0; i < deltaIndices.length; i++) {
            int delta = this.indices[i];
            if ((i - 1) >= 0) {
                delta = delta - this.indices[i - 1];
            }
            deltaIndices[i] = delta;
        }
        
        for (int i = 0; i < deltaIndices.length; i++) {
            int delta = deltaIndices[i];
            if (delta > 0) {
                delta = delta << 1;
            } else {
                delta = ((-delta) << 1) | 0x01;
            }
            deltaIndices[i] = delta;
        }
        
        for (int component = 0; component < 4; component++) {
            for (int i = 0; i < deltaIndices.length; i++) {
                int deltaIndex = deltaIndices[i];
                int deltaByte = (deltaIndex >> (component * 8)) & 0xFF;
                this.dataOutput.writeByte(deltaByte);
            }
        }
    }
    
    private void encode() throws IOException {
        writeHeader();
        writeVertices();
        writeIndices();
        
        this.dataOutput.flush();
        this.output.finish();
    }
    
    private void readHeader() throws IOException {
        long magic = this.dataInput.readLong();
        if (magic != MAGIC) {
            throw new IOException("Invalid magic number.");
        }
        
        this.vertices = new float[this.dataInput.readInt()];
        this.vertexSize = this.dataInput.readInt();
        this.indices = new int[this.dataInput.readInt()];
    }
    
    private void readVertices() throws IOException {
        int amountOfVertices = this.vertices.length / this.vertexSize;
        for (int component = 0; component < this.vertexSize; component++) {
            for (int vertex = 0; vertex < amountOfVertices; vertex++) {
                this.vertices[(vertex * this.vertexSize) + component] = this.dataInput.readFloat();
            }
        }
    }
    
    private void readIndices() throws IOException {
        for (int component = 0; component < 4; component++) {
            for (int i = 0; i < this.indices.length; i++) {
                int deltaByte = ((int)this.dataInput.readByte()) & 0xFF;
                this.indices[i] = this.indices[i] | (deltaByte << (component * 8));
            }
        }
        
        for (int i = 0; i < this.indices.length; i++) {
            int delta = this.indices[i];
            if ((delta & 0x01) == 1) {
                delta = -(delta >>> 1);
            } else {
                delta = (delta >>> 1);
            }
            this.indices[i] = delta;
        }
        
        for (int i = 1; i < this.indices.length; i++) {
            this.indices[i] = this.indices[i - 1] + this.indices[i];
        }
    }
    
    private MeshStoreOutput decode() throws IOException {
        readHeader();
        readVertices();
        readIndices();
        
        return new MeshStoreOutput(this.vertices, this.vertexSize, this.indices);
    }

}
