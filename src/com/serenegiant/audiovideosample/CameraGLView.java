package com.serenegiant.audiovideosample;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014 saki t_saki@serenegiant.com
 *
 * File name: CameraGLView.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.glutils.GLDrawer2D;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

/**
 * Sub class of GLSurfaceView to display camera preview and write video frame to capturing surface
 */
public final class CameraGLView extends GLSurfaceView {

	private static final boolean DEBUG = true; // TODO set false on release
	private static final String TAG = "CameraGLView";

	private CameraSurfaceRenderer mRenderer;
	private double mRequestedAspect = -1.0;
	private boolean mHasSurface;
	private CameraHandler mCameraHandler = null;

	public CameraGLView(Context context) {
		this(context, null, 0);
	}

	public CameraGLView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CameraGLView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs);
		if (DEBUG) Log.v(TAG, "CameraGLView:");
		mRenderer = new CameraSurfaceRenderer(this);
		setEGLContextClientVersion(2);	// GLES 2.0, API >= 8
		setRenderer(mRenderer);
		// the frequency of refreshing of camera preview is at most 15 fps
		// and RENDERMODE_WHEN_DIRTY is better to reduce power consumption
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	@Override
	public void onResume() {
		if (DEBUG) Log.v(TAG, "onResume:");
		super.onResume();
		if (mHasSurface) {
			if (mCameraHandler == null) {
				if (DEBUG) Log.v(TAG, "surface already exist");
				startPreview(getWidth(),  getHeight());
			}
		}
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		if (mCameraHandler != null) {
			// just request stop prviewing
			mCameraHandler.stopPreview(false);
		}
		super.onPause();
	}

	/**
	 * set aspect ratio of this view
	 * <code>aspect ratio = width / height</code>.
	 */
	public void setAspectRatio(double aspectRatio) {
		if (DEBUG) Log.v(TAG, "setAspectRatio:");
		if (aspectRatio < 0) {
			throw new IllegalArgumentException();
		}
		if (mRequestedAspect != aspectRatio) {
			mRequestedAspect = aspectRatio;
//			requestLayout();
		}
	}

	/**
	 * measure view size with keeping aspect ration
	 */
/*	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		if (mRequestedAspect > 0) {
			if (DEBUG) Log.v(TAG, "onMeasure:");
			int initialWidth = MeasureSpec.getSize(widthMeasureSpec);
			int initialHeight = MeasureSpec.getSize(heightMeasureSpec);

			final int horizPadding = getPaddingLeft() + getPaddingRight();
			final int vertPadding = getPaddingTop() + getPaddingBottom();
			initialWidth -= horizPadding;
			initialHeight -= vertPadding;

			final double viewAspectRatio = (double)initialWidth / initialHeight;
			final double aspectDiff = mRequestedAspect / viewAspectRatio - 1;

			// stay size if the difference of calculated aspect ratio is small enough from specific value
			if (Math.abs(aspectDiff) > 0.01) {
				if (aspectDiff > 0) {
					// adjust heght from width
					initialHeight = (int) (initialWidth / mRequestedAspect);
				} else {
					// adjust width from height
					initialWidth = (int) (initialHeight * mRequestedAspect);
				}
				initialWidth += horizPadding;
				initialHeight += vertPadding;
				widthMeasureSpec = MeasureSpec.makeMeasureSpec(initialWidth, MeasureSpec.EXACTLY);
				heightMeasureSpec = MeasureSpec.makeMeasureSpec(initialHeight, MeasureSpec.EXACTLY);
			}
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	} */

	public SurfaceTexture getSurfaceTexture() {
		if (DEBUG) Log.v(TAG, "getSurfaceTexture:");
		return mRenderer != null ? mRenderer.mSTexture : null;
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (DEBUG) Log.v(TAG, "surfaceDestroyed:");
		if (mCameraHandler != null) {
			// wait for finish previewing heare
			// otherwise camera try to display on un-exist Surface and some error will occure
			mCameraHandler.stopPreview(true);
		}
		mCameraHandler = null;
		mHasSurface = false;
		mRenderer.onSurfaceDestroyed();
		super.surfaceDestroyed(holder);
	}

	public void setVideoEncoder(final MediaVideoEncoder encoder) {
		if (DEBUG) Log.v(TAG, "setVideoEncoder:tex_id=" + mRenderer.hTex);
		queueEvent(new Runnable() {
			@Override
			public void run() {
				synchronized (mRenderer) {
					if (encoder != null) {
						encoder.setEglContext(EGL14.eglGetCurrentContext(), mRenderer.hTex);
					}
					mRenderer.mVideoEncoder = encoder;
				}
			}
		});
	}

