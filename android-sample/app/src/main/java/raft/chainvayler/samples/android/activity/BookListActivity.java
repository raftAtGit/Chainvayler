package raft.chainvayler.samples.android.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import raft.chainvayler.samples.android.LibraryApplication;
import raft.chainvayler.samples.android.R;
import raft.chainvayler.samples.android.data.Book;
import raft.chainvayler.samples.android.data.Library;
import raft.chainvayler.samples.android.event.BookAddedEvent;
import raft.chainvayler.samples.android.event.BookModifiedEvent;
import raft.chainvayler.samples.android.event.BookRemovedEvent;
import raft.chainvayler.samples.android.event.LibraryListener;
import raft.chainvayler.samples.android.widget.EmptyRecyclerView;

/** Displays list of books */
public class BookListActivity extends AppCompatActivity {

    private LibraryApplication application;
    private BookAdapter adapter;

    private EmptyRecyclerView recyclerView;
    private SearchView searchView;

    private Library library;

    private Set<String> genreFilter = new HashSet<>();
    private boolean showFavorites = false;
    private boolean showUnreads = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);

        this.application = (LibraryApplication) getApplication();
        this.library = application.getLibrary();
        application.getPostman().addListener(libraryListener);

        this.recyclerView = findViewById(R.id.books_recyclerView);
        this.adapter = new BookAdapter(library);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setEmptyView(findViewById(R.id.empty_view));

        this.searchView = findViewById(R.id.action_search);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                adapter.getFilter().filter(query);
                return true;
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(event -> finish());

        toolbar.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case R.id.action_add:
                    onNewButtonClick(null);
                    return true;
                case R.id.action_filter_by_genre:
                    showFilterByGenreDialog();
                    return true;
                case R.id.action_filter_favorite: {
                    boolean show = !showFavorites;
                    resetFilters();
                    showFavorites = show;
                    applyFilter();
                    return true;
                }
                case R.id.action_filter_unread: {
                    boolean show = !showUnreads;
                    resetFilters();
                    showUnreads = show;
                    applyFilter();
                    return true;
                }
                case R.id.action_reset_filters:
                    resetFilters();
                    applyFilter();
                    return true;
                case R.id.action_delete_all:
                    showDeleteAllDialog();
                    return true;
                default:
                    return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        application.getPostman().removeListener(libraryListener);
    }

    public void onNewButtonClick(View view) {
        startActivity(new Intent(this, BookActivity.class));
    }

    public void onShareButtonClick(View view) {
        StringBuilder text = new StringBuilder();
        text.append("My fovorite books:\n\n");

        adapter.books.forEach(book -> {
            text.append(book.getName());
            if (book.getAuthor() != null)
                text.append(String.format(" (by %s)", book.getAuthor().getName()));
            text.append("\n");
        });

        Intent sendIntent = new Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text.toString())
                .putExtra(Intent.EXTRA_TITLE, "My fovorite books")
                .setType("text/plain");

        startActivity(Intent.createChooser(sendIntent, null));
    }

    private void resetFilters() {
        showFavorites = false;
        showUnreads = false;
        genreFilter.clear();
    }

    private void showFilterByGenreDialog() {
        String[] genres = library.getGenres().stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList())
                .toArray(new String[0]);
        boolean checkedGenres[] = new boolean[genres.length];

        Set<String> filter = new HashSet<>();

        if (!genreFilter.isEmpty()) {
            for (int i = 0; i < genres.length; i++) {
                if (genreFilter.contains(genres[i])) {
                    checkedGenres[i] = true;
                    filter.add(genres[i]);
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by genre")
                .setMultiChoiceItems(genres, checkedGenres, (d, index, checked) -> {
                    if (checked) {
                        filter.add(genres[index]);
                    } else {
                        filter.remove(genres[index]);
                    }
                })
                .setPositiveButton("Filter", (d, id) -> {
                    genreFilter.clear();
                    genreFilter.addAll(filter);
                    adapter.getFilter().filter("");
                })
                .setNegativeButton("Cancel", (d, id) -> {}); // do nothing

        if (!genreFilter.isEmpty())
            builder.setNeutralButton("Clear filter", (d, id) -> {
                genreFilter.clear();
                applyFilter();
            });

        builder.create().show();
    }

    private void applyFilter() {
        adapter.getFilter().filter("");
    }

    private void showDeleteAllDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete all books?")
                .setMessage("Are you sure? You cannot revert this action!")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Delete", (d, id) -> {
                    library.removeAllBooks();
                    resetFilters();
                    adapter.createList();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", (d, id) -> {}); // do nothing

        builder.create().show();
    }

    private class BookAdapter extends RecyclerView.Adapter<BookViewHolder> implements Filterable {
        private List<Book> books;

        BookAdapter(Library library) {
            createList();
        }

        void createList() {
            this.books = new ArrayList<>(library.getBooks());
            this.books.sort(Comparator.comparing(Book::getName, String::compareToIgnoreCase)); // sort books alphabetically
        }

        @Override
        public BookViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
            View listItem = layoutInflater.inflate(R.layout.list_item_book, parent, false);
            return new BookViewHolder(listItem);
        }

        @Override
        public void onBindViewHolder(BookViewHolder holder, int position) {
            final Book book = books.get(position);

            holder.booknameView.setText(book.getName());
            holder.authorView.setText(book.getAuthor() == null ? "" : book.getAuthor().getName());
            holder.favoriteIcon.setVisibility(book.isFavorite() ? View.VISIBLE : View.GONE);
            holder.readIcon.setVisibility(book.isRead() ? View.VISIBLE : View.GONE);

            holder.layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(BookListActivity.this, BookActivity.class);
                    intent.putExtra(LibraryApplication.EXTRA_BOOK_ID, book.getId());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return books.size();
        }

        @Override
        public Filter getFilter() {
            return new BookSearch();
        }
    }

    private class BookSearch extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            String query = constraint.toString().trim().toLowerCase();
            final List<Book> books;

            if (query.isEmpty()) {
                books = library.getBooks().stream()
                        .filter(book -> {
                            return genreFilter.isEmpty()
                                ? (!showFavorites || book.isFavorite()) && (!showUnreads || !book.isRead())
                                : book.getGenres().stream().anyMatch(genre -> genreFilter.contains(genre));
                        })
                        .sorted(Comparator.comparing(Book::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList());
            } else {
                books = library.getBooks().stream()
                        .filter(book -> { return book.getName().toLowerCase().contains(query); })
                        .sorted(Comparator.comparing(Book::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList());
            }

            FilterResults result = new FilterResults();
            result.values = books;
            return result;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            adapter.books = (List<Book>) results.values;
            adapter.notifyDataSetChanged();
        }
    }

    private static class BookViewHolder extends RecyclerView.ViewHolder {
        final TextView booknameView, authorView;
        final View layout, favoriteIcon, readIcon;

        BookViewHolder(View itemView) {
            super(itemView);
            this.booknameView = itemView.findViewById(R.id.booknameView);
            this.authorView = itemView.findViewById(R.id.authorView);
            this.favoriteIcon = itemView.findViewById(R.id.favoriteIcon);
            this.readIcon = itemView.findViewById(R.id.readIcon);
            this.layout = itemView.findViewById(R.id.layout);
        }
    }

    private final LibraryListener libraryListener = new LibraryListener.Adapter() {
        @Override
        public void onBookAdded(BookAddedEvent event) {
            closeSearchView();
            adapter.createList();

            Book book = library.getBook(event.bookId);
            adapter.notifyItemInserted(adapter.books.indexOf(book));
            scrollToBook(event.bookId);
        }

        @Override
        public void onBookModified(BookModifiedEvent event) {
            closeSearchView();
            // if name changes, ordering also changes, so recreate the list
            adapter.createList();
            adapter.notifyDataSetChanged();
            scrollToBook(event.bookId);
        }

        @Override
        public void onBookRemoved(BookRemovedEvent event) {
            closeSearchView();
            Book book = adapter.books.stream()
                    .filter(a -> event.bookId == a.getId())
                    .findAny()
                    .orElseThrow(NoSuchElementException::new);

            int index = adapter.books.indexOf(book);
            adapter.createList();
            adapter.notifyItemRemoved(index);
        }

        private void scrollToBook(int bookId) {
            Book book = library.getBook(bookId);
            int position = adapter.books.indexOf(book);
            recyclerView.scrollToPosition(position);
        }

        private void closeSearchView() {
            if (!searchView.isIconified()) {
                searchView.setQuery("", false);
                searchView.setIconified(true);
            }
        }
    };

}
