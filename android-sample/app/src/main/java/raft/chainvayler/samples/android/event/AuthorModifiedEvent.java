package raft.chainvayler.samples.android.event;

public class AuthorModifiedEvent {
    public final int authorId;

    public AuthorModifiedEvent(int authorId) {
        this.authorId = authorId;
    }
}