//********************************************************************************
//********************************************************************************
	private synchronized void startPreview(final int width, final int height) {
		if (mCameraHandler == null) {
			final CameraThread thread = new CameraThread(this);
			thread.setName("Camera Thread");
			thread.start();
			mCameraHandler = thread.getHandler();
		}
		mCameraHandler.startPreview(width, height);
	}

	/**
	 * GLSurfaceViewのRenderer
	 */
	private static final class CameraSurfaceRenderer
		implements GLSurfaceView.Renderer,
					SurfaceTexture.OnFrameAvailableListener {	// API >= 11

		private final WeakReference<CameraGLView> mWeakParent;
		private SurfaceTexture mSTexture;	// API >= 11
		private int hTex;
		private GLDrawer2D mDrawer;
		private final float[] mStMatrix = new float[16];
		private MediaVideoEncoder mVideoEncoder;

		public CameraSurfaceRenderer(CameraGLView parent) {
			if (DEBUG) Log.v(TAG, "CameraSurfaceRenderer:");
			mWeakParent = new WeakReference<CameraGLView>(parent);
		}

		@Override
		public void onSurfaceCreated(GL10 unused, EGLConfig config) {
			if (DEBUG) Log.v(TAG, "onSurfaceCreated:");
			// This renderer required OES_EGL_image_external extension
			final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);	// API >= 8
//			if (DEBUG) Log.i(TAG, "onSurfaceCreated:Gl extensions: " + extensions);
			if (!extensions.contains("OES_EGL_image_external"))
				throw new RuntimeException("This system does not support OES_EGL_image_external.");
			// create textur ID
			hTex = GLDrawer2D.initTex();
			// create SurfaceTexture with texture ID.
			mSTexture = new SurfaceTexture(hTex);
			mSTexture.setOnFrameAvailableListener(this);
			// clear screen with yellow color but there are not many meanings
			GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
			final CameraGLView parent = mWeakParent.get();
			if (parent != null) {
				parent.mHasSurface = true;
			}
			// create object for preview display
			mDrawer = new GLDrawer2D();
		}

		@Override
		public void onSurfaceChanged(GL10 unused, int width, int height) {
			if (DEBUG) Log.v(TAG, "onSurfaceChanged:");
			// if at least with or height is zero, initialization of this view is still progress.
			if ((width == 0) || (height == 0)) return;
			// set Viewport to draw whole view
			GLES20.glViewport(0, 0, width, height);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			final CameraGLView parent = mWeakParent.get();
			if (parent != null) {
				final float req = (float)parent.mRequestedAspect;
				final float r = width / (float)height;
				int w, h, temp;
				if (r > req) {
					// if view is wider than camera image, calc width of drawing area based on view height
					h = 0;
					temp = (int)(req * height);
					w = (width - temp) / 2;
					width = temp;
				} else {
					// if view is higher than camera image, calc height of drawing area based on view width
					w = 0;
					temp = (int)(width / req);
					h = (height - temp) / 2;
					height = temp;
				}
				// set viewport to draw keeping aspect ration of camera image
				GLES20.glViewport(w, h, width, height);
			}
			parent.startPreview(width, height);
		}
		/**
		 * when GLSurface context is soon destroyed
		 * this method should be called from CameraGLView#surfaceDestroyed
		 */
		public void onSurfaceDestroyed() {
			if (DEBUG) Log.v(TAG, "onSurfaceDestroyed:");
			// Here is in GL context
			// This is the last palce that OpenGL functions can execute .
			// although texture and other GL objects are lost when GL context is lost.
			if (mDrawer != null) {
				mDrawer.release();
				mDrawer = null;
			}
			if (mSTexture != null) {
				mSTexture.release();
				mSTexture = null;
			}
			GLDrawer2D.deleteTex(hTex);
		}

		/**
		 * drawing to GLSurface
		 * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
		 * this method is only called when #requestRender is called(= when texture is required to update)
		 * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
		 */
		@Override
		public void onDrawFrame(GL10 unused) {
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

			// update texture(came from camera)
			mSTexture.updateTexImage();
			// get texture matrix
			mSTexture.getTransformMatrix(mStMatrix);
			synchronized (this) {
				if (mVideoEncoder != null) {
					// notify to capturing thread that the camera frame is available.
					mVideoEncoder.frameAvailableSoon(mStMatrix);
				}
			}
			// draw to preview screen
			mDrawer.draw(hTex, mStMatrix);
		}

		@Override
		public void onFrameAvailable(SurfaceTexture st) {
			final CameraGLView parent = mWeakParent.get();
			if (parent != null)
				parent.requestRender();
		}
	}

	/**
	 * Handler class for asynchronous camera operation
	 */
	private static final class CameraHandler extends Handler {
		private static final int MSG_PREVIEW_START = 1;
		private static final int MSG_PREVIEW_STOP = 2;
		private CameraThread mThread;

		public CameraHandler(CameraThread thread) {
			mThread = thread;
		}

		public void startPreview(int width, int height) {
			sendMessage(obtainMessage(MSG_PREVIEW_START, width, height));
		}

		/**
		 * request to stop camera preview
		 * @param needWait need to wait for stopping camera preview
		 */
		public void stopPreview(boolean needWait) {
			sendEmptyMessage(MSG_PREVIEW_STOP);
			if (needWait && mThread.mIsRunning) {
				synchronized (this) {
					try {
						if (DEBUG) Log.d(TAG, "wait for terminating of camera thread");
						wait();
					} catch (InterruptedException e) {
					}
				}
			}
		}

		/**
		 * message handler for camera thread
		 */
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_PREVIEW_START:
				mThread.startPreview(msg.arg1, msg.arg2);
				break;
			case MSG_PREVIEW_STOP:
				mThread.stopPreview();
				synchronized (this) {
					notifyAll();
				}
				Looper.myLooper().quit();
				mThread = null;
				break;
			default:
				throw new RuntimeException("unknown message:what=" + msg.what);
			}
		}
	}

	/**
	 * Thread for asynchronous operation of camera preview
	 */
	private static final class CameraThread extends Thread {
    	private final Object mReadyFence = new Object();
    	private final WeakReference<CameraGLView>mWeakParent;
    	private CameraHandler mHandler;
    	private volatile boolean mIsRunning = false;
		private Camera mCamera;
		private boolean mIsFrontFace;

    	public CameraThread(CameraGLView parent) {
    		mWeakParent = new WeakReference<CameraGLView>(parent);
    	}

    	public CameraHandler getHandler() {
            synchronized (mReadyFence) {
            	try {
            		mReadyFence.wait();
            	} catch (InterruptedException e) {
                }
            }
            return mHandler;
    	}

    	/**
    	 * message loop
    	 * prepare Looper and create Handler for this thread
    	 */
		@Override
		public void run() {
            if (DEBUG) Log.d(TAG, "Camera thread start");
            Looper.prepare();
            synchronized (mReadyFence) {
                mHandler = new CameraHandler(this);
                mIsRunning = true;
                mReadyFence.notify();
            }
            Looper.loop();
            if (DEBUG) Log.d(TAG, "Camera thread finish");
            synchronized (mReadyFence) {
                mHandler = null;
                mIsRunning = false;
            }
		}

		/**
		 * start camera preview
		 * @param width
		 * @param height
		 */
		private final void startPreview(int width, int height) {
			if (DEBUG) Log.v(TAG, "startPreview:");
			final CameraGLView parent = mWeakParent.get();
			if ((parent != null) && (mCamera == null)) {
				// This is a sample project so just use 0 as camera ID.
				// it is better to selecting camera is available
				try {
					mCamera = Camera.open(0);
					final Camera.Parameters params = mCamera.getParameters();
					// let's try fastest frame rate. You will get near 60fps, but your device become hot.
					final List<int[]> supportedFpsRange = params.getSupportedPreviewFpsRange();
					final int[] max_fps = supportedFpsRange.get(supportedFpsRange.size() - 1);
					params.setPreviewFpsRange(max_fps[0], max_fps[1]);
					params.setRecordingHint(true);
					// request preview size
					// this is a sample project and just use fixed value
					params.setPreviewSize(640, 480);
					// rotate camera preview according to the device orientation
					setRotation(params);
					mCamera.setParameters(params);
					// get the actual preview size
					final Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
					// adjust view size with keeping the aspect ration of camera preview.
					// here is not a UI thread and we should request parent view to execute.
					parent.post(new Runnable() {
						@Override
						public void run() {
							parent.setAspectRatio(previewSize.width * 1.0f / previewSize.height);
						}
					});
					mCamera.setPreviewTexture(parent.getSurfaceTexture());
				} catch (IOException e) {
					Log.e(TAG, "startPreview:", e);
					if (mCamera != null) {
						mCamera.release();
						mCamera = null;
					}
				} catch (RuntimeException e) {
					Log.e(TAG, "startPreview:", e);
					if (mCamera != null) {
						mCamera.release();
						mCamera = null;
					}
				}
				if (mCamera != null) {
					// start camera preview display
					mCamera.startPreview();
				}
			}
		}

		/**
		 * stop camera preview
		 */
		private void stopPreview() {
			if (DEBUG) Log.v(TAG, "stopPreview:");
			if (mCamera != null) {
				mCamera.stopPreview();
		        mCamera.release();
		        mCamera = null;
			}
			final CameraGLView parent = mWeakParent.get();
			if (parent == null) return;
			parent.mCameraHandler = null;
		}

		/**
		 * rotate preview screen according to the device orientation
		 * @param params
		 */
		private final void setRotation(Camera.Parameters params) {
			if (DEBUG) Log.v(TAG, "setRotation:");
			final CameraGLView parent = mWeakParent.get();
			if (parent == null) return;

			final Display display = ((WindowManager)parent.getContext()
				.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
			int rotation = display.getRotation();
			int degrees = 0;
			switch (rotation) {
				case Surface.ROTATION_0: degrees = 0; break;
				case Surface.ROTATION_90: degrees = 90; break;
				case Surface.ROTATION_180: degrees = 180; break;
				case Surface.ROTATION_270: degrees = 270; break;
			}
			// get whether the camera is front camera or back camera
			final Camera.CameraInfo info =
					new android.hardware.Camera.CameraInfo();
				android.hardware.Camera.getCameraInfo(0, info);
			mIsFrontFace = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
			if (mIsFrontFace) {	// front camera
				degrees = (info.orientation + degrees) % 360;
				degrees = (360 - degrees) % 360;  // reverse
			} else {  // back camera
				degrees = (info.orientation - degrees + 360) % 360;
			}
			// apply rotation setting
			mCamera.setDisplayOrientation(degrees);
			params.setRotation(degrees);
		}

	}
}
