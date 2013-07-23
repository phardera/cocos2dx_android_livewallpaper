package com.macaronics.cclw;

import android.service.wallpaper.WallpaperService;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

import org.cocos2dx.lib.Cocos2dxRenderer;
import org.cocos2dx.lib.Cocos2dxHelper;


import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.egl.EGLConfig;

import android.os.Handler;
import android.os.HandlerThread;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;

import java.nio.ByteOrder;
import android.opengl.GLES20;

import java.util.concurrent.CountDownLatch;


public class cclwservice extends WallpaperService {
	
	public final static long NANOSECONDSPERSECOND = 1000000000L;
	public final static long NANOSECONDSPERMICROSECOND = 1000000;
	
	public static native void nativeAttachEGLImageKHR();
	public static native void nativeDestroyEGLImageKHR();
	public static native void nativeRenderToTexture();

    //----------------------
    // LIBRARY
    //
        static {
            System.loadLibrary("game");
        }

    //----------------------
    // VARIABLES
    //
        public static cclwservice inst =null;

        public static PixelBuffer pb =null;
        public static Handler ph =null;         //for rendering, rendering thread
        public static HandlerThread mThread;    //rendering thread

        private Cocos2dxRenderer pc2r =null;

    //----------------------
    // GL Engine
    //
        private class GLEngine extends Engine
        {
        
            //----------------------
            // VARIABLES
            //
                private WallpaperGLSurfaceView glSurfaceView;
                private boolean rendererHasBeenSet;

                private WallpaperGLRenderer glRenderer;
                 
            //----------------------
            // FUNCTION
            //                 
                @Override
                public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
                    Log.i("cclw", "onCommand");
                    return super.onCommand(action, x, y, z, extras, resultRequested);
                }

                @Override
                public void onCreate(SurfaceHolder surfaceHolder) {
                    super.onCreate(surfaceHolder);

                    setTouchEventsEnabled(true);

                    Log.i("cclw", "onCreate");
                    glSurfaceView = new WallpaperGLSurfaceView(cclwservice.this);

                    setEGLContextClientVersion(2);
                    glRenderer =new WallpaperGLRenderer();
                    setRenderer(glRenderer);

                }

                @Override
                public void onDestroy() {
                    super.onDestroy();

                    glRenderer.onDestroy();
                    glRenderer =null;

                    Log.i("cclw", "onDestroy");
                    glSurfaceView.onDestroy();
                }

                @Override
                public void onVisibilityChanged(boolean visible) {
                    super.onVisibilityChanged(visible);
                 
                    Log.i("cclw", "onVisibilityChanged, visible ="+visible);
                    if (rendererHasBeenSet) {
                        if (visible) {
                            Log.i("cclw", "calling onResume...");
                            glSurfaceView.onResume();
                        } else {
                            Log.i("cclw", "calling onPause...");
                            glSurfaceView.onPause();
                        }
                    }
                }

