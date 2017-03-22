package com.wizzardo.http.websocket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: wizzardo
 * Date: 03.10.14
 */
public class SimpleWebSocketClient extends Thread {
    protected static final byte[] RNRN = "\r\n\r\n".getBytes(Charsets.UTF_8);
    protected Request request;
    protected InputStream in;
    protected OutputStream out;
    protected byte[] buffer = new byte[1024];
    protected volatile int limit = 0;
    protected Message message = new Message();
    protected Socket socket;
    protected volatile boolean connected;

    public static class Request {
        protected URI uri;

        public Request(String url) throws URISyntaxException {
            URI u = new URI(url.trim());
            if (!u.getScheme().equalsIgnoreCase("ws") && !u.getScheme().equalsIgnoreCase("wss"))
                throw new IllegalArgumentException("url must use ws or wss scheme");
            uri = u;
        }

        private Map<String, String> params = new HashMap<String, String>();
        private Map<String, String> headers = new HashMap<String, String>();

        public Request param(String key, String value) {
            try {
                params.put(URLEncoder.encode(key, "utf-8"), URLEncoder.encode(value, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        public Request header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public String build() {
            StringBuilder sb = new StringBuilder();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            sb.append("GET ").append(path.isEmpty() ? "/" : path);
            boolean amp = query != null;
            if (amp || !params.isEmpty())
                sb.append('?');
            if (amp)
                sb.append(query);
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (amp)
                    sb.append('&');
                else
                    amp = true;
                sb.append(param.getKey()).append('=').append(param.getValue());
            }

            sb.append(" HTTP/1.1\r\n")
                    .append("Host: ").append(uri.getHost()).append(":").append(port()).append("\r\n")
                    .append("Upgrade: websocket\r\n")
                    .append("Connection: Upgrade\r\n")
                    .append("Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==\r\n")
                    .append("Sec-WebSocket-Version: 13\r\n")
                    .append("Origin: http://").append(uri.getHost()).append(":").append(port()).append("\r\n");

            for (Map.Entry<String, String> header : headers.entrySet())
                sb.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            sb.append("\r\n");

            return sb.toString();
        }

        public String host() {
            return uri.getHost();
        }

        public int port() {
            int port = uri.getPort();
            if (port != -1)
                return port;

            if (isSecure())
                return 443;
            else
                return 80;
        }

        public boolean isSecure() {
            return uri.getScheme().equalsIgnoreCase("wss");
        }

        public Socket connect() throws IOException {
            if (!isSecure())
                return new Socket(host(), port());

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(host(), port());
            return socket;
        }
    }

    public SimpleWebSocketClient(Request request) throws URISyntaxException, IOException {
        this.request = request;
    }

    public SimpleWebSocketClient(String url) throws URISyntaxException, IOException {
        this(new Request(url));
    }

    public void connectIfNot() throws IOException {
        if (!connected)
            handshake(request);
    }

    protected synchronized void handshake(Request request) throws IOException {
        socket = request.connect();
        in = socket.getInputStream();
        out = socket.getOutputStream();

        out.write(request.build().getBytes());
        out.flush();

        int response = 0;
        while ((limit += in.read(buffer, limit, buffer.length - limit)) != -1) {
//            System.out.println(new String(bytes, 0, r));
            if ((response = search(buffer, 0, limit, RNRN)) >= 0)
                break;
        }

//        System.out.println(new String(buffer, 0, response));
        try {
            onConnect();
        } catch (Exception e) {
            onError(e);
        }

        limit -= response - 4;
        if (limit != 0)
            System.arraycopy(buffer, response + 4, buffer, 0, limit);

        connected = true;
    }

    protected int search(byte[] src, int from, int to, byte[] needle) {
        if (needle == null || needle.length == 0)
            return -1;

        int length = needle.length;
        to = Math.min(to, src.length);
        to -= needle.length;

        outer:
        for (int i = from; i <= to; i++) {
            if (src[i] == needle[0]) {
                for (int j = 1; j < length; j++) {
                    if (src[i + j] != needle[j])
                        continue outer;
                }
                return i;
            }
        }
        return -1;
    }

    @Override
    public void run() {
        while (true) {
            try {
                waitForMessage();
            } catch (IOException e) {
                connected = false;
                try {
                    onError(e);
                    onClose();
                } catch (Exception ex) {
                    onError(ex);
                }
                message = new Message();
                limit = 0;
            }
        }
    }

    public void waitForMessage() throws IOException {
        connectIfNot();

        while (!message.isComplete()) {
            if (!onFrame(readFrame()))
                break;
        }
        if (!message.isComplete())
            return;

        onMessage(message);
        message = new Message();
    }

    protected boolean onFrame(Frame frame) {
        if (frame.isPing())
            return true;

        if (frame.isClose()) {
            connected = false;
            onClose();
            return false;
        }

        message.add(frame);
        return true;
    }

    private Frame readFrame() throws IOException {
        while (!Frame.hasHeaders(buffer, 0, limit)) {
            int read = in.read(buffer, limit, buffer.length - limit);
            if (read == -1)
                throw new IOException("Connection closed");

            limit += read;
        }
        Frame frame = new Frame();
        int r = frame.read(buffer, 0, limit);
        limit -= r;
        if (limit != 0)
            System.arraycopy(buffer, r, buffer, 0, limit);

        if (frame.isComplete())
            return frame;
        else {
            while (!frame.isComplete()) {
                frame.read(in);
            }
            return frame;
        }
    }

    public void onMessage(Message message) {
    }

    public void onConnect() {
    }

    public void onClose() {
    }

    public void onError(Exception e) {
        e.printStackTrace();
    }

    public boolean isClosed() {
        return !connected;
    }


    public void send(Message message) throws IOException {
        connectIfNot();

        for (Frame frame : message.getFrames()) {
            frame.mask().write(out);
        }
    }

    public void send(String s) throws IOException {
        send(s.getBytes());
    }

    public void send(byte[] data) throws IOException {
        send(data, 0, data.length);
    }

    public void send(Frame frame) throws IOException {
        connectIfNot();

        frame.write(out);
    }

    public void send(byte[] data, int offset, int length) throws IOException {
        send(new Frame(data, offset, length).mask());
    }

    public long ping() throws IOException {
        connectIfNot();

        long time = System.currentTimeMillis();
        new Frame(Frame.OPCODE_PING).write(out);
        Frame frame = readFrame();
        while (!frame.isPong()) {
            onFrame(frame);
            if (frame.isClose())
                break;

            frame = readFrame();
        }
        return System.currentTimeMillis() - time;
    }

    public void close() throws IOException {
        if (!connected)
            return;

        new Frame(Frame.OPCODE_CONNECTION_CLOSE).write(out);
        Frame frame = readFrame();
        while (!frame.isClose()) {
            onFrame(frame);
            frame = readFrame();
        }
        connected = false;
        onClose();
    }
}
