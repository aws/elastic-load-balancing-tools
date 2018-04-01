/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import static com.amazonaws.proprot.Util.toHex;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.proprot.Header.SslFlags;
import com.amazonaws.proprot.ProxyProtocolSpec.AddressFamily;
import com.amazonaws.proprot.ProxyProtocolSpec.Command;
import com.amazonaws.proprot.ProxyProtocolSpec.TransportProtocol;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

public class ParserTest {
    private static final Optional<SslFlags> SSL_FLAGS = SslFlags.getOptional(true, false, true, false);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testReadPrefix_happyPath() throws IOException {
        newParser(ProxyProtocolSpec.PREFIX).readPrefix();
    }

    @Test
    public void testReadPrefix_different() throws IOException {
        byte[] data = ProxyProtocolSpec.PREFIX.clone();
        data[2] = (byte) 0xFF;

        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("0xff");

        newParser(data).readPrefix();
    }

    @Test
    public void testReadPrefix_truncated() throws IOException {
        byte[] data = new byte[] {
                ProxyProtocolSpec.PREFIX[0],
                ProxyProtocolSpec.PREFIX[1],
                ProxyProtocolSpec.PREFIX[2],
        };

        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage(Integer.toString(data.length));
        newParser(data).readPrefix();
    }

    @Test
    public void testReadPrefix_empty() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("0");
        newParser(new byte[0]).readPrefix();
    }

    @Test
    public void testReadCommand_happyPath() throws IOException {
        assertEquals(Command.PROXY, newParser(0x21).readCommand());
    }

    @Test
    public void testReadCommand_badVersion() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        // expected version 2
        thrown.expectMessage("2");
        // read version
        thrown.expectMessage("0xf");
        assertEquals(Command.PROXY, newParser(0xF1).readCommand());
    }

    @Test
    public void testReadCommand_badValue() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("0xd");
        thrown.expectMessage(Command.class.getSimpleName());
        assertEquals(Command.PROXY, newParser(0x2D).readCommand());
    }

    @Test
    public void testGetAddressFamily_happyPath() {
        assertEquals(AddressFamily.AF_UNIX,
                newParser(new byte[0]).getAddressFamily(0x3F));
    }

    @Test
    public void testGetAddressFamily_badValue() {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("0xf");
        newParser(new byte[0]).getAddressFamily(0xF1);
    }

    @Test
    public void testGetTransportProtocol_happyPath() {
        assertEquals(TransportProtocol.DGRAM,
                newParser(new byte[0]).getTransportProtocol(0xF2));
    }

    @Test
    public void testGetTransportProtocol_badValue() {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("0xf");
        newParser(new byte[0]).getTransportProtocol(0x1F);
    }

    @Test
    public void testToString() {
        assertEquals("[0xff, 0x0, 0xa]",
                new Parser().toString(new byte[] {-1, 0, 10}));
    }

    @Test
    public void testReadAddresses_ip4() throws IOException {
        AddressFamily addressFamily = AddressFamily.AF_INET;
        TransportProtocol transportProtocol = TransportProtocol.STREAM;
        Command command = Command.PROXY;
        int ipSize = addressFamily.getAddressSize();
        int portSize = 2;

        byte[] srcAddress = InetAddress.getByName("1.2.3.4").getAddress();
        byte[] dstAddress = InetAddress.getByName("5.6.7.8").getAddress();
        short srcPort = 501;
        short dstPort = 601;

        byte[] stream = Bytes.concat(srcAddress, dstAddress,
                Shorts.toByteArray(srcPort), Shorts.toByteArray(dstPort));
        assertEquals(ipSize * 2 + portSize * 2, stream.length);

        Parser parser = newParser(stream);
        Header header = new Header();
        header.setCommand(command);
        header.setAddressFamily(addressFamily);
        header.setTransportProtocol(transportProtocol);
        parser.readAddresses(header);

        assertArrayEquals(srcAddress, header.getSrcAddress());
        assertArrayEquals(dstAddress, header.getDstAddress());
        assertEquals(srcPort, header.getSrcPort());
        assertEquals(dstPort, header.getDstPort());
    }

    @Test
    public void testReadAddresses_ip6() throws IOException {
        AddressFamily addressFamily = AddressFamily.AF_INET6;
        TransportProtocol transportProtocol = TransportProtocol.STREAM;
        Command command = Command.PROXY;
        int ipSize = addressFamily.getAddressSize();
        int portSize = 2;

        byte[] srcAddress = InetAddress.getByName("1:2:3:4:5:6:7:8").getAddress();
        byte[] dstAddress = InetAddress.getByName("9:A:B:C:D:E:F:1").getAddress();
        short srcPort = 501;
        short dstPort = 601;

        assertEquals(ipSize, srcAddress.length);
        assertEquals(ipSize, dstAddress.length);

        byte[] stream = Bytes.concat(srcAddress, dstAddress,
                Shorts.toByteArray(srcPort), Shorts.toByteArray(dstPort));
        assertEquals(ipSize * 2 + portSize * 2, stream.length);

        Parser parser = newParser(stream);
        Header header = new Header();
        header.setCommand(command);
        header.setAddressFamily(addressFamily);
        header.setTransportProtocol(transportProtocol);
        parser.readAddresses(header);

        assertArrayEquals(srcAddress, header.getSrcAddress());
        assertArrayEquals(dstAddress, header.getDstAddress());
        assertEquals(srcPort, header.getSrcPort());
        assertEquals(dstPort, header.getDstPort());
    }

    @Test
    public void testReadAddresses_unix() throws IOException {
        AddressFamily addressFamily = AddressFamily.AF_UNIX;
        TransportProtocol transportProtocol = TransportProtocol.DGRAM;
        Command command = Command.PROXY;
        int addressSize = addressFamily.getAddressSize();

        byte[] srcAddress = new byte[] {1,2,3,4};
        byte[] srcPadding = new byte[addressSize - srcAddress.length];
        byte[] dstAddress = new byte[] {6,7,8,9,10};
        byte[] dstPadding = new byte[addressSize - dstAddress.length];

        byte[] stream = Bytes.concat(srcAddress, srcPadding, dstAddress, dstPadding);
        assertEquals(addressSize * 2, stream.length);


        Parser parser = newParser(stream);
        Header header = new Header();
        header.setCommand(command);
        header.setAddressFamily(addressFamily);
        header.setTransportProtocol(transportProtocol);
        parser.readAddresses(header);

        assertEquals(0, header.getSrcPort());
        assertEquals(0, header.getDstPort());
        assertArrayEquals(srcAddress, header.getSrcAddress());
        assertArrayEquals(dstAddress, header.getDstAddress());
    }

    @Test
    public void testReadAddresses_unspec() throws IOException {
        Parser parser = newParser(new byte[0]);
        Header header = new Header();
        header.setAddressFamily(AddressFamily.AF_UNSPEC);
        parser.readAddresses(header);
        assertArrayEquals(new byte[0], header.getSrcAddress());
        assertArrayEquals(new byte[0], header.getDstAddress());
        assertEquals(0, header.getSrcPort());
        assertEquals(0, header.getDstPort());
    }

    @Test
    public void testReadUnixAddress() throws IOException {
        checkUnixAddressRoundtrip(new byte[] {1,2,3,4,5});
        checkUnixAddressRoundtrip(new byte[0]);

        byte[] b = new byte[AddressFamily.AF_UNIX.getAddressSize()];
        Arrays.fill(b, (byte) 1);
        checkUnixAddressRoundtrip(b);
    }

    private void checkUnixAddressRoundtrip(byte[] expected) throws IOException {
        final byte[] b = Arrays.copyOf(expected, 200);
        byte[] b2 = newParser(b).readUnixAddress("a string");
        assertArrayEquals(expected, b2);
    }

    /**
     * Reads 2 TLVs - one - declared, second - read as undeclared raw.
     */
    @Test
    public void testReadTLVs_happyPath() throws IOException {
        TestTlvAdapter a = new TestTlvAdapter();
        a.bytesToRead = 2;
        int type2 = a.getType() + 1;
        int type3 = ProxyProtocolSpec.PP2_TYPE_AUTHORITY;

        byte[] data = new byte[] {
                // TLV with the registered adapter
                (byte) a.getType(), 0, (byte) a.bytesToRead, 1, 2,
                // TLV without registered adapter
                (byte) type2, 0, 4, 1, 2, 3, 4,
                // TLV read to header
                (byte) type3, 0, 3, 'a', 'b', 'c'};
        Parser parser = newParser(data);
        parser.getAssist().setHeaderSize(data.length);
        parser.getAdapters().put(a.getType(), a);

        Header header = new Header();
        assertTrue(header.getTlvs().isEmpty());
        assertFalse(header.getAuthority().isPresent());
        parser.readTLVs(header);
        assertEquals(2, header.getTlvs().size());

        assertEquals(a.getType(), header.getTlvs().get(0).getType());
        TlvRaw tlv = (TlvRaw) header.getTlvs().get(1);
        assertEquals(type2, tlv.getType());
        assertArrayEquals(new byte[] {1,2,3,4}, tlv.getValue());

        assertEquals("abc", header.getAuthority().get());
    }
    @Test
    public void testReadTLVSubTypes() throws IOException {
        TlvSubTypeRawAdapter a = new TlvSubTypeRawAdapter(0Xf);
        int type2 = a.getType() + 1;
        int type3 = ProxyProtocolSpec.PP2_TYPE_AUTHORITY;

        byte[] data = new byte[] {
                // TLV with the registered TlvSubType adapter
                (byte) a.getType(), 0, (byte) 3, 5, 1, 2,
                // TLV without registered adapter
                (byte) type2, 0, 4, 1, 2, 3, 4,
                // TLV read to header
                (byte) type3, 0, 3, 'a', 'b', 'c'};
        Parser parser = newParser(data);
        parser.getAssist().setHeaderSize(data.length);
        parser.getAdapters().put(a.getType(), a);

        Header header = new Header();
        assertTrue(header.getTlvs().isEmpty());
        assertFalse(header.getAuthority().isPresent());
        parser.readTLVs(header);
        assertEquals(2, header.getTlvs().size());

        assertEquals(a.getType(), header.getTlvs().get(0).getType());
        TlvSubTypeRaw tlv0 = (TlvSubTypeRaw) header.getTlvs().get(0);
        TlvRaw tlv = (TlvRaw) header.getTlvs().get(1);
        assertEquals(type2, tlv.getType());
        assertArrayEquals(new byte[] {1,2,3,4}, tlv.getValue());


        assertEquals("abc", header.getAuthority().get());
    }

    @Test
    public void testReadTLVs_twoTlvsOfSameType() throws IOException {
        int type = 0x55;
        byte[] data = new byte[] {
                (byte) type, 0, 4, 1, 2, 3, 4,
                (byte) type, 0, 4, 1, 2, 3, 4,
                };
        Parser parser = newParser(data);
        parser.getAssist().setHeaderSize(data.length);

        parser.readTLVs(new Header());
    }

    @Test
    public void testReadTLVs_mismatchingType() throws IOException {

        TestTlvAdapter a = new TestTlvAdapter();
        a.bytesToRead = 2;

        int type2 = 22;
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(toHex(type2));
        thrown.expectMessage(toHex(a.getType()));

        byte[] data = new byte[] {
                (byte) type2, 0, (byte) a.bytesToRead, 1, 2};
        Parser parser = newParser(data);
        parser.getAssist().setHeaderSize(data.length);
        parser.getAdapters().put(type2, a);
        parser.readTLVs(new Header());
    }

    @Test
    public void testReadTlvValueToHeader_happyPath() throws IOException {
        TestTlvAdapter adapter = new TestTlvAdapter();
        adapter.bytesToRead = 2;
        newParser(new byte[] {1,2,3}).readTlvValue(adapter, 2);
    }

    @Test
    public void testReadTlvValueToHeader_wrongReadLen() throws IOException {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("1");
        thrown.expectMessage("2");

        TestTlvAdapter adapter = new TestTlvAdapter();
        adapter.bytesToRead = 2;
        newParser(new byte[] {1,2,3}).readTlvValue(adapter, 1);
    }

    @Test
    public void testReadAddressFamilyAndTransportProtocol() throws IOException {
        Header header = new Header();
        header.setCommand(Command.PROXY);
        assertEquals(AddressFamily.AF_UNSPEC, header.getAddressFamily());
        assertEquals(TransportProtocol.UNSPEC, header.getTransportProtocol());
        AddressFamily af = AddressFamily.AF_INET6;
        TransportProtocol tp = TransportProtocol.STREAM;
        Parser parser = newParser(new byte[] {(byte) ProxyProtocolSpec.pack(af, tp)});
        parser.readAddressFamilyAndTransportProtocol(header);
        assertEquals(af, header.getAddressFamily());
        assertEquals(tp, header.getTransportProtocol());
    }

    @Test
    public void testMaybeValidateChecksum_happyPath() throws IOException {
        byte[] data = new byte[] {
                (byte) ProxyProtocolSpec.PP2_TYPE_CRC32C, 0, 4,
                (byte) 0xE3, (byte) 0x7E, (byte) 0xA1, (byte) 0x85};
        Parser parser = newParser(data);
        parser.getAssist().setHeaderSize(data.length);
        parser.readTLVs(new Header());

        parser.maybeValidateChecksum();
    }

    @Test
    public void testMaybeValidateChecksum_doesNotMatch() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("Invalid ");
        thrown.expectMessage("0x0");
        byte[] data = new byte[] {
                (byte) ProxyProtocolSpec.PP2_TYPE_CRC32C, 0, 4, 0, 0, 0, 0};
        Parser parser = newParser(data);
        parser.getAssist().setHeaderSize(data.length);
        parser.readTLVs(new Header());

        parser.maybeValidateChecksum();
    }

    @Test
    public void testMaybeValidateChecksum_notEnforceChecksum() {
        Parser parser = newParser(new byte[0]);
        parser.setEnforceChecksum(false);
        parser.maybeValidateChecksum();
    }

    @Test
    public void testMaybeValidateChecksum_checksumNotFound() {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("not found");

        Parser parser = newParser(new byte[0]);
        parser.maybeValidateChecksum();
    }

    @Test
    public void testReadAlpn() throws IOException {
        byte[] data = new byte[] {1, 2, 3, 4};
        Header header = new Header();
        assertFalse(header.getAlpn().isPresent());
        newParser(data).readAlpn(header, data.length);
        assertArrayEquals(data, header.getAlpn().get());
    }

    @Test
    public void testReadAuthority() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        assertFalse(header.getAuthority().isPresent());
        newParser(data).readAuthority(header, data.length);
        assertEquals("abc", header.getAuthority().get());
    }

    @Test
    public void testReadSslFlags_allSet() throws IOException {
        byte[] data = new byte[] {7, 0, 0, 0, 0};
        Header header = new Header();
        assertFalse(header.getSslFlags().isPresent());
        newParser(data).readSslFlags(header, data.length);
        SslFlags flags = header.getSslFlags().get();
        assertTrue(flags.isClientConnectedWithSsl());
        assertTrue(flags.isClientProvidedCertDuringConnection());
        assertTrue(flags.isClientProvidedCertDuringSession());
        assertTrue(flags.isClientVerifiedCert());
    }

    @Test
    public void testReadSslFlags_allClear() throws IOException {
        byte[] data = new byte[] {0, 0, 0, 0, 1};
        Header header = new Header();
        assertFalse(header.getSslFlags().isPresent());
        newParser(data).readSslFlags(header, data.length);
        SslFlags flags = header.getSslFlags().get();
        assertFalse(flags.isClientConnectedWithSsl());
        assertFalse(flags.isClientProvidedCertDuringConnection());
        assertFalse(flags.isClientProvidedCertDuringSession());
        assertFalse(flags.isClientVerifiedCert());
    }

    @Test
    public void testReadCRC32c() throws IOException {
        byte[] data = Ints.toByteArray(12345);
        newParser(data).readCRC32c(data.length);
    }

    @Test
    public void testReadSslVersion() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        header.setSslFlags(SSL_FLAGS);

        assertFalse(header.getSslVersion().isPresent());
        newParser(data).readSslVersion(header, data.length);
        assertEquals("abc", header.getSslVersion().get());
    }

    @Test
    public void testReadSslCommonName() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        header.setSslFlags(SSL_FLAGS);

        assertFalse(header.getSslCommonName().isPresent());
        newParser(data).readSslCommonName(header, data.length);
        assertEquals("abc", header.getSslCommonName().get());
    }

    @Test
    public void testReadSslCipher() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        header.setSslFlags(SSL_FLAGS);

        assertFalse(header.getSslCipher().isPresent());
        newParser(data).readSslCipher(header, data.length);
        assertEquals("abc", header.getSslCipher().get());
    }

    @Test
    public void testReadSslSigAlg() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        header.setSslFlags(SSL_FLAGS);

        assertFalse(header.getSslSigAlg().isPresent());
        newParser(data).readSslSigAlg(header, data.length);
        assertEquals("abc", header.getSslSigAlg().get());
    }

    @Test
    public void testReadSslKeyAlg() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        header.setSslFlags(SSL_FLAGS);

        assertFalse(header.getSslKeyAlg().isPresent());
        newParser(data).readSslKeyAlg(header, data.length);
        assertEquals("abc", header.getSslKeyAlg().get());
    }

    @Test
    public void testReadNetNS() throws IOException {
        byte[] data = new byte[] {'a', 'b', 'c'};
        Header header = new Header();
        header.setSslFlags(SSL_FLAGS);

        assertFalse(header.getNetNS().isPresent());
        newParser(data).readNetNS(header, data.length);
        assertEquals("abc", header.getNetNS().get());
    }
    
    @Test
    public void testAddressRemovalWithLocalCommand() throws IOException {      
        byte stream[] = {
                0x0d, 0x0a, 0x0d, 0x0a, /* Start of Sig */
                0x00, 0x0d, 0x0a, 0x51,
                0x55, 0x49, 0x54, 0x0a, /* End of Sig */
                0x20, 0x11, 0x00, 0x0c, /* ver_cmd, fam and len */
                (byte) 0xac, 0x1f, 0x07, 0x71, /* Caller src ip */
                (byte) 0xac, 0x1f, 0x0a, 0x1f, /* Endpoint dst ip */
                (byte) 0xc8, (byte) 0xf2, 0x00, 0x50 /* Proxy src port & dst port */
        };
        
        Parser parser = new Parser();
        parser.setEnforceChecksum(false);
        
        Header header = new Header();
        byte[] defaultAddress = header.getSrcAddress(); 
        header = parser.read(new ByteArrayInputStream(stream));
        
        assertEquals(Command.LOCAL, header.getCommand());
        assertEquals(defaultAddress, header.getSrcAddress());
        assertEquals(defaultAddress, header.getDstAddress());
        assertEquals(0, header.getSrcPort());
        assertEquals(0, header.getDstPort());
        assertEquals(AddressFamily.AF_UNSPEC, header.getAddressFamily());
        assertEquals(TransportProtocol.UNSPEC, header.getTransportProtocol());
    }

    private Parser newParser(int b) {
        return newParser(new byte[] {(byte) b});
    }

    private Parser newParser(byte[] data) {
        final Parser parser = new Parser();
        parser.init(new ByteArrayInputStream(data));
        return parser;
    }

    private final class TestTlvAdapter implements TlvAdapter<TlvRaw> {
        public int bytesToRead;

        @Override
        public void writeValue(Tlv value, DataOutputStream out) {}

        @Override
        public TlvRaw read(InputAssist inputAssist, int length) throws IOException {
            TlvRaw tlv = new TlvRaw();
            tlv.setType(getType());
            tlv.setValue(new byte[bytesToRead]);
            inputAssist.getDataInputStream().read(tlv.getValue());
            return tlv;
        }

        @Override
        public int getType() {
            return 0xF0;
        }
    }
}
