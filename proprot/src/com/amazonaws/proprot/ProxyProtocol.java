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
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 * Marshalls and unmarshalls the Proxy Protocol header.
 * The facade of the library. Is thread-safe once configured.
 */
public class ProxyProtocol {
    private boolean readUnknownTLVs = true;
    private boolean enforceChecksum = true;
    private Optional<Integer> enforcedSize = Optional.empty();

    private final Map<Integer, TlvAdapter<?>> adapters = new HashMap<>();

    /**
     * Reads the protocol header.
     * On successful completion the input stream is positioned on the first byte after
     * the Proxy Protocol header data. That's the first byte of the original connection.
     */
    public Header read(InputStream in) throws IOException, InvalidHeaderException {
        Parser parser = new Parser();
        parser.setReadUnknownTLVs(isReadUnknownTLVs());
        parser.setEnforceChecksum(isEnforceChecksum());
        parser.getAdapters().putAll(getAdapters());
        return parser.read(in);
    }

    /**
     * Writes the protocol header.
     */
    public void write(Header header, OutputStream out) throws IOException, InvalidHeaderException {
        Generator generator = new Generator();
        generator.setEnforceChecksum(isEnforceChecksum());
        generator.setEnforcedSize(getEnforcedSize());
        generator.getAdapters().putAll(getAdapters());
        generator.write(header, out);
    }

    /**
     * <p>Indicates whether {@link #read(InputStream)} returns the TLVs this object does not have
     * adapters for. If <code>true</code> the unrecognized TLVs are returned as {@link TlvRaw}s.
     * </p>
     * <p>By default returns <code>true</code>.</p>
     */
    public boolean isReadUnknownTLVs() {
        return readUnknownTLVs;
    }

    /**
     * @see #isReadUnknownTLVs()
     */
    public void setReadUnknownTLVs(boolean readUnknownTLVs) {
        this.readUnknownTLVs = readUnknownTLVs;
    }

    /**
     * <p>If <code>true</code>, enforce the checksum when reading and writing the protocol header.
     * When reading, calculate the checksum and throw {@link InvalidHeaderException} if
     * the checksum does not match the checksum specified in the header or the header does not
     * include the checksum. Generate the checksum when writing.</p>
     *
     * <p>If <code>false</code>, the checksum is ignored when reading and is not generated when
     * writing.
     * </p>
     *
     * <p>The checksum is not exposed as a TLV object even though according to the protocol
     * specification it is stored as a TLV.
     * </p>
     *
     * <p>By default returns <code>true</code>.</p>
     *
     * <p>Checksum processing approximately doubles processing time of a header.</p>
     *
     */
    public boolean isEnforceChecksum() {
        return enforceChecksum;
    }

    public void setEnforceChecksum(boolean enforceChecksum) {
        this.enforceChecksum = enforceChecksum;
    }

    /**
     * If specified, the class generates Proxy Protocol headers of the specified size if necessary
     * padding the header with the {@link ProxyProtocolSpec#PP2_TYPE_NOOP} TLV.
     * {@link #write(Header, OutputStream)} throws {@link InvalidHeaderException} if the size of
     * the generated header is greater than the specified size. Due to the restriction of the Proxy
     * Protocol the exception is also thrown when the generated header is smaller than the
     * specified size by 1 or 2 bytes.
     * @return by default returns {@link Optional#empty()}
     */
    public Optional<Integer> getEnforcedSize() {
        return enforcedSize;
    }

    /**
     * @see #getEnforcedSize()
     */
    public void setEnforcedSize(Optional<Integer> enforcedSize) {
        this.enforcedSize = enforcedSize;
    }

    /**
     * A map of adapter type to adapter.
     */
    public Map<Integer, TlvAdapter<?>> getAdapters() {
        validateAdapters();
        return adapters;
    }

    public void setAdapters(Collection<TlvAdapter<?>> adapterColl) {
        Preconditions.checkNotNull(adapters);
        adapters.clear();
        for (TlvAdapter<?> adapter : adapterColl) {
            TlvAdapter<?> oldAdapter = adapters.put(adapter.getType(), adapter);
            if (oldAdapter != null) {
                throw new IllegalArgumentException("Found two adapters for the same TLV type "
                        + toHex(adapter.getType()) + ": " + oldAdapter + " and " + adapter);
            }
        }
        validateAdapters();
    }

    private void validateAdapters() {
        for (Integer adapterType: adapters.keySet()) {
            if (ProxyProtocolSpec.STANDARD_TLV_TYPES.contains(adapterType)) {
                throw new IllegalArgumentException(
                        "The TLV type " + toHex(adapterType) + " is handled by the library "
                        + "and can not be processed by the external adapter " + adapters.get(adapterType));
            }
        }
    }
}
