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
import java.io.OutputStream;


class Util {
    public final static byte[] INT0 = new byte[] {0, 0, 0, 0};
    public final static byte[] INT1 = new byte[] {0, 0, 0, 1};

    public static String toHex(int b) {
        return "0x" + Integer.toHexString(b);
    }


    /**
     * When we do not want to create {@link DataOutputStream} to write out the data.
     */
    public static void writeShort(int v, OutputStream out) throws IOException {
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
    }
}
