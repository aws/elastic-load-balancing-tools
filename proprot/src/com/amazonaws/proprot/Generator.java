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
import static com.amazonaws.proprot.Util.writeShort;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.proprot.Header.SslFlags;
import com.amazonaws.proprot.ProxyProtocolSpec.AddressFamily;

/**
 * Generates Proxy Protocol v2 header.
 * Is not thread-safe.
 * @see ProxyProtocol#read(java.io.InputStream)
 */
class Generator {
    private static final int SIZE_OF_HEADER_SIZE_FIELD = Short.BYTES;

    private final Map<Integer, TlvAdapter<? extends Tlv>> adapters = new HashMap<>();

    /**
     * Is used to convert a value to byte[] before writing it out in order to calculate
     * its length because length is written before the value.
     */
    private final ByteArrayOutputStream valueBuf = new ByteArrayOutputStream(30);
    private final DataOutputStream valueOut = new DataOutputStream(valueBuf);
    private Header header;
    private boolean enforceChecksum;
    private Optional<Integer> enforcedSize = Optional.empty();

    public void write(Header header, OutputStream outputStream) throws IOException {
        this.header = header;
        header.validate();

        // code is ugly because we need to conditionally calculate the checksum,
        // backtrack to include the size of the header and the size of the PP2_TYPE_SSL TLV

        CRC32COutputStream out = new CRC32COutputStream(outputStream, enforceChecksum);
        ByteArrayOutputStream mainBuf = new ByteArrayOutputStream(
                header.getAddressFamily().getAddressSize() * 2 + 100);

        // the fixed-size part of the header before the header size field
        writeFixedHeader(mainBuf);
        int writtenSize = mainBuf.size();
        mainBuf.writeTo(out);
        mainBuf.reset();

        // the size field should go after the fixed size part
        // generate the rest of the header in memory, so we can calculate the size

        writeAddresses(mainBuf);
        writeTlvs(mainBuf);
        maybeWriteSsl(header, mainBuf);
        writtenSize += mainBuf.size();
        maybePadHeader(writtenSize + SIZE_OF_HEADER_SIZE_FIELD + getCrcTlvSize(), mainBuf);

        if (enforceChecksum) {
            writeTlvStart(ProxyProtocolSpec.PP2_TYPE_CRC32C, getCrcValueSize(), mainBuf);
        }

        // flush the size field
        int headerSize = getHeaderSize(mainBuf);
        writeShort(headerSize, out);

        // flush mainBuf
        mainBuf.writeTo(out);

        // generate and write the checksum
        if (enforceChecksum) {
            out.writeChecksum();
        }

        // keep static code analysis happy
        out.close();
        mainBuf.close();
    }

    /**
     * Writes the fixed part of the header up to but not including the header size field.
     */
    private void writeFixedHeader(ByteArrayOutputStream out) throws IOException {
        out.write(ProxyProtocolSpec.PREFIX);
        assert out.size() == 12;

        writeCommand(out);
        assert out.size() == 13;

        writeAddressFamilyAndTransportProtocol(out);
        assert out.size() == 14;
    }

    private void writeCommand(OutputStream out) throws IOException {
        out.write(0x20 | header.getCommand().getCode());
    }

    private void writeAddresses(OutputStream out) throws IOException {
        AddressFamily af = header.getAddressFamily();
        if (af.equals(AddressFamily.AF_INET) ||
                af.equals(AddressFamily.AF_INET6)) {
            out.write(header.getSrcAddress());
            out.write(header.getDstAddress());
            writeShort(header.getSrcPort(), out);
            writeShort(header.getDstPort(), out);
        } else if (af.equals(AddressFamily.AF_UNIX)) {
            int addressSize = AddressFamily.AF_UNIX.getAddressSize();
            out.write(Arrays.copyOf(header.getSrcAddress(), addressSize));
            out.write(Arrays.copyOf(header.getDstAddress(), addressSize));
        } else {
            assert af.equals(AddressFamily.AF_UNSPEC);
        }
    }

