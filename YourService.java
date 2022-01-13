package jp.jaxa.iss.kibo.rpc.defaultapk;

import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;
import android.graphics.Bitmap;
import android.util.Log;


import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.opencv.aruco.Aruco;
import org.opencv.aruco.Dictionary;
import org.opencv.core.CvType;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import static android.content.ContentValues.TAG;

/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee
 */

public class YourService extends KiboRpcService {
    @Override
    protected void runPlan1() {
        // astrobee is undocked and the mission starts
        api.startMission();

        // astrobee is undocked and the mission starts
        // move to point A
        Point point1 = new Point(11.21, -10.05, 4.95);
        Quaternion quaternion1 = new Quaternion(0f, 0f, -0.707f, 0.707f);
        api.moveTo(point1, quaternion1, true);


        // QR read
        scanQRImage();
        String QRcontent = scanQRImage();
        float qr_Data[] = qrSplit(QRcontent); // koz x y z
        Log.i("qrSplit Completed", Arrays.toString(qr_Data));

        final int koz_p = (int)qr_Data[0];

        if(koz_p == 7){
            move_pattern7(qr_Data[1],qr_Data[3]);
        }
        else if (koz_p == 5 || koz_p == 6) {
            move_pattern56(qr_Data[1] - 0.05f,qr_Data[3]);
        }
        else {
            move_to(qr_Data[1],qr_Data[2],qr_Data[3], new Quaternion(0, 0, -0.707f, 0.707f));
        }


        api.laserControl(true);
        api.takeSnapshot();
        api.laserControl(false);
        move_toB(koz_p, qr_Data[1], qr_Data[3]);

        boolean cleared;
        do {
            cleared = api.reportMissionCompletion();
        } while (!cleared);


    }

    @Override
    protected void runPlan2() {

    }

    @Override
    protected void runPlan3() {
        // write here your plan 3
    }
    final int LOOP_MAX = 3;
    final int NAV_MAX_WIDTH = 1280;
    final int NAV_MAX_HEIGHT = 960;
    final double[] CAM_MATSIM = {
            567.229305, 0.0, 659.077221,
            0.0, 574.192915, 517.007571,
            0.0, 0.0, 1.0
    };

    final double[] DIST_COEFFSIM = {
            -0.216247, 0.03875, -0.010157, 0.001969, 0.0
    };
    public Rect crop() {
        return new Rect(590, 480, 300, 400);
    }
    // undistort
    public Mat undistort(Mat in) {

        final int width = in.cols();
        final int height = in.rows();

        Log.i("undistort", "Start");

        Mat cam_Mat = new Mat(3, 3, CvType.CV_32FC1);
        Mat dist_coeff = new Mat(1, 5, CvType.CV_32FC1);


        cam_Mat.put(0, 0, CAM_MATSIM);
        dist_coeff.put(0, 0, DIST_COEFFSIM);

        Mat out = new Mat(NAV_MAX_WIDTH, NAV_MAX_HEIGHT, CvType.CV_8UC1);
        Imgproc.undistort(in, out, cam_Mat, dist_coeff);
        Log.d("undistort: ", "Finish undistorting");
        return out;
    }

    // Nav Img
    public BinaryBitmap getNavImg() {
        Log.i(TAG, "Processing img");
        Mat pic = new Mat(api.getMatNavCam(), crop());
        Log.i(TAG, "bMap init");
        Bitmap bMap = Bitmap.createBitmap(pic.width(), pic.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(pic, bMap);
        Log.i(TAG, "intArr");
            int[] intArr = new int[bMap.getWidth() * bMap.getHeight()];
        Log.i(TAG, "getPixels");
        bMap.getPixels(intArr, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());
        Log.i(TAG, "Luminance Source");
        LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArr);
        Log.i(TAG, "out");
        BinaryBitmap out = new BinaryBitmap(new HybridBinarizer(source));
        Log.i(TAG, "Success processing img");
        return out;
}
    // QR Read
    public  String scanQRImage() {
        Log.i("scanQRImage" ,"Start");
        BinaryBitmap bitmap;
        String contents;

        bitmap = getNavImg();

        try {
            com.google.zxing.Result result = new QRCodeReader().decode(bitmap);
            contents = result.getText();
            api.sendDiscoveredQR(contents);
            Log.i("QRscan", "Success!");
            Log.i("QRscan : ","Data = " + contents);
            return contents;

        } catch (Exception e) {
            Log.e("QrScan", "Error decoding barcode", e);
            e.printStackTrace();
        }
        Log.i("QRscan", "Done with failure");
        return null;
    }

    private float[] qrSplit(String QRContent) {
        Log.i("Split QR", "Start");
        String[] multi_contents = QRContent.split(",");

        int pattern = Integer.parseInt(multi_contents[0].substring(5));

        float final_x = Float.parseFloat(multi_contents[1].substring(4));
        float final_y = Float.parseFloat(multi_contents[2].substring(4));
        float final_z = Float.parseFloat(multi_contents[3].substring(4, multi_contents[3].length()-1));
        Log.i("Interpret QR String", "Done");
        return new float[] {pattern, final_x, final_y, final_z};
    }
    // AR Tag


