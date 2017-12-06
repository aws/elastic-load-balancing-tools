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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;

public class TlvRawAdapterTest {
    final TlvRawAdapter adapter = new TlvRawAdapter(8);

    @Test
    public void testRead() throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4}));
        TlvRaw tlv = adapter.read(new InputAssist(in, false), 3);
        assertArrayEquals(new byte[] {1, 2, 3},  tlv.getValue());
    }

    @Test
    public void testWriteValue() throws IOException {
        TlvRaw tlv = new TlvRaw();
        byte[] value = new byte[] {1, 2, 3, 4};
        tlv.setValue(value);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        adapter.writeValue(tlv, out);
        assertArrayEquals(value, buf.toByteArray());
    }
    @Test
    public void testReadEmptyArray() throws IOException {
        byte[] expected = new byte[]{};
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(expected));
        TlvRaw tlv = adapter.read(new InputAssist(in, false), 0);
        assertArrayEquals(new byte[] {}, tlv.getValue());
    }

    @Test
    public void testWriteWithNoValue() throws IOException {
        TlvRaw tlv = new TlvRaw();
        tlv.setType(1);
        tlv.setValue(new byte[]{});
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        adapter.writeValue(tlv, out);
        assertArrayEquals(new byte[]{}, buf.toByteArray());
    }
}
