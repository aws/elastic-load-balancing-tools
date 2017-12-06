/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class GeneratorTest {
    private static final int TLV_TYPE = 0xF0;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetAdapter_found() {
        Tlv tlv = new TestTlv(TLV_TYPE);
        Generator generator = new Generator();
        TestTlvAdapter adapter = new TestTlvAdapter();
        generator.getAdapters().put(tlv.getType(), adapter);
        assertSame(adapter, generator.getAdapter(tlv));
    }

    @Test
    public void testGetAdapter_raw() {
        TlvRaw tlv = new TlvRaw();
        tlv.setType(0xF0);
        tlv.setValue(new byte[] {1, 2, 3});

        assertThat(new Generator().getAdapter(tlv), instanceOf(TlvRawAdapter.class));
    }

    @Test
    public void testGetAdapter_noAdapter() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("0xf0");

        Tlv tlv = new TestTlv(0xF0);
        new Generator().getAdapter(tlv);
    }

    @Test
    public void testMaybePadHeader_notPadded() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Generator().maybePadHeader(2000, out);
        assertEquals(0, out.size());
    }

    @Test
    public void testMaybePadHeader_exactSize() throws IOException {
        callPadHeader(0);
    }

    @Test
    public void testMaybePadHeader_exceedsEnforcedSize() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        callPadHeader(-1);
    }

    /**
     * Special value - can't pad 1 byte.
     */
    @Test
    public void testMaybePadHeader_lessThanEnforcedSizeBy1() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        callPadHeader(1);
    }

    /**
     * Special value - can't pad 2 bytes.
     */
    @Test
    public void testMaybePadHeader_lessThenEnforcedSizeBy2() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        callPadHeader(2);
    }

    @Test
    public void testMaybePadHeader_lessThenEnforcedSize() throws IOException {
        callPadHeader(3);
        callPadHeader(4);
        callPadHeader(10000);
    }

    private void callPadHeader(int enforcedSizeChange) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Generator generator = new Generator();
        generator.setEnforcedSize(Optional.of(2000 + enforcedSizeChange));
        generator.maybePadHeader(2000, out);
        assertEquals(enforcedSizeChange, out.size());
    }

    private class TestTlv implements Tlv {
        private final int type;

        public TestTlv(int type) {
            this.type = type;
        }

        @Override
        public int getType() {
            return type;
        }
    }

    private class TestTlvAdapter implements TlvAdapter<TestTlv> {

        @Override
        public int getType() {
            return TLV_TYPE;
        }

        @Override
        public TestTlv read(InputAssist inputAssist, int length) {
            return null;
        }

        @Override
        public void writeValue(Tlv tlv, DataOutputStream out) {}
    }
}
