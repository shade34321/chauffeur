package net.ophuk.chauffeur;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.core.Scalar;
import org.opencv.core.Core;


import java.util.ArrayList;

public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener {

    // Name of my module so I can find it in the debug logs
    private static final String PKG_NAME = "CHAUFFEUR";

    private CameraBridgeViewBase openCvCameraView;
    private CascadeClassifier cascadeClassifier;
    private Mat grayscaleImage;
    private int width, height, vote;
    double rho, theta;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d(PKG_NAME, "OpenCV not loaded.");
        } else {
            Log.d(PKG_NAME, "OpenCV loaded.");
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            Log.d(PKG_NAME, "Running onManagerConnected");
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.d(PKG_NAME, "LoaderCallbackInterface succeeded.");
                    initializeOpenCVDependencies();
                    break;
                default:
                    Log.d(PKG_NAME, "LoaderCallbackInterface did not succeed.");
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    // Need to get training data and load it
    private void initializeOpenCVDependencies() {
        Log.d(PKG_NAME, "initializeOpenCVDependencies running.");

        try {
            // Open training data and load it in for OpenCV

        } catch (Exception e) {
            Log.e(PKG_NAME, ": OpenCVActivity: Error loading cascaede", e);
        }

        openCvCameraView.enableView();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(PKG_NAME, "onCreate");
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        openCvCameraView = new JavaCameraView(this, -1);
        setContentView(openCvCameraView);
        openCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(PKG_NAME, "onCameraViewStarted.");
        grayscaleImage = new Mat(height, width, CvType.CV_8UC4);

        // Probably need to grab something with the image size here
        this.width = width;
        this.height = height;
        vote = 200;
    }

    @Override
    public void onCameraViewStopped() {

        Log.d(PKG_NAME, "onCameraViewStopped");
    }

    private Mat apply_canny(Mat img) {
        Mat tmp = new Mat();

        //Canny edge detection
        Imgproc.Canny(img, tmp, 50.0, 125.0);
        Imgproc.threshold(tmp, tmp, 128, 255, Imgproc.THRESH_BINARY_INV);

        return tmp;
    }

    private Mat findLines(Mat img, double minLength, double maxGap) {
        Mat lines = new Mat(img.size(), CvType.CV_8U, new Scalar(255));
        vote = 80;

        // Probablistic Hough transformation
        Imgproc.HoughLinesP(img, lines, rho, theta, vote, minLength, maxGap);

        return lines;
    }

    private Mat findLines(Mat img) {
        Mat lines = new Mat();


        if (vote < 1 || lines.rows() > 2) {
            vote = 200;
        } else {
            vote += 25;
        }


        while (lines.cols() < 5 && vote > 0) {
            // Hough Transformation to find the lines.
            Imgproc.HoughLines(img, lines, rho, theta, vote);
            vote -= 25;
        }

        return lines;
    }

    private Mat drawHoughLines(Mat lines, Mat image, Scalar s) {
        Mat result = image.clone();

        for (int i = 0; i < lines.rows(); i++) {
            double data[] = lines.get(i, 0);
            Point x = new Point(data[0], data[1]);
            Point y = new Point(data[2], data[3]);
            Scalar scale = new Scalar(0, 0, 255);
            Imgproc.line(result, x, y, scale, 2);
        }

        return result;
    }

    private Mat drawHoughLines(Mat lines, Mat image) {
        Mat result = image.clone();

        for (int i = 0; i < lines.rows(); i++) {
            double data[] = lines.get(i, 0);
            double rho = data[0];
            double theta = data[1];
            Point pt1, pt2;

            if (theta > 0.09 && theta < 1.48 || theta < 3.14 && theta > 1.66) { // We only care about vertical lines.
                pt1 = new Point(rho / Math.cos(theta), 0);
                pt2 = new Point((rho - result.rows() * Math.sin(theta)) / Math.cos(theta), result.rows());
                Imgproc.line(result, pt1, pt2, new Scalar(0, 0, 255), 2);
                //Imgproc.line(result, pt1, pt2, new Scalar(0, 0, 255), 2);
            }
        }

        return result;
    }

    // Find lanes and return with lines drawn.
    @Override
    public Mat onCameraFrame(Mat aInputFrame) {
        Log.d(PKG_NAME, "onCameraFrame");
        rho = 1.0;
        theta = Math.PI / 180.0;

        Imgproc.cvtColor(aInputFrame, grayscaleImage, Imgproc.COLOR_RGBA2RGB);

        //Canny edge detection
        Mat canny_matrix = new Mat();
        canny_matrix = apply_canny(grayscaleImage);

        //Get the ROI
        //Rect roi = new Rect(0, canny_matrix.cols() / 3, canny_matrix.cols() - 1, canny_matrix.rows() - canny_matrix.cols() / 3);
        //Mat imgROI = canny_matrix.submat(roi);


        Mat hough = findLines(canny_matrix);
        //Mat hough_p = findLines(canny_matrix, 60., 10.);
        //Core.bitwise_and(hough_p, hough, hough_p);
        //Mat hough_p_inv = new Mat(imgROI.size(), CvType.CV_8UC4, new Scalar(0));
        //Imgproc.threshold(hough_p, hough_p_inv, 150, 255, Imgproc.THRESH_BINARY_INV);
        //apply_canny(hough_p_inv);
        //Mat result = findLines(hough_p_inv, 60., 10.);
        Mat result = drawHoughLines(hough, aInputFrame, new Scalar(0));
        //Mat result = drawHoughLines(hough, aInputFrame);

        return result;
    }

    @Override
    public void onResume() {
        Log.d(PKG_NAME, "onResume");
        super.onResume();
        ;
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
    }
}
