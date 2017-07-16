package com.wizzardo.http.websocket;

import java.util.ArrayList;
import java.util.List;

import static com.wizzardo.http.websocket.Charsets.UTF_8;

/**
 * @author: wizzardo
 * Date: 06.10.14
 */
public class Message {
    private List<Frame> frames = new ArrayList<Frame>();

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

    public boolean isComplete() {
        if (frames.isEmpty())
            return false;

        Frame frame = frames.get(frames.size() - 1);
        return frame.isFinalFrame() && frame.isComplete();
    }

    public void add(Frame frame) {
        if (!frames.isEmpty()) {
            frames.get(frames.size() - 1).setIsFinalFrame(false);
            frame.setOpcode(Frame.OPCODE_CONTINUATION_FRAME);
        }
        frames.add(frame);
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
            System.arraycopy(frame.getData(), frame.getOffset(), result, offset, frame.getLength());
            offset += frame.getLength();
        }

        return offset;
    }

    public int getBytesLength() {
        int length = 0;
        for (Frame frame : frames)
            length += frame.getLength();
        return length;
    }

    List<Frame> getFrames() {
        return frames;
    }
}
