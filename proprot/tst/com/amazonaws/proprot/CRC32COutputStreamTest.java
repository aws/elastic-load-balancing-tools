/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import com.google.common.primitives.Ints;

public class CRC32COutputStreamTest {
    @Test
    public void testCRC32CSamples() throws IOException {
        // samples from https://tools.ietf.org/html/rfc7143 ending in byte[] {0, 0, 0, 0}
        // the checksum is known for the zero checksum value
        assertCRC32C(0x8A9136AA,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertCRC32C(0xD9963A56,
                (byte) 0x01, (byte) 0xC0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x18,
                (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
    }

    private void assertCRC32C(int crc, byte... data) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        CRC32COutputStream out = new CRC32COutputStream(bout, true);
        int dataLength = data.length - Integer.BYTES;
        out.write(data, 0, dataLength);
        out.writeChecksum();

        byte[] buf = bout.toByteArray();
        assertEquals(data.length, buf.length);
        assertArrayEquals(Ints.toByteArray(crc), Arrays.copyOfRange(buf, dataLength, data.length));

        // code coverage, keep static code analysis happy
        out.flush();
        out.close();
    }
}
