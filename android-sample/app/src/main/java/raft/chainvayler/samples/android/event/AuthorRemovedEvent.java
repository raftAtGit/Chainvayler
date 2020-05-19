package raft.chainvayler.samples.android.event;

public class AuthorRemovedEvent {
    public final int authorId;

    public AuthorRemovedEvent(int authorId) {
        this.authorId = authorId;
    }
}
