package raft.chainvayler.samples.android.data;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import raft.chainvayler.Chained;
import raft.chainvayler.Modification;

@Chained
public class Author implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String country;
    private Date birth;
    private Date death;
    private Set<Book> books = new HashSet<>();

    public Author(String name) {
        this.name = name;
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

    public Set<Book> getBooks() {
        return Collections.unmodifiableSet(books);
    }

    @Modification
    void addBook(Book book) {
        books.add(book);
    }

    @Modification
    void removeBook(Book book) {
        books.remove(book);
    }

    @Modification
    void removeAllBooks() {
        books.clear();
    }

    public String getCountry() {
        return country;
    }

    @Modification
    public void setCountry(String country) {
        this.country = country;
    }

    public Date getBirth() {
        return birth;
    }

    @Modification
    public void setBirth(Date birth) {
        this.birth = birth;
    }

    public Date getDeath() {
        return death;
    }

    @Modification
    public void setDeath(Date death) {
        this.death = death;
    }

    @Override
    public String toString() {
        return "Author:" + name;
    }

}
