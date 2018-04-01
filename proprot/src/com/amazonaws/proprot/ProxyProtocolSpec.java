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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * The constants describing the Proxy Protocol.
 * Whenever possible the names here correspond to the protocol specification.
 * See the protocol specification for more information.
 */
public final class ProxyProtocolSpec {
    public static final byte[] PREFIX = {0xD, 0xA, 0xD, 0xA, 0x0, 0xD, 0xA, 0x51, 0x55, 0x49, 0x54, 0x0A};

    // 13th byte
    public static final int PROTOCOL_V2 = 0x2;

    public interface Code {
        int getCode();
    }

    public enum Command implements Code {
        LOCAL(0x0), PROXY(0x1);

        private static Map<Integer, Command> valuesByCode = new HashMap<>();

        private final int code;

        static {
            initCodeEnum(values(), valuesByCode);
        }

        private Command(int code) {
            this.code = code;
        }

        public static Command valueOf(Integer code, long pos) {
            return valueByCode(code, valuesByCode, pos);
        }

        @Override
        public String toString() {
            return codeEnumToString(this);
        }

        @Override
        public int getCode() {
            return code;
        }
    }

    // 14th byte

    public enum AddressFamily implements Code {
        AF_UNSPEC(0x0, 0), AF_INET(0x1, 4), AF_INET6(0x2, 16), AF_UNIX(0x3, 108);

        private static Map<Integer, AddressFamily> valuesByCode = new HashMap<>();

        private final int code;
        private final int addressSize;

        static {
            initCodeEnum(values(), valuesByCode);
        }

        private AddressFamily(int code, int addressSize) {
            this.code = code;
            this.addressSize = addressSize;
        }

        public static AddressFamily valueOf(Integer code, long pos) {
            return valueByCode(code, valuesByCode, pos);
        }

        @Override
        public String toString() {
            return codeEnumToString(this);
        }

        @Override
        public int getCode() {
            return code;
        }

        /**
         * The number of bytes reserved for address of the family.
         */
        public int getAddressSize() {
            return addressSize;
        }
    }

    public enum TransportProtocol implements Code {
        UNSPEC(0x0), STREAM(0x1), DGRAM(0x2);

        private static Map<Integer, TransportProtocol> valuesByCode = new HashMap<>();

        private final int code;

        static {
            initCodeEnum(values(), valuesByCode);
        }

        private TransportProtocol(int code) {
            this.code = code;
        }

        public static TransportProtocol valueOf(Integer code, long pos) {
            return valueByCode(code, valuesByCode, pos);
        }

        @Override
        public String toString() {
            return codeEnumToString(this);
        }

        @Override
        public int getCode() {
            return code;
        }
    }

    /**
     * Valid AF/TP combinations.
     */
    public static final Set<Integer> VALID_AF_TP_VALUES = ImmutableSet.of(
            // UNSPEC
            0x00,
            // TCP over IPv4
            0x11,
            // UDP over IPv4
            0x12,
            // TCP over IPv6
            0x21,
            // UDP over IPv6
            0x22,
            // UNIX stream
            0x31,
            // UNIX datagram
            0x32);

    // 15th & 16th bytes - address length in bytes
    /**
     * The number of bytes to add to the value stored in the header size field to calculate the total size of a header.
     */
    public static final int ADD_TO_HEADER_SIZE = 16;

    // Value Types
    public static final int PP2_TYPE_ALPN = 0x1;
    public static final int PP2_TYPE_AUTHORITY = 0x2;
    public static final int PP2_TYPE_CRC32C = 0x3;
    public static final int PP2_TYPE_NOOP = 0x4;
    public static final int PP2_TYPE_SSL = 0x20;
    public static final int PP2_SUBTYPE_SSL_VERSION = 0x21;
    public static final int PP2_SUBTYPE_SSL_CN = 0x22;
    public static final int PP2_SUBTYPE_SSL_CIPHER = 0x23;
    public static final int PP2_SUBTYPE_SSL_SIG_ALG = 0x24;
    public static final int PP2_SUBTYPE_SSL_KEY_ALG = 0x25;
    public static final int PP2_TYPE_NETNS = 0x30;

    public static final int PP2_CLIENT_SSL = 0x01;
    public static final int PP2_CLIENT_CERT_CONN = 0x02;
    public static final int PP2_CLIENT_CERT_SESS = 0x04;

    public static final Set<Integer> STANDARD_TLV_TYPES = ImmutableSet.of(
            ProxyProtocolSpec.PP2_TYPE_ALPN,
            ProxyProtocolSpec.PP2_TYPE_AUTHORITY,
            ProxyProtocolSpec.PP2_TYPE_CRC32C,
            ProxyProtocolSpec.PP2_TYPE_NOOP,
            ProxyProtocolSpec.PP2_TYPE_SSL,
            ProxyProtocolSpec.PP2_SUBTYPE_SSL_VERSION,
            ProxyProtocolSpec.PP2_SUBTYPE_SSL_CN,
            ProxyProtocolSpec.PP2_SUBTYPE_SSL_CIPHER,
            ProxyProtocolSpec.PP2_SUBTYPE_SSL_SIG_ALG,
            ProxyProtocolSpec.PP2_SUBTYPE_SSL_KEY_ALG,
            ProxyProtocolSpec.PP2_TYPE_NETNS);


    /**
     * Packs address family and transport protocol into a single byte as described by the spec.
     */
    public static int pack(AddressFamily addressFamily, TransportProtocol transportProtocol) {
        return (addressFamily.getCode() << 4) | transportProtocol.getCode();
    }


    private static <E extends Enum<?> & Code> E valueByCode(
            Integer code, Map<Integer, E> valuesByCode, long pos) {
        E value = valuesByCode.get(code);
        if (value == null) {
            Class<?> enumClass = getEnumClass(valuesByCode);
            throw new InvalidHeaderException("Unexpected code " + toHex(code) + " for "
                    + enumClass.getSimpleName()
                    + " at byte " + pos);
        } else {
            return value;
        }
    }

    /**
     * Should be called by the {@link Code} enumerations.
     * @param valuesByCode is populated by the method.
     */
    static <E extends Enum<?> & Code> void initCodeEnum(
            E[] values, Map<Integer, E> valuesByCode) {
        assert valuesByCode.isEmpty();
        for (E value : values) {
            int code = value.getCode();
            E oldValue = valuesByCode.put(code, value);
            if (oldValue != null) {
                Class<?> enumClass = getEnumClass(valuesByCode);
                throw new InvalidHeaderException(enumClass.getSimpleName()
                        + " has multiple constants with the same code " + toHex(code) + " - "
                        + value.toString() + " and " + oldValue.toString());
            }
        }
    }

    private static <E extends Enum<?> & Code> String codeEnumToString(E value) {
        return value.name() + "(" + value.getCode() + ")";
    }

    /**
     * Class of the map values.
     */
    private static <E extends Enum<?> & Code> Class<?> getEnumClass(Map<Integer, E> valuesByCode) {
        return valuesByCode.values().iterator().next().getDeclaringClass();
    }
}
