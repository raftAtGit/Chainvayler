package raft.chainvayler.samples.android.event;

public class BookModifiedEvent {
    public final int bookId;

    public BookModifiedEvent(int bookId) {
        this.bookId = bookId;
    }
}