    // MOVING
    private void move_to(double x, double y, double z, Quaternion q) {
        final String TAG = "func_move";
        final Point p = new Point(x, y, z);
        int counter = 0;
        gov.nasa.arc.astrobee.Result result;
        Log.i(TAG, "Start");

        do {
            result = api.moveTo(p, q, true);
            counter++;
        } while (!result.hasSucceeded() && (counter < LOOP_MAX));
        Log.i(TAG, "Done");
    }

    private void move_to(Point p, Quaternion q) {
        final String TAG = "func_move_PP";
        int counter = 0;
        gov.nasa.arc.astrobee.Result result;
        Log.i(TAG, "Start");
        do {
            result = api.moveTo(p, q, true);
            counter++;
        } while (!result.hasSucceeded() && (counter < LOOP_MAX));

        Log.i(TAG, "Done");
    }

    // KOZ Pattern

    final float A_PrimeToTarget = 0.08f;
    final float KIZ_edgeR = 11.55f;
    final float KIZ_edgeL = 10.3f;
    final float KIZ_edgeB = 5.57f;

    private void move_pattern7(float A_PrimeX, float A_PrimeZ) {
        final String TAG = "move_KoZ7";

        final Quaternion q = new Quaternion(0,0,-0.707f,0.707f);
        final float[] target = {A_PrimeX - A_PrimeToTarget, A_PrimeZ - A_PrimeToTarget};
        final float KOZ_edgeT = target[1] - 0.3f;

        final float x = KIZ_edgeR - 0.01f;

        Log.i(TAG, "KOZ_edgeT=" + KOZ_edgeT);

        Log.i(TAG, "target_x=" + target[0]);
        Log.i(TAG, "target_z=" + target[1]);

        Log.i(TAG, "x=" + x);

        move_to(x, -9.8000, KOZ_edgeT, q);
        move_to(x, -9.8000, A_PrimeZ, q);
        move_to(A_PrimeX, -9.8000, A_PrimeZ, q);


    }
    private void move_pattern56(float A_PrimeX, float A_PrimeZ) {
        final String TAG = "move_KoZ56";

        Quaternion q = new Quaternion(0,0,-0.707f,0.707f);

        final float[] target = {A_PrimeX + A_PrimeToTarget, A_PrimeZ - A_PrimeToTarget};

        final float KOZ_edgeL = target[0] - 0.3f;
        final float KOZ_edgeB = target[1] + 0.3f;
        final float KOZ_edgeT = target[1] - 0.3f;

        float x = KOZ_edgeL - 0.16f;
        float z = KOZ_edgeB - 0.15f;

        if (x < KIZ_edgeL) {
            x = KIZ_edgeL + 0.16f;
        }

        if (z > KIZ_edgeB) {
            z = KIZ_edgeB - 0.16f;
        }

        Log.i(TAG, "KOZ_edgeL=" + KOZ_edgeL);
        Log.i(TAG, "KOZ_edgeT=" + KOZ_edgeT);
        Log.i(TAG, "KOZ_edgeB=" + KOZ_edgeB);

        Log.i(TAG, "target_x=" + target[0]);
        Log.i(TAG, "target_z=" + target[1]);

        Log.i(TAG, "x=" + x);
        Log.i(TAG, "z=" + z);

        move_to(x, -9.8000, KOZ_edgeT, q);
        move_to(x, -9.8000, A_PrimeZ, q);
        move_to(A_PrimeX, -9.8000, A_PrimeZ, q);


    }
    private void move_toB(int pattern, float A_PrimeX, float A_PrimeZ) {
        final String TAG = "move_toB";
        final Quaternion q = new Quaternion(0,0,-0.707f,0.707f);

        final Point PointB = new Point(10.6000, -8.0000, 4.5000);

        if (pattern == 2 || pattern == 3 || pattern == 4) {
            move_to(10.6000, -8.6500, PointB.getZ(), q);
            move_to(PointB, q);
        }

        if (pattern == 1|| pattern == 8) {
            final float[] target = {A_PrimeX - A_PrimeToTarget, A_PrimeZ + A_PrimeToTarget};
            final float KOZ_edgeT = target[1] - 0.3f;
            float KOZ_edgeR = target[0] - 0.075f;

            if (pattern == 8) {
                move_to(A_PrimeX, -9.8000, KOZ_edgeT - 0.32, q);
            }
            else {
                move_to(KOZ_edgeR - 0.16, -9.4000, KOZ_edgeT - 0.16, q);
            }

            move_to(10.6000 , -8.6500, PointB.getZ(), q);
            move_to(PointB, q);
        }

        if (pattern == 5 || pattern == 6) {
            move_to(10.6000, -8.6500, A_PrimeZ, q);
            move_to(PointB, q);
        }

        if (pattern == 7) {
            final float x = KIZ_edgeR - 0.01f;

            move_to(x, -9.8000, A_PrimeZ, q);
            move_to(x, -9.0000, PointB.getZ(), q);
            move_to(10.6000, -9.0000, PointB.getZ(), q);
            move_to(PointB, q);
        }

    }
}
