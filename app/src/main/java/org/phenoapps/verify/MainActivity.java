package org.phenoapps.verify;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.phenoapps.verify.R;

public class MainActivity extends AppCompatActivity {

    final static private String line_separator = System.getProperty("line.separator");

    private IdEntryDbHelper mDbHelper;

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;

    //database prepared statements
    private SQLiteStatement sqlUpdateNote;
    private SQLiteStatement sqlDeleteId;
    private SQLiteStatement sqlUpdateChecked;
    private SQLiteStatement sqlUpdateUserAndDate;

    private SparseArray<String> mIds;

    //Verify UI variables
    private ActionBarDrawerToggle mDrawerToggle;

    //global variable to track matching order
    private int mMatchingOrder;

    private String mListId;

    //pair mode vars
    private String mPairCol;
    private String mNextPairVal;

    private String mFileName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(org.phenoapps.verify.R.layout.activity_main);

        mIds = new SparseArray<>();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        final View auxInfo = findViewById(R.id.auxScrollView);
        final View auxValue = findViewById(R.id.auxValueView);

        Log.d("camera id", "onCreate: "+R.id.action_camera);

        if (sharedPref.getBoolean(SettingsActivity.AUX_INFO, false)) {
            auxInfo.setVisibility(View.VISIBLE);
            auxValue.setVisibility(View.VISIBLE);

        } else {
            auxInfo.setVisibility(View.GONE);
            auxValue.setVisibility(View.GONE);
        }

        mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {

                if (sharedPreferences.getBoolean(SettingsActivity.AUX_INFO, false)) {
                    auxInfo.setVisibility(View.VISIBLE);
                    auxValue.setVisibility(View.VISIBLE);
                } else {
                    auxInfo.setVisibility(View.GONE);
                    auxValue.setVisibility(View.GONE);
                }
            }
        };

        sharedPref.registerOnSharedPreferenceChangeListener(mPrefListener);

        if (!sharedPref.getBoolean("onlyLoadTutorialOnce", false)) {
            launchIntro();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("onlyLoadTutorialOnce", true);
            editor.apply();
        } else {
            boolean tutorialMode = sharedPref.getBoolean(SettingsActivity.TUTORIAL_MODE, false);

            if (tutorialMode)
                launchIntro();
        }

        mFileName = sharedPref.getString(SettingsActivity.FILE_NAME, "");

        ActivityCompat.requestPermissions(this, VerifyConstants.permissions, VerifyConstants.PERM_REQ);

        mNextPairVal = null;
        mMatchingOrder = 0;
        mPairCol = null;

        initializeUIVariables();

        mDbHelper = new IdEntryDbHelper(this);

        loadSQLToLocal();

        if (mListId != null)
            updateCheckedItems();
    }

    private void copyRawToVerify(File verifyDirectory, String fileName, int rawId) {

        String fieldSampleName = verifyDirectory.getAbsolutePath() + "/" + fileName;
        File fieldSampleFile = new File(fieldSampleName);
        if (!Arrays.asList(verifyDirectory.listFiles()).contains(fieldSampleFile)) {
            try {
                InputStream inputStream = getResources().openRawResource(rawId);
                FileOutputStream foStream =  new FileOutputStream(fieldSampleName);
                byte[] buff = new byte[1024];
                int read = 0;
                try {
                    while ((read = inputStream.read(buff)) > 0) {
                        foStream.write(buff, 0, read);
                    }
                    scanFile(this, fieldSampleFile);
                } finally {
                    inputStream.close();
                    foStream.close();
                }
            } catch (IOException io) {
                io.printStackTrace();
            }
        }
    }

    public static void scanFile(Context ctx, File filePath) {
        MediaScannerConnection.scanFile(ctx, new String[] { filePath.getAbsolutePath()}, null, null);
    }

    private void prepareStatements() {

        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
        try {
            String updateNoteQuery = "UPDATE VERIFY SET note = ? WHERE " + mListId + " = ?";
            sqlUpdateNote = db.compileStatement(updateNoteQuery);

            String deleteIdQuery = "DELETE FROM VERIFY WHERE " + mListId + " = ?";
            sqlDeleteId = db.compileStatement(deleteIdQuery);

            String updateCheckedQuery = "UPDATE VERIFY SET color = 1 WHERE " + mListId + " = ?";
            sqlUpdateChecked = db.compileStatement(updateCheckedQuery);

            String updateUserAndDateQuery =
                    "UPDATE VERIFY SET user = ?, date = ?, scan_count = scan_count + 1 WHERE " + mListId + " = ?";
            sqlUpdateUserAndDate = db.compileStatement(updateUserAndDateQuery);
        } catch(SQLiteException e) {
            e.printStackTrace();
        }
    }

    private void initializeUIVariables() {

        if (getSupportActionBar() != null){
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        final NavigationView nvDrawer = (NavigationView) findViewById(R.id.nvView);

        // Setup drawer view
        setupDrawerContent(nvDrawer);
        setupDrawer();

        final EditText scannerTextView = ((EditText) findViewById(R.id.scannerTextView));
        scannerTextView.setSelectAllOnFocus(true);
        scannerTextView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {

                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_ENTER) {
                        checkScannedItem();
                    }
                }
                return false;
            }
        });

        ListView idTable = ((ListView) findViewById(R.id.idTable));
        idTable.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        idTable.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                scannerTextView.setText(((TextView) view).getText().toString());
                scannerTextView.setSelection(scannerTextView.getText().length());
                scannerTextView.requestFocus();
                scannerTextView.selectAll();
                checkScannedItem();
            }
        });

        idTable.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                //get app settings
                insertNoteIntoDb(((TextView) view).getText().toString());
                return true;
            }
        });

        TextView valueView = (TextView) findViewById(R.id.valueView);
        valueView.setMovementMethod(new ScrollingMovementMethod());

        findViewById(org.phenoapps.verify.R.id.clearButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scannerTextView.setText("");
            }
        });
    }

    private synchronized void checkScannedItem() {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        int scanMode = Integer.valueOf(sharedPref.getString(SettingsActivity.SCAN_MODE_LIST, "-1"));
        boolean displayAux = sharedPref.getBoolean(SettingsActivity.AUX_INFO, true);

        String scannedId = ((EditText) findViewById(org.phenoapps.verify.R.id.scannerTextView))
                .getText().toString();

        if (mIds != null && mIds.size() > 0) {
            //update database
            exertModeFunction(scannedId);

            //view updated database
            SQLiteDatabase db = mDbHelper.getReadableDatabase();

            String table = IdEntryContract.IdEntry.TABLE_NAME;
            String[] selectionArgs = new String[]{scannedId};
            Cursor cursor = db.query(table, null, mListId + "=?", selectionArgs, null, null, null);

            String[] headerTokens = cursor.getColumnNames();
            StringBuilder values = new StringBuilder();
            StringBuilder auxValues = new StringBuilder();
            if (cursor.moveToFirst()) {
                for (String header : headerTokens) {

                    if (!header.equals(mListId)) {

                        final String val = cursor.getString(
                                cursor.getColumnIndexOrThrow(header)
                        );

                        if (header.equals("color") || header.equals("scan_count") || header.equals("date")
                                || header.equals("user") || header.equals("note")) {
                            if (header.equals("color")) continue;
                            else if (header.equals("scan_count")) auxValues.append("Number of scans");
                            else if (header.equals("date")) auxValues.append("Date");
                            else auxValues.append(header);
                            auxValues.append(" : ");
                            if (val != null) auxValues.append(val);
                            auxValues.append(line_separator);
                        } else {
                            values.append(header);
                            values.append(" : ");
                            if (val != null) values.append(val);
                            values.append(line_separator);
                        }
                    }
                }
                cursor.close();
                ((TextView) findViewById(org.phenoapps.verify.R.id.valueView)).setText(values.toString());
                ((TextView) findViewById(R.id.auxValueView)).setText(auxValues.toString());
                ((EditText) findViewById(R.id.scannerTextView)).setText("");
            } else {
                if (scanMode != 2) {
                    ringNotification(false);
                }
            }
        }
    }

    private Boolean checkIdExists(String id) {
        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        final String table = IdEntryContract.IdEntry.TABLE_NAME;
        final String[] selectionArgs = new String[] { id };
        final Cursor cursor = db.query(table, null, mListId + "=?", selectionArgs, null, null, null);

        if (cursor.moveToFirst()) {
            cursor.close();
            return true;
        } else {
            cursor.close();
            return false;
        }
    }

    private synchronized void insertNoteIntoDb(@NonNull final String id) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter a note for the given item.");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = input.getText().toString();
                if (!value.isEmpty()) {

                    final SQLiteDatabase db = mDbHelper.getWritableDatabase();

                    if (sqlUpdateNote != null) {
                        sqlUpdateNote.bindAllArgsAsStrings(new String[]{
                                value, id
                        });
                        sqlUpdateNote.executeUpdateDelete();
                    }
                }
            }
        });

        builder.show();
    }

    private synchronized void exertModeFunction(@NonNull String id) {

        //get app settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        int scanMode = Integer.valueOf(sharedPref.getString(SettingsActivity.SCAN_MODE_LIST, "-1"));

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        if (scanMode == 0 ) { //default mode
            mMatchingOrder = 0;
            ringNotification(checkIdExists(id));

        } else if (scanMode == 1) { //order mode
            final int tableIndex = getTableIndexById(id);

            if (tableIndex != -1) {
                if (mMatchingOrder == tableIndex) {
                    mMatchingOrder++;
                    Toast.makeText(this, "Order matches id: " + id + " at index: " + tableIndex, Toast.LENGTH_SHORT).show();
                    ringNotification(true);
                } else {
                    Toast.makeText(this, "Scanning out of order!", Toast.LENGTH_SHORT).show();
                    ringNotification(false);
                }
            }
        } else if (scanMode == 2) { //filter mode, delete rows with given id

            mMatchingOrder = 0;
            if (sqlDeleteId != null) {
                sqlDeleteId.bindAllArgsAsStrings(new String[]{id});
                sqlDeleteId.executeUpdateDelete();
            }
            updateFilteredArrayAdapter(id);

        } else if (scanMode == 3) { //if color mode, update the db to highlight the item

            mMatchingOrder = 0;
            if (sqlUpdateChecked != null) {
                sqlUpdateChecked.bindAllArgsAsStrings(new String[]{id});
                sqlUpdateChecked.executeUpdateDelete();
            }
        } else if (scanMode == 4) { //pair mode

            mMatchingOrder = 0;

            if (mPairCol != null) {

                //if next pair id is waiting, check if it matches scanned id and reset mode
                if (mNextPairVal != null) {
                    if (mNextPairVal.equals(id)) {
                        ringNotification(true);
                        Toast.makeText(this, "Scanned paired item: " + id, Toast.LENGTH_SHORT).show();
                    }
                    mNextPairVal = null;
                } else { //otherwise query for the current id's pair
                    String table = IdEntryContract.IdEntry.TABLE_NAME;
                    String[] columnsNames = new String[] { mPairCol };
                    String selection = mListId + "=?";
                    String[] selectionArgs = { id };
                    Cursor cursor = db.query(table, columnsNames, selection, selectionArgs, null, null, null);
                    if (cursor.moveToFirst()) {
                        mNextPairVal = cursor.getString(
                                cursor.getColumnIndexOrThrow(mPairCol)
                        );
                    } else mNextPairVal = null;
                    cursor.close();
                }
            }
        }
        //always update user and datetime
        final Calendar c = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault());

        if (sqlUpdateUserAndDate != null) { //no db yet
            String firstName = sharedPref.getString(SettingsActivity.FIRST_NAME, "");
            String lastName = sharedPref.getString(SettingsActivity.LAST_NAME, "");
            sqlUpdateUserAndDate.bindAllArgsAsStrings(new String[]{
                    firstName + " " + lastName,
                    sdf.format(c.getTime()),
                    id
            });
            sqlUpdateUserAndDate.executeUpdateDelete();
        }

        updateCheckedItems();
    }

    private synchronized void updateCheckedItems() {

        final SQLiteDatabase db = mDbHelper.getReadableDatabase();

        //list of ideas to populate and update the view with
        final HashSet<String> ids = new HashSet<>();

        final String table = IdEntryContract.IdEntry.TABLE_NAME;
        final String[] columns = new String[] { mListId };
        final String selection = "color = 1";

        try {
            final Cursor cursor = db.query(table, columns, selection, null, null, null, null);
            if (cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(
                            cursor.getColumnIndexOrThrow(mListId)
                    );

                    ids.add(id);
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (SQLiteException e) {
            e.printStackTrace();
        }
        ListView idTable = (ListView) findViewById(org.phenoapps.verify.R.id.idTable);
        for (int position = 0; position < idTable.getCount(); position++) {

            final String id = (idTable.getItemAtPosition(position)).toString();

            if (ids.contains(id)) {
                idTable.setItemChecked(position, true);
            } else idTable.setItemChecked(position, false);
        }
    }

    private synchronized void loadSQLToLocal() {

        mIds = new SparseArray<>();

        mDbHelper = new IdEntryDbHelper(this);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        mListId = sharedPref.getString(SettingsActivity.LIST_KEY_NAME, null);
        mPairCol = sharedPref.getString(SettingsActivity.PAIR_NAME, null);

        if (mListId != null) {
            prepareStatements();
            loadBarcodes();
            buildListView();
        }
    }

    private void loadBarcodes() {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try {
            final String table = IdEntryContract.IdEntry.TABLE_NAME;
            final Cursor cursor = db.query(table, null, null, null, null, null, null);

            if (cursor.moveToFirst()) {
                do {
                    final String[] headers = cursor.getColumnNames();
                    for (String header : headers) {

                        final String val = cursor.getString(
                                cursor.getColumnIndexOrThrow(header)
                        );

                        if (header.equals(mListId)) {
                            mIds.append(mIds.size(), val);
                        }
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();

        } catch (SQLiteException e) {
            e.printStackTrace();
        }
    }

    private synchronized void askUserExportFileName() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose name for exported file.");
        final EditText input = new EditText(this);

        final Calendar c = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        int lastDot = mFileName.lastIndexOf('.');
        if (lastDot != -1) {
            mFileName = mFileName.substring(0, lastDot);
        }
        input.setText(mFileName + "_" + sdf.format(c.getTime()));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Export", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = input.getText().toString();
                if (!value.isEmpty()) {
                    if (isExternalStorageWritable()) {
                        try {
                            File verifyDirectory = new File(Environment.getExternalStorageDirectory().getPath() + "/Verify");
                            final File output = new File(verifyDirectory, value + ".csv");
                            final FileOutputStream fstream = new FileOutputStream(output);
                            final SQLiteDatabase db = mDbHelper.getReadableDatabase();
                            final String table = IdEntryContract.IdEntry.TABLE_NAME;
                            final Cursor cursor = db.query(table, null, null, null, null, null, null);
                            //final Cursor cursor = db.rawQuery("SElECT * FROM VERIFY", null);

                            //first write header line
                            final String[] headers = cursor.getColumnNames();
                            for (int i = 0; i < headers.length; i++) {
                                if (i != 0) fstream.write(",".getBytes());
                                fstream.write(headers[i].getBytes());
                            }
                            fstream.write(line_separator.getBytes());
                            //populate text file with current database values
                            if (cursor.moveToFirst()) {
                                do {
                                    for (int i = 0; i < headers.length; i++) {
                                        if (i != 0) fstream.write(",".getBytes());
                                        final String val = cursor.getString(
                                                cursor.getColumnIndexOrThrow(headers[i])
                                        );
                                        if (val == null) fstream.write("null".getBytes());
                                        else fstream.write(val.getBytes());
                                    }
                                    fstream.write(line_separator.getBytes());
                                } while (cursor.moveToNext());
                            }

                            cursor.close();
                            fstream.flush();
                            fstream.close();
                            scanFile(MainActivity.this, output);
                            /*MediaScannerConnection.scanFile(MainActivity.this, new String[] {output.toString()}, null, new MediaScannerConnection.OnScanCompletedListener() {
                                @Override
                                public void onScanCompleted(String path, Uri uri) {
                                    Log.v("scan complete", path);
                                }
                            });*/
                        } catch (SQLiteException e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "Error exporting file, is your table empty?", Toast.LENGTH_SHORT).show();
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException io) {
                            io.printStackTrace();
                        }
                    } else {
                        Toast.makeText(MainActivity.this,
                                "External storage not writable.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this,
                            "Must enter a file name.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.show();

    }

    //returns index of table with identifier = id, returns -1 if not found
    private int getTableIndexById(String id) {

        ListView idTable = (ListView) findViewById(org.phenoapps.verify.R.id.idTable);
        final int size = idTable.getAdapter().getCount();
        int ret = -1;
        for (int i = 0; i < size; i++) {
            final String temp = (String) idTable.getAdapter().getItem(i);
            if (temp.equals(id)) {
                ret = i;
                break; //break out of for-loop early
            }
        }

        return ret;
    }

    private void updateFilteredArrayAdapter(String id) {

        ListView idTable = (ListView) findViewById(org.phenoapps.verify.R.id.idTable);
        //update id table array adapter
        final ArrayAdapter<String> updatedAdapter = new ArrayAdapter<>(this, org.phenoapps.verify.R.layout.row);
        final int oldSize = idTable.getAdapter().getCount();

        for (int i = 0; i < oldSize; i++) {
            final String temp = (String) idTable.getAdapter().getItem(i);
            if (!temp.equals(id)) updatedAdapter.add(temp);
        }
        idTable.setAdapter(updatedAdapter);
    }

    private void ringNotification(boolean success) {

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean audioEnabled = sharedPref.getBoolean(SettingsActivity.AUDIO_ENABLED, true);

        if(success) { //ID found
            if(audioEnabled) {
                if (success) {
                    try {
                        int resID = getResources().getIdentifier("plonk", "raw", getPackageName());
                        MediaPlayer chimePlayer = MediaPlayer.create(MainActivity.this, resID);
                        chimePlayer.start();

                        chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                mp.release();
                            }
                        });
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        if(!success) { //ID not found
            ((TextView) findViewById(org.phenoapps.verify.R.id.valueView)).setText("");

            if (audioEnabled) {
                if(!success) {
                    try {
                        int resID = getResources().getIdentifier("error", "raw", getPackageName());
                        MediaPlayer chimePlayer = MediaPlayer.create(MainActivity.this, resID);
                        chimePlayer.start();

                        chimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                mp.release();
                            }
                        });
                    } catch (Exception ignore) {
                    }
                }
            } else {
                if (!success) {
                    Toast.makeText(this, "Scanned ID not found", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    final public boolean onCreateOptionsMenu(Menu m) {

        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(org.phenoapps.verify.R.menu.activity_main_toolbar, m);
        return true;
    }

    @Override
    final public boolean onOptionsItemSelected(MenuItem item) {
        DrawerLayout dl = (DrawerLayout) findViewById(R.id.drawer_layout);
        int action_camera = R.id.action_camera;
        int action_compare = R.id.action_compare;
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        Log.d("debug switch", "onOptionsItemSelected: " + item.getItemId());
//        switch (item.getItemId()) {
//            case android.R.id.home:
//                dl.openDrawer(GravityCompat.START);
//                break;
//            case action_camera:
//                final Intent cameraIntent = new Intent(this, ScanActivity.class);
//                startActivityForResult(cameraIntent, VerifyConstants.CAMERA_INTENT_REQ);
//                break;
//            case 1000030:
//                final Intent compareIntent = new Intent(MainActivity.this, CompareActivity.class);
//                runOnUiThread(new Runnable() {
//                    @Override public void run() {
//                        startActivity(compareIntent);
//                    }
//                });
//                break;
//            default:
//                return super.onOptionsItemSelected(item);
//        }

        if (item.getItemId() == android.R.id.home){
            dl.openDrawer(GravityCompat.START);
        }
        else if(item.getItemId() == action_camera){
            final Intent cameraIntent = new Intent(this, ScanActivity.class);
            startActivityForResult(cameraIntent, VerifyConstants.CAMERA_INTENT_REQ);
        }
        else if(item.getItemId() == action_compare){
            final Intent compareIntent = new Intent(MainActivity.this, CompareActivity.class);
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    startActivity(compareIntent);
                }
            });
        }
        else{
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    final protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_OK) {

            if (intent != null) {
                switch (requestCode) {
                    case VerifyConstants.DEFAULT_CONTENT_REQ:
                        Intent i = new Intent(this, LoaderDBActivity.class);
                        i.setData(intent.getData());
                        startActivityForResult(i, VerifyConstants.LOADER_INTENT_REQ);
                        break;
                    case VerifyConstants.LOADER_INTENT_REQ:

                        mListId = null;
                        mPairCol = null;
                        mFileName = "";

                        if (intent.hasExtra(VerifyConstants.FILE_NAME))
                            mFileName = intent.getStringExtra(VerifyConstants.FILE_NAME);
                        if (intent.hasExtra(VerifyConstants.LIST_ID_EXTRA))
                            mListId = intent.getStringExtra(VerifyConstants.LIST_ID_EXTRA);
                        if (intent.hasExtra(VerifyConstants.PAIR_COL_EXTRA))
                            mPairCol = intent.getStringExtra(VerifyConstants.PAIR_COL_EXTRA);

                        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        final SharedPreferences.Editor editor = sharedPref.edit();

                        int scanMode = Integer.valueOf(sharedPref.getString(SettingsActivity.SCAN_MODE_LIST, "-1"));

                        if (mPairCol != null) {
                            editor.putBoolean(SettingsActivity.DISABLE_PAIR, false);
                            if (scanMode != 4) showPairDialog();
                        } else {
                            editor.putBoolean(SettingsActivity.DISABLE_PAIR, true);
                        }

                        if (mPairCol == null && scanMode == 4) {
                            editor.putString(SettingsActivity.SCAN_MODE_LIST, "0");
                            Toast.makeText(this,
                                    "Switching to default mode, no pair ID found.",
                                    Toast.LENGTH_SHORT).show();
                        }
                        editor.putString(SettingsActivity.FILE_NAME, mFileName);
                        editor.putString(SettingsActivity.PAIR_NAME, mPairCol);
                        editor.putString(SettingsActivity.LIST_KEY_NAME, mListId);
                        editor.apply();

                        clearListView();
                        loadSQLToLocal();
                        updateCheckedItems();
                        break;
                }

                if (intent.hasExtra(VerifyConstants.CAMERA_RETURN_ID)) {
                    ((EditText) findViewById(org.phenoapps.verify.R.id.scannerTextView))
                            .setText(intent.getStringExtra(VerifyConstants.CAMERA_RETURN_ID));
                    checkScannedItem();
                }
            }
        }
    }

    private void buildListView() {

        ListView idTable = (ListView) findViewById(org.phenoapps.verify.R.id.idTable);
        ArrayAdapter<String> idAdapter =
                new ArrayAdapter<>(this, org.phenoapps.verify.R.layout.row);
        int size = mIds.size();
        for (int i = 0; i < size; i++) {
            idAdapter.add(this.mIds.get(this.mIds.keyAt(i)));
        }
        idTable.setAdapter(idAdapter);
    }

    private void clearListView() {

        ListView idTable = (ListView) findViewById(org.phenoapps.verify.R.id.idTable);
        final ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, org.phenoapps.verify.R.layout.row);

        idTable.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    private void setupDrawer() {

        DrawerLayout dl = (DrawerLayout) findViewById(org.phenoapps.verify.R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, dl,
                org.phenoapps.verify.R.string.drawer_open, org.phenoapps.verify.R.string.drawer_close) {

            public void onDrawerOpened(View drawerView) {
                View view = MainActivity.this.getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }

            public void onDrawerClosed(View view) {
            }

        };

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        dl.addDrawerListener(mDrawerToggle);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                        selectDrawerItem(menuItem);
                        return true;
                    }
                });
    }

    private void selectDrawerItem(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        // constants like id in R class are no longer final, thus can't use switch here
//        switch (menuItem.getItemId()) {
//
//            case org.phenoapps.verify.R.id.nav_import:
//                final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
//                final int scanMode = Integer.valueOf(sharedPref.getString(SettingsActivity.SCAN_MODE_LIST, "-1"));
//
//                final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
//                i.setType("*/*");
//                startActivityForResult(Intent.createChooser(i, "Choose file to import."), VerifyConstants.DEFAULT_CONTENT_REQ);
//
//                break;
//            case org.phenoapps.verify.R.id.nav_settings:
//                final Intent settingsIntent = new Intent(this, SettingsActivity.class);
//                startActivityForResult(settingsIntent, VerifyConstants.SETTINGS_INTENT_REQ);
//                break;
//            case org.phenoapps.verify.R.id.nav_export:
//                askUserExportFileName();
//                break;
//            case org.phenoapps.verify.R.id.nav_about:
//                showAboutDialog();
//                break;
//            case org.phenoapps.verify.R.id.nav_intro:
//                final Intent intro_intent = new Intent(MainActivity.this, IntroActivity.class);
//                runOnUiThread(new Runnable() {
//                    @Override public void run() {
//                        startActivity(intro_intent);
//                    }
//                });
//                break;
//        }

        if (itemId == R.id.nav_import){
            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            final int scanMode = Integer.valueOf(sharedPref.getString(SettingsActivity.SCAN_MODE_LIST, "-1"));

            final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            startActivityForResult(Intent.createChooser(i, "Choose file to import."), VerifyConstants.DEFAULT_CONTENT_REQ);
        } else if (itemId == R.id.nav_settings) {
            final Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(settingsIntent, VerifyConstants.SETTINGS_INTENT_REQ);
        } else if (itemId == R.id.nav_export) {
            askUserExportFileName();
        } else if (itemId == R.id.nav_about) {

        } else if (itemId == R.id.nav_intro) {
            final Intent intro_intent = new Intent(MainActivity.this, IntroActivity.class);
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    startActivity(intro_intent);
                }
            });
        }
        DrawerLayout dl = (DrawerLayout) findViewById(org.phenoapps.verify.R.id.drawer_layout);
        dl.closeDrawers();
    }

    private void showPairDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pair column selected, would you like to switch to Pair mode?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(SettingsActivity.SCAN_MODE_LIST, "4");
                editor.apply();
            }
        });

        builder.setNegativeButton("No thanks", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        builder.show();
    }

    private void showAboutDialog()
    {
        Log.d("hmmm", "showAboutDialog: yeah");
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        {
            android.view.View personView = this.getLayoutInflater().inflate(
                    org.phenoapps.verify.R.layout.about, new android.widget.LinearLayout(this),
                    false);

            {
                assert personView != null;
                android.widget.TextView versionTextView = (android.widget.TextView)
                        personView.findViewById(org.phenoapps.verify.R.id.tvVersion);
                try
                {
                    android.content.pm.PackageInfo packageInfo =
                            this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
                    assert packageInfo     != null;
                    assert versionTextView != null;
                    versionTextView.setText(this.getResources().getString(
                            org.phenoapps.verify.R.string.versiontitle) +
                            ' ' + packageInfo.versionName);
                }
                catch (android.content.pm.PackageManager.NameNotFoundException e)
                { e.printStackTrace(); }
                versionTextView.setOnClickListener(new android.view.View.OnClickListener()
                {
                    @java.lang.Override
                    public void onClick(android.view.View v)
                    { MainActivity.this.showChangeLog(); }
                });
            }

            builder.setCancelable(true);
            builder.setTitle     (this.getResources().getString(
                    org.phenoapps.verify.R.string.about));
            builder.setView(personView);
        }

        builder.setNegativeButton(
                this.getResources().getString(org.phenoapps.verify.R.string.ok),
                new android.content.DialogInterface.OnClickListener()
                {
                    @java.lang.Override
                    public void onClick(android.content.DialogInterface dialog, int which)
                    {
                        assert dialog != null;
                        dialog.dismiss();
                    }
                });

        builder.show();
    }

    private void showChangeLog() {

    }

    @Override
    final protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    final public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private void launchIntro() {

        new Thread(new Runnable() {
            @Override
            public void run() {

            //  Launch app intro
            final Intent i = new Intent(MainActivity.this, IntroActivity.class);

            runOnUiThread(new Runnable() {
                @Override public void run() {
                    startActivity(i);
                }
            });


            }
        }).start();
    }

    /* Checks if external storage is available for read and write */
    static private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    @Override
    final public void onDestroy() {
        mDbHelper.close();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int resultCode, String[] permissions, int[] granted) {

        boolean externalWriteAccept = false;
        if (resultCode == VerifyConstants.PERM_REQ) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals("android.permission.WRITE_EXTERNAL_STORAGE")) {
                    externalWriteAccept = true;
                }
            }
        }
        if (externalWriteAccept && isExternalStorageWritable()) {
//            File verifyDirectory = new File(Context.getExternalStorageDirectory().getPath() + "/Verify");
            File verifyDirectory = new File(getExternalFilesDir(null), "/Verify");
//            File file = new File(getExternalFilesDir(null), "DemoFile.jpg");
            Log.d("test","onRequestPermissionsResult:"+verifyDirectory.getPath());
            if (!verifyDirectory.isDirectory()) {
                final boolean makeDirsSuccess = verifyDirectory.mkdirs();
                if (!makeDirsSuccess) Log.d("Verify Make Directory", "failed");
                return ;
            }
            copyRawToVerify(verifyDirectory, "field_sample.csv", R.raw.field_sample);
            copyRawToVerify(verifyDirectory, "verify_pair_sample.csv", R.raw.verify_pair_sample);
        }
    }

    @Override
    final public void onPause() {
        super.onPause();
    }

}
