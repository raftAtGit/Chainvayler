package raft.chainvayler.samples.android;

import android.app.Application;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;

import raft.chainvayler.Chainvayler;
import raft.chainvayler.samples.android.data.Author;
import raft.chainvayler.samples.android.data.Book;
import raft.chainvayler.samples.android.data.Library;
import raft.chainvayler.samples.android.event.Postman;

public class LibraryApplication extends Application {

    public static final String EXTRA_BOOK_ID = "raft.chainvayler.samples.android.BOOK_ID";
    public static final String EXTRA_AUTHOR_ID = "raft.chainvayler.samples.android.AUTHOR_ID";

    private static String LOG_TAG = "Chainvayler demo app";

    /** Take a snapshot of @Chained graph after at least this much transactions.
     * This is to accelerate application startup next time. */
    private static final int SNAPSHOT_TRANSACTIONS = 100;

    private Library library;
    private final Postman postman = new Postman();

    @Override
    public void onCreate() {
        super.onCreate();

        String persistDir = getApplicationContext().getFilesDir().getAbsolutePath() + "/persist";
        try {
            this.library = Chainvayler.create(Library.class, persistDir);
            Log.i(LOG_TAG, "Sucessfully created Chainvayler");

            if (!library.isPopulated()) {
                populate();
                library.setPopulated();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to create Chainvayler", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Library getLibrary() {
        return library;
    }

    public Postman getPostman() {
        return postman;
    }

    /** Maybe take a snapshot of @Chained graph, to accelerate app launch time next time */
    public void maybeTakeSnapshot() {
        try {
            if (Chainvayler.getTransactionCount() > Chainvayler.getLastSnapshotVersion() + SNAPSHOT_TRANSACTIONS) {
                Chainvayler.takeSnapshot();
                Chainvayler.deleteRedundantFiles();

                Log.i(LOG_TAG, "Took snapshot and cleaned up files");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to take snapshot", e);
        }
    }

    private void populate() throws Exception {
        JSONArray authors = new JSONArray(getFileContents("authors.json"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        for (int i = 0; i < authors.length(); i++) {
            JSONObject json = authors.getJSONObject(i);

            Author author = new Author(json.getString("name"));
            author.setCountry(json.getString("country"));
            author.setBirth(dateFormat.parse(json.getString("birth")));
            if (json.has("death"))
                author.setDeath(dateFormat.parse(json.getString("death")));

            library.addAuthor(author);
        }
        Log.i(LOG_TAG, "Populated authors from file");

        JSONArray books = new JSONArray(getFileContents("books.json"));
        for (int i = 0; i < books.length(); i++) {
            JSONObject json = books.getJSONObject(i);

            Book book = new Book(json.getString("name"));
            book.setAuthor(library.findAuthorByName(json.getString("author")));

            JSONArray genres = json.getJSONArray("genres");
            for (int j = 0; j < genres.length(); j++) {
                book.addGenre(genres.getString(j));
            }

            library.addBook(book);
        }
        Chainvayler.takeSnapshot();
        Log.i(LOG_TAG, "Populated books from file");
    }

    private String getFileContents(String fileName) throws IOException {
        StringBuilder contents = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(fileName), "UTF-8"))) {
            reader.lines().forEach(s -> contents.append(s));
        }
        return contents.toString();
    }
}
