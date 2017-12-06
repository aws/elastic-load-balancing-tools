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
import java.util.Arrays;

public class TlvSubTypeRawAdapter implements TlvAdapter<TlvSubTypeRaw> {
    private final int type;

    public TlvSubTypeRawAdapter(int type) {
        this.type = type;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public TlvSubTypeRaw read(InputAssist inputAssist, int length) throws IOException {
        if (length < 1) {
            throw new InvalidHeaderException("Unexpected length " + length + " of the TLV " + type + ".  Should be at least 1.");
        }
        TlvSubTypeRaw tlv = new TlvSubTypeRaw();
        tlv.setType(type);
        tlv.setSubType(inputAssist.readByte());
        tlv.setValue(inputAssist.readBytes(length - 1, "TLV " + type));
        return tlv;
    }

    @Override
    public void writeValue(Tlv tlv, DataOutputStream out) throws IOException {
        TlvSubTypeRaw subTypeTlv = (TlvSubTypeRaw) tlv;
        out.writeByte(subTypeTlv.getSubType());
        out.write(subTypeTlv.getValue());
    }
}
