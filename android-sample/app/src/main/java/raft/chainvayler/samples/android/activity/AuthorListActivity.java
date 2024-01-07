package raft.chainvayler.samples.android.activity;

import android.annotation.SuppressLint;
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
import raft.chainvayler.samples.android.data.Author;
import raft.chainvayler.samples.android.data.Library;
import raft.chainvayler.samples.android.event.AuthorAddedEvent;
import raft.chainvayler.samples.android.event.AuthorModifiedEvent;
import raft.chainvayler.samples.android.event.AuthorRemovedEvent;
import raft.chainvayler.samples.android.event.LibraryListener;
import raft.chainvayler.samples.android.widget.EmptyRecyclerView;

/** Displays list of authors */
public class AuthorListActivity extends AppCompatActivity {

    private LibraryApplication application;
    private AuthorAdapter adapter;

    private EmptyRecyclerView recyclerView;
    private SearchView searchView;

    private Library library;

    private Set<String> countryFilter = new HashSet<>();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_author_list);

        this.application = (LibraryApplication) getApplication();
        this.library = application.getLibrary();
        application.getPostman().addListener(libraryListener);

        this.recyclerView = findViewById(R.id.authors_recyclerView);
        this.adapter = new AuthorAdapter(library);
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
                case R.id.action_filter_by_country:
                    showFilterByCountryDialog();
                    return true;
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
        startActivity(new Intent(this, AuthorActivity.class));
    }

    public void onShareButtonClick(View view) {
        StringBuilder text = new StringBuilder();
        text.append("My fovorite authors:\n\n");
        adapter.authors.forEach(author -> text.append(author.getName() + "\n"));

        Intent sendIntent = new Intent(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, text.toString())
                .putExtra(Intent.EXTRA_TITLE, "My fovorite authors")
                .setType("text/plain");

        startActivity(Intent.createChooser(sendIntent, null));
    }

    private void resetFilters() {
        countryFilter.clear();
    }

    private void showFilterByCountryDialog() {
        String[] countries = library.getCountries().stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList())
                .toArray(new String[0]);
        boolean checkedGenres[] = new boolean[countries.length];

        Set<String> filter = new HashSet<>();

        if (!countryFilter.isEmpty()) {
            for (int i = 0; i < countries.length; i++) {
                if (countryFilter.contains(countries[i])) {
                    checkedGenres[i] = true;
                    filter.add(countries[i]);
                }
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by country")
                .setMultiChoiceItems(countries, checkedGenres, (d, index, checked) -> {
                    if (checked) {
                        filter.add(countries[index]);
                    } else {
                        filter.remove(countries[index]);
                    }
                })
                .setPositiveButton("Filter", (d, id) -> {
                    countryFilter.clear();
                    countryFilter.addAll(filter);
                    adapter.getFilter().filter("");
                })
                .setNegativeButton("Cancel", (d, id) -> {}); // do nothing

        if (!countryFilter.isEmpty())
            builder.setNeutralButton("Clear filter", (d, id) -> {
                countryFilter.clear();
                applyFilter();
            });

        builder.create().show();
    }

    private void applyFilter() {
        adapter.getFilter().filter("");
    }

    private void showDeleteAllDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete all authors?")
                .setMessage("Are you sure? You cannot revert this action!")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Delete", (d, id) -> {
                    library.removeAllAuthors();
                    resetFilters();
                    adapter.createList();
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", (d, id) -> {}); // do nothing

        builder.create().show();
    }

    private class AuthorAdapter extends RecyclerView.Adapter<AuthorViewHolder> implements Filterable {

        private List<Author> authors;

        AuthorAdapter(Library library) {
            createList();
        }

        void createList() {
            this.authors = new ArrayList<>(library.getAuthors());
            this.authors.sort(Comparator.comparing(Author::getName, String::compareToIgnoreCase)); // sort authors alphabetically
        }

        @Override
        public AuthorViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
            View listItem = layoutInflater.inflate(R.layout.list_item_author, parent, false);
            return new AuthorViewHolder(listItem);
        }

        @Override
        public void onBindViewHolder(AuthorViewHolder holder, int position) {
            final Author author = authors.get(position);

            holder.authornameView.setText(author.getName());
            holder.countryView.setText(author.getCountry());
            holder.bookcountView.setText(String.valueOf(author.getBooks().size()));
            holder.booksView.setText(author.getBooks().size() == 1 ? "book" : "books");

            holder.layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(AuthorListActivity.this, AuthorActivity.class);
                    intent.putExtra(LibraryApplication.EXTRA_AUTHOR_ID, author.getId());
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return authors.size();
        }

        public Filter getFilter() {
            return new AuthorSearch();
        }

    }

    private class AuthorSearch extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            String query = constraint.toString().trim().toLowerCase();
            final List<Author> authors;

            if (query.isEmpty()) {
                authors = library.getAuthors().stream()
                        .filter(author -> countryFilter.isEmpty() || countryFilter.contains(author.getCountry()))
                        .sorted(Comparator.comparing(Author::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList());
            } else {
                authors = library.getAuthors().stream()
                        .filter(book -> { return book.getName().toLowerCase().contains(query); })
                        .sorted(Comparator.comparing(Author::getName, String::compareToIgnoreCase))
                        .collect(Collectors.toList());
            }

            FilterResults result = new FilterResults();
            result.values = authors;
            return result;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            adapter.authors = (List<Author>) results.values;
            adapter.notifyDataSetChanged();
        }
    }


    private static class AuthorViewHolder extends RecyclerView.ViewHolder {
        final TextView authornameView, countryView, bookcountView, booksView;
        final View layout;

        AuthorViewHolder(View itemView) {
            super(itemView);
            this.authornameView = itemView.findViewById(R.id.textView_name);
            this.countryView = itemView.findViewById(R.id.textView_country);
            this.bookcountView = itemView.findViewById(R.id.textView_bookCount);
            this.booksView = itemView.findViewById(R.id.textView_books);
            this.layout = itemView.findViewById(R.id.layout);
        }
    }

    private final LibraryListener libraryListener = new LibraryListener.Adapter() {
        @Override
        public void onAuthorAdded(AuthorAddedEvent event) {
            closeSearchView();
            adapter.createList();

            Author author = library.getAuthor(event.authorId);
            adapter.notifyItemInserted(adapter.authors.indexOf(author));
            scrollToAuthor(event.authorId);
        }

        @Override
        public void onAuthorModified(AuthorModifiedEvent event) {
            closeSearchView();
            // if name changes, ordering also changes, so recreate the list
            adapter.createList();
            adapter.notifyDataSetChanged();
            scrollToAuthor(event.authorId);
        }

        @Override
        public void onAuthorRemoved(AuthorRemovedEvent event) {
            closeSearchView();
            Author author = adapter.authors.stream()
                    .filter(a -> event.authorId == a.getId())
                    .findAny()
                    .orElseThrow(NoSuchElementException::new);

            int index = adapter.authors.indexOf(author);
            adapter.createList();
            adapter.notifyItemRemoved(index);
        }

        private void scrollToAuthor(int authorId) {
            Author author = library.getAuthor(authorId);
            int position = adapter.authors.indexOf(author);
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
