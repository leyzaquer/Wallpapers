package org.laneby.wallpaperpicker

import android.app.WallpaperManager
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.RectF
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Gallery
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.materialswitch.MaterialSwitch
import org.laneby.wallpaperpicker.databinding.ChooserActivityBinding
import kotlin.math.max
import kotlin.math.min

class WallpaperChooserActivity : ComponentActivity(), AdapterView.OnItemSelectedListener,
    View.OnClickListener {
    private var mGallery: Gallery? = null
    private var mImageView: ImageView? = null
    private var mInfoView: TextView? = null
    private var mIsWallpaperSet = false

    private var mBitmap: Bitmap? = null

    private var mThumbs: ArrayList<Int?>? = null
    private var mImages: ArrayList<Int?>? = null
    private var mLoader: WallpaperLoader? = null
    private var mBottomSheetBehavior: BottomSheetBehavior<*>? = null
    private var mViewBottomPane: View? = null
    private var mPreview: MaterialSwitch? = null


    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enableEdgeToEdge()

        findWallpapers()

        val adapter = ImageAdapter(this)
        val binding = ChooserActivityBinding.inflate(getLayoutInflater())
        setContentView(binding.getRoot())

        binding.fabApplyClose.setOnClickListener {
            if (mIsWallpaperSet) {
                return@setOnClickListener
            }
            finish()
        }

        mGallery = findViewById<View?>(R.id.gallery) as Gallery
        mGallery!!.setAdapter(adapter)
        mGallery!!.setOnItemSelectedListener(this)
        mGallery!!.setCallbackDuringFling(false)
        mGallery!!.setUnselectedAlpha(0.3f)
        mGallery!!.setSpacing(getResources().getDimensionPixelSize(R.dimen.gallery_spacing))

        mPreview = findViewById<View?>(R.id.preview) as MaterialSwitch?
        mPreview!!.setOnClickListener(View.OnClickListener { v: View? -> this.setupBottomSheet(v) })
        mViewBottomPane = findViewById<View>(R.id.galleryLayout)
        mBottomSheetBehavior = BottomSheetBehavior.from<View?>(mViewBottomPane!!)
        val callback: BottomSheetCallback = object : BottomSheetCallback() {
            override fun onStateChanged(p0: View, p1: Int) {
                when (p1) {
                    BottomSheetBehavior.STATE_HIDDEN -> setPreviewChecked(true /* checked */)
                    BottomSheetBehavior.STATE_EXPANDED -> setPreviewChecked(false /* checked */)
                }
            }

            override fun onSlide(p0: View, p1: Float) {
            }
        }
        mBottomSheetBehavior!!.setBottomSheetCallback(callback)
        val state = mBottomSheetBehavior!!.getState()
        callback.onStateChanged(mViewBottomPane!!, state)
        when (state) {
            BottomSheetBehavior.STATE_HIDDEN -> callback.onSlide(mViewBottomPane!!, 0f)
            BottomSheetBehavior.STATE_EXPANDED -> callback.onSlide(mViewBottomPane!!, 1f)
        }
        findViewById<View>(R.id.set).setOnClickListener(this)

        mImageView = findViewById<View?>(R.id.wallpaper) as ImageView
        mInfoView = findViewById<View?>(R.id.info) as TextView
        mPrefs = getSharedPreferences(PREF_KEY, MODE_PRIVATE)
    }

    private fun findWallpapers() {
        mThumbs = ArrayList<Int?>(48)
        mImages = ArrayList<Int?>(48)

        val resources = getResources()
        val packageName = getApplication().getPackageName()

        addWallpapers(resources, packageName, R.array.wallpapers)
        addWallpapers(resources, packageName, R.array.extra_wallpapers)
    }

    private fun addWallpapers(resources: Resources, packageName: String?, list: Int) {
        val extras = resources.getStringArray(list)
        for (extra in extras) {
            val res = resources.getIdentifier(extra, "drawable", packageName)
            if (res != 0) {
                val thumbRes = resources.getIdentifier(
                    extra + "_small",
                    "drawable", packageName
                )

                if (thumbRes != 0) {
                    mThumbs!!.add(thumbRes)
                    mImages!!.add(res)
                }
            }
        }
    }

    private fun setPreviewChecked(checked: Boolean) {
        if (mPreview != null) {
            mPreview!!.setChecked(checked)
            val resId = if (checked)
                R.string.expand_attribution_panel
            else
                R.string.collapse_attribution_panel
            mPreview!!.setContentDescription(getResources().getString(resId))
        }
    }

    private fun setupBottomSheet(v: View?) {
        val checkedSwitch = v as MaterialSwitch
        if (checkedSwitch.isChecked()) {
            mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            mBottomSheetBehavior!!.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }


    override fun onResume() {
        super.onResume()
        mIsWallpaperSet = false
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mLoader != null && mLoader!!.getStatus() != AsyncTask.Status.FINISHED) {
            mLoader!!.cancel(true)
            mLoader = null
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
        val loader = WallpaperLoader()

        if (mLoader != null && mLoader!!.getStatus() != AsyncTask.Status.FINISHED) {
            mLoader!!.cancel()
        }
        mLoader = loader.execute(position) as WallpaperLoader?
    }

    protected fun isScreenLarge(res: Resources): Boolean {
        val config = res.getConfiguration()
        return config.smallestScreenWidthDp >= 720
    }

    protected fun getDefaultWallpaperSize(res: Resources, windowManager: WindowManager): Point {
        // Uses suggested size if available
        val wallpaperManager = WallpaperManager.getInstance(this)
        val suggestedWidth = wallpaperManager.getDesiredMinimumWidth()
        val suggestedHeight = wallpaperManager.getDesiredMinimumHeight()
        if (suggestedWidth != 0 && suggestedHeight != 0) {
            return Point(suggestedWidth, suggestedHeight)
        }

        // Else, calculate desired size from screen size
        val minDims = Point()
        val maxDims = Point()
        windowManager.getDefaultDisplay().getCurrentSizeRange(minDims, maxDims)

        var maxDim = max(maxDims.x, maxDims.y)
        var minDim = max(minDims.x, minDims.y)

        val realSize = Point()
        windowManager.getDefaultDisplay().getRealSize(realSize)
        maxDim = max(realSize.x, realSize.y)
        minDim = min(realSize.x, realSize.y)

        // We need to ensure that there is enough extra space in the wallpaper
        // for the intended
        // parallax effects
        val defaultWidth: Int
        val defaultHeight: Int
        if (isScreenLarge(res)) {
            defaultWidth = (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim)).toInt()
            defaultHeight = maxDim
        } else {
            defaultWidth = max((minDim * WALLPAPER_SCREENS_SPAN).toInt(), maxDim)
            defaultHeight = maxDim
        }
        return Point(defaultWidth, defaultHeight)
    }

    // As a ratio of screen height, the total distance we want the parallax effect to span
    // horizontally
    protected fun wallpaperTravelToScreenWidthRatio(width: Int, height: Int): Float {
        val aspectRatio = width / height.toFloat()

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:
        val ASPECT_RATIO_LANDSCAPE = 16 / 10f
        val ASPECT_RATIO_PORTRAIT = 10 / 16f
        val WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f
        val WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        val x =
            (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                    (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT)
        val y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT
        return x * aspectRatio + y
    }

    protected fun getMaxCropRect(
        inWidth: Int, inHeight: Int, outWidth: Int, outHeight: Int, leftAligned: Boolean
    ): RectF {
        val cropRect = RectF()
        // Get a crop rect that will fit this
        if (inWidth / inHeight.toFloat() > outWidth / outHeight.toFloat()) {
            cropRect.top = 0f
            cropRect.bottom = inHeight.toFloat()
            cropRect.left = (inWidth - (outWidth / outHeight.toFloat()) * inHeight) / 2
            cropRect.right = inWidth - cropRect.left
            if (leftAligned) {
                cropRect.right -= cropRect.left
                cropRect.left = 0f
            }
        } else {
            cropRect.left = 0f
            cropRect.right = inWidth.toFloat()
            cropRect.top = (inHeight - (outHeight / outWidth.toFloat()) * inWidth) / 2
            cropRect.bottom = inHeight - cropRect.top
        }
        return cropRect
    }

    protected fun cropImageAndSetWallpaper(resId: Int) {
        val outSize = getDefaultWallpaperSize(getResources(), getWindowManager())
        val cropTask = BitmapCropTask(
            this, getResources(), resId,
            null, 0, outSize.x, outSize.y, true, false, null
        )
        val inSize = cropTask.getImageBounds()
        val crop = getMaxCropRect(inSize.x, inSize.y, outSize.x, outSize.y, false)
        cropTask.setCropBounds(crop)
        val onEndCrop: Runnable = object : Runnable {
            override fun run() {
                val point = cropTask.getImageBounds()
                this@WallpaperChooserActivity.updateWallpaperDimensions(point.x, point.y)
                setResult(RESULT_OK)
                finish()
            }
        }
        cropTask.setOnEndRunnable(onEndCrop)
        cropTask.execute()
    }

    protected fun updateWallpaperDimensions(width: Int, height: Int) {
        val editor: SharedPreferences.Editor = mPrefs!!.edit()
        if (width != 0 && height != 0) {
            editor.putInt(WALLPAPER_WIDTH_KEY, width)
            editor.putInt(WALLPAPER_HEIGHT_KEY, height)
        } else {
            editor.remove(WALLPAPER_WIDTH_KEY)
            editor.remove(WALLPAPER_HEIGHT_KEY)
        }
        editor.commit()

        suggestWallpaperDimension(
            getResources(),
            getWindowManager(),
            WallpaperManager.getInstance(this)
        )
    }

    fun suggestWallpaperDimension(
        res: Resources,
        windowManager: WindowManager,
        wallpaperManager: WallpaperManager
    ) {
        val defaultWallpaperSize = getDefaultWallpaperSize(res, windowManager)

        object : Thread("suggestWallpaperDimension") {
            override fun run() {
                // If we have saved a wallpaper width/height, use that instead
                val savedWidth: Int = mPrefs!!.getInt(WALLPAPER_WIDTH_KEY, defaultWallpaperSize.x)
                val savedHeight: Int = mPrefs!!.getInt(WALLPAPER_HEIGHT_KEY, defaultWallpaperSize.y)
                wallpaperManager.suggestDesiredDimensions(savedWidth, savedHeight)
            }
        }.start()
    }

    /*
         * When using touch if you tap an image it triggers both the onItemClick and
         * the onTouchEvent causing the wallpaper to be set twice. Ensure we only
         * set the wallpaper once.
         */
    private fun selectWallpaper(position: Int) {
        if (mIsWallpaperSet) {
            return
        }

        mIsWallpaperSet = true
        cropImageAndSetWallpaper(mImages!!.get(position)!!)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    private inner class ImageAdapter(context: WallpaperChooserActivity) : BaseAdapter() {
        private val mLayoutInflater: LayoutInflater

        init {
            mLayoutInflater = context.getLayoutInflater()
        }

        override fun getCount(): Int {
            return mThumbs!!.size
        }

        override fun getItem(position: Int): Any {
            return position
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val image: ImageView

            if (convertView == null) {
                image = mLayoutInflater.inflate(R.layout.wallpaper_item, parent, false) as ImageView
            } else {
                image = convertView as ImageView
            }

            val thumbRes = mThumbs!!.get(position)!!
            image.setImageResource(thumbRes)
            val thumbDrawable = image.getDrawable()
            if (thumbDrawable != null) {
                thumbDrawable.setDither(true)
            } else {
                Log.e(
                    "Paperless System", String.format(
                        "Error decoding thumbnail resId=%d for wallpaper #%d",
                        thumbRes, position
                    )
                )
            }
            return image
        }
    }

    override fun onClick(v: View?) {
        selectWallpaper(mGallery!!.getSelectedItemPosition())
    }

    internal inner class WallpaperLoader : AsyncTask<Int?, Void?, Bitmap?>() {
        var mOptions: BitmapFactory.Options

        init {
            mOptions = BitmapFactory.Options()
            mOptions.inDither = false
            mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        override fun doInBackground(vararg params: Int?): Bitmap? {
            if (isCancelled()) return null
            try {
                return BitmapFactory.decodeResource(
                    getResources(),
                    mImages!!.get(params[0]!!)!!, mOptions
                )
            } catch (e: OutOfMemoryError) {
                return null
            }
        }

        override fun onPostExecute(b: Bitmap?) {
            if (b == null) return

            if (!isCancelled() && !mOptions.mCancel) {
                // Help the GC
                if (mBitmap != null) {
                    mBitmap!!.recycle()
                }

                mInfoView!!.setText(getResources().getStringArray(R.array.info)[mGallery!!.getSelectedItemPosition()])

                val view = mImageView!!
                view.setImageBitmap(b)

                mBitmap = b

                val drawable = view.getDrawable()
                drawable.setFilterBitmap(true)
                drawable.setDither(true)

                view.postInvalidate()

                mLoader = null
            } else {
                b.recycle()
            }
        }

        fun cancel() {
            mOptions.requestCancelDecode()
            super.cancel(true)
        }
    }

    companion object {
        protected const val WALLPAPER_SCREENS_SPAN: Float = 2f
        private const val PREF_KEY = "wallpaper_prefs"
        protected const val WALLPAPER_WIDTH_KEY: String = "wallpaper.width"
        protected const val WALLPAPER_HEIGHT_KEY: String = "wallpaper.height"

        private var mPrefs: SharedPreferences? = null
    }
}