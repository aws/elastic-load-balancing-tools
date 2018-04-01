/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.google.common.io.CountingInputStream;

/**
 * Provides tools helping the ProProt parser and the external TLV adapters to parse Proxy Protocol Header.
 * The provided methods reading from the input data stream validate that stream is not read beyond the
 * header and that stream does not reach a premature end.
 *
 * @see ProxyProtocolSpec
 */
public class InputAssist {
    private final CountingInputStream cin;
    private final CRC32CInputStream in;
    private int headerSize;

    public InputAssist(InputStream inputStream, boolean enforceChecksum) {
        // use MAX_VALUE before we know the actual header size
        headerSize = Integer.MAX_VALUE;

        cin = new CountingInputStream(inputStream);
        in = new CRC32CInputStream(cin, enforceChecksum);
    }

    /**
     * Reads the specified number of bytes to a UTF8 string.
     */
    public String readString(int length, String label) throws IOException {
        byte[] buf = readBytes(length, label);
        return new String(buf, StandardCharsets.UTF_8);
    }

    public int readByte() throws IOException {
        validateHeaderSizeBeforeRead(1);

        int readByte = in.read();
        checkEOF(readByte);
        return readByte;
    }

    public int readShort() throws IOException {
        validateHeaderSizeBeforeRead(2);

        int b1 = in.read();
        int b2 = in.read();
        checkEOF(b1 | b2);
        return (b1 << 8) + b2;
    }

    public int readInt() throws IOException {
        validateHeaderSizeBeforeRead(4);

        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        checkEOF(b1 | b2 | b3 | b4);
        return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
    }

    /**
     * Same as {@link #readBytesIntoBuf(int, String)} but reads the data into a newly created array
     * which is returned.
     */
    public byte[] readBytes(int length, String label) throws IOException, InvalidHeaderException {
        // to handle zero-length reads, InputStream.read with zero length does not return 0
        if (length == 0) {
            return new byte[0];
        }
        // Experimented with reusing a buffer instead of creating a new one every time,
        // but that did not make much difference.
        // There was no difference for a header without TLVs and about 13% smaller memory usage
        // for the headers with all the known TLVs. That does not seem to be worth the added code
        // complexity since this is not a very realistic case.
        validateHeaderSizeBeforeRead(length);

        byte[] b = new byte[length];
        int readCount = in.read(b);
        checkEOF(readCount);
        if (readCount < length) {
            throw new InvalidHeaderException("Premature end of the Proxy Protocol prefix data stream "
                    + "when reading " + label + ". "
                    + "Read " + readCount + " bytes instead of expected " + length + ".");
        }
        return b;
    }

    /**
     * Reads the protocol header size from the input stream.
     */
    void readHeaderSize() throws IOException {
        headerSize = readShort() + ProxyProtocolSpec.ADD_TO_HEADER_SIZE;
    }

    /**
     * Throws the EOF exception if the read result is -1.
     */
    private void checkEOF(int readResult) {
        if (readResult == -1) {
            throw throwEOF(cin.getCount());
        }
    }

    private InvalidHeaderException throwEOF(long readCount) {
        throw new InvalidHeaderException(
                "Premature end of the Proxy Protocol prefix data stream after "
                + readCount + " bytes");
    }

    void validateHeaderSizeBeforeRead(int readSize) {
        long countBeforeRead = getReadCount();
        if (countBeforeRead + readSize > headerSize) {
            throw new InvalidHeaderException(
                    "Proxy Protocol header is longer than its declared size " + headerSize + ".");
        }
    }

    /**
     * The number of bytes read from the provided stream so far.
     */
    public long getReadCount() {
        return cin.getCount();
    }

    /**
     * The position in the stream, 1 less than {@link #getReadCount()}.
     */
    public long getReadPos() {
        return getReadCount() - 1;
    }

    int readChecksum() throws IOException {
        return in.readChecksum();
    }

    int getChecksum() {
        return in.getChecksum();
    }

    public InputStream getDataInputStream() {
        return in;
    }

    void setHeaderSize(int headerSize) {
        this.headerSize = headerSize;
    }

    int getHeaderSize() {
        return headerSize;
    }
}
