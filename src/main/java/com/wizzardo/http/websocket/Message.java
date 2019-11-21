package com.wizzardo.http.websocket;

import java.util.ArrayList;
import java.util.List;

import static com.wizzardo.http.websocket.Charsets.UTF_8;

/**
 * @author: wizzardo
 * Date: 06.10.14
 */
public class Message {
    protected List<Frame> frames = new ArrayList<Frame>();

    public Message() {
    }

    public Message(String message) {
        append(message);
    }

    public Message(byte[] message) {
        append(message);
    }

    public Message(byte[] message, int offset, int length) {
        append(message, offset, length);
    }

    public void clear() {
        frames.clear();
    }

    public boolean isComplete() {
        if (frames.isEmpty())
            return false;

        Frame frame = last();
        return frame.isFinalFrame() && frame.isComplete();
    }

    public void add(Frame frame) {
        if (!frames.isEmpty()) {
            last().setIsFinalFrame(false);
            frame.setOpcode(Frame.OPCODE_CONTINUATION_FRAME);
        }
        frames.add(frame);
    }

    public Frame get(int i) {
        return frames.get(i);
    }

    public Frame last() {
        return get(size() - 1);
    }

    public int size() {
        return frames.size();
    }

    public boolean isTextMessage() {
        return size() > 0 && last().opcode == Frame.OPCODE_TEXT_FRAME;
    }

    public boolean isBinaryMessage() {
        return size() > 0 && last().opcode == Frame.OPCODE_BINARY_FRAME;
    }

    public Message append(String s) {
        return append(s.getBytes(UTF_8));
    }

    public Message append(byte[] bytes) {
        return append(bytes, 0, bytes.length);
    }

    public Message append(byte[] bytes, int offset, int length) {
        add(new Frame(bytes, offset, length));
        return this;
    }

    public Message append(byte[] bytes, int offset, int length, byte opcode) {
        add(new Frame(opcode, bytes, offset, length));
        return this;
    }

    public String asString() {
        return new String(asBytes(), UTF_8);
    }

    public byte[] asBytes() {
        byte[] data = new byte[getBytesLength()];
        asBytes(data);
        return data;
    }

    public int asBytes(byte[] result) {
        int offset = 0;
        for (Frame frame : frames) {
            offset = frame.asBytes(result, offset);
        }
        return offset;
    }

    public int getBytesLength() {
        int length = 0;
        for (Frame frame : frames)
            length += frame.getLength();
        return length;
    }

    public int length() {
        return getBytesLength();
    }

    List<Frame> getFrames() {
        return frames;
    }
}
