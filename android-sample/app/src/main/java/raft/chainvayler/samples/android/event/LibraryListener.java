package raft.chainvayler.samples.android.event;

public interface LibraryListener {
    void onAuthorAdded(AuthorAddedEvent event);
    void onAuthorModified(AuthorModifiedEvent event);
    void onAuthorRemoved(AuthorRemovedEvent event);

    void onBookAdded(BookAddedEvent event);
    void onBookModified(BookModifiedEvent event);
    void onBookRemoved(BookRemovedEvent event);

    public static abstract class Adapter implements LibraryListener {

        @Override
        public void onAuthorAdded(AuthorAddedEvent event) {}

        @Override
        public void onAuthorModified(AuthorModifiedEvent event) {}

        @Override
        public void onAuthorRemoved(AuthorRemovedEvent event) {}

        @Override
        public void onBookAdded(BookAddedEvent event) {}

        @Override
        public void onBookModified(BookModifiedEvent event) {}

        @Override
        public void onBookRemoved(BookRemovedEvent event) {}
    }
}
