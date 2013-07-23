package com.macaronics.cclw;

import static javax.microedition.khronos.egl.EGL10.*;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.Log;

public class PixelBuffer
{
    final static String TAG = "PixelBuffer";
    final static boolean LIST_CONFIGS = true;

    GLSurfaceView.Renderer mRenderer; // borrow this interface
    int mWidth, mHeight;
    Bitmap mBitmap;
        
    EGL10 mEGL; 
    EGLDisplay mEGLDisplay;
    EGLConfig[] mEGLConfigs;
    EGLConfig mEGLConfig;
    EGLContext mEGLContext;
    EGLSurface mEGLSurface;
    GL10 mGL;

    EGLConfigChooser mEGLConfigChooser;
    int mEGLContextClientVersion;
    
    String mThreadOwner;
    private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static int EGL_LARGEST_PBUFFER        = 0x3058;
    private static int EGL_OPENGL_ES2_BIT          = 4;


    public PixelBuffer(int width, int height) {
        mWidth = width;
        mHeight = height;
        mEGLContextClientVersion =2;
                
        int[] version = new int[2];
        int[] attribList = new int[] {
            EGL_WIDTH,              mWidth,
            EGL_HEIGHT,             mHeight,
            EGL_LARGEST_PBUFFER,    1,
            EGL_NONE
        };

        int [] context_attribList =new int[] {
            EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion, 
            EGL_NONE
        };
                
        // No error checking performed, minimum required code to elucidate logic
        mEGL = (EGL10) EGLContext.getEGL();
        mEGLDisplay = mEGL.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        mEGL.eglInitialize(mEGLDisplay, version);
        Log.i("cclw", "eglInitialized, version ="+version[0]+"."+version[1]);
        Log.i("cclw", "EGL Extension : "+mEGL.eglQueryString(mEGLDisplay, EGL_EXTENSIONS));

        mEGLConfigChooser =new SimpleEGLConfigChooser(8, 8, 8, 0, true);
        mEGLConfig =mEGLConfigChooser.chooseConfig(mEGL, mEGLDisplay);
        if (mEGLConfig ==null)
        {
            mEGLConfigChooser =new SimpleEGLConfigChooser(5, 8, 5, 0, true);
            mEGLConfig =mEGLConfigChooser.chooseConfig(mEGL, mEGLDisplay);
        }
        
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, mEGLConfig, EGL_NO_CONTEXT, context_attribList);
        mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, attribList);
        mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
        mGL = (GL10) mEGLContext.getGL();
        
        // Record thread owner of OpenGL context
        mThreadOwner = Thread.currentThread().getName();
        Log.i("cclw", "PixelBuffer created. mThreadOwner ="+mThreadOwner);

    }

    public void dispose()
    {
        if (mEGLDisplay !=EGL_NO_DISPLAY)
        {
            mEGL.eglMakeCurrent(mEGLDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (mEGLContext !=EGL_NO_CONTEXT)
                mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);

            if (mEGLSurface !=EGL_NO_SURFACE)
                mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEGL.eglTerminate(mEGLDisplay);
        }
    }
    
    public void setRenderer(GLSurfaceView.Renderer renderer) {
        mRenderer = renderer;
        
        // Does this thread own the OpenGL context?
        if (!Thread.currentThread().getName().equals(mThreadOwner)) {
            Log.e(TAG, "setRenderer: This thread does not own the OpenGL context.");
            return;
        }
        
        // Call the renderer initialization routines
        mRenderer.onSurfaceCreated(mGL, mEGLConfig);
        mRenderer.onSurfaceChanged(mGL, mWidth, mHeight);
    }

    public void drawFrame()
    {
        makeCurrent();
        mRenderer.onDrawFrame(mGL);
    }

    public void makeCurrent()
    {
        // Do we have a renderer?
        if (mRenderer == null) {
            Log.e(TAG, "getBitmap: Renderer was not set.");
            return;
        }
        
        // Does this thread own the OpenGL context?
        if (!Thread.currentThread().getName().equals(mThreadOwner)) {
            Log.e(TAG, "getBitmap: This thread does not own the OpenGL context.");
            return;
        }
                
        // Call the renderer draw routine
        mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
    } 

    public interface EGLConfigChooser {
        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display);
    }

    private abstract class BaseConfigChooser implements EGLConfigChooser {
        public BaseConfigChooser(int[] configSpec) {
            mConfigSpec =configSpec;
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] num_config = new int[1];
            if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config)) {
                throw new IllegalArgumentException("eglChooseConfig failed");
            }

            int numConfigs = num_config[0];

            if (numConfigs <= 0) {
                throw new IllegalArgumentException("No configs match configSpec");
            }

            EGLConfig[] configs = new EGLConfig[numConfigs];
            if (!egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs, num_config)) {
                throw new IllegalArgumentException("eglChooseConfig#2 failed");
            }

            //list configs
            listConfig(configs);

            EGLConfig config = chooseConfig(egl, display, configs);
            if (config == null) {
                throw new IllegalArgumentException("No config chosen");
            }
            return config;
        }

        abstract EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs);
        protected int[] mConfigSpec;
    }

    private class ComponentSizeChooser extends BaseConfigChooser {
        public ComponentSizeChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize, int stencilSize) {
            super(new int[] {
                    EGL10.EGL_RENDERABLE_TYPE,  EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_SURFACE_TYPE,     EGL_WINDOW_BIT | EGL_PBUFFER_BIT,

                    EGL10.EGL_RED_SIZE,         redSize,
                    EGL10.EGL_GREEN_SIZE,       greenSize,
                    EGL10.EGL_BLUE_SIZE,        blueSize,
                    EGL10.EGL_ALPHA_SIZE,       alphaSize,
                    EGL10.EGL_DEPTH_SIZE,       depthSize,
                    EGL10.EGL_STENCIL_SIZE,     stencilSize,

                    EGL10.EGL_NONE});

            mValue = new int[1];
            mRedSize = redSize;
            mGreenSize = greenSize;
            mBlueSize = blueSize;
            mAlphaSize = alphaSize;
            mDepthSize = depthSize;
            mStencilSize = stencilSize;
        }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
                EGLConfig[] configs) {
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                if ((d >= mDepthSize) && (s >= mStencilSize)) {
                    int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                    int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                    int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                    int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
                    if ((r == mRedSize) && (g == mGreenSize) && (b == mBlueSize) && (a == mAlphaSize)) {
                        return config;
                    }
                }
            }
            return null;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute, int defaultValue) {

            if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
                return mValue[0];
            }
            return defaultValue;
        }

        private int[] mValue;
        // Subclasses can adjust these values:
        protected int mRedSize;
        protected int mGreenSize;
        protected int mBlueSize;
        protected int mAlphaSize;
        protected int mDepthSize;
        protected int mStencilSize;
        }

    private class SimpleEGLConfigChooser extends ComponentSizeChooser {
        public SimpleEGLConfigChooser(int red, int green, int blue, int alpha, boolean withDepthBuffer) {
            super(red, green, blue, alpha, withDepthBuffer ? 16 : 0, 0);
        }
    }    
    
    private void listConfig(EGLConfig[] tmpConfig) {
        Log.i("cclw", "Config List {");

        for (EGLConfig config : tmpConfig) {
            int d, s, r, g, b, a;
                    
            // Expand on this logic to dump other attributes        
            d = getConfigAttrib(config, EGL_DEPTH_SIZE);
            s = getConfigAttrib(config, EGL_STENCIL_SIZE);
            r = getConfigAttrib(config, EGL_RED_SIZE);
            g = getConfigAttrib(config, EGL_GREEN_SIZE);
            b = getConfigAttrib(config, EGL_BLUE_SIZE);
            a = getConfigAttrib(config, EGL_ALPHA_SIZE);
            Log.i("cclw", "    <d,s,r,g,b,a> = <" + d + "," + s + "," +  r + "," + g + "," + b + "," + a + ">");
        }

        Log.i("cclw", "}");
    }
        
    private int getConfigAttrib(EGLConfig config, int attribute) {
        int[] value = new int[1];
        return mEGL.eglGetConfigAttrib(mEGLDisplay, config,
                        attribute, value)? value[0] : 0;
    }
}
