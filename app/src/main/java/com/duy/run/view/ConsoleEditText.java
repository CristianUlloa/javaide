package com.duy.run.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;

import com.duy.ide.setting.JavaPreferences;
import com.duy.run.utils.IntegerQueue;
import com.spartacusrex.spartacuside.util.ByteQueue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Duy on 30-Jul-17.
 */

public class ConsoleEditText extends AppCompatEditText {
    private static final String TAG = "ConsoleEditText";
    private static final int NEW_OUTPUT = 1;
    private static final int NEW_ERR = 2;


    //length of text
    private int mLength = 0;

    //out, in and err stream
    private ConsoleOutputStream outputStream;
    private InputStream inputStream;
    private ConsoleErrorStream errorStream;

    /**
     * uses for input
     */
    private IntegerQueue mInputBuffer = new IntegerQueue(IntegerQueue.QUEUE_SIZE);

    /**
     * buffer for output
     */
    private ByteQueue mStdoutBuffer = new ByteQueue(IntegerQueue.QUEUE_SIZE);

    /**
     * buffer for output
     */
    private ByteQueue mStderrBuffer = new ByteQueue(IntegerQueue.QUEUE_SIZE);
    private AtomicBoolean isRunning = new AtomicBoolean(true);

    //filter input text, block a part of text
    private TextListener mTextListener = new TextListener();
    private EnterListener mEnterListener = new EnterListener();
    private Thread mReadStdoutThread, mReadStderrThread;
    private byte[] mReceiveBuffer;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (!isRunning.get()) {
                return;
            }
            if (msg.what == NEW_OUTPUT) {
                writeStdoutToScreen();
            } else if (msg.what == NEW_ERR) {
                writeStderrToScreen();
            }
        }
    };

    public ConsoleEditText(Context context) {
        super(context);
        init(context);
    }

    public ConsoleEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);

    }

    public ConsoleEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    private void init(Context context) {
        if (!isInEditMode()) {
            JavaPreferences pref = new JavaPreferences(context);
            setTypeface(pref.getConsoleFont());
            setTextSize(pref.getConsoleTextSize());
        }
        setFilters(new InputFilter[]{mTextListener});
        setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        addTextChangedListener(mEnterListener);
        setMaxLines(2000);

        createIOStream();
    }

    private void createIOStream() {
        inputStream = new ConsoleInputStream();
        outputStream = new ConsoleOutputStream();
        errorStream = new ConsoleErrorStream();
        mReceiveBuffer = new byte[4 * 1024];
        mStdoutBuffer = new ByteQueue(4 * 1024);
        mStderrBuffer = new ByteQueue(4 * 1024);
    }

    private void writeStdoutToScreen() {

        int bytesAvailable = mStdoutBuffer.getBytesAvailable();
        int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
        try {
            int bytesRead = mStdoutBuffer.read(mReceiveBuffer, 0, bytesToRead);
//                        mEmulator.append(mReceiveBuffer, 0, bytesRead);
            String out = new String(mReceiveBuffer, 0, bytesRead);
            mLength = mLength + out.length();
            appendStdout(out);
        } catch (InterruptedException e) {
        }
//
//        String out = new String(Character.toChars(read));
//        mLength = mLength + out.length();
//        appendStdout(out);
    }

    private void writeStderrToScreen() {

        int bytesAvailable = mStderrBuffer.getBytesAvailable();
        int bytesToRead = Math.min(bytesAvailable, mReceiveBuffer.length);
        try {
            int bytesRead = mStderrBuffer.read(mReceiveBuffer, 0, bytesToRead);
//                        mEmulator.append(mReceiveBuffer, 0, bytesRead);
            String out = new String(mReceiveBuffer, 0, bytesRead);
            mLength = mLength + out.length();
            appendStderr(out);
        } catch (InterruptedException e) {
        }
//
//        String out = new String(Character.toChars(read));
//        mLength = mLength + out.length();
//        appendStdout(out);
    }

    public ConsoleOutputStream getOutputStream() {
        return outputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public ConsoleErrorStream getErrorStream() {
        return errorStream;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @UiThread
    private void appendStdout(final CharSequence spannableString) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                append(spannableString);
            }
        });
    }

    @UiThread
    private void appendStderr(final CharSequence str) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                SpannableString spannableString = new SpannableString(str);
                spannableString.setSpan(new ForegroundColorSpan(Color.RED), 0, str.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                append(spannableString);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mInputBuffer.write(-1);
    }

    private class EnterListener implements TextWatcher {

        private CharSequence s;
        private int start;
        private int before;
        private int count;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            Log.d(TAG, "onTextChanged() called with: s = [" + s + "], start = [" + start + "], before = [" + before + "], count = [" + count + "]");
            this.s = s;
            this.start = start;
            this.before = before;
            this.count = count;
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (count == 1 && s.charAt(start) == '\n' && start >= mLength) {
                String data = s.toString().substring(mLength);
                Log.d(TAG, "afterTextChanged data = " + data);
                for (char c : data.toCharArray()) {
                    mInputBuffer.write(c);
                }
                mInputBuffer.write(-1); //flush
                mLength = s.length(); //append to console
//                ForegroundColorSpan[] spans = s.getSpans(0, mLength, ForegroundColorSpan.class);
//                for (ForegroundColorSpan span : spans) {
//                    s.removeSpan(span);
//                }
            }
        }
    }

    private class ConsoleOutputStream extends OutputStream {
        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
//            super.write(b, off, len);
            try {
                mStdoutBuffer.write(b, off, len);
                mHandler.sendMessage(mHandler.obtainMessage(NEW_OUTPUT));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void write(int b) throws IOException {
//            mStdoutBuffer.write(b);
        }
    }

    private class ConsoleErrorStream extends OutputStream {
        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            try {
                mStderrBuffer.write(b, off, len);
                mHandler.sendMessage(mHandler.obtainMessage(NEW_ERR));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void write(int b) throws IOException {
//            mStderrBuffer.write(b);
        }
    }

    private class ConsoleInputStream extends InputStream {
        private final Object mLock = new Object();

        @Override
        public int read() throws IOException {
            synchronized (mLock) {
                return mInputBuffer.read();
            }
        }
    }

    private class TextListener implements InputFilter {
        public CharSequence removeStr(CharSequence removeChars, int startPos) {
            Log.d(TAG, "removeStr() called with: removeChars = [" + removeChars + "], startPos = [" + startPos + "]");
            if (startPos < mLength) { //this mean output from console
                return removeChars; //can not remove console output
            } else {
                return "";
            }
        }

        public CharSequence insertStr(CharSequence newChars, int startPos) {
            Log.d(TAG, "insertStr() called with: newChars = [" + newChars + "], startPos = [" + startPos + "]");
            if (startPos < mLength) { //it mean output from console
                return newChars;

            } else { //(startPos >= mLength)
                SpannableString spannableString = new SpannableString(newChars);
                spannableString.setSpan(new ForegroundColorSpan(Color.GREEN), 0,
                        spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return spannableString;

            }
        }

        public CharSequence updateStr(CharSequence oldChars, int startPos, CharSequence newChars) {
            Log.d(TAG, "updateStr() called with: oldChars = [" + oldChars + "], startPos = [" + startPos + "], newChars = [" + newChars + "]");
            if (startPos < mLength) {
                return oldChars; //don't edit

            } else {//if (startPos >= mLength)
                SpannableString spannableString = new SpannableString(newChars);
                spannableString.setSpan(new ForegroundColorSpan(Color.GREEN), 0,
                        spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return spannableString;
            }
        }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            CharSequence returnStr = source;
            String curStr = dest.subSequence(dstart, dend).toString();
            String newStr = source.toString();
            int length = end - start;
            int dlength = dend - dstart;
            if (dlength > 0 && length == 0) {
                // Case: Remove chars, Simple
                returnStr = TextListener.this.removeStr(dest.subSequence(dstart, dend), dstart);
            } else if (length > 0 && dlength == 0) {
                // Case: Insert chars, Simple
                returnStr = TextListener.this.insertStr(source.subSequence(start, end), dstart);
            } else if (curStr.length() > newStr.length()) {
                // Case: Remove string or replace
                if (curStr.startsWith(newStr)) {
                    // Case: Insert chars, by append
                    returnStr = TextUtils.concat(curStr.subSequence(0, newStr.length()), TextListener.this.removeStr(curStr.subSequence(newStr.length(), curStr.length()), dstart + curStr.length()));
                } else {
                    // Case Replace chars.
                    returnStr = TextListener.this.updateStr(curStr, dstart, newStr);
                }
            } else if (curStr.length() < newStr.length()) {
                // Case: Append String or rrepace.
                if (newStr.startsWith(curStr)) {
                    // Addend, Insert
                    returnStr = TextUtils.concat(curStr, TextListener.this.insertStr(newStr.subSequence(curStr.length(), newStr.length()), dstart + curStr.length()));
                } else {
                    returnStr = TextListener.this.updateStr(curStr, dstart, newStr);
                }
            } else {
                // No update os str...
            }

            // If the return value is same as the source values, return the source value.
            return returnStr;
        }
    }
}