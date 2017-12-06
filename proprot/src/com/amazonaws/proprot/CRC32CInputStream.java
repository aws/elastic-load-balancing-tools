/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;

/**
 * Calculates the stream checksum while allowing to read the expected checksum value from
 * the stream interpreting it as zero for the purposes of checksum calculation.
 */
class CRC32CInputStream extends InputStream {
    // XXX with the move to Java9 switch to http://download.java.net/java/jdk9/docs/api/java/util/zip/CRC32C.html?
    private final Hasher hasher;
    private final InputStream in;

    public CRC32CInputStream(InputStream in, boolean calculateChecksum) {
        this.in = in;
        this.hasher = calculateChecksum ? Hashing.crc32c().newHasher() : NullHasher.INSTANCE;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            hasher.putByte((byte) b);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = in.read(b, off, len);
        if (result != -1) {
            hasher.putBytes(b, off, result);
        }
        return result;
    }

    public int readChecksum() throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException();
        }

        hasher.putBytes(Util.INT0);
        return Ints.fromBytes((byte) b1, (byte) b2, (byte) b3, (byte) b4);
    }

    public int getChecksum() {
        return hasher.hash().asInt();
    }
}
