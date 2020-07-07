package de.jugl.nandmeasure.util;

import android.content.Context;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CsvWriter {

    private static final String TAG = "CsvWriter";

    /**
     * Name of the file to create.
     */
    private String mFileName;

    /**
     * Application context.
     */
    private Context mContext;

    /**
     * Table header.
     */
    private String[] mCsvHeader;

    /**
     * File output stream.
     */
    private FileOutputStream mFos;

    /**
     * <code>true</code> if the file is open, <code>false</code> otherwise.
     */
    private boolean mOpen;

    /**
     * Creates a new {@link CsvWriter} with no table header.
     *
     * @param prefix File name prefix
     * @param ctx Application context
     */
    public CsvWriter(String prefix, Context ctx) {
        this(prefix, ctx, new String[0]);
    }

    /**
     * Creates a new {@link CsvWriter}.
     *
     * @param prefix File name prefix
     * @param ctx Application context
     * @param csvHeader Table header
     */
    public CsvWriter(String prefix, Context ctx, String[] csvHeader) {
        this.mFileName = prefix + "_" + System.currentTimeMillis() + ".csv";
        this.mContext = ctx;
        this.mCsvHeader = csvHeader;
        this.mOpen = false;
    }

    /**
     * Tries to create a CSV table file in the application directory with the file name <code>[prefix]_[unix_timestamp].csv</code>.
     * If a table header is provided, it is written immediately.
     *
     * @return <code>true</code> if the file was created successfully, <code>false</code> otherwise
     */
    public boolean open() {
        // Can't open file if it was already opened.
        if (this.mOpen) {
            Log.e(TAG, "File already open.");
            return false;
        }

        try {
            this.mFos = this.mContext.openFileOutput(this.mFileName, Context.MODE_PRIVATE);
            this.mOpen = true;

            // Check if file header is written correctly.
            if (this.mCsvHeader.length != 0 && !this.write(this.mCsvHeader)) {
                return false;
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Couldn't open file.", e);
            return false;
        }

        // Mark file as open.
        return true;
    }

    /**
     * Writes a table row to the file. Every object is serialized with {@link String#valueOf(Object)}.
     *
     * @param values Array of values to write
     * @return <code>true</code> if the file was written successfully, <code>false</code> otherwise
     */
    public boolean write(Object[] values) {
        // File can't be written to if it's not open.
        if (!this.mOpen) {
            Log.e(TAG, "Can't write to unopened file.");
            return false;
        }

        // String.join() is only available at API level 26, so we have to do it this way.
        StringBuilder line = new StringBuilder();

        for (Object o : values) {
            line.append(",").append(o);
        }

        try {
            // Append a newline and remove the first comma.
            this.mFos.write(line.append("\n").substring(1).getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Couldn't write line.", e);
            return false;
        }

        return true;
    }

    /**
     * Tries to close the file.
     *
     * @return <code>true</code> if the file was closed successfully, <code>false</code> otherwise
     */
    public boolean close() {
        // File cannot be closed if it's not open.
        if (!this.mOpen) {
            Log.e(TAG, "Can't close unopened file.");
            return false;
        }

        try {
            this.mFos.close();
        } catch (IOException e) {
            Log.e(TAG, "Couldn't close file.", e);
            return false;
        }

        // Mark file as closed.
        this.mOpen = false;
        return true;
    }

}
