package com.wizzardo.http.websocket.extension;


import com.wizzardo.http.websocket.Message;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wizzardo on 20/05/17.
 */

/**
 * Handles commands in format %<b>CommandNameLength</b>%%<b>CommandName</b>%%<b>Command</b>%,<br>
 * for example '13SimpleCommand{}' <br> <br>
 * <b>CommandNameLength</b> is optional
 */
public class SimpleCommandHandler<T> {
    protected Map<String, Map.Entry<Class<? extends CommandPojo>, CommandHandler<? extends T, ? extends CommandPojo>>> handlers = new ConcurrentHashMap<String, Map.Entry<Class<? extends CommandPojo>, CommandHandler<? extends T, ? extends CommandPojo>>>(16, 1f);
    protected Reader reader;
    protected ErrorHandler errorHandler;

    public SimpleCommandHandler(Reader reader, ErrorHandler errorHandler) {
        this.reader = reader;
        this.errorHandler = errorHandler;
    }

    public interface ErrorHandler {
        void onError(Exception e);
    }

    public interface Reader {
        Object read(Class<?> clazz, byte[] bytes, int offset, int length);
    }

    public interface CommandPojo {
    }

    public interface CommandHandler<T, C extends CommandPojo> {
        void handle(T client, C command);
    }

    public <C extends CommandPojo> void addHandler(Class<C> commandClass, CommandHandler<? extends T, C> handler) {
        AbstractMap.SimpleEntry<Class<? extends CommandPojo>, CommandHandler<? extends T, ? extends CommandPojo>> entry = new AbstractMap.SimpleEntry<Class<? extends CommandPojo>, CommandHandler<? extends T, ? extends CommandPojo>>(commandClass, handler);
        handlers.put(commandClass.getSimpleName(), entry);
    }

    public void onMessage(T listener, Message message) {
        try {
            byte[] bytes = message.asBytes();

            int[] holder = new int[1];
            int position = readInt(holder, bytes, 0, bytes.length);
            int nameLength = holder[0];
            String commandName;
            if (nameLength != -1) {
                commandName = new String(bytes, position, nameLength);
            } else {
                position = 0;
                nameLength = indexOf((byte) '{', bytes, position, bytes.length);
                commandName = new String(bytes, position, nameLength);
            }
            int offset = position + nameLength;
            Map.Entry<Class<? extends CommandPojo>, CommandHandler<? extends T, ? extends CommandPojo>> commandHandlerPair = handlers.get(commandName);
            if (commandHandlerPair == null)
                throw new IllegalArgumentException("Unknown command: " + commandName);

            CommandHandler<T, CommandPojo> handler = (CommandHandler<T, CommandPojo>) commandHandlerPair.getValue();
            Class<? extends CommandPojo> commandClass = commandHandlerPair.getKey();
            CommandPojo command = (CommandPojo) reader.read(commandClass, bytes, offset, bytes.length - offset);
            handler.handle(listener, command);
        } catch (Exception e) {
            onError(e);
        }
    }

    protected void onError(Exception e) {
        errorHandler.onError(e);
    }

    protected static int indexOf(byte b, byte[] bytes, int offset, int limit) {
        for (int i = offset; i < limit; i++) {
            if (bytes[i] == b)
                return i;
        }
        return -1;
    }

    protected static int readInt(int[] holder, byte[] bytes, int offset, int limit) {
        int value = 0;
        int i = offset;
        while (i < limit) {
            byte b = bytes[i];
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
            } else {
                if (i == offset)
                    holder[0] = -1;
                else
                    holder[0] = value;
                return i;
            }
            i++;
        }

        holder[0] = value;
        return limit;
    }
}
