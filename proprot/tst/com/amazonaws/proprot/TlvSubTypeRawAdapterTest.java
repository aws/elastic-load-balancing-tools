package com.amazonaws.proprot;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class TlvSubTypeRawAdapterTest {
    final TlvSubTypeRawAdapter subTypeAdapter = new TlvSubTypeRawAdapter(8);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testReadSubType() throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                new byte[] { -128, 1, 2, 3, 4}));
        TlvSubTypeRaw subTypeTlv = subTypeAdapter.read(new InputAssist(in, false), 4);
        assertArrayEquals(new byte[] {1, 2, 3},  subTypeTlv.getValue());
    }
    @Test
    public void testReadSubTypeWithNoValue() throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(new byte[]{-128}));
        TlvSubTypeRaw subTypeTlv = subTypeAdapter.read(new InputAssist(in, false), 1);
        assertArrayEquals(new byte[] {}, subTypeTlv.getValue());
    }

    @Test
    public void testWriteSubTypeWithNoValue() throws IOException {
        TlvSubTypeRaw subTypeTlv = new TlvSubTypeRaw();
        subTypeTlv.setType(1);
        subTypeTlv.setSubType(128);
        subTypeTlv.setValue(new byte[]{});
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        subTypeAdapter.writeValue(subTypeTlv, out);
        assertArrayEquals(new byte[]{-128}, buf.toByteArray());
    }

    @Test
    public void testReadSubTypeWithEmptyArray() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                new byte[0]));
        TlvSubTypeRaw subTypeTlv = subTypeAdapter.read(new InputAssist(in, false), 0);
        assertArrayEquals(new byte[] {},  subTypeTlv.getValue());
    }

    @Test
    public void testWriteValueForTlvSubType() throws IOException {
        TlvSubTypeRaw tlv = new TlvSubTypeRaw();
        int subType = 128;
        byte[] value = new byte[] {1, 2, 3, 4};
        byte[] expectedValue = new byte[] {(byte) subType, 1, 2, 3, 4};
        tlv.setValue(value);
        tlv.setSubType(subType);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        subTypeAdapter.writeValue(tlv, out);
        assertArrayEquals(expectedValue, buf.toByteArray());
    }
}
