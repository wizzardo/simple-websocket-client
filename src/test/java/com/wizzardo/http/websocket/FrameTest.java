package com.wizzardo.http.websocket;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by wizzardo on 30/03/17.
 */
public class FrameTest {

    @Test
    public void test_read() throws IOException {
        byte[] data = "hello".getBytes("UTF-8");
        Frame frame = new Frame(data, 0, data.length).mask();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        frame.write(out);

        byte[] bytes = out.toByteArray();
        frame = new Frame();

        int read = frame.read(bytes, 0, bytes.length);

        Assert.assertEquals(bytes.length, read);
        Assert.assertTrue(frame.isComplete());
        Assert.assertTrue(frame.isMasked());

        frame.unmask();
        Assert.assertFalse(frame.isMasked());
        Assert.assertEquals(data.length, frame.getLength());

        byte[] result = new byte[frame.getLength()];
        System.arraycopy(frame.getData(), frame.getOffset(), result, 0, frame.getLength());
        Assert.assertArrayEquals(data, result);
    }

    @Test
    public void test_read_2() throws IOException {
        byte[] data = "hello".getBytes("UTF-8");
        Frame frame = new Frame(data, 0, data.length).mask();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        frame.write(out);

        byte[] bytes = out.toByteArray();
        frame = new Frame();

        Assert.assertEquals(0, frame.read(bytes, 0, 1));
        Assert.assertEquals(0, frame.read(bytes, 0, 2));
        Assert.assertEquals(0, frame.read(bytes, 0, 3));
        Assert.assertEquals(0, frame.read(bytes, 0, 4));
        Assert.assertEquals(0, frame.read(bytes, 0, 5));

        Assert.assertEquals(6, frame.read(bytes, 0, 6));

        int offset = 6;
        Assert.assertEquals(1, frame.read(bytes, offset++, 1));
        Assert.assertEquals(1, frame.read(bytes, offset++, 1));
        Assert.assertEquals(1, frame.read(bytes, offset++, 1));
        Assert.assertEquals(1, frame.read(bytes, offset++, 1));
        Assert.assertEquals(1, frame.read(bytes, offset++, 1));

        Assert.assertEquals(bytes.length, offset);
        Assert.assertEquals(0, frame.read(bytes, offset, 1));

        Assert.assertTrue(frame.isComplete());
        Assert.assertTrue(frame.isMasked());

        frame.unmask();
        Assert.assertFalse(frame.isMasked());
        Assert.assertEquals(data.length, frame.getLength());

        byte[] result = new byte[frame.getLength()];
        System.arraycopy(frame.getData(), frame.getOffset(), result, 0, frame.getLength());
        Assert.assertArrayEquals(data, result);
    }
}