    private void writeTlvs(OutputStream out) throws IOException {
        maybeWriteByteTVL(ProxyProtocolSpec.PP2_TYPE_ALPN, header.getAlpn(), out);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_TYPE_AUTHORITY, header.getAuthority(), out);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_TYPE_NETNS, header.getNetNS(), out);

        for (Tlv tlv : header.getTlvs()) {
            assert valueBuf.size() == 0;

            TlvAdapter<? extends Tlv> adapter = getAdapter(tlv);
            adapter.writeValue(tlv, valueOut);
            writeTLVFromValueBuf(tlv.getType(), out);
        }
    }

    private void maybeWriteSsl(Header header, OutputStream out) throws IOException {
        if (header.getSslFlags().isPresent()) {
            SslFlags flags = header.getSslFlags().get();

            ByteArrayOutputStream sslBuf = writeSslTlvs();
            // length
            int size = sslBuf.size() + Byte.BYTES + Integer.BYTES;
            writeTlvStart(ProxyProtocolSpec.PP2_TYPE_SSL, size, out);
            // client
            out.write(flags.getClient());
            // verify
            out.write(flags.isClientVerifiedCert() ? Util.INT0 : Util.INT1);

            sslBuf.writeTo(out);

            // to keep static code analysis happy
            sslBuf.close();
        }
    }

    private ByteArrayOutputStream writeSslTlvs() throws IOException {
        ByteArrayOutputStream sslBuf = new ByteArrayOutputStream(20);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_SUBTYPE_SSL_VERSION, header.getSslVersion(), sslBuf);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_SUBTYPE_SSL_CN, header.getSslCommonName(), sslBuf);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_SUBTYPE_SSL_CIPHER, header.getSslCipher(), sslBuf);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_SUBTYPE_SSL_SIG_ALG, header.getSslSigAlg(), sslBuf);
        maybeWriteStrTVL(ProxyProtocolSpec.PP2_SUBTYPE_SSL_KEY_ALG, header.getSslKeyAlg(), sslBuf);
        return sslBuf;
    }

    /**
     * Should be called last or before last checksum TLV.
     */
    void maybePadHeader(int knownSize, OutputStream out) throws IOException {
        if (!enforcedSize.isPresent()) {
            return;
        }
        int targetSize = enforcedSize.get();
        if (knownSize == targetSize) {
            // nothing to do
            return;
        }
        if (knownSize > targetSize) {
            throw new InvalidHeaderException("Header size " + knownSize
                    + " can not be larger than the specified limit " + targetSize);
        }

        int remainingSize = targetSize - knownSize;
        if (remainingSize == 1 || remainingSize == 2) {
            throw new InvalidHeaderException("Due to Proxy Protocol limitation can not pad header "
                    + "of size " + knownSize + " by 1 or 2 bytes to the specified limit "
                    + targetSize);
        } else {
            int padSize = remainingSize - getTlvStartSize();
            padHeader(padSize, out);
        }
    }

    private void padHeader(int padSize, OutputStream out) throws IOException {
        for (int i = 0; i < padSize; i++) {
            valueBuf.write(0);
        }
        writeTLVFromValueBuf(ProxyProtocolSpec.PP2_TYPE_NOOP, out);
    }

    private void maybeWriteStrTVL(int type, Optional<String> data, OutputStream out)
            throws IOException {
        if (data.isPresent()) {
            assert valueBuf.size() == 0;
            valueBuf.write(data.get().getBytes(StandardCharsets.UTF_8));
            writeTLVFromValueBuf(type, out);
        }
    }

    private void maybeWriteByteTVL(int type, Optional<byte[]> data, OutputStream out)
            throws IOException {
        if (data.isPresent()) {
            assert valueBuf.size() == 0;
            valueBuf.write(data.get());
            writeTLVFromValueBuf(type, out);
        }
    }

    private void writeTLVFromValueBuf(int type, OutputStream out) throws IOException {
        writeTlvStart(type, valueBuf.size(), out);
        valueBuf.writeTo(out);
        valueBuf.reset();
    }

    private void writeTlvStart(int type, int size, OutputStream out) throws IOException {
        out.write(type);
        writeShort(size, out);
    }

    TlvAdapter<? extends Tlv> getAdapter(Tlv tlv) {
        int type = tlv.getType();
        if (tlv instanceof TlvRaw) {
            return new TlvRawAdapter(type);
        } else if (adapters.containsKey(type)) {
            return adapters.get(type);
        } else {
            throw new IllegalStateException(
                    "Unable to find adapter for " + tlv + " of type " + toHex(type));
        }
    }

    private void writeAddressFamilyAndTransportProtocol(OutputStream out) throws IOException {
        out.write(ProxyProtocolSpec.pack(
                header.getAddressFamily(), header.getTransportProtocol()));
    }

    private int getHeaderSize(ByteArrayOutputStream mainBuf) {
        if (enforceChecksum) {
            // only CRC value is left not in mainBuf
            return mainBuf.size() + getCrcValueSize();
        } else {
            return mainBuf.size();
        }
    }

    private int getCrcValueSize() {
        return Integer.BYTES;
    }

    private int getCrcTlvSize() {
        if (enforceChecksum) {
            return getTlvStartSize() + getCrcValueSize();
        } else {
            return 0;
        }
    }

    private int getTlvStartSize() {
        return Byte.BYTES + Short.BYTES;
    }

    public Map<Integer, TlvAdapter<? extends Tlv>> getAdapters() {
        return adapters;
    }

    public void setEnforceChecksum(boolean enforceChecksum) {
        this.enforceChecksum = enforceChecksum;
    }

    public void setEnforcedSize(Optional<Integer> enforcedSize) {
        this.enforcedSize = enforcedSize;
    }
}
