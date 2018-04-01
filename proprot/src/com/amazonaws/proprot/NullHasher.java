/* Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
except in compliance with the License. A copy of the License is located at

    http://aws.amazon.com/apache2.0/

or in the "license" file accompanying this file. This file is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under the License.
*/
package com.amazonaws.proprot;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;

class NullHasher implements Hasher {
    private static final HashCode HASH_CODE = HashCode.fromInt(0);
    public static final Hasher INSTANCE = new NullHasher();

    @Override
    public Hasher putByte(byte b) {
        return null;
    }

    @Override
    public Hasher putBytes(byte[] bytes) {
        return null;
    }
    
    public Hasher putBytes(ByteBuffer bytes) {
        return null;
    }

    @Override
    public Hasher putBytes(byte[] bytes, int off, int len) {
        return null;
    }

    @Override
    public Hasher putShort(short s) {
        return null;
    }

    @Override
    public Hasher putInt(int i) {
        return null;
    }

    @Override
    public Hasher putLong(long l) {
        return null;
    }

    @Override
    public Hasher putFloat(float f) {
        return null;
    }

    @Override
    public Hasher putDouble(double d) {
        return null;
    }

    @Override
    public Hasher putBoolean(boolean b) {
        return null;
    }

    @Override
    public Hasher putChar(char c) {
        return null;
    }

    @Override
    public Hasher putUnencodedChars(CharSequence charSequence) {
        return null;
    }

    @Override
    public Hasher putString(CharSequence charSequence, Charset charset) {
        return null;
    }

    @Override
    public <T> Hasher putObject(T instance, Funnel<? super T> funnel) {
        return null;
    }

    @Override
    public HashCode hash() {
        return HASH_CODE;
    }
}