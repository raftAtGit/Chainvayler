package raft.chainvayler.samples.android.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

@Chained
public class Book implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private Author author;
    private boolean read = false;
    private boolean favorite = false;
    private String notes = "";
    private Set<String> genres = new HashSet<>();

    public Book(String name) {
        this.name = name;
    }

    public Book(String name, Author author) {
        this(name);
        setAuthor(author);
    }

    public int getId() {
        return id;
    }

    @Modification
    void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    @Modification
    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getGenres() {
        return Collections.unmodifiableSet(genres);
    }

    @Modification
    public void addGenre(String genre) {
        genres.add(genre);
    }

    @Modification
    public void setGenres(Collection<String> genres) {
        this.genres.clear();
        this.genres.addAll(genres);
    }

    @Modification
    public void removeGenre(String genre) {
        genres.remove(genre);
    }


    public Author getAuthor() {
        return author;
    }

    @Modification
    public void setAuthor(Author author) {
        if (this.author != null) {
            this.author.removeBook(this);
        }
        this.author = author;
        if (author != null) {
            author.addBook(this);
        }
    }

    public boolean isRead() {
        return read;
    }

    @Modification
    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isFavorite() {
        return favorite;
    }

    @Modification
    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getNotes() {
        return notes;
    }

    @Modification
    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "Book:" + name;
    }

}
