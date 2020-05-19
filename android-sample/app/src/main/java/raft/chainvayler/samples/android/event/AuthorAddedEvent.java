package raft.chainvayler.samples.android.event;

public class AuthorAddedEvent {
    public final int authorId;

    public AuthorAddedEvent(int authorId) {
        this.authorId = authorId;
    }
}
