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

import org.junit.Test;

import com.google.common.hash.Hasher;

public class NullHasherTest {
    @Test
    public void testBasics() {
        Hasher hasher = NullHasher.INSTANCE;
        assertEquals(0, hasher.hash().asInt());
        hasher.putBoolean(false);
        hasher.putByte((byte) 3);
        hasher.putBytes(new byte[0]);
        hasher.putBytes(null, 3, 3);
        hasher.putChar('c');
        hasher.putDouble(3.3);
        hasher.putFloat(3.4f);
        hasher.putInt(7);
        hasher.putLong(3);
        hasher.putObject(null, null);
        hasher.putShort((short) 7);
        hasher.putString(null, null);
        hasher.putUnencodedChars(null);
    }
}
