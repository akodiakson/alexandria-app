package it.jaschke.alexandria.CameraPreview;

import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.Log;

import it.jaschke.alexandria.BusProvider;

/**
 * Created by ace0808 on 9/6/2015.
 */
public class ObtainCameraTask extends AsyncTask<Void, Void, Camera> {
    @Override
    protected Camera doInBackground(Void... params) {
        Camera camera = null;
        try {
            camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
        }catch (Exception ex){
            Log.e("ObtainCameraTask", ex.getMessage());
        }
        return camera;
    }

    @Override
    protected void onPostExecute(Camera camera) {
        BusProvider.getInstance().post(new CameraObtainedEvent(camera));
    }

    public final class CameraObtainedEvent{

        private Camera mCamera;

        public CameraObtainedEvent(Camera mCamera) {
            this.mCamera = mCamera;
        }

        public Camera getmCamera() {
            return mCamera;
        }
    }

}