                @Override
                public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset)
                {
                    super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
                    //Log.i("cclw", "onOffsetsChanged, xOffset="+xOffset+", yOffset="+yOffset+", xOffsetStep="+xOffsetStep+", yOffsetStep="+yOffsetStep+", xPixelOffset="+xPixelOffset+", yPixelOffset="+yPixelOffset);
                }

                protected void setRenderer(Renderer renderer){
                    glSurfaceView.setRenderer(renderer);
                    rendererHasBeenSet =true;
                }

                protected void setEGLContextClientVersion(int version) {
                      glSurfaceView.setEGLContextClientVersion(version);
                }


            //------------------------
            // Custom SurfaceView for WallpaperService.engine
            //
                private class WallpaperGLSurfaceView extends GLSurfaceView {
                 
                    WallpaperGLSurfaceView(Context context) {
                        super(context);
                    }
                 
                    @Override
                    public SurfaceHolder getHolder() {
                        return getSurfaceHolder();
                    }
                 
                    public void onDestroy() {
                        //super.onDetachedFromWindow();
                    }
                }

        }

    //----------------------
    // RENDERER
    //
        private class WallpaperGLRenderer implements GLSurfaceView.Renderer{

            private String TAG ="cclw";
            
            int[] mTextureNameWorkspace =new int[] {0};

            private final float[] mTriangleVerticesData = { 
                        -1.0f, 1.0f, 0.0f, // Position 0
                        -1.0f, -1.0f, 0.0f, // Position 1
                        1.0f, -1.0f, 0.0f, // Position 2

                        -1.0f, 1.0f, 0.0f, // Position 0
                        1.0f, -1.0f, 0.0f, // Position 2
                        1.0f, 1.0f, 0.0f // Position 3    
                    };
            private final float[] mTriangleNormalData ={
                        0.0f,1.0f,0.0f,
                        0.0f,1.0f,0.0f,
                        0.0f,1.0f,0.0f,

                        0.0f,1.0f,0.0f,
                        0.0f,1.0f,0.0f,
                        0.0f,1.0f,0.0f
                    };

            private final float[] mTriangleTexCoordData ={
                        0.0f, 1.0f, // TexCoord 0
                        0.0f, 0.0f, // TexCoord 1
                        1.0f, 0.0f, // TexCoord 2

                        0.0f, 1.0f, // TexCoord 0
                        1.0f, 0.0f, // TexCoord 2
                        1.0f, 1.0f  // TexCoord 3
                    };

            private final float[] mTriangleTexCoordData2 ={
                        1.0f, 1.0f, // TexCoord 0
                        0.0f, 1.0f, // TexCoord 1
                        0.0f, 0.0f, // TexCoord 2

                        1.0f, 1.0f, // TexCoord 0
                        0.0f, 0.0f, // TexCoord 2
                        1.0f, 0.0f  // TexCoord 3
                    };

            private FloatBuffer mTriangleVertices;
            private FloatBuffer mTriangleNormal;
            private FloatBuffer mTriangleTexCoord;
            private FloatBuffer mTriangleTexCoord2;


            private final String mVertexShader = "attribute vec4 a_position;   \n"+
                "attribute vec3 a_normal;     \n"+
                "attribute vec2 a_texCoord;   \n"+
                "varying vec2 v_texCoord;     \n"+
                "varying vec3 v_normal;       \n"+
                "void main()                  \n"+
                "{                            \n"+
                "   gl_Position =a_position;  \n"+
                "   v_normal = a_normal;      \n"+
                "   v_texCoord = a_texCoord;  \n"+
                "}                            \n";

            private final String mFragmentShader = "precision mediump float; \n"+
                        "varying vec2 v_texCoord;                            \n"+
                        "varying vec3 v_normal;                              \n"+
                        "uniform sampler2D s_texture;                        \n"+
                        "void main()                                         \n"+
                        "{                                                   \n"+
                        "  gl_FragColor = texture2D( s_texture, v_texCoord );\n"+
                        "}                                                   \n";            

            private int mProgram;
            private int mvPositionHandle;
            private int mvNormalHandle;
            private int mvTexCoordHandle;
            private int mvSamplerHandle;

            private long mLastTickInNanoSeconds;
            public long aniInterval;

            public int mOrientation =0;

            public boolean onDestroyCalled =false;

            public WallpaperGLRenderer() {
                mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                mTriangleVertices.put(mTriangleVerticesData).position(0);

                mTriangleNormal = ByteBuffer.allocateDirect(mTriangleNormalData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                mTriangleNormal.put(mTriangleNormalData).position(0);

                mTriangleTexCoord = ByteBuffer.allocateDirect(mTriangleTexCoordData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                mTriangleTexCoord.put(mTriangleTexCoordData).position(0);

                mTriangleTexCoord2 =ByteBuffer.allocateDirect(mTriangleTexCoordData2.length *4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                mTriangleTexCoord2.put(mTriangleTexCoordData2).position(0);

                this.mLastTickInNanoSeconds =System.nanoTime();
                aniInterval =(long) (1.0 / 30 * NANOSECONDSPERSECOND);
            }

            public void onDestroy(){
                Log.i("cclw", "onDestroy called...");
                onDestroyCalled =true;

            }
            
            protected class WorkerRunnable implements Runnable{
                private final CountDownLatch doneSignal;
                WorkerRunnable(CountDownLatch doneSignal){
                    this.doneSignal =doneSignal;
                }

                public void run(){
                    try{
                        checkGlError("check GLError before eglCreateImageKHR");
                        checkEGLError("check EGLError before eglCreateImageKHR");
                        cclwservice.renderToTexture();
                        checkEGLError("eglCreateImageKHR");

                        this.doneSignal.countDown();
                    }catch(Exception ex){
                        Log.i("cclw", "Error : Runnable return exception : "+ex);

                    }                    
                }
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                try{
                        final long nowInNanoSeconds = System.nanoTime();
                        final long interval = nowInNanoSeconds - this.mLastTickInNanoSeconds;

                        //------------------------
                        // FETCH DATA
                        //
                            EGL10 mEGL = (EGL10) EGLContext.getEGL();
                            EGLSurface mEGLSurface =mEGL.eglGetCurrentSurface(EGL10.EGL_DRAW);
                            EGLDisplay mEGLDisplay =mEGL.eglGetCurrentDisplay();
                            EGLContext mEGLContext =mEGL.eglGetCurrentContext();

                            CountDownLatch doneSignal =new CountDownLatch(1);
                            cclwservice.ph.post(new WorkerRunnable(doneSignal));

                            doneSignal.await();

                        //------------------------
                        // SETUP BASIC ENVIRONMENT
                        //
                            mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
                            if (onDestroyCalled==true)
                            {
                                Log.i("cclw", "onDestroyCalled==true, ignore drawing...");
                                return;
                            }

                        //------------------------
                        // UPDATE TEXTURE
                        //
                            if (mTextureNameWorkspace[0]==0){                                                            
                                //Load texture
                                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                                gl.glGenTextures(1, mTextureNameWorkspace, 0);
                                Log.i("cclw", "mTextureNameWorkspace[0]="+mTextureNameWorkspace[0]);

                            }

                            gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextureNameWorkspace[0]);

                            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

                            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

                            cclwservice.nativeAttachEGLImageKHR();

                            gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);


                        //------------------------
                        // RENDER SCENE
                        //
                            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                            GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
                            GLES20.glUseProgram(mProgram);
                            checkGlError("glUseProgram");

                            GLES20.glVertexAttribPointer(mvPositionHandle, 3, GLES20.GL_FLOAT, false, 0, mTriangleVertices);
                            GLES20.glVertexAttribPointer(mvNormalHandle, 3, GLES20.GL_FLOAT, false, 0, mTriangleNormal);

                            if (mOrientation ==1)
                                GLES20.glVertexAttribPointer(mvTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTriangleTexCoord);
                            else if(mOrientation ==0)
                                GLES20.glVertexAttribPointer(mvTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTriangleTexCoord2);
                            checkGlError("glVertexAttribPointer");

                            GLES20.glEnableVertexAttribArray(mvPositionHandle);
                            GLES20.glEnableVertexAttribArray(mvNormalHandle);
                            GLES20.glEnableVertexAttribArray(mvTexCoordHandle);
                            checkGlError("glEnableVertexAttribArray");

                            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureNameWorkspace[0]);
                            GLES20.glUniform1i(mvSamplerHandle, 0);

                            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
                            checkGlError("glDrawArrays");

                        //------------------------
                        // FPS Limitation
                        //
                            final long val =(aniInterval - interval) / NANOSECONDSPERMICROSECOND;
                            if (val >0)
                            {
                                try{
                                    Thread.sleep(val);
                                }catch(final Exception e){}
                            }

                            this.mLastTickInNanoSeconds = nowInNanoSeconds;

                }
                catch(Exception e){

                }
            }

            @Override
            public void onSurfaceChanged(GL10 arg0, int arg1, int arg2) { //arg1:width, arg2:height
                // TODO Auto-generated method stub
                Log.i("cclw", "WallpaperGLRenderer::onSurfaceChanged, arg1="+arg1+", arg2="+arg2);

                float myRatio =1184.0f/720.0f;
                mOrientation =0;
                if (arg2>=arg1)
                {
                    myRatio =720.0f/1184.0f;
                    mOrientation =1;
                }

                int targetHeight =arg2;
                int targetWidth =(int)((float)arg2*myRatio);

                if (targetWidth >arg1)
                {
                    targetWidth =arg1;
                    targetHeight =(int)((float)arg1/myRatio);
                }

                Log.i("cclw", "WallpaperGLRenderer::onSurfaceChanged, fit targetWidth="+targetWidth+", targetHeight="+targetHeight);
                GLES20.glViewport((int)((arg1-targetWidth)*0.5f), (int)((arg2-targetHeight)*0.5f), targetWidth, targetHeight);
            }

            @Override
            public void onSurfaceCreated(GL10 arg0, EGLConfig arg1) {
                // TODO Auto-generated method stub
                Log.i("cclw", "WallpaperGLRenderer::onSurfaceCreated");

                mProgram =createProgram(mVertexShader, mFragmentShader);
                if (mProgram ==0)
                    return;

                mvPositionHandle =GLES20.glGetAttribLocation(mProgram, "a_position");
                if (mvPositionHandle ==-1)
                    return;
                
                mvNormalHandle =GLES20.glGetAttribLocation(mProgram, "a_normal");
                mvTexCoordHandle =GLES20.glGetAttribLocation(mProgram, "a_texCoord");
                mvSamplerHandle =GLES20.glGetUniformLocation(mProgram, "s_texture");

                GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            }

            private int loadShader(int shaderType, String source) {
                int shader = GLES20.glCreateShader(shaderType);
                if (shader != 0) {
                    GLES20.glShaderSource(shader, source);
                    GLES20.glCompileShader(shader);
                    int[] compiled = new int[1];
                    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
                    if (compiled[0] == 0) {
                        Log.e(TAG, "Could not compile shader " + shaderType + ":");
                        Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                        GLES20.glDeleteShader(shader);
                        shader = 0;
                    }
                }
                return shader;
            }

            private int createProgram(String vertexSource, String fragmentSource) {
                int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
                if (vertexShader == 0) {
                    return 0;
                }

                int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
                if (pixelShader == 0) {
                    return 0;
                }

                int program = GLES20.glCreateProgram();
                if (program != 0) {
                    GLES20.glAttachShader(program, vertexShader);
                    checkGlError("glAttachShader");
                    GLES20.glAttachShader(program, pixelShader);
                    checkGlError("glAttachShader");
                    GLES20.glLinkProgram(program);
                    int[] linkStatus = new int[1];
                    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
                    if (linkStatus[0] != GLES20.GL_TRUE) {
                        Log.e(TAG, "Could not link program: ");
                        Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                        GLES20.glDeleteProgram(program);
                        program = 0;
                    }
                }
                return program;
            }

            private void checkGlError(String op) {
                int error;
                while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                    Log.e(TAG, op + ": glError " + error);
                    throw new RuntimeException(op + ": glError " + error);
                }
            }

            private void checkEGLError(String op)
            {
                int error;
                EGL10 mEGL = (EGL10) EGLContext.getEGL();
                while ((error = mEGL.eglGetError()) != EGL10.EGL_SUCCESS) {
                    Log.e(TAG, op + ": eglError " + error);
                    throw new RuntimeException(op + ": eglError " + error);
                }
            }


        }


    //----------------------
    // MAIN FUNCTION
    //
        @Override
        public Engine onCreateEngine() {

            Log.i("cclw", "cclw - onCreateEngine");
            Engine retEng =null;
            retEng =new GLEngine();

            return retEng;
        }

        @Override
        public void onCreate()
        {
            //---------------------------------------
            Log.i("cclw", "cclw - onCreate");
            super.onCreate();
            inst =this;

            mThread =new HandlerThread("Rendering Thread");
            mThread.start();
            ph =new Handler(mThread.getLooper());
            ph.post(new Runnable(){
                @Override
                public void run(){
                    Log.i("cclw", "cclw - calling Cocos2dxHelper.init...");
                    Cocos2dxHelper.init(cclwservice.inst, null);

                    //prepare offscreen buffer
                    Log.i("cclw", "cclw - prepare offscreen buffer...");
                    if (pb ==null)
                        pb =new PixelBuffer(720, 1184);

                    Log.i("cclw", "cclw - create native renderer...");
                    //create renderer
                    pc2r =new Cocos2dxRenderer();
                    pc2r.setScreenWidthAndHeight(720, 1184);
                    //cclwservice.setupDesiredWallapperSize(720, 1184);
                    pb.setRenderer(pc2r);
                }
            });
                    
        }

        @Override
        public void onDestroy(){
            Log.i("cclw", "cclw - onDestroy");
            super.onDestroy();

            //dispose handler
            ph =null;


            //dispose thread
            Log.i("cclw", "cclw - dispose Rendering Thread...");
            mThread.quit();

            cclwservice.nativeDestroyEGLImageKHR();
            //cclwservice.nativeEnd();

            Log.i("cclw", "cclw - dispose PixelBuffer");
            if (pb !=null)
            {
                pb.dispose();
            }
            pb =null;
        }

        synchronized static public void renderToTexture()
        {
            pb.makeCurrent();
            cclwservice.nativeRenderToTexture();
        }

}