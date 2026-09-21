package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;

/**
 * A tiny live-preview pip of a TerminalSession. Scales the full terminal screen down to fit
 * its view bounds using Canvas.scale(), preserving all colors and cursor state.
 * Call notifyUpdate() from onTextChanged() to keep it live.
 */
public class MiniTerminalPipView extends View {

    private TerminalSession mSession;
    private final TerminalRenderer mRenderer;
    private final Paint mBorderPaint = new Paint();
    private final Paint mBgPaint = new Paint();
    private boolean mIsActive = false;

    // Border colors
    private static final int COLOR_BORDER_ACTIVE  = 0xFFBB86FC;
    private static final int COLOR_BORDER_INACTIVE = 0xFF444444;
    private static final float BORDER_WIDTH = 2.5f;

    public MiniTerminalPipView(Context context) {
        super(context);
        // Font size just needs to give reasonable glyph proportions before scaling.
        mRenderer = new TerminalRenderer(12, Typeface.MONOSPACE);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(BORDER_WIDTH);
        mBorderPaint.setAntiAlias(false);
        mBgPaint.setStyle(Paint.Style.FILL);
    }

    public void setSession(TerminalSession session) {
        mSession = session;
        invalidate();
    }

    public TerminalSession getSession() {
        return mSession;
    }

    public void setActive(boolean active) {
        if (mIsActive == active) return;
        mIsActive = active;
        invalidate();
    }

    /** Call from onTextChanged() — safe from any thread. */
    public void notifyUpdate() {
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TerminalEmulator emulator = (mSession != null) ? mSession.getEmulator() : null;

        if (emulator == null) {
            // Draw placeholder
            mBgPaint.setColor(0xFF1A1A1A);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBgPaint);
        } else {
            // Fill background with terminal's background color
            int bgColor = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND];
            mBgPaint.setColor(bgColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBgPaint);

            float rendererW = mRenderer.getFontWidth() * emulator.mColumns;
            float rendererH = mRenderer.getFontLineSpacing() * emulator.mRows;

            if (rendererW > 0 && rendererH > 0) {
                float scaleX = getWidth()  / rendererW;
                float scaleY = getHeight() / rendererH;
                canvas.save();
                canvas.scale(scaleX, scaleY);
                mRenderer.render(emulator, canvas, 0, -1, -1, -1, -1);
                canvas.restore();
            }
        }

        // Border — accent when active, dim when inactive
        mBorderPaint.setColor(mIsActive ? COLOR_BORDER_ACTIVE : COLOR_BORDER_INACTIVE);
        float half = BORDER_WIDTH / 2f;
        canvas.drawRect(half, half, getWidth() - half, getHeight() - half, mBorderPaint);
    }
}
