package ma.hissatsalati;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Écran plein affiché pendant l'adhan. Volume + / volume − ou le bouton l'arrêtent. */
public class AdhanActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (!AdhanService.playing) { finish(); return; }
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        String prayer = getIntent().getStringExtra("prayer");
        String time = getIntent().getStringExtra("time");
        if (prayer == null) prayer = "";
        if (time == null) time = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int p = dp(28);
        root.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#10233A"), Color.parseColor("#2B4463")});
        root.setBackground(bg);

        TextView lead = text(getString(R.string.adhan_lead), 20, "#DCE6EF");
        TextView name = text(prayer, 56, "#FFFFFF");
        name.setTypeface(Typeface.DEFAULT_BOLD);
        TextView at = text(time, 34, "#FDE9B5");
        TextView hint = text(getString(R.string.adhan_hint), 16, "#DCE6EF");
        hint.setPadding(0, dp(24), 0, dp(24));

        Button stop = new Button(this);
        stop.setText(getString(R.string.stop_adhan));
        stop.setTextSize(20);
        stop.setAllCaps(false);
        stop.setTextColor(Color.parseColor("#10233A"));
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(Color.parseColor("#FDF6E4"));
        bb.setCornerRadius(dp(14));
        stop.setBackground(bb);
        stop.setPadding(dp(36), dp(14), dp(36), dp(14));
        stop.setOnClickListener(v -> stopAndClose());

        root.addView(lead); root.addView(name); root.addView(at); root.addView(hint); root.addView(stop);
        setContentView(root);
        h.post(watch);
    }

    private TextView text(String s, int sp, String color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void stopAndClose() {
        startService(new Intent(this, AdhanService.class).setAction(AdhanService.ACTION_STOP));
        finish();
    }

    /** dispatchKeyEvent passe avant onKeyDown : on attrape volume + et volume − à coup sûr. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) stopAndClose();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() { h.removeCallbacks(watch); super.onDestroy(); }
}
