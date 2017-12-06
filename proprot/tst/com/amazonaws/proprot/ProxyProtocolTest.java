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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.proprot.Header.SslFlags;
import com.amazonaws.proprot.ProxyProtocolSpec.AddressFamily;
import com.amazonaws.proprot.ProxyProtocolSpec.Command;
import com.amazonaws.proprot.ProxyProtocolSpec.TransportProtocol;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;


public class ProxyProtocolTest {
    private static final int TLV_TYPE = 0xF0;
    private static final int TLV_SUB_TYPE = 0xEC;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testRoundtrip_ipv6() throws IOException {
        final Header header = createBaseHeader();

        header.setAlpn(Optional.of(new byte[] {3, 4, 5}));
        header.setAuthority(Optional.of("authority 1"));
        header.setSslFlags(SslFlags.getOptional(true, true, true, true));
        header.setSslVersion(Optional.of("ssl version 1"));
        header.setSslCommonName(Optional.of("ssl common name 1"));
        header.setSslCipher(Optional.of("ssl cipher 1"));
        header.setSslSigAlg(Optional.of("sig algorithm 1"));
        header.setSslKeyAlg(Optional.of("key algorithm 1"));
        header.setNetNS(Optional.of("Net namespace 1"));

        TlvRaw tlv = new TlvRaw();
        tlv.setType(TLV_TYPE);
        tlv.setValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        header.addTlv(tlv);

        ProxyProtocol protocol = new ProxyProtocol();

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        // validate
        assertEquals(header.getCommand(), header2.getCommand());
        assertEquals(header.getAddressFamily(), header2.getAddressFamily());
        assertEquals(header.getTransportProtocol(), header2.getTransportProtocol());
        assertArrayEquals(header.getSrcAddress(), header2.getSrcAddress());
        assertArrayEquals(header.getDstAddress(), header2.getDstAddress());
        assertEquals(header.getSrcPort(), header2.getSrcPort());
        assertEquals(header.getDstPort(), header2.getDstPort());

        assertArrayEquals(header.getAlpn().get(), header2.getAlpn().get());
        assertEquals(header.getAuthority(), header2.getAuthority());
        assertEquals(header.getSslFlags(), header2.getSslFlags());
        assertEquals(header.getSslVersion(), header2.getSslVersion());
        assertEquals(header.getSslCommonName(), header2.getSslCommonName());
        assertEquals(header.getSslCipher(), header2.getSslCipher());
        assertEquals(header.getSslSigAlg(), header2.getSslSigAlg());
        assertEquals(header.getSslKeyAlg(), header2.getSslKeyAlg());
        assertEquals(header.getNetNS(), header2.getNetNS());

        TlvRaw tlv2 = (TlvRaw) Iterables.getOnlyElement(header2.getTlvs());
        assertEquals(tlv.getType(), tlv2.getType());
        assertArrayEquals(tlv.getValue(), tlv2.getValue());
    }

    @Test
    public void testRoundtrip_ipv4() throws IOException {
        final Header header = new Header();
        header.setCommand(Command.PROXY);
        header.setAddressFamily(AddressFamily.AF_INET);
        header.setTransportProtocol(TransportProtocol.STREAM);
        header.setSrcAddress(InetAddress.getByName("196.168.1.1").getAddress());
        header.setDstAddress(InetAddress.getByName("196.168.5.5").getAddress());
        header.setSrcPort(1111);
        header.setDstPort(2222);

        TlvRaw tlv = new TlvRaw();
        tlv.setType(0xF0);
        tlv.setValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        header.addTlv(tlv);

        // write
        ProxyProtocol protocol = new ProxyProtocol();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        // validate
        assertEquals(header.getCommand(), header2.getCommand());
        assertEquals(header.getAddressFamily(), header2.getAddressFamily());
        assertEquals(header.getTransportProtocol(), header2.getTransportProtocol());
        assertArrayEquals(header.getSrcAddress(), header2.getSrcAddress());
        assertArrayEquals(header.getDstAddress(), header2.getDstAddress());
        assertEquals(header.getSrcPort(), header2.getSrcPort());
        assertEquals(header.getDstPort(), header2.getDstPort());

        TlvRaw tlv2 = (TlvRaw) Iterables.getOnlyElement(header2.getTlvs());
        assertEquals(tlv.getType(), tlv2.getType());
        assertArrayEquals(tlv.getValue(), tlv2.getValue());
    }

