package ma.hissatsalati;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lecture du tableau mensuel à partir d'une photo.
 *
 * Pourquoi ici et pas dans la page : la reconnaissance de caractères doit
 * fonctionner sans connexion, dans un avion comme dans un sous-sol. Le modèle
 * est donc embarqué dans l'application (ML Kit, variante « bundled »), et rien
 * ne sort jamais du téléphone : ni la photo, ni le texte lu.
 *
 * On ne reconnaît que les chiffres latins, pas l'arabe — et c'est suffisant :
 * le tableau des Habous écrit ses heures en chiffres, et la page sait déjà
 * remettre dans l'ordre des colonnes qui se lisent de droite à gauche.
 *
 * Ce qui est renvoyé à la page : une ligne de texte par rangée du tableau,
 * les cases rangées de gauche à droite, séparées par des espaces.
 */
final class Ocr {

    /** Au-delà, on réduit la photo : un appareil de 50 Mpx ferait manquer de mémoire. */
    private static final int MAX_SIDE = 2600;

    interface OnText {
        void onText(String lines);
        void onError(String why);
    }

    private Ocr() {}

    static void read(Context c, Uri uri, OnText cb) {
        InputImage img;
        try {
            Bitmap bmp = load(c, uri);
            if (bmp == null) { cb.onError("image"); return; }
            img = InputImage.fromBitmap(bmp, rotation(c, uri));
        } catch (Throwable e) {
            cb.onError("image");
            return;
        }
        final TextRecognizer r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        r.process(img)
                .addOnSuccessListener(t -> { try { cb.onText(rows(t)); } finally { r.close(); } })
                .addOnFailureListener(e -> { try { cb.onError("ocr"); } finally { r.close(); } });
    }

    /* ---------------------------------------------------------------- image */

    /** Décode la photo en la réduisant si besoin, pour ne pas saturer la mémoire. */
    private static Bitmap load(Context c, Uri uri) throws Exception {
        BitmapFactory.Options mes = new BitmapFactory.Options();
        mes.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, mes);
        }
        int side = Math.max(mes.outWidth, mes.outHeight);
        if (side <= 0) return null;
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = 1;
        while (side / opt.inSampleSize > MAX_SIDE) opt.inSampleSize *= 2;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, opt);
        }
    }

    /** Orientation notée par l'appareil photo : sans elle, une photo prise de travers est illisible. */
    private static int rotation(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            int o = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Throwable ignored) {}
        return 0;
    }

    /* ---------------------------------------------------- mise en lignes */

    private static final class Cell {
        final String t; final int x, y, h;
        Cell(String t, int x, int y, int h) { this.t = t; this.x = x; this.y = y; this.h = h; }
    }

    /**
     * Remet le texte reconnu en rangées.
     *
     * ML Kit rend des mots isolés avec leur position dans l'image, dans un ordre
     * qui n'a rien à voir avec celui du tableau. On regroupe donc par hauteur
     * (deux mots au même niveau sont sur la même rangée), puis on range chaque
     * rangée de gauche à droite. La tolérance verticale vaut les deux tiers de
     * la hauteur habituelle d'un mot : assez pour absorber une photo un peu de
     * travers, trop peu pour confondre deux rangées voisines.
     */
    private static String rows(Text t) {
        List<Cell> cells = new ArrayList<>();
        for (Text.TextBlock b : t.getTextBlocks())
            for (Text.Line l : b.getLines())
                for (Text.Element e : l.getElements()) {
                    Rect r = e.getBoundingBox();
                    String s = e.getText();
                    if (r == null || s == null || s.trim().isEmpty()) continue;
                    cells.add(new Cell(s.trim(), r.centerX(), r.centerY(), r.height()));
                }
        if (cells.isEmpty()) return "";

        int[] hs = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) hs[i] = cells.get(i).h;
        Arrays.sort(hs);
        int tol = Math.max(4, hs[hs.length / 2] * 2 / 3);

        Collections.sort(cells, (a, b) -> Integer.compare(a.y, b.y));
        StringBuilder out = new StringBuilder();
        List<Cell> row = new ArrayList<>();
        int base = cells.get(0).y;
        for (Cell c : cells) {
            if (c.y - base > tol) { flush(row, out); row.clear(); base = c.y; }
            row.add(c);
        }
        flush(row, out);
        return out.toString();
    }

    private static void flush(List<Cell> row, StringBuilder out) {
        if (row.isEmpty()) return;
        List<Cell> s = new ArrayList<>(row);
        Collections.sort(s, (a, b) -> Integer.compare(a.x, b.x));
        boolean first = true;
        for (Cell c : s) {
            if (!first) out.append(' ');
            out.append(c.t);
            first = false;
        }
        out.append('\n');
    }
}
