/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import static java.lang.Integer.toHexString;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.amazonaws.proprot.ProxyProtocolSpec.Code;
import com.amazonaws.proprot.ProxyProtocolSpec.Command;


public class ProxyProtocolSpecTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testValueOf_happyPath() {
        assertEquals(Command.PROXY, Command.valueOf(Command.PROXY.getCode(), 37));
    }

    @Test
    public void testValidatePrefix_valueOf() {
        thrown.expect(InvalidHeaderException.class);
        int pos = 37;
        thrown.expectMessage(Integer.toString(pos));
        int code = 89;
        thrown.expectMessage("0x" + toHexString(code));
        thrown.expectMessage(Command.class.getSimpleName());
        Command.valueOf(code, pos);
    }

    @Test
    public void testInitCodeEnum_happyPath() {
        Map<Integer, Command> valuesByCode = new HashMap<>();
        ProxyProtocolSpec.initCodeEnum(Command.values(), valuesByCode);
        assertEquals(Command.values().length, valuesByCode.size());
    }

    @Test
    public void testInitCodeEnum_duplicatedCode() {
        thrown.expect(InvalidHeaderException.class);
        thrown.expectMessage("0x" + toHexString(TestCode.DUP_1.getCode()));
        thrown.expectMessage(TestCode.DUP_1.name());
        thrown.expectMessage(TestCode.DUP_2.name());
        ProxyProtocolSpec.initCodeEnum(TestCode.values(), new HashMap<>());
    }

    @Test
    public void testToString() {
        assertEquals("PROXY(1)", Command.PROXY.toString());
    }

    private enum TestCode implements Code {
        CODE0(0x0), CODE1(0x1), DUP_1(22), DUP_2(22);

        private final int code;

        private TestCode(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }
    }
}

