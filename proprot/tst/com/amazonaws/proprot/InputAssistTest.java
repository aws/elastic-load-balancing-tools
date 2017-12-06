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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class InputAssistTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testValidateHeaderSizeBeforeRead_happyPath() throws IOException {
        int headerSize = 5;
        InputAssist assist = newAssist(new byte[] {0, (byte) headerSize});
        assist.readHeaderSize();
        int read = 2;
        assist.validateHeaderSizeBeforeRead(
                ProxyProtocolSpec.ADD_TO_HEADER_SIZE - read + headerSize);
    }

    @Test
    public void testValidateHeaderSizeBeforeRead_overHeaderSize() throws IOException {
        int headerSize = 5;
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage(Integer.toString(ProxyProtocolSpec.ADD_TO_HEADER_SIZE + headerSize));

        InputAssist assist = newAssist(new byte[] {0, (byte) headerSize});
        assist.readHeaderSize();
        int read = 2;
        assist.validateHeaderSizeBeforeRead(
                ProxyProtocolSpec.ADD_TO_HEADER_SIZE - read + headerSize + 1);
    }

    @Test
    public void testReadShort_happyPath() throws IOException {
        InputAssist assist = newAssist(new byte[] {-1, -1});
        assertEquals(0xFFFF, assist.readShort());
    }

    @Test
    public void testReadShort_truncated() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        newAssist(new byte[] {-1}).readShort();
    }

    @Test
    public void testReadShort_empty() throws IOException {
        thrown.expect(InvalidHeaderException.class);
        newAssist(new byte[0]).readShort();
    }
    @Test
    public void testReadBytes_empty() throws IOException {
        newAssist(new byte[0]).readBytes(0, "label");
    }


    private InputAssist newAssist(byte[] data) {
        return new InputAssist(new ByteArrayInputStream(data), false);
    }

}
