package com.azkar.duain;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor fileDescriptor;
    private RecyclerView recyclerView;
    private PageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(242, 242, 237));

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        recyclerView.setPadding(dp(8), dp(8), dp(8), dp(16));
        recyclerView.setClipToPadding(false);
        root.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Azkar & Duain");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, dp(16), 0);
        title.setBackgroundColor(Color.rgb(16, 74, 49));

        // A compact top bar with the app title and page count.
        FrameLayout container = new FrameLayout(this);
        container.addView(root);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.TOP);
        container.addView(title, titleParams);
        FrameLayout.LayoutParams listParams = (FrameLayout.LayoutParams) recyclerView.getLayoutParams();
        listParams.topMargin = dp(54);
        recyclerView.setLayoutParams(listParams);
        setContentView(container);

        try {
            File pdfFile = copyPdfFromAssets();
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(fileDescriptor);
            adapter = new PageAdapter(this, renderer);
            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            TextView error = new TextView(this);
            error.setText("PDF open nahi ho saki. App ko dobara install karein.");
            error.setTextColor(Color.BLACK);
            error.setGravity(Gravity.CENTER);
            setContentView(error);
        }
    }

    private File copyPdfFromAssets() throws IOException {
        File output = new File(getFilesDir(), "duain.pdf");
        if (!output.exists() || output.length() == 0) {
            try (InputStream input = getAssets().open("duain.pdf");
                 FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            }
        }
        return output;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) adapter.release();
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (fileDescriptor != null) fileDescriptor.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    static class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        private final Activity activity;
        private final PdfRenderer renderer;

        PageAdapter(Activity activity, PdfRenderer renderer) {
            this.activity = activity;
            this.renderer = renderer;
            setHasStableIds(true);
        }

        @Override public int getItemCount() { return renderer.getPageCount(); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(activity);
            frame.setBackgroundColor(Color.WHITE);
            frame.setElevation(dp(2));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            frame.setLayoutParams(lp);
            return new PageHolder(frame);
        }

        @Override
        public void onBindViewHolder(PageHolder holder, int position) {
            holder.frame.removeAllViews();
            ProgressBar progress = new ProgressBar(activity);
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
            holder.frame.addView(progress, pp);
            holder.frame.post(() -> renderPage(holder, position));
        }

        private void renderPage(PageHolder holder, int position) {
            if (holder.getBindingAdapterPosition() != position || holder.frame.getWidth() == 0) return;
            PdfRenderer.Page page = null;
            try {
                page = renderer.openPage(position);
                int targetWidth = Math.min(holder.frame.getWidth(), 1600);
                float ratio = (float) page.getHeight() / (float) page.getWidth();
                int targetHeight = Math.max(1, (int) (targetWidth * ratio));
                Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                android.widget.ImageView image = new android.widget.ImageView(activity);
                image.setImageBitmap(bitmap);
                image.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
                image.setAdjustViewBounds(true);
                image.setContentDescription("Dua page " + (position + 1));
                holder.frame.removeAllViews();
                holder.frame.addView(image, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } catch (Exception e) {
                TextView error = new TextView(activity);
                error.setText("Page " + (position + 1) + " load nahi ho saka.");
                error.setPadding(dp(16), dp(16), dp(16), dp(16));
                holder.frame.removeAllViews();
                holder.frame.addView(error);
            } finally {
                if (page != null) page.close();
            }
        }

        void release() { /* PdfRenderer is owned and closed by the Activity. */ }
        private int dp(float value) {
            return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
        }
    }

    static class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout frame;
        PageHolder(FrameLayout frame) {
            super(frame);
            this.frame = frame;
        }
    }
}
