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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.amazonaws.proprot.ProxyProtocolSpec.AddressFamily;
import com.amazonaws.proprot.ProxyProtocolSpec.Command;
import com.amazonaws.proprot.ProxyProtocolSpec.TransportProtocol;
import com.google.common.io.BaseEncoding;

/**
 * The Proxy Protocol header.
 */
public class Header {
    private final static byte[] BYTE_A_0 = new byte[0];

    private Command command = Command.LOCAL;
    private AddressFamily addressFamily = AddressFamily.AF_UNSPEC;
    private TransportProtocol transportProtocol = TransportProtocol.UNSPEC;

    private byte[] srcAddress = BYTE_A_0;
    private byte[] dstAddress = BYTE_A_0;
    private int srcPort;
    private int dstPort;

    // data of the predefined TLVs
    private Optional<byte[]> alpn = Optional.empty();
    private Optional<String> authority = Optional.empty();
    private Optional<SslFlags> sslFlags = Optional.empty();
    private Optional<String> sslVersion = Optional.empty();
    private Optional<String> sslCommonName = Optional.empty();
    private Optional<String> sslCipher = Optional.empty();
    private Optional<String> sslSigAlg = Optional.empty();
    private Optional<String> sslKeyAlg = Optional.empty();
    private Optional<String> netNS = Optional.empty();

    private List<Tlv> tlvs = Collections.emptyList();

    /**
     * Checks the header consistency.
     * This method is called after parsing header and before writing the header information to the output stream.
     * @throws InvalidHeaderException if inconsistencies are found.
     */
    public void validate() throws InvalidHeaderException {
        verifyAddressFamilyTransportProtocolCombination();
        verifyAddressSize(srcAddress);
        verifyAddressSize(dstAddress);
        if (!getSslFlags().isPresent()) {
            assertNoSslContainedTlv(sslVersion, "SSL version");
            assertNoSslContainedTlv(sslCommonName, "SSL Common Name (CN)");
            assertNoSslContainedTlv(sslCipher, "SSL Cipher");
            assertNoSslContainedTlv(sslSigAlg, "SSL Certificate Signature Algorithm");
            assertNoSslContainedTlv(sslKeyAlg, "SSL Certificate Key Algorithm");
        }
    }

    private void verifyAddressFamilyTransportProtocolCombination() {
        int value = ProxyProtocolSpec.pack(addressFamily, transportProtocol);
        if (!ProxyProtocolSpec.VALID_AF_TP_VALUES.contains(value)) {
            throw new InvalidHeaderException("Unexpected address family/transport protocol value "
                    + toHex(value) + " (" + addressFamily + "/" + transportProtocol + ")");
        }
    }

    // CHECKSTYLE:OFF
    private void verifyAddressSize(byte[] address) {
    // CHECKSTYLE:ON
        int addressLength = address.length;
        switch (addressFamily) {
        case AF_UNSPEC:
        case AF_INET:
        case AF_INET6:
            if (addressLength != addressFamily.getAddressSize()) {
                throw new InvalidHeaderException("For the address family " + addressFamily +
                        " expected address size " + addressFamily.getAddressSize() + " but got "
                        + addressLength + " for the address "
                        + BaseEncoding.base16().lowerCase().encode(address));
            }
            break;
        case AF_UNIX:
            // Unix address can be smaller than the reserved space
            if (addressLength > addressFamily.getAddressSize()
                || addressLength == 0) {
                throw new InvalidHeaderException("Invalid size " + addressLength +
                        " of the Unix address "
                        + BaseEncoding.base16().lowerCase().encode(address));
            }
            break;
        }
    }

    private void assertNoSslContainedTlv(Optional<?> value, String label) {
        if (value.isPresent()) {
            throw new InvalidHeaderException(
                    label + " TLV must be part of the container SSL TLV");
        }
    }

    /**
     * @return Never <code>null</code>. By default {@link Command#PROXY}.
     */
    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public AddressFamily getAddressFamily() {
        return addressFamily;
    }

    public void setAddressFamily(AddressFamily addressFamily) {
        this.addressFamily = addressFamily;
    }

    public TransportProtocol getTransportProtocol() {
        return transportProtocol;
    }

    public void setTransportProtocol(TransportProtocol transportProtocol) {
        this.transportProtocol = transportProtocol;
    }

    /**
     * Source address represented as a byte array. Default value is
     * <code>byte[0]</code>The actual format depends on the value of the
     * {@link #getAddressFamily()} field. Address is not specified and is equal to the default
     * value for {@link AddressFamily#AF_UNSPEC}.
     * For {@link AddressFamily#AF_INET} and {@link AddressFamily#AF_INET6} addresses you can
     * use {@link InetAddress#getByAddress(byte[])}.
     * {@link AddressFamily#AF_UNIX} address is a sequence of bytes with the trailing zero bytes
     * truncated. Willy Tarreau, the author of the Proxy Protocol v2:
     * <blockquote>
     * UNIX addresses should be seen as byte streams, they are usually
     * 108 bytes and stop at the first zero <i>if there is one</i>.
     * On Linux we also support abstract namespace sockets which basically are
     * Unix socket addresses starting with a zero, and the remaining 107 bytes
     * become an internal address. This way there's no need for FS access. And
     * thanks to this they're also supported by default without doing anything.
     * </blockquote>
     *
     */
    public byte[] getSrcAddress() {
        return srcAddress;
    }

