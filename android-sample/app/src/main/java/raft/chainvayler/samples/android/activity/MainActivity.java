package raft.chainvayler.samples.android.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import raft.chainvayler.samples.android.LibraryApplication;
import raft.chainvayler.samples.android.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ((LibraryApplication) getApplication()).maybeTakeSnapshot();
    }

    public void onInfoButtonClick(View view) {
        startActivity(new Intent(this, InfoActivity.class));
    }

    public void onBooksButtonClick(View view) {
        startActivity(new Intent(this, BookListActivity.class));
    }

    public void onAuthorsButtonClick(View view) {
        startActivity(new Intent(this, AuthorListActivity.class));
    }
}