    @Test
    public void testRoundtrip_unix() throws IOException {
        final Header header = new Header();
        header.setCommand(Command.PROXY);
        header.setAddressFamily(AddressFamily.AF_UNIX);
        header.setTransportProtocol(TransportProtocol.STREAM);
        header.setSrcAddress(new byte[] {11, 12, 13});
        header.setDstAddress(new byte[] {11, 12, 13});

        TlvRaw tlv = new TlvRaw();
        tlv.setType(0xF0);
        tlv.setValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        header.addTlv(tlv);

        // write
        ProxyProtocol protocol = new ProxyProtocol();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        // validate
        assertEquals(header.getCommand(), header2.getCommand());
        assertEquals(header.getAddressFamily(), header2.getAddressFamily());
        assertEquals(header.getTransportProtocol(), header2.getTransportProtocol());
        assertArrayEquals(header.getSrcAddress(), header2.getSrcAddress());
        assertArrayEquals(header.getDstAddress(), header2.getDstAddress());
        assertEquals(header.getSrcPort(), header2.getSrcPort());
        assertEquals(header.getDstPort(), header2.getDstPort());

        TlvRaw tlv2 = (TlvRaw) Iterables.getOnlyElement(header2.getTlvs());
        assertEquals(tlv.getType(), tlv2.getType());
        assertArrayEquals(tlv.getValue(), tlv2.getValue());
    }

    @Test
    public void testRoundtrip_unspec() throws IOException {
        final Header header = new Header();
        header.setCommand(Command.LOCAL);
        header.setAddressFamily(AddressFamily.AF_UNSPEC);
        header.setTransportProtocol(TransportProtocol.UNSPEC);

        TlvRaw tlv = new TlvRaw();
        tlv.setType(0xF0);
        tlv.setValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        header.addTlv(tlv);

        // write
        ProxyProtocol protocol = new ProxyProtocol();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        // validate
        assertEquals(header.getCommand(), header2.getCommand());
        assertEquals(header.getAddressFamily(), header2.getAddressFamily());
        assertEquals(header.getTransportProtocol(), header2.getTransportProtocol());
        assertArrayEquals(header.getSrcAddress(), header2.getSrcAddress());
        assertArrayEquals(header.getDstAddress(), header2.getDstAddress());
        assertEquals(0, header2.getSrcPort());
        assertEquals(0, header2.getDstPort());

        TlvRaw tlv2 = (TlvRaw) Iterables.getOnlyElement(header2.getTlvs());
        assertEquals(tlv.getType(), tlv2.getType());
        assertArrayEquals(tlv.getValue(), tlv2.getValue());
    }

    @Test
    public void testRoundtrip_customAdapter() throws IOException {
        final Header header = createBaseHeader();

        TestTlv tlv = new TestTlv();
        tlv.setValue(7);
        header.addTlv(tlv);

        ProxyProtocol protocol = new ProxyProtocol();
        protocol.getAdapters().put(TLV_TYPE, new TestTlvAdapter());

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        TestTlv tlv2 = (TestTlv) Iterables.getOnlyElement(header2.getTlvs());
        assertEquals(tlv.getType(), tlv2.getType());
        assertEquals(tlv.getValue(), tlv2.getValue());
    }

    @Test
    public void testRoundtrip_subTypeAdapter() throws IOException {
        final Header header = createBaseHeader();

        TlvSubTypeRaw tlv = new TlvSubTypeRaw();
        tlv.setType(TLV_SUB_TYPE);
        tlv.setSubType(7);
        tlv.setValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        header.addTlv(tlv);

        ProxyProtocol protocol = new ProxyProtocol();
        TlvSubTypeRawAdapter adapter = new TlvSubTypeRawAdapter(TLV_SUB_TYPE);
        protocol.setAdapters(ImmutableList.of(adapter));

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        TlvSubTypeRaw tlv2 = (TlvSubTypeRaw) Iterables.getOnlyElement(header2.getTlvs());
        assertEquals(tlv.getType(), tlv2.getType());
        assertArrayEquals(tlv.getValue(), tlv2.getValue());
    }

