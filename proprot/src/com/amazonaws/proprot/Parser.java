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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.amazonaws.proprot.Header.SslFlags;
import com.amazonaws.proprot.ProxyProtocolSpec.AddressFamily;
import com.amazonaws.proprot.ProxyProtocolSpec.Command;
import com.amazonaws.proprot.ProxyProtocolSpec.TransportProtocol;

/**
 * Parses Proxy Protocol v2.
 * Is not thread-safe. Reusable.
 * @see ProxyProtocol#read(java.io.InputStream)
 */
class Parser {
    private final Map<Integer, TlvAdapter<?>> adapters = new HashMap<>();
    private boolean readUnknownTLVs = true;
    private InputAssist assist;
    private boolean enforceChecksum = true;
    private boolean foundChecksum;
    private int expectedChecksum;

    public Header read(InputStream inputStream) throws IOException, InvalidHeaderException {
        init(inputStream);

        readPrefix();
        assert assist.getReadCount() == 12;

        Header header = new Header();
        header.setCommand(readCommand());
        assert assist.getReadCount() == 13;

        readAddressFamilyAndTransportProtocol(header);
        assert assist.getReadCount() == 14;
        
        assist.readHeaderSize();
        assert assist.getReadCount() == ProxyProtocolSpec.ADD_TO_HEADER_SIZE;

        readAddresses(header);
        
        header.correctAddressesForLocalCommand();
        
        // the remainder of the header are TLVs
        readTLVs(header);
        maybeValidateChecksum();

        header.validate();
        return header;
    }

    /**
     * Initializes resources for parsing.
     * Is exposed to be called by tests, so the tests can call low-level methods.
     */
    void init(InputStream inputStream) {
        foundChecksum = false;

        assist = new InputAssist(inputStream, enforceChecksum);
    }

    void readPrefix() throws IOException {
        int length = ProxyProtocolSpec.PREFIX.length;
        byte[] buf = assist.readBytes(length, "Proxy Protocol prefix");
        if (!Arrays.equals(buf, ProxyProtocolSpec.PREFIX)) {
            throw new InvalidHeaderException(
                    "Invalid Proxy Protocol prefix " + toString(buf) + ". "
                    + "Expected " + toString(ProxyProtocolSpec.PREFIX));
        }
    }

    Command readCommand() throws IOException {
        int versionAndCommand = assist.readByte();

        // higher 4 bits - version
        int version = (versionAndCommand >>> 4) & 0xF;
        validateProtocolVersion(version);

        // lower 4 bits - command
        int commandCode = versionAndCommand & 0xF;
        return Command.valueOf(commandCode, assist.getReadPos());
    }

    private void validateProtocolVersion(int version) {
        if (version != ProxyProtocolSpec.PROTOCOL_V2) {
            throw new InvalidHeaderException(
                    "Expected version value 0x2 but got " + toHex(version)
                    + " at the byte " + assist.getReadPos());
        }
    }

    void readAddressFamilyAndTransportProtocol(Header header) throws IOException {
        int transportProtocolAddAddressFamily = assist.readByte();
        header.setAddressFamily(getAddressFamily(transportProtocolAddAddressFamily));
        header.setTransportProtocol(getTransportProtocol(transportProtocolAddAddressFamily));
    }

    AddressFamily getAddressFamily(
            int transportProtocolAddAddressFamily) {
        int code = (transportProtocolAddAddressFamily >>> 4) & 0xF;
        return AddressFamily.valueOf(code, assist.getReadPos());
    }

    TransportProtocol getTransportProtocol(
            int transportProtocolAddAddressFamily) {
        int code = transportProtocolAddAddressFamily & 0xF;
        return TransportProtocol.valueOf(code, assist.getReadPos());
    }

