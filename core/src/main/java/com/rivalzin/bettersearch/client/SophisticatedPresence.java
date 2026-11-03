package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.FailurePolicy;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SophisticatedPresence {
    private static final String BASE = "net/p3pp3rf1y/sophisticatedcore/client/gui/StorageScreenBase.class";

    private SophisticatedPresence() {
    }

    public static boolean available(boolean backpacks) {
        return Loaded.CONTRACT && (backpacks ? Loaded.BACKPACKS : Loaded.STORAGE);
    }

    private static boolean exists(String name) {
        try {
            return SophisticatedPresence.class.getClassLoader().getResource(name) != null;
        } catch (RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    private static boolean contract() {
        try (InputStream source = SophisticatedPresence.class.getClassLoader().getResourceAsStream(BASE)) {
            if (source == null) {
                return false;
            }
            DataInputStream input = new DataInputStream(source);
            if (input.readInt() != 0xCAFEBABE) {
                return false;
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            String[] utf = new String[input.readUnsignedShort()];
            for (int index = 1; index < utf.length; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1:
                        utf[index] = input.readUTF();
                        break;
                    case 3:
                    case 4:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        input.readInt();
                        break;
                    case 5:
                    case 6:
                        input.readLong();
                        index++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        input.readUnsignedShort();
                        break;
                    case 15:
                        input.readUnsignedByte();
                        input.readUnsignedShort();
                        break;
                    default:
                        return false;
                }
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            input.readUnsignedShort();
            for (int count = input.readUnsignedShort(); count > 0; count--) {
                input.readUnsignedShort();
            }
            boolean filter = false;
            for (int count = input.readUnsignedShort(); count > 0; count--) {
                int access = input.readUnsignedShort();
                String name = utf[input.readUnsignedShort()];
                String descriptor = utf[input.readUnsignedShort()];
                filter |= "stackFilter".equals(name) && "Ljava/util/function/Predicate;".equals(descriptor)
                        && (access & 0x0018) == 0;
                skipAttributes(input);
            }
            boolean update = false;
            boolean changed = false;
            for (int count = input.readUnsignedShort(); count > 0; count--) {
                int access = input.readUnsignedShort();
                String name = utf[input.readUnsignedShort()];
                String descriptor = utf[input.readUnsignedShort()];
                if ((access & 0x0508) == 0 && "(Ljava/lang/String;)V".equals(descriptor)) {
                    update |= "updateSearchFilter".equals(name);
                    changed |= "onSearchPhraseChange".equals(name);
                }
                skipAttributes(input);
            }
            return filter && update && changed;
        } catch (IOException | RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        for (int count = input.readUnsignedShort(); count > 0; count--) {
            input.readUnsignedShort();
            int remaining = input.readInt();
            if (remaining < 0 || remaining > 16_777_216) {
                throw new IOException("Invalid optional class attribute length");
            }
            while (remaining > 0) {
                int skipped = input.skipBytes(remaining);
                if (skipped == 0) {
                    input.readByte();
                    skipped = 1;
                }
                remaining -= skipped;
            }
        }
    }

    private static final class Loaded {
        static final boolean CONTRACT = contract();
        static final boolean BACKPACKS = exists("net/p3pp3rf1y/sophisticatedbackpacks/client/gui/BackpackScreen.class");
        static final boolean STORAGE = exists("net/p3pp3rf1y/sophisticatedstorage/client/gui/StorageScreen.class");
    }
}
