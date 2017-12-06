/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;

/**
 * Calculation of the CRC32c checksum is implemented as output stream, so it can be used
 * with {@link ByteArrayOutputStream#writeTo(OutputStream)}.
 * Assumes the checksum value comes right after the data being checksummed and includes
 * the zero value checksum into the checksum calculation.
 */
class CRC32COutputStream extends OutputStream {
    private final OutputStream out;
    private final Hasher hasher;

    public CRC32COutputStream(OutputStream out, boolean calculateChecksum) {
        this.hasher = calculateChecksum ? Hashing.crc32c().newHasher() : NullHasher.INSTANCE;
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        hasher.putByte((byte) b);
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        hasher.putBytes(b, off, len);
        out.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        hasher.putBytes(b);
        out.write(b);
    }

    /**
     * Writes the checksum calculated on the data so far plus zero checksum value to the
     * wrapped output stream.
     */
    public void writeChecksum() throws IOException {
        hasher.putBytes(Util.INT0);
        out.write(Ints.toByteArray(hasher.hash().asInt()));
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }
}
