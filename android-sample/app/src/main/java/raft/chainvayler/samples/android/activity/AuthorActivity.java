package raft.chainvayler.samples.android.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import raft.chainvayler.samples.android.LibraryApplication;
import raft.chainvayler.samples.android.R;
import raft.chainvayler.samples.android.data.Author;
import raft.chainvayler.samples.android.data.Book;
import raft.chainvayler.samples.android.data.Library;
import raft.chainvayler.samples.android.event.AuthorAddedEvent;
import raft.chainvayler.samples.android.event.AuthorModifiedEvent;
import raft.chainvayler.samples.android.event.AuthorRemovedEvent;

/** Edit an author or add a new one */
public class AuthorActivity extends AppCompatActivity {

    private static final int NO_VALUE = -1;

    private Library library;
    private Author author;
    private List<String> books;

    private EditText editName, editCountry;
    private LinearLayout booksLayout;
    private FloatingActionButton saveButton, deleteButton;

    private LibraryApplication application;

    private LayoutInflater layoutInflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author);

        this.application = (LibraryApplication) getApplication();
        this.library = application.getLibrary();

        int authorId = getIntent().getIntExtra(LibraryApplication.EXTRA_AUTHOR_ID, NO_VALUE);
        if (authorId != NO_VALUE) {
            this.author = library.getAuthor(authorId);
            assert author != null;
        }

        this.editName = findViewById(R.id.editText_name);
        this.editCountry = findViewById(R.id.editText_country);
        this.booksLayout = findViewById(R.id.linearLayout_books);
        this.saveButton = findViewById(R.id.saveButton);
        this.deleteButton = findViewById(R.id.deleteButton);

        this.layoutInflater = LayoutInflater.from(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(event -> finish());

        if (author != null) {
            editName.setText(author.getName());
            editCountry.setText(author.getCountry());

            this.books = new ArrayList<>(author.getBooks()).stream()
                    .map(Book::getName)
                    .sorted(String::compareToIgnoreCase)
                    .collect(Collectors.toList());

            booksLayout.removeAllViews();
            books.forEach(book -> addBookRow(book));

            deleteButton.setVisibility(View.VISIBLE);

            toolbar.setTitle("Edit Author");
        } else {
            deleteButton.setVisibility(View.INVISIBLE);
        }

        editName.addTextChangedListener(textWatcher);
        editCountry.addTextChangedListener(textWatcher);

        // somehow these doesnt work in xml
        saveButton.setEnabled(false);
    }

    public void onSaveButtonClick(View view) {
        final boolean newAuthor = author == null;

        String name = editName.getText().toString().trim();
        String country = editCountry.getText().toString().trim();

        if (newAuthor) {
            author = new Author(name);
            library.addAuthor(author);
        }
        if (!name.equals(author.getName()))
            author.setName(name);
        if (!country.equals(author.getCountry()))
            author.setCountry(country);

        new ArrayList<>(author.getBooks()).forEach(book -> {
            if (!books.contains(book.getName()))
                book.setAuthor(null);
        });

        if (newAuthor) {
            application.getPostman().post(new AuthorAddedEvent(author.getId()));
        } else {
            application.getPostman().post(new AuthorModifiedEvent(author.getId()));
        }

        finish();
    }

    public void onDeleteButtonClick(View view) {
        library.removeAuthor(author);
        new ArrayList<>(author.getBooks()).forEach(book -> book.setAuthor(null));

        application.getPostman().post(new AuthorRemovedEvent(author.getId()));
        finish();
    }

    private void updateState() {
        String name = editName.getText().toString().trim();
        String country = editCountry.getText().toString().trim();

        if (author == null) {
            saveButton.setEnabled(name.length() > 0);
        } else {
            saveButton.setEnabled(name.length() > 0 &&
                    (!name.equals(author.getName())
                     || !country.equals(author.getCountry()))
                     || books.size() != author.getBooks().size());
        }

    }

    private void addBookRow(String book) {
        View row  = layoutInflater.inflate(R.layout.list_item_swipe, booksLayout, false);
        ((TextView) row.findViewById(R.id.label)).setText(book);
        booksLayout.addView(row);

        row.findViewById(R.id.deleteButton).setOnClickListener(v -> {
            booksLayout.removeView(row);
            books.remove(book);
            updateState();
        });
    }

    private final TextWatcher textWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateState();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    };
}