    void readAddresses(Header header) throws IOException {
        final AddressFamily af = header.getAddressFamily();
        if (af.equals(AddressFamily.AF_INET) ||
                af.equals(AddressFamily.AF_INET6)) {

            header.setSrcAddress(
                    readAddress(header.getAddressFamily(), "src"));
            header.setDstAddress(
                    readAddress(header.getAddressFamily(), "dst"));
            header.setSrcPort(assist.readShort());
            header.setDstPort(assist.readShort());
        } else if (af.equals(AddressFamily.AF_UNIX)) {
            header.setSrcAddress(readUnixAddress("src unix"));
            header.setDstAddress(readUnixAddress("dst unix"));
        } else {
            assert af.equals(AddressFamily.AF_UNSPEC);
            // do nothing
        }
    }
    
    /**
     * Note that some TLVs are not returned as TLV objects.
     * Instead the data is stored in the {@link Header} fields,
     * or is used without storing (e.g. checksum).
     */
    void readTLVs(Header header) throws IOException {
        while (assist.getReadCount() < assist.getHeaderSize()) {
            readTlv(header);
        }
    }

    private void readTlv(Header header) throws IOException {
        int type = assist.readByte();
        int length = assist.readShort();
        if (ProxyProtocolSpec.STANDARD_TLV_TYPES.contains(type)) {
            readTlvValueToHeader(header, type, length);
        } else if (adapters.containsKey(type)) {
            TlvAdapter<?> adapter = getAdapter(type);
            Tlv result = readTlvValue(adapter, length);
            header.addTlv(result);
        } else {
            Tlv result = readTlvValue(new TlvRawAdapter(type), length);
            if (readUnknownTLVs) {
                header.addTlv(result);
            } else {
                // discard the read TLV
            }
        }
    }

    private void readTlvValueToHeader(Header header, int type, int length) throws IOException {
        // The SSL TLV contains other TLVs, we don't care about this and "flatten" the SSL TLV
        // structure by reading the nested TLVs as the top-level TLVs.
        // This will prevent the read length validation check from failing.
        int correctedLength = type == ProxyProtocolSpec.PP2_TYPE_SSL
                ? Byte.BYTES + Integer.BYTES
                : length;

        long countBeforeRead = assist.getReadCount();
        doReadTlvValueToHeader(header, type, correctedLength);
        checkReadExpected(correctedLength, countBeforeRead, type);
    }

    // CHECKSTYLE:OFF
    private void doReadTlvValueToHeader(Header header, int type, int length) throws IOException {
    // CHECKSTYLE:ON
        // optimization using switch statement
        // it took a lot of time to initialize the map from types to the reading lambdas
        switch (type) {
        case ProxyProtocolSpec.PP2_TYPE_ALPN:
            readAlpn(header, length);
            break;
        case ProxyProtocolSpec.PP2_TYPE_AUTHORITY:
            readAuthority(header, length);
            break;
        case ProxyProtocolSpec.PP2_TYPE_CRC32C:
            readCRC32c(length);
            break;
        case ProxyProtocolSpec.PP2_TYPE_NOOP:
            assist.readBytes(length, "noop");
            break;
        case ProxyProtocolSpec.PP2_TYPE_SSL:
            readSslFlags(header, length);
            break;
        case ProxyProtocolSpec.PP2_SUBTYPE_SSL_VERSION:
            readSslVersion(header, length);
            break;
        case ProxyProtocolSpec.PP2_SUBTYPE_SSL_CN:
            readSslCommonName(header, length);
            break;
        case ProxyProtocolSpec.PP2_SUBTYPE_SSL_CIPHER:
            readSslCipher(header, length);
            break;
        case ProxyProtocolSpec.PP2_SUBTYPE_SSL_SIG_ALG:
            readSslSigAlg(header, length);
            break;
        case ProxyProtocolSpec.PP2_SUBTYPE_SSL_KEY_ALG:
            readSslKeyAlg(header, length);
            break;
        case ProxyProtocolSpec.PP2_TYPE_NETNS:
            readNetNS(header, length);
            break;
        default:
            throw new IllegalStateException("Unrecognized TLV type " + toHex(type));
        }
    }

    Tlv readTlvValue(TlvAdapter<?> adapter, int length) throws IOException {
        long countBeforeRead = assist.getReadCount();
        Tlv result = adapter.read(assist, length);
        checkReadExpected(length, countBeforeRead, adapter.getType());
        return result;
    }

