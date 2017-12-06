/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.junit.Test;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class CRC32CInputStreamTest {
    /**
     * Make sure our CRC32C algorithm works as expected.
     */
    @Test
    public void testCRC32CSamples() throws IOException {
        assertCRC32C(0xC1D04330, (byte) 'a');
        assertCRC32C(0x364B3FB7, (byte) 'a', (byte) 'b', (byte) 'c');
        assertCRC32C(0xc1d04330, (byte) 0x61);


        // samples from https://tools.ietf.org/html/rfc3720#appendix-B.4 ,
        // reverted because it seems they had wrong endianness according to the
        // Guava and JDK9 implementations of CRC32C
        assertCRC32C(0x8A9136AA,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertCRC32C(0x62A8AB43,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertCRC32C(0x46DD794E,
                (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
                (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13, (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17,
                (byte) 0x18, (byte) 0x19, (byte) 0x1A, (byte) 0x1B, (byte) 0x1C, (byte) 0x1D, (byte) 0x1E, (byte) 0x1F);
        assertCRC32C(0x113FDB5C,
                (byte) 0x1F, (byte) 0x1E, (byte) 0x1D, (byte) 0x1C, (byte) 0x1B, (byte) 0x1A, (byte) 0x19, (byte) 0x18,
                (byte) 0x17, (byte) 0x16, (byte) 0x15, (byte) 0x14, (byte) 0x13, (byte) 0x12, (byte) 0x11, (byte) 0x10,
                (byte) 0x0F, (byte) 0x0E, (byte) 0x0D, (byte) 0x0C, (byte) 0x0B, (byte) 0x0A, (byte) 0x09, (byte) 0x08,
                (byte) 0x07, (byte) 0x06, (byte) 0x05, (byte) 0x04, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00);
        assertCRC32C(0xD9963A56,
                (byte) 0x01, (byte) 0xC0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x18,
                (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
    }

    @Test
    public void testReadChecksum() throws Exception {
        byte[] data = new byte[] {0, 0, 0, 1};
        CRC32CInputStream in1 = new CRC32CInputStream(new ByteArrayInputStream(data), true);
        CRC32CInputStream in2 = new CRC32CInputStream(new ByteArrayInputStream(data), true);
        DataInputStream din2 = new DataInputStream(in2);
        assertEquals(in1.readChecksum(), din2.readInt());

        CRC32CInputStream in0 = new CRC32CInputStream(new ByteArrayInputStream(
                new byte[] {0, 0, 0, 0}), true);
        in0.read(new byte[4]);
        assertEquals(in0.getChecksum(), in1.getChecksum());

        in1.close();
        in2.close();
        din2.close();
        in0.close();
    }

    private void assertCRC32C(int crc, byte... data) throws IOException {
        byte[] buf = new byte[100];
        assertThat(buf.length, greaterThanOrEqualTo(data.length));

        CRC32CInputStream in = new CRC32CInputStream(new ByteArrayInputStream(data), true);
        assertEquals(data.length, in.read(buf));
        int checksum = in.getChecksum();
        in.close();
        assertEquals("Expected " + Util.toHex(crc)
                + ", calculated " + Util.toHex(checksum), crc, checksum);

        // make sure we calculate the same value as the hasher:
        {
            Hasher hasher = Hashing.crc32c().newHasher();
            hasher.putBytes(data);
            assertEquals("Expected " + Util.toHex(crc)
                    + ", calculated " + Util.toHex(hasher.hash().asInt()), crc, hasher.hash().asInt());
        }
    }
}