    public void setSrcAddress(byte[] srcAddress) {
        this.srcAddress = srcAddress;
    }

    /**
     * @see #getSrcAddress()
     */
    public byte[] getDstAddress() {
        return dstAddress;
    }

    public void setDstAddress(byte[] dstAddress) {
        this.dstAddress = dstAddress;
    }

    /**
     * Is specified when {@link #getTransportProtocol()} is {@link TransportProtocol#STREAM}
     * or {@link TransportProtocol#DGRAM}.
     * Default value is zero.
     */
    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int srcPort) {
        this.srcPort = srcPort;
    }
    
    /**
     * Is specified when {@link #getTransportProtocol()} is {@link TransportProtocol#STREAM}
     * or {@link TransportProtocol#DGRAM}.
     * Default value is zero.
     */
    public int getDstPort() {
        return dstPort;
    }

    public void setDstPort(int dstPort) {
        this.dstPort = dstPort;
    }

    /**
     * When {@link #getCommand()} is {@link Command#LOCAL}, the specification requires parser to process
     * address information and then ignore it. In that case, reset {@link #getSrcAddress()}, {@link #getDstAddress()}, 
     * {@link #getSrcPort()}, {@link #getDstPort()}, {@link #getAddressFamily()}, and {@link #getTransportProtocol()}.
     * This method is called after parsing header for address information.
     */
    void correctAddressesForLocalCommand() {
        if (command.equals(Command.LOCAL)) {
            this.setSrcAddress(BYTE_A_0);
            this.setDstAddress(BYTE_A_0);
            this.setSrcPort(0);
            this.setDstPort(0);
            
            this.setAddressFamily(AddressFamily.AF_UNSPEC);
            this.setTransportProtocol(TransportProtocol.UNSPEC);
        }
    }
    
    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_TYPE_ALPN} TLV.
     */
    public Optional<byte[]> getAlpn() {
        return alpn;
    }

    public void setAlpn(Optional<byte[]> alpn) {
        this.alpn = alpn;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_TYPE_AUTHORITY} TLV.
     */
    public Optional<String> getAuthority() {
        return authority;
    }

    public void setAuthority(Optional<String> authority) {
        this.authority = authority;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_TYPE_SSL} TLV without the nested TLVs.
     */
    public Optional<SslFlags> getSslFlags() {
        return sslFlags;
    }

    public void setSslFlags(Optional<SslFlags> sslFlags) {
        this.sslFlags = sslFlags;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_SUBTYPE_SSL_VERSION} TLV.
     */
    public Optional<String> getSslVersion() {
        return sslVersion;
    }

    public void setSslVersion(Optional<String> sslVersion) {
        this.sslVersion = sslVersion;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_SUBTYPE_SSL_CN} TLV.
     */
    public Optional<String> getSslCommonName() {
        return sslCommonName;
    }

    public void setSslCommonName(Optional<String> sslCommonName) {
        this.sslCommonName = sslCommonName;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_SUBTYPE_SSL_CIPHER} TLV.
     */
    public Optional<String> getSslCipher() {
        return sslCipher;
    }

    public void setSslCipher(Optional<String> sslCipher) {
        this.sslCipher = sslCipher;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_SUBTYPE_SSL_SIG_ALG} TLV.
     */
    public Optional<String> getSslSigAlg() {
        return sslSigAlg;
    }

    public void setSslSigAlg(Optional<String> sslSigAlg) {
        this.sslSigAlg = sslSigAlg;
    }


    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_SUBTYPE_SSL_KEY_ALG} TLV.
     */
    public Optional<String> getSslKeyAlg() {
        return sslKeyAlg;
    }

    public void setSslKeyAlg(Optional<String> sslKeyAlg) {
        this.sslKeyAlg = sslKeyAlg;
    }

    /**
     * The contents of the {@link ProxyProtocolSpec#PP2_TYPE_NETNS} TLV.
     */
    public Optional<String> getNetNS() {
        return netNS;
    }

    public void setNetNS(Optional<String> netNS) {
        this.netNS = netNS;
    }

    /**
     * The TLVs of the types not known to the library.
     * Immutable.
     */
    public List<Tlv> getTlvs() {
        return tlvs;
    }

    public void addTlv(Tlv tlv) {
        int tlvType = tlv.getType();
        if (ProxyProtocolSpec.STANDARD_TLV_TYPES.contains(tlvType)) {
            throw new InvalidHeaderException(
                    "The TLV type " + toHex(tlvType) + " defined in the Proxy Protocol "
                    + "specification should not be exposed as a TLV. "
                    + "Use Header class fields instead");
        }

        if (tlvs.isEmpty()) {
            // assume the number of the custom TLVs is small
            tlvs = new ArrayList<>(4);
        }
        tlvs.add(tlv);
    }

    /**
     * Implements the Flightweight pattern.
     */
    public static class SslFlags {
        /**
         * The number of bits used in the flag field.
         */
        private static final int BIT_COUNT = 3;

        private static final int BIT_MASK = (1 << BIT_COUNT) - 1;

        private static final int VERIFIED_FLAG = 1 << BIT_COUNT;

        /**
         * The count of all the flags tracked by the objects of this class.
         */
        private static final int FLAG_COUNT = BIT_COUNT + 1;

        private final boolean clientConnectedWithSsl;
        private final boolean clientProvidedCertDuringConnection;
        private final boolean clientProvidedCertDuringSession;
        private final boolean clientVerifiedCert;
        private final int client;

        private SslFlags(boolean clientConnectedWithSsl, boolean clientProvidedCertDuringConnection,
                boolean clientProvidedCertDuringSession, boolean clientVerifiedCert) {
            this.clientConnectedWithSsl = clientConnectedWithSsl;
            this.clientProvidedCertDuringConnection = clientProvidedCertDuringConnection;
            this.clientProvidedCertDuringSession = clientProvidedCertDuringSession;
            this.client = calculateClient(clientConnectedWithSsl,
                    clientProvidedCertDuringConnection, clientProvidedCertDuringSession);
            this.clientVerifiedCert = clientVerifiedCert;
        }

        /**
         * The value for the "client" field.
         */
        public int getClient() {
            return client;
        }

        public boolean isClientConnectedWithSsl() {
            return clientConnectedWithSsl;
        }

        public boolean isClientProvidedCertDuringConnection() {
            return clientProvidedCertDuringConnection;
        }

        public boolean isClientProvidedCertDuringSession() {
            return clientProvidedCertDuringSession;
        }

        public boolean isClientVerifiedCert() {
            return clientVerifiedCert;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o instanceof SslFlags) {
                SslFlags other = (SslFlags) o;
                return client == other.client
                        && clientVerifiedCert == other.clientVerifiedCert;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return getValueIdx(client, clientVerifiedCert);
        }

        public static Optional<SslFlags> getOptional(int client, boolean clientVerifiedCert) {
            return Container.VALUES[getValueIdx(client, clientVerifiedCert)];
        }

        public static Optional<SslFlags> getOptional(boolean clientConnectedWithSsl,
                boolean clientProvidedCertDuringConnection, boolean clientProvidedCertDuringSession,
                boolean clientVerifiedCert) {
            int client = calculateClient(clientConnectedWithSsl, clientProvidedCertDuringConnection,
                    clientProvidedCertDuringSession);
            return getOptional(client, clientVerifiedCert);
        }

        private static int getValueIdx(int client, boolean clientVerifiedCert) {
            return (client & BIT_MASK) | (clientVerifiedCert ? VERIFIED_FLAG : 0);
        }

        private static int calculateClient(boolean clientConnectedWithSsl,
                boolean clientProvidedCertDuringConnection, boolean clientProvidedCertDuringSession) {
            int value = 0;
            if (clientConnectedWithSsl) {
                value |= ProxyProtocolSpec.PP2_CLIENT_SSL;
            }
            if (clientProvidedCertDuringConnection) {
                value |= ProxyProtocolSpec.PP2_CLIENT_CERT_CONN;
            }
            if (clientProvidedCertDuringSession) {
                value |= ProxyProtocolSpec.PP2_CLIENT_CERT_SESS;
            }
            return value;
        }

        /**
         * Lazy load, otherwise SslFlags creation causes infinite loop.
         */
        private static class Container {
            /**
             * All the class values, is used to optimize memory allocation when parsing.
             */
            @SuppressWarnings("unchecked")
            private static final Optional<SslFlags>[] VALUES = new Optional[1 << FLAG_COUNT];
            static {
                final boolean[] bools = new boolean[] {false, true};
                int i = 0;
                for (boolean f1 : bools) {
                    for (boolean f2 : bools) {
                        for (boolean f3 : bools) {
                            for (boolean f4 : bools) {
                                VALUES[i] = Optional.of(new SslFlags(f1, f2, f3, f4));
                                i++;
                            }
                        }
                    }
                }

                assert i == VALUES.length;
                SslFlags lastValue = VALUES[VALUES.length - 1].get();
                assert lastValue.isClientConnectedWithSsl();
                assert lastValue.isClientProvidedCertDuringConnection();
                assert lastValue.isClientProvidedCertDuringSession();
                assert lastValue.isClientVerifiedCert();
                assert lastValue == getOptional(0xFF, true).get();
            }
        }
    }
}
