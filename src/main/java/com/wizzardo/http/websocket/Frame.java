package com.wizzardo.http.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import static com.wizzardo.http.websocket.Charsets.UTF_8;

/**
 * @author: wizzardo
 * Date: 06.10.14
 */
public class Frame {
    public static final int MAX_HEADER_LENGTH = 14;

    public static final int FINAL_FRAME = 1 << 7;
    public static final int MASKED = 1 << 7;
    public static final int RSV1 = 1 << 6;
    public static final int RSV2 = 1 << 5;
    public static final int RSV3 = 1 << 4;
    public static final int OPCODE = 0x0f;
    public static final byte OPCODE_CONTINUATION_FRAME = 0;
    public static final byte OPCODE_TEXT_FRAME = 1;
    public static final byte OPCODE_BINARY_FRAME = 2;
    public static final byte OPCODE_CONNECTION_CLOSE = 8;
    public static final byte OPCODE_PING = 9;
    public static final byte OPCODE_PONG = 10;
    public static final int LENGTH_FIRST_BYTE = 0x7f;

    protected static final Random RANDOM = new Random();
    protected static final ByteArraySupplier DEFAULT_BYTE_ARRAY_SUPPLIER = new ByteArraySupplier() {
        @Override
        public byte[] supply(int minLength) {
            return new byte[minLength];
        }
    };

    protected boolean finalFrame = true;
    protected byte rsv1, rsv2, rsv3;
    protected byte opcode = OPCODE_TEXT_FRAME;
    protected boolean masked;
    protected int length;
    protected byte[] maskingKey;
    protected boolean complete;
    protected byte[] data;
    protected int offset;
    protected int read;
    protected boolean readHeaders = false;
    protected int limit;
    protected ByteArraySupplier byteArraySupplier;

    public Frame(byte[] data, int offset, int length) {
        this(data, offset, length, true);
    }

    public Frame(byte[] data, int offset, int length, boolean copy) {
        this(OPCODE_TEXT_FRAME, data, offset, length, copy);
    }

    public Frame(byte opcode, byte[] data, int offset, int length) {
        this(opcode, data, offset, length, true);
    }

    public Frame(byte opcode, byte[] data, int offset, int length, boolean copy) {
        if (copy) {
            byte[] bytes = new byte[MAX_HEADER_LENGTH + length];
            System.arraycopy(data, offset, bytes, MAX_HEADER_LENGTH, length);
            this.data = bytes;
            this.offset = MAX_HEADER_LENGTH;
            this.length = length;
        } else {
            if (offset < MAX_HEADER_LENGTH)
                throw new IllegalArgumentException("Offset should be >= MAX_HEADER_LENGTH (" + MAX_HEADER_LENGTH + ")");

            this.data = data;
            this.offset = offset;
            this.length = length;
        }
        this.opcode = opcode;
        byteArraySupplier = DEFAULT_BYTE_ARRAY_SUPPLIER;
    }

    public Frame(byte opCode) {
        this.opcode = opCode;
        byteArraySupplier = DEFAULT_BYTE_ARRAY_SUPPLIER;
    }

    public Frame(int limit) {
        this.limit = limit;
        byteArraySupplier = DEFAULT_BYTE_ARRAY_SUPPLIER;
    }

    public Frame() {
        this(Integer.MAX_VALUE);
    }

    public static Frame closeFrame(int status) {
        return closeFrame(status, null);
    }

    public static Frame closeFrame(int status, String message) {
        if (status < 1000 || status >= 5000)
            throw new IllegalArgumentException("Status must be > 999 and < 5000");

        byte[] data;
        if (message != null) {
            byte[] bytes = message.getBytes(Charsets.UTF_8);
            data = new byte[bytes.length + 2 + MAX_HEADER_LENGTH];
            System.arraycopy(bytes, 0, data, 2 + MAX_HEADER_LENGTH, bytes.length);
        } else {
            data = new byte[2 + MAX_HEADER_LENGTH];
        }

        data[0 + MAX_HEADER_LENGTH] = (byte) ((status >> 8) & 0xff);
        data[1 + MAX_HEADER_LENGTH] = (byte) (status & 0xff);

        return new Frame(Frame.OPCODE_CONNECTION_CLOSE, data, MAX_HEADER_LENGTH, data.length - MAX_HEADER_LENGTH, false);
    }

