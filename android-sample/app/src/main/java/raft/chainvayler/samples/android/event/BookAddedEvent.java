package raft.chainvayler.samples.android.event;

public class BookAddedEvent {
    public final int bookId;

    public BookAddedEvent(int bookId) {
        this.bookId = bookId;
    }
}
