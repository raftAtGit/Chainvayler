package raft.chainvayler.samples.android.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import raft.chainvayler.samples.android.LibraryApplication;
import raft.chainvayler.samples.android.R;
import raft.chainvayler.samples.android.data.Author;
import raft.chainvayler.samples.android.data.Book;
import raft.chainvayler.samples.android.data.Library;
import raft.chainvayler.samples.android.event.BookAddedEvent;
import raft.chainvayler.samples.android.event.BookModifiedEvent;
import raft.chainvayler.samples.android.event.BookRemovedEvent;

/** Edit an book or add a new one */
public class BookActivity extends AppCompatActivity {

    private static final int NO_VALUE = -1;

    private Library library;
    private Book book;
    private List<String> genres = new ArrayList<>();

    private EditText editName, editNotes, editNewGenre;
    private AutoCompleteTextView editAuthor;
    private CheckBox checkboxRead, checkboxFavorite;
    private LinearLayout genresLayout;
    private FloatingActionButton saveButton, deleteButton;
    private ImageButton addGenreButton;

    private LibraryApplication application;

    private LayoutInflater layoutInflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        this.application = (LibraryApplication) getApplication();
        this.library = application.getLibrary();

        int bookId = getIntent().getIntExtra(LibraryApplication.EXTRA_BOOK_ID, NO_VALUE);
        if (bookId != NO_VALUE) {
            this.book = library.getBook(bookId);
            assert book != null;
        }

        this.editName = findViewById(R.id.editText_name);
        this.editNotes = findViewById(R.id.editText_notes);
        this.editNewGenre = findViewById(R.id.editText_newGenre);
        this.editAuthor = findViewById(R.id.editText_author);
        this.checkboxRead = findViewById(R.id.checkBox_read);
        this.checkboxFavorite = findViewById(R.id.checkBox_favorite);
        this.genresLayout = findViewById(R.id.linearLayout_genres);
        this.saveButton = findViewById(R.id.saveButton);
        this.deleteButton = findViewById(R.id.deleteButton);
        this.addGenreButton = findViewById(R.id.addGenreButton);

        this.layoutInflater = LayoutInflater.from(this);

        deleteButton.setVisibility(book == null ? View.INVISIBLE : View.VISIBLE);

        List<String> authors = library.getAuthors().stream()
                .map(Author::getName)
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());

        editAuthor.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, authors));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(event -> finish());

        if (book != null) {
            editName.setText(book.getName());
            editNotes.setText(book.getNotes());

            if (book.getAuthor() != null)
                editAuthor.setText(book.getAuthor().getName());

            checkboxRead.setChecked(book.isRead());
            checkboxFavorite.setChecked(book.isFavorite());

            this.genres = new ArrayList<>(book.getGenres());
            genres.sort(String::compareToIgnoreCase);

            genresLayout.removeAllViews();
            genres.forEach(genre -> addGenreRow(genre));

            toolbar.setTitle("Edit Book");
        }

        editName.addTextChangedListener(textWatcher);
        editAuthor.addTextChangedListener(textWatcher);
        editNotes.addTextChangedListener(textWatcher);
        editNewGenre.addTextChangedListener(textWatcher);

        // somehow these doesnt work in xml
        addGenreButton.setEnabled(false);
        saveButton.setEnabled(false);
    }

    public void onSaveButtonClick(View view) {
        String author = editAuthor.getText().toString().trim();

        if (!author.isEmpty() && library.findAuthorByName(author) == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Create author?")
                    .setMessage(String.format("Author \"%s\" not found, create now?", author))
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setPositiveButton("Create", (d, id) -> {
                        library.addAuthor(new Author(author));
                        saveBook();
                    })
                    .setNegativeButton("Cancel", (d, id) -> {}); // do nothing
            builder.create().show();
        } else {
            saveBook();
        }
    }

    private void saveBook() {
        final boolean newBook = (book == null);

        String name = editName.getText().toString().trim();
        String notes = editNotes.getText().toString().trim();
        String authorName = editAuthor.getText().toString().trim();
        boolean read = checkboxRead.isChecked();
        boolean favorite = checkboxFavorite.isChecked();

        Author author = (authorName.isEmpty()) ? null : library.findAuthorByName(authorName);

        if (newBook) {
            book = new Book(name);
            library.addBook(book);
        } else {
            book.setName(name);
        }
        if (!name.equals(book.getName()))
            book.setName(name);
        if (book.getAuthor() != author)
            book.setAuthor(author);
        if (!notes.equals(book.getNotes()))
            book.setNotes(notes);
        if (read != book.isRead())
            book.setRead(read);
        if (favorite != book.isFavorite())
            book.setFavorite(favorite);
        if (!genres.equals(book.getGenres()))
            book.setGenres(genres);

        if (newBook) {
            application.getPostman().post(new BookAddedEvent(book.getId()));
        } else {
            application.getPostman().post(new BookModifiedEvent(book.getId()));
        }

        finish();
    }

    public void onDeleteButtonClick(View view) {
        library.removeBook(book);
        application.getPostman().post(new BookRemovedEvent(book.getId()));
        finish();
    }

    public void onAddGenreButtonClick(View view) {
        String genre = editNewGenre.getText().toString().trim();
        if (genre.length() > 0) {
            genres.add(genre);
            addGenreRow(genre);
            editNewGenre.setText("");
            updateState();
        }
    }

    public void onCheckboxClick(View view) {
        updateState();
    }

    private void updateState() {
        String name = editName.getText().toString().trim();
        String author = editAuthor.getText().toString().trim();
        String notes = editNotes.getText().toString().trim();
        boolean read = checkboxRead.isChecked();
        boolean favorite = checkboxFavorite.isChecked();

        if (book == null) {
            saveButton.setEnabled(!name.isEmpty());
        } else {
            boolean authorModified = (book.getAuthor() == null)
                    ? !author.isEmpty()
                    : !author.equalsIgnoreCase(book.getAuthor().getName());

                    saveButton.setEnabled(!name.isEmpty() &&
                    (authorModified
                    || !name.equals(book.getName())
                    || !notes.equals(book.getNotes())
                    || read != book.isRead())
                    || favorite != book.isFavorite()
                    || !new HashSet<>(genres).equals(book.getGenres()));
        }

        String newGenre = editNewGenre.getText().toString().trim();
        addGenreButton.setEnabled(!newGenre.isEmpty());
    }

    private void addGenreRow(String genre) {
        View row  = layoutInflater.inflate(R.layout.list_item_swipe, genresLayout, false);
        ((TextView) row.findViewById(R.id.label)).setText(genre);
        genresLayout.addView(row);

        row.findViewById(R.id.deleteButton).setOnClickListener(v -> {
            genresLayout.removeView(row);
            genres.remove(genre);
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
