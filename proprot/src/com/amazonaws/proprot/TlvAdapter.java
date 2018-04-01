/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Writes and reads a TLV. The implementations must be thread-safe.
 */
public interface TlvAdapter<T extends Tlv> {
    /**
     * Returns the TLV type handled by the adapter.
     */
    int getType();

    /**
     * The implementors don't need to validate whether all the available data was read.
     * The caller will throw an exception when the number of bytes read from the input
     * stream does not equal to the "length" parameter.
     *
     * @param inputAssist contains the input data stream to read the TLV value from and
     * the parsing tools.
     * @param length the number of bytes belonging to the TLV in the provided input stream.
     */
    T read(InputAssist inputAssist, int length) throws IOException, InvalidHeaderException;

    /**
     * Serializes the value of the provided TLV to the output stream.
     */
    void writeValue(Tlv tlv, DataOutputStream out) throws IOException, InvalidHeaderException;
}
