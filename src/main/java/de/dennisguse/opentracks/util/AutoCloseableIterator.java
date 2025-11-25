package de.dennisguse.opentracks.util;

import java.util.Iterator;

public abstract class AutoCloseableIterator<T> implements Iterator<T>, AutoCloseable {

    /**
     * Override: AutoClosable.close() throws Exception.
     */
    public abstract void close();

    public abstract int getPosition();

    public abstract int getCount();
}