    @Override
    public String toString() {
        return new String(data, offset, length);
    }

    public byte[] getData() {
        return data;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String asString() {
        return new String(asBytes(), UTF_8);
    }

    public byte[] asBytes() {
        byte[] data = new byte[length];
        asBytes(data);
        return data;
    }

    public int asBytes(byte[] result) {
        return asBytes(result, 0);
    }

    public int asBytes(byte[] result, int offset) {
        System.arraycopy(data, this.offset, result, offset, length);
        return offset + length;
    }

    public void setByteArraySupplier(ByteArraySupplier byteArraySupplier) {
        this.byteArraySupplier = byteArraySupplier;
    }

    public ByteArraySupplier getByteArraySupplier() {
        return byteArraySupplier;
    }

    public void write(OutputStream out) throws IOException {
        if (data == null) {
            data = new byte[MAX_HEADER_LENGTH];
            offset = MAX_HEADER_LENGTH;
        }

        int headerOffset = getHeader(data);
        out.write(data, headerOffset, length + MAX_HEADER_LENGTH - headerOffset);
    }

    protected void mask(byte[] data, byte[] mask, int offset, int length) {
        for (int i = offset; i < length + offset; i++) {
            data[i] = (byte) (data[i] ^ mask[(i - offset) % 4]);
        }
    }

    public Frame unmask() {
        if (!masked)
            return this;

        masked = false;
        mask(data, maskingKey, offset, length);
        return this;
    }

    public Frame mask() {
        if (masked)
            return this;

        masked = true;
        if (maskingKey == null)
            maskingKey = intToBytes(RANDOM.nextInt());
        mask(data, maskingKey, offset, length);
        return this;
    }

    protected byte[] intToBytes(int i) {
        return intToBytes(i, new byte[4], 0);
    }

    protected byte[] intToBytes(int i, byte[] bytes, int offset) {
        bytes[offset] = (byte) ((i >> 24) & 0xff);
        bytes[offset + 1] = (byte) ((i >> 16) & 0xff);
        bytes[offset + 2] = (byte) ((i >> 8) & 0xff);
        bytes[offset + 3] = (byte) (i & 0xff);
        return bytes;
    }

    public void read(InputStream in) throws IOException {
        while (read != length) {
            int r = in.read(data, offset + read, (length - read));
            if (r == -1)
                throw new IOException("Unexpected end of stream");
            read += r;
        }
        complete = true;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isFinalFrame() {
        return finalFrame;
    }

    public int read(byte[] bytes, int offset, int length) {
        if (complete)
            return 0;

        if (readHeaders) {
            int r = Math.min(this.length - read, length);
            System.arraycopy(bytes, offset, data, read + this.offset, r);
            read += r;
            if (this.length == read)
                complete = true;
            return r;
        } else {
            if (offset + 2 > length)
                return 0;

            byte b = bytes[offset];
            finalFrame = (b & FINAL_FRAME) != 0;
            rsv1 = (byte) (b & RSV1);
            rsv2 = (byte) (b & RSV2);
            rsv3 = (byte) (b & RSV3);

            opcode = (byte) (b & OPCODE);

            b = bytes[offset + 1];
            masked = (b & MASKED) != 0;
            this.length = b & LENGTH_FIRST_BYTE;
            int r = 2;
            if (this.length == 126) {
                r += 2;
                if (offset + r > length)
                    return 0;
                this.length = ((bytes[offset + 2] & 0xff) << 8) + (bytes[offset + 3] & 0xff);
            } else if (this.length == 127) {
                r += 8;
                if (offset + r > length)
                    return 0;
                this.length =
//                        ((long) (bytes[offset + 2] & 0xff) << 56)
//                        + ((long) (bytes[offset + 3] & 0xff) << 48)
//                        + ((long) (bytes[offset + 4] & 0xff) << 40)
//                        + ((long) (bytes[offset + 5] & 0xff) << 32)  // not support long frames
                        +((bytes[offset + 6] & 0xff) << 24)
                                + ((bytes[offset + 7] & 0xff) << 16)
                                + ((bytes[offset + 8] & 0xff) << 8)
                                + (bytes[offset + 9] & 0xff);
                if (this.length > limit)
                    throw new IllegalStateException("Max frame length is exceeded. " + this.length + ">" + limit);
            }
            if (masked) {
                if (offset + r + 4 > length)
                    return 0;
                maskingKey = new byte[]{bytes[offset + r], bytes[offset + r + 1], bytes[offset + r + 2], bytes[offset + r + 3]};
                r += 4;
            }

            complete = length - r >= this.length;

            data = byteArraySupplier.supply(MAX_HEADER_LENGTH + this.length);
            read = Math.min(length - r, this.length);
            System.arraycopy(bytes, r, data, MAX_HEADER_LENGTH, read);
            this.offset = MAX_HEADER_LENGTH;
            readHeaders = true;
            return read + r;
        }
    }

    public static boolean hasHeaders(byte[] bytes, int offset, int length) {
        if (length >= 2) {
            int b = bytes[offset + 1] & 0xff;
            if ((b & Frame.MASKED) != 0) {
                length -= 4;
                b -= 128;
            }

            if (b <= 125)
                return true;
            else if (b == 126 && length >= 4)
                return true;
            else if (b == 127 && length >= 10)
                return true;

        }
        return false;
    }

    protected int calculateHeadersSize(int dataLength, boolean masked) {
        if (dataLength <= 125)
            return 2 + (masked ? 4 : 0);
        else if (dataLength < 65536)
            return 4 + (masked ? 4 : 0);
        else
            return 10 + (masked ? 4 : 0);
    }

    public int getHeader(byte[] header) {
        int headerOffset = offset - calculateHeadersSize(length, masked);
        int value = opcode;
        if (finalFrame)
            value |= FINAL_FRAME;
        header[headerOffset] = (byte) value;

        value = length;
        if (value <= 125) {
            if (masked)
                value |= MASKED;
            header[headerOffset + 1] = (byte) value;
        } else if (value < 65536) {
            value = 126;
            if (masked)
                value |= MASKED;
            header[headerOffset + 1] = (byte) value;
            header[headerOffset + 2] = (byte) (length >> 8);
            header[headerOffset + 3] = (byte) length;
        } else {
            value = 127;
            if (masked)
                value |= MASKED;
            header[headerOffset + 1] = (byte) value;
            header[headerOffset + 6] = (byte) (length >> 24);
            header[headerOffset + 7] = (byte) (length >> 16);
            header[headerOffset + 8] = (byte) (length >> 8);
            header[headerOffset + 9] = (byte) length;
        }

        if (masked) {
            header[offset - 4] = maskingKey[0];
            header[offset - 3] = maskingKey[1];
            header[offset - 2] = maskingKey[2];
            header[offset - 1] = maskingKey[3];
        }

        return headerOffset;
    }

    public boolean isPing() {
        return opcode == OPCODE_PING;
    }

    public boolean isPong() {
        return opcode == OPCODE_PONG;
    }

    public void setOpcode(byte opcode) {
        this.opcode = opcode;
    }

    public boolean isMasked() {
        return masked;
    }

    public void setIsFinalFrame(boolean isFinalFrame) {
        this.finalFrame = isFinalFrame;
    }

    public boolean isClose() {
        return opcode == OPCODE_CONNECTION_CLOSE;
    }

    public byte[] getFrameBytes() {
        if (data == null) {
            data = new byte[MAX_HEADER_LENGTH];
            offset = MAX_HEADER_LENGTH;
        }

        getHeader(data);

        return data;
    }

    public int getFrameOffset() {
        return MAX_HEADER_LENGTH - calculateHeadersSize(length, masked);
    }

    public int getFrameLength() {
        return calculateHeadersSize(length, masked) + length;
    }
}