    @Test
    public void testRoundtrip_noChecksum() throws IOException {
        final Header header = createBaseHeader();

        ProxyProtocol protocol = new ProxyProtocol();
        protocol.setEnforceChecksum(false);

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        protocol.read(in);
    }

    @Test
    public void testRoundtrip_noUnknown() throws IOException {
        final Header header = createBaseHeader();

        TlvRaw tlv = new TlvRaw();
        tlv.setType(TLV_TYPE);
        tlv.setValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        header.addTlv(tlv);

        ProxyProtocol protocol = new ProxyProtocol();
        protocol.setReadUnknownTLVs(false);

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);

        // validate
        assertTrue(header2.getTlvs().isEmpty());
    }

    @Test
    public void testEnforceSize_padding() throws IOException {
        int headerSize = 200;

        ProxyProtocol protocol = new ProxyProtocol();
        protocol.setEnforcedSize(Optional.of(headerSize));

        Header header = createBaseHeader();

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        assertEquals(headerSize, out.toByteArray().length);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);
        assertTrue(header2.getTlvs().isEmpty());
    }

    @Test
    public void testEnforceSize_paddingNoChecksum() throws IOException {
        int headerSize = 200;

        ProxyProtocol protocol = new ProxyProtocol();
        protocol.setEnforceChecksum(false);
        protocol.setEnforcedSize(Optional.of(headerSize));

        Header header = createBaseHeader();

        // write
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        protocol.write(header, out);

        assertEquals(headerSize, out.toByteArray().length);

        // read
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Header header2 = protocol.read(in);
        assertTrue(header2.getTlvs().isEmpty());
    }

    @Test
    public void testEnforceSize_tooLong() throws IOException {
        thrown.expect(InvalidHeaderException.class);

        int headerSize = 5;

        ProxyProtocol protocol = new ProxyProtocol();
        protocol.setEnforcedSize(Optional.of(headerSize));

        protocol.write(createBaseHeader(), new ByteArrayOutputStream());
    }

    @Test
    public void testValidateAdapters_happyPath() {
        int type = 0xEE;
        new ProxyProtocol().setAdapters(Collections.singleton(new TlvRawAdapter(type)));
    }

    @Test
    public void testValidateAdapters_invalidType() {
        thrown.expect(IllegalArgumentException.class);
        int type = ProxyProtocolSpec.PP2_TYPE_ALPN;
        new ProxyProtocol().setAdapters(Collections.singleton(new TlvRawAdapter(type)));
    }

    @Test
    public void testSetAdapters_happyPath() {
        ProxyProtocol proxyProtocol = new ProxyProtocol();
        TestTlvAdapter adapter = new TestTlvAdapter();
        proxyProtocol.setAdapters(Collections.singleton(adapter));
        assertSame(adapter, Iterables.getOnlyElement(proxyProtocol.getAdapters().values()));
    }

    @Test
    public void testSetAdapters_duplicateType() {
        thrown.expect(IllegalArgumentException.class);
        ProxyProtocol proxyProtocol = new ProxyProtocol();
        TestTlvAdapter adapter = new TestTlvAdapter();
        proxyProtocol.setAdapters(
                ImmutableList.of(adapter, new TlvRawAdapter(adapter.getType())));
    }
    
    private Header createBaseHeader() throws UnknownHostException {
        Header header = new Header();
        header.setCommand(Command.PROXY);
        header.setAddressFamily(AddressFamily.AF_INET6);
        header.setTransportProtocol(TransportProtocol.STREAM);
        header.setSrcAddress(InetAddress.getByName("1::2").getAddress());
        header.setDstAddress(InetAddress.getByName("3::4").getAddress());
        header.setSrcPort(1111);
        header.setDstPort(2222);
        return header;
    }

    private static class TestTlv implements Tlv {
        private int value;

        @Override
        public int getType() {
            return TLV_TYPE;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    private static class TestTlvAdapter implements TlvAdapter<TestTlv> {

        @Override
        public int getType() {
            return TLV_TYPE;
        }

        @Override
        public TestTlv read(InputAssist inputAssist, int length) throws IOException {
            assert length == 1;

            TestTlv tlv = new TestTlv();
            tlv.setValue(inputAssist.readByte());
            return tlv;
        }

        @Override
        public void writeValue(Tlv tlv, DataOutputStream out) throws IOException {
            out.writeByte(((TestTlv) tlv).getValue());
        }
    }
}
