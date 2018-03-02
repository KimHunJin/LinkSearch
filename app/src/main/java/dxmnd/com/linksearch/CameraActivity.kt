package dxmnd.com.linksearch

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.SensorManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.RelativeLayout
import android.widget.Toast
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.android.synthetic.main.activity_camera.*
import org.opencv.android.*
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


class CameraActivity : AppCompatActivity() {


    companion object {
        val TAG = CameraActivity::class.java.simpleName!!
        val PERMISSION_REQUEST_CODE = 1
        val PERMISSIONS: Array<String> = Array(1, { "android.permission.CAMERA" })
        var sTess: TessBaseAPI? = null

        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("opencv_java3")
            System.loadLibrary("OpenCV")
        }
    }

    private val lang: String = "kor"
    private var dataPath: String = ""

    private var imageInput: Mat? = null
    private var mRectRoi: Rect? = null
    private var mSurfaceRoi: SurfaceView? = null
    private var mSurfaceRoiBorder: SurfaceView? = null
    private var bmpResult: Bitmap ?= null

    private var mRoiWidth: Int = 0
    private var mRoiHeight: Int = 0
    private var mRoiX: Int = 0
    private var mRoiY: Int = 0
    private var mDwScale: Double = 0.0
    private var mDhScale: Double = 0.0

    private var mViewDeco: View? = null
    private var mNUIOption: Int = 0
    private var mRelativeLayoutParams: RelativeLayout.LayoutParams? = null
    private var mMatRoi: Mat? = null
    private var mStartFlag = false

    private var mStrOcrResult: String = ""

    private enum class OrientHomeButton {
        Right, Bottom, Left, Top
    }

    private var currentOrientHomeButton: OrientHomeButton = OrientHomeButton.Right

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!hasPermission(PERMISSIONS)) { //퍼미션 허가를 했었는지 여부를 확인
            requestPermission(PERMISSIONS)//퍼미션 허가안되어 있다면 사용자에게 요청
        } else {
            //이미 사용자에게 퍼미션 허가를 받음.
        }

        tessSetting()
        cameraSetting()
        viewSetting()
        orientationSetting()
        btnClick()
    }

    private fun tessSetting() {
        sTess = TessBaseAPI()
        dataPath = filesDir.toString() + "/tesseract/"
        if (checkFile(File(dataPath + "tessdata/"))) {
            sTess?.init(dataPath, lang)
        }
    }

    private fun checkFile(dir: File): Boolean {
        if (!dir.exists() && dir.mkdirs()) {
            copyFile()
        }
        if (dir.exists()) {
            val dataFilePath = "$dataPath/tessdata/$lang.traineddata"
            val datafile = File(dataFilePath)
            if (!datafile.exists()) {
                copyFile()
            }
        }
        return false
    }

    private fun copyFile() {
        val assetManager: AssetManager = assets

        val inputStream: InputStream = assetManager.open("tessdata/" + lang + ".traineddata")
        val destFile = "$dataPath/$lang.trainneddata"
        val outputStream = FileOutputStream(destFile)

        val buffer = ByteArray(1024)

        var read: Int


        while (true) {
            read = inputStream.read(buffer)
            if (read == -1) {
                break
            }
            outputStream.write(buffer, 0, read)
        }
        outputStream.flush()
        outputStream.close()
        inputStream.close()
    }

    private fun btnClick() {
        btn_capture.setOnClickListener {
            if (!mStartFlag) {
                btn_capture.isEnabled = false
                bmpResult = Bitmap.createBitmap(mMatRoi?.cols()!!, mMatRoi?.rows()!!, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(mMatRoi, bmpResult)
                img_capture.visibility = View.VISIBLE
                img_capture.setImageBitmap(bmpResult)

                if (currentOrientHomeButton !== OrientHomeButton.Right) {
                    when (currentOrientHomeButton) {
                        OrientHomeButton.Bottom -> bmpResult = getRotatedBitmap(bmpResult, 90)
                        OrientHomeButton.Left -> bmpResult = getRotatedBitmap(bmpResult, 180)
                        OrientHomeButton.Top -> bmpResult = getRotatedBitmap(bmpResult, 270)
                        else -> {

                        }
                    }
                }

                bmpResult?.let {
                    AsyncTess().execute(it)
                }
            } else {
                img_capture.setImageBitmap(null)
                txt_ocr_result.text = "없음"
                btn_capture.isEnabled = true
                mStartFlag = false
            }
        }
    }

    @Synchronized
    private fun getRotatedBitmap(cBitmap: Bitmap?, degrees: Int): Bitmap {
        var bitmap = cBitmap
        if (degrees != 0 && bitmap != null) {
            val m = Matrix()
            m.setRotate(degrees.toFloat(), bitmap.width.toFloat() / 2, bitmap.height.toFloat() / 2)
            try {
                val b2 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                //bitmap.recycle(); (일반적으로는 하는게 옳으나, ImageView에 쓰이는 Bitmap은 recycle을 하면 오류가 발생함.)
                if (bitmap != b2) {
                    bitmap = b2
                }
            } catch (ex: OutOfMemoryError) {
                // We have no memory to rotate. Return the original bitmap.
            }

        }

        return bitmap!!
    }


    @SuppressLint("ObsoleteSdkInt")
    private fun viewSetting() {
        mViewDeco = window.decorView
        mNUIOption = window.decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            mNUIOption = mNUIOption or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mNUIOption = mNUIOption or View.SYSTEM_UI_FLAG_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            mNUIOption = mNUIOption or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        mViewDeco?.systemUiVisibility = mNUIOption

    }

    private fun cameraSetting() {
        camera_view.visibility = SurfaceView.VISIBLE
        camera_view.setCameraIndex(0)
        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        camera_view.setCvCameraViewListener(object : CameraBridgeViewBase.CvCameraViewListener2 {
            override fun onCameraViewStarted(width: Int, height: Int) {
            }

            override fun onCameraViewStopped() {
            }

            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
                imageInput = inputFrame?.rgba()!!

                mRoiWidth = (imageInput?.size()!!.width * mDwScale).toInt()
                mRoiHeight = (imageInput?.size()!!.height * mDhScale).toInt()

                mRoiX = ((imageInput?.size()!!.width - mRoiWidth) / 2).toInt()
                mRoiY = ((imageInput?.size()!!.height - mRoiHeight) / 2).toInt()

                mRectRoi = Rect(mRoiX, mRoiY, mRoiWidth, mRoiHeight)

                mMatRoi = imageInput?.submat(mRectRoi)
                Imgproc.cvtColor(mMatRoi, mMatRoi, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.cvtColor(mMatRoi, mMatRoi, Imgproc.COLOR_GRAY2RGBA)
                mMatRoi?.copyTo(imageInput?.submat(mRectRoi))
                return imageInput!!
            }

        })
    }

    private fun orientationSetting() {
        val mOrientEventListener = object : OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {

            override fun onOrientationChanged(arg0: Int) {

                //방향센서값에 따라 화면 요소들 회전

                // 0˚ (portrait)
                if (arg0 >= 315 || arg0 < 45) {
                    rotateViews(270)
                    currentOrientHomeButton = OrientHomeButton.Bottom
                    // 90˚
                } else if (arg0 in 45..134) {
                    rotateViews(180)
                    currentOrientHomeButton = OrientHomeButton.Left
                    // 180˚
                } else if (arg0 in 135..224) {
                    rotateViews(90)
                    currentOrientHomeButton = OrientHomeButton.Top
                    // 270˚ (landscape)
                } else {
                    rotateViews(0)
                    currentOrientHomeButton = OrientHomeButton.Right
                }


                //ROI 선 조정
                mRelativeLayoutParams = android.widget.RelativeLayout.LayoutParams(mRoiWidth + 5, mRoiHeight + 5)
                mRelativeLayoutParams?.setMargins(mRoiX, mRoiY, 0, 0)
                mSurfaceRoiBorder?.layoutParams = mRelativeLayoutParams

                //ROI 영역 조정
                mRelativeLayoutParams = android.widget.RelativeLayout.LayoutParams(mRoiWidth - 5, mRoiHeight - 5)
                mRelativeLayoutParams?.setMargins(mRoiX + 5, mRoiY + 5, 0, 0)
                mSurfaceRoi?.layoutParams = mRelativeLayoutParams

            }
        }
        mOrientEventListener.enable()

        if (!mOrientEventListener.canDetectOrientation()) {
            Toast.makeText(this, "Can't Detect Orientation",
                    Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun rotateViews(degree: Int) {
        btn_history.rotation = degree.toFloat()
        btn_capture.rotation = degree.toFloat()
        txt_ocr_result.rotation = degree.toFloat()

        when (degree) {
            0, 180 -> {
                mDwScale = 1 / 2.toDouble()
                mDhScale = 1 / 2.toDouble()

                mRelativeLayoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                mRelativeLayoutParams?.setMargins(0, convertDpToPixel(20.toFloat()), 0, 0)
                mRelativeLayoutParams?.addRule(RelativeLayout.CENTER_HORIZONTAL)
                txt_ocr_result.layoutParams = mRelativeLayoutParams
            }
            90, 270 -> {
                mDwScale = 1.toDouble() / 4    //h (반대)
                mDhScale = 3.toDouble() / 4    //w

                mRelativeLayoutParams = android.widget.RelativeLayout.LayoutParams(convertDpToPixel(300.toFloat()), ViewGroup.LayoutParams.WRAP_CONTENT)
                mRelativeLayoutParams?.setMargins(convertDpToPixel(15.toFloat()), 0, 0, 0)
                mRelativeLayoutParams?.addRule(RelativeLayout.CENTER_VERTICAL)
                txt_ocr_result.layoutParams = mRelativeLayoutParams
            }
        }
    }

    private fun convertDpToPixel(dp: Float): Int {
        val resources = applicationContext.resources

        val metrics = resources.displayMetrics

        val px = dp * (metrics.densityDpi / 160f)
        return px.toInt()
    }

    private fun hasPermission(permission: Array<String>): Boolean {
        var result = -1
        for (i in 0 until permission.size) {
            result = ContextCompat.checkSelfPermission(applicationContext, permission[i])
        }
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(permission: Array<String>) {
        ActivityCompat.requestPermissions(this, permission, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (!hasPermission(PERMISSIONS)) {
                    Toast.makeText(applicationContext, "CAMERA PERMISSION FAIL", Toast.LENGTH_LONG).show()
                    finish()
                }
                return
            }
        }
    }


    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    // 퍼미션 확인 후 카메라 활성화
                    if (hasPermission(PERMISSIONS))
                        camera_view.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    inner private class AsyncTess : AsyncTask<Bitmap, Int, String>() {

        override fun doInBackground(vararg params: Bitmap?): String? {
            sTess?.setImage(bmpResult)
            return sTess?.utF8Text
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            btn_capture.isEnabled = true
            mStartFlag = true
            Log.e(TAG,"result : $result")
            result?.let {
                mStrOcrResult = it
            }
            txt_ocr_result.text = mStrOcrResult
        }
    }

    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback)
        } else {
            Log.d(TAG, "onResume :: OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    override fun onDestroy() {
        camera_view.disableView()
        super.onDestroy()
    }

    override fun onPause() {
        camera_view.disableView()
        super.onPause()
    }
}
