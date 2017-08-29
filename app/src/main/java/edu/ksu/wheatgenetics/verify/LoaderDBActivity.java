package edu.ksu.wheatgenetics.verify;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import static edu.ksu.wheatgenetics.verify.VerifyConstants.CSV_URI;
import static edu.ksu.wheatgenetics.verify.VerifyConstants.DEFAULT_CONTENT_REQ;

public class LoaderDBActivity extends AppCompatActivity {

    private SparseArray<String> _ids, _cols, _checkedIds, _dateIds, _userIds;
    private SparseIntArray _scannedIds;

    private HashSet<String> displayCols;

    private Uri _csvUri;
    private String mDelimiter;

    private String mIdHeader;

    private Button finishButton, doneButton, chooseHeaderButton, choosePairButton;
    private ListView headerList;
    private TextView tutorialText;
    private EditText separatorText;
    private String mHeader;
    private String mPairCol;
    private int mIdHeaderIndex;
    private int mPairColIndex;

    private IdEntryDbHelper mDbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_load_file);

        if(getSupportActionBar() != null){
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        ActivityCompat.requestPermissions(this, VerifyConstants.permissions, VerifyConstants.PERM_REQ);

        mDbHelper = new IdEntryDbHelper(this);

        displayCols = new HashSet<>();

        headerList = ((ListView) findViewById(R.id.headerList));

        headerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        headerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                chooseHeaderButton.setEnabled(true);
                mIdHeaderIndex = position;
                mIdHeader = ((TextView) view).getText().toString();
                tutorialText.setText(R.string.press_continue_tutorial);
            }
        });

        tutorialText = (TextView) findViewById(R.id.tutorialTextView);
        separatorText = (EditText) findViewById(R.id.separatorTextView);

        choosePairButton = (Button) findViewById(R.id.choosePairButton);
        chooseHeaderButton = (Button) findViewById(R.id.chooseHeaderButton);
        doneButton = ((Button) findViewById(R.id.doneButton));
        finishButton = ((Button) findViewById(R.id.finishButton));

        final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "Choose file to import."), VerifyConstants.DEFAULT_CONTENT_REQ);


        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                doneButton.setVisibility(View.GONE);
                separatorText.setVisibility(View.GONE);
                mDelimiter = separatorText.getText().toString();
                if (mDelimiter.isEmpty()) mDelimiter = ",";
                displayHeaderList();
            }
        });

        chooseHeaderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                chooseHeaderButton.setVisibility(View.GONE);

                tutorialText.setText(R.string.choose_pair_button_tutorial);
                choosePairButton.setVisibility(View.VISIBLE);
                choosePairButton.setEnabled(false);
                headerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                        tutorialText.setText(R.string.press_continue_tutorial);
                        choosePairButton.setEnabled(true);
                        mPairCol = ((TextView) view).getText().toString();
                        mPairColIndex = position;
                    }
                });

                displayColsList(true);

            }
        });

        choosePairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                choosePairButton.setVisibility(View.GONE);
                tutorialText.setText(R.string.columns_tutorial);
                finishButton.setVisibility(View.VISIBLE);
                finishButton.setEnabled(false);
                displayColsList(false);
            }
        });

        finishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                tutorialText.setText(R.string.finish_tutorial);

                //create database
                insertColumns();

                //send relative information to main activity
                final String[] displayHeaders = displayCols.toArray(new String[] {});

                if (displayHeaders.length > 0) {
                    String headers = mIdHeader;

                    for (int i = 0; i < displayHeaders.length; i = i + 1) {
                        headers += ",";
                        headers += displayHeaders[i];
                    }
                    //initialize intent
                    final Intent intent = new Intent();
                    intent.putExtra(VerifyConstants.HEADER_LIST_EXTRA, headers);
                    intent.putExtra(VerifyConstants.HEADER_DELIMETER_EXTRA, mDelimiter);
                    intent.putExtra(VerifyConstants.LIST_ID_EXTRA, mIdHeader);
                    intent.putExtra(VerifyConstants.PAIR_COL_EXTRA, mPairCol);

                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });
    }

    private synchronized void insertColumns() {

        final SQLiteDatabase db = mDbHelper.getWritableDatabase();

        db.execSQL("DROP TABLE IF EXISTS VERIFY");

        final StringBuilder dbExecCreate = new StringBuilder();

        dbExecCreate.append("CREATE TABLE VERIFY(" + mIdHeader + " TEXT PRIMARY KEY");

        dbExecCreate.append(", " + mPairCol + " TEXT");
        dbExecCreate.append(", d DATE");
        dbExecCreate.append(", user TEXT");
        dbExecCreate.append(", note TEXT");
        dbExecCreate.append(", s INT DEFAULT 0");
        dbExecCreate.append(", c INT");

        final String[] cols = displayCols.toArray(new String[] {});
        final int colSize = cols.length;
        for (int i = 0; i < colSize; i++) {
            dbExecCreate.append(",");
            dbExecCreate.append(cols[i] + " TEXT");
        }
        dbExecCreate.append(");");

        db.execSQL(dbExecCreate.toString());

        try {
            final InputStream is = getContentResolver().openInputStream(_csvUri);
            if (is != null) {

                if (mDelimiter != null) {
                    final BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    final String[] headers = br.readLine().split(mDelimiter);

                    String temp;
                    while ((temp = br.readLine()) != null) {
                        final ContentValues entry = new ContentValues();
                        final String[] id_line = temp.split(mDelimiter);
                        final int size = id_line.length;
                        if (size != 0 && size <= headers.length) {

                            entry.put(headers[mIdHeaderIndex], id_line[mIdHeaderIndex]);

                            for (int i = 0; i < size; i = i + 1) {

                                if (displayCols.contains(headers[i]) || headers[i].equals(mPairCol)) {
                                    entry.put(headers[i], id_line[i]);
                                }
                            }
                            final long newRowId = db.insert("VERIFY", null, entry);
                        } else Log.d("ROW ERROR", temp);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    private void displayColsList(boolean pairMode) {

        headerList.setVisibility(View.VISIBLE);

        final String[] headers = mHeader.split(mDelimiter);
        if (headers.length > 0 && headers[0] != null) {
            final ArrayAdapter<String> idAdapter =
                    new ArrayAdapter<>(this, R.layout.row);
            for (String h : headers) {
                if (!h.equals(headers[mIdHeaderIndex])) {
                    idAdapter.add(h);
                }
            }
            headerList.setAdapter(idAdapter);
        }

        if (pairMode && mPairCol == null) {
            headerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            headerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    choosePairButton.setEnabled(true);
                    mPairCol = ((TextView) view).getText().toString();
                }
            });
        } else {
            headerList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            headerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    finishButton.setEnabled(true);
                    displayCols.add(((TextView) view).getText().toString());
                }
            });
        }

    }

    private void displayHeaderList() {

        tutorialText.setText(R.string.choose_header_tutorial);
        chooseHeaderButton.setVisibility(View.VISIBLE);
        chooseHeaderButton.setEnabled(false);
        headerList.setVisibility(View.VISIBLE);

        if (mHeader == null) {
            headerList.setAdapter(new ArrayAdapter<String>(this, R.layout.row));
            tutorialText.setText("Error reading file.");
            return;
        }

        final String[] headers = mHeader.split(mDelimiter);
        if (headers.length > 0 && headers[0] != null) {
            final ArrayAdapter<String> idAdapter =
                    new ArrayAdapter<>(this, R.layout.row);
            for (String h : headers) {
                idAdapter.add(h);
            }
            headerList.setAdapter(idAdapter);
        } else {
            headerList.setAdapter(new ArrayAdapter<String>(this, R.layout.row));
            tutorialText.setText("Error reading file.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == DEFAULT_CONTENT_REQ) {
            if (resultCode == RESULT_OK) {
                _csvUri = intent.getData();

                //inner async class to get header line
                new AsyncTask<Uri, Void, String>() {

                    @Override
                    protected String doInBackground(Uri[] params) {

                        if (params.length > 0 && params[0] != null) try {

                            //query file path type
                            String fileType = getPath(params[0]);
                            final String[] pathSplit = fileType.split("\\.");
                            final String fileExtension = pathSplit[pathSplit.length - 1];

                            if (fileExtension.equals("csv")) { //files ending in .csv
                                mDelimiter = ",";
                            } else if (fileExtension.equals("tsv") || fileExtension.equals("txt")) { //fiels ending in .txt
                                mDelimiter = "\t";
                            } else mDelimiter = null; //non-supported file type, display header for user to choose delimiter

                            final InputStream is = getContentResolver().openInputStream(params[0]);
                            if (is != null) {
                                final BufferedReader br = new BufferedReader(new InputStreamReader(is));
                                final String header = br.readLine();
                                br.close();
                                return header;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    protected void onPostExecute(String header) {

                        mHeader = header;

                        if (header == null) {

                        }
                        //if unsupported file type, start delimiter tutorial
                        if (mDelimiter == null) {
                            if (header == null) {
                                tutorialText.setText("Error reading file.");
                            } else {
                                separatorText.setVisibility(View.VISIBLE);
                                doneButton.setVisibility(View.VISIBLE);
                                tutorialText.setText(R.string.choose_separator_tutorial);
                                tutorialText.append(header);
                            }

                        } else { //display header list
                            displayHeaderList();
                        }
                    }

                }.execute(_csvUri);
            } else finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (_csvUri != null)
            outState.putString(CSV_URI, _csvUri.toString());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_OK);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //based on https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
    public String getPath(Uri uri) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            if (DocumentsContract.isDocumentUri(LoaderDBActivity.this, uri)) {

                if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                    final String[] doc =  DocumentsContract.getDocumentId(uri).split(":");
                    final String documentType = doc[0];

                    if ("primary".equalsIgnoreCase(documentType)) {
                        return Environment.getExternalStorageDirectory() + "/" + doc[1];
                    }
                }
                else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    return getDataColumn(LoaderDBActivity.this, contentUri, null, null);
                }
            }
            else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            } else if ("com.estrongs.files".equals(uri.getAuthority())) {
                return uri.getPath();
            }
        }
        return null;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }
}