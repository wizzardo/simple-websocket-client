package com.wizzardo.http.websocket;

/**
 * Created by Mikhail Bobrutskov on 10.07.17.
 */
public interface ByteArraySupplier {
    byte[] supply(int minLength);
}