    private void checkReadExpected(int lengthToRead, long countBeforeRead, int type) {
        long readCount = assist.getReadCount() - countBeforeRead;
        if (readCount != lengthToRead) {
            throw new IllegalStateException(
                    "TLV read for the type " + toHex(type)
                    + " was expected to read " + lengthToRead + " bytes but read " + readCount);
        }
    }

    void maybeValidateChecksum() {
        if (enforceChecksum) {
            if (foundChecksum) {
                if (expectedChecksum != assist.getChecksum()) {
                    throw new InvalidHeaderException(
                            "Invalid checksum. Parsed " + toHex(expectedChecksum)
                            + " but calculated " + toHex(assist.getChecksum()));
                }
            } else {
                throw new InvalidHeaderException("Checksum was not found in the header");
            }
        }
    }

    private byte[] readAddress(AddressFamily addressFamily, String label) throws IOException {
        return assist.readBytes(addressFamily.getAddressSize(), label);
    }

    byte[] readUnixAddress(String label) throws IOException {
        byte[] b = assist.readBytes(AddressFamily.AF_UNIX.getAddressSize(), label);
        int lastIdx = b.length - 1;
        while (lastIdx >= 0 && b[lastIdx] == 0) {
            lastIdx--;
        }
        return Arrays.copyOf(b, lastIdx + 1);
    }

    void readAlpn(Header header, int length) throws IOException {
        header.setAlpn(Optional.of(assist.readBytes(length, "alpn")));
    }

    void readAuthority(Header header, int length) throws IOException {
        header.setAuthority(Optional.of(assist.readString(length, "authority")));
    }

    void readCRC32c(int length) throws IOException {
        assert length == 4;
        foundChecksum = true;
        expectedChecksum = assist.readChecksum();
    }

    void readSslFlags(Header header, int length) throws IOException {
        assert length == Byte.BYTES + Integer.BYTES;
        int client = assist.readByte();
        int clientVerifiedCert = assist.readInt();
        header.setSslFlags(SslFlags.getOptional(client, clientVerifiedCert == 0));
    }

    void readSslVersion(Header header, int length) throws IOException {
        header.setSslVersion(Optional.of(assist.readString(length, "ssl version")));
    }

    void readSslCommonName(Header header, int length) throws IOException {
        header.setSslCommonName(Optional.of(assist.readString(length, "ssl common name")));
    }

    void readSslCipher(Header header, int length) throws IOException {
        header.setSslCipher(Optional.of(assist.readString(length, "ssl cipher")));
    }

    void readSslSigAlg(Header header, int length) throws IOException {
        header.setSslSigAlg(Optional.of(assist.readString(length, "signature algorithm")));
    }

    void readSslKeyAlg(Header header, int length) throws IOException {
        header.setSslKeyAlg(Optional.of(assist.readString(length, "key algorithm")));
    }

    void readNetNS(Header header, int length) throws IOException {
        header.setNetNS(Optional.of(assist.readString(length, "net NS")));
    }

    /**
     * Returns a string representation of the array printing numbers in hexadecimal.
     */
    String toString(byte[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');

        for (int i = 0; i < a.length; i++) {
            sb.append(toHex(Byte.toUnsignedInt(a[i])));
            if (i < a.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public Map<Integer, TlvAdapter<?>> getAdapters() {
        return adapters;
    }

    public void setAdapters(Map<Integer, TlvAdapter<?>> adapters) {
        this.adapters.clear();
        this.adapters.putAll(adapters);
    }

    private TlvAdapter<?> getAdapter(int type) {
        TlvAdapter<?> adapter = adapters.get(type);
        if (type != adapter.getType()) {
            throw new IllegalStateException("Adapter " + adapter + " has TLV type "
                    + toHex(adapter.getType()) + " however it is registered "
                    + "to handle type " + toHex(type));
        }
        return adapter;
    }

    public void setReadUnknownTLVs(boolean readUnknownTLVs) {
        this.readUnknownTLVs = readUnknownTLVs;
    }

    public void setEnforceChecksum(boolean enforceChecksum) {
        this.enforceChecksum = enforceChecksum;
    }

    InputAssist getAssist() {
        return assist;
    }
}
