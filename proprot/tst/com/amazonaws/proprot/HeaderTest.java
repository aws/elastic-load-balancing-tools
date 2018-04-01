/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.proprot.Header.SslFlags;
import com.amazonaws.proprot.ProxyProtocolSpec.AddressFamily;
import com.amazonaws.proprot.ProxyProtocolSpec.TransportProtocol;

public class HeaderTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testValidate_defaultValid() {
        new Header().validate();
    }

    @Test
    public void testVerifyAddressFamilyTransportProtocolCombination_happyPath() {
        AddressFamily addressFamily = AddressFamily.AF_INET;
        TransportProtocol transportProtocol = TransportProtocol.UNSPEC;

        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage(addressFamily.toString());
        thrown.expectMessage(transportProtocol.toString());
        thrown.expectMessage(Util.toHex(ProxyProtocolSpec.pack(addressFamily, transportProtocol)));

        Header header = new Header();
        header.setAddressFamily(addressFamily);
        header.setTransportProtocol(transportProtocol);
        header.validate();
    }

    @Test
    public void testVerifyAddressSize() {
        verifyAddressSize(AddressFamily.AF_INET, TransportProtocol.STREAM);
        verifyAddressSize(AddressFamily.AF_INET6, TransportProtocol.STREAM);
        verifyAddressSize(AddressFamily.AF_UNIX, TransportProtocol.STREAM);
        verifyAddressSize(AddressFamily.AF_UNSPEC, TransportProtocol.UNSPEC);
    }

    private void verifyAddressSize(AddressFamily addressFamily, TransportProtocol transportProtocol) {
        Header header = new Header();
        header.setAddressFamily(addressFamily);
        header.setTransportProtocol(transportProtocol);

        // right size
        {
            setAddresses(header, addressFamily.getAddressSize());
            header.validate();
        }

        // larger
        {
            setAddresses(header, addressFamily.getAddressSize() + 1);
            try {
                header.validate();
                fail();
            } catch (InvalidHeaderException expected) {}
        }

        // empty
        if (addressFamily.getAddressSize() > 0) {

            setAddresses(header, 0);
            try {
                header.validate();
                fail();
            } catch (InvalidHeaderException expected) {}
        }
    }

    private void setAddresses(Header header, int addressSize) {
        byte[] address = new byte[addressSize];
        Arrays.fill(address, (byte) 0xF1);
        header.setSrcAddress(address);
        header.setDstAddress(address);
    }

    @Test
    public void testSslContainerContains() {
        Header header = new Header();
        header.setSslFlags(SslFlags.getOptional(true, false, true, false));
        header.setSslVersion(Optional.of("version 1"));
        new Header().validate();
    }

    @Test
    public void testSslContainerDoesNotContain() {
        thrown.expect(InvalidHeaderException.class);
        Header header = new Header();
        header.setSslVersion(Optional.of("version 1"));
        header.validate();
    }

    @Test
    public void testEqualsHashCode() {
        SslFlags flags1 = SslFlags.getOptional(true, true, true, true).get();
        assertEquals(flags1, flags1);
        assertFalse(flags1.equals(null));
        assertFalse(flags1.equals(new Object()));

        {
            SslFlags flags2 = SslFlags.getOptional(true, true, true, true).get();
            assertEquals(flags1, flags2);
            assertEquals(flags1.hashCode(), flags2.hashCode());
            assertEquals(flags1.getClient(), flags2.getClient());
        }

        {
            SslFlags flags2 = SslFlags.getOptional(false, false, false, false).get();
            assertFalse(flags1.equals(flags2));
            assertFalse(flags1.hashCode() == flags2.hashCode());
        }
    }

    @Test
    public void testAddTlv_happyPath() {
        Header header = new Header();
        {
            TlvRaw tlv = new TlvRaw();
            tlv.setType(0xF3);
            header.addTlv(tlv);
        }
        {
            TlvRaw tlv = new TlvRaw();
            tlv.setType(0xF4);
            header.addTlv(tlv);
        }
        {
            TlvSubTypeRaw tlv = new TlvSubTypeRaw();
            tlv.setType(0xF4);
            tlv.setSubType(0x14);
            header.addTlv(tlv);
        }
    }

    @Test
    public void testAddTlv_immutable() {
        thrown.expect(UnsupportedOperationException.class);
        new Header().getTlvs().add(null);
    }

    @Test
    public void testAddTlv_knownType() {
        thrown.expect(InvalidHeaderException.class);
        TlvRaw tlv = new TlvRaw();
        tlv.setType(ProxyProtocolSpec.PP2_TYPE_ALPN);
        new Header().addTlv(tlv);
    }

    @Test
    public void testSslFlags() {
        {
            SslFlags flags = SslFlags.getOptional(0, false).get();
            assertSame(flags, SslFlags.getOptional(false, false, false, false).get());
            assertFalse(flags.isClientConnectedWithSsl());
            assertFalse(flags.isClientProvidedCertDuringConnection());
            assertFalse(flags.isClientProvidedCertDuringSession());
            assertFalse(flags.isClientVerifiedCert());
        }
        {
            SslFlags flags = SslFlags.getOptional(0xFF, true).get();
            assertSame(flags, SslFlags.getOptional(true, true, true, true).get());
            assertTrue(flags.isClientConnectedWithSsl());
            assertTrue(flags.isClientProvidedCertDuringConnection());
            assertTrue(flags.isClientProvidedCertDuringSession());
            assertTrue(flags.isClientVerifiedCert());
        }
    }
}
