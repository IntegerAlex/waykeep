package de.dennisguse.opentracks.util;

import androidx.annotation.NonNull;

import java.util.Iterator;

public abstract class AutoCloseableIterator<T> implements Iterator<T>, AutoCloseable {

    /**
     * Override: AutoClosable.close() throws Exception.
     */
    public abstract void close();

    public abstract int getPosition();

    public abstract boolean moveToPosition(int position);

    @NonNull
    public abstract T get();

    @NonNull
    public T get(int position) {
        moveToPosition(position);
        return get();
    }

    public abstract int getCount();
}
