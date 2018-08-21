/*
 * Copyright (C) 2018 VLC authors and VideoLAN
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Adapted from https://github.com/googlesamples/android-ndk
 * native-codec/app/src/main/java/com/example/nativecodec/MyGLSurfaceView.java
 *
 * This sample shows how to use the org.videolan.vlc.MediaPlauer.GLRenderer class
 * with a GLSurfaceView in order to render a video (Hardware accelerated or not)
 * via VLC on a GL texture.
 * */

package org.videolan.glessample;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Point;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.GLRenderer;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLESActivity extends Activity {
    GLESView mView;

    @Override protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mView = new GLESView(getApplication());
        setContentView(mView);
    }

    @Override protected void onPause() {
        super.onPause();
        mView.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        mView.onResume();
    }

    @Override
    protected void onDestroy() {
        mView.destroy();
        super.onDestroy();
    }
}

class GLESView extends GLSurfaceView {

    private static final int EGL_VERSION = 3;
    private static final String ASSET_FILENAME = "bbb.m4v";

    private MyRenderer mRenderer;
    private MediaPlayer mMediaPlayer = null;

    public GLESView(Context context) {
        this(context, null);
    }

    public GLESView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init(context);
    }

    private void init(Context context) {
        final ArrayList<String> args = new ArrayList<>();
        args.add("-vvvv");
        final LibVLC libVLC = new LibVLC(context, args);
        mMediaPlayer = new MediaPlayer(libVLC);

        try {
            final Media media = new Media(libVLC, context.getAssets().openFd(ASSET_FILENAME));
            mMediaPlayer.setMedia(media);
            media.release();
        } catch (IOException e) {
            throw new RuntimeException("Invalid asset folder");
        }
        libVLC.release(); /* Will be destroyed when The media player is released */

        setEGLContextClientVersion(EGL_VERSION);
        mRenderer = new MyRenderer(mMediaPlayer.enableGLRenderer(EGL_VERSION));
        setRenderer(mRenderer);
    }


    @Override
    public void onPause() {
        mRenderer.onPause();
        mMediaPlayer.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mRenderer.onResume();
        mMediaPlayer.play();
    }

    public void destroy() {
        mMediaPlayer.release();
    }
}

class MyRenderer implements GLSurfaceView.Renderer {

    MyRenderer(GLRenderer glRenderer) {
        mGLRenderer = glRenderer;
        mPosVertices = ByteBuffer.allocateDirect(sPosVerticesData.length
                * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPosVertices.put(sPosVerticesData).position(0);
        mTexVertices = ByteBuffer.allocateDirect(sTexVerticesData.length
                * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexVertices.put(sTexVerticesData).position(0);
    }
    public void onPause() {
        mGLRenderer.onSurfaceDestroyed();
    }

    public void onResume() {
        mLastTime = SystemClock.elapsedRealtimeNanos();
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        GLES20.glUniform1i(msTextureHandle, /*GL_TEXTURE*/0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        final Point videoSize = new Point();
        int textureId = mGLRenderer.getVideoTexture(videoSize);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        final float ratio = videoSize.x / (float) videoSize.y;
        mPosVertices.put(0, -ratio);
        mPosVertices.put(2, ratio);
        mPosVertices.put(4, -ratio);
        mPosVertices.put(6, ratio);
        mPosVertices.position(0);

        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false,
                VERTICES_STRIDE_BYTES, mPosVertices);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");

        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                VERTICES_STRIDE_BYTES, mTexVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        /* Move it */
        long now = SystemClock.elapsedRealtimeNanos();
        mRunTime += (now - mLastTime);
        mLastTime = now;
        double d = ((double)mRunTime) / 500000000;
        Matrix.setIdentityM(mMMatrix, 0);
        Matrix.rotateM(mMMatrix, 0, 30, (float)Math.sin(d), (float)Math.cos(d), 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0);

        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Ignore the passed-in GL10 interface, and use the GLES20
        // class's static methods instead.
        GLES20.glViewport(0, 0, width, height);
        final float ratio = (float) width / height;
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.e(TAG, "onSurfaceCreated");
        /* Set up shaders and handles to their variables */
        mProgram = createProgram(sVertexShader, sFragmentShader);
        if (mProgram == 0) {
            return;
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }

        Matrix.setLookAtM(mVMatrix, 0, 0, 0, 4f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        msTextureHandle = GLES20.glGetUniformLocation(mProgram, "sTexture");
        checkGlError("glGetUniformLocation sTexture");
        if (msTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for sTexture");
        }

        mGLRenderer.onSurfaceCreated();
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
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int VERTICES_STRIDE_BYTES = 2 * FLOAT_SIZE_BYTES;

    private static final float[] sPosVerticesData = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f,  1.0f,
            1.0f,  1.0f,
    };

    private static final float[] sTexVerticesData = {
            0.f, 0.f,
            1.f, 0.f,
            0.f, 1.f,
            1.f, 1.f,
    };

    private FloatBuffer mPosVertices;
    private FloatBuffer mTexVertices;

    private static final String sVertexShader =
            "uniform mat4 uMVPMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVPMatrix * aPosition;\n" +
                    "  vTextureCoord = aTextureCoord.xy;\n" +
                    "}\n";

    private static final String sFragmentShader =
            "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    private float[] mMVPMatrix = new float[16];
    private float[] mProjMatrix = new float[16];
    private float[] mMMatrix = new float[16];
    private float[] mVMatrix = new float[16];

    private int mProgram;
    private int muMVPMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;
    private int msTextureHandle;

    private GLRenderer mGLRenderer;
    private long mLastTime = -1;
    private long mRunTime = 0;

    private static final String TAG = "GLESViewRenderer";
}