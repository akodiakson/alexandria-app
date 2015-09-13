package it.jaschke.alexandria;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;
import java.lang.ref.WeakReference;

import it.jaschke.alexandria.CameraPreview.CameraSourcePreview;
import it.jaschke.alexandria.CameraPreview.GraphicOverlay;
import it.jaschke.alexandria.CameraPreview.VisionApiFocusFix;
import it.jaschke.alexandria.data.AlexandriaContract;
import it.jaschke.alexandria.services.BookService;
import it.jaschke.alexandria.services.DownloadImage;


public class AddBook extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "INTENT_TO_SCAN_ACTIVITY";
    private EditText ean;

    private final int LOADER_ID = 1;
    private View rootView;
    private final String EAN_CONTENT="eanContent";

    private static final int RC_HANDLE_GMS = 9001;

    private BarcodeDetector mBarcodeDetector;


    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
//    private String mCurrentEan;

    //Because the barcode detector is always scanning, we don't want to continuously show new
    //SnackBars each time it detects a frame with a bar code.
    private boolean mShowNetworkAvailabilitySnackBar = true;

    public AddBook(){
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(ean!=null) {
            outState.putString(EAN_CONTENT, ean.getText().toString());
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_add_book, container, false);
        ean = (EditText) rootView.findViewById(R.id.ean);

        ean.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //no need
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //no need
            }

            @Override
            public void afterTextChanged(Editable s) {
                processBarcodeInput(s.toString());
            }
        });

        rootView.findViewById(R.id.auto_focus_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                focusCamera();
            }
        });

        //The "Next" button was pressed
        rootView.findViewById(R.id.save_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ean.setText("");
                showInputHideResult();
                Snackbar
                        .make(rootView, "Book added to collection", Snackbar.LENGTH_SHORT)
                        .show();

            }
        });

        rootView.findViewById(R.id.delete_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent bookIntent = new Intent(getActivity(), BookService.class);
                bookIntent.putExtra(BookService.EAN, ean.getText().toString());//ACE was from editText
                bookIntent.setAction(BookService.DELETE_BOOK);
                getActivity().startService(bookIntent);
                ean.setText("");
                showInputHideResult();
            }
        });

        mPreview = (CameraSourcePreview) rootView.findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) rootView.findViewById(R.id.faceOverlay);

        createCameraSource();

        if(savedInstanceState!=null){
            ean.setText(savedInstanceState.getString(EAN_CONTENT));
            ean.setHint("");
        }



        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mCameraSource != null){
            mCameraSource.release();
        }
    }

    private void processBarcodeInput(String eanToCheck) {

        System.out.println("eanToCheck = " + eanToCheck);
        //catch isbn10 numbers
        if (eanToCheck.length() == 10 && !eanToCheck.startsWith("978")) {
            eanToCheck = "978" + eanToCheck;
        }
        if (eanToCheck.length() < 13) {
            clearFields();
            return;
        }

        //Once we have an ISBN, start a book intent
        if(NetworkUtility.isNetworkConnected(new WeakReference<Context>(getActivity()))){
            Intent bookIntent = new Intent(getActivity(), BookService.class);
            bookIntent.putExtra(BookService.EAN, eanToCheck);
            bookIntent.setAction(BookService.FETCH_BOOK);
            getActivity().startService(bookIntent);
            boolean hasRunningLoaders = AddBook.this.getLoaderManager().hasRunningLoaders();
            if(!hasRunningLoaders){
                AddBook.this.restartLoader();
            }
        } else {
            if(mShowNetworkAvailabilitySnackBar){
                Snackbar
                        .make(ean, getString(R.string.network_not_available), Snackbar.LENGTH_LONG)
                        .show();
            }

            mShowNetworkAvailabilitySnackBar = false;
        }

    }

    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {


        Context context = getActivity();


        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        mBarcodeDetector = new BarcodeDetector.Builder(context).setBarcodeFormats(Barcode.EAN_13 | Barcode.ISBN).build();

        mBarcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {

            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                SparseArray<Barcode> detectedItems = detections.getDetectedItems();
                int size = detectedItems.size();
                if (size != 0) {
                    int key = detectedItems.keyAt(0);
                    final Barcode barcode = detectedItems.get(key);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //This triggers the TextWatcher callback
                            ean.setText(barcode.rawValue);
                        }
                    });
                }
            }
        });

        if (!mBarcodeDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        mCameraSource = new CameraSource.Builder(getActivity(), mBarcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(CameraSourcePreview.PREVIEW_FRAME_DIMENSION, CameraSourcePreview.PREVIEW_FRAME_DIMENSION)
                .build();

    }

    private void focusCamera(){
        VisionApiFocusFix.cameraFocus(mCameraSource, Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }


    @Override
    public void onResume() {
        super.onResume();
        mShowNetworkAvailabilitySnackBar = true;
        startCameraSource();
    }

    @Override
    public void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
               getActivity());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(getActivity(), code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private void restartLoader(){
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public android.support.v4.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        System.out.println("AddBook.onCreateLoader");
//        if(mCurrentEan == null || mCurrentEan.isEmpty()){
//            return null;
//        }
        String eanStr= ean.getText().toString();
        if(eanStr.length()==10 && !eanStr.startsWith("978")){
            eanStr="978"+eanStr;
        }
        return new CursorLoader(
                getActivity(),
                AlexandriaContract.BookEntry.buildFullBookUri(Long.parseLong(eanStr)),
                null,
                null,
                null,
                null
        );
    }

    @Override
    public void onLoadFinished(android.support.v4.content.Loader<Cursor> loader, Cursor data) {
        if (!data.moveToFirst()) {
            return;
        }

        System.out.println("onLoadFinished");

        hideInputShowResult();

        String bookTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.TITLE));
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText(bookTitle);

        String bookSubTitle = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.SUBTITLE));
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText(bookSubTitle);

        String authors = data.getString(data.getColumnIndex(AlexandriaContract.AuthorEntry.AUTHOR));
        String[] authorsArr = authors.split(",");
        ((TextView) rootView.findViewById(R.id.authors)).setLines(authorsArr.length);
        ((TextView) rootView.findViewById(R.id.authors)).setText(authors.replace(",","\n"));
        String imgUrl = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.IMAGE_URL));
        if(Patterns.WEB_URL.matcher(imgUrl).matches()){
            new DownloadImage((ImageView) rootView.findViewById(R.id.bookCover)).execute(imgUrl);
            rootView.findViewById(R.id.bookCover).setVisibility(View.VISIBLE);
        }

        String categories = data.getString(data.getColumnIndex(AlexandriaContract.CategoryEntry.CATEGORY));
        ((TextView) rootView.findViewById(R.id.categories)).setText(categories);

        String description = data.getString(data.getColumnIndex(AlexandriaContract.BookEntry.DESC));
        ((TextView) rootView.findViewById(R.id.bookDescription)).setText(description);

        rootView.findViewById(R.id.save_button).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.VISIBLE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        //BUG DISCOVERED: The page title wasn't set here like the other fragments
        activity.setTitle(R.string.scan);
    }

    private void hideInputShowResult() {
        rootView.findViewById(R.id.ean_input_container).setVisibility(View.GONE);
        rootView.findViewById(R.id.ean_search_result_container).setVisibility(View.VISIBLE);
    }

    private void showInputHideResult() {
        rootView.findViewById(R.id.ean_input_container).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.ean_search_result_container).setVisibility(View.GONE);
    }

    @Override
    public void onLoaderReset(android.support.v4.content.Loader<Cursor> loader) {

    }

    private void clearFields(){
        ((TextView) rootView.findViewById(R.id.bookTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookSubTitle)).setText("");
        ((TextView) rootView.findViewById(R.id.bookDescription)).setText("");
        ((TextView) rootView.findViewById(R.id.authors)).setText("");
        ((TextView) rootView.findViewById(R.id.categories)).setText("");
        rootView.findViewById(R.id.bookCover).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.delete_button).setVisibility(View.INVISIBLE);
    }


}
