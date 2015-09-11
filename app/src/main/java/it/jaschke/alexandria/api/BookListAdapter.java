package it.jaschke.alexandria.api;


import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.graphics.Palette;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Callback;


import it.jaschke.alexandria.R;
import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.DownloadImage;

/**
 * Created by saj on 11/01/15.
 */
public class BookListAdapter extends CursorAdapter {


    public static class ViewHolder {
        public final ImageView bookCover;
        public final TextView bookTitle;
        public final TextView bookSubTitle;

        public ViewHolder(View view) {
            bookCover = (ImageView) view.findViewById(R.id.fullBookCover);
            bookTitle = (TextView) view.findViewById(R.id.listBookTitle);
            bookSubTitle = (TextView) view.findViewById(R.id.listBookSubTitle);
        }
    }

    public BookListAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public void bindView(View view, final Context context, Cursor cursor) {

        final ViewHolder viewHolder = (ViewHolder) view.getTag();

        String imgUrl = cursor.getString(cursor.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));

        Callback callback = new Callback.EmptyCallback() {
            @Override
            public void onSuccess() {
                super.onSuccess();
                //Source: http://jakewharton.com/coercing-picasso-to-play-with-palette/
                Bitmap bitmap = ((BitmapDrawable) viewHolder.bookCover.getDrawable()).getBitmap(); // Ew!
                Palette palette = Palette.from(bitmap).generate();
                viewHolder.bookTitle.setTextColor(palette.getDarkVibrantColor(context.getResources().getColor(R.color.colorBlack)));
            }
        };

        Picasso.with(context)
                .load(imgUrl)
                .into(viewHolder.bookCover, callback);

        String bookTitle = cursor.getString(cursor.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        viewHolder.bookTitle.setText(bookTitle);

        String bookSubTitle = cursor.getString(cursor.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        if(bookSubTitle != null && !bookSubTitle.isEmpty()){
            viewHolder.bookSubTitle.setVisibility(View.VISIBLE);
        }
        viewHolder.bookSubTitle.setText(bookSubTitle);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = LayoutInflater.from(context).inflate(R.layout.book_list_item, parent, false);

        ViewHolder viewHolder = new ViewHolder(view);
        view.setTag(viewHolder);

        return view;
    }
}
