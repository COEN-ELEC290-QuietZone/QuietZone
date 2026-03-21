package com.example.quietzone_app;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FirebaseListenerRegistry {

    private static final CopyOnWriteArrayList<ListenerHandle> ACTIVE_LISTENERS = new CopyOnWriteArrayList<>();

    private FirebaseListenerRegistry() {
    }

    public static ListenerHandle register(DatabaseReference reference, ValueEventListener listener) {
        if (reference == null || listener == null) {
            return null;
        }
        ListenerHandle handle = new ListenerHandle(reference, listener);
        ACTIVE_LISTENERS.add(handle);
        return handle;
    }

    public static void clearAll() {
        List<ListenerHandle> snapshot = new ArrayList<>(ACTIVE_LISTENERS);
        for (ListenerHandle handle : snapshot) {
            handle.detachInternal();
        }
        ACTIVE_LISTENERS.clear();
    }

    public static final class ListenerHandle {
        private final DatabaseReference reference;
        private final ValueEventListener listener;

        private ListenerHandle(DatabaseReference reference, ValueEventListener listener) {
            this.reference = reference;
            this.listener = listener;
        }

        public void detachAndUnregister() {
            detachInternal();
            ACTIVE_LISTENERS.remove(this);
        }

        private void detachInternal() {
            reference.removeEventListener(listener);
        }
    }
}
