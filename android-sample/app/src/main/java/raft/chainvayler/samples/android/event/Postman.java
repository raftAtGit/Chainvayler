package raft.chainvayler.samples.android.event;

import java.util.HashSet;
import java.util.Set;

public class Postman {
    private final Set<LibraryListener> listeners = new HashSet<>();

    public void addListener(LibraryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LibraryListener listener) {
        listeners.remove(listener);
    }

    public void post(AuthorAddedEvent event) {
        listeners.forEach(l -> l.onAuthorAdded(event));
    }

    public void post(AuthorModifiedEvent event) {
        listeners.forEach(l -> l.onAuthorModified(event));
    }

    public void post(AuthorRemovedEvent event) {
        listeners.forEach(l -> l.onAuthorRemoved(event));
    }

    public void post(BookAddedEvent event) {
        listeners.forEach(l -> l.onBookAdded(event));
    }

    public void post(BookModifiedEvent event) {
        listeners.forEach(l -> l.onBookModified(event));
    }

    public void post(BookRemovedEvent event) {
        listeners.forEach(l -> l.onBookRemoved(event));
    }

}
