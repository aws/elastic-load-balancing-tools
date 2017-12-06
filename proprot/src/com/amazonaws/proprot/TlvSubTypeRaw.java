/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/

package com.amazonaws.proprot;

/**
 * TlvSubTypeRaw implements a pattern of extending Proxy Protocol with a TLV having a byte-sized subtype.
 */
public class TlvSubTypeRaw implements Tlv {
    private int type;
    private int subType;
    private byte[] value;

    @Override
    public int getType() {
        return type;
    }

    public int getSubType() {
        return subType;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setSubType(int subType) {
        this.subType = subType;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;

    }
}
