package raft.chainvayler.samples.android.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

@Chained
public class Library implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Integer, Book> books = new HashMap<>();
    private final Map<Integer, Author> authors = new HashMap<>();

    private int lastBookId = 1;
    private int lastAuthorId = 1;

    private boolean populated = false;

    public Book getBook(int id) {
        return books.get(id);
    }

    @Modification
    public void addBook(Book book) {
        book.setId(lastBookId++);
        books.put(book.getId(), book);
    }

    @Modification
    public void removeBook(Book book) {
        books.remove(book.getId());
    }

    public Collection<Book> getBooks() {
        return Collections.unmodifiableCollection(books.values());
    }

    @Modification
    public void removeAllBooks() {
        books.clear();
        authors.values().forEach(author -> author.removeAllBooks());
    }

    public Author getAuthor(int id) {
        return authors.get(id);
    }

    @Modification
    public void addAuthor(Author author) {
        author.setId(lastAuthorId++);
        authors.put(author.getId(), author);
    }

    @Modification
    public void removeAuthor(Author author) {
        authors.remove(author.getId());
    }

    public Collection<Author> getAuthors() {
        return Collections.unmodifiableCollection(authors.values());
    }

    public Author findAuthorByName(String name) {
        return authors.values().stream()
            .filter(author -> name.equalsIgnoreCase(author.getName()))
            .findAny()
            .orElse(null);
    }

    @Modification
    public void removeAllAuthors() {
        authors.clear();
        books.values().forEach(book -> book.setAuthor(null));
    }

    public Set<String> getGenres() {
        Set<String> genres = new TreeSet<>();
        books.values().forEach(book -> genres.addAll(book.getGenres()));
        return genres;
    }

    public Set<String> getCountries() {
        Set<String> genres = new TreeSet<>();
        authors.values().forEach(author -> {
            String country = author.getCountry();
            if ((country != null) && !country.trim().isEmpty())
                genres.add(author.getCountry());
        });
        return genres;
    }

    public boolean isPopulated() {
        return populated;
    }
    @Modification
    public void setPopulated() {
        populated = true;
    }

}
