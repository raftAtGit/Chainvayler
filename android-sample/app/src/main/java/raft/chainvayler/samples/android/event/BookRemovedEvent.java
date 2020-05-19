package raft.chainvayler.samples.android.event;

public class BookRemovedEvent {
    public final int bookId;

    public BookRemovedEvent(int bookId) {
        this.bookId = bookId;
    }
}
